package br.com.monitordenoticias.desktop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.io.FilenameFilter
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

private val VEBgDeep = Color(0xFF03172A)
private val VEBgDark = Color(0xFF041D33)
private val VEPanel = Color(0xFF062944)
private val VEPanel2 = Color(0xFF073758)
private val VENavy950 = Color(0xFF021421)
private val VECyan = Color(0xFF00BDF2)
private val VECyanBright = Color(0xFF15D4FF)
private val VECyanSoft = Color(0xFF77DFFF)
private val VEBlueBorder = Color(0xFF087FB4)
private val VEYellow = Color(0xFFFFC837)
private val VEYellowLight = Color(0xFFFFE36B)
private val VEYellowDark = Color(0xFFE9A91C)
private val VERed = Color(0xFFFF456C)
private val VEGreen = Color(0xFF26D67B)
private val VEText = Color(0xFFF3F8FF)
private val VEText2 = Color(0xFFA7CAE7)
private val VEMuted = Color(0xFF6894B8)
private val ZOOM_LEVELS = listOf(1.8f, 2.8f, 4.8f, 8.0f, 13.0f, 20.0f)

@Composable
fun VideoEditorScreen(onBack: () -> Unit = {}) {
    val scope = rememberCoroutineScope()
    val engine = remember { VideoEditorEngine() }
    val preview = remember { VideoPreviewController() }
    val clips = remember { mutableStateListOf<VideoClip>() }
    val undoStack = remember { mutableStateListOf<VideoEditorSnapshot>() }

    var selectedIndex by remember { mutableIntStateOf(-1) }
    var globalPlayheadMs by remember { mutableLongStateOf(0L) }
    var sequencePlaying by remember { mutableStateOf(false) }
    var zoomIndex by remember { mutableIntStateOf(2) }
    var startText by remember { mutableStateOf("00:00:00.000") }
    var endText by remember { mutableStateOf("00:00:00.000") }
    var durationText by remember { mutableStateOf("00:00:00.000") }
    var status by remember { mutableStateOf("Sistema pronto • Abra um ou mais vídeos para começar.") }
    var editingText by remember { mutableStateOf(false) }
    var volume by remember { mutableFloatStateOf(.72f) }

    var outputName by remember { mutableStateOf("video_final.mp4") }
    var resolution by remember { mutableStateOf(VideoResolution.ORIGINAL) }
    var codec by remember { mutableStateOf(VideoCodec.H265) }
    var targetSizeEnabled by remember { mutableStateOf(true) }
    var targetSizeMb by remember { mutableIntStateOf(30) }
    var exportBusy by remember { mutableStateOf(false) }
    var exportProgress by remember { mutableIntStateOf(0) }

    fun totalDuration(): Long = clips.sumOf { it.durationMs }
    fun prefixFor(index: Int): Long = clips.take(index.coerceAtLeast(0)).sumOf { it.durationMs }
    fun selectedClip(): VideoClip? = clips.getOrNull(selectedIndex)

    fun syncFields(clip: VideoClip?) {
        if (clip == null) {
            startText = "00:00:00.000"
            endText = "00:00:00.000"
            durationText = "00:00:00.000"
        } else {
            startText = formatVideoTime(clip.startMs)
            endText = formatVideoTime(clip.endMs)
            durationText = formatVideoTime(clip.durationMs)
        }
    }

    fun sourcePositionForGlobal(index: Int, globalMs: Long): Long {
        val clip = clips.getOrNull(index) ?: return 0L
        val local = (globalMs - prefixFor(index)).coerceIn(0L, clip.durationMs)
        return clip.startMs + local
    }

    fun loadIndex(index: Int, globalMs: Long = prefixFor(index), autoPlay: Boolean = sequencePlaying) {
        val clip = clips.getOrNull(index) ?: return
        selectedIndex = index
        globalPlayheadMs = globalMs.coerceIn(prefixFor(index), prefixFor(index) + clip.durationMs)
        syncFields(clip)
        val sourceMs = sourcePositionForGlobal(index, globalPlayheadMs)
        if (preview.currentPath != clip.path) preview.load(clip.path, sourceMs, autoPlay)
        else {
            preview.seek(sourceMs)
            if (autoPlay) preview.play()
        }
    }

    fun seekGlobal(targetMs: Long, autoPlay: Boolean = sequencePlaying) {
        if (clips.isEmpty()) return
        val total = totalDuration().coerceAtLeast(1L)
        val target = targetMs.coerceIn(0L, total)
        var acc = 0L
        var index = clips.lastIndex
        for (i in clips.indices) {
            val next = acc + clips[i].durationMs
            if (target < next || (target == total && i == clips.lastIndex)) {
                index = i
                break
            }
            acc = next
        }
        val clip = clips[index]
        val local = (target - prefixFor(index)).coerceIn(0L, clip.durationMs)
        loadIndex(index, prefixFor(index) + local, autoPlay)
    }

    fun pushUndo(label: String) {
        undoStack += VideoEditorSnapshot(clips.toList(), selectedIndex, globalPlayheadMs, label)
        while (undoStack.size > 50) undoStack.removeAt(0)
    }

    fun undo() {
        if (undoStack.isEmpty() || exportBusy) return
        val snap = undoStack.removeAt(undoStack.lastIndex)
        sequencePlaying = false
        preview.pause()
        clips.clear(); clips.addAll(snap.clips)
        selectedIndex = snap.selectedIndex.coerceIn(-1, clips.lastIndex)
        globalPlayheadMs = snap.globalPlayheadMs.coerceIn(0L, totalDuration())
        if (selectedIndex >= 0) loadIndex(selectedIndex, globalPlayheadMs, false) else preview.stopAndDispose()
        syncFields(selectedClip())
        status = "Desfeito: ${snap.label}."
    }

    fun removeSelected() {
        if (selectedIndex !in clips.indices || exportBusy) return
        pushUndo("exclusão de trecho")
        val removedAt = selectedIndex
        clips.removeAt(removedAt)
        sequencePlaying = false
        preview.pause()
        if (clips.isEmpty()) {
            selectedIndex = -1; globalPlayheadMs = 0L; preview.stopAndDispose(); syncFields(null)
        } else {
            selectedIndex = min(removedAt, clips.lastIndex)
            globalPlayheadMs = prefixFor(selectedIndex)
            loadIndex(selectedIndex, globalPlayheadMs, false)
        }
        status = "Trecho excluído."
    }

    fun cutAtPlayhead() {
        val index = selectedIndex
        val clip = clips.getOrNull(index) ?: return
        val local = (globalPlayheadMs - prefixFor(index)).coerceIn(0L, clip.durationMs)
        if (local <= 1L || local >= clip.durationMs - 1L) {
            status = "O corte precisa ficar dentro do trecho selecionado."
            return
        }
        pushUndo("corte")
        val splitSource = clip.startMs + local
        val first = clip.copy(id = java.util.UUID.randomUUID().toString(), endMs = splitSource)
        val second = clip.copy(id = java.util.UUID.randomUUID().toString(), startMs = splitSource, cutBefore = true)
        clips[index] = first
        clips.add(index + 1, second)
        selectedIndex = index + 1
        globalPlayheadMs = prefixFor(selectedIndex)
        loadIndex(selectedIndex, globalPlayheadMs, false)
        status = "Corte realizado em ${formatVideoTime(splitSource)}."
    }

    fun duplicateSelected() {
        val clip = selectedClip() ?: return
        clips.add(selectedIndex + 1, clip.copy(id = java.util.UUID.randomUUID().toString()))
        selectedIndex += 1
        globalPlayheadMs = prefixFor(selectedIndex)
        loadIndex(selectedIndex, globalPlayheadMs, false)
        status = "Trecho duplicado."
    }

    fun moveSelected(delta: Int) {
        val from = selectedIndex
        val to = from + delta
        if (from !in clips.indices || to !in clips.indices) return
        val item = clips.removeAt(from)
        clips.add(to, item)
        selectedIndex = to
        globalPlayheadMs = prefixFor(to)
        loadIndex(to, globalPlayheadMs, false)
        status = "Trecho reordenado."
    }

    fun applyTimes() {
        val clip = selectedClip() ?: return
        val start = parseVideoTime(startText)
        val end = parseVideoTime(endText)
        if (start == null || end == null || start < 0L || start >= end || end > clip.info.durationMs) {
            status = "Tempos inválidos. Use HH:MM:SS.mmm dentro da duração do vídeo."
            return
        }
        if (start == clip.startMs && end == clip.endMs) return
        pushUndo("ajuste de início/fim")
        clips[selectedIndex] = clip.copy(startMs = start, endMs = end)
        globalPlayheadMs = prefixFor(selectedIndex)
        syncFields(clips[selectedIndex])
        loadIndex(selectedIndex, globalPlayheadMs, false)
        status = "Início/fim atualizados."
    }

    fun markStartHere() {
        val clip = selectedClip() ?: return
        val source = preview.currentPositionMs().coerceIn(0L, clip.info.durationMs)
        if (source >= clip.endMs) { status = "O início precisa ser menor que o fim."; return }
        pushUndo("marcação de início")
        clips[selectedIndex] = clip.copy(startMs = source)
        globalPlayheadMs = prefixFor(selectedIndex)
        syncFields(clips[selectedIndex])
        status = "Início marcado em ${formatVideoTime(source)}."
    }

    fun markEndHere() {
        val clip = selectedClip() ?: return
        val source = preview.currentPositionMs().coerceIn(0L, clip.info.durationMs)
        if (source <= clip.startMs) { status = "O fim precisa ser maior que o início."; return }
        pushUndo("marcação de fim")
        clips[selectedIndex] = clip.copy(endMs = source)
        globalPlayheadMs = prefixFor(selectedIndex) + clips[selectedIndex].durationMs
        syncFields(clips[selectedIndex])
        status = "Fim marcado em ${formatVideoTime(source)}."
    }

    fun adjustCursor(deltaMs: Long) {
        val clip = selectedClip() ?: return
        val prefix = prefixFor(selectedIndex)
        val local = (globalPlayheadMs - prefix + deltaMs).coerceIn(0L, clip.durationMs)
        seekGlobal(prefix + local, false)
    }

    fun resetVisualSelection() {
        sequencePlaying = false
        preview.pause()
        selectedIndex = -1
        globalPlayheadMs = 0L
        syncFields(null)
        status = "Seleção visual redefinida. Os vídeos permanecem na timeline."
    }

    fun chooseVideos() {
        val dialog = FileDialog(null as Frame?, "Abrir vídeos", FileDialog.LOAD).apply {
            isMultipleMode = true
            directory = engine.videosDir.absolutePath
            filenameFilter = FilenameFilter { _, name ->
                name.substringAfterLast('.', "").lowercase() in setOf("mp4", "mkv", "webm", "mov", "avi", "m4v", "mpeg", "mpg")
            }
        }
        dialog.isVisible = true
        val files = dialog.files.toList()
        if (files.isEmpty()) return
        scope.launch {
            status = "Analisando ${files.size} vídeo(s)..."
            files.forEach { file ->
                runCatching { engine.probe(file) }
                    .onSuccess { info ->
                        clips += VideoClip(path = file.absolutePath, startMs = 0L, endMs = info.durationMs, info = info)
                    }
                    .onFailure { status = "Falha em ${file.name}: ${it.message}" }
            }
            if (selectedIndex < 0 && clips.isNotEmpty()) loadIndex(0, 0L, false)
            status = if (clips.isNotEmpty()) "${clips.size} clipe(s) na timeline • ${formatVideoTime(totalDuration())}" else "Nenhum vídeo válido foi adicionado."
        }
    }

    fun startExport() {
        if (exportBusy || clips.isEmpty()) return
        exportBusy = true
        exportProgress = 0
        status = "Preparando exportação..."
        scope.launch {
            runCatching {
                engine.export(
                    clips.toList(),
                    VideoExportConfig(outputName, resolution, codec, targetSizeEnabled, targetSizeMb)
                ) { pct, msg ->
                    exportProgress = pct
                    if (msg.isNotBlank()) status = msg
                }
            }.onSuccess { result ->
                exportProgress = 100
                status = "Exportado: ${result.file.name} • ${"%.1f".format(result.realSizeMb)} MB • ${result.effectiveCodec.label}"
            }.onFailure {
                status = "Falha na exportação: ${it.message ?: "erro desconhecido"}"
            }
            exportBusy = false
        }
    }

    DisposableEffect(Unit) {
        onDispose { preview.stopAndDispose() }
    }

    LaunchedEffect(Unit) {
        while (true) {
            delay(55)
            val clip = selectedClip()
            if (clip != null && preview.currentPath == clip.path) {
                val source = preview.currentPositionMs()
                val local = (source - clip.startMs).coerceIn(0L, clip.durationMs)
                globalPlayheadMs = prefixFor(selectedIndex) + local
                if (sequencePlaying && source >= clip.endMs - 35L) {
                    if (selectedIndex < clips.lastIndex) {
                        loadIndex(selectedIndex + 1, prefixFor(selectedIndex + 1), true)
                    } else {
                        sequencePlaying = false
                        preview.pause()
                        globalPlayheadMs = totalDuration()
                    }
                }
            }
        }
    }

    Box(
        Modifier.fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFF06243E), VEBgDeep, VENavy950)))
            .onPreviewKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown || editingText) return@onPreviewKeyEvent false
                when {
                    event.key == Key.Delete -> { removeSelected(); true }
                    event.isCtrlPressed && event.key == Key.Z -> { undo(); true }
                    else -> false
                }
            }
    ) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(Color(0x1700BDF2), size.minDimension * .48f, Offset(size.width * .56f, -size.height * .06f))
            drawCircle(Color(0x0E15D4FF), size.minDimension * .30f, Offset(size.width * .77f, size.height * .18f))
        }

        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp)
        ) {
            EditorHeroHeader(
                clipsCount = clips.size,
                canUndo = undoStack.isNotEmpty() && !exportBusy,
                onOpen = ::chooseVideos,
                onUndo = ::undo,
                onReset = ::resetVisualSelection
            )

            Row(
                Modifier.fillMaxWidth().height(420.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                PreviewPanel(
                    modifier = Modifier.weight(1f),
                    preview = preview,
                    clip = selectedClip(),
                    currentGlobalMs = globalPlayheadMs,
                    totalMs = totalDuration(),
                    sequencePlaying = sequencePlaying,
                    volume = volume,
                    onVolume = { volume = it; preview.setVolume(it.toDouble()) },
                    onPlayPause = {
                        if (clips.isEmpty()) return@PreviewPanel
                        if (selectedIndex < 0) loadIndex(0, 0L, true)
                        if (sequencePlaying) { sequencePlaying = false; preview.pause() }
                        else {
                            sequencePlaying = true
                            val clip = selectedClip()
                            if (clip != null && preview.currentPath != clip.path) loadIndex(selectedIndex, globalPlayheadMs, true) else preview.play()
                        }
                    },
                    onStart = { seekGlobal(0L, false) },
                    onBack5 = { seekGlobal(globalPlayheadMs - 5000L, sequencePlaying) },
                    onForward5 = { seekGlobal(globalPlayheadMs + 5000L, sequencePlaying) },
                    onEnd = { seekGlobal(totalDuration(), false) }
                )
                QuickMarkPanel(
                    modifier = Modifier.width(355.dp),
                    clip = selectedClip(),
                    startText = startText,
                    endText = endText,
                    durationText = durationText,
                    onStartText = { startText = it },
                    onEndText = { endText = it },
                    onFocus = { editingText = it },
                    onMarkStart = ::markStartHere,
                    onMarkEnd = ::markEndHere,
                    onApply = ::applyTimes
                )
            }

            TimelinePanel(
                clips = clips,
                selectedIndex = selectedIndex,
                globalPlayheadMs = globalPlayheadMs,
                totalDurationMs = totalDuration(),
                zoomIndex = zoomIndex,
                onZoom = { zoomIndex = it.coerceIn(0, ZOOM_LEVELS.lastIndex) },
                onSelect = { idx -> loadIndex(idx, prefixFor(idx), false) },
                onSeek = { seekGlobal(it, false) },
                onReorder = { from, to ->
                    if (from in clips.indices && to in clips.indices && from != to) {
                        val item = clips.removeAt(from); clips.add(to, item); selectedIndex = to; globalPlayheadMs = prefixFor(to); loadIndex(to, globalPlayheadMs, false)
                    }
                },
                onTrim = { start, end ->
                    val clip = selectedClip() ?: return@TimelinePanel
                    val safeStart = start.coerceIn(0L, clip.info.durationMs - 1L)
                    val safeEnd = end.coerceIn(safeStart + 1L, clip.info.durationMs)
                    clips[selectedIndex] = clip.copy(startMs = safeStart, endMs = safeEnd)
                    syncFields(clips[selectedIndex])
                    globalPlayheadMs = prefixFor(selectedIndex) + (globalPlayheadMs - prefixFor(selectedIndex)).coerceIn(0L, clips[selectedIndex].durationMs)
                },
                onCut = ::cutAtPlayhead,
                onDelete = ::removeSelected,
                onDuplicate = ::duplicateSelected,
                onLeft = { moveSelected(-1) },
                onRight = { moveSelected(1) },
                onPrecision = ::adjustCursor,
                onJump = { delta -> seekGlobal(globalPlayheadMs + delta, false) }
            )

            ExportPanel(
                clips = clips,
                outputName = outputName,
                onOutputName = { outputName = it },
                resolution = resolution,
                onResolution = { resolution = it },
                codec = codec,
                onCodec = { codec = it },
                targetSizeEnabled = targetSizeEnabled,
                onTargetEnabled = { targetSizeEnabled = it },
                targetSizeMb = targetSizeMb,
                onTargetSize = { targetSizeMb = it },
                busy = exportBusy,
                progress = exportProgress,
                status = status,
                onExport = ::startExport,
                onOpenFolder = { runCatching { Desktop.getDesktop().open(engine.videosDir) } },
                onFocus = { editingText = it }
            )
        }
    }
}

@Composable
private fun EditorHeroHeader(
    clipsCount: Int,
    canUndo: Boolean,
    onOpen: () -> Unit,
    onUndo: () -> Unit,
    onReset: () -> Unit
) {
    Box(
        Modifier.fillMaxWidth().height(100.dp).clip(RoundedCornerShape(15.dp))
            .background(Brush.horizontalGradient(listOf(Color(0xEE05243D), Color(0xF0042037), Color(0xEE062B49))))
            .border(1.dp, VEBlueBorder.copy(alpha = .62f), RoundedCornerShape(15.dp))
            .padding(horizontal = 15.dp, vertical = 8.dp)
    ) {
        Row(Modifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(46.dp).clip(RoundedCornerShape(11.dp))
                    .background(Brush.verticalGradient(listOf(VEYellowLight, VEYellow, VEYellowDark)))
                    .border(1.dp, Color(0xFFFFE987), RoundedCornerShape(11.dp)),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.MovieCreation, null, tint = Color(0xFF082033), modifier = Modifier.size(23.dp)) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.width(430.dp)) {
                Text("Vídeos  ›  Editor de Vídeo", color = VECyanSoft, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                Text("EDITOR DE VÍDEO", color = VEText, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = .4.sp)
                Text(if (clipsCount == 0) "Edite, marque e exporte trechos de forma rápida e precisa." else "$clipsCount clipe(s) carregado(s) • editor funcional", color = VEText2, fontSize = 10.sp)
            }
            Box(Modifier.weight(1f).fillMaxHeight()) {
                RadarGraphic(Modifier.align(Alignment.Center).size(88.dp))
                Column(Modifier.align(Alignment.CenterEnd).padding(end = 10.dp), horizontalAlignment = Alignment.End) {
                    Text("VIGILÂNCIA", color = VECyanSoft.copy(alpha = .72f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Text("MÍDIA", color = VECyanSoft.copy(alpha = .55f), fontSize = 9.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("ANÁLISE", color = VECyanSoft.copy(alpha = .72f), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    Text("RESULTADOS", color = VECyanSoft.copy(alpha = .55f), fontSize = 9.sp)
                }
            }
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.width(65.dp).height(2.dp).background(VEYellow))
                    Spacer(Modifier.width(7.dp))
                    Text("BRASIL SEMPRE MAIS INFORMADO", color = VEYellowLight, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(5.dp)); Icon(Icons.Default.Anchor, null, tint = VEYellow, modifier = Modifier.size(17.dp))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                    ActionButton("ABRIR VÍDEO", Icons.Default.FolderOpen, true, gold = true, width = 140.dp, onClick = onOpen)
                    ActionButton("Desfazer (Ctrl+Z)", Icons.Default.Undo, canUndo, width = 140.dp, onClick = onUndo)
                    ActionButton("Redefinir", Icons.Default.Refresh, true, width = 105.dp, onClick = onReset)
                }
            }
        }
    }
}

@Composable
private fun PreviewPanel(
    modifier: Modifier,
    preview: VideoPreviewController,
    clip: VideoClip?,
    currentGlobalMs: Long,
    totalMs: Long,
    sequencePlaying: Boolean,
    volume: Float,
    onVolume: (Float) -> Unit,
    onPlayPause: () -> Unit,
    onStart: () -> Unit,
    onBack5: () -> Unit,
    onForward5: () -> Unit,
    onEnd: () -> Unit
) {
    NeonPanel(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Visibility, null, tint = VECyanBright, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(7.dp)); Text("PRÉ-VISUALIZAÇÃO", color = VEText, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f)); Text(clip?.let { File(it.path).name } ?: "Nenhum vídeo selecionado", color = VEMuted, fontSize = 10.sp, maxLines = 1)
        }
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(11.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF061522), Color(0xFF020A10))))
                .border(1.dp, VEBlueBorder.copy(alpha = .55f), RoundedCornerShape(11.dp))
        ) {
            if (clip != null) {
                SwingPanel(factory = { preview.panel }, modifier = Modifier.fillMaxSize())
            } else {
                Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.MovieCreation, null, tint = Color(0xFF315D79), modifier = Modifier.size(52.dp))
                    Spacer(Modifier.height(8.dp)); Text("Nenhum vídeo selecionado", color = VEText2, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Text("Abra um arquivo de vídeo para começar a editar.", color = VEMuted, fontSize = 9.sp)
                }
            }
            CornerMark(Alignment.TopStart); CornerMark(Alignment.TopEnd); CornerMark(Alignment.BottomStart); CornerMark(Alignment.BottomEnd)
        }
        Spacer(Modifier.height(7.dp))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("${formatVideoTime(currentGlobalMs)} / ${formatVideoTime(totalMs)}", color = VEText2, fontSize = 9.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.width(180.dp))
            Spacer(Modifier.weight(1f))
            IconControl(Icons.Default.SkipPrevious, clip != null, onStart)
            Spacer(Modifier.width(4.dp)); IconControl(Icons.Default.Replay5, clip != null, onBack5)
            Spacer(Modifier.width(6.dp))
            Box(
                Modifier.size(42.dp).clip(CircleShape)
                    .background(Brush.verticalGradient(listOf(Color(0xFF0A75B3), Color(0xFF08456F))))
                    .border(1.dp, VECyanBright, CircleShape).clickable(enabled = clip != null, onClick = onPlayPause),
                contentAlignment = Alignment.Center
            ) { Icon(if (sequencePlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(25.dp)) }
            Spacer(Modifier.width(6.dp)); IconControl(Icons.Default.Forward5, clip != null, onForward5)
            Spacer(Modifier.width(4.dp)); IconControl(Icons.Default.SkipNext, clip != null, onEnd)
            Spacer(Modifier.weight(1f)); Icon(Icons.Default.VolumeUp, null, tint = VEText2, modifier = Modifier.size(16.dp))
            Slider(value = volume, onValueChange = onVolume, modifier = Modifier.width(76.dp), colors = SliderDefaults.colors(thumbColor = VECyanSoft, activeTrackColor = VECyan, inactiveTrackColor = Color(0xFF173F5E)))
            Icon(Icons.Default.Fullscreen, null, tint = VEMuted, modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun QuickMarkPanel(
    modifier: Modifier,
    clip: VideoClip?,
    startText: String,
    endText: String,
    durationText: String,
    onStartText: (String) -> Unit,
    onEndText: (String) -> Unit,
    onFocus: (Boolean) -> Unit,
    onMarkStart: () -> Unit,
    onMarkEnd: () -> Unit,
    onApply: () -> Unit
) {
    NeonPanel(modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Bookmark, null, tint = VECyanBright, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(7.dp))
            Text("MARCAÇÃO RÁPIDA", color = VEText, fontSize = 13.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.weight(1f))
            Icon(Icons.Default.Bolt, null, tint = VEYellow, modifier = Modifier.size(18.dp))
        }
        Text("Defina os pontos de início e fim do trecho desejado.", color = VEMuted, fontSize = 9.sp)
        Spacer(Modifier.height(5.dp))
        TimeEditor("Início", startText, clip != null, onStartText, onFocus)
        Spacer(Modifier.height(5.dp)); TimeEditor("Fim", endText, clip != null, onEndText, onFocus)
        Spacer(Modifier.height(5.dp)); TimeEditor("Duração", durationText, false, {}, onFocus)
        Spacer(Modifier.height(5.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SmallButton("Início aqui", Icons.Default.FirstPage, clip != null, Modifier.weight(1f), onMarkStart)
            SmallButton("Fim aqui", Icons.Default.LastPage, clip != null, Modifier.weight(1f), onMarkEnd)
        }
        Spacer(Modifier.weight(1f))
        ActionButton("✓ APLICAR TEMPOS", Icons.Default.Done, clip != null, fill = true, onClick = onApply)
    }
}

@Composable
private fun TimelinePanel(
    clips: List<VideoClip>,
    selectedIndex: Int,
    globalPlayheadMs: Long,
    totalDurationMs: Long,
    zoomIndex: Int,
    onZoom: (Int) -> Unit,
    onSelect: (Int) -> Unit,
    onSeek: (Long) -> Unit,
    onReorder: (Int, Int) -> Unit,
    onTrim: (Long, Long) -> Unit,
    onCut: () -> Unit,
    onDelete: () -> Unit,
    onDuplicate: () -> Unit,
    onLeft: () -> Unit,
    onRight: () -> Unit,
    onPrecision: (Long) -> Unit,
    onJump: (Long) -> Unit
) {
    val selected = clips.getOrNull(selectedIndex)
    NeonPanel(Modifier.fillMaxWidth().height(260.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.ViewList, null, tint = VECyanBright, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(7.dp))
            Text("TIMELINE", color = VEText, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(9.dp)); Text("Arraste o próprio vídeo com o mouse para reordenar", color = VEMuted, fontSize = 9.sp)
            Spacer(Modifier.weight(1f))
            Icon(Icons.Default.ZoomOut, null, tint = VEText2, modifier = Modifier.size(17.dp).clickable { onZoom(zoomIndex - 1) })
            Slider(value = zoomIndex.toFloat(), onValueChange = { onZoom(it.toInt()) }, valueRange = 0f..5f, steps = 4, modifier = Modifier.width(82.dp), colors = SliderDefaults.colors(thumbColor = VECyanSoft, activeTrackColor = VECyan))
            Icon(Icons.Default.ZoomIn, null, tint = VEText2, modifier = Modifier.size(17.dp).clickable { onZoom(zoomIndex + 1) })
            Spacer(Modifier.width(7.dp)); Text("Zoom ${zoomIndex + 1}/6", color = VEText2, fontSize = 9.sp)
        }
        Spacer(Modifier.height(5.dp))
        CompositionStrip(clips, selectedIndex, globalPlayheadMs, totalDurationMs, zoomIndex, onSelect, onSeek, onReorder)
        Spacer(Modifier.height(5.dp))
        if (selected != null) {
            RangeTrimBar(selected, globalPlayheadMs - clips.take(selectedIndex).sumOf { it.durationMs }, onTrim) { local -> onSeek(clips.take(selectedIndex).sumOf { it.durationMs } + local) }
        } else {
            Box(Modifier.fillMaxWidth().height(30.dp).background(Color(0xFF041B2E), RoundedCornerShape(7.dp)), contentAlignment = Alignment.Center) {
                Text("Abra um vídeo para habilitar o RangeSlider de precisão.", color = VEMuted, fontSize = 9.sp)
            }
        }
        Spacer(Modifier.height(5.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            TimelineButton("Cortar", Icons.Default.ContentCut, selected != null, VECyanSoft, onCut)
            Spacer(Modifier.width(5.dp)); TimelineButton("Excluir", Icons.Default.DeleteOutline, selected != null, VERed, onDelete)
            Spacer(Modifier.width(5.dp)); TimelineButton("Duplicar", Icons.Default.ContentCopy, selected != null, VEText2, onDuplicate)
            Spacer(Modifier.width(5.dp)); IconControl(Icons.Default.KeyboardArrowLeft, selectedIndex > 0, onLeft)
            Spacer(Modifier.width(3.dp)); IconControl(Icons.Default.KeyboardArrowRight, selectedIndex >= 0 && selectedIndex < clips.lastIndex, onRight)
            Spacer(Modifier.weight(1f))
            listOf(-1000L to "-1s", -100L to "-100ms", -10L to "-10ms", 10L to "+10ms", 100L to "+100ms", 1000L to "+1s").forEachIndexed { i, pair ->
                TinyButton(pair.second, selected != null) { onPrecision(pair.first) }
                if (i < 5) Spacer(Modifier.width(4.dp))
            }
            Spacer(Modifier.width(8.dp))
            TinyButton("-5s", selected != null) { onJump(-5000L) }; Spacer(Modifier.width(4.dp))
            TinyButton("+5s", selected != null) { onJump(5000L) }
        }
    }
}

@Composable
private fun CompositionStrip(
    clips: List<VideoClip>,
    selectedIndex: Int,
    globalPlayheadMs: Long,
    totalDurationMs: Long,
    zoomIndex: Int,
    onSelect: (Int) -> Unit,
    onSeek: (Long) -> Unit,
    onReorder: (Int, Int) -> Unit
) {
    val scroll = rememberScrollState()
    val pxPerSec = ZOOM_LEVELS[zoomIndex]
    Column(Modifier.fillMaxWidth().height(98.dp)) {
        Row(Modifier.fillMaxWidth().height(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("${clips.size} clipe(s) • ${formatVideoTime(totalDurationMs)}", color = VEMuted, fontSize = 8.sp, modifier = Modifier.width(125.dp))
            Box(Modifier.weight(1f).height(20.dp).background(Color(0xFF041B2E), RoundedCornerShape(4.dp))) {
                Canvas(Modifier.fillMaxSize()) {
                    val widthMs = max(1L, totalDurationMs)
                    val marks = 10
                    repeat(marks + 1) { i ->
                        val x = size.width * i / marks
                        drawLine(VEBlueBorder.copy(alpha = .5f), Offset(x, size.height - 7f), Offset(x, size.height), 1f)
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().weight(1f)) {
            Column(Modifier.width(46.dp).fillMaxHeight(), verticalArrangement = Arrangement.SpaceEvenly) {
                Text("🎞  V1", color = VEText2, fontSize = 9.sp)
                Text("♫  A1", color = VEText2, fontSize = 9.sp)
            }
            Box(Modifier.weight(1f).fillMaxHeight().background(Color(0xFF041B2E), RoundedCornerShape(7.dp)).border(1.dp, VEBlueBorder.copy(alpha=.35f), RoundedCornerShape(7.dp))) {
                Row(Modifier.fillMaxHeight().horizontalScroll(scroll).padding(horizontal = 5.dp, vertical = 6.dp)) {
                    if (clips.isEmpty()) {
                        Box(Modifier.width(900.dp).fillMaxHeight(), contentAlignment = Alignment.Center) { Text("Timeline vazia", color = VEMuted, fontSize = 9.sp) }
                    } else clips.forEachIndexed { index, clip ->
                        var drag by remember(clip.id) { mutableFloatStateOf(0f) }
                        val width = max(78f, clip.durationMs / 1000f * pxPerSec).dp
                        val selected = index == selectedIndex
                        Box(
                            Modifier.width(width).fillMaxHeight().padding(horizontal = 2.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (selected) Color(0xFF0B5B82) else Color(0xFF07304D))
                                .border(1.dp, if (selected) VECyanBright else VEBlueBorder.copy(alpha=.45f), RoundedCornerShape(6.dp))
                                .clickable { onSelect(index) }
                                .pointerInput(clip.id, index) {
                                    detectDragGestures(
                                        onDragStart = { drag = 0f },
                                        onDragEnd = {
                                            when {
                                                drag > 40f && index < clips.lastIndex -> onReorder(index, index + 1)
                                                drag < -40f && index > 0 -> onReorder(index, index - 1)
                                            }
                                            drag = 0f
                                        }
                                    ) { change, amount -> change.consume(); drag += amount.x }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Movie, null, tint = if (selected) VECyanSoft else VEMuted, modifier = Modifier.size(17.dp))
                                Text(File(clip.path).nameWithoutExtension.take(18), color = VEText2, fontSize = 8.sp, maxLines = 1)
                                Text(formatVideoTime(clip.durationMs), color = VEMuted, fontSize = 7.sp, fontFamily = FontFamily.Monospace)
                            }
                        }
                    }
                }
                if (totalDurationMs > 0) {
                    Canvas(Modifier.fillMaxSize().pointerInput(totalDurationMs) {
                        detectDragGestures(
                            onDragStart = { p -> onSeek((p.x / size.width * totalDurationMs).toLong().coerceIn(0L, totalDurationMs)) },
                            onDrag = { change, _ -> onSeek((change.position.x / size.width * totalDurationMs).toLong().coerceIn(0L, totalDurationMs)) }
                        )
                    }) {
                        val x = size.width * (globalPlayheadMs.toFloat() / totalDurationMs.toFloat()).coerceIn(0f, 1f)
                        drawLine(VECyanBright, Offset(x, 0f), Offset(x, size.height), 2f)
                        val path = Path().apply { moveTo(x - 6f, 0f); lineTo(x + 6f, 0f); lineTo(x, 9f); close() }
                        drawPath(path, VECyanBright)
                    }
                }
            }
        }
    }
}

@Composable
private fun RangeTrimBar(clip: VideoClip, localCursorMs: Long, onTrim: (Long, Long) -> Unit, onCursor: (Long) -> Unit) {
    var activeHandle by remember(clip.id) { mutableIntStateOf(2) }
    Box(
        Modifier.fillMaxWidth().height(34.dp).background(Color(0xFF041B2E), RoundedCornerShape(7.dp))
            .border(1.dp, VEBlueBorder.copy(alpha=.35f), RoundedCornerShape(7.dp))
            .pointerInput(clip.id, clip.startMs, clip.endMs) {
                fun update(x: Float) {
                    val source = (x / size.width * clip.info.durationMs).toLong().coerceIn(0L, clip.info.durationMs)
                    when (activeHandle) {
                        0 -> onTrim(source.coerceAtMost(clip.endMs - 1), clip.endMs)
                        1 -> onTrim(clip.startMs, source.coerceAtLeast(clip.startMs + 1))
                        else -> onCursor((source - clip.startMs).coerceIn(0L, clip.durationMs))
                    }
                }
                detectDragGestures(
                    onDragStart = { p ->
                        val lowerX = clip.startMs.toFloat() / clip.info.durationMs * size.width
                        val upperX = clip.endMs.toFloat() / clip.info.durationMs * size.width
                        val cursorX = (clip.startMs + localCursorMs.coerceIn(0L, clip.durationMs)).toFloat() / clip.info.durationMs * size.width
                        val ds = listOf(abs(p.x-lowerX), abs(p.x-upperX), abs(p.x-cursorX))
                        activeHandle = ds.indices.minByOrNull { ds[it] } ?: 2
                        update(p.x)
                    },
                    onDrag = { change, _ -> update(change.position.x) }
                )
            }
    ) {
        Canvas(Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 7.dp)) {
            val d = clip.info.durationMs.coerceAtLeast(1L).toFloat()
            val lower = clip.startMs / d * size.width
            val upper = clip.endMs / d * size.width
            val cursor = (clip.startMs + localCursorMs.coerceIn(0L, clip.durationMs)) / d * size.width
            val y = size.height / 2f
            drawLine(Color(0xFF183D5A), Offset(0f,y), Offset(size.width,y), 5f, StrokeCap.Round)
            drawLine(VECyan, Offset(lower,y), Offset(upper,y), 5f, StrokeCap.Round)
            drawCircle(VEYellow, 6f, Offset(lower,y)); drawCircle(VEYellow, 6f, Offset(upper,y)); drawCircle(VECyanBright, 5f, Offset(cursor,y))
        }
        Text("IN ${formatVideoTime(clip.startMs)}", color = VEMuted, fontSize = 7.sp, modifier = Modifier.align(Alignment.BottomStart).padding(start=8.dp,bottom=1.dp))
        Text("OUT ${formatVideoTime(clip.endMs)}", color = VEMuted, fontSize = 7.sp, modifier = Modifier.align(Alignment.BottomEnd).padding(end=8.dp,bottom=1.dp))
    }
}

@Composable
private fun ExportPanel(
    clips: List<VideoClip>,
    outputName: String,
    onOutputName: (String) -> Unit,
    resolution: VideoResolution,
    onResolution: (VideoResolution) -> Unit,
    codec: VideoCodec,
    onCodec: (VideoCodec) -> Unit,
    targetSizeEnabled: Boolean,
    onTargetEnabled: (Boolean) -> Unit,
    targetSizeMb: Int,
    onTargetSize: (Int) -> Unit,
    busy: Boolean,
    progress: Int,
    status: String,
    onExport: () -> Unit,
    onOpenFolder: () -> Unit,
    onFocus: (Boolean) -> Unit
) {
    NeonPanel(Modifier.fillMaxWidth().height(124.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.FileUpload, null, tint = VEYellow, modifier = Modifier.size(21.dp)); Spacer(Modifier.width(7.dp))
            Text("EXPORTAÇÃO", color = VEText, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(9.dp)); Text("Exporte a sequência editada em MP4.", color = VEMuted, fontSize = 9.sp)
            Spacer(Modifier.weight(1f)); Text(status, color = if (busy) VECyanSoft else VEMuted, fontSize = 8.sp, maxLines = 1, modifier = Modifier.width(330.dp))
        }
        Spacer(Modifier.height(5.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            OutlinedTextField(
                value = outputName, onValueChange = onOutputName, singleLine = true,
                modifier = Modifier.width(220.dp).height(44.dp).onFocusChanged { onFocus(it.isFocused) },
                textStyle = LocalTextStyle.current.copy(color = VEText2, fontSize = 10.sp),
                label = { Text("Arquivo", fontSize = 8.sp) },
                colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = VECyan, unfocusedBorderColor = VEBlueBorder, focusedTextColor = VEText2, unfocusedTextColor = VEText2)
            )
            CycleButton(resolution.label) {
                val all = VideoResolution.entries; onResolution(all[(resolution.ordinal + 1) % all.size])
            }
            CycleButton(codec.label) {
                val all = VideoCodec.entries; onCodec(all[(codec.ordinal + 1) % all.size])
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = targetSizeEnabled, onCheckedChange = onTargetEnabled, enabled = !busy, colors = SwitchDefaults.colors(checkedThumbColor = VEYellow, checkedTrackColor = Color(0xFF725A13)))
                Text("Alvo", color = VEText2, fontSize = 8.sp)
            }
            Slider(value = targetSizeMb.toFloat(), onValueChange = { onTargetSize(it.toInt().coerceIn(1,100)) }, valueRange = 1f..100f, enabled = targetSizeEnabled && !busy, modifier = Modifier.width(115.dp), colors = SliderDefaults.colors(thumbColor = VEYellow, activeTrackColor = VEYellowDark))
            Text("$targetSizeMb MB", color = VEText2, fontSize = 9.sp, modifier = Modifier.width(46.dp))
            SmallButton("Abrir pasta", Icons.Default.FolderOpen, true, Modifier.width(120.dp), onOpenFolder)
            Spacer(Modifier.weight(1f))
            ActionButton(if (busy) "EXPORTANDO $progress%" else "EXPORTAR", Icons.Default.FileUpload, clips.isNotEmpty() && !busy, gold = true, width = 205.dp, onClick = onExport)
        }
        if (busy || progress > 0) {
            Spacer(Modifier.height(4.dp)); LinearProgressIndicator(progress = { progress / 100f }, modifier = Modifier.fillMaxWidth().height(4.dp), color = VEYellow, trackColor = Color(0xFF173F5E))
        }
    }
}

@Composable
private fun TimeEditor(label: String, value: String, enabled: Boolean, onChange: (String) -> Unit, onFocus: (Boolean) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        enabled = enabled,
        singleLine = true,
        modifier = Modifier.fillMaxWidth().height(46.dp).onFocusChanged { onFocus(it.isFocused) },
        leadingIcon = { Icon(Icons.Default.Schedule, null, modifier = Modifier.size(16.dp)) },
        label = { Text(label, fontSize = 8.sp) },
        textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 10.sp),
        colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = VECyanBright, unfocusedBorderColor = VEBlueBorder.copy(alpha=.9f), focusedTextColor = VEText, unfocusedTextColor = VEText, disabledTextColor = Color(0xFF7FA7C4), disabledBorderColor = VEBlueBorder.copy(alpha=.65f), focusedLabelColor = VECyanSoft, unfocusedLabelColor = VEText2, disabledLabelColor = Color(0xFF6F94AE), focusedLeadingIconColor = VECyanSoft, unfocusedLeadingIconColor = VEText2, disabledLeadingIconColor = Color(0xFF6F94AE))
    )
}

@Composable
private fun NeonPanel(modifier: Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        color = Color.Transparent,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, VECyan.copy(alpha = .62f)),
        modifier = modifier
    ) {
        Column(
            Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xF5073556), Color(0xFA041F36)))).padding(10.dp),
            content = content
        )
    }
}

@Composable
private fun ActionButton(
    label: String,
    icon: ImageVector,
    enabled: Boolean,
    gold: Boolean = false,
    width: androidx.compose.ui.unit.Dp = 120.dp,
    fill: Boolean = false,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(10.dp)
    val mod = if (fill) Modifier.fillMaxWidth().height(36.dp) else Modifier.width(width).height(36.dp)
    val brush = if (gold) Brush.verticalGradient(listOf(VEYellowLight, VEYellow, VEYellowDark)) else Brush.verticalGradient(listOf(Color(0xFF0A3B5E), Color(0xFF062B47)))
    Row(
        mod.clip(shape).background(if (enabled) brush else Brush.verticalGradient(listOf(Color(0xFF0A253A), Color(0xFF071D2E))))
            .border(1.dp, if (gold && enabled) Color(0xFFFFE987) else VEBlueBorder.copy(alpha = if (enabled) .75f else .28f), shape)
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center
    ) {
        Icon(icon, null, tint = if (!enabled) VEMuted.copy(alpha=.45f) else if (gold) Color(0xFF082033) else VEText2, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(6.dp)); Text(label, color = if (!enabled) VEMuted.copy(alpha=.45f) else if (gold) Color(0xFF082033) else VEText, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable
private fun SmallButton(label: String, icon: ImageVector, enabled: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier.height(30.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF072A45))
            .border(1.dp, VEBlueBorder.copy(alpha = if (enabled) .65f else .25f), RoundedCornerShape(8.dp))
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal=8.dp),
        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center
    ) {
        Icon(icon, null, tint = if (enabled) VEText2 else VEMuted.copy(alpha=.4f), modifier=Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)); Text(label, color=if(enabled) VEText2 else VEMuted.copy(alpha=.4f), fontSize=8.sp)
    }
}

@Composable
private fun TimelineButton(label: String, icon: ImageVector, enabled: Boolean, color: Color, onClick: () -> Unit) {
    Row(
        Modifier.height(30.dp).clip(RoundedCornerShape(7.dp)).background(Color(0xFF072A45))
            .border(1.dp, color.copy(alpha=if(enabled) .7f else .2f), RoundedCornerShape(7.dp))
            .clickable(enabled=enabled,onClick=onClick).padding(horizontal=9.dp),
        verticalAlignment=Alignment.CenterVertically
    ) { Icon(icon,null,tint=color.copy(alpha=if(enabled) 1f else .35f),modifier=Modifier.size(14.dp)); Spacer(Modifier.width(4.dp)); Text(label,color=color.copy(alpha=if(enabled) 1f else .35f),fontSize=8.sp) }
}

@Composable
private fun TinyButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.height(26.dp).widthIn(min=40.dp).clip(RoundedCornerShape(6.dp)).background(Color(0xFF072A45))
            .border(1.dp, VEBlueBorder.copy(alpha=if(enabled) .55f else .2f), RoundedCornerShape(6.dp)).clickable(enabled=enabled,onClick=onClick).padding(horizontal=6.dp),
        contentAlignment=Alignment.Center
    ) { Text(label,color=if(enabled) VEText2 else VEMuted.copy(alpha=.35f),fontSize=7.sp) }
}

@Composable
private fun CycleButton(label: String, onClick: () -> Unit) {
    Row(
        Modifier.height(32.dp).clip(RoundedCornerShape(8.dp)).background(Color(0xFF072A45)).border(1.dp, VEBlueBorder.copy(alpha=.6f),RoundedCornerShape(8.dp)).clickable(onClick=onClick).padding(horizontal=9.dp),
        verticalAlignment=Alignment.CenterVertically
    ) { Text(label,color=VEText2,fontSize=8.sp); Spacer(Modifier.width(4.dp)); Icon(Icons.Default.ExpandMore,null,tint=VEMuted,modifier=Modifier.size(13.dp)) }
}

@Composable
private fun IconControl(icon: ImageVector, enabled: Boolean, onClick: () -> Unit) {
    Box(Modifier.size(25.dp).clip(CircleShape).clickable(enabled=enabled,onClick=onClick), contentAlignment=Alignment.Center) {
        Icon(icon,null,tint=if(enabled) VEText2 else VEMuted.copy(alpha=.35f),modifier=Modifier.size(18.dp))
    }
}

@Composable
private fun RadarGraphic(modifier: Modifier) {
    Canvas(modifier) {
        val c = Offset(size.width/2,size.height/2); val maxR = size.minDimension*.46f
        repeat(4){i->drawCircle(VECyan.copy(alpha=.10f+i*.025f),maxR*(i+1)/4f,c,style=Stroke(1f))}
        repeat(8){i->
            val a=Math.toRadians(i*45.0); val p=Offset(c.x+kotlin.math.cos(a).toFloat()*maxR,c.y+kotlin.math.sin(a).toFloat()*maxR)
            drawLine(VECyan.copy(alpha=.13f),c,p,1f)
        }
        drawCircle(VECyanBright.copy(alpha=.8f),3.5f,c); drawCircle(VECyanBright.copy(alpha=.18f),10f,c)
    }
}

@Composable
private fun CornerMark(alignment: Alignment) {
    Box(Modifier.fillMaxSize().padding(10.dp), contentAlignment=alignment) {
        Canvas(Modifier.size(18.dp)) {
            val c=VECyanSoft.copy(alpha=.72f)
            drawLine(c,Offset(0f,0f),Offset(size.width*.75f,0f),1.5f)
            drawLine(c,Offset(0f,0f),Offset(0f,size.height*.75f),1.5f)
        }
    }
}
