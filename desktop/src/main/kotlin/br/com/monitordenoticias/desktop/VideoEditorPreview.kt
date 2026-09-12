package br.com.monitordenoticias.desktop

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.graphics.Color
import javafx.application.Platform
import javafx.embed.swing.JFXPanel
import javafx.scene.Scene
import javafx.scene.layout.StackPane
import javafx.scene.media.Media
import javafx.scene.media.MediaPlayer
import javafx.scene.media.MediaView
import javafx.util.Duration
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean
import javax.swing.SwingUtilities

private object VideoEditorJavaFxToolkit {
    private val initialized = AtomicBoolean(false)
    private val lock = Any()

    fun ensureStarted() {
        if (initialized.get()) return
        synchronized(lock) {
            if (initialized.get()) return
            val startToolkit = Runnable { JFXPanel() }
            if (SwingUtilities.isEventDispatchThread()) {
                startToolkit.run()
            } else {
                SwingUtilities.invokeAndWait(startToolkit)
            }
            Platform.runLater { Platform.setImplicitExit(false) }
            initialized.set(true)
        }
    }
}

internal class VideoEditorPreviewController {
    @Volatile private var panel: JFXPanel? = null
    @Volatile private var player: MediaPlayer? = null

    var onReady: ((Long) -> Unit)? = null
    var onPosition: ((Long) -> Unit)? = null
    var onPlayingChanged: ((Boolean) -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    fun attach(target: JFXPanel) {
        VideoEditorJavaFxToolkit.ensureStarted()
        target.background = java.awt.Color.BLACK
        target.isOpaque = true
        target.isDoubleBuffered = true
        panel = target
        Platform.runLater {
            Platform.setImplicitExit(false)
            if (target.scene == null) {
                target.scene = Scene(StackPane().apply { style = "-fx-background-color: #02060B;" }, 960.0, 540.0, javafx.scene.paint.Color.BLACK)
            }
        }
    }

    fun load(file: File) {
        VideoEditorJavaFxToolkit.ensureStarted()
        Platform.runLater {
            runCatching {
                val target = panel ?: error("Painel de preview ainda não foi inicializado.")
                target.background = java.awt.Color.BLACK
                target.isOpaque = true

                player?.stop()
                player?.dispose()

                val media = Media(file.toURI().toString())
                val next = MediaPlayer(media)
                val view = MediaView(next).apply {
                    isPreserveRatio = true
                    isSmooth = true
                }
                val root = StackPane().apply {
                    style = "-fx-background-color: #02060B;"
                    children.add(view)
                }
                view.fitWidthProperty().bind(root.widthProperty())
                view.fitHeightProperty().bind(root.heightProperty())
                val scene = Scene(root, 960.0, 540.0, javafx.scene.paint.Color.BLACK).apply {
                    fill = javafx.scene.paint.Color.BLACK
                }
                target.scene = scene
                target.revalidate()
                target.repaint()
                player = next

                next.setOnReady {
                    val total = next.totalDuration.toMillis().toLong().coerceAtLeast(0L)
                    next.seek(Duration.ZERO)
                    dispatch { onReady?.invoke(total) }
                }
                next.currentTimeProperty().addListener { _, _, value ->
                    dispatch { onPosition?.invoke(value.toMillis().toLong().coerceAtLeast(0L)) }
                }
                next.setOnPlaying { dispatch { onPlayingChanged?.invoke(true) } }
                next.setOnPaused { dispatch { onPlayingChanged?.invoke(false) } }
                next.setOnStopped { dispatch { onPlayingChanged?.invoke(false) } }
                next.setOnEndOfMedia { dispatch { onPlayingChanged?.invoke(false) } }
                next.setOnError {
                    dispatch { onError?.invoke(next.error?.message ?: "Falha no player de vídeo.") }
                }
                media.errorProperty().addListener { _, _, error ->
                    if (error != null) dispatch { onError?.invoke(error.message ?: "Falha ao abrir a mídia.") }
                }
            }.onFailure { error ->
                dispatch { onError?.invoke(error.message ?: "Falha ao carregar o preview.") }
            }
        }
    }

    fun play() {
        VideoEditorJavaFxToolkit.ensureStarted()
        Platform.runLater {
            val next = player
            if (next == null) {
                dispatch { onError?.invoke("Nenhum preview carregado para reproduzir.") }
            } else {
                next.play()
            }
        }
    }

    fun pause() {
        VideoEditorJavaFxToolkit.ensureStarted()
        Platform.runLater { player?.pause() }
    }

    fun seek(positionMs: Long) {
        VideoEditorJavaFxToolkit.ensureStarted()
        Platform.runLater { player?.seek(Duration.millis(positionMs.coerceAtLeast(0L).toDouble())) }
    }

    fun dispose() {
        VideoEditorJavaFxToolkit.ensureStarted()
        Platform.runLater {
            runCatching { player?.stop() }
            runCatching { player?.dispose() }
            player = null
            panel?.scene = Scene(StackPane().apply { style = "-fx-background-color: #02060B;" }, 960.0, 540.0, javafx.scene.paint.Color.BLACK)
        }
    }

    private fun dispatch(block: () -> Unit) {
        if (SwingUtilities.isEventDispatchThread()) block() else SwingUtilities.invokeLater(block)
    }
}

@Composable
internal fun VideoEditorPreview(controller: VideoEditorPreviewController, modifier: Modifier = Modifier) {
    DisposableEffect(controller) {
        onDispose { controller.dispose() }
    }
    SwingPanel(
        factory = {
            VideoEditorJavaFxToolkit.ensureStarted()
            JFXPanel().apply {
                background = java.awt.Color.BLACK
                isOpaque = true
                isDoubleBuffered = true
            }.also(controller::attach)
        },
        modifier = modifier,
        background = Color.Black
    )
}
