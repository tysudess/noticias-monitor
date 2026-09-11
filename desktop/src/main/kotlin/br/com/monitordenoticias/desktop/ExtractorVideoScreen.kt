package br.com.monitordenoticias.desktop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.awt.Desktop

private val ExBg = Color(0xFF070A12)
private val ExCard = Color(0xFF0D1421)
private val ExBorder = Color(0xFF25314A)
private val ExText = Color(0xFFF1F5FB)
private val ExMuted = Color(0xFF93A1B8)
private val ExPurple = Color(0xFF743AF3)
private val ExBlue = Color(0xFF256EFF)
private val ExCyan = Color(0xFF22D3EE)
private val ExDanger = Color(0xFFB93B4C)

private enum class ExtractorPage { DOWNLOAD, HISTORY, SETTINGS }

@Composable
fun ExtractorVideoScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val engine = remember { ExtractorVideoEngine() }
    val sessionStore = remember { GloboplaySessionStore(engine.appDir.toFile()) }
    val updater = remember { YtDlpUpdater(engine) }
    var page by remember { mutableStateOf(ExtractorPage.DOWNLOAD) }
    var url by remember { mutableStateOf("") }
    var qualityIndex by remember { mutableIntStateOf(1) }
    var status by remember { mutableStateOf("Cole o link, escolha a qualidade e clique em BAIXAR VÍDEO.") }
    var progress by remember { mutableFloatStateOf(0f) }
    var busy by remember { mutableStateOf(false) }
    var history by remember { mutableStateOf(listOf<String>()) }
    var proxy by remember { mutableStateOf("") }
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
            val result = engine.download(url.trim(), chosen, proxy) { pct, msg ->
                progress = pct.coerceIn(0, 100) / 100f
                if (msg.isNotBlank()) status = msg
            }
            busy = false
            result.fold(
                onSuccess = { file ->
                    progress = 1f
                    status = "Download concluído: ${file.name}"
                    history = listOf(file.absolutePath) + history.take(49)
                },
                onFailure = { err -> status = err.message ?: "Falha no download." }
            )
        }
    }

    Column(Modifier.fillMaxSize().background(ExBg).padding(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("EXTRATOR DE VÍDEOS", color = ExText, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold)
                Text("Fluxo direto Windows Portable v3.0.1 integrado ao Monitor", color = ExMuted, fontSize = 12.sp)
            }
            TextButton(onClick = onBack) { Text("Voltar ao Monitor", color = ExCyan) }
        }

        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ExtractorTab("Download", page == ExtractorPage.DOWNLOAD, Icons.Default.VideoLibrary) { page = ExtractorPage.DOWNLOAD }
            ExtractorTab("Histórico", page == ExtractorPage.HISTORY, Icons.Default.History) { page = ExtractorPage.HISTORY }
            ExtractorTab("Configurações", page == ExtractorPage.SETTINGS, Icons.Default.Settings) { page = ExtractorPage.SETTINGS }
        }
        Spacer(Modifier.height(14.dp))

        when (page) {
            ExtractorPage.DOWNLOAD -> Surface(
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                color = ExCard,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, ExBorder)
            ) {
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Cole o link do vídeo", color = ExText, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("https://...", color = ExMuted) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { startDownload() }),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = ExText,
                            unfocusedTextColor = ExText,
                            focusedBorderColor = ExPurple,
                            unfocusedBorderColor = ExBorder,
                            cursorColor = ExCyan
                        )
                    )

                    Text("Qualidade do vídeo", color = ExText, fontWeight = FontWeight.SemiBold)
                    EXTRACTOR_QUALITIES.forEachIndexed { index, item ->
                        Row(
                            Modifier.fillMaxWidth().clickable(enabled = !busy) { qualityIndex = index }.padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(selected = qualityIndex == index, onClick = { if (!busy) qualityIndex = index })
                            Text(item.label, color = ExText)
                        }
                    }
                    Text("Formato de saída: MP4", color = ExMuted)

                    Button(
                        enabled = url.trim().isNotEmpty() && !busy,
                        onClick = { startDownload() },
                        modifier = Modifier.fillMaxWidth().height(50.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = ExPurple)
                    ) { Text("↓  BAIXAR VÍDEO", fontWeight = FontWeight.Bold) }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { runCatching { Desktop.getDesktop().open(engine.videosDir) } }) {
                            Icon(Icons.Default.FolderOpen, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Abrir Videos")
                        }
                        OutlinedButton(
                            enabled = busy,
                            onClick = {
                                engine.cancel()
                                busy = false
                                status = "Download cancelado."
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ExDanger)
                        ) { Text("CANCELAR") }
                    }

                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth(),
                        color = ExCyan,
                        trackColor = Color(0xFF101827)
                    )
                    Text("${(progress * 100).toInt()}%", color = ExText, fontWeight = FontWeight.Bold)
                    Text(status, color = ExMuted)
                    Text(
                        "Binários: yt-dlp=${engine.ytDlp.exists()} • stable=${engine.ytDlpStable.exists()} • ffmpeg=${engine.ffmpeg.exists()} • ffprobe=${engine.ffprobe.exists()} • deno=${engine.deno.exists()}",
                        color = ExMuted,
                        fontSize = 10.sp
                    )
                }
            }

            ExtractorPage.HISTORY -> Surface(
                Modifier.fillMaxSize(),
                color = ExCard,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, ExBorder)
            ) {
                Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("HISTÓRICO", color = ExText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    if (history.isEmpty()) Text("Nenhum download nesta sessão.", color = ExMuted)
                    history.forEach { Text(it, color = ExText, fontSize = 12.sp) }
                }
            }

            ExtractorPage.SETTINGS -> Surface(
                Modifier.fillMaxSize(),
                color = ExCard,
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, ExBorder)
            ) {
                Column(
                    Modifier.padding(20.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("CONFIGURAÇÕES", color = ExText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text("Proxy opcional", color = ExText, fontWeight = FontWeight.SemiBold)
                    OutlinedTextField(
                        value = proxy,
                        onValueChange = { proxy = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text("http://usuario:senha@servidor:porta", color = ExMuted) },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = ExText,
                            unfocusedTextColor = ExText,
                            focusedBorderColor = ExPurple,
                            unfocusedBorderColor = ExBorder
                        )
                    )
                    Text("O mesmo proxy é aplicado ao yt-dlp e aos fallbacks HTML/HLS.", color = ExMuted)

                    HorizontalDivider(color = ExBorder)
                    Text("Globoplay", color = ExText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Text(
                        if (sessionSaved) "Sessão protegida salva neste usuário do Windows (DPAPI)." else "Nenhuma sessão Globoplay salva.",
                        color = if (sessionSaved) ExCyan else ExMuted
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            enabled = sessionSaved,
                            onClick = {
                                val deleted = sessionStore.deleteSavedSession()
                                sessionSaved = sessionStore.hasSavedSession()
                                settingsStatus = if (deleted) "Sessão Globoplay apagada." else "Não foi possível apagar a sessão Globoplay."
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = ExDanger)
                        ) { Text("APAGAR SESSÃO") }
                    }
                    Text(
                        "O login interno será conectado aqui usando a página oficial do Globoplay. A senha não será armazenada; somente os cookies da sessão serão protegidos por DPAPI.",
                        color = ExMuted
                    )

                    HorizontalDivider(color = ExBorder)
                    Text("yt-dlp", color = ExText, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Button(
                        enabled = !updatingYtDlp && !busy,
                        onClick = {
                            updatingYtDlp = true
                            settingsStatus = "Preparando atualização do yt-dlp..."
                            scope.launch {
                                val result = updater.update { settingsStatus = it }
                                settingsStatus = result.message
                                updatingYtDlp = false
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = ExBlue)
                    ) { Text(if (updatingYtDlp) "ATUALIZANDO..." else "ATUALIZAR YT-DLP", fontWeight = FontWeight.Bold) }
                    Text("A atualização só substitui o executável depois de baixar e validar a nova versão. Em caso de falha, o anterior é preservado.", color = ExMuted)

                    if (settingsStatus.isNotBlank()) {
                        Text(settingsStatus, color = ExCyan)
                    }
                }
            }
        }
    }
}

@Composable
private fun ExtractorTab(label: String, selected: Boolean, icon: ImageVector, onClick: () -> Unit) {
    Surface(
        color = if (selected) ExPurple else ExCard,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, if (selected) ExBlue else ExBorder),
        modifier = Modifier.clickable { onClick() }
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, color = Color.White)
        }
    }
}
