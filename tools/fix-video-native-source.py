from pathlib import Path

path = Path('desktop/src/main/kotlin/br/com/monitordenoticias/desktop/NativeVideoExtractor.kt')
text = path.read_text(encoding='utf-8')
text = text.replace('Icons.Default.MovieEdit', 'Icons.Default.Movie')

old_class = r'''private class VexFxPreview {
    val panel = JFXPanel()
    private var player: MediaPlayer? = null
    @Volatile var currentMs: Long = 0L
        private set
    @Volatile var durationMs: Long = 0L
        private set
    @Volatile var playing: Boolean = false
        private set
    @Volatile var loadedPath: String = ""
        private set

    init {
        Platform.setImplicitExit(false)
        Platform.runLater {
            panel.scene = Scene(StackPane().apply { style = "-fx-background-color: #06111f;" })
        }
    }

    fun load(path: String, positionMs: Long = 0L, autoplay: Boolean = false) {
        if (path.isBlank()) return
        Platform.runLater {
            runCatching {
                player?.stop(); player?.dispose()
                val media = Media(File(path).toURI().toString())
                val mp = MediaPlayer(media)
                val view = MediaView(mp).apply {
                    isPreserveRatio = true
                    fitWidth = 900.0
                    fitHeight = 500.0
                }
                panel.scene = Scene(StackPane(view).apply { style = "-fx-background-color: #06111f;" })
                player = mp
                loadedPath = path
                mp.setOnReady {
                    durationMs = mp.totalDuration.toMillis().toLong().coerceAtLeast(0L)
                    mp.seek(javafx.util.Duration.millis(positionMs.toDouble()))
                    if (autoplay) mp.play()
                }
                mp.currentTimeProperty().addListener { _, _, value -> currentMs = value.toMillis().toLong().coerceAtLeast(0L) }
                mp.statusProperty().addListener { _, _, value -> playing = value == MediaPlayer.Status.PLAYING }
            }
        }
    }

    fun play() = Platform.runLater { player?.play() }
    fun pause() = Platform.runLater { player?.pause() }
    fun seek(ms: Long) = Platform.runLater { player?.seek(javafx.util.Duration.millis(ms.coerceAtLeast(0L).toDouble())) }
    fun toggle() = Platform.runLater { if (player?.status == MediaPlayer.Status.PLAYING) player?.pause() else player?.play() }
    fun dispose() = Platform.runLater { player?.stop(); player?.dispose(); player = null }
}'''

new_class = r'''private class VexFxPreview {
    @Volatile private var panel: JFXPanel? = null
    private var player: MediaPlayer? = null
    @Volatile var currentMs: Long = 0L
        private set
    @Volatile var durationMs: Long = 0L
        private set
    @Volatile var playing: Boolean = false
        private set
    @Volatile var loadedPath: String = ""
        private set

    /**
     * SwingPanel invokes its factory on Swing's EDT. JFXPanel must be created there;
     * constructing it eagerly from the Compose render thread can deadlock AWT/JavaFX
     * when the user first opens Editor / Timeline.
     */
    fun createPanel(): JFXPanel {
        val created = JFXPanel()
        panel = created
        Platform.setImplicitExit(false)
        Platform.runLater {
            if (panel === created) {
                created.scene = Scene(StackPane().apply { style = "-fx-background-color: #06111f;" })
            }
        }
        return created
    }

    fun load(path: String, positionMs: Long = 0L, autoplay: Boolean = false) {
        if (path.isBlank()) return
        val targetPanel = panel ?: return
        Platform.runLater {
            if (panel !== targetPanel) return@runLater
            runCatching {
                player?.stop()
                player?.dispose()
                val media = Media(File(path).toURI().toString())
                val mp = MediaPlayer(media)
                val view = MediaView(mp).apply {
                    isPreserveRatio = true
                    fitWidth = 900.0
                    fitHeight = 500.0
                }
                targetPanel.scene = Scene(StackPane(view).apply { style = "-fx-background-color: #06111f;" })
                player = mp
                loadedPath = path
                mp.setOnReady {
                    durationMs = mp.totalDuration.toMillis().toLong().coerceAtLeast(0L)
                    mp.seek(javafx.util.Duration.millis(positionMs.toDouble()))
                    if (autoplay) mp.play()
                }
                mp.currentTimeProperty().addListener { _, _, value -> currentMs = value.toMillis().toLong().coerceAtLeast(0L) }
                mp.statusProperty().addListener { _, _, value -> playing = value == MediaPlayer.Status.PLAYING }
            }
        }
    }

    fun play() = Platform.runLater { player?.play() }
    fun pause() = Platform.runLater { player?.pause() }
    fun seek(ms: Long) = Platform.runLater { player?.seek(javafx.util.Duration.millis(ms.coerceAtLeast(0L).toDouble())) }
    fun toggle() = Platform.runLater { if (player?.status == MediaPlayer.Status.PLAYING) player?.pause() else player?.play() }
    fun dispose() {
        panel = null
        Platform.runLater {
            player?.stop()
            player?.dispose()
            player = null
            loadedPath = ""
            currentMs = 0L
            durationMs = 0L
            playing = false
        }
    }
}'''

if old_class in text:
    text = text.replace(old_class, new_class)
elif 'fun createPanel(): JFXPanel' not in text:
    raise SystemExit('ERRO: bloco VexFxPreview esperado nao foi encontrado; patch abortado para nao corromper o fonte.')

old_panel = 'SwingPanel(factory = { preview.panel }, modifier = Modifier.weight(1f).fillMaxWidth())'
new_panel = 'SwingPanel(factory = { preview.createPanel() }, modifier = Modifier.weight(1f).fillMaxWidth())'
if old_panel in text:
    text = text.replace(old_panel, new_panel)
elif new_panel not in text:
    raise SystemExit('ERRO: SwingPanel do editor nao foi encontrado; patch abortado.')

path.write_text(text, encoding='utf-8')
print('Native video source compatibility + JavaFX editor deadlock fix applied.')
