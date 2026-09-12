package br.com.monitordenoticias.desktop

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.awt.Desktop
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

internal data class VideoEditorMediaInfo(
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val videoCodec: String,
    val audioCodec: String?,
    val formatName: String,
    val fps: Double = 0.0,
    val hasAudio: Boolean = audioCodec != null
)

/** Motor independente do Editor de Vídeo. Não importa nem chama ExtractorVideoEngine. */
internal class VideoEditorEngine {
    val appDir: Path = discoverAppDir()
    val binDir: File = appDir.resolve("bin").toFile()
    val ffmpeg: File = File(binDir, "ffmpeg.exe")
    val ffprobe: File = File(binDir, "ffprobe.exe")
    val exportsDir: File = appDir.resolve("VideoEditorExports").toFile().apply { mkdirs() }
    private val previewDir: File = appDir.resolve("data/video-editor/preview").toFile().apply { mkdirs() }
    private val thumbnailDir: File = appDir.resolve("data/video-editor/thumbs").toFile().apply { mkdirs() }

    private val activeProcess = AtomicReference<Process?>(null)
    private val cancelRequested = AtomicBoolean(false)

    fun cancel() {
        cancelRequested.set(true)
        VideoEditorProcess.destroyTree(activeProcess.getAndSet(null))
    }

    fun isSupportedInput(file: File): Boolean =
        file.isFile && file.extension.lowercase(Locale.ROOT) in SUPPORTED_EXTENSIONS

    suspend fun probe(file: File): Result<VideoEditorMediaInfo> = withContext(Dispatchers.IO) {
        runCatching {
            require(isSupportedInput(file)) { "Formato não suportado. Use MP4, MKV, WEBM, MOV, AVI ou M4V." }
            require(ffprobe.exists()) { "ffprobe.exe não encontrado em ${binDir.absolutePath}." }

            val result = runProcessCapture(
                listOf(
                    ffprobe.absolutePath,
                    "-v", "error",
                    "-print_format", "json",
                    "-show_format",
                    "-show_streams",
                    file.absolutePath
                ),
                60
            )
            if (result.exitCode != 0) {
                error("Não foi possível analisar o vídeo. ${result.output.takeLast(500)}")
            }

            val root = JSONObject(result.output)
            val streams = root.optJSONArray("streams") ?: error("O arquivo não possui streams reconhecíveis.")
            var videoCodec = ""
            var audioCodec: String? = null
            var width = 0
            var height = 0
            var fps = 0.0
            var streamDurationMs = 0L

            for (index in 0 until streams.length()) {
                val stream = streams.optJSONObject(index) ?: continue
                when (stream.optString("codec_type")) {
                    "video" -> if (videoCodec.isBlank()) {
                        videoCodec = stream.optString("codec_name", "").lowercase(Locale.ROOT)
                        width = stream.optInt("width", 0)
                        height = stream.optInt("height", 0)
                        fps = parseFps(stream.optString("avg_frame_rate", ""))
                            .takeIf { it > 0.0 } ?: parseFps(stream.optString("r_frame_rate", ""))
                        streamDurationMs = parseDurationMs(stream.optString("duration", ""))
                    }
                    "audio" -> if (audioCodec == null) {
                        audioCodec = stream.optString("codec_name", "")
                            .lowercase(Locale.ROOT)
                            .ifBlank { null }
                    }
                }
            }
            require(videoCodec.isNotBlank()) { "Nenhuma faixa de vídeo foi encontrada no arquivo." }

            val format = root.optJSONObject("format")
            val formatDurationMs = parseDurationMs(format?.optString("duration", "").orEmpty())
            val durationMs = maxOf(formatDurationMs, streamDurationMs)
            require(durationMs > 0L) { "Não foi possível determinar a duração do vídeo." }

            VideoEditorMediaInfo(
                durationMs = durationMs,
                width = width,
                height = height,
                videoCodec = videoCodec,
                audioCodec = audioCodec,
                formatName = format?.optString("format_name", "").orEmpty(),
                fps = fps,
                hasAudio = audioCodec != null
            )
        }
    }

    suspend fun preparePreview(
        input: File,
        info: VideoEditorMediaInfo,
        update: (Int, String) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            require(ffmpeg.exists()) { "ffmpeg.exe não encontrado em ${binDir.absolutePath}." }
            cancelRequested.set(false)

            if (canPlayDirectly(input, info)) {
                update(100, "Preview pronto.")
                return@runCatching input
            }

            previewDir.mkdirs()
            val proxy = File(previewDir, "${previewKey(input)}.mp4")
            if (proxy.exists() && proxy.length() > 64 * 1024L && proxy.lastModified() >= input.lastModified()) {
                update(100, "Preview compatível reutilizado.")
                return@runCatching proxy
            }

            runCatching { proxy.delete() }
            update(0, "Preparando preview compatível para reprodução interna...")
            val command = listOf(
                ffmpeg.absolutePath,
                "-y",
                "-i", input.absolutePath,
                "-map", "0:v:0",
                "-map", "0:a?",
                "-vf", "scale=w='min(1280,iw)':h=-2",
                "-c:v", "libx264",
                "-preset", "ultrafast",
                "-crf", "28",
                "-pix_fmt", "yuv420p",
                "-c:a", "aac",
                "-b:a", "128k",
                "-movflags", "+faststart",
                "-progress", "pipe:1",
                "-nostats",
                proxy.absolutePath
            )
            val result = runFfmpegWithProgress(command, info.durationMs, update)
            if (result.exitCode != 0 || !proxy.exists() || proxy.length() <= 1024L) {
                runCatching { proxy.delete() }
                if (cancelRequested.get()) error("Preparação do preview cancelada.")
                error("Falha ao preparar preview. ${result.output.takeLast(600)}")
            }
            proxy.setLastModified(System.currentTimeMillis())
            update(100, "Preview pronto.")
            proxy
        }
    }

    suspend fun generateThumbnails(
        input: File,
        startMs: Long,
        endMs: Long,
        maxCount: Int = 6
    ): Result<List<File>> = withContext(Dispatchers.IO) {
        runCatching {
            require(ffmpeg.exists()) { "ffmpeg.exe não encontrado em ${binDir.absolutePath}." }
            val duration = (endMs - startMs).coerceAtLeast(1L)
            val count = maxCount.coerceIn(1, 6)
            thumbnailDir.mkdirs()
            val base = previewKey(input)
            val files = mutableListOf<File>()
            for (index in 0 until count) {
                val at = startMs + ((duration * (index + 1)) / (count + 1))
                val out = File(thumbnailDir, "${base}_${startMs}_${endMs}_$index.jpg")
                if (!out.exists() || out.length() <= 256L || out.lastModified() < input.lastModified()) {
                    runCatching { out.delete() }
                    val result = runProcessCapture(
                        listOf(
                            ffmpeg.absolutePath,
                            "-y",
                            "-ss", secondsArg(at),
                            "-i", input.absolutePath,
                            "-frames:v", "1",
                            "-q:v", "5",
                            "-vf", "scale=160:-1",
                            out.absolutePath
                        ),
                        45
                    )
                    if (result.exitCode != 0 || !out.exists()) continue
                }
                files += out
            }
            files
        }
    }

    suspend fun exportPrecise(
        input: File,
        startMs: Long,
        endMs: Long,
        update: (Int, String) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            require(ffmpeg.exists()) { "ffmpeg.exe não encontrado em ${binDir.absolutePath}." }
            require(input.exists()) { "O vídeo original não foi encontrado." }
            require(startMs >= 0L) { "O ponto IN é inválido." }
            require(endMs > startMs) { "O ponto OUT deve ser maior que o ponto IN." }

            val durationMs = endMs - startMs
            require(durationMs >= 100L) { "Selecione um trecho com pelo menos 0,1 segundo." }
            cancelRequested.set(false)
            exportsDir.mkdirs()

            val safeBase = sanitizeFileName(input.nameWithoutExtension).ifBlank { "video" }
            val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date())
            val output = uniqueOutput(File(exportsDir, "${safeBase}_corte_$stamp.mp4"))

            update(0, "Exportando corte preciso em MP4...")
            val command = listOf(
                ffmpeg.absolutePath,
                "-y",
                "-i", input.absolutePath,
                "-ss", secondsArg(startMs),
                "-t", secondsArg(durationMs),
                "-map", "0:v:0",
                "-map", "0:a?",
                "-vf", "scale=trunc(iw/2)*2:trunc(ih/2)*2",
                "-c:v", "libx264",
                "-preset", "veryfast",
                "-crf", "20",
                "-pix_fmt", "yuv420p",
                "-c:a", "aac",
                "-b:a", "160k",
                "-movflags", "+faststart",
                "-progress", "pipe:1",
                "-nostats",
                output.absolutePath
            )

            val result = runFfmpegWithProgress(command, durationMs, update)
            if (result.exitCode != 0 || !output.exists() || output.length() <= 1024L) {
                runCatching { output.delete() }
                if (cancelRequested.get()) error("Exportação cancelada.")
                error("Falha ao exportar o corte. ${result.output.takeLast(700)}")
            }
            update(100, "Exportação concluída: ${output.name}")
            output
        }
    }

    fun openExportsFolder(): Result<Unit> = runCatching {
        exportsDir.mkdirs()
        require(Desktop.isDesktopSupported()) { "Abertura de pasta não é suportada neste ambiente." }
        Desktop.getDesktop().open(exportsDir)
    }

    private fun canPlayDirectly(file: File, info: VideoEditorMediaInfo): Boolean {
        val ext = file.extension.lowercase(Locale.ROOT)
        val compatibleContainer = ext in setOf("mp4", "mov", "m4v")
        val compatibleVideo = info.videoCodec == "h264" || info.videoCodec == "avc1"
        val compatibleAudio = info.audioCodec == null || info.audioCodec in setOf("aac", "mp3")
        return compatibleContainer && compatibleVideo && compatibleAudio
    }

    private fun runFfmpegWithProgress(
        command: List<String>,
        expectedDurationMs: Long,
        update: (Int, String) -> Unit
    ): ProcessResult {
        val process = VideoEditorProcess.start(command, appDir.toFile())
        activeProcess.set(process)
        val tail = StringBuilder()

        process.inputStream.bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.forEach { raw ->
                val line = raw.trim()
                if (tail.length > 8000) tail.delete(0, tail.length - 6000)
                tail.appendLine(line)
                val elapsedMs = when {
                    line.startsWith("out_time_us=") -> line.substringAfter('=').toLongOrNull()?.div(1000L)
                    line.startsWith("out_time_ms=") -> line.substringAfter('=').toLongOrNull()?.div(1000L)
                    else -> null
                }
                if (elapsedMs != null && expectedDurationMs > 0L) {
                    val pct = ((elapsedMs.toDouble() / expectedDurationMs.toDouble()) * 100.0)
                        .toInt().coerceIn(0, 99)
                    update(pct, "Processando vídeo... $pct%")
                } else if (line == "progress=end") {
                    update(100, "Finalizando arquivo...")
                }
            }
        }

        val exit = process.waitFor()
        activeProcess.compareAndSet(process, null)
        return ProcessResult(exit, tail.toString())
    }

    private fun runProcessCapture(command: List<String>, timeoutSeconds: Long): ProcessResult {
        val process = VideoEditorProcess.start(command, appDir.toFile())
        val output = StringBuilder()
        val reader = Thread {
            process.inputStream.bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.forEach { output.appendLine(it) }
            }
        }.apply { isDaemon = true; start() }
        val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
        if (!finished) VideoEditorProcess.destroyTree(process)
        reader.join(1500)
        return ProcessResult(if (finished) process.exitValue() else -1, output.toString())
    }

    private data class ProcessResult(val exitCode: Int, val output: String)

    private fun previewKey(file: File): String {
        val source = "${file.absolutePath.lowercase(Locale.ROOT)}|${file.length()}|${file.lastModified()}"
        val bytes = MessageDigest.getInstance("SHA-256").digest(source.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }.take(24)
    }

    private fun uniqueOutput(initial: File): File {
        if (!initial.exists()) return initial
        var index = 2
        while (true) {
            val candidate = File(initial.parentFile, "${initial.nameWithoutExtension}-$index.${initial.extension}")
            if (!candidate.exists()) return candidate
            index++
        }
    }

    private fun sanitizeFileName(value: String): String = value
        .replace(Regex("[\\\\/:*?\"<>|]"), "_")
        .replace(Regex("\\s+"), " ")
        .trim()
        .take(120)

    private fun secondsArg(ms: Long): String = String.format(Locale.US, "%.3f", ms / 1000.0)

    companion object {
        private val SUPPORTED_EXTENSIONS = setOf("mp4", "mov", "mkv", "webm", "avi", "m4v")

        private fun parseDurationMs(value: String): Long {
            val seconds = value.toDoubleOrNull() ?: return 0L
            if (!seconds.isFinite() || seconds <= 0.0) return 0L
            return (seconds * 1000.0).toLong()
        }

        private fun parseFps(value: String): Double {
            if (value.isBlank() || value == "0/0") return 0.0
            if ('/' !in value) return value.toDoubleOrNull()?.takeIf { it.isFinite() } ?: 0.0
            val parts = value.split('/', limit = 2)
            val numerator = parts.getOrNull(0)?.toDoubleOrNull() ?: return 0.0
            val denominator = parts.getOrNull(1)?.toDoubleOrNull() ?: return 0.0
            if (denominator == 0.0) return 0.0
            return (numerator / denominator).takeIf { it.isFinite() } ?: 0.0
        }

        private fun discoverAppDir(): Path {
            val cwd = Paths.get(System.getProperty("user.dir", ".")).toAbsolutePath().normalize()
            val launcherDir = runCatching {
                System.getProperty("jpackage.app-path", "")
                    .trim()
                    .takeIf { it.isNotBlank() }
                    ?.let { Paths.get(it).toAbsolutePath().normalize().parent }
            }.getOrNull()
            val codeSourceDir = runCatching {
                val path = Paths.get(VideoEditorEngine::class.java.protectionDomain.codeSource.location.toURI())
                    .toAbsolutePath().normalize()
                if (path.toFile().isFile) path.parent else path
            }.getOrNull()
            val candidates = listOfNotNull(launcherDir, cwd, codeSourceDir, cwd.parent).distinct()
            return candidates.firstOrNull { candidate ->
                candidate.resolve("bin").toFile().exists() ||
                    candidate.resolve("MonitorDeNoticias.exe").toFile().exists()
            } ?: launcherDir ?: cwd
        }
    }
}
