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
                isR7(cleanUrl) -> downloadGeneric(cleanUrl, quality, proxy, update, "R7/Record")
                else -> downloadGeneric(cleanUrl, quality, proxy, update, "vídeo")
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
            downloadGeneric(url, quality, proxy, update, "YouTube")
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
            val json = JSONObject(result.output.trim().lineSequence().last { it.trim().startsWith("{") })
            val liveStatus = json.optString("live_status", "").lowercase(Locale.ROOT)
            val isLive = json.optBoolean("is_live", false) || liveStatus == "is_live" || liveStatus == "post_live"
            val ts = when {
                json.has("release_timestamp") -> json.optLong("release_timestamp")
                json.has("timestamp") -> json.optLong("timestamp")
                else -> 0L
            }.takeIf { it > 0L }
            YoutubeProbe(isLive, ts)
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
        val extra = listOf("--live-from-start", "--hls-use-mpegts")
        return runYtDlp(url, selector, proxy, extra, update)
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

        val attempts = buildList {
            add(url)
            if (!id.isNullOrBlank()) add("globo:$id")
        }
        var lastError = "Falha ao baixar conteúdo do Globoplay."
        for ((index, target) in attempts.withIndex()) {
            update(0, "Globoplay: tentativa ${index + 1}/${attempts.size}...")
            val result = runCatching {
                runYtDlp(target, quality.selector, proxy, listOf("--force-ipv4", "--ignore-config", "--no-mtime"), update, exe)
            }
            if (result.isSuccess) return result.getOrThrow()
            lastError = friendlyError(result.exceptionOrNull()?.message.orEmpty())
            if (lastError.contains("DRM", true)) error(lastError)
        }
        error(lastError)
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
        executable: File = ytDlp
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
        val result = runProcessCapture(cmd, 60 * 60)
        if (result.exitCode != 0 || !out.exists() || out.length() <= 1024) {
            runCatching { out.delete() }
            return file
        }
        val backup = File(file.parentFile, file.nameWithoutExtension + ".original" + "." + file.extension)
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
        val r = runProcessCapture(cmd, 60)
        return r.output.trim().lineSequence().firstOrNull().orEmpty().lowercase(Locale.ROOT)
    }

    private data class ProcessResult(val exitCode: Int, val output: String)

    private fun runProcessCapture(cmd: List<String>, timeoutSeconds: Long): ProcessResult {
        val p = ProcessBuilder(cmd).directory(appDir.toFile()).redirectErrorStream(true).start()
        activeProcess.set(p)
        val reader = Thread {
            // leitura é feita sincronicamente abaixo para evitar deadlock de buffer
        }
        @Suppress("UNUSED_VARIABLE") val ignored = reader
        val output = p.inputStream.bufferedReader(Charsets.UTF_8).readText()
        val finished = p.waitFor(timeoutSeconds, java.util.concurrent.TimeUnit.SECONDS)
        if (!finished) {
            p.destroyForcibly()
            activeProcess.compareAndSet(p, null)
            return ProcessResult(-1, output + "\nTimeout")
        }
        activeProcess.compareAndSet(p, null)
        return ProcessResult(p.exitValue(), output)
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
        "$1://***:***@"
    )

    private fun isYouTube(url: String) = url.contains("youtube.com", true) || url.contains("youtu.be", true)
    private fun isGloboplay(url: String) = url.contains("globoplay.globo.com", true) || url.startsWith("globo:", true)
    private fun isR7(url: String) = url.contains("r7.com", true) || url.contains("record", true)

    private fun normalizeR7Url(value: String): String {
        var url = value.trim()
        val duplicated = Regex("(https?://.+?)(https?://)", RegexOption.IGNORE_CASE).find(url)
        if (duplicated != null) url = url.substring(0, duplicated.range.last - duplicated.groupValues[2].length + 1)
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
