package br.com.monitordenoticias.desktop

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.nio.file.Path
import java.nio.file.Paths
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

internal data class ExtractorQuality(
    val label: String,
    val selector: String,
    val compat: String,
    val maxHeight: Int?
)

internal val EXTRACTOR_QUALITIES = listOf(
    ExtractorQuality("360p", "bv*[height<=360][ext=mp4]+ba[ext=m4a]/b[height<=360][ext=mp4]/bv*[height<=360]+ba/b[height<=360]/b", "b[height<=360][ext=mp4]/b[height<=360]/b", 360),
    ExtractorQuality("480p", "bv*[height<=480][ext=mp4]+ba[ext=m4a]/b[height<=480][ext=mp4]/bv*[height<=480]+ba/b[height<=480]/b", "b[height<=480][ext=mp4]/b[height<=480]/b", 480),
    ExtractorQuality("720p HD", "bv*[height<=720][ext=mp4]+ba[ext=m4a]/b[height<=720][ext=mp4]/bv*[height<=720]+ba/b[height<=720]/b", "b[height<=720][ext=mp4]/b[height<=720]/b", 720),
    ExtractorQuality("1080p Full HD", "bv*[height<=1080][ext=mp4]+ba[ext=m4a]/b[height<=1080][ext=mp4]/bv*[height<=1080]+ba/b[height<=1080]/b", "b[height<=1080][ext=mp4]/b[height<=1080]/b", 1080),
    ExtractorQuality("Melhor disponível", "bv*[ext=mp4]+ba[ext=m4a]/b[ext=mp4]/bv*+ba/b", "b[ext=mp4]/b", null)
)

internal class ExtractorVideoEngine {
    val appDir: Path = discoverAppDir()
    val binDir = appDir.resolve("bin").toFile()
    val videosDir = appDir.resolve("Videos").toFile().apply { mkdirs() }
    val ytDlp = File(binDir, "yt-dlp.exe")
    val ytDlpStable = File(binDir, "yt-dlp-stable.exe")
    val ffmpeg = File(binDir, "ffmpeg.exe")
    val ffprobe = File(binDir, "ffprobe.exe")
    val deno = File(binDir, "deno.exe")
    private val activeProcess = AtomicReference<Process?>(null)
    private val globoplaySessionStore = GloboplaySessionStore(appDir.toFile())

    fun cancel() {
        activeProcess.getAndSet(null)?.let { runCatching { it.destroyForcibly() } }
    }

    suspend fun download(
        url: String,
        quality: ExtractorQuality,
        proxy: String,
        update: (Int, String) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            require(ytDlp.exists()) { "yt-dlp.exe não encontrado na pasta bin." }
            require(ffmpeg.exists()) { "ffmpeg.exe não encontrado na pasta bin." }
            val cleanUrl = normalizeR7Url(url.trim())
            if (cleanUrl.isBlank()) error("Informe um link válido.")

            when {
                isYouTube(cleanUrl) -> downloadYouTube(cleanUrl, quality, proxy, update)
                isGloboplay(cleanUrl) -> downloadGloboplay(cleanUrl, quality, proxy, update)
                isR7(cleanUrl) -> downloadGeneric(cleanUrl, quality, proxy, "R7/Record", update)
                else -> downloadGeneric(cleanUrl, quality, proxy, "vídeo", update)
            }
        }
    }

    private fun downloadYouTube(
        url: String,
        quality: ExtractorQuality,
        proxy: String,
        update: (Int, String) -> Unit
    ): File {
        val probe = probeYoutube(url, proxy)
        val file = if (probe.live) {
            update(0, "🔴 Live detectada. Baixando do início até o ponto atual...")
            downloadYoutubeLive(url, quality, proxy, update)
        } else {
            downloadGeneric(url, quality, proxy, "YouTube", update)
        }
        return ensureH264(file, update)
    }

    private data class YoutubeProbe(val live: Boolean, val timestamp: Long?)

    private fun probeYoutube(url: String, proxy: String): YoutubeProbe {
        val cmd = mutableListOf(
            ytDlp.absolutePath, "--ignore-config", "--no-playlist", "--dump-single-json",
            "--skip-download", "--socket-timeout", "30"
        )
        addCommonRuntime(cmd, proxy)
        cmd += url
        val result = runProcessCapture(cmd, 90)
        if (result.exitCode != 0 || result.output.isBlank()) return YoutubeProbe(false, null)
        return runCatching {
            val jsonLine = result.output.lineSequence().map { it.trim() }.lastOrNull { it.startsWith("{") } ?: return@runCatching YoutubeProbe(false, null)
            val json = JSONObject(jsonLine)
            val liveStatus = json.optString("live_status", "").lowercase(Locale.ROOT)
            val live = json.optBoolean("is_live", false) || liveStatus == "is_live" || liveStatus == "post_live"
            val timestamp = when {
                json.has("release_timestamp") -> json.optLong("release_timestamp")
                json.has("timestamp") -> json.optLong("timestamp")
                else -> 0L
            }.takeIf { it > 0L }
            YoutubeProbe(live, timestamp)
        }.getOrDefault(YoutubeProbe(false, null))
    }

    private fun downloadYoutubeLive(
        url: String,
        quality: ExtractorQuality,
        proxy: String,
        update: (Int, String) -> Unit
    ): File {
        val selector = quality.maxHeight?.let {
            "bv*[height<=$it][ext=mp4]+ba[ext=m4a]/b[height<=$it]/best[height<=$it]"
        } ?: quality.selector
        return runYtDlp(url, selector, proxy, listOf("--live-from-start", "--hls-use-mpegts"), update)
    }

    private fun downloadGloboplay(
        url: String,
        quality: ExtractorQuality,
        proxy: String,
        update: (Int, String) -> Unit
    ): File {
        val exe = if (ytDlpStable.exists()) ytDlpStable else ytDlp
        val id = Regex("(?:video|videos|v)/(?:[^0-9]*)([0-9]{5,})", RegexOption.IGNORE_CASE)
            .find(url)?.groupValues?.getOrNull(1)
            ?: Regex("([0-9]{6,})").find(url)?.groupValues?.getOrNull(1)

        val runtimeCookie = globoplaySessionStore.createRuntimeCookieFile()
        try {
            val common = listOf("--force-ipv4", "--ignore-config", "--no-mtime")
            var lastError = "Falha ao baixar conteúdo do Globoplay."

            val first = runCatching {
                update(0, "Globoplay: tentativa 1 — URL original...")
                runYtDlp(url, quality.selector, proxy, common, update, exe, runtimeCookie, url)
            }
            if (first.isSuccess) return first.getOrThrow()
            lastError = friendlyError(first.exceptionOrNull()?.message.orEmpty())
            if (lastError.contains("DRM", true)) error(lastError)

            if (!id.isNullOrBlank()) {
                val second = runCatching {
                    update(0, "Globoplay: tentativa 2 — globo:$id...")
                    runYtDlp("globo:$id", quality.selector, proxy, common, update, exe, runtimeCookie, url)
                }
                if (second.isSuccess) return second.getOrThrow()
                lastError = friendlyError(second.exceptionOrNull()?.message.orEmpty())
                if (lastError.contains("DRM", true)) error(lastError)
            }

            val hlsExtra = common + listOf("--hls-use-mpegts", "--downloader", "m3u8:native")
            val hls = runCatching {
                update(0, "Globoplay: tentativa HLS nativa...")
                runYtDlp(url, quality.selector, proxy, hlsExtra, update, exe, runtimeCookie, url)
            }
            if (hls.isSuccess) return hls.getOrThrow()
            lastError = friendlyError(hls.exceptionOrNull()?.message.orEmpty())
            if (lastError.contains("DRM", true)) error(lastError)

            val candidates = GloboplayHtmlFallback.extractM3u8Candidates(url, proxy).take(15)
            for ((index, candidate) in candidates.withIndex()) {
                val fallback = runCatching {
                    update(0, "Globoplay: mídia HLS ${index + 1}/${candidates.size}...")
                    runYtDlp(candidate, quality.selector, proxy, hlsExtra, update, exe, runtimeCookie, url)
                }
                if (fallback.isSuccess) return fallback.getOrThrow()
                lastError = friendlyError(fallback.exceptionOrNull()?.message.orEmpty())
                if (lastError.contains("DRM", true)) error(lastError)
            }

            if (runtimeCookie == null && (lastError.contains("autent", true) || lastError.contains("sessão", true) || lastError.contains("login", true))) {
                error("Globoplay requer autenticação. Abra Configurações > Globoplay, faça login e use SALVAR SESSÃO E VOLTAR.")
            }
            error(lastError)
        } finally {
            runCatching { runtimeCookie?.delete() }
        }
    }

    private fun downloadGeneric(
        url: String,
        quality: ExtractorQuality,
        proxy: String,
        source: String,
        update: (Int, String) -> Unit
    ): File {
        update(0, "Iniciando download de $source em ${quality.label}...")
        return runYtDlp(url, quality.selector, proxy, emptyList(), update)
    }

    private fun runYtDlp(
        url: String,
        selector: String,
        proxy: String,
        extra: List<String>,
        update: (Int, String) -> Unit,
        executable: File = ytDlp,
        cookieFile: File? = null,
        referer: String? = null
    ): File {
        videosDir.mkdirs()
        val before = videosDir.listFiles()?.associateBy { it.absolutePath.lowercase(Locale.ROOT) }.orEmpty()
        val cmd = mutableListOf(
            executable.absolutePath,
            "--no-playlist", "--newline", "--progress", "--windows-filenames",
            "--trim-filenames", "180", "--continue",
            "--retries", "10", "--fragment-retries", "10",
            "--retry-sleep", "http:linear=1::3", "--retry-sleep", "fragment:linear=1::3",
            "--socket-timeout", "30", "--ffmpeg-location", binDir.absolutePath,
            "-f", selector, "--merge-output-format", "mp4", "--remux-video", "mp4",
            "-o", File(videosDir, "%(title).150B [%(id)s].%(ext)s").absolutePath,
            "--print", "after_move:FINAL_FILE:%(filepath)s"
        )
        addCommonRuntime(cmd, proxy)
        if (cookieFile != null && cookieFile.exists()) cmd += listOf("--cookies", cookieFile.absolutePath)
        if (!referer.isNullOrBlank()) cmd += listOf("--referer", referer)
        cmd += extra
        cmd += url

        val process = ProcessBuilder(cmd).directory(appDir.toFile()).redirectErrorStream(true).start()
        activeProcess.set(process)
        var finalPath = ""
        var tail = ""
        val percentRegex = Regex("(\\d{1,3}(?:\\.\\d+)?)%")
        BufferedReader(InputStreamReader(process.inputStream, Charsets.UTF_8)).use { reader ->
            reader.forEachLine { line ->
                tail = (tail + "\n" + line).takeLast(6000)
                if (line.startsWith("FINAL_FILE:")) finalPath = line.substringAfter("FINAL_FILE:").trim().trim('"')
                val pct = percentRegex.find(line)?.groupValues?.getOrNull(1)?.toDoubleOrNull()?.toInt()
                if (pct != null) update(pct.coerceIn(0, 99), line.takeLast(220))
                else if (line.contains("[download]") || line.contains("[Merger]") || line.contains("[ffmpeg]", true)) {
                    update(0, line.takeLast(220))
                }
            }
        }
        val code = process.waitFor()
        activeProcess.compareAndSet(process, null)
        if (code != 0) error(friendlyError(tail))

        finalPath.takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.exists() && it.length() > 1024 }?.let { return it }
        return videosDir.listFiles()
            ?.filter { it.isFile && it.length() > 1024 && !before.containsKey(it.absolutePath.lowercase(Locale.ROOT)) }
            ?.maxByOrNull { it.lastModified() }
            ?: error("O processo terminou, mas nenhum arquivo de vídeo válido foi criado.")
    }

    private fun addCommonRuntime(cmd: MutableList<String>, proxy: String) {
        if (deno.exists()) cmd += listOf("--js-runtimes", "deno:${deno.absolutePath}")
        if (proxy.isNotBlank()) cmd += listOf("--proxy", proxy.trim())
    }

    private fun ensureH264(file: File, update: (Int, String) -> Unit): File {
        if (!ffprobe.exists() || !ffmpeg.exists()) return file
        val codec = probeCodec(file)
        if (codec == "h264" || codec == "avc1") return file

        update(99, "Convertendo vídeo para H.264/AVC compatível com Windows...")
        val out = File(file.parentFile, file.nameWithoutExtension + ".h264.mp4")
        val cmd = listOf(
            ffmpeg.absolutePath, "-y", "-i", file.absolutePath,
            "-c:v", "libx264", "-preset", "veryfast", "-crf", "20", "-pix_fmt", "yuv420p",
            "-c:a", "aac", "-b:a", "160k", "-movflags", "+faststart", out.absolutePath
        )
        val result = runProcessCapture(cmd, 3600)
        if (result.exitCode != 0 || !out.exists() || out.length() <= 1024) {
            runCatching { out.delete() }
            return file
        }
        val backup = File(file.parentFile, file.nameWithoutExtension + ".original." + file.extension)
        runCatching { file.renameTo(backup) }
        if (!out.renameTo(file)) return out
        runCatching { backup.delete() }
        return file
    }

    private fun probeCodec(file: File): String {
        val cmd = listOf(
            ffprobe.absolutePath, "-v", "error", "-select_streams", "v:0",
            "-show_entries", "stream=codec_name", "-of", "default=nw=1:nk=1", file.absolutePath
        )
        val result = runProcessCapture(cmd, 60)
        return result.output.trim().lineSequence().firstOrNull().orEmpty().lowercase(Locale.ROOT)
    }

    private data class ProcessResult(val exitCode: Int, val output: String)

    private fun runProcessCapture(cmd: List<String>, timeoutSeconds: Long): ProcessResult {
        val process = ProcessBuilder(cmd).directory(appDir.toFile()).redirectErrorStream(true).start()
        activeProcess.set(process)
        val output = StringBuilder()
        val readerThread = Thread {
            process.inputStream.bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.forEach { output.appendLine(it) }
            }
        }.apply { isDaemon = true; start() }
        val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            readerThread.join(1000)
            activeProcess.compareAndSet(process, null)
            return ProcessResult(-1, output.toString() + "\nTimeout")
        }
        readerThread.join(1000)
        activeProcess.compareAndSet(process, null)
        return ProcessResult(process.exitValue(), output.toString())
    }

    private fun friendlyError(raw: String): String {
        val text = redactProxy(raw)
        return when {
            text.contains("DRM", true) -> "Este conteúdo é protegido por DRM. O Extrator apenas detecta e informa; não realiza bypass de DRM."
            text.contains("login", true) || text.contains("authentication", true) || text.contains("cookies", true) -> "O conteúdo exige autenticação ou a sessão expirou. No Globoplay, use Configurações > Globoplay e salve uma sessão válida."
            text.contains("proxy", true) -> "Falha ao usar o proxy configurado. Confira servidor, porta e credenciais."
            text.contains("geo", true) || text.contains("region", true) || text.contains("country", true) -> "Conteúdo indisponível para esta região ou conta."
            text.contains("subscription", true) || text.contains("members", true) -> "Este conteúdo exige assinatura/permissão da conta."
            text.contains("unavailable", true) || text.contains("not available", true) -> "Conteúdo indisponível ou removido pela plataforma."
            text.contains("format", true) -> "A qualidade/formato solicitado não está disponível para este vídeo."
            text.contains("timed out", true) || text.contains("timeout", true) -> "A conexão expirou. Verifique rede ou proxy e tente novamente."
            else -> "Falha no download. Verifique rede, proxy, autenticação e disponibilidade do vídeo. Detalhe: ${text.takeLast(320)}"
        }
    }

    private fun redactProxy(text: String): String = text.replace(
        Regex("(?i)(https?|socks5?)://[^\\s:@/]+:[^\\s@/]+@"),
        "\$1://***:***@"
    )

    private fun isYouTube(url: String) = url.contains("youtube.com", true) || url.contains("youtu.be", true)
    private fun isGloboplay(url: String) = url.contains("globoplay.globo.com", true) || url.startsWith("globo:", true)
    private fun isR7(url: String) = url.contains("r7.com", true) || url.contains("record", true)

    private fun normalizeR7Url(value: String): String {
        var url = value.trim()
        val secondHttp = Regex("https?://", RegexOption.IGNORE_CASE).findAll(url).drop(1).firstOrNull()
        if (secondHttp != null) url = url.substring(0, secondHttp.range.first)
        return url.replace(" ", "%20")
    }

    companion object {
        private fun discoverAppDir(): Path {
            val cwd = Paths.get(System.getProperty("user.dir", ".")).toAbsolutePath().normalize()
            val candidates = listOf(cwd, cwd.parent ?: cwd)
            return candidates.firstOrNull { it.resolve("bin").toFile().exists() } ?: cwd
        }
    }
}
