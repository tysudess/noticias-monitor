from pathlib import Path
import re

path = Path('desktop/src/main/kotlin/br/com/monitordenoticias/desktop/NativeVideoExtractor.kt')
text = path.read_text(encoding='utf-8')
text = text.replace('Icons.Default.MovieEdit', 'Icons.Default.Movie')

# The previous implementation embedded JFXPanel inside Compose SwingPanel. On Windows this can
# capture/block the Compose event loop when Editor / Timeline is opened. Keep JavaFX only as the
# media decoder/audio engine and render snapshots with a normal Compose Image instead.
text = text.replace('import androidx.compose.ui.awt.SwingPanel\n', '')
text = text.replace('import javafx.embed.swing.JFXPanel\n', '')

imports = {
    'import androidx.compose.foundation.Image\n': 'import androidx.compose.foundation.BorderStroke\n',
    'import androidx.compose.ui.graphics.ImageBitmap\n': 'import androidx.compose.ui.graphics.Color\n',
    'import androidx.compose.ui.graphics.toComposeImageBitmap\n': 'import androidx.compose.ui.graphics.ImageBitmap\n',
    'import androidx.compose.ui.layout.ContentScale\n': 'import androidx.compose.ui.input.pointer.pointerInput\n',
    'import javafx.animation.KeyFrame\n': 'import javafx.application.Platform\n',
    'import javafx.animation.Timeline\n': 'import javafx.animation.KeyFrame\n',
    'import javafx.embed.swing.SwingFXUtils\n': 'import javafx.animation.Timeline\n',
    'import javafx.scene.image.WritableImage\n': 'import javafx.scene.Scene\n',
    'import java.awt.image.BufferedImage\n': 'import java.awt.Frame\n',
    'import java.util.concurrent.CompletableFuture\n': 'import java.io.File\n',
    'import java.util.concurrent.atomic.AtomicBoolean\n': 'import java.util.concurrent.CompletableFuture\n',
}
for wanted, anchor in imports.items():
    if wanted not in text:
        if anchor not in text:
            raise SystemExit(f'ERRO: import anchor ausente: {anchor.strip()}')
        text = text.replace(anchor, anchor + wanted, 1)

new_preview = r'''private object VexFxRuntime {
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

                val timer = Timeline(KeyFrame(javafx.util.Duration.millis(125.0)) { captureFrameFx() }).apply {
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
        Timeline(KeyFrame(javafx.util.Duration.millis(80.0)) { captureFrameFx() }).play()
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
}'''

pattern = re.compile(r'private (?:object VexFxRuntime \{.*?\n\}\n\n)?class VexFxPreview \{.*?\n\}\n(?:\n@Composable\nprivate fun VexPreviewSurface\(.*?\n\}\n)?\nprivate enum class VexTab', re.S)
match = pattern.search(text)
if not match:
    raise SystemExit('ERRO: bloco de preview do Editor/Timeline nao foi encontrado; patch abortado para nao corromper o fonte.')
text = text[:match.start()] + new_preview + '\n\nprivate enum class VexTab' + text[match.end():]

panel_variants = [
    'SwingPanel(factory = { preview.createPanel() }, modifier = Modifier.weight(1f).fillMaxWidth())',
    'SwingPanel(factory = { preview.panel }, modifier = Modifier.weight(1f).fillMaxWidth())',
]
replaced = False
for old in panel_variants:
    if old in text:
        text = text.replace(old, 'VexPreviewSurface(preview, Modifier.weight(1f).fillMaxWidth())')
        replaced = True
if not replaced and 'VexPreviewSurface(preview, Modifier.weight(1f).fillMaxWidth())' not in text:
    raise SystemExit('ERRO: area visual do editor nao foi encontrada; patch abortado.')

if 'SwingPanel(' in text or 'JFXPanel' in text:
    raise SystemExit('ERRO: componente Swing/JavaFX bloqueante ainda existe no fonte.')

path.write_text(text, encoding='utf-8')
print('Editor/Timeline: SwingPanel/JFXPanel removido; preview agora usa Compose com JavaFX off-screen nao bloqueante.')
