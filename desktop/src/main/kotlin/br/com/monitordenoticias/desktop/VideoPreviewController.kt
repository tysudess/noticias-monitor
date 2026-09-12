package br.com.monitordenoticias.desktop

import javafx.application.Platform
import javafx.embed.swing.JFXPanel
import javafx.scene.Scene
import javafx.scene.layout.StackPane
import javafx.scene.media.Media
import javafx.scene.media.MediaPlayer
import javafx.scene.media.MediaView
import javafx.scene.paint.Color
import javafx.util.Duration
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

internal class VideoPreviewController {
    val panel = JFXPanel().apply {
        background = java.awt.Color(2, 10, 16)
        isOpaque = true
        isFocusable = false
        focusTraversalKeysEnabled = false
    }

    private var player: MediaPlayer? = null
    private var view: MediaView? = null
    private val currentMs = AtomicLong(0L)
    private val durationMs = AtomicLong(0L)
    private val lastError = AtomicReference<String?>(null)

    @Volatile var currentPath: String = ""
        private set
    @Volatile var playing: Boolean = false
        private set
    @Volatile var onEnd: (() -> Unit)? = null

    init {
        Platform.setImplicitExit(false)
        Platform.runLater {
            try {
                val mediaView = MediaView().apply {
                    isPreserveRatio = true
                    isSmooth = true
                    mediaPlayer = player
                }
                view = mediaView
                val root = StackPane(mediaView).apply {
                    style = "-fx-background-color: #020A10;"
                }
                panel.scene = Scene(root, Color.web("#020A10"))
                mediaView.fitWidthProperty().bind(root.widthProperty())
                mediaView.fitHeightProperty().bind(root.heightProperty())
                panel.repaint()
            } catch (t: Throwable) {
                lastError.set(t.message ?: "Falha ao inicializar a área de preview.")
            }
        }
    }

    fun load(path: String, sourcePositionMs: Long, autoPlay: Boolean = false) {
        val file = File(path)
        if (!file.exists()) {
            lastError.set("Arquivo não encontrado: ${file.absolutePath}")
            return
        }
        lastError.set(null)
        currentPath = file.absolutePath
        currentMs.set(sourcePositionMs.coerceAtLeast(0L))

        Platform.runLater {
            runCatching { player?.stop() }
            runCatching { player?.dispose() }

            val media = try {
                Media(file.toURI().toString()).also { m ->
                    m.setOnError {
                        lastError.set(m.error?.message ?: "Formato de mídia não suportado no preview.")
                        playing = false
                    }
                }
            } catch (t: Throwable) {
                lastError.set(t.message ?: "Falha ao abrir mídia no preview.")
                playing = false
                return@runLater
            }

            val p = try {
                MediaPlayer(media)
            } catch (t: Throwable) {
                lastError.set(t.message ?: "Falha ao criar o reprodutor do preview.")
                playing = false
                return@runLater
            }

            player = p
            view?.mediaPlayer = p
            p.setOnError {
                lastError.set(p.error?.message ?: "Falha de reprodução no preview.")
                playing = false
            }
            p.volume = .72
            p.currentTimeProperty().addListener { _, _, value ->
                currentMs.set(value.toMillis().toLong().coerceAtLeast(0L))
            }
            p.totalDurationProperty().addListener { _, _, value ->
                if (!value.isUnknown) durationMs.set(value.toMillis().toLong().coerceAtLeast(0L))
            }
            p.setOnReady {
                view?.mediaPlayer = p
                durationMs.set(p.totalDuration.toMillis().toLong().coerceAtLeast(0L))
                p.seek(Duration.millis(sourcePositionMs.coerceAtLeast(0L).toDouble()))
                panel.repaint()
                if (autoPlay) p.play()
            }
            p.setOnEndOfMedia {
                playing = false
                onEnd?.invoke()
            }
            p.setOnPlaying { playing = true }
            p.setOnPaused { playing = false }
            p.setOnStopped { playing = false }
        }
    }

    fun seek(sourcePositionMs: Long) {
        currentMs.set(sourcePositionMs.coerceAtLeast(0L))
        Platform.runLater { player?.seek(Duration.millis(sourcePositionMs.coerceAtLeast(0L).toDouble())) }
    }

    fun play() {
        Platform.runLater {
            val p = player
            if (p == null) {
                lastError.set("O preview ainda não está pronto para reprodução.")
            } else {
                view?.mediaPlayer = p
                p.play()
            }
        }
    }

    fun pause() {
        Platform.runLater { player?.pause() }
    }

    fun toggle() {
        if (playing) pause() else play()
    }

    fun setVolume(value: Double) {
        Platform.runLater { player?.volume = value.coerceIn(0.0, 1.0) }
    }

    fun currentPositionMs(): Long = currentMs.get()
    fun mediaDurationMs(): Long = durationMs.get()
    fun consumeError(): String? = lastError.getAndSet(null)

    fun stopAndDispose() {
        Platform.runLater {
            runCatching { player?.stop() }
            runCatching { player?.dispose() }
            player = null
            view?.mediaPlayer = null
            currentPath = ""
            currentMs.set(0L)
            durationMs.set(0L)
            playing = false
        }
    }
}
