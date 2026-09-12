package br.com.monitordenoticias.desktop

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import org.jetbrains.skia.Image as SkiaImage
import java.io.File

/**
 * Preview interno do Editor de Vídeo sem JavaFX Media.
 *
 * O JavaFX Media/MediaView ficou instável no portable em alguns Windows. Este controlador
 * exibe imagens JPEG já geradas pelo FFmpeg em uma sequência de preview. Durante o play
 * ele não chama FFmpeg; apenas escolhe o frame em cache correspondente ao tempo atual.
 * O motor de corte/exportação continua separado e usando o arquivo original.
 */
internal class VideoEditorPreviewController {
    @Volatile private var source: File? = null
    @Volatile private var sequence: VideoEditorFrameSequence? = null

    var frameFile by mutableStateOf<File?>(null)
        private set
    var frameVersion by mutableLongStateOf(0L)
        private set
    var loaded by mutableStateOf(false)
        private set

    var onReady: ((Long) -> Unit)? = null
    var onPosition: ((Long) -> Unit)? = null
    var onPlayingChanged: ((Boolean) -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    fun load(file: File) {
        source = file
        sequence = null
        loaded = true
        frameFile = null
        frameVersion++
        onReady?.invoke(0L)
    }

    fun loadSequence(file: File, frameSequence: VideoEditorFrameSequence) {
        source = file
        sequence = frameSequence
        loaded = true
        frameFile = null
        frameVersion++
        onReady?.invoke(frameSequence.durationMs)
        showAt(0L)
    }

    fun showAt(positionMs: Long) {
        val selected = sequence?.frameFor(positionMs)
        if (selected == null) {
            onPosition?.invoke(positionMs.coerceAtLeast(0L))
            return
        }
        showFrame(selected)
        onPosition?.invoke(positionMs.coerceAtLeast(0L))
    }

    fun showFrame(file: File) {
        if (!file.exists() || file.length() <= 0L) {
            onError?.invoke("Frame de preview não foi gerado.")
            return
        }
        if (frameFile?.absolutePath == file.absolutePath && frameFile?.lastModified() == file.lastModified()) return
        frameFile = file
        frameVersion++
    }

    fun clearFrame() {
        frameFile = null
        frameVersion++
    }

    fun play() {
        if (!loaded || source == null) {
            onError?.invoke("Nenhum preview carregado para reproduzir.")
            return
        }
        if (sequence == null) {
            onError?.invoke("Sequência de preview ainda não foi preparada.")
            return
        }
        onPlayingChanged?.invoke(true)
    }

    fun pause() {
        onPlayingChanged?.invoke(false)
    }

    fun seek(positionMs: Long) {
        showAt(positionMs.coerceAtLeast(0L))
    }

    fun dispose() {
        pause()
        source = null
        sequence = null
        loaded = false
        clearFrame()
    }
}

@Composable
internal fun VideoEditorPreview(controller: VideoEditorPreviewController, modifier: Modifier = Modifier) {
    DisposableEffect(controller) {
        onDispose { controller.dispose() }
    }

    val frame = controller.frameFile
    val version = controller.frameVersion
    val bitmap = remember(frame?.absolutePath, frame?.length(), frame?.lastModified(), version) {
        frame?.let { decodePreviewFrame(it) }
    }

    Box(modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = "Preview do vídeo",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        } else {
            Text(
                text = if (controller.loaded) "Preparando sequência de preview estável..." else "Preview aguardando vídeo",
                color = Color.White.copy(alpha = 0.72f)
            )
        }
    }
}

private fun decodePreviewFrame(file: File) = runCatching {
    SkiaImage.makeFromEncoded(file.readBytes()).toComposeImageBitmap()
}.getOrNull()
