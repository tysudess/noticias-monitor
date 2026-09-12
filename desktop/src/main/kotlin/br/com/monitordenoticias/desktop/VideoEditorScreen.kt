package br.com.monitordenoticias.desktop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.io.File
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter
import kotlin.math.max
import kotlin.math.min

private val VE_BG = Color(0xFF07111F)
private val VE_PANEL = Color(0xED0B1626)
private val VE_PANEL_2 = Color(0xFF101D2F)
private val VE_BORDER = Color(0xFF40526B)
private val VE_BORDER_SOFT = Color(0xFF26384F)
private val VE_TEXT = Color(0xFFF2F3F6)
private val VE_MUTED = Color(0xFFA7B2C5)
private val VE_GOLD = Color(0xFFD5A34B)
private val VE_GOLD_2 = Color(0xFFFFAB23)
private val VE_BLUE = Color(0xFF168FFF)
private val VE_CYAN = Color(0xFF27DDE9)
private val VE_GREEN = Color(0xFF49D5A5)
private val VE_RED = Color(0xFFE35D67)

@Composable
fun VideoEditorScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val engine = remember { VideoEditorEngine() }
    val preview = remember { VideoEditorPreviewController() }

    var input by remember { mutableStateOf<File?>(null) }
    var info by remember { mutableStateOf<VideoEditorMediaInfo?>(null) }
    var currentMs by remember { mutableLongStateOf(0L) }
    var inMs by remember { mutableLongStateOf(0L) }
    var outMs by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var status by remember { mutableStateOf("Abra um vídeo para começar a editar.") }
    var exported by remember { mutableStateOf<File?>(null) }

    DisposableEffect(preview) {
        preview.onReady = { duration ->
            if (outMs <= 0L && duration > 0L) outMs = duration
        }
        preview.onPosition = { value ->
            currentMs = value
            if (playing && outMs > inMs && value >= outMs) {
                preview.pause()
                preview.seek(outMs)
            }
        }
        preview.onPlayingChanged = { playing = it }
        preview.onError = { status = "Preview: $it" }
        onDispose {
            preview.onReady = null
            preview.onPosition = null
            preview.onPlayingChanged = null
            preview.onError = null
            engine.cancel()
        }
    }

    fun openVideo() {
        if (busy) return
        val chosen = chooseVideoFile() ?: return
        if (!engine.isSupportedInput(chosen)) {
            status = "Formato não suportado. Use MP4, MOV, MKV ou WEBM."
            return
        }
        preview.pause()
        busy = true
        progress = 0f
        status = "Analisando ${chosen.name}..."
        exported = null
        scope.launch {
            val probed = engine.probe(chosen)
            val mediaInfo = probed.getOrElse {
                busy = false
                status = it.message ?: "Falha ao analisar o vídeo."
                return@launch
            }
            input = chosen
            info = mediaInfo
            currentMs = 0L
            inMs = 0L
            outMs = mediaInfo.durationMs
            val prepared = engine.preparePreview(chosen, mediaInfo) { pct, msg ->
                progress = pct.coerceIn(0, 100) / 100f
                if (msg.isNotBlank()) status = msg
            }
            busy = false
            prepared.fold(
                onSuccess = { file ->
                    progress = 1f
                    status = "Vídeo pronto para edição: ${chosen.name}"
                    preview.load(file)
                },
                onFailure = { status = it.message ?: "Falha ao preparar o preview." }
            )
        }
    }

    fun exportCut() {
        val source = input ?: return
        if (busy || outMs <= inMs) return
        preview.pause()
        busy = true
        progress = 0f
        status = "Preparando exportação precisa..."
        scope.launch {
            val result = engine.exportPrecise(source, inMs, outMs) { pct, msg ->
                progress = pct.coerceIn(0, 100) / 100f
                if (msg.isNotBlank()) status = msg
            }
            busy = false
            result.fold(
                onSuccess = {
                    exported = it
                    progress = 1f
                    status = "Exportação concluída: ${it.name}"
                },
                onFailure = { status = it.message ?: "Falha na exportação." }
            )
        }
    }

    Box(
        Modifier.fillMaxSize().background(
            Brush.radialGradient(listOf(Color(0xFF12314A), VE_BG), radius = 1300f)
        )
    ) {
        Column(Modifier.fillMaxSize().padding(14.dp)) {
            EditorHeader(onBack)
            Spacer(Modifier.height(10.dp))
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                EditorToolRail(
                    hasVideo = input != null,
                    busy = busy,
                    onOpen = ::openVideo,
                    onCut = ::exportCut
                )

                Column(Modifier.weight(1f).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PreviewPanel(
                        input = input,
                        info = info,
                        preview = preview,
                        playing = playing,
                        busy = busy,
                        onOpen = ::openVideo,
                        onPlayPause = {
                            if (input != null && !busy) {
                                if (playing) preview.pause()
                                else {
                                    if (outMs > inMs && currentMs >= outMs - 40L) preview.seek(inMs)
                                    preview.play()
                                }
                            }
                        }
                    )

                    TimelinePanel(
                        durationMs = info?.durationMs ?: 0L,
                        currentMs = currentMs,
                        inMs = inMs,
                        outMs = outMs,
                        enabled = input != null && !busy,
                        onSeek = {
                            currentMs = it
                            preview.seek(it)
                        },
                        onMarkIn = {
                            val maxIn = max(0L, outMs - 100L)
                            inMs = min(currentMs, maxIn)
                            status = "Ponto IN marcado em ${formatTime(inMs)}"
                        },
                        onMarkOut = {
                            val duration = info?.durationMs ?: 0L
                            outMs = max(inMs + 100L, min(currentMs, duration)).coerceAtMost(duration)
                            status = "Ponto OUT marcado em ${formatTime(outMs)}"
                        }
                    )
                }

                ExportPanel(
                    input = input,
                    info = info,
                    inMs = inMs,
                    outMs = outMs,
                    busy = busy,
                    progress = progress,
                    status = status,
                    exported = exported,
                    onExport = ::exportCut,
                    onCancel = {
                        engine.cancel()
                        busy = false
                        preview.pause()
                        status = "Operação cancelada."
                    },
                    onOpenFolder = {
                        engine.openExportsFolder().onFailure {
                            status = it.message ?: "Não foi possível abrir a pasta."
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun EditorHeader(onBack: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(92.dp).border(1.dp, VE_BORDER_SOFT, RoundedCornerShape(12.dp))
            .background(Color(0xB8091423), RoundedCornerShape(12.dp)).padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.width(250.dp)) {
            Text("DEPARTAMENTO", color = VE_GOLD, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
            Text("DE IMPRENSA", color = VE_GOLD, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
        }
        Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("EDITOR DE VÍDEO", color = VE_TEXT, fontSize = 27.sp, fontWeight = FontWeight.Bold)
            Text("Abra um vídeo para começar a editar", color = Color(0xFFD6DAE1), fontSize = 14.sp)
            Text("Primeira versão funcional • corte preciso MP4", color = VE_MUTED, fontSize = 11.sp)
        }
        OutlinedButton(
            onClick = onBack,
            border = BorderStroke(1.dp, VE_GOLD),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = VE_GOLD),
            modifier = Modifier.width(190.dp).height(44.dp)
        ) {
            Icon(Icons.Default.ArrowBack, null)
            Spacer(Modifier.width(8.dp))
            Text("Voltar ao Monitor")
        }
    }
}

@Composable
private fun EditorToolRail(hasVideo: Boolean, busy: Boolean, onOpen: () -> Unit, onCut: () -> Unit) {
    Column(
        Modifier.width(250.dp).fillMaxHeight().background(VE_PANEL, RoundedCornerShape(14.dp))
            .border(1.dp, VE_BORDER_SOFT, RoundedCornerShape(14.dp)).padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        RailButton("Arquivos", Icons.Default.FolderOpen, true, VE_GOLD, onOpen)
        RailButton("Cortar", Icons.Default.ContentCut, hasVideo && !busy, VE_GOLD, onCut)
        RailButton("Redimensionar", Icons.Default.Crop, false, Color(0xFF7B94B3)) {}
        RailButton("Criar", Icons.Default.AddBox, false, Color(0xFF6C91C7)) {}
        RailButton("Excluir", Icons.Default.Delete, false, Color(0xFF7B94B3)) {}
        RailButton("Ordenar", Icons.Default.SwapVert, false, VE_GREEN) {}
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier.fillMaxWidth().height(150.dp).border(1.dp, VE_BORDER, RoundedCornerShape(12.dp))
                .background(Color(0x66111E2E), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.Movie, null, tint = VE_GOLD, modifier = Modifier.size(34.dp))
                Spacer(Modifier.height(8.dp))
                Text("FORMATOS INICIAIS", color = VE_TEXT, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text("MP4 • MOV • MKV • WEBM", color = VE_MUTED, fontSize = 10.sp)
            }
        }
        Spacer(Modifier.weight(1f))
        Text("Recursos em cinza entram nas próximas etapas.", color = VE_MUTED, fontSize = 10.sp)
    }
}

@Composable
private fun RailButton(label: String, icon: ImageVector, enabled: Boolean, accent: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(10.dp))
            .background(if (enabled) Color(0xFF122338) else Color(0xFF0C1726))
            .border(1.dp, if (enabled) VE_BORDER else VE_BORDER_SOFT, RoundedCornerShape(10.dp))
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 13.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = if (enabled) accent else Color(0xFF536177), modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Text(label, color = if (enabled) VE_TEXT else Color(0xFF647086), fontSize = 14.sp)
        if (!enabled && label !in setOf("Arquivos", "Cortar")) {
            Spacer(Modifier.weight(1f))
            Text("EM BREVE", color = Color(0xFF536177), fontSize = 8.sp)
        }
    }
}

@Composable
private fun PreviewPanel(
    input: File?,
    info: VideoEditorMediaInfo?,
    preview: VideoEditorPreviewController,
    playing: Boolean,
    busy: Boolean,
    onOpen: () -> Unit,
    onPlayPause: () -> Unit
) {
    Column(
        Modifier.weight(1f).fillMaxWidth().background(VE_PANEL, RoundedCornerShape(14.dp))
            .border(1.dp, VE_BORDER, RoundedCornerShape(14.dp)).padding(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("VISUALIZAÇÃO", color = VE_GOLD, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            info?.let {
                Text("${it.width}×${it.height}  •  ${it.videoCodec.uppercase()}", color = VE_MUTED, fontSize = 10.sp)
            }
        }
        Spacer(Modifier.height(8.dp))
        Box(
            Modifier.weight(1f).fillMaxWidth().background(Color.Black, RoundedCornerShape(8.dp))
                .border(1.dp, Color(0xFF53647D), RoundedCornerShape(8.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (input == null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Movie, null, tint = Color(0xFF566A82), modifier = Modifier.size(72.dp))
                    Spacer(Modifier.height(12.dp))
                    Text("Nenhum vídeo aberto", color = VE_TEXT, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Text("Use Arquivos para selecionar um vídeo", color = VE_MUTED, fontSize = 12.sp)
                    Spacer(Modifier.height(14.dp))
                    Button(onClick = onOpen, colors = ButtonDefaults.buttonColors(containerColor = VE_BLUE)) {
                        Icon(Icons.Default.FolderOpen, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Abrir vídeo")
                    }
                }
            } else {
                VideoEditorPreview(preview, Modifier.fillMaxSize().padding(2.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = onPlayPause,
                enabled = input != null && !busy,
                colors = ButtonDefaults.buttonColors(containerColor = VE_BLUE),
                modifier = Modifier.width(130.dp)
            ) {
                Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, null)
                Spacer(Modifier.width(6.dp))
                Text(if (playing) "Pausar" else "Reproduzir")
            }
            Spacer(Modifier.width(10.dp))
            Text(input?.name ?: "", color = VE_MUTED, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun TimelinePanel(
    durationMs: Long,
    currentMs: Long,
    inMs: Long,
    outMs: Long,
    enabled: Boolean,
    onSeek: (Long) -> Unit,
    onMarkIn: () -> Unit,
    onMarkOut: () -> Unit
) {
    Column(
        Modifier.fillMaxWidth().height(172.dp).background(VE_PANEL, RoundedCornerShape(14.dp))
            .border(1.dp, VE_BORDER_SOFT, RoundedCornerShape(14.dp)).padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("TIMELINE", color = VE_GOLD, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text("${formatTime(currentMs)} / ${formatTime(durationMs)}", color = VE_CYAN, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = currentMs.coerceIn(0L, max(1L, durationMs)).toFloat(),
            onValueChange = { onSeek(it.toLong()) },
            enabled = enabled,
            valueRange = 0f..max(1L, durationMs).toFloat(),
            colors = SliderDefaults.colors(thumbColor = VE_GOLD_2, activeTrackColor = VE_BLUE, inactiveTrackColor = Color(0xFF33445D))
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            TimeChip("IN", inMs, VE_GREEN)
            TimeChip("OUT", outMs, VE_RED)
            Spacer(Modifier.weight(1f))
            OutlinedButton(onClick = onMarkIn, enabled = enabled, border = BorderStroke(1.dp, VE_GREEN)) {
                Text("Marcar IN", color = VE_GREEN)
            }
            OutlinedButton(onClick = onMarkOut, enabled = enabled, border = BorderStroke(1.dp, VE_RED)) {
                Text("Marcar OUT", color = VE_RED)
            }
        }
    }
}

@Composable
private fun TimeChip(label: String, value: Long, color: Color) {
    Surface(color = Color(0xFF101E30), shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, color)) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = color, fontWeight = FontWeight.Bold, fontSize = 10.sp)
            Spacer(Modifier.width(7.dp))
            Text(formatTime(value), color = VE_TEXT, fontSize = 11.sp)
        }
    }
}

@Composable
private fun ExportPanel(
    input: File?,
    info: VideoEditorMediaInfo?,
    inMs: Long,
    outMs: Long,
    busy: Boolean,
    progress: Float,
    status: String,
    exported: File?,
    onExport: () -> Unit,
    onCancel: () -> Unit,
    onOpenFolder: () -> Unit
) {
    Column(
        Modifier.width(300.dp).fillMaxHeight().background(VE_PANEL, RoundedCornerShape(14.dp))
            .border(1.dp, VE_BORDER_SOFT, RoundedCornerShape(14.dp)).padding(14.dp)
    ) {
        Text("CORTE E EXPORTAÇÃO", color = VE_GOLD, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(14.dp))
        LabelValue("Ponto IN", formatTime(inMs), VE_GREEN)
        Spacer(Modifier.height(8.dp))
        LabelValue("Ponto OUT", formatTime(outMs), VE_RED)
        Spacer(Modifier.height(8.dp))
        LabelValue("Trecho", formatTime((outMs - inMs).coerceAtLeast(0L)), VE_CYAN)
        Spacer(Modifier.height(16.dp))
        HorizontalDivider(color = VE_BORDER_SOFT)
        Spacer(Modifier.height(14.dp))
        Text("SAÍDA", color = VE_MUTED, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Text("MP4 • H.264/AVC • AAC", color = VE_TEXT, fontSize = 12.sp)
        Text("Corte preciso com recodificação", color = VE_MUTED, fontSize = 10.sp)
        info?.let {
            Spacer(Modifier.height(10.dp))
            Text("Origem: ${it.formatName}", color = VE_MUTED, fontSize = 10.sp, maxLines = 2)
        }
        Spacer(Modifier.height(18.dp))
        Button(
            onClick = onExport,
            enabled = input != null && !busy && outMs > inMs,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.buttonColors(containerColor = VE_BLUE)
        ) {
            Icon(Icons.Default.ContentCut, null)
            Spacer(Modifier.width(8.dp))
            Text("CORTAR / EXPORTAR")
        }
        Spacer(Modifier.height(10.dp))
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(8.dp)),
            color = VE_GOLD_2,
            trackColor = Color(0xFF26384F)
        )
        Spacer(Modifier.height(8.dp))
        Text(status, color = if (exported != null) VE_GREEN else VE_MUTED, fontSize = 10.sp, maxLines = 5)
        Spacer(Modifier.height(12.dp))
        OutlinedButton(
            onClick = onCancel,
            enabled = busy,
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, VE_RED)
        ) {
            Icon(Icons.Default.Cancel, null, tint = VE_RED)
            Spacer(Modifier.width(8.dp))
            Text("Cancelar", color = VE_RED)
        }
        Spacer(Modifier.weight(1f))
        exported?.let {
            Text(it.name, color = VE_TEXT, fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(8.dp))
        }
        OutlinedButton(
            onClick = onOpenFolder,
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, VE_GOLD)
        ) {
            Icon(Icons.Default.FolderOpen, null, tint = VE_GOLD)
            Spacer(Modifier.width(8.dp))
            Text("Abrir pasta de exportação", color = VE_GOLD, fontSize = 11.sp)
        }
    }
}

@Composable
private fun LabelValue(label: String, value: String, accent: Color) {
    Surface(color = VE_PANEL_2, shape = RoundedCornerShape(9.dp), border = BorderStroke(1.dp, VE_BORDER_SOFT), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = VE_MUTED, fontSize = 10.sp)
            Spacer(Modifier.weight(1f))
            Text(value, color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
    }
}

private fun chooseVideoFile(): File? {
    val chooser = JFileChooser().apply {
        dialogTitle = "Abrir vídeo"
        fileFilter = FileNameExtensionFilter("Vídeos (MP4, MOV, MKV, WEBM)", "mp4", "mov", "mkv", "webm")
        isAcceptAllFileFilterUsed = false
    }
    return if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) chooser.selectedFile else null
}

private fun formatTime(ms: Long): String {
    val safe = ms.coerceAtLeast(0L)
    val totalSeconds = safe / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    val millis = safe % 1000L
    return if (hours > 0L) "%02d:%02d:%02d.%03d".format(hours, minutes, seconds, millis)
    else "%02d:%02d.%03d".format(minutes, seconds, millis)
}
