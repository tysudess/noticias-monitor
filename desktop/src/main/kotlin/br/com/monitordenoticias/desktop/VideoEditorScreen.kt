package br.com.monitordenoticias.desktop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val V10BgDeep = Color(0xFF03172A)
private val V10BgDark = Color(0xFF041D33)
private val V10Panel = Color(0xFF062944)
private val V10Panel2 = Color(0xFF073758)
private val V10Navy950 = Color(0xFF021421)
private val V10Cyan = Color(0xFF00BDF2)
private val V10CyanBright = Color(0xFF15D4FF)
private val V10CyanSoft = Color(0xFF77DFFF)
private val V10BlueBorder = Color(0xFF087FB4)
private val V10Yellow = Color(0xFFFFC837)
private val V10YellowLight = Color(0xFFFFE36B)
private val V10YellowDark = Color(0xFFE9A91C)
private val V10Red = Color(0xFFFF456C)
private val V10Green = Color(0xFF26D67B)
private val V10Text = Color(0xFFF3F8FF)
private val V10Text2 = Color(0xFFA7CAE7)
private val V10Muted = Color(0xFF6894B8)

@Composable
fun VideoEditorScreen(onBack: () -> Unit = {}) {
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(listOf(Color(0xFF06243E), V10BgDeep, V10Navy950))
        )
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(Color(0x1700BDF2), radius = size.minDimension * .48f, center = Offset(size.width * .56f, -size.height * .06f))
            drawCircle(Color(0x0E15D4FF), radius = size.minDimension * .30f, center = Offset(size.width * .77f, size.height * .18f))
        }

        Column(
            Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            EditorHeroHeader()

            Row(
                Modifier.fillMaxWidth().height(338.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PreviewPanel(Modifier.weight(1f))
                QuickMarkPanel(Modifier.width(300.dp))
            }

            TimelinePanel()
            ExportPanel()
        }
    }
}

@Composable
private fun EditorHeroHeader() {
    Box(
        Modifier.fillMaxWidth().height(112.dp).clip(RoundedCornerShape(15.dp))
            .background(Brush.horizontalGradient(listOf(Color(0xEE05243D), Color(0xF0042037), Color(0xEE062B49))))
            .border(1.dp, V10BlueBorder.copy(alpha = .62f), RoundedCornerShape(15.dp))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(50.dp).clip(RoundedCornerShape(12.dp))
                    .background(Brush.verticalGradient(listOf(V10YellowLight, V10Yellow, V10YellowDark)))
                    .border(1.dp, Color(0xFFFFE987), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.MovieCreation, null, tint = Color(0xFF082033), modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.width(13.dp))
            Column(Modifier.width(430.dp)) {
                Text("Vídeos  ›  Editor de Vídeo", color = V10CyanSoft, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(3.dp))
                Text("EDITOR DE VÍDEO", color = V10Text, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = .4.sp)
                Text("Edite, marque e exporte trechos de forma rápida e precisa.", color = V10Text2, fontSize = 11.sp)
            }

            Box(Modifier.weight(1f).fillMaxHeight()) {
                RadarGraphic(Modifier.align(Alignment.Center).size(105.dp))
                Column(Modifier.align(Alignment.CenterEnd).padding(end = 12.dp), horizontalAlignment = Alignment.End) {
                    Text("VIGILÂNCIA", color = V10CyanSoft.copy(alpha = .72f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Text("MÍDIA", color = V10CyanSoft.copy(alpha = .55f), fontSize = 9.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("ANÁLISE", color = V10CyanSoft.copy(alpha = .72f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Text("RESULTADOS", color = V10CyanSoft.copy(alpha = .55f), fontSize = 9.sp)
                }
            }

            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(78.dp).height(2.dp).background(V10Yellow))
                    Spacer(Modifier.width(8.dp))
                    Text("BRASIL SEMPRE MAIS INFORMADO", color = V10YellowLight, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Default.Anchor, null, tint = V10Yellow, modifier = Modifier.size(18.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    GoldActionButton("ABRIR VÍDEO", Icons.Default.FolderOpen, enabled = true, width = 145.dp)
                    NavyActionButton("Desfazer (Ctrl+Z)", Icons.Default.Undo, enabled = false, width = 145.dp)
                    NavyActionButton("Refazer (Ctrl+Y)", Icons.Default.Redo, enabled = false, width = 145.dp)
                }
            }
        }
    }
}

@Composable
private fun PreviewPanel(modifier: Modifier) {
    NeonPanel(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Visibility, null, tint = V10CyanBright, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(7.dp))
            Text("PRÉ-VISUALIZAÇÃO", color = V10Text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text("Nenhum vídeo selecionado", color = V10Muted, fontSize = 10.sp)
        }
        Spacer(Modifier.height(9.dp))

        Box(
            Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(11.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF061522), Color(0xFF020A10))))
                .border(1.dp, V10BlueBorder.copy(alpha = .55f), RoundedCornerShape(11.dp))
        ) {
            CornerMark(Alignment.TopStart, false)
            CornerMark(Alignment.TopEnd, true)
            CornerMark(Alignment.BottomStart, false)
            CornerMark(Alignment.BottomEnd, true)
            Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.MovieCreation, null, tint = Color(0xFF315D79), modifier = Modifier.size(58.dp))
                Spacer(Modifier.height(8.dp))
                Text("Nenhum vídeo selecionado", color = V10Text2, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                Text("Abra um arquivo de vídeo para começar a editar.", color = V10Muted, fontSize = 9.sp)
            }
        }

        Spacer(Modifier.height(9.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("00:00:00.000 / 00:00:00.000", color = V10Text2, fontSize = 10.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.width(190.dp))
            Spacer(Modifier.weight(1f))
            IconControl(Icons.Default.SkipPrevious, false)
            Spacer(Modifier.width(6.dp))
            IconControl(Icons.Default.Replay10, false)
            Spacer(Modifier.width(7.dp))
            Box(
                Modifier.size(48.dp).clip(CircleShape)
                    .background(Brush.verticalGradient(listOf(Color(0xFF0A75B3), Color(0xFF08456F))))
                    .border(1.dp, V10CyanBright, CircleShape),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(27.dp)) }
            Spacer(Modifier.width(7.dp))
            IconControl(Icons.Default.Forward10, false)
            Spacer(Modifier.width(6.dp))
            IconControl(Icons.Default.SkipNext, false)
            Spacer(Modifier.weight(1f))
            Icon(Icons.Default.VolumeUp, null, tint = V10Text2, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(6.dp))
            Box(Modifier.width(76.dp).height(4.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFF173F5E))) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(.72f).background(V10Cyan))
            }
            Spacer(Modifier.width(9.dp))
            IconControl(Icons.Default.Fullscreen, false)
            Spacer(Modifier.width(4.dp))
            IconControl(Icons.Default.Settings, false)
        }
    }
}

@Composable
private fun QuickMarkPanel(modifier: Modifier) {
    NeonPanel(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Bookmark, null, tint = V10CyanBright, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(7.dp))
            Text("MARCAÇÃO RÁPIDA", color = V10Text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Icon(Icons.Default.Bolt, null, tint = V10Yellow, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text("Defina os pontos de início e fim do trecho desejado.", color = V10Muted, fontSize = 9.sp, lineHeight = 12.sp)
        Spacer(Modifier.height(10.dp))
        PrecisionTimeField("Início")
        Spacer(Modifier.height(8.dp))
        PrecisionTimeField("Fim")
        Spacer(Modifier.height(8.dp))
        PrecisionTimeField("Duração")
        Spacer(Modifier.weight(1f))
        NavyActionButton("Adicionar à timeline", Icons.Default.Add, enabled = false, width = Dp.Unspecified, fill = true)
    }
}

@Composable
private fun TimelinePanel() {
    NeonPanel(Modifier.fillMaxWidth().height(235.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.ViewList, null, tint = V10CyanBright, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(7.dp))
            Text("TIMELINE", color = V10Text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(10.dp))
            Text("Arraste e solte o próprio vídeo com o mouse para reordenar", color = V10Muted, fontSize = 9.sp)
            Spacer(Modifier.weight(1f))
            Icon(Icons.Default.ZoomOut, null, tint = V10Text2, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Box(Modifier.width(76.dp).height(4.dp).clip(RoundedCornerShape(4.dp)).background(Color(0xFF173F5E))) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(.48f).background(V10Cyan))
            }
            Spacer(Modifier.width(6.dp))
            Icon(Icons.Default.ZoomIn, null, tint = V10Text2, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(7.dp))
            Text("Zoom 3/6", color = V10Text2, fontSize = 9.sp)
            Spacer(Modifier.width(10.dp))
            Box(Modifier.width(1.dp).height(18.dp).background(V10BlueBorder.copy(alpha = .5f)))
            Spacer(Modifier.width(8.dp))
            Icon(Icons.Default.Tune, null, tint = V10Text2, modifier = Modifier.size(16.dp))
        }
        Spacer(Modifier.height(7.dp))
        TimelineRuler()
        Spacer(Modifier.height(4.dp))
        Row(Modifier.weight(1f).fillMaxWidth()) {
            Column(Modifier.width(60.dp).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                TrackLabel("V1", Icons.Default.Movie)
                TrackLabel("A1", Icons.Default.MusicNote)
            }
            Box(
                Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF041B2E))
                    .border(1.dp, V10BlueBorder.copy(alpha = .38f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val x = 10f
                    drawLine(V10CyanBright, Offset(x, 0f), Offset(x, size.height), 2f)
                    val p = androidx.compose.ui.graphics.Path().apply {
                        moveTo(x - 6f, 0f); lineTo(x + 6f, 0f); lineTo(x, 9f); close()
                    }
                    drawPath(p, V10CyanBright)
                    val dashColor = V10BlueBorder.copy(alpha = .24f)
                    for (i in 1..8) {
                        val yy = size.height * i / 9f
                        drawLine(dashColor, Offset(0f, yy), Offset(size.width, yy), 1f)
                    }
                }
                Text("A timeline funcional será conectada no próximo ciclo.", color = V10Muted, fontSize = 10.sp)
            }
        }
        Spacer(Modifier.height(7.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TimelineButton("Cortar", Icons.Default.ContentCut, false, V10Muted)
            Spacer(Modifier.width(6.dp))
            TimelineButton("Excluir trecho", Icons.Default.DeleteOutline, false, V10Red)
            Spacer(Modifier.width(6.dp))
            TimelineButton("Duplicar", Icons.Default.ContentCopy, false, V10Muted)
            Spacer(Modifier.weight(1f))
            listOf("-5s", "+5s", "-10s", "+10s", "-1 min", "+1 min").forEachIndexed { i, t ->
                TinyTimeButton(t)
                if (i < 5) Spacer(Modifier.width(5.dp))
            }
        }
    }
}

@Composable
private fun ExportPanel() {
    NeonPanel(Modifier.fillMaxWidth().height(106.dp)) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.FileUpload, null, tint = V10Yellow, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text("EXPORTAÇÃO", color = V10Text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text("Exporte o trecho editado no formato desejado.", color = V10Muted, fontSize = 9.sp)
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("video_final.mp4", color = V10Text2, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Default.Edit, null, tint = V10Muted, modifier = Modifier.size(13.dp))
                    ExportDivider()
                    Text("Original", color = V10Text2, fontSize = 10.sp)
                    ExportDivider()
                    Text("H.265 / HEVC", color = V10Text2, fontSize = 10.sp)
                    ExportDivider()
                    Text("1920 × 1080", color = V10Text2, fontSize = 10.sp)
                    ExportDivider()
                    Text("30 MB", color = V10Text2, fontSize = 10.sp)
                }
            }
            NavyActionButton("Configurações", Icons.Default.Settings, enabled = false, width = 130.dp)
            Spacer(Modifier.width(8.dp))
            Box(
                Modifier.width(148.dp).height(52.dp).clip(RoundedCornerShape(12.dp))
                    .background(Brush.verticalGradient(listOf(Color(0xFF092A45), Color(0xFF041C30))))
                    .border(1.dp, V10Yellow, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Upload, null, tint = V10YellowLight, modifier = Modifier.size(22.dp))
                    Spacer(Modifier.width(7.dp))
                    Text("EXPORTAR", color = V10YellowLight, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

@Composable
private fun NeonPanel(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Box(
        modifier.clip(RoundedCornerShape(14.dp))
            .background(Brush.verticalGradient(listOf(Color(0xF0073556), Color(0xFA041F36))))
            .border(1.dp, V10Cyan.copy(alpha = .58f), RoundedCornerShape(14.dp))
            .padding(12.dp)
    ) { Column(Modifier.fillMaxSize(), content = content) }
}

@Composable
private fun PrecisionTimeField(label: String) {
    Column {
        Text(label, color = V10Text2, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Row(
            Modifier.fillMaxWidth().height(39.dp).clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF041D31)).border(1.dp, V10BlueBorder.copy(alpha = .72f), RoundedCornerShape(8.dp)),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("00:00:00.000", color = V10Text, fontSize = 11.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.padding(start = 10.dp).weight(1f))
            Column(Modifier.width(26.dp).fillMaxHeight()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Text("▲", color = V10Muted, fontSize = 7.sp) }
                Box(Modifier.fillMaxWidth().height(1.dp).background(V10BlueBorder.copy(alpha = .35f)))
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Text("▼", color = V10Muted, fontSize = 7.sp) }
            }
        }
    }
}

@Composable
private fun TimelineRuler() {
    val labels = listOf("00:00","00:30","01:00","01:30","02:00","02:30","03:00","03:30","04:00","04:30","05:00")
    Row(Modifier.fillMaxWidth().padding(start = 60.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        labels.forEach { Text(it, color = V10Muted, fontSize = 7.sp, fontFamily = FontFamily.Monospace) }
    }
}

@Composable
private fun TrackLabel(label: String, icon: ImageVector) {
    Row(
        Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(7.dp)).background(Color(0xFF082B46)).padding(horizontal = 7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = V10CyanSoft, modifier = Modifier.size(13.dp))
        Spacer(Modifier.width(6.dp))
        Text(label, color = V10Text2, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TimelineButton(text: String, icon: ImageVector, enabled: Boolean, tint: Color) {
    Surface(color = Color(0xFF06243C), shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, tint.copy(alpha = if (enabled) .75f else .4f))) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = tint.copy(alpha = if (enabled) 1f else .7f), modifier = Modifier.size(14.dp))
            Spacer(Modifier.width(5.dp))
            Text(text, color = tint.copy(alpha = if (enabled) 1f else .7f), fontSize = 9.sp)
        }
    }
}

@Composable
private fun TinyTimeButton(text: String) {
    Surface(color = Color(0xFF06243C), shape = RoundedCornerShape(7.dp), border = BorderStroke(1.dp, V10BlueBorder.copy(alpha = .52f))) {
        Text(text, color = V10Text2, fontSize = 8.sp, modifier = Modifier.padding(horizontal = 9.dp, vertical = 7.dp))
    }
}

@Composable
private fun IconControl(icon: ImageVector, enabled: Boolean) {
    Box(Modifier.size(28.dp).clip(RoundedCornerShape(7.dp)).background(Color(0xFF082A45)), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = if (enabled) V10CyanBright else V10Text2.copy(alpha = .72f), modifier = Modifier.size(16.dp))
    }
}

@Composable
private fun GoldActionButton(text: String, icon: ImageVector, enabled: Boolean, width: Dp) {
    Box(
        Modifier.width(width).height(38.dp).clip(RoundedCornerShape(11.dp))
            .background(Brush.verticalGradient(listOf(V10YellowLight, V10Yellow, V10YellowDark)))
            .border(1.dp, Color(0xFFFFE987), RoundedCornerShape(11.dp)),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Color(0xFF071A29), modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text(text, color = Color(0xFF071A29), fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
        }
    }
}

@Composable
private fun NavyActionButton(text: String, icon: ImageVector, enabled: Boolean, width: Dp, fill: Boolean = false) {
    val modifier = if (fill) Modifier.fillMaxWidth().height(38.dp) else Modifier.width(width).height(38.dp)
    Surface(
        color = Color(0xFF06243C),
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, V10BlueBorder.copy(alpha = if (enabled) .72f else .34f)),
        modifier = modifier
    ) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Icon(icon, null, tint = V10Text2.copy(alpha = if (enabled) 1f else .45f), modifier = Modifier.size(15.dp))
            Spacer(Modifier.width(5.dp))
            Text(text, color = V10Text2.copy(alpha = if (enabled) 1f else .45f), fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun ExportDivider() {
    Spacer(Modifier.width(9.dp))
    Box(Modifier.width(1.dp).height(15.dp).background(V10BlueBorder.copy(alpha = .45f)))
    Spacer(Modifier.width(9.dp))
}

@Composable
private fun RadarGraphic(modifier: Modifier) {
    Canvas(modifier) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val r = size.minDimension * .44f
        listOf(1f, .68f, .36f).forEach { scale -> drawCircle(V10Cyan.copy(alpha = .25f), r * scale, c, style = Stroke(1f)) }
        drawLine(V10Cyan.copy(alpha = .22f), Offset(c.x - r, c.y), Offset(c.x + r, c.y), 1f)
        drawLine(V10Cyan.copy(alpha = .22f), Offset(c.x, c.y - r), Offset(c.x, c.y + r), 1f)
        drawLine(V10CyanBright.copy(alpha = .55f), c, Offset(c.x + r * .72f, c.y - r * .34f), 2f, cap = StrokeCap.Round)
        drawCircle(V10Yellow.copy(alpha = .9f), 2.7f, Offset(c.x + r * .45f, c.y - r * .18f))
        drawCircle(V10CyanBright.copy(alpha = .9f), 2.4f, Offset(c.x - r * .30f, c.y + r * .22f))
    }
}

@Composable
private fun BoxScope.CornerMark(alignment: Alignment, right: Boolean) {
    Box(
        Modifier.align(alignment).padding(8.dp).width(18.dp).height(2.dp).background(V10Cyan.copy(alpha = .75f))
    )
}
