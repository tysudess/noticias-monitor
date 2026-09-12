package br.com.monitordenoticias.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

/**
 * A aba do Monitor hospeda o AdvancedVideoEditorWidget300 original.
 * A interface e o motor de edição não são recriados em Compose: o próprio
 * editor PySide6 aprovado é embutido como janela filha do painel da aba.
 */
@Composable
fun VideoEditorScreen(onBack: () -> Unit = {}) {
    val host = remember { LegacyVideoEditorHostController() }
    var hostError by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        onDispose { host.stopAndDispose() }
    }

    LaunchedEffect(Unit) {
        while (true) {
            host.consumeError()?.let { hostError = it }
            delay(250)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF05070F))
    ) {
        SwingPanel(
            modifier = Modifier.fillMaxSize(),
            background = Color(0xFF05070F),
            factory = { host.panel }
        )

        hostError?.let { message ->
            Text(
                text = message,
                color = Color(0xFFFFB4C5),
                fontSize = 13.sp,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}
