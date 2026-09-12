package br.com.monitordenoticias.desktop

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.nio.file.Files
import java.util.Locale
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal data class VideoInfo(
    val width: Int,
    val height: Int,
    val durationMs: Long,
    val fps: Double,
    val hasAudio: Boolean,
    val codec: String
)

internal data class VideoClip(
    val id: String = UUID.randomUUID().toString(),
    val path: String,
    val startMs: Long,
    val endMs: Long,
    val cutBefore: Boolean = false,
    val info: VideoInfo
) {
    val durationMs: Long get() = max(0L, endMs - startMs)
}

internal data class VideoEditorSnapshot(
    val clips: List<VideoClip>,
    val selectedIndex: Int,
    val globalPlayheadMs: Long,
    val label: String
)

internal enum class VideoResolution(val label: String, val targetHeight: Int?) {
    ORIGINAL("Original", null),
    P360("360p", 360),
    P480("480p", 480),
    P720("720p", 720),
    P1080("1080p", 1080)
}

internal enum class VideoCodec(val label: String) {
    H264("H.264 / AVC"),
    H265("H.265 / HEVC")
}

internal data class VideoExportConfig(
    val outputName: String = "video_final.mp4",
    val resolution: VideoResolution = VideoResolution.ORIGINAL,
    val codec: VideoCodec = VideoCodec.H265,
    val targetSizeEnabled: Boolean = true,
    val targetSizeMb: Int = 30
)

internal data class VideoExportResult(
    val file: File,
    val requestedCodec: VideoCodec,
    val effectiveCodec: VideoCodec,
    val width: Int,
    val height: Int,
    val videoBitrateKbps: Int,
    val realSizeMb: Double
)

internal class VideoEditorEngine {
    private val extractorEngine = ExtractorVideoEngine()
    val appDir = extractorEngine.appDir.toFile()
    val videosDir = extractorEngine.videosDir
    val ffmpeg = extractorEngine.ffmpeg
    val ffprobe = extractorEngine.ffprobe

    suspend fun probe(file: File): VideoInfo = withContext(Dispatchers.IO) {
        require(file.exists()) { "Arquivo não encontrado: ${file.name}" }
        require(ffprobe.exists()) { "ffprobe.exe não encontrado na pasta bin." }
        val cmd = listOf(
            ffprobe.absolutePath, "-v", "error", "-print_format", "json",
            "-show_streams", "-show_format", file.absolutePath
        )
        val result = runCapture(cmd, 60)
        if (result.first != 0) error(result.second.takeLast(1800))
        val root = JSONObject(result.second)
        val streams = root.optJSONArray("streams") ?: error("FFprobe não retornou streams.")
        var width = 0
        var height = 0
        var fps = 30.0
        var codec = ""
        var hasAudio = false
        for (i in 0 until streams.length()) {
            val s = streams.optJSONObject(i) ?: continue
            when (s.optString("codec_type")) {
                "video" -> if (width == 0) {
                    width = s.optInt("width", 0)
                    height = s.optInt("height", 0)
                    codec = s.optString("codec_name", "")
                    fps = parseFps(s.optString("avg_frame_rate", s.optString("r_frame_rate", "30/1")))
                }
                "audio" -> hasAudio = true
            }
        }
        val format = root.optJSONObject("format")
        val durationSec = format?.optString("duration")?.toDoubleOrNull()
            ?: (0 until streams.length()).mapNotNull { idx -> streams.optJSONObject(idx)?.optString("duration")?.toDoubleOrNull() }.maxOrNull()
            ?: 0.0
        require(width > 0 && height > 0 && durationSec > 0) { "Não foi possível identificar vídeo/duração." }
        VideoInfo(width, height, (durationSec * 1000.0).roundToInt().toLong(), fps.coerceIn(1.0, 60.0), hasAudio, codec)
    }

    suspend fun generateThumbnail(clip: VideoClip, destination: File): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            require(ffmpeg.exists()) { "ffmpeg.exe não encontrado." }
            destination.parentFile?.mkdirs()
            val middle = clip.startMs + clip.durationMs / 2
            val cmd = listOf(
                ffmpeg.absolutePath, "-y", "-ss", seconds(middle), "-i", clip.path,
                "-frames:v", "1", "-vf", "scale=240:-2", destination.absolutePath
            )
            val result = runCapture(cmd, 90)
            if (result.first != 0 || !destination.exists() || destination.length() < 1000) {
                error("Falha ao gerar miniatura: ${result.second.takeLast(1000)}")
            }
            destination
        }
    }

    fun sanitizeOutputName(raw: String): String {
        val base = raw.trim().ifBlank { "video_final.mp4" }
            .replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .replace(Regex("\\s+"), " ")
            .trim('.', ' ')
            .ifBlank { "video_final" }
        return if (base.lowercase(Locale.ROOT).endsWith(".mp4")) base else "$base.mp4"
    }

    fun uniqueOutputFile(rawName: String): File {
        videosDir.mkdirs()
        val safe = sanitizeOutputName(rawName)
        var out = File(videosDir, safe)
        if (!out.exists()) return out
        val stem = safe.removeSuffix(".mp4")
        var n = 2
        while (out.exists()) {
            out = File(videosDir, "$stem ($n).mp4")
            n++
        }
        return out
    }

    suspend fun export(
        clips: List<VideoClip>,
        config: VideoExportConfig,
        onProgress: (Int, String) -> Unit
    ): VideoExportResult = withContext(Dispatchers.IO) {
        require(ffmpeg.exists()) { "ffmpeg.exe não encontrado na pasta bin." }
        require(clips.isNotEmpty()) { "Adicione pelo menos um vídeo à timeline." }
        clips.forEach {
            require(File(it.path).exists()) { "Arquivo ausente: ${File(it.path).name}" }
            require(it.durationMs > 0L) { "Trecho inválido: ${File(it.path).name}" }
        }

        val totalDurationMs = clips.sumOf { it.durationMs }.coerceAtLeast(1L)
        val firstInfo = clips.first().info
        val fps = firstInfo.fps.takeIf { it in 1.0..60.0 } ?: 30.0
        val requestedCodec = config.codec
        val hevcAvailable = if (requestedCodec == VideoCodec.H265) supportsEncoder("libx265") else true
        val effectiveCodec = if (requestedCodec == VideoCodec.H265 && !hevcAvailable) VideoCodec.H264 else requestedCodec
        if (requestedCodec == VideoCodec.H265 && !hevcAvailable) {
            onProgress(0, "H.265 não disponível; usando H.264/AVC.")
        }

        val target = resolveDimensions(firstInfo.width, firstInfo.height, config.resolution)
        val audioKbps = 128
        val videoKbps = if (config.targetSizeEnabled) {
            val totalBits = config.targetSizeMb.coerceIn(1, 100).toDouble() * 1024.0 * 1024.0 * 8.0
            val seconds = totalDurationMs / 1000.0
            max(180, ((totalBits * 0.96 / seconds) / 1000.0 - audioKbps).roundToInt())
        } else {
            estimateDefaultBitrate(target.first, target.second, effectiveCodec)
        }
        val maxRate = max(videoKbps + 64, (videoKbps * 1.35).roundToInt())
        val bufSize = max(videoKbps * 2, (videoKbps * 2.5).roundToInt())

        val temp = Files.createTempDirectory("ExtratorVideos-timeline-").toFile()
        val parts = mutableListOf<File>()
        try {
            clips.forEachIndexed { index, clip ->
                val part = File(temp, "parte_${(index + 1).toString().padStart(3, '0')}.ts")
                val encoder = if (effectiveCodec == VideoCodec.H265) "libx265" else "libx264"
                val vf = buildScaleFilter(target.first, target.second, fps)
                val cmd = mutableListOf(
                    ffmpeg.absolutePath, "-y", "-hide_banner", "-nostats", "-progress", "pipe:1",
                    "-ss", seconds(clip.startMs), "-i", clip.path
                )
                if (!clip.info.hasAudio) {
                    cmd += listOf("-f", "lavfi", "-i", "anullsrc=channel_layout=stereo:sample_rate=48000")
                }
                cmd += listOf("-t", seconds(clip.durationMs), "-map", "0:v:0")
                cmd += if (clip.info.hasAudio) listOf("-map", "0:a:0?") else listOf("-map", "1:a:0")
                cmd += listOf(
                    "-vf", vf,
                    "-c:v", encoder, "-preset", "fast",
                    "-b:v", "${videoKbps}k", "-maxrate", "${maxRate}k", "-bufsize", "${bufSize}k",
                    "-pix_fmt", "yuv420p",
                    "-c:a", "aac", "-b:a", "${audioKbps}k", "-ar", "48000", "-ac", "2",
                    "-af", "aresample=async=1:first_pts=0",
                    "-shortest", "-avoid_negative_ts", "make_zero"
                )
                if (effectiveCodec == VideoCodec.H265) cmd += listOf("-x265-params", "log-level=error")
                cmd += part.absolutePath

                val basePercent = (index.toDouble() / clips.size * 90.0).roundToInt()
                val span = max(1, (90.0 / clips.size).roundToInt())
                val code = runProgress(cmd, clip.durationMs) { pct, msg ->
                    onProgress((basePercent + pct * span / 100).coerceIn(0, 90), msg)
                }
                if (code != 0 || !part.exists() || part.length() <= 1024) {
                    part.delete()
                    error("Falha ao processar ${File(clip.path).name}")
                }
                parts += part
            }

            val listFile = File(temp, "list.txt")
            listFile.writeText(parts.joinToString(System.lineSeparator()) { "file '${it.absolutePath.replace("'", "'\\''")}'" })
            val out = uniqueOutputFile(config.outputName)
            onProgress(91, "Unindo trechos...")
            val concatCmd = mutableListOf(
                ffmpeg.absolutePath, "-y", "-hide_banner", "-fflags", "+genpts",
                "-f", "concat", "-safe", "0", "-i", listFile.absolutePath,
                "-map", "0:v:0", "-map", "0:a:0?", "-c", "copy"
            )
            if (effectiveCodec == VideoCodec.H265) concatCmd += listOf("-tag:v", "hvc1")
            concatCmd += listOf("-bsf:a", "aac_adtstoasc", "-movflags", "+faststart", out.absolutePath)
            val concat = runCapture(concatCmd, 1800)
            if (concat.first != 0 || !out.exists() || out.length() <= 1024) {
                out.delete()
                error("Falha ao unir trechos: ${concat.second.takeLast(1800)}")
            }
            onProgress(100, "Exportação concluída: ${out.name}")
            VideoExportResult(
                file = out,
                requestedCodec = requestedCodec,
                effectiveCodec = effectiveCodec,
                width = target.first,
                height = target.second,
                videoBitrateKbps = videoKbps,
                realSizeMb = out.length().toDouble() / 1024.0 / 1024.0
            )
        } finally {
            runCatching { temp.deleteRecursively() }
        }
    }

    private fun supportsEncoder(name: String): Boolean {
        val result = runCapture(listOf(ffmpeg.absolutePath, "-hide_banner", "-encoders"), 30)
        return result.first == 0 && result.second.contains(name)
    }

    private fun resolveDimensions(w: Int, h: Int, resolution: VideoResolution): Pair<Int, Int> {
        if (resolution.targetHeight == null) return even(w) to even(h)
        val targetH = resolution.targetHeight
        val scale = targetH.toDouble() / h.toDouble()
        val targetW = even(max(2, (w * scale).roundToInt()))
        return targetW to even(targetH)
    }

    private fun buildScaleFilter(width: Int, height: Int, fps: Double): String {
        return "scale=$width:$height:force_original_aspect_ratio=decrease,pad=$width:$height:(ow-iw)/2:(oh-ih)/2,setsar=1,fps=${"%.3f".format(Locale.US, fps)}"
    }

    private fun estimateDefaultBitrate(width: Int, height: Int, codec: VideoCodec): Int {
        val pixels = width.toLong() * height.toLong()
        val base = when {
            pixels <= 640L * 360L -> 900
            pixels <= 854L * 480L -> 1500
            pixels <= 1280L * 720L -> 3000
            else -> 5500
        }
        return if (codec == VideoCodec.H265) (base * 0.72).roundToInt() else base
    }

    private fun runProgress(cmd: List<String>, durationMs: Long, cb: (Int, String) -> Unit): Int {
        val process = ProcessBuilder(cmd).directory(appDir).redirectErrorStream(true).start()
        var tail = ""
        BufferedReader(InputStreamReader(process.inputStream, Charsets.UTF_8)).use { reader ->
            reader.forEachLine { line ->
                tail = (tail + "\n" + line).takeLast(8000)
                val rawUs = when {
                    line.startsWith("out_time_us=") -> line.substringAfter('=').toLongOrNull()
                    line.startsWith("out_time_ms=") -> line.substringAfter('=').toLongOrNull()
                    else -> null
                }
                if (rawUs != null) {
                    val ms = rawUs / 1000L
                    cb(((ms.toDouble() / durationMs.coerceAtLeast(1L)) * 100.0).roundToInt().coerceIn(0, 99), "Processando...")
                } else if (line.startsWith("progress=end")) cb(100, "Trecho processado.")
            }
        }
        val code = process.waitFor()
        if (code != 0) cb(0, tail.takeLast(1000))
        return code
    }

    private fun runCapture(cmd: List<String>, timeoutSeconds: Long): Pair<Int, String> {
        val process = ProcessBuilder(cmd).directory(appDir).redirectErrorStream(true).start()
        val out = StringBuilder()
        val thread = Thread {
            process.inputStream.bufferedReader(Charsets.UTF_8).useLines { lines -> lines.forEach { out.appendLine(it) } }
        }.apply { isDaemon = true; start() }
        val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            thread.join(500)
            return -1 to (out.toString() + "\nTimeout")
        }
        thread.join(500)
        return process.exitValue() to out.toString()
    }

    private fun parseFps(value: String): Double {
        val parts = value.split('/')
        val result = if (parts.size == 2) {
            val a = parts[0].toDoubleOrNull() ?: 0.0
            val b = parts[1].toDoubleOrNull() ?: 1.0
            if (b == 0.0) 0.0 else a / b
        } else value.toDoubleOrNull() ?: 0.0
        return result.takeIf { it in 1.0..60.0 } ?: 30.0
    }

    private fun seconds(ms: Long): String = "%.3f".format(Locale.US, ms.coerceAtLeast(0L) / 1000.0)
    private fun even(v: Int): Int = if (v % 2 == 0) max(2, v) else max(2, v - 1)
}

internal fun formatVideoTime(ms: Long): String {
    val total = ms.coerceAtLeast(0L)
    val hours = total / 3_600_000
    val minutes = (total % 3_600_000) / 60_000
    val seconds = (total % 60_000) / 1000
    val millis = total % 1000
    return "%02d:%02d:%02d.%03d".format(hours, minutes, seconds, millis)
}

internal fun parseVideoTime(text: String): Long? {
    val clean = text.trim().replace(',', '.')
    val parts = clean.split(':')
    if (parts.size !in 2..3) return null
    val secPart = parts.last().toDoubleOrNull() ?: return null
    val sec = secPart.toInt()
    if (sec !in 0..59) return null
    val min = parts[parts.size - 2].toIntOrNull() ?: return null
    if (min !in 0..59) return null
    val hour = if (parts.size == 3) parts[0].toIntOrNull() ?: return null else 0
    if (hour < 0 || secPart < 0.0) return null
    return ((hour * 3600L + min * 60L) * 1000L + (secPart * 1000.0).roundToInt()).coerceAtLeast(0L)
}
