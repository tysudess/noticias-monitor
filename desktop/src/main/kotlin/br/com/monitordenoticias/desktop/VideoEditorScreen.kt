package br.com.monitordenoticias.desktop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val VEBackground = Color(0xFF07111F)
private val VEPanel = Color(0xFF0B1B2D)
private val VEPanel2 = Color(0xFF10243A)
private val VEBorder = Color(0xFF244A70)
private val VEText = Color(0xFFEAF4FF)
private val VEMuted = Color(0xFF8EA8C2)
private val VECyan = Color(0xFF48D8FF)
private val VEPurple = Color(0xFF7765FF)
private val VERed = Color(0xFFE84B5F)

/**
 * Primeira versão testável da aba Editor de Vídeo.
 *
 * Nesta etapa o objetivo é validar a integração no Monitor e o esqueleto visual
 * sem tocar no Editor de PDF ou na lógica das demais áreas. As funcionalidades
 * de timeline, preview real, cortes e exportação serão conectadas em ciclos
 * posteriores, preservando este contrato de navegação.
 */
@Composable
fun VideoEditorScreen(onBack: () -> Unit = {}) {
    Column(
        Modifier.fillMaxSize().background(VEBackground).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        VideoEditorHeader()

        Row(
            Modifier.weight(1f).fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                color = VEPanel,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, VEBorder),
                modifier = Modifier.weight(1f).fillMaxHeight()
            ) {
                Column(Modifier.fillMaxSize().padding(14.dp)) {
                    Text("PRÉ-VISUALIZAÇÃO", color = VECyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    Box(
                        Modifier.weight(1f).fillMaxWidth().background(Color.Black, RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(Icons.Default.Movie, null, tint = Color(0xFF3F5972), modifier = Modifier.size(72.dp))
                            Spacer(Modifier.height(10.dp))
                            Text("Nenhum vídeo selecionado", color = VEMuted, fontSize = 13.sp)
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = {}, enabled = false) { Text("−5s") }
                        Spacer(Modifier.width(8.dp))
                        Button(onClick = {}, enabled = false) {
                            Icon(Icons.Default.PlayArrow, null)
                            Spacer(Modifier.width(5.dp))
                            Text("Reproduzir")
                        }
                        Spacer(Modifier.width(8.dp))
                        OutlinedButton(onClick = {}, enabled = false) { Text("+5s") }
                        Spacer(Modifier.width(14.dp))
                        Text("00:00:00.000 / 00:00:00.000", color = VEMuted, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }

            Surface(
                color = VEPanel,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, VEBorder),
                modifier = Modifier.width(300.dp).fillMaxHeight()
            ) {
                Column(Modifier.fillMaxSize().padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("MARCAÇÃO RÁPIDA", color = VECyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    VideoTimeField("Início", "00:00:00.000")
                    VideoTimeField("Fim", "00:00:00.000")
                    VideoTimeField("Duração", "00:00:00.000")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.weight(1f)) { Text("Início aqui", fontSize = 10.sp) }
                        OutlinedButton(onClick = {}, enabled = false, modifier = Modifier.weight(1f)) { Text("Fim aqui", fontSize = 10.sp) }
                    }
                    Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) { Text("Aplicar tempos") }
                    Spacer(Modifier.weight(1f))
                    Text("Selecione um clipe na timeline para ajustar o trecho.", color = VEMuted, fontSize = 10.sp, lineHeight = 14.sp)
                }
            }
        }

        VideoTimelineShell()
        VideoExportShell()
    }
}

@Composable
private fun VideoEditorHeader() {
    Surface(
        color = VEPanel,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, VEBorder),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("EDITOR DE VÍDEO", color = VEText, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                Text("Nenhum vídeo selecionado", color = VEMuted, fontSize = 11.sp)
            }
            Button(
                onClick = {},
                colors = ButtonDefaults.buttonColors(containerColor = VEPurple)
            ) {
                Icon(Icons.Default.FolderOpen, null)
                Spacer(Modifier.width(6.dp))
                Text("Abrir vídeo")
            }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = {}, enabled = false) {
                Icon(Icons.Default.Undo, null)
                Spacer(Modifier.width(6.dp))
                Text("VOLTAR / DESFAZER (Ctrl+Z)", fontSize = 10.sp)
            }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = {}, enabled = false) {
                Icon(Icons.Default.Redo, null)
                Spacer(Modifier.width(6.dp))
                Text("Redefinir", fontSize = 10.sp)
            }
        }
    }
}

@Composable
private fun VideoTimelineShell() {
    Surface(
        color = VEPanel,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, VEBorder),
        modifier = Modifier.fillMaxWidth().height(205.dp)
    ) {
        Column(Modifier.fillMaxSize().padding(13.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("TIMELINE", color = VEText, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text("  •  arraste o próprio vídeo com o mouse para reordenar", color = VEMuted, fontSize = 10.sp)
                Spacer(Modifier.weight(1f))
                Text("0 clipes  •  00:00:00.000  •  Zoom 3/6", color = VEMuted, fontSize = 10.sp)
            }
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier.weight(1f).fillMaxWidth().background(VEPanel2, RoundedCornerShape(9.dp)),
                contentAlignment = Alignment.Center
            ) {
                Text("A timeline funcional será conectada no próximo ciclo.", color = VEMuted, fontSize = 11.sp)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(onClick = {}, enabled = false) { Icon(Icons.Default.ContentCut, null); Spacer(Modifier.width(4.dp)); Text("Cortar") }
                OutlinedButton(onClick = {}, enabled = false) { Text("Excluir trecho", color = VERed) }
                OutlinedButton(onClick = {}, enabled = false) { Text("Duplicar") }
                Spacer(Modifier.weight(1f))
                listOf("−1s", "−100ms", "−10ms", "+10ms", "+100ms", "+1s").forEach {
                    OutlinedButton(onClick = {}, enabled = false) { Text(it, fontSize = 9.sp) }
                }
            }
        }
    }
}

@Composable
private fun VideoExportShell() {
    Surface(
        color = VEPanel,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, VEBorder),
        modifier = Modifier.fillMaxWidth().height(104.dp)
    ) {
        Row(Modifier.fillMaxSize().padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("EXPORTAÇÃO", color = VEText, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(5.dp))
                Text("video_final.mp4   •   Original   •   H.265 / HEVC   •   alvo 30 MB", color = VEMuted, fontSize = 10.sp)
                Spacer(Modifier.height(5.dp))
                Text("SISTEMA PRONTO • Adicione um vídeo à timeline para editar e exportar.", color = VECyan, fontSize = 10.sp)
            }
            Button(onClick = {}, enabled = false, colors = ButtonDefaults.buttonColors(containerColor = VEPurple)) {
                Icon(Icons.Default.SaveAlt, null)
                Spacer(Modifier.width(6.dp))
                Text("EXPORTAR")
            }
        }
    }
}

@Composable
private fun VideoTimeField(label: String, value: String) {
    Column {
        Text(label, color = VEMuted, fontSize = 9.sp)
        Spacer(Modifier.height(3.dp))
        Surface(
            color = VEPanel2,
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, VEBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(value, color = VEText, fontSize = 12.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp))
        }
    }
}
