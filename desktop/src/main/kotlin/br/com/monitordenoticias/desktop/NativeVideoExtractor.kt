package br.com.monitordenoticias.desktop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import javafx.application.Platform
import javafx.animation.KeyFrame
import javafx.animation.Timeline
import javafx.embed.swing.SwingFXUtils
import javafx.scene.Scene
import javafx.scene.image.WritableImage
import javafx.scene.layout.StackPane
import javafx.scene.media.Media
import javafx.scene.media.MediaPlayer
import javafx.scene.media.MediaView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.awt.image.BufferedImage
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import kotlin.math.roundToInt

private val VexInk = Color(0xFF0A1F4B)
private val VexMuted = Color(0xFF58739F)
private val VexBlue = Color(0xFF087AF7)
private val VexPurple = Color(0xFF743AF3)
private val VexGreen = Color(0xFF08A86F)
private val VexRed = Color(0xFFD92F43)
private val VexOrange = Color(0xFFFF820A)
private val VexBg = Color(0xFFF3F8FE)
private val VexBorder = Color(0xFFD5E4F3)
private val VexSoftBlue = Color(0xFFEAF4FF)
private val VexSoftPurple = Color(0xFFF2ECFF)

private data class VexCoreState(
    val available: Boolean = false,
    val version: String = "v3.0.1",
    val videoDir: String = "",
    val ytDlp: Boolean = false,
    val ytDlpStable: Boolean = false,
    val ffmpeg: Boolean = false,
    val ffprobe: Boolean = false,
    val deno: Boolean = false,
    val proxyEnabled: Boolean = false,
    val proxyServer: String = "",
    val proxyPort: String = "",
    val globoplaySession: Boolean = false,
    val error: String = ""
)

private data class VexClip(
    val path: String,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val fps: Double,
    val hasAudio: Boolean,
    val startMs: Long = 0,
    val endMs: Long = durationMs,
    val cutBefore: Boolean = false
) {
    val selectedDurationMs: Long get() = (endMs - startMs).coerceAtLeast(0)
}

private data class VexEditorSnapshot(
    val clips: List<VexClip>,
    val selectedIndex: Int,
    val playheadMs: Long
)

private object VexBridge {
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
            .filter { it.isNotBlank() }
            .forEach { parents(File(it).parentFile) }
        return roots.asSequence().map { File(it, "tools/ExtratorVideosCore") }
            .firstOrNull { File(it, "VideoCoreBridge.exe").isFile }
    }

    fun videoFolder(): File? = resolveCore()?.let { File(it, "Videos") }

    suspend fun state(): VexCoreState = withContext(Dispatchers.IO) {
        val response = runRequest(JSONObject().put("action", "state"), null, null)
        if (!response.optBoolean("ok", false)) {
            return@withContext VexCoreState(error = response.optString("error", "Núcleo do Extrator de Vídeos não encontrado."))
        }
        val bins = response.optJSONObject("binaries") ?: JSONObject()
        val proxy = response.optJSONObject("proxy") ?: JSONObject()
        VexCoreState(
            available = true,
            version = response.optString("version", "v3.0.1"),
            videoDir = response.optString("videoDir", ""),
            ytDlp = bins.optBoolean("ytDlp"),
            ytDlpStable = bins.optBoolean("ytDlpStable"),
            ffmpeg = bins.optBoolean("ffmpeg"),
            ffprobe = bins.optBoolean("ffprobe"),
            deno = bins.optBoolean("deno"),
            proxyEnabled = proxy.optBoolean("enabled"),
            proxyServer = proxy.optString("server", ""),
            proxyPort = proxy.optString("port", ""),
            globoplaySession = response.optBoolean("globoplaySession")
        )
    }

    suspend fun probe(path: String): VexClip = withContext(Dispatchers.IO) {
        val response = runRequest(JSONObject().put("action", "probe").put("path", path), null, null)
        if (!response.optBoolean("ok", false)) throw IllegalStateException(response.optString("error", "Não foi possível analisar o vídeo."))
        val info = response.optJSONObject("info") ?: JSONObject()
        val duration = info.optLong("duration_ms", 0L)
        if (duration <= 0L) throw IllegalStateException("Não foi possível identificar a duração do vídeo.")
        VexClip(
            path = response.optString("path", path),
            durationMs = duration,
            width = info.optInt("width", 0),
            height = info.optInt("height", 0),
            fps = info.optDouble("fps", 30.0),
            hasAudio = info.optBoolean("has_audio", false),
            endMs = duration
        )
    }

    suspend fun download(
        url: String,
        qualityIndex: Int,
        proxyEnabled: Boolean,
        proxyServer: String,
        proxyPort: String,
        proxyUser: String,
        proxyPassword: String,
        onProgress: (Int) -> Unit,
        onMessage: (String) -> Unit
    ): JSONObject = withContext(Dispatchers.IO) {
        val req = commonProxy(proxyEnabled, proxyServer, proxyPort, proxyUser, proxyPassword)
            .put("action", "download").put("url", url).put("qualityIndex", qualityIndex)
        runRequest(req, onProgress, onMessage)
    }

    suspend fun updateYtDlp(
        proxyEnabled: Boolean,
        proxyServer: String,
        proxyPort: String,
        proxyUser: String,
        proxyPassword: String,
        onMessage: (String) -> Unit
    ): JSONObject = withContext(Dispatchers.IO) {
        runRequest(
            commonProxy(proxyEnabled, proxyServer, proxyPort, proxyUser, proxyPassword).put("action", "update"),
            null,
            onMessage
        )
    }

    suspend fun saveProxy(enabled: Boolean, server: String, port: String): JSONObject = withContext(Dispatchers.IO) {
        runRequest(JSONObject().put("action", "save_proxy").put("proxyEnabled", enabled).put("proxyServer", server).put("proxyPort", port), null, null)
    }

    suspend fun globoplayLogin(
        proxyEnabled: Boolean,
        proxyServer: String,
        proxyPort: String,
        proxyUser: String,
        proxyPassword: String
    ): JSONObject = withContext(Dispatchers.IO) {
        runRequest(commonProxy(proxyEnabled, proxyServer, proxyPort, proxyUser, proxyPassword).put("action", "login"), null, null)
    }

    suspend fun deleteGloboplaySession(): JSONObject = withContext(Dispatchers.IO) {
        runRequest(JSONObject().put("action", "delete_session"), null, null)
    }

    suspend fun export(
        clips: List<VexClip>,
        outputName: String,
        resolution: String,
        codec: String,
        targetEnabled: Boolean,
        targetMb: Int,
        onProgress: (Int) -> Unit,
        onMessage: (String) -> Unit
    ): JSONObject = withContext(Dispatchers.IO) {
        val array = JSONArray()
        clips.forEach { clip ->
            array.put(
                JSONObject()
                    .put("path", clip.path)
                    .put("start_ms", clip.startMs)
                    .put("end_ms", clip.endMs)
                    .put("duration_ms", clip.durationMs)
                    .put("cut_before", clip.cutBefore)
                    .put(
                        "info", JSONObject()
                            .put("duration_ms", clip.durationMs)
                            .put("width", clip.width)
                            .put("height", clip.height)
                            .put("fps", clip.fps)
                            .put("has_audio", clip.hasAudio)
                    )
            )
        }
        val req = JSONObject()
            .put("action", "export")
            .put("clips", array)
            .put("outputName", outputName)
            .put("resolution", resolution)
            .put("codec", codec)
            .put("targetSizeEnabled", targetEnabled)
            .put("targetSizeMb", targetMb)
        runRequest(req, onProgress, onMessage)
    }

    private fun commonProxy(enabled: Boolean, server: String, port: String, user: String, password: String) =
        JSONObject()
            .put("proxyEnabled", enabled)
            .put("proxyServer", server)
            .put("proxyPort", port)
            .put("proxyUser", user)
            .put("proxyPassword", password)

    private fun runRequest(
        request: JSONObject,
        onProgress: ((Int) -> Unit)?,
        onMessage: ((String) -> Unit)?
    ): JSONObject {
        val core = resolveCore() ?: return JSONObject().put("ok", false).put("error", "Núcleo do Extrator de Vídeos não encontrado no pacote.")
        val exe = File(core, "VideoCoreBridge.exe")
        if (!exe.isFile) return JSONObject().put("ok", false).put("error", "VideoCoreBridge.exe não encontrado.")
        val process = ProcessBuilder(exe.absolutePath).directory(core).redirectErrorStream(true).start()
        running.set(process)
        process.outputStream.bufferedWriter(Charsets.UTF_8).use { out -> out.write(request.toString()) }
        var terminal: JSONObject? = null
        try {
            process.inputStream.bufferedReader(Charsets.UTF_8).useLines { lines ->
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
            process.waitFor()
        } finally {
            running.compareAndSet(process, null)
        }
        return terminal ?: JSONObject().put("ok", false).put("error", "O núcleo do Extrator de Vídeos encerrou sem resposta.")
    }

    fun cancel() {
        val process = running.getAndSet(null) ?: return
        runCatching {
            if (System.getProperty("os.name", "").contains("Windows", true)) {
                ProcessBuilder("taskkill", "/PID", process.pid().toString(), "/T", "/F").start().waitFor()
            } else {
                process.toHandle().descendants().forEach { it.destroyForcibly() }
                process.destroyForcibly()
            }
        }.onFailure { runCatching { process.destroyForcibly() } }
    }
}

private object VexFxRuntime {
    private val started = AtomicBoolean(false)
    private val ready = CompletableFuture<Unit>()

    private fun ensureStarted() {
        if (!started.compareAndSet(false, true)) return
        Thread({
            try {
                Platform.startup {
                    Platform.setImplicitExit(false)
                    ready.complete(Unit)
                }
            } catch (_: IllegalStateException) {
                // JavaFX was already initialized by another component.
                ready.complete(Unit)
            } catch (t: Throwable) {
                ready.completeExceptionally(t)
            }
        }, "monitor-video-javafx-startup").apply {
            isDaemon = true
            start()
        }
    }

    fun run(action: () -> Unit) {
        ensureStarted()
        ready.whenComplete { _, error ->
            if (error == null) runCatching { Platform.runLater(action) }
        }
    }
}

private class VexFxPreview {
    private var player: MediaPlayer? = null
    private var mediaView: MediaView? = null
    private var scene: Scene? = null
    private var frameTimer: Timeline? = null
    @Volatile private var frame: BufferedImage? = null

    @Volatile var currentMs: Long = 0L
        private set
    @Volatile var durationMs: Long = 0L
        private set
    @Volatile var playing: Boolean = false
        private set
    @Volatile var loadedPath: String = ""
        private set

    fun latestFrame(): BufferedImage? = frame

    fun load(path: String, positionMs: Long = 0L, autoplay: Boolean = false) {
        if (path.isBlank()) return
        loadedPath = path
        VexFxRuntime.run {
            runCatching {
                stopFx()
                val media = Media(File(path).toURI().toString())
                val mp = MediaPlayer(media)
                val view = MediaView(mp).apply {
                    isPreserveRatio = true
                    fitWidth = 960.0
                    fitHeight = 540.0
                }
                val root = StackPane(view).apply {
                    style = "-fx-background-color: #071426;"
                    resize(960.0, 540.0)
                }
                val sc = Scene(root, 960.0, 540.0)
                root.applyCss()
                root.layout()

                player = mp
                mediaView = view
                scene = sc
                currentMs = positionMs.coerceAtLeast(0L)
                durationMs = 0L
                playing = false
                frame = null

                val timer = Timeline(KeyFrame(javafx.util.Duration.millis(125.0), javafx.event.EventHandler { captureFrameFx() })).apply {
                    cycleCount = Timeline.INDEFINITE
                }
                frameTimer = timer

                mp.setOnReady {
                    durationMs = mp.totalDuration.toMillis().toLong().coerceAtLeast(0L)
                    mp.seek(javafx.util.Duration.millis(positionMs.coerceAtLeast(0L).toDouble()))
                    captureFrameFx()
                    if (autoplay) mp.play()
                }
                mp.currentTimeProperty().addListener { _, _, value ->
                    currentMs = value.toMillis().toLong().coerceAtLeast(0L)
                }
                mp.statusProperty().addListener { _, _, value ->
                    playing = value == MediaPlayer.Status.PLAYING
                    if (playing) timer.play() else {
                        timer.pause()
                        captureFrameFx()
                    }
                }
                mp.setOnEndOfMedia {
                    playing = false
                    timer.pause()
                    captureFrameFx()
                }
                mp.setOnError {
                    playing = false
                    timer.stop()
                }
            }.onFailure {
                playing = false
                durationMs = 0L
            }
        }
    }

    private fun captureFrameFx() {
        val view = mediaView ?: return
        runCatching {
            val image = WritableImage(960, 540)
            val snapped = view.snapshot(null, image)
            frame = SwingFXUtils.fromFXImage(snapped, null)
        }
    }

    private fun stopFx() {
        frameTimer?.stop()
        frameTimer = null
        player?.stop()
        player?.dispose()
        player = null
        mediaView = null
        scene = null
        playing = false
    }

    fun play() = VexFxRuntime.run { player?.play() }
    fun pause() = VexFxRuntime.run { player?.pause() }
    fun seek(ms: Long) = VexFxRuntime.run {
        player?.seek(javafx.util.Duration.millis(ms.coerceAtLeast(0L).toDouble()))
        Timeline(KeyFrame(javafx.util.Duration.millis(80.0), javafx.event.EventHandler { captureFrameFx() })).play()
    }
    fun toggle() = VexFxRuntime.run {
        if (player?.status == MediaPlayer.Status.PLAYING) player?.pause() else player?.play()
    }
    fun dispose() {
        frame = null
        loadedPath = ""
        currentMs = 0L
        durationMs = 0L
        playing = false
        VexFxRuntime.run { stopFx() }
    }
}

@Composable
private fun VexPreviewSurface(preview: VexFxPreview, modifier: Modifier = Modifier) {
    var frame by remember(preview) { mutableStateOf<ImageBitmap?>(null) }

    LaunchedEffect(preview) {
        var lastFrame: BufferedImage? = null
        while (true) {
            val latest = preview.latestFrame()
            if (latest !== lastFrame) {
                frame = if (latest == null) null else withContext(Dispatchers.Default) { latest.toComposeImageBitmap() }
                lastFrame = latest
            }
            delay(if (preview.playing) 100 else 240)
        }
    }

    Box(modifier.background(Color(0xFF071426)), contentAlignment = Alignment.Center) {
        val bitmap = frame
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = "Pré-visualização do vídeo",
                modifier = Modifier.fillMaxSize().padding(6.dp),
                contentScale = ContentScale.Fit
            )
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.VideoLibrary, null, tint = Color(0xFF58739F), modifier = Modifier.size(40.dp))
                Text("Abra um vídeo para visualizar e editar", color = Color(0xFF9FB2CA), fontSize = 11.sp)
            }
        }
    }
}

private enum class VexTab { DOWNLOAD, EDITOR }

@Composable
fun V5NativeVideoExtractorScreen() {
    var tab by remember { mutableStateOf(VexTab.DOWNLOAD) }
    var state by remember { mutableStateOf(VexCoreState()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { state = VexBridge.state() }

    Column(Modifier.fillMaxSize().background(VexBg), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(color = Color.White, shape = RoundedCornerShape(13.dp), border = BorderStroke(1.dp, VexBorder), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(38.dp).background(VexSoftPurple, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.VideoLibrary, null, tint = VexPurple, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Extrator de Vídeos", color = VexInk, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Motor original ${state.version} integrado ao Monitor • download + edição em timeline", color = VexMuted, fontSize = 10.5.sp)
                }
                VexStatusPill(
                    text = if (state.available && state.ffmpeg && state.ytDlp) "Núcleo pronto" else if (state.error.isNotBlank()) "Núcleo indisponível" else "Verificando...",
                    ok = state.available && state.ffmpeg && state.ytDlp
                )
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            VexTabButton("Download", Icons.Default.Download, tab == VexTab.DOWNLOAD, Modifier.weight(1f)) { tab = VexTab.DOWNLOAD }
            VexTabButton("Editor / Timeline", Icons.Default.Movie, tab == VexTab.EDITOR, Modifier.weight(1f)) { tab = VexTab.EDITOR }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            when (tab) {
                VexTab.DOWNLOAD -> VexDownloadScreen(state, onStateChange = { state = it }, onRefresh = { scope.launch { state = VexBridge.state() } })
                VexTab.EDITOR -> VexEditorScreen(state)
            }
        }
    }
}

@Composable
private fun VexTabButton(text: String, icon: ImageVector, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        color = if (selected) VexSoftBlue else Color.White,
        shape = RoundedCornerShape(11.dp),
        border = BorderStroke(1.dp, if (selected) VexBlue else VexBorder),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Row(Modifier.padding(vertical = 9.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = if (selected) VexBlue else VexMuted, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(7.dp)); Text(text, color = if (selected) VexBlue else VexInk, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
        }
    }
}

@Composable
private fun VexDownloadScreen(state: VexCoreState, onStateChange: (VexCoreState) -> Unit, onRefresh: () -> Unit) {
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf("") }
    var qualityIndex by remember { mutableIntStateOf(1) }
    val qualities = listOf("360p", "480p", "720p HD", "1080p Full HD", "Melhor disponível")
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf("Cole o link, escolha a qualidade e clique em Baixar vídeo.") }
    var statusOk by remember { mutableStateOf(true) }
    var proxyExpanded by remember { mutableStateOf(false) }
    var globoExpanded by remember { mutableStateOf(false) }
    var proxyEnabled by remember(state.proxyEnabled) { mutableStateOf(state.proxyEnabled) }
    var proxyServer by remember(state.proxyServer) { mutableStateOf(state.proxyServer) }
    var proxyPort by remember(state.proxyPort) { mutableStateOf(state.proxyPort) }
    var proxyUser by remember { mutableStateOf("") }
    var proxyPassword by remember { mutableStateOf("") }

    fun fail(text: String) { status = text; statusOk = false }
    fun openFolder() {
        runCatching {
            val folder = state.videoDir.takeIf { it.isNotBlank() }?.let(::File) ?: VexBridge.videoFolder()
            require(folder != null) { "Pasta de vídeos não encontrada." }
            folder.mkdirs(); Desktop.getDesktop().open(folder)
        }.onFailure { fail(it.message ?: "Não foi possível abrir a pasta.") }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(color = Color.White, shape = RoundedCornerShape(13.dp), border = BorderStroke(1.dp, VexBorder), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("DOWNLOAD DE VÍDEO", color = VexBlue, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                        Text("YouTube, Live, R7/Record, Globoplay e páginas compatíveis", color = VexInk, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                    VexStatusPill(if (proxyEnabled) "Proxy ativo" else "Conexão direta", true)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        modifier = Modifier.weight(1f),
                        label = { Text("Link do vídeo") },
                        placeholder = { Text("https://...") },
                        leadingIcon = { Icon(Icons.Default.Link, null) },
                        singleLine = true
                    )
                    VexSelector(qualities[qualityIndex], qualities, onSelect = { qualityIndex = it }, modifier = Modifier.width(180.dp))
                    Button(
                        onClick = {
                            if (!state.available) { fail("Núcleo do Extrator de Vídeos não está disponível no pacote."); return@Button }
                            busy = true; progress = 0; statusOk = true; status = "Preparando download..."
                            scope.launch {
                                val result = runCatching {
                                    VexBridge.download(url.trim(), qualityIndex, proxyEnabled, proxyServer, proxyPort, proxyUser, proxyPassword,
                                        onProgress = { progress = it }, onMessage = { if (it.isNotBlank()) status = it })
                                }.getOrElse { JSONObject().put("ok", false).put("error", it.message ?: "Falha no download.") }
                                busy = false
                                if (result.optBoolean("ok", false)) {
                                    progress = 100; statusOk = true
                                    val path = result.optString("result", "")
                                    status = if (path.isNotBlank()) "✓ Download concluído: ${File(path).name}" else "✓ Download concluído."
                                } else if (result.optBoolean("canceled", false)) {
                                    status = "Download cancelado."; statusOk = true
                                } else fail(result.optString("error", "Falha no download."))
                            }
                        },
                        enabled = !busy && url.trim().startsWith("http", true),
                        modifier = Modifier.height(56.dp)
                    ) {
                        if (busy) CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp, color = Color.White) else Icon(Icons.Default.Download, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp)); Text(if (busy) "Baixando..." else "Baixar vídeo")
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { VexBridge.cancel(); busy = false; status = "Cancelamento solicitado." }, enabled = busy) {
                        Icon(Icons.Default.Cancel, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(5.dp)); Text("Cancelar")
                    }
                    OutlinedButton(onClick = {
                        if (busy) return@OutlinedButton
                        busy = true; status = "Atualizando yt-dlp..."; statusOk = true
                        scope.launch {
                            val r = runCatching { VexBridge.updateYtDlp(proxyEnabled, proxyServer, proxyPort, proxyUser, proxyPassword) { if (it.isNotBlank()) status = it } }
                                .getOrElse { JSONObject().put("ok", false).put("error", it.message ?: "Falha ao atualizar yt-dlp.") }
                            busy = false
                            if (r.optBoolean("ok", false)) { status = "✓ yt-dlp atualizado."; statusOk = true; onRefresh() }
                            else fail(r.optString("error", "Falha ao atualizar yt-dlp."))
                        }
                    }, enabled = !busy && state.available) {
                        Icon(Icons.Default.Update, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(5.dp)); Text("Atualizar yt-dlp")
                    }
                    OutlinedButton(onClick = ::openFolder) {
                        Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(5.dp)); Text("Abrir pasta de vídeos")
                    }
                    Spacer(Modifier.weight(1f))
                    Text("MP4 • qualidade real escolhida", color = VexMuted, fontSize = 10.sp)
                }
                LinearProgressIndicator(progress = { (progress / 100f).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(7.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (statusOk) Icons.Default.Info else Icons.Default.Error, null, tint = if (statusOk) VexBlue else VexRed, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(7.dp)); Text(status, color = if (statusOk) VexInk else VexRed, fontSize = 10.5.sp, modifier = Modifier.weight(1f))
                    Text("$progress%", color = VexInk, fontWeight = FontWeight.Bold, fontSize = 10.5.sp)
                }
                if (!state.available && state.error.isNotBlank()) {
                    VexNotice(state.error, VexRed)
                } else {
                    Text(
                        "yt-dlp ${if (state.ytDlp) "OK" else "ausente"}  •  FFmpeg ${if (state.ffmpeg) "OK" else "ausente"}  •  FFprobe ${if (state.ffprobe) "OK" else "ausente"}  •  Deno ${if (state.deno) "OK" else "ausente"}",
                        color = VexMuted, fontSize = 9.5.sp
                    )
                }
            }
        }

        VexExpandableCard("Conexão / Proxy", if (proxyEnabled) "Proxy ${proxyServer.ifBlank { "configurado" }}:${proxyPort.ifBlank { "?" }}" else "Conexão direta", Icons.Default.VpnLock, proxyExpanded, { proxyExpanded = !proxyExpanded }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = proxyEnabled, onCheckedChange = { proxyEnabled = it }); Spacer(Modifier.width(8.dp))
                Text("Usar proxy", color = VexInk, fontWeight = FontWeight.Bold)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(proxyServer, { proxyServer = it }, label = { Text("Servidor") }, singleLine = true, modifier = Modifier.weight(1f), enabled = proxyEnabled)
                OutlinedTextField(proxyPort, { proxyPort = it }, label = { Text("Porta") }, singleLine = true, modifier = Modifier.width(130.dp), enabled = proxyEnabled)
                OutlinedTextField(proxyUser, { proxyUser = it }, label = { Text("Usuário") }, singleLine = true, modifier = Modifier.weight(0.8f), enabled = proxyEnabled)
                OutlinedTextField(proxyPassword, { proxyPassword = it }, label = { Text("Senha") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.weight(0.8f), enabled = proxyEnabled)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = {
                    scope.launch {
                        val r = VexBridge.saveProxy(proxyEnabled, proxyServer, proxyPort)
                        if (r.optBoolean("ok", false)) { status = "✓ Configuração de proxy salva. Usuário e senha permanecem apenas nesta sessão."; statusOk = true; onStateChange(state.copy(proxyEnabled = proxyEnabled, proxyServer = proxyServer, proxyPort = proxyPort)) }
                        else fail(r.optString("error", "Não foi possível salvar o proxy."))
                    }
                }, enabled = state.available) { Icon(Icons.Default.Save, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(5.dp)); Text("Salvar configuração") }
                Spacer(Modifier.width(10.dp)); Text("Usuário e senha do proxy não são gravados.", color = VexMuted, fontSize = 10.sp)
            }
        }

        VexExpandableCard("Globoplay", if (state.globoplaySession) "Sessão autenticada salva e protegida pelo Windows" else "Sessão não salva", Icons.Default.Lock, globoExpanded, { globoExpanded = !globoExpanded }) {
            VexNotice("O login é feito somente na página oficial do Globoplay. A senha não é recebida pelo Monitor. A sessão/cookies é protegida pelo Windows, como no Extrator original.", VexBlue)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    if (busy) return@Button
                    busy = true; status = "Abrindo login oficial do Globoplay..."; statusOk = true
                    scope.launch {
                        val r = runCatching { VexBridge.globoplayLogin(proxyEnabled, proxyServer, proxyPort, proxyUser, proxyPassword) }
                            .getOrElse { JSONObject().put("ok", false).put("error", it.message ?: "Falha no login Globoplay.") }
                        busy = false
                        if (r.optBoolean("ok", false)) { status = "✓ Sessão do Globoplay salva."; statusOk = true; onRefresh() }
                        else if (r.optString("error").isNotBlank()) fail(r.optString("error")) else { status = "Login do Globoplay fechado sem salvar."; statusOk = true }
                    }
                }, enabled = state.available && !busy) { Icon(Icons.Default.Login, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(5.dp)); Text("Entrar / atualizar sessão Globoplay") }
                OutlinedButton(onClick = {
                    scope.launch {
                        val r = VexBridge.deleteGloboplaySession()
                        if (r.optBoolean("ok", false)) { status = "Sessão Globoplay apagada."; statusOk = true; onRefresh() }
                        else fail(r.optString("error", "Falha ao apagar sessão."))
                    }
                }, enabled = state.available && state.globoplaySession) { Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(5.dp)); Text("Apagar sessão") }
            }
            Text("DRM, assinatura sem acesso, bloqueio regional e outras restrições continuam sendo respeitados; o aplicativo não tenta contornar proteção de conteúdo.", color = VexMuted, fontSize = 9.5.sp)
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun VexEditorScreen(state: VexCoreState) {
    val scope = rememberCoroutineScope()
    val clips = remember { mutableStateListOf<VexClip>() }
    val undo = remember { mutableStateListOf<VexEditorSnapshot>() }
    var selectedIndex by remember { mutableIntStateOf(-1) }
    var playheadMs by remember { mutableLongStateOf(0L) }
    var status by remember { mutableStateOf("Adicione um ou mais vídeos para começar.") }
    var statusOk by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }
    var outputName by remember { mutableStateOf("video_final.mp4") }
    var resolution by remember { mutableStateOf("Original") }
    var codec by remember { mutableStateOf("H.265 / HEVC") }
    var targetEnabled by remember { mutableStateOf(false) }
    var targetMb by remember { mutableFloatStateOf(30f) }
    var zoom by remember { mutableIntStateOf(3) }
    val preview = remember { VexFxPreview() }
    var textFocused by remember { mutableStateOf(false) }
    var sequencePlaying by remember { mutableStateOf(false) }
    var dragAccum by remember { mutableFloatStateOf(0f) }

    val selected = clips.getOrNull(selectedIndex)
    val totalDuration = clips.sumOf { it.selectedDurationMs }

    fun clipGlobalStart(index: Int): Long = clips.take(index.coerceAtLeast(0)).sumOf { it.selectedDurationMs }
    fun pushUndo() {
        undo += VexEditorSnapshot(clips.toList(), selectedIndex, playheadMs)
        if (undo.size > 50) undo.removeAt(0)
    }
    fun restoreUndo() {
        val snap = undo.removeLastOrNull() ?: run { status = "Nada para desfazer na timeline."; return }
        sequencePlaying = false; preview.pause(); clips.clear(); clips.addAll(snap.clips)
        selectedIndex = snap.selectedIndex.coerceIn(-1, clips.lastIndex); playheadMs = snap.playheadMs
        clips.getOrNull(selectedIndex)?.let { preview.load(it.path, (it.startMs + localSourceAtGlobal(selectedIndex, playheadMs, clips)).coerceIn(it.startMs, it.endMs)) }
        status = "Última alteração desfeita."; statusOk = true
    }
    fun select(index: Int, sourceMs: Long? = null) {
        if (index !in clips.indices) return
        selectedIndex = index
        val clip = clips[index]
        playheadMs = clipGlobalStart(index) + ((sourceMs ?: clip.startMs) - clip.startMs).coerceIn(0, clip.selectedDurationMs)
        preview.load(clip.path, (sourceMs ?: clip.startMs).coerceIn(clip.startMs, clip.endMs), false)
    }
    fun removeSelected() {
        if (selectedIndex !in clips.indices || busy) return
        pushUndo(); sequencePlaying = false; preview.pause(); clips.removeAt(selectedIndex)
        if (clips.isEmpty()) { selectedIndex = -1; playheadMs = 0; status = "Timeline vazia." }
        else { selectedIndex = selectedIndex.coerceAtMost(clips.lastIndex); select(selectedIndex) }
    }
    fun reorder(from: Int, to: Int) {
        if (from !in clips.indices || to !in clips.indices || from == to || busy) return
        pushUndo(); val item = clips.removeAt(from); clips.add(to, item); selectedIndex = to; playheadMs = clipGlobalStart(to); preview.load(item.path, item.startMs)
        status = "Ordem alterada: clipe movido para a posição ${to + 1}."; statusOk = true
    }
    fun updateSelected(transform: (VexClip) -> VexClip, label: String) {
        if (selectedIndex !in clips.indices || busy) return
        pushUndo(); clips[selectedIndex] = transform(clips[selectedIndex]); val c = clips[selectedIndex]
        playheadMs = clipGlobalStart(selectedIndex); preview.load(c.path, c.startMs); status = label; statusOk = true
    }

    DisposableEffect(Unit) { onDispose { preview.dispose(); VexBridge.cancel() } }

    LaunchedEffect(selectedIndex, sequencePlaying, clips.size) {
        while (true) {
            delay(90)
            val idx = selectedIndex
            val clip = clips.getOrNull(idx) ?: continue
            if (preview.loadedPath == clip.path) {
                val source = preview.currentMs.coerceIn(0, clip.durationMs)
                playheadMs = clipGlobalStart(idx) + (source - clip.startMs).coerceIn(0, clip.selectedDurationMs)
                if (sequencePlaying && source >= clip.endMs - 45) {
                    if (idx < clips.lastIndex) {
                        selectedIndex = idx + 1
                        val next = clips[idx + 1]
                        playheadMs = clipGlobalStart(idx + 1)
                        preview.load(next.path, next.startMs, true)
                    } else {
                        sequencePlaying = false; preview.pause(); playheadMs = totalDuration
                    }
                }
            }
        }
    }

    Column(
        Modifier.fillMaxSize()
            .verticalScroll(rememberScrollState())
            .onPreviewKeyEvent { e ->
                if (textFocused || e.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                when {
                    e.key == Key.Delete -> { removeSelected(); true }
                    e.isCtrlPressed && e.key == Key.Z -> { restoreUndo(); true }
                    else -> false
                }
            },
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(color = Color.White, shape = RoundedCornerShape(13.dp), border = BorderStroke(1.dp, VexBorder), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("EDITOR DE VÍDEO", color = VexPurple, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                    Text(selected?.let { File(it.path).name } ?: "Nenhum vídeo selecionado", color = VexInk, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (selected != null) Text("${vexTime(selected.selectedDurationMs)} • ${selected.width}×${selected.height} • ${"%.2f".format(selected.fps)} fps${if (selected.hasAudio) " • áudio" else " • sem áudio"}", color = VexMuted, fontSize = 9.5.sp)
                }
                OutlinedButton(onClick = {
                    if (busy || !state.available) return@OutlinedButton
                    val files = pickVideoFiles(state.videoDir)
                    if (files.isEmpty()) return@OutlinedButton
                    status = "Analisando ${files.size} vídeo(s)..."; statusOk = true
                    scope.launch {
                        for (file in files) {
                            runCatching { VexBridge.probe(file.absolutePath) }
                                .onSuccess { clip -> clips += clip; if (selectedIndex < 0) select(0) }
                                .onFailure { status = it.message ?: "Falha ao analisar ${file.name}."; statusOk = false }
                        }
                        if (clips.isNotEmpty()) { status = "Timeline pronta: ${clips.size} clipe(s) • ${vexTime(totalDuration)}"; statusOk = true }
                    }
                }, enabled = state.available && !busy) { Icon(Icons.Default.VideoFile, null, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(6.dp)); Text("Abrir vídeo") }
                Spacer(Modifier.width(7.dp))
                OutlinedButton(onClick = ::restoreUndo, enabled = undo.isNotEmpty() && !busy) {
                    Icon(Icons.Default.Undo, null, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(6.dp)); Text("Voltar / Desfazer (Ctrl+Z)")
                }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
            Surface(color = Color(0xFF071426), shape = RoundedCornerShape(13.dp), border = BorderStroke(1.dp, Color(0xFF1A355A)), modifier = Modifier.weight(0.68f).height(390.dp)) {
                Column(Modifier.fillMaxSize()) {
                    VexPreviewSurface(preview, Modifier.weight(1f).fillMaxWidth())
                    Row(Modifier.fillMaxWidth().background(Color(0xFF0B1C31)).padding(8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                        OutlinedButton(onClick = { val c = clips.getOrNull(selectedIndex) ?: return@OutlinedButton; preview.seek((preview.currentMs - 5000).coerceAtLeast(c.startMs)); playheadMs = clipGlobalStart(selectedIndex) + (preview.currentMs - c.startMs).coerceAtLeast(0) }, enabled = selected != null) { Text("−5s") }
                        Button(onClick = {
                            val c = clips.getOrNull(selectedIndex) ?: return@Button
                            if (preview.playing) { sequencePlaying = false; preview.pause() }
                            else { if (preview.currentMs >= c.endMs - 50) preview.seek(c.startMs); sequencePlaying = true; preview.play() }
                        }, enabled = selected != null) { Icon(if (preview.playing) Icons.Default.Pause else Icons.Default.PlayArrow, null, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(5.dp)); Text(if (preview.playing) "Pausar" else "Reproduzir") }
                        OutlinedButton(onClick = { val c = clips.getOrNull(selectedIndex) ?: return@OutlinedButton; preview.seek((preview.currentMs + 5000).coerceAtMost(c.endMs)) }, enabled = selected != null) { Text("+5s") }
                        Spacer(Modifier.weight(1f)); Text("${vexTime(playheadMs)} / ${vexTime(totalDuration)}", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Surface(color = Color.White, shape = RoundedCornerShape(13.dp), border = BorderStroke(1.dp, VexBorder), modifier = Modifier.weight(0.32f)) {
                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("MARCAÇÃO RÁPIDA", color = VexPurple, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                    if (selected == null) Text("Selecione um clipe na timeline.", color = VexMuted, fontSize = 10.sp)
                    else {
                        var startText by remember(selected.path, selected.startMs) { mutableStateOf(vexTime(selected.startMs)) }
                        var endText by remember(selected.path, selected.endMs) { mutableStateOf(vexTime(selected.endMs)) }
                        OutlinedTextField(startText, { startText = it }, label = { Text("Início") }, modifier = Modifier.fillMaxWidth().onFocusChanged { textFocused = it.isFocused }, singleLine = true)
                        OutlinedTextField(endText, { endText = it }, label = { Text("Fim") }, modifier = Modifier.fillMaxWidth().onFocusChanged { textFocused = it.isFocused }, singleLine = true)
                        Text("Duração: ${vexTime(selected.selectedDurationMs)}", color = VexMuted, fontSize = 10.sp)
                        Button(onClick = {
                            val s = runCatching { vexParseTime(startText) }.getOrElse { status = it.message ?: "Tempo inválido."; statusOk = false; return@Button }
                            val e = runCatching { vexParseTime(endText) }.getOrElse { status = it.message ?: "Tempo inválido."; statusOk = false; return@Button }
                            if (e <= s || e > selected.durationMs) { status = "O fim precisa ser maior que o início e não pode ultrapassar o vídeo."; statusOk = false; return@Button }
                            updateSelected({ it.copy(startMs = s, endMs = e) }, "Trecho atualizado na timeline.")
                        }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Done, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(5.dp)); Text("Aplicar tempos") }
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(onClick = { val p = preview.currentMs.coerceIn(0, selected.endMs - 1); updateSelected({ it.copy(startMs = p) }, "Início marcado em ${vexTime(p)}") }, modifier = Modifier.weight(1f)) { Text("Início aqui", fontSize = 9.5.sp) }
                            OutlinedButton(onClick = { val p = preview.currentMs.coerceIn(selected.startMs + 1, selected.durationMs); updateSelected({ it.copy(endMs = p) }, "Fim marcado em ${vexTime(p)}") }, modifier = Modifier.weight(1f)) { Text("Fim aqui", fontSize = 9.5.sp) }
                        }
                    }
                }
            }
        }

        Surface(color = Color.White, shape = RoundedCornerShape(13.dp), border = BorderStroke(1.dp, VexBorder), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("TIMELINE", color = VexPurple, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                    Spacer(Modifier.width(8.dp)); Text("${clips.size} clipe(s) • ${vexTime(totalDuration)}", color = VexMuted, fontSize = 9.5.sp)
                    Spacer(Modifier.weight(1f)); Text("Cursor ${vexTime(playheadMs)}", color = VexBlue, fontWeight = FontWeight.Bold, fontSize = 9.5.sp)
                    Spacer(Modifier.width(8.dp)); IconButton(onClick = { zoom = (zoom - 1).coerceAtLeast(1) }) { Icon(Icons.Default.Remove, "Menos zoom") }
                    Text("Zoom $zoom/6", color = VexMuted, fontSize = 9.5.sp)
                    IconButton(onClick = { zoom = (zoom + 1).coerceAtMost(6) }) { Icon(Icons.Default.Add, "Mais zoom") }
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).height(100.dp), horizontalArrangement = Arrangement.spacedBy(7.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (clips.isEmpty()) {
                        Box(Modifier.width(720.dp).height(82.dp).background(VexSoftBlue, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) { Text("Adicione vídeos para montar a timeline", color = VexMuted) }
                    } else clips.forEachIndexed { index, clip ->
                        val width = (110 + (clip.selectedDurationMs / 1000.0 * zoom * 0.55)).coerceIn(125.0, 360.0).dp
                        Surface(
                            color = if (index == selectedIndex) VexSoftPurple else VexSoftBlue,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(if (index == selectedIndex) 2.dp else 1.dp, if (index == selectedIndex) VexPurple else VexBorder),
                            modifier = Modifier.width(width).height(82.dp)
                                .pointerInput(index, clips.size, busy) {
                                    if (!busy) detectDragGestures(
                                        onDragStart = { dragAccum = 0f },
                                        onDrag = { change, amount -> change.consume(); dragAccum += amount.x },
                                        onDragEnd = {
                                            val steps = (dragAccum / 145f).roundToInt()
                                            if (steps != 0) reorder(index, (index + steps).coerceIn(0, clips.lastIndex))
                                            else select(index)
                                            dragAccum = 0f
                                        }
                                    )
                                }
                                .clickable(enabled = !busy) { select(index) }
                        ) {
                            Column(Modifier.padding(9.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text("${index + 1}. ${File(clip.path).name}", color = VexInk, fontWeight = FontWeight.Bold, fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Spacer(Modifier.weight(1f)); Text("${vexTime(clip.startMs)} → ${vexTime(clip.endMs)}", color = VexMuted, fontSize = 8.5.sp)
                                Text("≡ arraste para reordenar", color = if (index == selectedIndex) VexPurple else VexBlue, fontSize = 8.5.sp)
                            }
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    listOf(-1000L to "−1s", -100L to "−100ms", -10L to "−10ms", 10L to "+10ms", 100L to "+100ms", 1000L to "+1s").forEach { (delta, label) ->
                        OutlinedButton(onClick = { val c = clips.getOrNull(selectedIndex) ?: return@OutlinedButton; val p = (preview.currentMs + delta).coerceIn(c.startMs, c.endMs); preview.seek(p); playheadMs = clipGlobalStart(selectedIndex) + p - c.startMs }, contentPadding = PaddingValues(horizontal = 9.dp, vertical = 5.dp), enabled = selected != null && !busy) { Text(label, fontSize = 9.sp) }
                    }
                    Spacer(Modifier.weight(1f))
                    OutlinedButton(onClick = { if (selectedIndex > 0) reorder(selectedIndex, selectedIndex - 1) }, enabled = selectedIndex > 0 && !busy) { Icon(Icons.Default.ArrowBack, null, modifier = Modifier.size(15.dp)); Spacer(Modifier.width(4.dp)); Text("Esquerda", fontSize = 9.5.sp) }
                    OutlinedButton(onClick = { if (selectedIndex in 0 until clips.lastIndex) reorder(selectedIndex, selectedIndex + 1) }, enabled = selectedIndex in 0 until clips.lastIndex && !busy) { Text("Direita", fontSize = 9.5.sp); Spacer(Modifier.width(4.dp)); Icon(Icons.Default.ArrowForward, null, modifier = Modifier.size(15.dp)) }
                    OutlinedButton(onClick = { val c = clips.getOrNull(selectedIndex) ?: return@OutlinedButton; pushUndo(); clips.add(selectedIndex + 1, c.copy(cutBefore = false)); selectedIndex += 1; status = "Clipe duplicado." }, enabled = selected != null && !busy) { Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(15.dp)); Spacer(Modifier.width(4.dp)); Text("Duplicar", fontSize = 9.5.sp) }
                    Button(onClick = {
                        val c = clips.getOrNull(selectedIndex) ?: return@Button
                        val source = preview.currentMs.coerceIn(c.startMs, c.endMs)
                        if (source <= c.startMs + 40 || source >= c.endMs - 40) { status = "Mova o cursor para dentro do trecho antes de cortar."; statusOk = false; return@Button }
                        pushUndo(); val left = c.copy(endMs = source); val right = c.copy(startMs = source, cutBefore = true); clips[selectedIndex] = left; clips.add(selectedIndex + 1, right); selectedIndex += 1; select(selectedIndex); status = "Corte criado em ${vexTime(source)}."; statusOk = true
                    }, enabled = selected != null && !busy) { Icon(Icons.Default.ContentCut, null, modifier = Modifier.size(15.dp)); Spacer(Modifier.width(4.dp)); Text("Cortar", fontSize = 9.5.sp) }
                    OutlinedButton(onClick = ::removeSelected, enabled = selected != null && !busy, colors = ButtonDefaults.outlinedButtonColors(contentColor = VexRed)) { Icon(Icons.Default.Delete, null, modifier = Modifier.size(15.dp)); Spacer(Modifier.width(4.dp)); Text("Excluir trecho", fontSize = 9.5.sp) }
                }
            }
        }

        Surface(color = Color.White, shape = RoundedCornerShape(13.dp), border = BorderStroke(1.dp, VexBorder), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("EXPORTAÇÃO", color = VexPurple, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(outputName, { outputName = it }, label = { Text("Nome do arquivo") }, modifier = Modifier.weight(1f).onFocusChanged { textFocused = it.isFocused }, singleLine = true)
                    VexStringSelector(resolution, listOf("Original", "360p", "480p", "720p", "1080p"), { resolution = it }, Modifier.width(155.dp))
                    VexStringSelector(codec, listOf("H.264 / AVC", "H.265 / HEVC"), { codec = it }, Modifier.width(165.dp))
                    Button(onClick = {
                        if (clips.isEmpty() || busy) return@Button
                        busy = true; progress = 0; statusOk = true; status = "Preparando timeline para exportação..."; sequencePlaying = false; preview.pause()
                        scope.launch {
                            val r = runCatching { VexBridge.export(clips.toList(), outputName, resolution, codec, targetEnabled, targetMb.roundToInt(), { progress = it }, { if (it.isNotBlank()) status = it }) }
                                .getOrElse { JSONObject().put("ok", false).put("error", it.message ?: "Falha na exportação.") }
                            busy = false
                            if (r.optBoolean("ok", false)) {
                                progress = 100; statusOk = true
                                val result = r.optJSONObject("result")
                                val path = result?.optString("path", "").orEmpty()
                                val actual = result?.optDouble("actual_mb", 0.0) ?: 0.0
                                status = "✓ Exportação concluída: ${if (path.isNotBlank()) File(path).name else outputName}${if (actual > 0) " • ${"%.1f".format(actual)} MB" else ""}. Originais preservados."
                            } else { status = r.optString("error", "Falha na exportação."); statusOk = false }
                        }
                    }, enabled = clips.isNotEmpty() && !busy && state.available, modifier = Modifier.height(56.dp)) {
                        if (busy) CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp, color = Color.White) else Icon(Icons.Default.Upload, null, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp)); Text(if (busy) "Exportando..." else "Exportar")
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = targetEnabled, onCheckedChange = { targetEnabled = it })
                    Text("Definir tamanho aproximado", color = VexInk, fontSize = 10.5.sp)
                    if (targetEnabled) {
                        Spacer(Modifier.width(10.dp)); Slider(value = targetMb, onValueChange = { targetMb = it }, valueRange = 1f..1000f, modifier = Modifier.width(330.dp))
                        Text("≈ ${targetMb.roundToInt()} MB", color = VexBlue, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                    }
                    Spacer(Modifier.weight(1f))
                    OutlinedButton(onClick = {
                        runCatching {
                            val folder = state.videoDir.takeIf { it.isNotBlank() }?.let(::File) ?: VexBridge.videoFolder()
                            require(folder != null) { "Pasta de vídeos não encontrada." }; folder.mkdirs(); Desktop.getDesktop().open(folder)
                        }.onFailure { status = it.message ?: "Falha ao abrir pasta."; statusOk = false }
                    }) { Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(15.dp)); Spacer(Modifier.width(5.dp)); Text("Abrir pasta") }
                }
                LinearProgressIndicator(progress = { (progress / 100f).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(7.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (statusOk) Icons.Default.Info else Icons.Default.Error, null, tint = if (statusOk) VexBlue else VexRed, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(7.dp))
                    Text(status, color = if (statusOk) VexInk else VexRed, fontSize = 10.5.sp, modifier = Modifier.weight(1f)); Text("$progress%", color = VexInk, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                }
                Text("Atalhos preservados: Delete exclui o trecho selecionado • Ctrl+Z desfaz ações na timeline. H.265 usa H.264 automaticamente se o encoder HEVC não estiver disponível.", color = VexMuted, fontSize = 9.5.sp)
            }
        }
        Spacer(Modifier.height(4.dp))
    }
}

private fun localSourceAtGlobal(index: Int, globalMs: Long, clips: List<VexClip>): Long {
    val before = clips.take(index.coerceAtLeast(0)).sumOf { it.selectedDurationMs }
    return (globalMs - before).coerceIn(0, clips.getOrNull(index)?.selectedDurationMs ?: 0L)
}

private fun pickVideoFiles(defaultDir: String): List<File> {
    val dialog = FileDialog(null as Frame?, "Adicionar vídeos à timeline", FileDialog.LOAD).apply {
        isMultipleMode = true
        directory = defaultDir.takeIf { it.isNotBlank() && File(it).isDirectory }
        filenameFilter = java.io.FilenameFilter { _, name -> name.substringAfterLast('.', "").lowercase() in setOf("mp4", "mkv", "webm", "mov", "avi", "m4v") }
        isVisible = true
    }
    return dialog.files?.toList().orEmpty()
}

private fun vexTime(ms: Long): String {
    val safe = ms.coerceAtLeast(0)
    val millis = safe % 1000
    val total = safe / 1000
    val sec = total % 60
    val min = (total / 60) % 60
    val hour = total / 3600
    return "%02d:%02d:%02d.%03d".format(hour, min, sec, millis)
}

private fun vexParseTime(text: String): Long {
    val parts = text.trim().replace(',', '.').split(':')
    require(parts.size in 2..3) { "Use MM:SS.mmm ou HH:MM:SS.mmm" }
    val h: Long; val m: Long; val s: Double
    if (parts.size == 2) { h = 0; m = parts[0].toLong(); s = parts[1].toDouble() }
    else { h = parts[0].toLong(); m = parts[1].toLong(); s = parts[2].toDouble() }
    require(h >= 0 && m in 0..59 && s >= 0 && s < 60) { "Tempo inválido." }
    return ((h * 3600 + m * 60) * 1000 + s * 1000).roundToInt().toLong()
}

@Composable
private fun VexSelector(selected: String, options: List<String>, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Icon(Icons.Default.HighQuality, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(5.dp)); Text(selected, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.weight(1f)); Icon(Icons.Default.ArrowDropDown, null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEachIndexed { i, value -> DropdownMenuItem(text = { Text(value) }, onClick = { open = false; onSelect(i) }) }
        }
    }
}

@Composable
private fun VexStringSelector(selected: String, options: List<String>, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text(selected, maxLines = 1, overflow = TextOverflow.Ellipsis); Spacer(Modifier.weight(1f)); Icon(Icons.Default.ArrowDropDown, null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { value -> DropdownMenuItem(text = { Text(value) }, onClick = { open = false; onSelect(value) }) }
        }
    }
}

@Composable
private fun VexExpandableCard(title: String, subtitle: String, icon: ImageVector, expanded: Boolean, onToggle: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = Color.White, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, VexBorder), modifier = Modifier.fillMaxWidth()) {
        Column {
            TextButton(onClick = onToggle, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 13.dp, vertical = 8.dp)) {
                Icon(icon, null, tint = VexBlue, modifier = Modifier.size(19.dp)); Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) { Text(title, color = VexInk, fontWeight = FontWeight.Bold, fontSize = 11.5.sp); Text(subtitle, color = VexMuted, fontSize = 9.5.sp) }
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = VexMuted)
            }
            if (expanded) { HorizontalDivider(color = VexBorder); Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content) }
        }
    }
}

@Composable
private fun VexNotice(text: String, accent: Color) {
    Surface(color = if (accent == VexRed) Color(0xFFFFEEF0) else VexSoftBlue, shape = RoundedCornerShape(9.dp), border = BorderStroke(1.dp, if (accent == VexRed) Color(0xFFF0BFC7) else VexBorder), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(9.dp), verticalAlignment = Alignment.Top) { Icon(Icons.Default.Info, null, tint = accent, modifier = Modifier.size(15.dp)); Spacer(Modifier.width(7.dp)); Text(text, color = VexInk, fontSize = 9.5.sp, modifier = Modifier.weight(1f)) }
    }
}

@Composable
private fun VexStatusPill(text: String, ok: Boolean) {
    Surface(color = if (ok) Color(0xFFEAF9F3) else Color(0xFFFFEEF0), shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, if (ok) Color(0xFFBDEBD8) else Color(0xFFF0BFC7))) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (ok) Icons.Default.CheckCircle else Icons.Default.Error, null, tint = if (ok) VexGreen else VexRed, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(5.dp)); Text(text, color = VexInk, fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
