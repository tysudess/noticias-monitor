package br.com.monitordenoticias.desktop

import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI
import java.net.URL
import java.nio.file.Files
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

internal object YouTubeLiveSnapshot {
    data class Request(
        val url: String,
        val wantedHeight: Int,
        val proxy: String,
        val knownStartTimestamp: Long?,
        val ytDlp: File,
        val ffmpeg: File,
        val binDir: File,
        val deno: File,
        val appDir: File,
        val videosDir: File
    )

    fun download(
        request: Request,
        onProcess: (Process?) -> Unit,
        update: (Int, String) -> Unit
    ): Result<File> = runCatching {
        update(0, "🔴 Confirmando a live e congelando o ponto final…")
        val data = refreshLive(request, onProcess)
        val now = System.currentTimeMillis() / 1000L
        val start = when {
            data.optLong("release_timestamp", 0L) > 0L -> data.optLong("release_timestamp")
            data.optLong("timestamp", 0L) > 0L -> data.optLong("timestamp")
            (request.knownStartTimestamp ?: 0L) > 0L -> request.knownStartTimestamp!!
            else -> 0L
        }
        val duration = data.optDouble("duration", 0.0).toLong()
        val targetSeconds = when {
            start > 0L && start < now -> max(5L, now - start - 2L)
            duration > 0L -> max(5L, duration - 2L)
            else -> error("Não foi possível identificar quando a live começou. Esta transmissão pode estar sem DVR desde o início.")
        }
        update(0, "🔴 LIVE: baixar 00:00:00 → ${formatTime(targetSeconds)}. A transmissão continuará, mas o arquivo parará nesse ponto.")

        val format = selectMuxedHls(data, request.wantedHeight)
            ?: error("Nenhuma variante HLS muxada com áudio e vídeo foi encontrada para congelar o DVR.")
        val playlistUrl = format.optString("url")
        val headers = jsonHeaders(format.optJSONObject("http_headers"))
            .toMutableMap()
            .apply {
                putIfAbsent("User-Agent", "Mozilla/5.0")
                putIfAbsent("Referer", "https://www.youtube.com/")
            }
        val actualHeight = format.optInt("height", request.wantedHeight).takeIf { it > 0 } ?: request.wantedHeight
        update(0, "🔴 Congelando a janela DVR em ${actualHeight}p…")

        val frozen = freezePlaylist(playlistUrl, headers, request.proxy, targetSeconds)
        try {
            update(1, "Janela DVR congelada: ~${formatTime(frozen.durationSeconds.toLong())}. Novos minutos da live não serão adicionados.")
            request.videosDir.mkdirs()
            val title = safeName(data.optString("title", "Live YouTube"))
            val id = safeName(data.optString("id", "live"))
            val stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss", Locale.ROOT)
                .withZone(ZoneId.systemDefault()).format(Instant.now())
            var output = File(request.videosDir, "$title [LIVE-ATE-AGORA $stamp] [$id].mp4")
            var suffix = 2
            while (output.exists()) {
                output = File(request.videosDir, "$title [LIVE-ATE-AGORA $stamp-$suffix] [$id].mp4")
                suffix++
            }

            val copied = runFfmpegSnapshot(
                request = request,
                playlist = frozen.file,
                headers = headers,
                targetSeconds = targetSeconds,
                output = output,
                transcode = false,
                onProcess = onProcess,
                update = update
            )
            if (copied && output.exists() && output.length() > 1024L) return@runCatching output

            runCatching { output.delete() }
            update(1, "Remux direto não funcionou; tentando H.264/AAC…")
            val transcoded = runFfmpegSnapshot(
                request = request,
                playlist = frozen.file,
                headers = headers,
                targetSeconds = targetSeconds,
                output = output,
                transcode = true,
                onProcess = onProcess,
                update = update
            )
            if (!transcoded || !output.exists() || output.length() <= 1024L) {
                runCatching { output.delete() }
                error("FFmpeg não conseguiu processar a playlist DVR congelada.")
            }
            output
        } finally {
            runCatching { frozen.tempDir.deleteRecursively() }
        }
    }

    private data class FrozenPlaylist(val file: File, val durationSeconds: Double, val tempDir: File)

    private fun refreshLive(request: Request, onProcess: (Process?) -> Unit): JSONObject {
        val cmd = mutableListOf(
            request.ytDlp.absolutePath,
            "--no-playlist", "--dump-single-json", "--skip-download",
            "--socket-timeout", "25", "--ffmpeg-location", request.binDir.absolutePath
        )
        if (request.deno.exists()) cmd += listOf("--js-runtimes", "deno:${request.deno.absolutePath}")
        if (request.proxy.isNotBlank()) cmd += listOf("--proxy", request.proxy)
        cmd += request.url
        val captured = capture(cmd, request.appDir, emptyMap(), 90, onProcess)
        if (captured.first != 0) error(captured.second.takeLast(1600).ifBlank { "Falha ao confirmar a live." })
        val jsonLine = captured.second.lineSequence().map { it.trim() }.lastOrNull { it.startsWith("{") }
            ?: error("O YouTube não retornou metadados válidos para a live.")
        val data = JSONObject(jsonLine)
        val status = data.optString("live_status", "")
        if (!(data.optBoolean("is_live", false) || status == "is_live")) {
            error("O link não está mais ao vivo. Se a transmissão terminou, baixe como vídeo normal.")
        }
        return data
    }

    private fun selectMuxedHls(data: JSONObject, wantedHeight: Int): JSONObject? {
        val formats = data.optJSONArray("formats") ?: return null
        data class Candidate(val over: Int, val distance: Int, val height: Int, val tbr: Double, val obj: JSONObject)
        val candidates = mutableListOf<Candidate>()
        for (i in 0 until formats.length()) {
            val fmt = formats.optJSONObject(i) ?: continue
            val url = fmt.optString("url", "")
            val protocol = fmt.optString("protocol", "").lowercase(Locale.ROOT)
            if (url.isBlank()) continue
            if (!protocol.contains("m3u8") && !url.lowercase(Locale.ROOT).contains(".m3u8") && !url.lowercase(Locale.ROOT).contains("manifest/hls")) continue
            if (fmt.optString("vcodec", "none") == "none" || fmt.optString("acodec", "none") == "none") continue
            val height = fmt.optDouble("height", 0.0).toInt()
            val tbr = fmt.optDouble("tbr", 0.0)
            val over = if (height > 0 && height > wantedHeight) 1 else 0
            val distance = abs((if (height > 0) height else wantedHeight) - wantedHeight)
            candidates += Candidate(over, distance, height, tbr, fmt)
        }
        return candidates.sortedWith(
            compareBy<Candidate> { it.over }
                .thenBy { it.distance }
                .thenByDescending { it.height }
                .thenByDescending { it.tbr }
        ).firstOrNull()?.obj
    }

    private fun freezePlaylist(
        originalUrl: String,
        headers: Map<String, String>,
        proxy: String,
        targetSeconds: Long
    ): FrozenPlaylist {
        var playlistUrl = originalUrl
        var raw = fetchText(playlistUrl, headers, proxy)
        if (!raw.contains("#EXTM3U")) error("O YouTube não retornou uma playlist HLS válida para esta live.")
        var lines = raw.lines()
        if (lines.any { it.startsWith("#EXT-X-STREAM-INF") }) {
            var variant: String? = null
            for (i in lines.indices) {
                if (!lines[i].startsWith("#EXT-X-STREAM-INF")) continue
                for (j in i + 1..min(i + 3, lines.lastIndex)) {
                    val candidate = lines[j].trim()
                    if (candidate.isNotBlank() && !candidate.startsWith("#")) {
                        variant = URI(playlistUrl).resolve(candidate).toString()
                        break
                    }
                }
                if (variant != null) break
            }
            playlistUrl = variant ?: error("Não foi possível localizar a variante HLS da live.")
            raw = fetchText(playlistUrl, headers, proxy)
            lines = raw.lines()
        }

        var total = 0.0
        lines.forEach { line ->
            if (line.startsWith("#EXTINF:")) {
                total += line.substringAfter(':').substringBefore(',').toDoubleOrNull() ?: 0.0
            }
        }
        if (total <= 0.0) error("A playlist DVR não informou a duração dos segmentos.")
        val tolerance = max(90.0, min(300.0, targetSeconds * 0.04))
        if (total + tolerance < targetSeconds) {
            error("A janela DVR disponível contém cerca de ${formatTime(total.toLong())}, mas a live já tem aproximadamente ${formatTime(targetSeconds)}. O início não está mais disponível nessa janela HLS.")
        }

        val uriRegex = Regex("URI=\"([^\"]+)\"")
        val frozen = lines.map { line ->
            val trimmed = line.trim()
            when {
                trimmed.isBlank() -> line
                trimmed.startsWith("#") -> uriRegex.replace(line) { match ->
                    "URI=\"${URI(playlistUrl).resolve(match.groupValues[1])}\""
                }
                else -> URI(playlistUrl).resolve(trimmed).toString()
            }
        }.toMutableList()
        if (frozen.none { it.startsWith("#EXT-X-ENDLIST") }) frozen += "#EXT-X-ENDLIST"
        val dir = Files.createTempDirectory("extrator-live-").toFile()
        val file = File(dir, "snapshot.m3u8")
        file.writeText(frozen.joinToString("\n", postfix = "\n"), Charsets.UTF_8)
        return FrozenPlaylist(file, total, dir)
    }

    private fun runFfmpegSnapshot(
        request: Request,
        playlist: File,
        headers: Map<String, String>,
        targetSeconds: Long,
        output: File,
        transcode: Boolean,
        onProcess: (Process?) -> Unit,
        update: (Int, String) -> Unit
    ): Boolean {
        val cmd = mutableListOf(
            request.ffmpeg.absolutePath, "-y", "-hide_banner", "-loglevel", "warning",
            "-protocol_whitelist", "file,http,https,tcp,tls,crypto"
        )
        val headerBlob = headers.entries.joinToString("") { "${it.key}: ${it.value}\\r\\n" }
        if (headerBlob.isNotBlank()) cmd += listOf("-headers", headerBlob)
        cmd += listOf(
            "-i", playlist.absolutePath,
            "-t", targetSeconds.coerceAtLeast(1L).toString(),
            "-map", "0:v:0?", "-map", "0:a:0?"
        )
        if (transcode) {
            cmd += listOf(
                "-c:v", "libx264", "-preset", "veryfast", "-crf", "20", "-pix_fmt", "yuv420p",
                "-c:a", "aac", "-b:a", "160k"
            )
        } else {
            cmd += listOf("-c", "copy")
        }
        cmd += listOf("-movflags", "+faststart", "-progress", "pipe:1", "-nostats", output.absolutePath)

        val env = if (request.proxy.isBlank()) emptyMap() else mapOf("http_proxy" to request.proxy, "https_proxy" to request.proxy)
        val process = HiddenWindowsProcess.start(cmd, request.appDir, env)
        onProcess(process)
        var tail = ""
        process.inputStream.bufferedReader(Charsets.UTF_8).useLines { sequence ->
            sequence.forEach { line ->
                tail = (tail + "\n" + line).takeLast(5000)
                val micro = Regex("out_time_(?:ms|us)=(\\d+)").find(line)?.groupValues?.getOrNull(1)?.toLongOrNull()
                if (micro != null) {
                    val seconds = micro / 1_000_000.0
                    update((seconds * 100.0 / targetSeconds.coerceAtLeast(1L)).toInt().coerceIn(1, 99), line)
                }
            }
        }
        val code = process.waitFor()
        onProcess(null)
        if (code != 0) update(0, tail.takeLast(300))
        return code == 0
    }

    private fun capture(
        command: List<String>,
        directory: File,
        environment: Map<String, String>,
        timeoutSeconds: Long,
        onProcess: (Process?) -> Unit
    ): Pair<Int, String> {
        val process = HiddenWindowsProcess.start(command, directory, environment)
        onProcess(process)
        val output = StringBuilder()
        val reader = Thread {
            process.inputStream.bufferedReader(Charsets.UTF_8).useLines { lines -> lines.forEach { output.appendLine(it) } }
        }.apply { isDaemon = true; start() }
        val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
        if (!finished) {
            HiddenWindowsProcess.destroyTree(process)
            reader.join(1000)
            onProcess(null)
            return -1 to (output.toString() + "\nTimeout")
        }
        reader.join(1000)
        onProcess(null)
        return process.exitValue() to output.toString()
    }

    private fun fetchText(url: String, headers: Map<String, String>, proxyUrl: String): String {
        val connection = open(url, proxyUrl)
        connection.connectTimeout = 45_000
        connection.readTimeout = 45_000
        connection.instanceFollowRedirects = true
        headers.forEach { (key, value) -> connection.setRequestProperty(key, value) }
        return connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
    }

    private fun open(url: String, proxyUrl: String): HttpURLConnection {
        if (proxyUrl.isBlank()) return URL(url).openConnection() as HttpURLConnection
        val uri = URI(proxyUrl)
        val host = uri.host ?: return URL(url).openConnection() as HttpURLConnection
        val port = if (uri.port > 0) uri.port else 8080
        val type = if (uri.scheme.equals("socks5", true) || uri.scheme.equals("socks", true)) Proxy.Type.SOCKS else Proxy.Type.HTTP
        return URL(url).openConnection(Proxy(type, InetSocketAddress(host, port))) as HttpURLConnection
    }

    private fun jsonHeaders(json: JSONObject?): Map<String, String> {
        if (json == null) return emptyMap()
        return json.keys().asSequence().associateWith { json.optString(it, "") }.filterValues { it.isNotBlank() }
    }

    private fun safeName(value: String): String = value
        .replace(Regex("[<>:\"/\\\\|?*\\x00-\\x1f]"), "_")
        .trim(' ', '.')
        .take(145)
        .ifBlank { "Live YouTube" }

    private fun formatTime(seconds: Long): String {
        val safe = seconds.coerceAtLeast(1L)
        val h = safe / 3600L
        val m = (safe % 3600L) / 60L
        val s = safe % 60L
        return "%02d:%02d:%02d".format(Locale.ROOT, h, m, s)
    }
}
