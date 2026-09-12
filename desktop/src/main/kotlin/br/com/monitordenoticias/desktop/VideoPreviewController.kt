package br.com.monitordenoticias.desktop

import javafx.application.Platform
import javafx.embed.swing.JFXPanel
import javafx.scene.Scene
import javafx.scene.layout.StackPane
import javafx.scene.media.Media
import javafx.scene.media.MediaPlayer
import javafx.scene.media.MediaView
import javafx.util.Duration
import java.io.File
import java.util.concurrent.atomic.AtomicLong

internal class VideoPreviewController {
    val panel = JFXPanel()
    private var player: MediaPlayer? = null
    private var view: MediaView? = null
    private val currentMs = AtomicLong(0L)
    private val durationMs = AtomicLong(0L)
    @Volatile var currentPath: String = ""
        private set
    @Volatile var playing: Boolean = false
        private set
    @Volatile var onEnd: (() -> Unit)? = null

    init {
        Platform.setImplicitExit(false)
        Platform.runLater {
            val mediaView = MediaView().apply {
                isPreserveRatio = true
                isSmooth = true
            }
            view = mediaView
            val root = StackPane(mediaView).apply {
                style = "-fx-background-color: #020A10;"
            }
            panel.scene = Scene(root)
            mediaView.fitWidthProperty().bind(root.widthProperty())
            mediaView.fitHeightProperty().bind(root.heightProperty())
        }
    }

    fun load(path: String, sourcePositionMs: Long, autoPlay: Boolean = false) {
        val file = File(path)
        if (!file.exists()) return
        currentPath = file.absolutePath
        Platform.runLater {
            runCatching { player?.dispose() }
            val p = MediaPlayer(Media(file.toURI().toString()))
            player = p
            view?.mediaPlayer = p
            p.volume = .72
            p.currentTimeProperty().addListener { _, _, value -> currentMs.set(value.toMillis().toLong().coerceAtLeast(0L)) }
            p.totalDurationProperty().addListener { _, _, value -> if (!value.isUnknown) durationMs.set(value.toMillis().toLong().coerceAtLeast(0L)) }
            p.setOnReady {
                durationMs.set(p.totalDuration.toMillis().toLong().coerceAtLeast(0L))
                p.seek(Duration.millis(sourcePositionMs.coerceAtLeast(0L).toDouble()))
                if (autoPlay) {
                    p.play()
                    playing = true
                }
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
        Platform.runLater { player?.seek(Duration.millis(sourcePositionMs.coerceAtLeast(0L).toDouble())) }
    }

    fun play() {
        Platform.runLater { player?.play() }
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
