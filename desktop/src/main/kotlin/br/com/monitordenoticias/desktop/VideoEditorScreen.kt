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
private enum class InspectorTab(val label: String) { VIDEO("Vídeo"), AUDIO("Áudio"), EFFECTS("Efeitos"), ADJUST("Ajustes") }

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
    var inspectorTab by remember { mutableStateOf(InspectorTab.VIDEO) }

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

    fun showNotImplemented(feature: String) {
        status = "Esta função não existe no motor atual: $feature."
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
        mediaFilter = ProjectMediaFilter.ALL
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
        val source = input ?: run {
            status = "Abra um vídeo antes de exportar."
            return
        }
        if (busy || outMs <= inMs) return
        preview.pause()
        busy = true
        progress = 0f
        status = "Exportando trecho selecionado com o motor atual..."
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

    fun seekBy(deltaMs: Long) {
        val duration = info?.durationMs ?: 0L
        if (duration <= 0L) return
        val target = (currentMs + deltaMs).coerceIn(0L, duration)
        currentMs = target
        preview.seek(target)
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

            Row(Modifier.fillMaxWidth().heightIn(min = 690.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LeftModuleRail(
                    onOpen = ::openVideo,
                    onCut = ::exportCut,
                    onNotImplemented = ::showNotImplemented
                )

                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(Modifier.fillMaxWidth().height(430.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ProjectMediaPanel(
                            input = input,
                            info = info,
                            filter = mediaFilter,
                            onFilter = { mediaFilter = it },
                            onOpen = ::openVideo,
                            onNotImplemented = ::showNotImplemented,
                            modifier = Modifier.width(450.dp).fillMaxHeight()
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
                                    if (playing) preview.pause()
                                    else {
                                        if (outMs > inMs && currentMs >= outMs - 40L) preview.seek(inMs)
                                        preview.play()
                                    }
                                }
                            },
                            onSeek = {
                                currentMs = it
                                preview.seek(it)
                            },
                            onBackFive = { seekBy(-5000L) },
                            onForwardFive = { seekBy(5000L) },
                            onNotImplemented = ::showNotImplemented,
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        )

                        InspectorPanel(
                            tab = inspectorTab,
                            onTab = { inspectorTab = it },
                            info = info,
                            inMs = inMs,
                            outMs = outMs,
                            onNotImplemented = ::showNotImplemented,
                            modifier = Modifier.width(300.dp).fillMaxHeight()
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
                        onSeek = {
                            currentMs = it
                            preview.seek(it)
                        },
                        onMarkIn = {
                            val maxIn = max(0L, outMs - 100L)
                            inMs = min(currentMs, maxIn)
                            status = "Ponto inicial definido em ${formatTime(inMs)}"
                        },
                        onMarkOut = {
                            val duration = info?.durationMs ?: 0L
                            outMs = max(inMs + 100L, min(currentMs, duration)).coerceAtMost(duration)
                            status = "Ponto final definido em ${formatTime(outMs)}"
                        },
                        onExport = ::exportCut,
                        onNotImplemented = ::showNotImplemented,
                        modifier = Modifier.fillMaxWidth().height(280.dp)
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
                }
            )
        }
    }
}

@Composable
private fun VideoMasterTopBar(
    onOpen: () -> Unit,
    onBack: () -> Unit,
    onNotImplemented: (String) -> Unit
) {
    Row(
        Modifier.fillMaxWidth().height(76.dp).clip(RoundedCornerShape(8.dp)).background(VE_TOP)
            .border(1.dp, VE_BORDER_SOFT, RoundedCornerShape(8.dp)).padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("▥", color = VE_BLUE_2, fontSize = 42.sp, fontWeight = FontWeight.Light)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.width(430.dp)) {
            Text("VideoMaster PRO", color = VE_TEXT, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text("Editor • Extrator • Conversor • Compactador • Tudo em um só lugar", color = VE_MUTED, fontSize = 13.sp)
        }
        Spacer(Modifier.weight(1f))
        TopToolbarButton("▭", "Abrir Projeto", onOpen)
        TopToolbarButton("▣", "Salvar Projeto") { onNotImplemented("Salvar Projeto") }
        TopToolbarButton("⚙", "Configurações") { onNotImplemented("Configurações") }
        Spacer(Modifier.width(22.dp))
        Text("—", color = VE_MUTED, fontSize = 20.sp)
        Spacer(Modifier.width(18.dp))
        Text("□", color = VE_MUTED, fontSize = 17.sp)
        Spacer(Modifier.width(18.dp))
        Text("×", color = VE_MUTED, fontSize = 23.sp)
        Spacer(Modifier.width(18.dp))
        OutlinedButton(
            onClick = onBack,
            border = BorderStroke(1.dp, VE_BORDER),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = VE_MUTED),
            modifier = Modifier.height(38.dp)
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
private fun LeftModuleRail(
    onOpen: () -> Unit,
    onCut: () -> Unit,
    onNotImplemented: (String) -> Unit
) {
    Column(
        Modifier.width(190.dp).fillMaxHeight().clip(RoundedCornerShape(8.dp)).background(Color(0xE80A1524))
            .border(1.dp, VE_BORDER_SOFT, RoundedCornerShape(8.dp)).padding(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        ModuleButton("✂", "Editor de Vídeo", true) { onOpen() }
        ModuleButton("⇩", "Extração", false) { onNotImplemented("Extração dentro do Editor de Vídeo") }
        ModuleButton("▤", "Compactação", false) { onNotImplemented("Compactação dentro do Editor de Vídeo") }
        ModuleButton("✄", "Corte", false) { onCut() }
        ModuleButton("▣", "Unir Vídeos", false) { onNotImplemented("Unir Vídeos") }
        ModuleButton("↻", "Converter", false) { onNotImplemented("Converter") }
        Spacer(Modifier.height(18.dp))
        HorizontalDivider(color = VE_BORDER)
        ToolLabel("◒", "Mídia", true) { onOpen() }
        ToolLabel("✥", "Efeitos", false) { onNotImplemented("Efeitos") }
        ToolLabel("◧", "Transições", false) { onNotImplemented("Transições") }
        ToolLabel("♫", "Áudio", false) { onNotImplemented("Áudio como faixa editável") }
        ToolLabel("T", "Texto", false) { onNotImplemented("Texto/legendas") }
        ToolLabel("⌁", "Mais", false) { onNotImplemented("Mais ferramentas") }
        Spacer(Modifier.weight(1f))
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("▥", color = VE_FADED, fontSize = 30.sp)
            Spacer(Modifier.height(8.dp))
            Text("Crie • Edite • Converta", color = VE_MUTED, fontSize = 11.sp)
            Text("Sem funções falsas", color = VE_FADED, fontSize = 11.sp)
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
        Text(symbol, color = VE_TEXT, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(12.dp))
        Text(label, color = VE_TEXT, fontSize = 14.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
private fun ToolLabel(symbol: String, label: String, supported: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().height(42.dp).clip(RoundedCornerShape(6.dp)).clickable(onClick = onClick).padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(symbol, color = if (supported) VE_MUTED else VE_FADED, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.width(14.dp))
        Text(label, color = if (supported) VE_MUTED else VE_FADED, fontSize = 14.sp)
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
            Text("↕", color = VE_MUTED, fontSize = 16.sp)
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MediaAction("⇩", "Importar", onOpen, Modifier.weight(1f))
            MediaAction("●", "Gravar") { onNotImplemented("Gravar") }
            MediaAction("▭", "Adicionar Pasta") { onNotImplemented("Adicionar Pasta") }
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ProjectMediaFilter.values().forEach { item ->
                SmallTab(item.label, filter == item) { onFilter(item) }
            }
        }
        Spacer(Modifier.height(12.dp))
        val showVideo = filter == ProjectMediaFilter.ALL || filter == ProjectMediaFilter.VIDEOS
        if (input != null && showVideo) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                MediaCard(input, info, Modifier.weight(1f))
                EmptyMediaCard("Próximo vídeo", "Unir vídeos não existe no motor atual", Modifier.weight(1f))
                EmptyMediaCard("Imagem/Logo", "Importação de imagem não existe", Modifier.weight(1f))
            }
            Spacer(Modifier.height(10.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                EmptyMediaCard("Áudio", "Faixa de áudio editável não existe", Modifier.weight(1f))
                EmptyMediaCard("Texto", "Legenda/texto não existe", Modifier.weight(1f))
                EmptyMediaCard("Pasta", "Adicionar pasta não existe", Modifier.weight(1f))
            }
        } else {
            Box(
                Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(8.dp)).background(Color(0x800A1320))
                    .border(1.dp, VE_BORDER_SOFT, RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("▥", color = VE_FADED, fontSize = 46.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (filter == ProjectMediaFilter.ALL || filter == ProjectMediaFilter.VIDEOS) "Nenhum vídeo importado" else "Esta função não existe no motor atual",
                        color = VE_TEXT,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        if (filter == ProjectMediaFilter.ALL || filter == ProjectMediaFilter.VIDEOS) "Clique em Importar para abrir MP4, MOV, MKV ou WEBM" else "O editor atual só importa vídeo para preview e corte.",
                        color = VE_MUTED,
                        fontSize = 12.sp
                    )
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
        shape = RoundedCornerShape(6.dp),
        modifier = modifier.height(46.dp)
    ) {
        Text(symbol, fontSize = 16.sp)
        Spacer(Modifier.width(8.dp))
        Text(label, fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
private fun SmallTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        color = if (selected) Color(0xFF102D4B) else Color.Transparent,
        shape = RoundedCornerShape(6.dp),
        border = BorderStroke(1.dp, if (selected) VE_BLUE else Color.Transparent),
        modifier = Modifier.height(34.dp).clickable(onClick = onClick)
    ) { Box(Modifier.padding(horizontal = 13.dp), contentAlignment = Alignment.Center) { Text(label, color = VE_TEXT, fontSize = 12.sp) } }
}

@Composable
private fun MediaCard(file: File, info: VideoEditorMediaInfo?, modifier: Modifier) {
    Column(
        modifier.height(132.dp).clip(RoundedCornerShape(7.dp)).background(Color(0xFF101B2A))
            .border(1.dp, VE_BORDER, RoundedCornerShape(7.dp)).padding(6.dp)
    ) {
        Box(Modifier.fillMaxWidth().height(78.dp).clip(RoundedCornerShape(5.dp)).background(Brush.linearGradient(listOf(Color(0xFF149AFB), Color(0xFF0A4E81)))), contentAlignment = Alignment.BottomEnd) {
            Text("▥", color = Color.White.copy(alpha = 0.72f), fontSize = 38.sp)
            Surface(color = Color(0xAA000000), shape = RoundedCornerShape(4.dp), modifier = Modifier.padding(5.dp)) {
                Text(formatTime(info?.durationMs ?: 0L), color = VE_TEXT, fontSize = 10.sp, modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp))
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(file.name, color = VE_TEXT, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text("Vídeo • ${info?.videoCodec?.uppercase() ?: "Aguardando"}", color = VE_MUTED, fontSize = 10.sp, maxLines = 1)
    }
}

@Composable
private fun EmptyMediaCard(title: String, detail: String, modifier: Modifier) {
    Column(
        modifier.height(132.dp).clip(RoundedCornerShape(7.dp)).background(Color(0xFF101B2A))
            .border(1.dp, VE_BORDER_SOFT, RoundedCornerShape(7.dp)).padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("＋", color = VE_FADED, fontSize = 26.sp)
        Text(title, color = VE_FADED, fontSize = 12.sp, maxLines = 1)
        Text(detail, color = VE_FADED, fontSize = 9.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
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
            Text("Pré-visualização", color = VE_TEXT, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Surface(color = Color(0xFF0A1320), shape = RoundedCornerShape(5.dp), border = BorderStroke(1.dp, VE_BORDER_SOFT)) {
                Text("1080p (Full HD) ⌄", color = VE_TEXT, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp))
            }
            Spacer(Modifier.width(12.dp))
            Text("⛶", color = VE_TEXT, fontSize = 20.sp, modifier = Modifier.clickable { onNotImplemented("Tela cheia") })
        }
        Spacer(Modifier.height(10.dp))
        Box(
            Modifier.fillMaxWidth().weight(1f).clip(RoundedCornerShape(4.dp)).background(Color.Black)
                .border(1.dp, Color(0xFF1C89C7), RoundedCornerShape(4.dp)),
            contentAlignment = Alignment.Center
        ) {
            if (input == null) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("▥", color = VE_FADED, fontSize = 68.sp)
                    Spacer(Modifier.height(10.dp))
                    Text("Nenhum vídeo aberto", color = VE_TEXT, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Text("O preview usa o player atual do programa", color = VE_MUTED, fontSize = 12.sp)
                    Spacer(Modifier.height(12.dp))
                    Button(onClick = onOpen, colors = ButtonDefaults.buttonColors(containerColor = VE_BLUE)) { Text("Importar vídeo") }
                }
            } else {
                VideoEditorPreview(preview, Modifier.fillMaxSize().padding(1.dp))
                Surface(color = Color(0xAA08101C), shape = RoundedCornerShape(4.dp), modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp)) {
                    Text("Trecho selecionado: ${formatTime(inMs)} até ${formatTime(outMs)}", color = VE_TEXT, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
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
            PlayerButton("◀", input != null && !busy, onBackFive)
            PlayerButton(if (playing) "Ⅱ" else "▶", input != null && !busy, onPlayPause, large = true)
            PlayerButton("▶", input != null && !busy, onForwardFive)
            Spacer(Modifier.width(26.dp))
            PlayerButton("🔊", false) { onNotImplemented("Controle de volume") }
            PlayerButton("▣", false) { onNotImplemented("Captura de frame") }
            PlayerButton("⛶", false) { onNotImplemented("Tela cheia") }
        }
    }
}

@Composable
private fun PlayerButton(label: String, enabled: Boolean, onClick: () -> Unit, large: Boolean = false) {
    Text(
        label,
        color = if (enabled) VE_TEXT else VE_FADED,
        fontSize = if (large) 31.sp else 23.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(horizontal = 15.dp).clickable(enabled = enabled, onClick = onClick)
    )
}

@Composable
private fun InspectorPanel(
    tab: InspectorTab,
    onTab: (InspectorTab) -> Unit,
    info: VideoEditorMediaInfo?,
    inMs: Long,
    outMs: Long,
    onNotImplemented: (String) -> Unit,
    modifier: Modifier
) {
    Panel(modifier) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            InspectorTab.values().forEach { item ->
                Text(
                    item.label,
                    color = if (tab == item) VE_BLUE_2 else VE_TEXT,
                    fontSize = 13.sp,
                    fontWeight = if (tab == item) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.clip(RoundedCornerShape(5.dp)).background(if (tab == item) Color(0xFF112A44) else Color.Transparent)
                        .clickable { onTab(item) }.padding(horizontal = 10.dp, vertical = 10.dp)
                )
            }
        }
        HorizontalDivider(color = VE_BORDER_SOFT)
        Spacer(Modifier.height(10.dp))
        when (tab) {
            InspectorTab.VIDEO -> {
                SectionTitle("⌄", "Transformação")
                DisabledSliderRow("Zoom", "100%") { onNotImplemented("Zoom") }
                DisabledPairRow("Posição", "X", "0", "Y", "0") { onNotImplemented("Posição X/Y") }
                DisabledSliderRow("Rotação", "0°") { onNotImplemented("Rotação") }
                DisabledPairRow("Espelhar", "H", "—", "V", "—") { onNotImplemented("Espelhamento") }
                Spacer(Modifier.height(10.dp))
                SectionTitle("⌄", "Ajustes de Cor")
                DisabledSliderRow("Brilho", "0") { onNotImplemented("Brilho") }
                DisabledSliderRow("Contraste", "0") { onNotImplemented("Contraste") }
                DisabledSliderRow("Saturação", "0") { onNotImplemented("Saturação") }
                DisabledSliderRow("Temperatura", "0") { onNotImplemented("Temperatura") }
                DisabledSliderRow("Matiz", "0") { onNotImplemented("Matiz") }
                Spacer(Modifier.height(8.dp))
                Text("Esta função não existe no motor atual para transformação/ajustes. O motor atual suporta preview, IN/OUT e exportação de corte.", color = VE_FADED, fontSize = 10.sp)
                Spacer(Modifier.height(10.dp))
                info?.let {
                    LabelValue("Resolução", "${it.width}×${it.height}", VE_BLUE_2)
                    LabelValue("Codec", it.videoCodec.uppercase(), VE_CYAN)
                    LabelValue("Trecho", formatTime((outMs - inMs).coerceAtLeast(0L)), VE_GREEN)
                }
            }
            InspectorTab.AUDIO -> NotImplementedPanel("Áudio editável/faixas de áudio", onNotImplemented)
            InspectorTab.EFFECTS -> NotImplementedPanel("Efeitos", onNotImplemented)
            InspectorTab.ADJUST -> NotImplementedPanel("Ajustes aplicados ao vídeo", onNotImplemented)
        }
    }
}

@Composable
private fun SectionTitle(symbol: String, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(symbol, color = VE_BLUE_2, fontSize = 14.sp)
        Spacer(Modifier.width(8.dp))
        Text(label, color = VE_TEXT, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        Text("⌃", color = VE_MUTED, fontSize = 12.sp)
    }
}

@Composable
private fun DisabledSliderRow(label: String, value: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(34.dp).clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = VE_MUTED, fontSize = 12.sp, modifier = Modifier.width(82.dp))
        Slider(
            value = 0.45f,
            onValueChange = {},
            enabled = false,
            colors = SliderDefaults.colors(disabledThumbColor = VE_FADED, disabledActiveTrackColor = VE_BORDER, disabledInactiveTrackColor = Color(0xFF263348)),
            modifier = Modifier.weight(1f)
        )
        Surface(color = Color(0xFF0A1320), shape = RoundedCornerShape(5.dp), border = BorderStroke(1.dp, VE_BORDER_SOFT)) {
            Text(value, color = VE_MUTED, fontSize = 11.sp, modifier = Modifier.width(52.dp).padding(vertical = 5.dp), maxLines = 1)
        }
    }
}

@Composable
private fun DisabledPairRow(title: String, a: String, av: String, b: String, bv: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().height(34.dp).clickable(onClick = onClick), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = VE_MUTED, fontSize = 12.sp, modifier = Modifier.width(82.dp))
        Text(a, color = VE_MUTED, fontSize = 11.sp)
        ValuePill(av)
        Spacer(Modifier.width(8.dp))
        Text(b, color = VE_MUTED, fontSize = 11.sp)
        ValuePill(bv)
    }
}

@Composable
private fun ValuePill(value: String) {
    Surface(color = Color(0xFF0A1320), shape = RoundedCornerShape(5.dp), border = BorderStroke(1.dp, VE_BORDER_SOFT), modifier = Modifier.padding(start = 6.dp)) {
        Text(value, color = VE_MUTED, fontSize = 11.sp, modifier = Modifier.width(42.dp).padding(vertical = 5.dp))
    }
}

@Composable
private fun NotImplementedPanel(feature: String, onNotImplemented: (String) -> Unit) {
    Box(Modifier.fillMaxSize().clip(RoundedCornerShape(8.dp)).background(Color(0x660A1320)).clickable { onNotImplemented(feature) }, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("⚠", color = VE_FADED, fontSize = 35.sp)
            Text("Esta função não existe", color = VE_TEXT, fontWeight = FontWeight.Bold)
            Text("no motor atual: $feature", color = VE_MUTED, fontSize = 11.sp)
        }
    }
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
    Panel(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Timeline", color = VE_TEXT, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(60.dp))
            TimelineTool("↶", false) { onNotImplemented("Desfazer") }
            TimelineTool("↷", false) { onNotImplemented("Refazer") }
            TimelineTool("✂", enabled) { onMarkIn(); onMarkOut() }
            TimelineTool("🛡", false) { onNotImplemented("Proteção/seleção de trecho") }
            TimelineTool("🗑", false) { onNotImplemented("Excluir") }
            TimelineTool("⌘", false) { onNotImplemented("Separar") }
            TimelineTool("↩", false) { onNotImplemented("Copiar/colar") }
            Spacer(Modifier.weight(1f))
            Text(formatTime(currentMs), color = VE_BLUE_2, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(12.dp))
            Text("Zoom", color = VE_MUTED, fontSize = 11.sp)
            Slider(value = 0.55f, onValueChange = {}, enabled = false, modifier = Modifier.width(120.dp))
            OutlinedButton(onClick = { onNotImplemented("Ajustar zoom da timeline") }, border = BorderStroke(1.dp, VE_BORDER), modifier = Modifier.height(34.dp)) { Text("Ajustar", color = VE_MUTED, fontSize = 11.sp) }
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth()) {
            Column(Modifier.width(205.dp)) {
                Spacer(Modifier.height(34.dp))
                TrackHeader("▭", "Vídeo 2", false, onNotImplemented)
                TrackHeader("▭", "Vídeo 1", input != null, onNotImplemented)
                TrackHeader("♫", "Áudio 1", false, onNotImplemented)
                TrackHeader("↕", "Áudio 2", false, onNotImplemented)
            }
            Column(Modifier.weight(1f).horizontalScroll(scroll)) {
                TimelineRuler(durationMs)
                TimelineTrack("Vídeo 2", durationMs, currentMs, enabled, onSeek) {
                    GhostClip("Faixa disponível visualmente", VE_BORDER)
                }
                TimelineTrack("Vídeo 1", durationMs, currentMs, enabled, onSeek) {
                    if (input != null) RealVideoClip(input, info, inMs, outMs) else GhostClip("Aguardando vídeo", VE_BORDER)
                }
                TimelineTrack("Áudio 1", durationMs, currentMs, enabled, onSeek) {
                    GhostClip("Waveform não existe no motor atual", Color(0xFF0B777B))
                }
                TimelineTrack("Áudio 2", durationMs, currentMs, enabled, onSeek) {
                    GhostClip("Faixa não implementada", VE_BORDER)
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
    Text(label, color = if (enabled) VE_TEXT else VE_FADED, fontSize = 17.sp, modifier = Modifier.padding(horizontal = 11.dp).clickable(onClick = onClick))
}

@Composable
private fun TrackHeader(symbol: String, label: String, supported: Boolean, onNotImplemented: (String) -> Unit) {
    Row(Modifier.fillMaxWidth().height(42.dp).border(1.dp, VE_BORDER_SOFT).clickable { if (!supported) onNotImplemented(label) }, verticalAlignment = Alignment.CenterVertically) {
        Text(symbol, color = if (supported) VE_TEXT else VE_FADED, fontSize = 14.sp, modifier = Modifier.padding(start = 10.dp).width(26.dp))
        Text(label, color = if (supported) VE_TEXT else VE_MUTED, fontSize = 12.sp, modifier = Modifier.weight(1f))
        Text("🔒", color = VE_FADED, fontSize = 11.sp, modifier = Modifier.padding(end = 9.dp))
        Text("◉", color = if (supported) VE_TEXT else VE_FADED, fontSize = 11.sp, modifier = Modifier.padding(end = 9.dp))
    }
}

@Composable
private fun TimelineRuler(durationMs: Long) {
    Row(Modifier.width(980.dp).height(34.dp).background(Color(0x660A1320)).border(1.dp, VE_BORDER_SOFT), verticalAlignment = Alignment.Bottom) {
        val safeDuration = max(1L, durationMs)
        for (i in 0..6) {
            val t = safeDuration * i / 6
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                Text(formatTime(t), color = VE_MUTED, fontSize = 10.sp, modifier = Modifier.padding(start = 4.dp))
                Box(Modifier.width(1.dp).height(10.dp).background(VE_BORDER))
            }
        }
    }
}

@Composable
private fun TimelineTrack(label: String, durationMs: Long, currentMs: Long, enabled: Boolean, onSeek: (Long) -> Unit, content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.width(980.dp).height(42.dp).background(Color(0x440A1320)).border(1.dp, VE_BORDER_SOFT)) {
        Row(Modifier.fillMaxSize()) { repeat(8) { Box(Modifier.weight(1f).fillMaxHeight().border(1.dp, Color(0x22293A52))) } }
        Box(Modifier.fillMaxSize().clickable(enabled = enabled) { onSeek(currentMs.coerceIn(0L, max(1L, durationMs))) }, content = content)
        val fraction = if (durationMs > 0L) currentMs.toFloat() / durationMs.toFloat() else 0f
        Box(Modifier.offset(x = (960f * fraction.coerceIn(0f, 1f)).dp).width(2.dp).fillMaxHeight().background(VE_BLUE_2))
    }
}

@Composable
private fun RealVideoClip(file: File, info: VideoEditorMediaInfo?, inMs: Long, outMs: Long) {
    Row(
        Modifier.align(Alignment.CenterStart).padding(start = 78.dp).width(360.dp).height(32.dp).clip(RoundedCornerShape(4.dp))
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
private fun GhostClip(text: String, color: Color) {
    Box(
        Modifier.align(Alignment.CenterStart).padding(start = 70.dp).width(330.dp).height(30.dp).clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.25f)).border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(4.dp)),
        contentAlignment = Alignment.Center
    ) { Text(text, color = VE_FADED, fontSize = 10.sp) }
}

@Composable
private fun QuickActionBar(
    hasVideo: Boolean,
    busy: Boolean,
    onCut: () -> Unit,
    onOpen: () -> Unit,
    onNotImplemented: (String) -> Unit
) {
    Row(Modifier.fillMaxWidth().height(74.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        QuickCard("✂", "Cortar Vídeo", "Remove trecho por IN/OUT", VE_GREEN, hasVideo && !busy, onCut, Modifier.weight(1f))
        QuickCard("▣", "Unir Vídeos", "Esta função não existe", VE_PURPLE, true, { onNotImplemented("Unir Vídeos") }, Modifier.weight(1f))
        QuickCard("⇩", "Extrair", "Esta função não existe", VE_BLUE, true, { onNotImplemented("Extrair") }, Modifier.weight(1f))
        QuickCard("▤", "Compactar", "Esta função não existe", VE_ORANGE, true, { onNotImplemented("Compactar") }, Modifier.weight(1f))
        QuickCard("↻", "Converter", "Esta função não existe", VE_BLUE, true, { onNotImplemented("Converter") }, Modifier.weight(1f))
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
private fun BottomStatusBar(
    status: String,
    busy: Boolean,
    progress: Float,
    info: VideoEditorMediaInfo?,
    exported: File?,
    onExport: () -> Unit,
    onOpenFolder: () -> Unit
) {
    Row(
        Modifier.fillMaxWidth().height(66.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xEE07111F))
            .border(1.dp, VE_BORDER_SOFT, RoundedCornerShape(8.dp)).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("VideoMaster PRO v1.0", color = VE_TEXT, fontSize = 12.sp)
        Spacer(Modifier.width(18.dp))
        Text(if (busy) "● Processando" else "● Pronto", color = if (busy) VE_BLUE_2 else VE_GREEN, fontSize = 12.sp)
        Spacer(Modifier.width(14.dp))
        Text(status, color = if (exported != null) VE_GREEN else VE_MUTED, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        if (busy) {
            LinearProgressIndicator(progress = { progress.coerceIn(0f, 1f) }, modifier = Modifier.width(160.dp).height(6.dp).clip(RoundedCornerShape(6.dp)), color = VE_BLUE_2, trackColor = VE_BORDER)
            Spacer(Modifier.width(12.dp))
            Text("${(progress.coerceIn(0f, 1f) * 100).toInt()}%", color = VE_TEXT, fontSize = 11.sp)
        }
        Spacer(Modifier.width(16.dp))
        Surface(color = VE_PANEL_2, shape = RoundedCornerShape(6.dp), border = BorderStroke(1.dp, VE_BORDER_SOFT)) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Text("Qualidade: H.264", color = VE_MUTED, fontSize = 11.sp)
                Text("Formato: MP4", color = VE_MUTED, fontSize = 11.sp)
                Text("Resolução: ${info?.width ?: 0}×${info?.height ?: 0}", color = VE_MUTED, fontSize = 11.sp)
                Text("Tamanho estimado: não existe no motor atual", color = VE_MUTED, fontSize = 11.sp)
            }
        }
        Spacer(Modifier.width(12.dp))
        Button(onClick = onExport, enabled = info != null && !busy, colors = ButtonDefaults.buttonColors(containerColor = VE_BLUE), shape = RoundedCornerShape(8.dp), modifier = Modifier.height(48.dp).width(210.dp)) { Text("↥  Exportar Vídeo", color = Color.White, fontSize = 16.sp) }
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
