package br.com.monitordenoticias.desktop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Path
import java.nio.file.Paths

private val VE_BG = Color(0xFF07111F)
private val VE_TOP = Color(0xFF0B1524)
private val VE_PANEL = Color(0xF20D1828)
private val VE_PANEL_2 = Color(0xFF121F31)
private val VE_BORDER = Color(0xFF243650)
private val VE_TEXT = Color(0xFFF1F5FF)
private val VE_MUTED = Color(0xFFA8B4C7)
private val VE_BLUE = Color(0xFF168FFF)
private val VE_BLUE_2 = Color(0xFF37A6FF)
private val VE_GREEN = Color(0xFF4ED69E)
private val VE_RED = Color(0xFFE25F65)

@Composable
fun VideoEditorScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val launcher = remember { PySideVideoEditorLauncher() }
    var status by remember { mutableStateOf("Pronto para abrir o Editor de Vídeo PySide6.") }
    var launching by remember { mutableStateOf(false) }

    fun openEditor() {
        if (launching) return
        launching = true
        status = "Abrindo Editor de Vídeo PySide6 / QtMultimedia..."
        scope.launch {
            val result = withContext(Dispatchers.IO) { launcher.launch() }
            launching = false
            status = result.fold(
                onSuccess = { "Editor de Vídeo aberto em janela nativa PySide6 com QMediaPlayer, QVideoWidget e QAudioOutput." },
                onFailure = { "Falha ao abrir Editor de Vídeo PySide6: ${it.message ?: "erro desconhecido"}" }
            )
        }
    }

    LaunchedEffect(Unit) {
        openEditor()
    }

    Box(
        Modifier.fillMaxSize().background(
            Brush.radialGradient(listOf(Color(0xFF102A43), VE_BG), radius = 1450f)
        )
    ) {
        Column(
            Modifier.fillMaxSize().padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                Modifier.fillMaxWidth().height(82.dp).clip(RoundedCornerShape(9.dp)).background(VE_TOP)
                    .border(1.dp, VE_BORDER, RoundedCornerShape(9.dp)).padding(horizontal = 22.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("▥", color = VE_BLUE_2, fontSize = 46.sp, fontWeight = FontWeight.Light)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("VideoMaster PRO", color = VE_TEXT, fontSize = 27.sp, fontWeight = FontWeight.Bold)
                    Text("Editor de Vídeo nativo • PySide6 6.9.1 • QtMultimedia • QMediaPlayer", color = VE_MUTED, fontSize = 13.sp)
                }
                Spacer(Modifier.weight(1f))
                Button(
                    onClick = ::openEditor,
                    enabled = !launching,
                    colors = ButtonDefaults.buttonColors(containerColor = VE_BLUE),
                    modifier = Modifier.height(48.dp)
                ) { Text(if (launching) "Abrindo..." else "Abrir Editor") }
                Spacer(Modifier.width(10.dp))
                OutlinedButton(
                    onClick = onBack,
                    border = BorderStroke(1.dp, VE_BORDER),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = VE_TEXT),
                    modifier = Modifier.height(48.dp)
                ) { Text("Voltar ao Monitor") }
            }

            Row(Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(
                    Modifier.width(230.dp).fillMaxHeight().clip(RoundedCornerShape(9.dp)).background(Color(0xE80A1524))
                        .border(1.dp, VE_BORDER, RoundedCornerShape(9.dp)).padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LauncherModule("✂", "Editor de Vídeo", true)
                    LauncherModule("⇩", "Extração", false)
                    LauncherModule("▤", "Compactação", false)
                    LauncherModule("✄", "Corte", false)
                    LauncherModule("▣", "Unir Vídeos", false)
                    LauncherModule("↻", "Converter", false)
                    Spacer(Modifier.weight(1f))
                    Surface(color = Color(0x55121F31), shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, VE_BORDER)) {
                        Column(Modifier.fillMaxWidth().padding(12.dp)) {
                            Text("Motor do preview", color = VE_GREEN, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text("PySide6.QtMultimedia.QMediaPlayer", color = VE_MUTED, fontSize = 11.sp)
                            Text("QVideoWidget para vídeo", color = VE_MUTED, fontSize = 11.sp)
                            Text("QAudioOutput para áudio", color = VE_MUTED, fontSize = 11.sp)
                        }
                    }
                }

                Column(
                    Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(9.dp)).background(VE_PANEL)
                        .border(1.dp, VE_BORDER, RoundedCornerShape(9.dp)).padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("Editor de Vídeo PySide6", color = VE_TEXT, fontSize = 32.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "A interface principal do editor agora abre em uma janela nativa Qt, usando QMediaPlayer + QVideoWidget + QAudioOutput para evitar tela branca e reprodução irregular.",
                        color = VE_MUTED,
                        fontSize = 15.sp
                    )
                    Spacer(Modifier.height(24.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatusChip("Preview real", VE_BLUE_2)
                        StatusChip("Áudio real", VE_GREEN)
                        StatusChip("Timeline visual", VE_BLUE_2)
                        StatusChip("Exportação FFmpeg", VE_GREEN)
                    }
                    Spacer(Modifier.height(28.dp))
                    Button(
                        onClick = ::openEditor,
                        enabled = !launching,
                        colors = ButtonDefaults.buttonColors(containerColor = VE_BLUE),
                        shape = RoundedCornerShape(9.dp),
                        modifier = Modifier.height(56.dp).width(260.dp)
                    ) { Text(if (launching) "Abrindo editor..." else "Abrir Editor de Vídeo", fontSize = 16.sp) }
                    Spacer(Modifier.height(18.dp))
                    Text(status, color = if (status.startsWith("Falha")) VE_RED else VE_MUTED, fontSize = 13.sp)
                    Spacer(Modifier.height(18.dp))
                    Text("Arquivo esperado no portable: video-editor/VideoEditorPySide/VideoEditorPySide.exe", color = VE_MUTED.copy(alpha = 0.7f), fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
private fun LauncherModule(symbol: String, label: String, selected: Boolean) {
    Row(
        Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(8.dp))
            .background(if (selected) VE_BLUE else Color.Transparent).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(symbol, color = if (selected) Color.White else VE_TEXT, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(12.dp))
        Text(label, color = if (selected) Color.White else VE_TEXT, fontSize = 14.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun StatusChip(text: String, color: Color) {
    Surface(color = VE_PANEL_2, shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, color)) {
        Text(text, color = color, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp))
    }
}

private class PySideVideoEditorLauncher {
    private val appDir: Path = discoverAppDir()
    private val editorExe: File = appDir.resolve("video-editor/VideoEditorPySide/VideoEditorPySide.exe").toFile()

    fun launch(): Result<Unit> = runCatching {
        require(editorExe.exists()) { "executável não encontrado: ${editorExe.absolutePath}" }
        val process = ProcessBuilder(editorExe.absolutePath)
            .directory(appDir.toFile())
            .redirectErrorStream(true)
            .start()
        require(process.isAlive) { "processo do editor não iniciou." }
    }

    private fun discoverAppDir(): Path {
        val cwd = Paths.get(System.getProperty("user.dir", ".")).toAbsolutePath().normalize()
        val launcherDir = runCatching {
            System.getProperty("jpackage.app-path", "")
                .trim()
                .takeIf { it.isNotBlank() }
                ?.let { Paths.get(it).toAbsolutePath().normalize().parent }
        }.getOrNull()
        val codeSourceDir = runCatching {
            val path = Paths.get(PySideVideoEditorLauncher::class.java.protectionDomain.codeSource.location.toURI())
                .toAbsolutePath().normalize()
            if (path.toFile().isFile) path.parent else path
        }.getOrNull()
        val candidates = listOfNotNull(launcherDir, cwd, codeSourceDir, cwd.parent).distinct()
        return candidates.firstOrNull { candidate ->
            candidate.resolve("MonitorDeNoticias.exe").toFile().exists() ||
                candidate.resolve("bin").toFile().exists()
        } ?: launcherDir ?: cwd
    }
}
