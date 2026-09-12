package br.com.monitordenoticias.desktop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.awt.Desktop

private val HudBg = Color(0xFF07111F)
private val HudBg2 = Color(0xFF0B1626)
private val HudPanel = Color(0xE60B1424)
private val HudLine = Color(0xFF4D6786)
private val HudLineSoft = Color(0xFF263A55)
private val HudText = Color(0xFFF2F3F6)
private val HudMuted = Color(0xFFA8B4C8)
private val HudGold = Color(0xFFD6A44D)
private val HudGold2 = Color(0xFFFFA20A)
private val HudCyan = Color(0xFF28E4F2)
private val HudBlue = Color(0xFF1597FF)
private val HudPurple = Color(0xFF6F3CFF)
private val HudRed = Color(0xFFE55D64)
private val HudGreen = Color(0xFF47D7B0)

// Legacy contract only (sem UI/sem uso): SALVAR PROXY | REMOVER PROXY
private enum class ExtractorPage { DOWNLOAD, HISTORY, SETTINGS }

@Composable
fun ExtractorVideoScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val engine = remember { ExtractorVideoEngine() }
    val sessionStore = remember { GloboplaySessionStore(engine.appDir.toFile()) }
    val updater = remember { YtDlpUpdater(engine) }
    val portableState = remember { ExtractorPortableStateStore(engine.appDir.toFile()) }

    var page by remember { mutableStateOf(ExtractorPage.DOWNLOAD) }
    var url by remember { mutableStateOf("") }
    var qualityIndex by remember { mutableIntStateOf(portableState.loadQualityIndex(1)) }
    var status by remember { mutableStateOf("Cole o link, escolha a qualidade e clique em BAIXAR VÍDEO.") }
    var progress by remember { mutableFloatStateOf(0f) }
    var busy by remember { mutableStateOf(false) }
    var history by remember { mutableStateOf(portableState.loadHistory()) }
    var settingsStatus by remember { mutableStateOf("") }
    var sessionSaved by remember { mutableStateOf(sessionStore.hasSavedSession()) }
    var updatingYtDlp by remember { mutableStateOf(false) }

    fun startDownload() {
        if (busy || url.trim().isEmpty()) return
        val chosen = EXTRACTOR_QUALITIES[qualityIndex]
        busy = true
        progress = 0f
        status = "Iniciando download direto em ${chosen.label}..."
        scope.launch {
            val result = engine.download(url.trim(), chosen, "") { pct, msg ->
                scope.launch {
                    progress = pct.coerceIn(0, 100) / 100f
                    if (msg.isNotBlank()) status = msg
                }
            }
            busy = false
            result.fold(
                onSuccess = { file ->
                    progress = 1f
                    status = "Download concluído: ${file.name}"
                    history = portableState.addHistory(file.absolutePath)
                },
                onFailure = { err -> status = err.message ?: "Falha no download." }
            )
        }
    }

    Box(Modifier.fillMaxSize().background(HudBg)) {
        NavalHudBackdrop()
        Column(Modifier.fillMaxSize().padding(horizontal = 18.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text("EXTRATOR DE VÍDEOS", color = HudGold, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Fluxo direto Windows Portable v3.0.1 integrado ao Monitor", color = HudCyan, fontSize = 12.sp)
                }
                HudBackButton(onBack)
            }

            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HudTab("Download", page == ExtractorPage.DOWNLOAD, Icons.Default.Download) { page = ExtractorPage.DOWNLOAD }
                HudTab("Histórico", page == ExtractorPage.HISTORY, Icons.Default.History) { page = ExtractorPage.HISTORY }
                HudTab("Configurações", page == ExtractorPage.SETTINGS, Icons.Default.Settings) { page = ExtractorPage.SETTINGS }
            }
            Spacer(Modifier.height(10.dp))

            HudFrame(Modifier.fillMaxWidth().weight(1f)) {
                when (page) {
                    ExtractorPage.DOWNLOAD -> DownloadHudPage(
                        url = url,
                        onUrlChange = { url = it },
                        qualityIndex = qualityIndex,
                        onQuality = {
                            if (!busy) {
                                qualityIndex = it
                                portableState.saveQualityIndex(it)
                            }
                        },
                        busy = busy,
                        progress = progress,
                        status = status,
                        onDownload = ::startDownload,
                        onOpenVideos = { runCatching { Desktop.getDesktop().open(engine.videosDir) } },
                        onCancel = {
                            engine.cancel()
                            busy = false
                            status = "Download cancelado."
                        },
                        engine = engine
                    )

                    ExtractorPage.HISTORY -> HistoryHudPage(
                        history = history,
                        onClear = {
                            portableState.clearHistory()
                            history = emptyList()
                        }
                    )

                    ExtractorPage.SETTINGS -> SettingsHudPage(
                        sessionSaved = sessionSaved,
                        settingsStatus = settingsStatus,
                        updatingYtDlp = updatingYtDlp,
                        busy = busy,
                        onLogin = {
                            settingsStatus = "Abrindo login oficial do Globoplay..."
                            GloboplayLoginWindow(null, engine, "") { saved, message ->
                                scope.launch {
                                    sessionSaved = saved
                                    settingsStatus = message
                                }
                            }.open()
                        },
                        onDeleteSession = {
                            val deleted = sessionStore.deleteSavedSession()
                            sessionSaved = sessionStore.hasSavedSession()
                            settingsStatus = if (deleted) "Sessão Globoplay apagada." else "Não foi possível apagar a sessão Globoplay."
                        },
                        onUpdate = {
                            updatingYtDlp = true
                            settingsStatus = "Preparando atualização do yt-dlp..."
                            scope.launch {
                                val result = updater.update { message -> scope.launch { settingsStatus = message } }
                                settingsStatus = result.message
                                updatingYtDlp = false
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun NavalHudBackdrop() {
    Canvas(Modifier.fillMaxSize()) {
        drawRect(
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFF071321), Color(0xFF0A1625), Color(0xFF07111F)),
                start = Offset.Zero,
                end = Offset(size.width, size.height)
            )
        )
        val grid = 64f
        var x = 0f
        while (x < size.width) {
            drawLine(Color(0x1328A0BA), Offset(x, 0f), Offset(x, size.height), 1f)
            x += grid
        }
        var y = 0f
        while (y < size.height) {
            drawLine(Color(0x1028A0BA), Offset(0f, y), Offset(size.width, y), 1f)
            y += grid
        }
        val c = Offset(size.width * .51f, size.height * .54f)
        val r = minOf(size.width, size.height) * .30f
        repeat(4) { i ->
            val rr = r * (1f - i * .18f)
            drawCircle(Color(0x1820B8C5), rr, c, style = Stroke(width = 1.2f))
        }
        repeat(12) { i ->
            val a = Math.toRadians((i * 30).toDouble())
            val p = Offset(c.x + kotlin.math.cos(a).toFloat() * r, c.y + kotlin.math.sin(a).toFloat() * r)
            drawLine(Color(0x1620B8C5), c, p, 1f)
        }
        drawArc(
            color = Color(0x2938C7B4),
            startAngle = -30f,
            sweepAngle = 34f,
            useCenter = true,
            topLeft = Offset(c.x - r, c.y - r),
            size = Size(r * 2, r * 2)
        )
        drawArc(
            color = Color(0x2AD6A44D),
            startAngle = 210f,
            sweepAngle = 42f,
            useCenter = false,
            topLeft = Offset(c.x - r * .72f, c.y - r * .72f),
            size = Size(r * 1.44f, r * 1.44f),
            style = Stroke(width = 1.5f)
        )
        drawLine(Color(0x2237D5E0), Offset(size.width * .34f, 0f), Offset(size.width * .27f, size.height), 1f)
        drawLine(Color(0x2237D5E0), Offset(size.width * .70f, 0f), Offset(size.width * .76f, size.height), 1f)
    }
}

@Composable
private fun HudFrame(modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit) {
    Box(
        modifier
            .background(HudPanel, RoundedCornerShape(2.dp))
            .border(1.dp, HudLine, RoundedCornerShape(2.dp))
            .padding(14.dp)
    ) {
        Canvas(Modifier.matchParentSize()) {
            val len = 18f
            val inset = 5f
            val gold = HudGold.copy(alpha = .95f)
            val cyan = HudCyan.copy(alpha = .75f)
            val w = 2f
            drawLine(gold, Offset(inset, inset + len), Offset(inset, inset), w)
            drawLine(gold, Offset(inset, inset), Offset(inset + len, inset), w)
            drawLine(gold, Offset(size.width - inset - len, inset), Offset(size.width - inset, inset), w)
            drawLine(gold, Offset(size.width - inset, inset), Offset(size.width - inset, inset + len), w)
            drawLine(gold, Offset(inset, size.height - inset - len), Offset(inset, size.height - inset), w)
            drawLine(gold, Offset(inset, size.height - inset), Offset(inset + len, size.height - inset), w)
            drawLine(gold, Offset(size.width - inset - len, size.height - inset), Offset(size.width - inset, size.height - inset), w)
            drawLine(gold, Offset(size.width - inset, size.height - inset), Offset(size.width - inset, size.height - inset - len), w)
            drawLine(cyan, Offset(size.width * .34f, inset), Offset(size.width * .43f, inset), 2f)
            drawLine(cyan, Offset(size.width * .58f, inset), Offset(size.width * .67f, inset), 2f)
        }
        content()
    }
}

@Composable
private fun HudTab(label: String, selected: Boolean, icon: ImageVector, onClick: () -> Unit) {
    val shape = RoundedCornerShape(6.dp)
    Box(
        Modifier
            .height(42.dp)
            .background(
                if (selected) Brush.horizontalGradient(listOf(HudCyan, HudPurple))
                else Brush.horizontalGradient(listOf(HudBg2, HudBg2)),
                shape
            )
            .border(1.dp, if (selected) HudCyan else HudGold.copy(alpha = .72f), shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Icon(icon, null, tint = if (selected) Color(0xFF06121E) else HudText, modifier = Modifier.size(18.dp))
            Text(label, color = if (selected) Color(0xFF08111B) else HudText, fontSize = 15.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium)
        }
    }
}

@Composable
private fun HudBackButton(onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        border = BorderStroke(1.dp, HudGold.copy(alpha = .45f)),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = HudGold),
        shape = RoundedCornerShape(10.dp),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
    ) {
        Icon(Icons.Default.ArrowBack, null, modifier = Modifier.size(17.dp))
        Spacer(Modifier.width(6.dp))
        Text("Voltar ao Monitor")
    }
}

@Composable
private fun DownloadHudPage(
    url: String,
    onUrlChange: (String) -> Unit,
    qualityIndex: Int,
    onQuality: (Int) -> Unit,
    busy: Boolean,
    progress: Float,
    status: String,
    onDownload: () -> Unit,
    onOpenVideos: () -> Unit,
    onCancel: () -> Unit,
    engine: ExtractorVideoEngine
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 10.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OutlinedTextField(
            value = url,
            onValueChange = onUrlChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("https://...", color = HudMuted) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onDownload() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedTextColor = HudText,
                unfocusedTextColor = HudText,
                focusedBorderColor = HudCyan,
                unfocusedBorderColor = HudLine,
                cursorColor = HudCyan,
                focusedContainerColor = Color(0x5A07111F),
                unfocusedContainerColor = Color(0x5A07111F)
            ),
            shape = RoundedCornerShape(4.dp)
        )

        EXTRACTOR_QUALITIES.forEachIndexed { index, item ->
            Row(
                Modifier.fillMaxWidth().clickable(enabled = !busy) { onQuality(index) }.padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                HudRadio(selected = qualityIndex == index)
                Spacer(Modifier.width(12.dp))
                Text(item.label, color = HudText, fontSize = 15.sp)
            }
        }

        Text("Formato de saída: MP4", color = HudMuted, fontSize = 13.sp)

        HudGoldButton(
            text = "⇩  BAIXAR VÍDEO",
            enabled = url.trim().isNotEmpty() && !busy,
            onClick = onDownload
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = onOpenVideos,
                border = BorderStroke(1.dp, HudBlue),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = HudBlue),
                shape = RoundedCornerShape(20.dp)
            ) {
                Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Abrir Vídeos")
            }
            OutlinedButton(
                enabled = busy,
                onClick = onCancel,
                border = BorderStroke(1.dp, HudRed.copy(alpha = .55f)),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = HudRed),
                shape = RoundedCornerShape(20.dp)
            ) { Text("CANCELAR") }
        }

        HudProgress(progress)
        Text("${(progress * 100).toInt()}%", color = HudText, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        Text(status, color = if (status.startsWith("Falha", true)) HudRed else HudMuted, fontSize = 13.sp)
        Text(
            "Binários: yt-dlp=${engine.ytDlp.exists()} • stable=${engine.ytDlpStable.exists()} • ffmpeg=${engine.ffmpeg.exists()} • ffprobe=${engine.ffprobe.exists()} • deno=${engine.deno.exists()}",
            color = HudMuted,
            fontSize = 9.sp
        )
    }
}

@Composable
private fun HistoryHudPage(history: List<String>, onClear: () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("HISTÓRICO", color = HudGold, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        if (history.isEmpty()) Text("Nenhum download salvo.", color = HudMuted)
        if (history.isNotEmpty()) {
            OutlinedButton(onClick = onClear, border = BorderStroke(1.dp, HudGold.copy(alpha = .7f)), colors = ButtonDefaults.outlinedButtonColors(contentColor = HudGold)) {
                Text("LIMPAR HISTÓRICO")
            }
        }
        history.forEach { path ->
            Box(Modifier.fillMaxWidth().border(1.dp, HudLineSoft, RoundedCornerShape(4.dp)).padding(10.dp)) {
                Text(path, color = HudText, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun SettingsHudPage(
    sessionSaved: Boolean,
    settingsStatus: String,
    updatingYtDlp: Boolean,
    busy: Boolean,
    onLogin: () -> Unit,
    onDeleteSession: () -> Unit,
    onUpdate: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("CONFIGURAÇÕES", color = HudGold, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        HorizontalDivider(color = HudLineSoft)
        Text("Globoplay", color = HudText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(
            if (sessionSaved) "Sessão protegida salva neste usuário do Windows (DPAPI)." else "Nenhuma sessão Globoplay salva.",
            color = if (sessionSaved) HudGreen else HudMuted
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            HudMiniButton("LOGIN GLOBOPLAY", HudPurple, !busy, onLogin)
            HudMiniButton("APAGAR SESSÃO", HudRed, sessionSaved, onDeleteSession)
        }
        Text(
            "O login abre a página oficial dentro do Monitor. A senha não é armazenada; somente os cookies da sessão são protegidos pelo Windows.",
            color = HudMuted,
            fontSize = 12.sp
        )
        HorizontalDivider(color = HudLineSoft)
        Text("yt-dlp", color = HudText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        HudMiniButton(if (updatingYtDlp) "ATUALIZANDO..." else "ATUALIZAR YT-DLP", HudBlue, !updatingYtDlp && !busy, onUpdate)
        Text("A atualização só substitui o executável depois de validar a nova versão. Em caso de falha, o anterior é preservado.", color = HudMuted, fontSize = 12.sp)
        if (settingsStatus.isNotBlank()) Text(settingsStatus, color = HudCyan)
    }
}

@Composable
private fun HudRadio(selected: Boolean) {
    Canvas(Modifier.size(27.dp)) {
        drawCircle(if (selected) HudCyan.copy(alpha = .28f) else Color.Transparent, radius = size.minDimension * .48f)
        drawCircle(if (selected) HudCyan else HudLine, radius = size.minDimension * .39f, style = Stroke(width = 2.2f))
        if (selected) drawCircle(HudCyan, radius = size.minDimension * .20f)
    }
}

@Composable
private fun HudGoldButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Box(
        Modifier
            .fillMaxWidth()
            .height(46.dp)
            .background(
                if (enabled) Brush.horizontalGradient(listOf(HudGold2, Color(0xFFFFCC57), HudGold2))
                else Brush.horizontalGradient(listOf(Color(0xFF5B4B31), Color(0xFF6A593B))),
                shape
            )
            .border(1.dp, if (enabled) Color(0xFFFFD77A) else HudLineSoft, shape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = Color(0xFF111820), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun HudMiniButton(text: String, accent: Color, enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .height(40.dp)
            .background(if (enabled) accent.copy(alpha = .18f) else Color(0x22000000), RoundedCornerShape(6.dp))
            .border(1.dp, if (enabled) accent else HudLineSoft, RoundedCornerShape(6.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, color = if (enabled) HudText else HudMuted.copy(alpha = .45f), fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}

@Composable
private fun HudProgress(progress: Float) {
    Canvas(Modifier.fillMaxWidth().height(18.dp)) {
        val y = size.height / 2
        drawLine(HudLineSoft, Offset(0f, y), Offset(size.width, y), 1.5f)
        val end = size.width * progress.coerceIn(0f, 1f)
        drawLine(HudCyan, Offset(0f, y), Offset(end, y), 2f, cap = StrokeCap.Round)
        drawCircle(HudCyan, radius = 3.5f, center = Offset(end.coerceAtLeast(3.5f), y))
    }
}
