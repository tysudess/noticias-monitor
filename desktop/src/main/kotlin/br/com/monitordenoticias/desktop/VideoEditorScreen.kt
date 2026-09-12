package br.com.monitordenoticias.desktop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
private val VE_TOP = Color(0xFF0B1524)
private val VE_PANEL = Color(0xF20D1828)
private val VE_PANEL_2 = Color(0xFF121F31)
private val VE_PANEL_3 = Color(0xFF18263A)
private val VE_BORDER = Color(0xFF243650)
private val VE_BORDER_SOFT = Color(0xFF1B2A40)
private val VE_TEXT = Color(0xFFF1F5FF)
private val VE_MUTED = Color(0xFFA8B4C7)
private val VE_FADED = Color(0xFF66758D)
private val VE_BLUE = Color(0xFF168FFF)
private val VE_BLUE_2 = Color(0xFF37A6FF)
private val VE_CYAN = Color(0xFF38DDE8)
private val VE_GREEN = Color(0xFF4ED69E)
private val VE_RED = Color(0xFFE25F65)
private val VE_PURPLE = Color(0xFF7651D8)
private val VE_ORANGE = Color(0xFFFF6B44)

private enum class ProjectMediaFilter(val label: String) { ALL("Todos"), VIDEOS("Vídeos"), IMAGES("Imagens"), AUDIOS("Áudios") }

@Composable
fun VideoEditorScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val engine = remember { VideoEditorEngine() }
    val preview = remember { VideoEditorPreviewController() }
    val pageScroll = rememberScrollState()

    var input by remember { mutableStateOf<File?>(null) }
    var info by remember { mutableStateOf<VideoEditorMediaInfo?>(null) }
    var currentMs by remember { mutableLongStateOf(0L) }
    var inMs by remember { mutableLongStateOf(0L) }
    var outMs by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var status by remember { mutableStateOf("Pronto. Abra um vídeo para começar.") }
    var exported by remember { mutableStateOf<File?>(null) }
    var mediaFilter by remember { mutableStateOf(ProjectMediaFilter.ALL) }

    DisposableEffect(preview) {
        preview.onReady = { duration ->
            if (duration > 0L) {
                outMs = duration
                currentMs = currentMs.coerceIn(0L, duration)
            }
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

    fun showNotImplemented(feature: String) {
        status = "Esta função ainda não existe no motor atual: $feature."
    }

    fun seekTo(targetMs: Long) {
        val duration = max(1L, info?.durationMs ?: 0L)
        val target = targetMs.coerceIn(0L, duration)
        currentMs = target
        preview.seek(target)
    }

    fun seekBy(deltaMs: Long) {
        seekTo(currentMs + deltaMs)
    }

    fun markIn() {
        val safeOut = if (outMs > 100L) outMs else max(100L, info?.durationMs ?: 100L)
        inMs = min(currentMs, safeOut - 100L).coerceAtLeast(0L)
        if (currentMs < inMs) seekTo(inMs)
        status = "Ponto inicial definido em ${formatTime(inMs)}"
    }

    fun markOut() {
        val duration = max(100L, info?.durationMs ?: 100L)
        outMs = max(inMs + 100L, min(currentMs, duration)).coerceAtMost(duration)
        status = "Ponto final definido em ${formatTime(outMs)}"
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
        exported = null
        mediaFilter = ProjectMediaFilter.ALL
        status = "Analisando ${chosen.name}..."
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
                    status = "Vídeo pronto para preview e timeline: ${chosen.name}"
                    preview.load(file)
                },
                onFailure = { status = it.message ?: "Falha ao preparar o preview." }
            )
        }
    }

    fun exportCut() {
        val source = input ?: run {
            status = "Abra um vídeo antes de exportar."
            return
        }
        if (busy || outMs <= inMs) return
        preview.pause()
        busy = true
        progress = 0f
        status = "Exportando trecho selecionado..."
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
            Brush.radialGradient(listOf(Color(0xFF102A43), VE_BG), radius = 1450f)
        )
    ) {
        Column(
            Modifier.fillMaxSize().verticalScroll(pageScroll).padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            VideoMasterTopBar(
                onOpen = ::openVideo,
                onBack = onBack,
                onNotImplemented = ::showNotImplemented
            )

            Row(Modifier.fillMaxWidth().heightIn(min = 650.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LeftModuleRail(
                    onOpen = ::openVideo,
                    onCut = ::exportCut,
                    onNotImplemented = ::showNotImplemented
                )

                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth().height(560.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ProjectMediaPanel(
                            input = input,
                            info = info,
                            filter = mediaFilter,
                            onFilter = { mediaFilter = it },
                            onOpen = ::openVideo,
                            onNotImplemented = ::showNotImplemented,
                            modifier = Modifier.width(390.dp).fillMaxHeight()
                        )

                        PreviewStudioPanel(
                            input = input,
                            info = info,
                            preview = preview,
                            currentMs = currentMs,
                            inMs = inMs,
                            outMs = outMs,
                            playing = playing,
                            busy = busy,
                            onOpen = ::openVideo,
                            onPlayPause = {
                                if (input != null && !busy) {
                                    if (playing) preview.pause() else {
                                        if (outMs > inMs && currentMs >= outMs - 40L) seekTo(inMs)
                                        preview.play()
                                    }
                                }
                            },
                            onSeek = ::seekTo,
                            onBackFive = { seekBy(-5000L) },
                            onForwardFive = { seekBy(5000L) },
                            onNotImplemented = ::showNotImplemented,
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )
                    }

                    ProfessionalTimelinePanel(
                        input = input,
                        info = info,
                        durationMs = info?.durationMs ?: 0L,
                        currentMs = currentMs,
                        inMs = inMs,
                        outMs = outMs,
                        enabled = input != null && !busy,
                        onSeek = ::seekTo,
                        onMarkIn = ::markIn,
                        onMarkOut = ::markOut,
                        onExport = ::exportCut,
                        onNotImplemented = ::showNotImplemented,
                        modifier = Modifier.fillMaxWidth().height(235.dp)
                    )

                    QuickActionBar(
                        hasVideo = input != null,
                        busy = busy,
                        onCut = ::exportCut,
                        onOpen = ::openVideo,
                        onNotImplemented = ::showNotImplemented
                    )
                }
            }

            BottomStatusBar(
                status = status,
                busy = busy,
                progress = progress,
                info = info,
                exported = exported,
                onExport = ::exportCut,
                onOpenFolder = {
                    engine.openExportsFolder().onFailure {
                        status = it.message ?: "Não foi possível abrir a pasta."
                    }
                },
                onCancel = {
                    engine.cancel()
                    busy = false
                    preview.pause()
                    status = "Operação cancelada."
                }
            )
        }
    }
}

@Composable
private fun VideoMasterTopBar(onOpen: () -> Unit, onBack: () -> Unit, onNotImplemented: (String) -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(76.dp).clip(RoundedCornerShape(8.dp)).background(VE_TOP)
            .border(1.dp, VE_BORDER_SOFT, RoundedCornerShape(8.dp)).padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("▥", color = VE_BLUE_2, fontSize = 42.sp, fontWeight = FontWeight.Light)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.width(480.dp)) {
            Text("VideoMaster PRO", color = VE_TEXT, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("Editor de Vídeo em tela cheia • Preview • Timeline • Corte por IN/OUT", color = VE_MUTED, fontSize = 13.sp)
        }
        Spacer(Modifier.weight(1f))
        TopToolbarButton("▭", "Abrir Vídeo", onClick = onOpen)
        TopToolbarButton("▣", "Salvar Projeto", onClick = { onNotImplemented("Salvar Projeto") })
        TopToolbarButton("⚙", "Configurações", onClick = { onNotImplemented("Configurações") })
        Spacer(Modifier.width(20.dp))
        OutlinedButton(
            onClick = onBack,
            border = BorderStroke(1.dp, VE_BORDER),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = VE_TEXT),
            modifier = Modifier.height(42.dp)
        ) { Text("Voltar ao Monitor", fontSize = 12.sp) }
    }
}

@Composable
private fun TopToolbarButton(symbol: String, label: String, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = VE_PANEL_2, contentColor = VE_TEXT),
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier.height(46.dp).padding(horizontal = 4.dp)
    ) {
        Text(symbol, color = VE_TEXT, fontSize = 16.sp)
        Spacer(Modifier.width(8.dp))
        Text(label, fontSize = 12.sp)
    }
}

@Composable
private fun LeftModuleRail(onOpen: () -> Unit, onCut: () -> Unit, onNotImplemented: (String) -> Unit) {
    Column(
        Modifier.width(190.dp).fillMaxHeight().clip(RoundedCornerShape(8.dp)).background(Color(0xE80A1524))
            .border(1.dp, VE_BORDER_SOFT, RoundedCornerShape(8.dp)).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ModuleButton("✂", "Editor de Vídeo", selected = true, onClick = onOpen)
        ModuleButton("⇩", "Extração", selected = false, onClick = { onNotImplemented("Extração dentro do Editor de Vídeo") })
        ModuleButton("▤", "Compactação", selected = false, onClick = { onNotImplemented("Compactação dentro do Editor de Vídeo") })
        ModuleButton("✄", "Corte", selected = false, onClick = onCut)
        ModuleButton("▣", "Unir Vídeos", selected = false, onClick = { onNotImplemented("Unir Vídeos") })
        ModuleButton("↻", "Converter", selected = false, onClick = { onNotImplemented("Converter") })
        Spacer(Modifier.weight(1f))
        Surface(color = Color(0x55121F31), shape = RoundedCornerShape(8.dp), border = BorderStroke(1.dp, VE_BORDER_SOFT)) {
            Column(Modifier.fillMaxWidth().padding(10.dp)) {
                Text("Funcional agora", color = VE_GREEN, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text("Abrir vídeo", color = VE_MUTED, fontSize = 11.sp)
                Text("Preview interno", color = VE_MUTED, fontSize = 11.sp)
                Text("Timeline + IN/OUT", color = VE_MUTED, fontSize = 11.sp)
                Text("Exportar corte MP4", color = VE_MUTED, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun ModuleButton(symbol: String, label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(56.dp).clip(RoundedCornerShape(7.dp))
            .background(if (selected) VE_BLUE else Color.Transparent)
            .clickable(onClick = onClick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(symbol, color = if (selected) Color.White else VE_TEXT, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(12.dp))
        Text(label, color = if (selected) Color.White else VE_TEXT, fontSize = 14.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun ProjectMediaPanel(
    input: File?,
    info: VideoEditorMediaInfo?,
    filter: ProjectMediaFilter,
    onFilter: (ProjectMediaFilter) -> Unit,
    onOpen: () -> Unit,
    onNotImplemented: (String) -> Unit,
    modifier: Modifier
) {
    Panel(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Mídia do Projeto", color = VE_TEXT, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text("vídeo único", color = VE_FADED, fontSize = 11.sp)
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MediaAction("⇩", "Importar", onClick = onOpen, modifier = Modifier.weight(1f))
            MediaAction("●", "Gravar", onClick = { onNotImplemented("Gravar") }, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ProjectMediaFilter.values().forEach { item ->
                SmallTab(item.label, selected = filter == item, onClick = { onFilter(item) })
            }
        }
        Spacer(Modifier.height(12.dp))
        val showVideo = filter == ProjectMediaFilter.ALL || filter == ProjectMediaFilter.VIDEOS
        if (input != null && showVideo) {
            MediaCard(input, info, Modifier.fillMaxWidth())
            Spacer(Modifier.height(12.dp))
            LabelValue("Duração", formatTime(info?.durationMs ?: 0L), VE_GREEN)
            LabelValue("Resolução", "${info?.width ?: 0}×${info?.height ?: 0}", VE_BLUE_2)
            LabelValue("Codec", info?.videoCodec?.uppercase() ?: "VIDEO", VE_CYAN)
            LabelValue("Formato", info?.format ?: input.extension.uppercase(), VE_MUTED)
            Spacer(Modifier.height(12.dp))
            Text("Outras mídias, múltiplos vídeos, imagens, texto e áudio editável ainda não existem no motor atual.", color = VE_FADED, fontSize = 11.sp)
        } else {
            Box(
                Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(8.dp)).background(Color(0x800A1320))
                    .border(1.dp, VE_BORDER_SOFT, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("▥", color = VE_FADED, fontSize = 42.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(if (input == null) "Nenhuma mídia importada" else "Filtro sem mídia compatível", color = VE_TEXT, fontWeight = FontWeight.Bold)
                    Text("O motor atual importa vídeo único para corte.", color = VE_MUTED, fontSize = 11.sp)
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = onOpen, colors = ButtonDefaults.buttonColors(containerColor = VE_BLUE)) { Text("Importar vídeo") }
                }
            }
        }
    }
}

@Composable
private fun MediaAction(symbol: String, label: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = VE_PANEL_3, contentColor = VE_TEXT),
        shape = RoundedCornerShape(7.dp),
        modifier = modifier.height(46.dp)
    ) {
        Text(symbol, fontSize = 15.sp)
        Spacer(Modifier.width(7.dp))
        Text(label, fontSize = 11.sp, maxLines = 1)
    }
}

@Composable
private fun SmallTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        label,
        color = if (selected) VE_TEXT else VE_MUTED,
        fontSize = 12.sp,
        modifier = Modifier.clip(RoundedCornerShape(5.dp))
            .background(if (selected) Color(0xFF112A44) else Color.Transparent)
            .border(1.dp, if (selected) VE_BLUE else Color.Transparent, RoundedCornerShape(5.dp))
            .clickable(onClick = onClick).padding(horizontal = 11.dp, vertical = 8.dp)
    )
}

@Composable
private fun MediaCard(file: File, info: VideoEditorMediaInfo?, modifier: Modifier) {
    Column(modifier.height(150.dp).clip(RoundedCornerShape(7.dp)).background(VE_PANEL_2).border(1.dp, VE_BORDER_SOFT, RoundedCornerShape(7.dp))) {
        Box(Modifier.fillMaxWidth().height(96.dp).background(Brush.horizontalGradient(listOf(Color(0xFF0C7EBC), Color(0xFF14345B)))), contentAlignment = Alignment.Center) {
            Text("VÍDEO", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Surface(color = Color(0xAA000000), shape = RoundedCornerShape(4.dp), modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp)) {
                Text(formatTime(info?.durationMs ?: 0L), color = Color.White, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
            }
        }
        Spacer(Modifier.height(7.dp))
        Text(file.name, color = VE_TEXT, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 8.dp))
        Text("${info?.width ?: 0}×${info?.height ?: 0} • ${info?.videoCodec ?: "vídeo"}", color = VE_MUTED, fontSize = 10.sp, maxLines = 1, modifier = Modifier.padding(horizontal = 8.dp))
    }
}

@Composable
private fun PreviewStudioPanel(
    input: File?,
    info: VideoEditorMediaInfo?,
    preview: VideoEditorPreviewController,
    currentMs: Long,
    inMs: Long,
    outMs: Long,
    playing: Boolean,
    busy: Boolean,
    onOpen: () -> Unit,
    onPlayPause: () -> Unit,
    onSeek: (Long) -> Unit,
    onBackFive: () -> Unit,
    onForwardFive: () -> Unit,
    onNotImplemented: (String) -> Unit,
    modifier: Modifier
) {
    Panel(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Pré-visualização", color = VE_TEXT, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(12.dp))
            Text("${info?.width ?: 0}×${info?.height ?: 0}", color = VE_MUTED, fontSize = 12.sp)
            Spacer(Modifier.weight(1f))
            Surface(color = Color(0xFF09121F), shape = RoundedCornerShape(5.dp), border = BorderStroke(1.dp, VE_BORDER_SOFT)) {
                Text("Preview interno", color = VE_MUTED, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text("⛶", color = VE_TEXT, fontSize = 18.sp, modifier = Modifier.clickable { onNotImplemented("Tela cheia") })
        }
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(5.dp)).background(Color.Black).border(1.dp, VE_BORDER, RoundedCornerShape(5.dp)), contentAlignment = Alignment.Center) {
            if (input == null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("▥", color = VE_FADED, fontSize = 64.sp)
                    Text("Nenhum vídeo aberto", color = VE_TEXT, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    Text("Use Importar para carregar um vídeo", color = VE_MUTED, fontSize = 13.sp)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onOpen, colors = ButtonDefaults.buttonColors(containerColor = VE_BLUE)) { Text("Abrir vídeo") }
                }
            } else {
                VideoEditorPreview(preview, Modifier.fillMaxSize().padding(1.dp))
                Surface(color = Color(0xAA08101C), shape = RoundedCornerShape(4.dp), modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp)) {
                    Text("Trecho: ${formatTime(inMs)} até ${formatTime(outMs)}", color = VE_TEXT, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(formatTime(currentMs), color = VE_TEXT, fontSize = 12.sp)
            Slider(
                value = currentMs.coerceIn(0L, max(1L, info?.durationMs ?: 0L)).toFloat(),
                onValueChange = { onSeek(it.toLong()) },
                enabled = input != null && !busy,
                valueRange = 0f..max(1L, info?.durationMs ?: 0L).toFloat(),
                colors = SliderDefaults.colors(thumbColor = VE_BLUE_2, activeTrackColor = VE_BLUE, inactiveTrackColor = Color(0xFF33435A)),
                modifier = Modifier.weight(1f).padding(horizontal = 12.dp)
            )
            Text(formatTime(info?.durationMs ?: 0L), color = VE_TEXT, fontSize = 12.sp)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            PlayerButton("◀ 5s", enabled = input != null && !busy, onClick = onBackFive)
            PlayerButton(if (playing) "Ⅱ" else "▶", enabled = input != null && !busy, onClick = onPlayPause, large = true)
            PlayerButton("5s ▶", enabled = input != null && !busy, onClick = onForwardFive)
            Spacer(Modifier.width(22.dp))
            PlayerButton("🔊", enabled = false, onClick = { onNotImplemented("Controle de volume") })
            PlayerButton("▣", enabled = false, onClick = { onNotImplemented("Captura de frame") })
        }
    }
}

@Composable
private fun PlayerButton(label: String, enabled: Boolean, onClick: () -> Unit, large: Boolean = false) {
    Text(
        label,
        color = if (enabled) VE_TEXT else VE_FADED,
        fontSize = if (large) 31.sp else 17.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 15.dp).clickable(enabled = enabled, onClick = onClick)
    )
}

@Composable
private fun ProfessionalTimelinePanel(
    input: File?,
    info: VideoEditorMediaInfo?,
    durationMs: Long,
    currentMs: Long,
    inMs: Long,
    outMs: Long,
    enabled: Boolean,
    onSeek: (Long) -> Unit,
    onMarkIn: () -> Unit,
    onMarkOut: () -> Unit,
    onExport: () -> Unit,
    onNotImplemented: (String) -> Unit,
    modifier: Modifier
) {
    val scroll = rememberScrollState()
    val safeDuration = max(1L, durationMs)
    Panel(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Timeline", color = VE_TEXT, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(28.dp))
            TimelineTool("↶", enabled = false, onClick = { onNotImplemented("Desfazer") })
            TimelineTool("↷", enabled = false, onClick = { onNotImplemented("Refazer") })
            TimelineTool("✂", enabled = enabled, onClick = { onMarkIn(); onMarkOut() })
            Spacer(Modifier.weight(1f))
            Text(formatTime(currentMs), color = VE_BLUE_2, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("00:00.000", color = VE_MUTED, fontSize = 10.sp)
            Slider(
                value = currentMs.coerceIn(0L, safeDuration).toFloat(),
                onValueChange = { onSeek(it.toLong()) },
                enabled = enabled,
                valueRange = 0f..safeDuration.toFloat(),
                colors = SliderDefaults.colors(thumbColor = VE_BLUE_2, activeTrackColor = VE_BLUE, inactiveTrackColor = Color(0xFF33435A)),
                modifier = Modifier.weight(1f).padding(horizontal = 10.dp)
            )
            Text(formatTime(durationMs), color = VE_MUTED, fontSize = 10.sp)
        }
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.width(170.dp)) {
                Spacer(Modifier.height(28.dp))
                TrackHeader("▭", "Vídeo 1", supported = input != null, onNotImplemented = onNotImplemented)
                TrackHeader("♫", "Áudio 1", supported = input != null, onNotImplemented = onNotImplemented)
            }
            Column(Modifier.weight(1f).horizontalScroll(scroll)) {
                TimelineRuler(durationMs)
                TimelineTrack(durationMs, currentMs, enabled, onSeek) {
                    if (input != null) RealVideoClip(input, info, inMs, outMs) else GhostClip("Aguardando vídeo", VE_BORDER)
                }
                TimelineTrack(durationMs, currentMs, enabled, onSeek) {
                    if (input != null) GhostClip("Áudio original do vídeo", Color(0xFF0B777B)) else GhostClip("Aguardando áudio do vídeo", Color(0xFF0B777B))
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TimeChip("IN", inMs, VE_GREEN)
            Spacer(Modifier.width(8.dp))
            TimeChip("OUT", outMs, VE_RED)
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = onMarkIn, enabled = enabled, border = BorderStroke(1.dp, VE_GREEN), modifier = Modifier.height(36.dp)) { Text("Definir início", color = VE_GREEN, fontSize = 11.sp) }
            OutlinedButton(onClick = onMarkOut, enabled = enabled, border = BorderStroke(1.dp, VE_RED), modifier = Modifier.height(36.dp)) { Text("Definir fim", color = VE_RED, fontSize = 11.sp) }
            Spacer(Modifier.weight(1f))
            Button(onClick = onExport, enabled = enabled && outMs > inMs, colors = ButtonDefaults.buttonColors(containerColor = VE_BLUE), modifier = Modifier.height(38.dp)) { Text("Exportar trecho") }
        }
    }
}

@Composable
private fun TimelineTool(label: String, enabled: Boolean, onClick: () -> Unit) {
    Text(label, color = if (enabled) VE_TEXT else VE_FADED, fontSize = 17.sp, modifier = Modifier.padding(horizontal = 11.dp).clickable(enabled = enabled, onClick = onClick))
}

@Composable
private fun TrackHeader(symbol: String, label: String, supported: Boolean, onNotImplemented: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().height(42.dp).border(1.dp, VE_BORDER_SOFT).clickable { if (!supported) onNotImplemented(label) }, verticalAlignment = Alignment.CenterVertically) {
        Text(symbol, color = if (supported) VE_TEXT else VE_FADED, fontSize = 14.sp, modifier = Modifier.padding(start = 10.dp).width(26.dp))
        Text(label, color = if (supported) VE_TEXT else VE_MUTED, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Text("◉", color = if (supported) VE_TEXT else VE_FADED, fontSize = 11.sp, modifier = Modifier.padding(end = 9.dp))
    }
}

@Composable
private fun TimelineRuler(durationMs: Long) {
    Row(Modifier.width(980.dp).height(28.dp).background(Color(0x660A1320)).border(1.dp, VE_BORDER_SOFT), verticalAlignment = Alignment.Bottom) {
        val safeDuration = max(1L, durationMs)
        for (i in 0..6) {
            val t = safeDuration * i / 6
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                Text(formatTime(t), color = VE_MUTED, fontSize = 10.sp, modifier = Modifier.padding(start = 4.dp))
                Box(Modifier.width(1.dp).height(8.dp).background(VE_BORDER))
            }
        }
    }
}

@Composable
private fun TimelineTrack(durationMs: Long, currentMs: Long, enabled: Boolean, onSeek: (Long) -> Unit, content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.width(980.dp).height(42.dp).background(Color(0x440A1320)).border(1.dp, VE_BORDER_SOFT)) {
        Row(Modifier.fillMaxSize()) { repeat(8) { Box(Modifier.weight(1f).fillMaxHeight().border(1.dp, Color(0x22293A52))) } }
        Box(Modifier.fillMaxSize().clickable(enabled = enabled) { onSeek(currentMs.coerceIn(0L, max(1L, durationMs))) }, content = content)
        val fraction = if (durationMs > 0L) currentMs.toFloat() / durationMs.toFloat() else 0f
        Box(Modifier.offset(x = (960f * fraction.coerceIn(0f, 1f)).dp).width(2.dp).fillMaxHeight().background(VE_BLUE_2))
    }
}

@Composable
private fun BoxScope.RealVideoClip(file: File, info: VideoEditorMediaInfo?, inMs: Long, outMs: Long) {
    Row(
        Modifier.align(Alignment.CenterStart).padding(start = 70.dp).width(430.dp).height(32.dp).clip(RoundedCornerShape(4.dp))
            .background(Brush.horizontalGradient(listOf(Color(0xFF168FFF), Color(0xFF123D6A))))
            .border(1.dp, VE_BLUE_2, RoundedCornerShape(4.dp)).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("▥", color = VE_TEXT, fontSize = 16.sp)
        Spacer(Modifier.width(6.dp))
        Text(file.name, color = VE_TEXT, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text("${formatTime(inMs)}-${formatTime(outMs)}", color = VE_TEXT, fontSize = 9.sp)
    }
}

@Composable
private fun BoxScope.GhostClip(text: String, color: Color) {
    Box(
        Modifier.align(Alignment.CenterStart).padding(start = 70.dp).width(430.dp).height(30.dp).clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.25f)).border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(4.dp)),
        contentAlignment = Alignment.Center
    ) { Text(text, color = VE_FADED, fontSize = 10.sp) }
}

@Composable
private fun QuickActionBar(hasVideo: Boolean, busy: Boolean, onCut: () -> Unit, onOpen: () -> Unit, onNotImplemented: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().height(74.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        QuickCard("✂", "Cortar Vídeo", "Remove trecho por IN/OUT", VE_GREEN, hasVideo && !busy, onClick = onCut, modifier = Modifier.weight(1f))
        QuickCard("▣", "Unir Vídeos", "Ainda não existe", VE_PURPLE, true, onClick = { onNotImplemented("Unir Vídeos") }, modifier = Modifier.weight(1f))
        QuickCard("⇩", "Extrair", "Ainda não existe", VE_BLUE, true, onClick = { onNotImplemented("Extrair") }, modifier = Modifier.weight(1f))
        QuickCard("▤", "Compactar", "Ainda não existe", VE_ORANGE, true, onClick = { onNotImplemented("Compactar") }, modifier = Modifier.weight(1f))
        QuickCard("↻", "Converter", "Ainda não existe", VE_BLUE, true, onClick = { onNotImplemented("Converter") }, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun QuickCard(symbol: String, title: String, detail: String, accent: Color, enabled: Boolean, onClick: () -> Unit, modifier: Modifier) {
    Row(
        modifier.fillMaxHeight().clip(RoundedCornerShape(8.dp)).background(VE_PANEL_2).border(1.dp, VE_BORDER_SOFT, RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(52.dp).clip(RoundedCornerShape(8.dp)).background(accent), contentAlignment = Alignment.Center) { Text(symbol, color = Color.White, fontSize = 25.sp, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, color = VE_TEXT, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(detail, color = VE_MUTED, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun BottomStatusBar(status: String, busy: Boolean, progress: Float, info: VideoEditorMediaInfo?, exported: File?, onExport: () -> Unit, onOpenFolder: () -> Unit, onCancel: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(66.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xEE07111F))
            .border(1.dp, VE_BORDER_SOFT, RoundedCornerShape(8.dp)).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("VideoMaster PRO v1.1", color = VE_TEXT, fontSize = 12.sp)
        Spacer(Modifier.width(18.dp))
        Text(if (busy) "● Processando" else "● Pronto", color = if (busy) VE_BLUE_2 else VE_GREEN, fontSize = 12.sp)
        Spacer(Modifier.width(14.dp))
        Text(status, color = if (exported != null) VE_GREEN else VE_MUTED, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (busy) {
            LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.width(160.dp).height(6.dp).clip(RoundedCornerShape(6.dp)), color = VE_BLUE_2, trackColor = VE_BORDER)
            Spacer(Modifier.width(12.dp))
            Text("${(progress.coerceIn(0f, 1f) * 100).toInt()}%", color = VE_TEXT, fontSize = 11.sp)
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = onCancel, border = BorderStroke(1.dp, VE_RED), modifier = Modifier.height(38.dp)) { Text("Cancelar", color = VE_RED, fontSize = 12.sp) }
        }
        Spacer(Modifier.width(16.dp))
        Surface(color = VE_PANEL_2, shape = RoundedCornerShape(6.dp), border = BorderStroke(1.dp, VE_BORDER_SOFT)) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                Text("H.264", color = VE_MUTED, fontSize = 11.sp)
                Text("MP4", color = VE_MUTED, fontSize = 11.sp)
                Text("${info?.width ?: 0}×${info?.height ?: 0}", color = VE_MUTED, fontSize = 11.sp)
            }
        }
        Spacer(Modifier.width(12.dp))
        Button(onClick = onExport, enabled = info != null && !busy, colors = ButtonDefaults.buttonColors(containerColor = VE_BLUE), shape = RoundedCornerShape(8.dp), modifier = Modifier.height(48.dp).width(190.dp)) { Text("↥ Exportar Vídeo", color = Color.White, fontSize = 15.sp) }
        Spacer(Modifier.width(8.dp))
        OutlinedButton(onClick = onOpenFolder, border = BorderStroke(1.dp, VE_BORDER), modifier = Modifier.height(48.dp)) { Text("Pasta", color = VE_MUTED, fontSize = 12.sp) }
    }
}

@Composable
private fun Panel(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier.clip(RoundedCornerShape(8.dp)).background(VE_PANEL).border(1.dp, VE_BORDER_SOFT, RoundedCornerShape(8.dp)).padding(14.dp),
        content = content
    )
}

@Composable
private fun TimeChip(label: String, value: Long, color: Color) {
    Surface(color = Color(0xFF101E30), shape = RoundedCornerShape(6.dp), border = BorderStroke(1.dp, color)) {
        Row(Modifier.padding(horizontal = 9.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = color, fontWeight = FontWeight.Bold, fontSize = 10.sp)
            Spacer(Modifier.width(7.dp))
            Text(formatTime(value), color = VE_TEXT, fontSize = 11.sp)
        }
    }
}

@Composable
private fun LabelValue(label: String, value: String, accent: Color) {
    Surface(color = VE_PANEL_2, shape = RoundedCornerShape(7.dp), border = BorderStroke(1.dp, VE_BORDER_SOFT), modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = VE_MUTED, fontSize = 10.sp)
            Spacer(Modifier.weight(1f))
            Text(value, color = accent, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
