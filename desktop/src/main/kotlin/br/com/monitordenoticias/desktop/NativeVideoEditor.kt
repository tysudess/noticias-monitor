package br.com.monitordenoticias.desktop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.input.key.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.awt.FileDialog
import java.awt.Frame
import java.io.ByteArrayInputStream
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import javax.imageio.ImageIO
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.SourceDataLine
import kotlin.math.roundToInt

private val VedInk = Color(0xFF0A1F4B)
private val VedMuted = Color(0xFF58739F)
private val VedBlue = Color(0xFF087AF7)
private val VedPurple = Color(0xFF743AF3)
private val VedGreen = Color(0xFF08A86F)
private val VedRed = Color(0xFFD92F43)
private val VedBg = Color(0xFFF3F8FE)
private val VedBorder = Color(0xFFD5E4F3)
private val VedSoftBlue = Color(0xFFEAF4FF)
private val VedSoftPurple = Color(0xFFF2ECFF)

private data class VedState(
    val available: Boolean = false,
    val version: String = "v3.0.1",
    val videoDir: String = "",
    val ffmpeg: Boolean = false,
    val ffprobe: Boolean = false,
    val error: String = ""
)

private data class VedClip(
    val path: String,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val fps: Double,
    val hasAudio: Boolean,
    val startMs: Long = 0L,
    val endMs: Long = durationMs,
    val cutBefore: Boolean = false
) {
    val selectedDurationMs: Long get() = (endMs - startMs).coerceAtLeast(0L)
}

private data class VedSnapshot(val clips: List<VedClip>, val selectedIndex: Int, val sourceMs: Long)

private object VedCore {
    private val running = AtomicReference<Process?>(null)

    private fun resolveCore(): File? {
        val roots = linkedSetOf<File>()
        fun parents(start: File?) {
            var f = start?.absoluteFile
            repeat(10) {
                if (f == null) return@repeat
                roots += f!!
                f = f!!.parentFile
            }
        }
        parents(File(System.getProperty("user.dir", ".")))
        ProcessHandle.current().info().command().orElse(null)?.let { parents(File(it).parentFile) }
        System.getProperty("java.class.path", "").split(File.pathSeparatorChar)
            .filter { it.isNotBlank() }.forEach { parents(File(it).parentFile) }
        return roots.asSequence().map { File(it, "tools/ExtratorVideosCore") }
            .firstOrNull { File(it, "VideoCoreBridge.exe").isFile }
    }

    private fun ffmpegFile(): File? = resolveCore()?.let { File(it, "bin/ffmpeg.exe") }?.takeIf { it.isFile }
    private fun ffprobeFile(): File? = resolveCore()?.let { File(it, "bin/ffprobe.exe") }?.takeIf { it.isFile }

    suspend fun state(): VedState = withContext(Dispatchers.IO) {
        val r = request(JSONObject().put("action", "state"), null, null)
        if (!r.optBoolean("ok", false)) {
            val core = resolveCore()
            val ffmpeg = core?.let { File(it, "bin/ffmpeg.exe") }?.isFile == true
            val ffprobe = core?.let { File(it, "bin/ffprobe.exe") }?.isFile == true
            return@withContext VedState(
                available = core != null && ffmpeg && ffprobe,
                ffmpeg = ffmpeg,
                ffprobe = ffprobe,
                error = if (core == null) r.optString("error", "Núcleo de vídeo indisponível.") else ""
            )
        }
        val bins = r.optJSONObject("binaries") ?: JSONObject()
        VedState(
            available = true,
            version = r.optString("version", "v3.0.1"),
            videoDir = r.optString("videoDir", ""),
            ffmpeg = bins.optBoolean("ffmpeg"),
            ffprobe = bins.optBoolean("ffprobe")
        )
    }

    /**
     * Probe local e independente do bridge. Isto evita que uma falha do processo Python
     * impeça o arquivo selecionado de entrar na timeline.
     */
    suspend fun probe(path: String): VedClip = withContext(Dispatchers.IO) {
        val source = File(path)
        require(source.isFile) { "Arquivo de vídeo não encontrado: ${source.name}" }
        val ffprobe = ffprobeFile() ?: throw IllegalStateException("ffprobe.exe não encontrado no Portable.")
        val process = ProcessBuilder(
            ffprobe.absolutePath,
            "-v", "error",
            "-show_entries", "format=duration:stream=codec_type,width,height,r_frame_rate",
            "-of", "json",
            source.absolutePath
        ).redirectErrorStream(true).start()
        val output = process.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        if (!process.waitFor(30, java.util.concurrent.TimeUnit.SECONDS)) {
            process.destroyForcibly()
            throw IllegalStateException("Tempo esgotado ao analisar ${source.name}.")
        }
        if (process.exitValue() != 0 || output.isBlank()) {
            throw IllegalStateException("Não foi possível analisar ${source.name}.")
        }
        val root = JSONObject(output)
        val durationSeconds = root.optJSONObject("format")?.optString("duration", "0")?.toDoubleOrNull() ?: 0.0
        val durationMs = (durationSeconds * 1000.0).roundToInt().toLong()
        if (durationMs <= 0L) throw IllegalStateException("Duração do vídeo não identificada: ${source.name}.")

        var width = 0
        var height = 0
        var fps = 30.0
        var hasAudio = false
        val streams = root.optJSONArray("streams") ?: JSONArray()
        for (i in 0 until streams.length()) {
            val stream = streams.optJSONObject(i) ?: continue
            when (stream.optString("codec_type")) {
                "video" -> if (width == 0) {
                    width = stream.optInt("width", 0)
                    height = stream.optInt("height", 0)
                    val rate = stream.optString("r_frame_rate", "")
                    runCatching {
                        val parts = rate.split('/')
                        val value = if (parts.size == 2) parts[0].toDouble() / parts[1].toDouble() else rate.toDouble()
                        if (value in 1.0..240.0) fps = value
                    }
                }
                "audio" -> hasAudio = true
            }
        }
        VedClip(
            path = source.absolutePath,
            durationMs = durationMs,
            width = width,
            height = height,
            fps = fps,
            hasAudio = hasAudio,
            endMs = durationMs
        )
    }

    suspend fun frame(path: String, positionMs: Long): ImageBitmap? = withContext(Dispatchers.IO) {
        val exe = ffmpegFile() ?: return@withContext null
        val cmd = listOf(
            exe.absolutePath, "-hide_banner", "-loglevel", "error", "-ss",
            "%.3f".format(java.util.Locale.US, positionMs.coerceAtLeast(0L) / 1000.0),
            "-i", path, "-frames:v", "1", "-vf", "scale=960:540:force_original_aspect_ratio=decrease",
            "-f", "image2pipe", "-vcodec", "png", "pipe:1"
        )
        runCatching {
            val p = ProcessBuilder(cmd).redirectError(ProcessBuilder.Redirect.DISCARD).start()
            val bytes = p.inputStream.readBytes()
            p.waitFor()
            if (p.exitValue() != 0 || bytes.isEmpty()) null
            else ImageIO.read(ByteArrayInputStream(bytes))?.toComposeImageBitmap()
        }.getOrNull()
    }

    fun audioEngine(): VedAudio = VedAudio(ffmpegFile())

    suspend fun export(
        clips: List<VedClip>, outputName: String, resolution: String, codec: String,
        targetEnabled: Boolean, targetMb: Int,
        onProgress: (Int) -> Unit, onMessage: (String) -> Unit
    ): JSONObject = withContext(Dispatchers.IO) {
        val arr = JSONArray()
        clips.forEach { c ->
            arr.put(JSONObject()
                .put("path", c.path).put("start_ms", c.startMs).put("end_ms", c.endMs)
                .put("duration_ms", c.durationMs).put("cut_before", c.cutBefore)
                .put("info", JSONObject().put("duration_ms", c.durationMs).put("width", c.width)
                    .put("height", c.height).put("fps", c.fps).put("has_audio", c.hasAudio)))
        }
        request(
            JSONObject().put("action", "export").put("clips", arr).put("outputName", outputName)
                .put("resolution", resolution).put("codec", codec)
                .put("targetSizeEnabled", targetEnabled).put("targetSizeMb", targetMb),
            onProgress, onMessage
        )
    }

    private fun request(req: JSONObject, onProgress: ((Int) -> Unit)?, onMessage: ((String) -> Unit)?): JSONObject {
        val core = resolveCore() ?: return JSONObject().put("ok", false).put("error", "Núcleo do Extrator de Vídeos não encontrado no pacote.")
        val exe = File(core, "VideoCoreBridge.exe")
        val p = ProcessBuilder(exe.absolutePath).directory(core).redirectErrorStream(true).start()
        running.set(p)
        p.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(req.toString()) }
        var terminal: JSONObject? = null
        try {
            p.inputStream.bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.forEach { raw ->
                    val line = raw.trim()
                    if (!line.startsWith("{") || !line.endsWith("}")) return@forEach
                    val obj = runCatching { JSONObject(line) }.getOrNull() ?: return@forEach
                    when (obj.optString("type")) {
                        "progress" -> onProgress?.invoke(obj.optInt("value", 0))
                        "message" -> onMessage?.invoke(obj.optString("message", ""))
                        "result" -> terminal = obj
                    }
                }
            }
            p.waitFor()
        } finally {
            running.compareAndSet(p, null)
        }
        return terminal ?: JSONObject().put("ok", false).put("error", "Núcleo de vídeo encerrou sem resposta.")
    }

    fun cancel() {
        val p = running.getAndSet(null) ?: return
        runCatching { ProcessBuilder("taskkill", "/PID", p.pid().toString(), "/T", "/F").start().waitFor() }
            .onFailure { runCatching { p.destroyForcibly() } }
    }
}

private class VedAudio(private val ffmpeg: File?) {
    private val process = AtomicReference<Process?>(null)
    private val line = AtomicReference<SourceDataLine?>(null)

    fun play(path: String, positionMs: Long) {
        stop()
        val exe = ffmpeg ?: return
        Thread({
            runCatching {
                val p = ProcessBuilder(
                    exe.absolutePath, "-hide_banner", "-loglevel", "error", "-ss",
                    "%.3f".format(java.util.Locale.US, positionMs.coerceAtLeast(0L) / 1000.0),
                    "-i", path, "-vn", "-f", "s16le", "-acodec", "pcm_s16le", "-ac", "2", "-ar", "44100", "pipe:1"
                ).redirectError(ProcessBuilder.Redirect.DISCARD).start()
                process.set(p)
                val format = AudioFormat(44100f, 16, 2, true, false)
                val out = AudioSystem.getSourceDataLine(format)
                out.open(format)
                out.start()
                line.set(out)
                val buf = ByteArray(16384)
                p.inputStream.use { input ->
                    while (process.get() === p) {
                        val n = input.read(buf)
                        if (n <= 0) break
                        out.write(buf, 0, n)
                    }
                }
                out.drain()
            }
            line.getAndSet(null)?.let { runCatching { it.stop(); it.close() } }
            process.getAndSet(null)?.let { runCatching { it.destroyForcibly() } }
        }, "monitor-editor-audio").apply { isDaemon = true; start() }
    }

    fun stop() {
        line.getAndSet(null)?.let { runCatching { it.stop(); it.flush(); it.close() } }
        process.getAndSet(null)?.let { runCatching { it.destroyForcibly() } }
    }
}

@Composable
fun V5NativeVideoEditorScreen() {
    var core by remember { mutableStateOf(VedState()) }
    val scope = rememberCoroutineScope()
    val clips = remember { mutableStateListOf<VedClip>() }
    val undo = remember { mutableStateListOf<VedSnapshot>() }
    var selectedIndex by remember { mutableIntStateOf(-1) }
    var sourceMs by remember { mutableLongStateOf(0L) }
    var frame by remember { mutableStateOf<ImageBitmap?>(null) }
    var playing by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf("Adicione um ou mais vídeos para começar.") }
    var statusOk by remember { mutableStateOf(true) }
    var outputName by remember { mutableStateOf("video_final.mp4") }
    var resolution by remember { mutableStateOf("Original") }
    var codec by remember { mutableStateOf("H.265 / HEVC") }
    var targetEnabled by remember { mutableStateOf(false) }
    var targetMb by remember { mutableFloatStateOf(30f) }
    var textFocused by remember { mutableStateOf(false) }
    val audio = remember(core.ffmpeg) { VedCore.audioEngine() }

    LaunchedEffect(Unit) { core = VedCore.state() }
    DisposableEffect(audio) { onDispose { audio.stop(); VedCore.cancel() } }

    val selected = clips.getOrNull(selectedIndex)
    val totalDuration = clips.sumOf { it.selectedDurationMs }

    fun globalStart(index: Int) = clips.take(index.coerceAtLeast(0)).sumOf { it.selectedDurationMs }
    fun globalPlayhead() = if (selectedIndex in clips.indices) {
        globalStart(selectedIndex) + (sourceMs - clips[selectedIndex].startMs).coerceIn(0L, clips[selectedIndex].selectedDurationMs)
    } else 0L

    fun pushUndo() {
        undo += VedSnapshot(clips.toList(), selectedIndex, sourceMs)
        if (undo.size > 50) undo.removeAt(0)
    }

    fun refreshFrame(c: VedClip?, pos: Long) {
        if (c == null) { frame = null; return }
        scope.launch { frame = VedCore.frame(c.path, pos.coerceIn(c.startMs, c.endMs)) }
    }

    fun select(index: Int, pos: Long? = null) {
        if (index !in clips.indices) return
        playing = false
        audio.stop()
        selectedIndex = index
        val c = clips[index]
        sourceMs = (pos ?: c.startMs).coerceIn(c.startMs, c.endMs)
        refreshFrame(c, sourceMs)
    }

    fun restoreUndo() {
        val snap = undo.removeLastOrNull() ?: run { status = "Nada para desfazer na timeline."; return }
        playing = false
        audio.stop()
        clips.clear()
        clips.addAll(snap.clips)
        selectedIndex = snap.selectedIndex.coerceIn(-1, clips.lastIndex)
        sourceMs = clips.getOrNull(selectedIndex)?.let { snap.sourceMs.coerceIn(it.startMs, it.endMs) } ?: 0L
        refreshFrame(clips.getOrNull(selectedIndex), sourceMs)
        status = "Última alteração desfeita."
        statusOk = true
    }

    fun removeSelected() {
        if (selectedIndex !in clips.indices || busy || importing) return
        pushUndo()
        playing = false
        audio.stop()
        clips.removeAt(selectedIndex)
        if (clips.isEmpty()) {
            selectedIndex = -1
            sourceMs = 0L
            frame = null
            status = "Timeline vazia."
        } else select(selectedIndex.coerceAtMost(clips.lastIndex))
    }

    fun reorder(from: Int, to: Int) {
        if (from !in clips.indices || to !in clips.indices || from == to || busy || importing) return
        pushUndo()
        val c = clips.removeAt(from)
        clips.add(to, c)
        selectedIndex = to
        sourceMs = c.startMs
        refreshFrame(c, sourceMs)
        status = "Clipe movido para a posição ${to + 1}."
        statusOk = true
    }

    fun updateSelected(transform: (VedClip) -> VedClip, message: String) {
        if (selectedIndex !in clips.indices || busy || importing) return
        pushUndo()
        val c = transform(clips[selectedIndex])
        clips[selectedIndex] = c
        playing = false
        audio.stop()
        sourceMs = c.startMs
        refreshFrame(c, sourceMs)
        status = message
        statusOk = true
    }

    fun seek(delta: Long) {
        val c = clips.getOrNull(selectedIndex) ?: return
        sourceMs = (sourceMs + delta).coerceIn(c.startMs, c.endMs)
        if (playing) audio.play(c.path, sourceMs)
        refreshFrame(c, sourceMs)
    }

    LaunchedEffect(playing, selectedIndex, clips.size) {
        if (!playing || selectedIndex !in clips.indices) return@LaunchedEffect
        var last = System.nanoTime()
        clips.getOrNull(selectedIndex)?.let { audio.play(it.path, sourceMs) }
        while (playing && selectedIndex in clips.indices) {
            delay(180)
            val now = System.nanoTime()
            val delta = ((now - last) / 1_000_000L).coerceAtLeast(1L)
            last = now
            val c = clips.getOrNull(selectedIndex) ?: break
            val next = sourceMs + delta
            if (next >= c.endMs) {
                if (selectedIndex < clips.lastIndex) {
                    audio.stop()
                    selectedIndex += 1
                    val n = clips[selectedIndex]
                    sourceMs = n.startMs
                    frame = VedCore.frame(n.path, sourceMs)
                    audio.play(n.path, sourceMs)
                    last = System.nanoTime()
                } else {
                    sourceMs = c.endMs
                    frame = VedCore.frame(c.path, (c.endMs - 1).coerceAtLeast(c.startMs))
                    playing = false
                    audio.stop()
                }
            } else {
                sourceMs = next
                frame = VedCore.frame(c.path, sourceMs)
            }
        }
    }

    Column(
        Modifier.fillMaxSize().background(VedBg).verticalScroll(rememberScrollState())
            .onPreviewKeyEvent { e ->
                if (textFocused || e.type != KeyEventType.KeyDown) false
                else when {
                    e.isCtrlPressed && e.key == Key.Z -> { restoreUndo(); true }
                    e.key == Key.Delete -> { removeSelected(); true }
                    else -> false
                }
            },
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(color = Color.White, shape = RoundedCornerShape(13.dp), border = BorderStroke(1.dp, VedBorder), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(38.dp).background(VedSoftPurple, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Movie, null, tint = VedPurple, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Editor de vídeos", color = VedInk, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Timeline independente • reprodução sem JavaFX • motor de exportação original ${core.version}", color = VedMuted, fontSize = 10.5.sp)
                }
                VedPill(
                    if (core.available && core.ffmpeg && core.ffprobe) "Editor pronto" else if (core.error.isNotBlank()) "Núcleo indisponível" else "Verificando...",
                    core.available && core.ffmpeg && core.ffprobe
                )
            }
        }

        Surface(
            color = if (statusOk) Color(0xFFEDF6FF) else Color(0xFFFFECEE),
            shape = RoundedCornerShape(10.dp),
            border = BorderStroke(1.dp, if (statusOk) VedBorder else Color(0xFFFFCDD2)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(if (statusOk) Icons.Default.Info else Icons.Default.Error, null, tint = if (statusOk) VedBlue else VedRed, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(7.dp))
                Text(status, color = if (statusOk) VedInk else VedRed, fontSize = 10.5.sp, modifier = Modifier.weight(1f))
                if (importing) CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
            }
        }

        Surface(color = Color.White, shape = RoundedCornerShape(13.dp), border = BorderStroke(1.dp, VedBorder), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("EDIÇÃO / TIMELINE", color = VedPurple, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                    Text(selected?.let { File(it.path).name } ?: "Nenhum vídeo selecionado", color = VedInk, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                OutlinedButton(onClick = {
                    if (busy || importing || !core.available) return@OutlinedButton
                    val files = pickVedFiles(core.videoDir)
                    if (files.isEmpty()) return@OutlinedButton
                    importing = true
                    status = "Analisando ${files.size} vídeo(s)..."
                    statusOk = true
                    scope.launch {
                        var added = 0
                        val errors = mutableListOf<String>()
                        for (f in files) {
                            runCatching { VedCore.probe(f.absolutePath) }
                                .onSuccess { c ->
                                    clips.add(c)
                                    added++
                                    if (selectedIndex < 0) select(0)
                                }
                                .onFailure { errors += (it.message ?: "Falha ao analisar ${f.name}.") }
                        }
                        importing = false
                        if (added > 0) {
                            if (selectedIndex < 0 && clips.isNotEmpty()) select(0)
                            status = "Timeline pronta: ${clips.size} clipe(s) • ${vedTime(clips.sumOf { it.selectedDurationMs })}" +
                                if (errors.isNotEmpty()) " • ${errors.size} arquivo(s) ignorado(s)" else ""
                            statusOk = true
                        } else {
                            status = errors.firstOrNull() ?: "Nenhum vídeo pôde ser adicionado à timeline."
                            statusOk = false
                        }
                    }
                }, enabled = core.available && !busy && !importing) {
                    Icon(Icons.Default.VideoFile, null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (importing) "Analisando..." else "Abrir vídeo")
                }
                Spacer(Modifier.width(7.dp))
                OutlinedButton(onClick = ::restoreUndo, enabled = undo.isNotEmpty() && !busy && !importing) {
                    Icon(Icons.Default.Undo, null, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Desfazer (Ctrl+Z)")
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
            Surface(color = Color(0xFF071426), shape = RoundedCornerShape(13.dp), border = BorderStroke(1.dp, Color(0xFF1A355A)), modifier = Modifier.weight(0.68f).height(390.dp)) {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f).fillMaxWidth().background(Color(0xFF071426)), contentAlignment = Alignment.Center) {
                        val image = frame
                        if (image != null) Image(image, "Pré-visualização", Modifier.fillMaxSize().padding(6.dp), contentScale = ContentScale.Fit)
                        else Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.VideoLibrary, null, tint = Color(0xFF58739F), modifier = Modifier.size(42.dp))
                            Text("Abra um vídeo para visualizar e editar", color = Color(0xFF9FB2CA), fontSize = 11.sp)
                        }
                    }
                    Row(Modifier.fillMaxWidth().background(Color(0xFF0B1C31)).padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        OutlinedButton(onClick = { seek(-5000) }, enabled = selected != null) { Text("−5s") }
                        Button(onClick = {
                            val c = clips.getOrNull(selectedIndex) ?: return@Button
                            if (playing) {
                                playing = false
                                audio.stop()
                                refreshFrame(c, sourceMs)
                            } else {
                                if (sourceMs >= c.endMs - 20) sourceMs = c.startMs
                                playing = true
                            }
                        }, enabled = selected != null) {
                            Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, null, modifier = Modifier.size(17.dp))
                            Spacer(Modifier.width(5.dp))
                            Text(if (playing) "Pausar" else "Reproduzir")
                        }
                        OutlinedButton(onClick = { seek(5000) }, enabled = selected != null) { Text("+5s") }
                        Spacer(Modifier.weight(1f))
                        Text("${vedTime(globalPlayhead())} / ${vedTime(totalDuration)}", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Surface(color = Color.White, shape = RoundedCornerShape(13.dp), border = BorderStroke(1.dp, VedBorder), modifier = Modifier.weight(0.32f)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("INÍCIO / FIM", color = VedPurple, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                    if (selected == null) Text("Selecione um clipe na timeline.", color = VedMuted, fontSize = 10.sp)
                    else {
                        var startText by remember(selected.path, selected.startMs) { mutableStateOf(vedTime(selected.startMs)) }
                        var endText by remember(selected.path, selected.endMs) { mutableStateOf(vedTime(selected.endMs)) }
                        OutlinedTextField(startText, { startText = it }, label = { Text("Início") }, modifier = Modifier.fillMaxWidth().onFocusChanged { textFocused = it.isFocused }, singleLine = true)
                        OutlinedTextField(endText, { endText = it }, label = { Text("Fim") }, modifier = Modifier.fillMaxWidth().onFocusChanged { textFocused = it.isFocused }, singleLine = true)
                        Text("Duração: ${vedTime(selected.selectedDurationMs)}", color = VedMuted, fontSize = 10.sp)
                        Button(onClick = {
                            val s = runCatching { vedParse(startText) }.getOrElse { status = it.message ?: "Tempo inválido"; statusOk = false; return@Button }
                            val e = runCatching { vedParse(endText) }.getOrElse { status = it.message ?: "Tempo inválido"; statusOk = false; return@Button }
                            if (e <= s || e > selected.durationMs) {
                                status = "O fim deve ser maior que o início e estar dentro do vídeo."
                                statusOk = false
                            } else updateSelected({ it.copy(startMs = s, endMs = e) }, "Trecho atualizado.")
                        }, modifier = Modifier.fillMaxWidth()) { Text("Aplicar tempos") }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(onClick = {
                                val p = sourceMs.coerceIn(0L, selected.endMs - 1)
                                updateSelected({ it.copy(startMs = p) }, "Início marcado em ${vedTime(p)}")
                            }, modifier = Modifier.weight(1f)) { Text("Início aqui", fontSize = 9.sp) }
                            OutlinedButton(onClick = {
                                val p = sourceMs.coerceIn(selected.startMs + 1, selected.durationMs)
                                updateSelected({ it.copy(endMs = p) }, "Fim marcado em ${vedTime(p)}")
                            }, modifier = Modifier.weight(1f)) { Text("Fim aqui", fontSize = 9.sp) }
                        }
                    }
                }
            }
        }

        Surface(color = Color.White, shape = RoundedCornerShape(13.dp), border = BorderStroke(1.dp, VedBorder), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("TIMELINE", color = VedPurple, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.width(8.dp))
                    Text("${clips.size} clipe(s) • ${vedTime(totalDuration)}", color = VedMuted, fontSize = 9.5.sp)
                    Spacer(Modifier.weight(1f))
                    Text("Cursor ${vedTime(globalPlayhead())}", color = VedBlue, fontWeight = FontWeight.Bold, fontSize = 9.5.sp)
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).height(98.dp), horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (clips.isEmpty()) {
                        Box(Modifier.width(720.dp).height(80.dp).background(VedSoftBlue, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                            Text("Adicione vídeos para montar a timeline", color = VedMuted)
                        }
                    } else clips.forEachIndexed { index, c ->
                        Surface(
                            color = if (index == selectedIndex) VedSoftPurple else VedSoftBlue,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(if (index == selectedIndex) 2.dp else 1.dp, if (index == selectedIndex) VedPurple else VedBorder),
                            modifier = Modifier.width(215.dp).height(80.dp).clickable(enabled = !busy && !importing) { select(index) }
                        ) {
                            Column(Modifier.padding(9.dp)) {
                                Text("${index + 1}. ${File(c.path).name}", color = VedInk, fontWeight = FontWeight.Bold, fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Spacer(Modifier.weight(1f))
                                Text("${vedTime(c.startMs)} → ${vedTime(c.endMs)}", color = VedMuted, fontSize = 8.5.sp)
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    listOf(-1000L to "−1s", -100L to "−100ms", -10L to "−10ms", 10L to "+10ms", 100L to "+100ms", 1000L to "+1s").forEach { (delta, label) ->
                        OutlinedButton(onClick = { seek(delta) }, contentPadding = PaddingValues(horizontal = 8.dp, vertical = 5.dp), enabled = selected != null && !busy && !importing) { Text(label, fontSize = 8.5.sp) }
                    }
                    Spacer(Modifier.weight(1f))
                    OutlinedButton(onClick = { if (selectedIndex > 0) reorder(selectedIndex, selectedIndex - 1) }, enabled = selectedIndex > 0 && !busy && !importing) {
                        Icon(Icons.Default.ArrowBack, null, Modifier.size(15.dp)); Text(" Mover")
                    }
                    OutlinedButton(onClick = { if (selectedIndex in 0 until clips.lastIndex) reorder(selectedIndex, selectedIndex + 1) }, enabled = selectedIndex in 0 until clips.lastIndex && !busy && !importing) {
                        Text("Mover "); Icon(Icons.Default.ArrowForward, null, Modifier.size(15.dp))
                    }
                    OutlinedButton(onClick = {
                        val c = clips.getOrNull(selectedIndex) ?: return@OutlinedButton
                        pushUndo(); clips.add(selectedIndex + 1, c); select(selectedIndex + 1); status = "Clipe duplicado."; statusOk = true
                    }, enabled = selected != null && !busy && !importing) { Icon(Icons.Default.ContentCopy, null, Modifier.size(15.dp)); Text(" Duplicar") }
                    OutlinedButton(onClick = {
                        val c = clips.getOrNull(selectedIndex) ?: return@OutlinedButton
                        if (sourceMs <= c.startMs || sourceMs >= c.endMs) { status = "Posicione o cursor dentro do clipe para cortar."; statusOk = false; return@OutlinedButton }
                        pushUndo()
                        val left = c.copy(endMs = sourceMs)
                        val right = c.copy(startMs = sourceMs, cutBefore = true)
                        clips[selectedIndex] = left
                        clips.add(selectedIndex + 1, right)
                        select(selectedIndex + 1)
                        status = "Corte criado em ${vedTime(sourceMs)}."
                        statusOk = true
                    }, enabled = selected != null && !busy && !importing) { Icon(Icons.Default.ContentCut, null, Modifier.size(15.dp)); Text(" Cortar") }
                    Button(onClick = ::removeSelected, enabled = selected != null && !busy && !importing, colors = ButtonDefaults.buttonColors(containerColor = VedRed)) {
                        Icon(Icons.Default.Delete, null, Modifier.size(15.dp)); Text(" Excluir")
                    }
                }
            }
        }

        Surface(color = Color.White, shape = RoundedCornerShape(13.dp), border = BorderStroke(1.dp, VedBorder), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("EXPORTAÇÃO", color = VedPurple, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(outputName, { outputName = it }, label = { Text("Nome do arquivo") }, modifier = Modifier.weight(1f).onFocusChanged { textFocused = it.isFocused }, singleLine = true)
                    VedDropdown("Codec", codec, listOf("H.264 / AVC", "H.265 / HEVC"), { codec = it }, Modifier.width(190.dp))
                    VedDropdown("Resolução", resolution, listOf("Original", "720p", "1080p"), { resolution = it }, Modifier.width(150.dp))
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Checkbox(targetEnabled, { targetEnabled = it })
                    Text("Tamanho alvo", color = VedInk, fontSize = 10.sp)
                    Slider(targetMb, { targetMb = it }, valueRange = 5f..500f, steps = 98, enabled = targetEnabled, modifier = Modifier.width(240.dp))
                    Text("${targetMb.roundToInt()} MB", color = VedInk, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                    Spacer(Modifier.weight(1f))
                    Button(onClick = {
                        if (clips.isEmpty() || busy || importing) return@Button
                        busy = true
                        playing = false
                        audio.stop()
                        progress = 0
                        status = "Preparando exportação..."
                        statusOk = true
                        scope.launch {
                            val r = runCatching {
                                VedCore.export(clips.toList(), outputName, resolution, codec, targetEnabled, targetMb.roundToInt(), { progress = it }, { if (it.isNotBlank()) status = it })
                            }.getOrElse { JSONObject().put("ok", false).put("error", it.message ?: "Falha na exportação.") }
                            busy = false
                            if (r.optBoolean("ok", false)) {
                                progress = 100
                                status = "✓ Exportação concluída: ${r.optString("result", "vídeo final")}"
                                statusOk = true
                            } else {
                                status = r.optString("error", "Falha na exportação.")
                                statusOk = false
                            }
                        }
                    }, enabled = clips.isNotEmpty() && core.available && !busy && !importing) {
                        Icon(Icons.Default.FileUpload, null, Modifier.size(17.dp)); Spacer(Modifier.width(6.dp)); Text("Exportar vídeo")
                    }
                    if (busy) OutlinedButton(onClick = { VedCore.cancel(); busy = false; status = "Exportação cancelada." }) { Text("Cancelar") }
                }
                LinearProgressIndicator(progress = { (progress / 100f).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(7.dp))
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun VedPill(text: String, ok: Boolean) {
    Surface(color = if (ok) Color(0xFFE5F8F0) else Color(0xFFFFECEE), shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, if (ok) Color(0xFFBEECD9) else Color(0xFFFFCDD2))) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(if (ok) VedGreen else VedRed, RoundedCornerShape(50)))
            Spacer(Modifier.width(6.dp))
            Text(text, color = if (ok) VedGreen else VedRed, fontSize = 9.5.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun VedDropdown(label: String, value: String, options: List<String>, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text("$label: $value", maxLines = 1) }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(option) }, onClick = { onSelect(option); expanded = false })
            }
        }
    }
}

private fun pickVedFiles(initialDir: String): List<File> = runCatching {
    val dialog = FileDialog(null as Frame?, "Abrir vídeo", FileDialog.LOAD).apply {
        isMultipleMode = true
        if (initialDir.isNotBlank() && File(initialDir).isDirectory) directory = initialDir
        setFilenameFilter { _, name ->
            name.lowercase().let { it.endsWith(".mp4") || it.endsWith(".mkv") || it.endsWith(".mov") || it.endsWith(".webm") || it.endsWith(".m4v") || it.endsWith(".avi") }
        }
        isVisible = true
    }
    dialog.files?.filter { it.isFile }?.toList().orEmpty()
}.getOrDefault(emptyList())

private fun vedTime(ms: Long): String {
    val safe = ms.coerceAtLeast(0L)
    val total = safe / 1000
    val milli = safe % 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return "%02d:%02d:%02d.%03d".format(h, m, s, milli)
}

private fun vedParse(text: String): Long {
    val parts = text.trim().replace(',', '.').split(':')
    require(parts.size in 2..3) { "Use MM:SS.mmm ou HH:MM:SS.mmm" }
    val h: Int
    val m: Int
    val s: Double
    if (parts.size == 2) {
        h = 0; m = parts[0].toInt(); s = parts[1].toDouble()
    } else {
        h = parts[0].toInt(); m = parts[1].toInt(); s = parts[2].toDouble()
    }
    require(h >= 0 && m in 0..59 && s >= 0.0 && s < 60.0) { "Tempo inválido" }
    return ((h * 3600 + m * 60 + s) * 1000.0).roundToInt().toLong()
}
