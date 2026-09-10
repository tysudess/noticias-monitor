package br.com.monitordenoticias.desktop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.key.*
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.awt.Desktop
import java.awt.FileDialog
import java.awt.Frame
import java.awt.image.BufferedImage
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.math.abs
import kotlin.math.roundToInt

private val VexInk = Color(0xFF0A1F4B)
private val VexMuted = Color(0xFF58739F)
private val VexBlue = Color(0xFF087AF7)
private val VexPurple = Color(0xFF743AF3)
private val VexGreen = Color(0xFF08A86F)
private val VexRed = Color(0xFFD92F43)
private val VexOrange = Color(0xFFFF820A)
private val VexBg = Color(0xFFF3F8FE)
private val VexBorder = Color(0xFFD5E4F3)
private val VexSoftBlue = Color(0xFFEAF4FF)
private val VexSoftPurple = Color(0xFFF2ECFF)

private data class VexCoreState(
    val available: Boolean = false,
    val version: String = "v3.0.1",
    val videoDir: String = "",
    val ytDlp: Boolean = false,
    val ytDlpStable: Boolean = false,
    val ffmpeg: Boolean = false,
    val ffprobe: Boolean = false,
    val deno: Boolean = false,
    val proxyEnabled: Boolean = false,
    val proxyServer: String = "",
    val proxyPort: String = "",
    val globoplaySession: Boolean = false,
    val error: String = ""
)

private data class VexClip(
    val path: String,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val fps: Double,
    val hasAudio: Boolean,
    val startMs: Long = 0,
    val endMs: Long = durationMs,
    val cutBefore: Boolean = false
) {
    val selectedDurationMs: Long get() = (endMs - startMs).coerceAtLeast(0)
}

private data class VexEditorSnapshot(
    val clips: List<VexClip>,
    val selectedIndex: Int,
    val playheadMs: Long
)

private object VexBridge {
    private val running = AtomicReference<Process?>(null)

    private fun resolveCore(): File? {
        val roots = linkedSetOf<File>()
        fun parents(start: File?) {
            var f = start?.absoluteFile
            repeat(10) {
                if (f == null) return@repeat
                roots += f!!
                f = f!!.parentFile
            }
        }
        parents(File(System.getProperty("user.dir", ".")))
        ProcessHandle.current().info().command().orElse(null)?.let { parents(File(it).parentFile) }
        System.getProperty("java.class.path", "").split(File.pathSeparatorChar)
            .filter { it.isNotBlank() }
            .forEach { parents(File(it).parentFile) }
        return roots.asSequence().map { File(it, "tools/ExtratorVideosCore") }
            .firstOrNull { File(it, "VideoCoreBridge.exe").isFile }
    }

    fun videoFolder(): File? = resolveCore()?.let { File(it, "Videos") }

    suspend fun state(): VexCoreState = withContext(Dispatchers.IO) {
        val response = runRequest(JSONObject().put("action", "state"), null, null)
        if (!response.optBoolean("ok", false)) {
            return@withContext VexCoreState(error = response.optString("error", "Núcleo do Extrator de Vídeos não encontrado."))
        }
        val bins = response.optJSONObject("binaries") ?: JSONObject()
        val proxy = response.optJSONObject("proxy") ?: JSONObject()
        VexCoreState(
            available = true,
            version = response.optString("version", "v3.0.1"),
            videoDir = response.optString("videoDir", ""),
            ytDlp = bins.optBoolean("ytDlp"),
            ytDlpStable = bins.optBoolean("ytDlpStable"),
            ffmpeg = bins.optBoolean("ffmpeg"),
            ffprobe = bins.optBoolean("ffprobe"),
            deno = bins.optBoolean("deno"),
            proxyEnabled = proxy.optBoolean("enabled"),
            proxyServer = proxy.optString("server", ""),
            proxyPort = proxy.optString("port", ""),
            globoplaySession = response.optBoolean("globoplaySession")
        )
    }

    suspend fun probe(path: String): VexClip = withContext(Dispatchers.IO) {
        val response = runRequest(JSONObject().put("action", "probe").put("path", path), null, null)
        if (!response.optBoolean("ok", false)) throw IllegalStateException(response.optString("error", "Não foi possível analisar o vídeo."))
        val info = response.optJSONObject("info") ?: JSONObject()
        val duration = info.optLong("duration_ms", 0L)
        if (duration <= 0L) throw IllegalStateException("Não foi possível identificar a duração do vídeo.")
        VexClip(
            path = response.optString("path", path),
            durationMs = duration,
            width = info.optInt("width", 0),
            height = info.optInt("height", 0),
            fps = info.optDouble("fps", 30.0),
            hasAudio = info.optBoolean("has_audio", false),
            endMs = duration
        )
    }

    suspend fun download(
        url: String,
        qualityIndex: Int,
        proxyEnabled: Boolean,
        proxyServer: String,
        proxyPort: String,
        proxyUser: String,
        proxyPassword: String,
        onProgress: (Int) -> Unit,
        onMessage: (String) -> Unit
    ): JSONObject = withContext(Dispatchers.IO) {
        val req = commonProxy(proxyEnabled, proxyServer, proxyPort, proxyUser, proxyPassword)
            .put("action", "download").put("url", url).put("qualityIndex", qualityIndex)
        runRequest(req, onProgress, onMessage)
    }

    suspend fun updateYtDlp(
        proxyEnabled: Boolean,
        proxyServer: String,
        proxyPort: String,
        proxyUser: String,
        proxyPassword: String,
        onMessage: (String) -> Unit
    ): JSONObject = withContext(Dispatchers.IO) {
        runRequest(
            commonProxy(proxyEnabled, proxyServer, proxyPort, proxyUser, proxyPassword).put("action", "update"),
            null,
            onMessage
        )
    }

    suspend fun saveProxy(enabled: Boolean, server: String, port: String): JSONObject = withContext(Dispatchers.IO) {
        runRequest(JSONObject().put("action", "save_proxy").put("proxyEnabled", enabled).put("proxyServer", server).put("proxyPort", port), null, null)
    }

    suspend fun globoplayLogin(
        proxyEnabled: Boolean,
        proxyServer: String,
        proxyPort: String,
        proxyUser: String,
        proxyPassword: String
    ): JSONObject = withContext(Dispatchers.IO) {
        runRequest(commonProxy(proxyEnabled, proxyServer, proxyPort, proxyUser, proxyPassword).put("action", "login"), null, null)
    }

    suspend fun deleteGloboplaySession(): JSONObject = withContext(Dispatchers.IO) {
        runRequest(JSONObject().put("action", "delete_session"), null, null)
    }

    suspend fun export(
        clips: List<VexClip>,
        outputName: String,
        resolution: String,
        codec: String,
        targetEnabled: Boolean,
        targetMb: Int,
        onProgress: (Int) -> Unit,
        onMessage: (String) -> Unit
    ): JSONObject = withContext(Dispatchers.IO) {
        val array = JSONArray()
        clips.forEach { clip ->
            array.put(
                JSONObject()
                    .put("path", clip.path)
                    .put("start_ms", clip.startMs)
                    .put("end_ms", clip.endMs)
                    .put("duration_ms", clip.durationMs)
                    .put("cut_before", clip.cutBefore)
                    .put(
                        "info", JSONObject()
                            .put("duration_ms", clip.durationMs)
                            .put("width", clip.width)
                            .put("height", clip.height)
                            .put("fps", clip.fps)
                            .put("has_audio", clip.hasAudio)
                    )
            )
        }
        val req = JSONObject()
            .put("action", "export")
            .put("clips", array)
            .put("outputName", outputName)
            .put("resolution", resolution)
            .put("codec", codec)
            .put("targetSizeEnabled", targetEnabled)
            .put("targetSizeMb", targetMb)
        runRequest(req, onProgress, onMessage)
    }

    private fun commonProxy(enabled: Boolean, server: String, port: String, user: String, password: String) =
        JSONObject()
            .put("proxyEnabled", enabled)
            .put("proxyServer", server)
            .put("proxyPort", port)
            .put("proxyUser", user)
            .put("proxyPassword", password)

    private fun runRequest(
        request: JSONObject,
        onProgress: ((Int) -> Unit)?,
        onMessage: ((String) -> Unit)?
    ): JSONObject {
        val core = resolveCore() ?: return JSONObject().put("ok", false).put("error", "Núcleo do Extrator de Vídeos não encontrado no pacote.")
        val exe = File(core, "VideoCoreBridge.exe")
        if (!exe.isFile) return JSONObject().put("ok", false).put("error", "VideoCoreBridge.exe não encontrado.")
        val process = ProcessBuilder(exe.absolutePath).directory(core).redirectErrorStream(true).start()
        running.set(process)
        process.outputStream.bufferedWriter(Charsets.UTF_8).use { out -> out.write(request.toString()) }
        var terminal: JSONObject? = null
        try {
            process.inputStream.bufferedReader(Charsets.UTF_8).useLines { lines ->
                lines.forEach { raw ->
                    val line = raw.trim()
                    if (!line.startsWith("{") || !line.endsWith("}")) return@forEach
                    val obj = runCatching { JSONObject(line) }.getOrNull() ?: return@forEach
                    when (obj.optString("type")) {
                        "progress" -> onProgress?.invoke(obj.optInt("value", 0))
                        "message" -> onMessage?.invoke(obj.optString("message", ""))
                        "result" -> terminal = obj
                    }
                }
            }
            process.waitFor()
        } finally {
            running.compareAndSet(process, null)
        }
        return terminal ?: JSONObject().put("ok", false).put("error", "O núcleo do Extrator de Vídeos encerrou sem resposta.")
    }

    fun cancel() {
        val process = running.getAndSet(null) ?: return
        runCatching {
            if (System.getProperty("os.name", "").contains("Windows", true)) {
                ProcessBuilder("taskkill", "/PID", process.pid().toString(), "/T", "/F").start().waitFor()
            } else {
                process.toHandle().descendants().forEach { it.destroyForcibly() }
                process.destroyForcibly()
            }
        }.onFailure { runCatching { process.destroyForcibly() } }
    }
}


@Composable
fun V5NativeVideoExtractorScreen() {
    var state by remember { mutableStateOf(VexCoreState()) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { state = VexBridge.state() }

    Column(Modifier.fillMaxSize().background(VexBg), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(color = Color.White, shape = RoundedCornerShape(13.dp), border = BorderStroke(1.dp, VexBorder), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(38.dp).background(VexSoftPurple, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.VideoLibrary, null, tint = VexPurple, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text("Extrator de Vídeos", color = VexInk, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Motor original ${state.version} integrado ao Monitor • download, proxy e Globoplay", color = VexMuted, fontSize = 10.5.sp)
                }
                VexStatusPill(
                    text = if (state.available && state.ffmpeg && state.ytDlp) "Núcleo pronto" else if (state.error.isNotBlank()) "Núcleo indisponível" else "Verificando...",
                    ok = state.available && state.ffmpeg && state.ytDlp
                )
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            VexDownloadScreen(state, onStateChange = { state = it }, onRefresh = { scope.launch { state = VexBridge.state() } })
        }
    }
}

@Composable
private fun VexTabButton(text: String, icon: ImageVector, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Surface(
        color = if (selected) VexSoftBlue else Color.White,
        shape = RoundedCornerShape(11.dp),
        border = BorderStroke(1.dp, if (selected) VexBlue else VexBorder),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Row(Modifier.padding(vertical = 9.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = if (selected) VexBlue else VexMuted, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(7.dp)); Text(text, color = if (selected) VexBlue else VexInk, fontWeight = FontWeight.Bold, fontSize = 11.5.sp)
        }
    }
}

@Composable
private fun VexDownloadScreen(state: VexCoreState, onStateChange: (VexCoreState) -> Unit, onRefresh: () -> Unit) {
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf("") }
    var qualityIndex by remember { mutableIntStateOf(1) }
    val qualities = listOf("360p", "480p", "720p HD", "1080p Full HD", "Melhor disponível")
    var busy by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }
    var status by remember { mutableStateOf("Cole o link, escolha a qualidade e clique em Baixar vídeo.") }
    var statusOk by remember { mutableStateOf(true) }
    var proxyExpanded by remember { mutableStateOf(false) }
    var globoExpanded by remember { mutableStateOf(false) }
    var proxyEnabled by remember(state.proxyEnabled) { mutableStateOf(state.proxyEnabled) }
    var proxyServer by remember(state.proxyServer) { mutableStateOf(state.proxyServer) }
    var proxyPort by remember(state.proxyPort) { mutableStateOf(state.proxyPort) }
    var proxyUser by remember { mutableStateOf("") }
    var proxyPassword by remember { mutableStateOf("") }

    fun fail(text: String) { status = text; statusOk = false }
    fun openFolder() {
        runCatching {
            val folder = state.videoDir.takeIf { it.isNotBlank() }?.let(::File) ?: VexBridge.videoFolder()
            require(folder != null) { "Pasta de vídeos não encontrada." }
            folder.mkdirs(); Desktop.getDesktop().open(folder)
        }.onFailure { fail(it.message ?: "Não foi possível abrir a pasta.") }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Surface(color = Color.White, shape = RoundedCornerShape(13.dp), border = BorderStroke(1.dp, VexBorder), modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("DOWNLOAD DE VÍDEO", color = VexBlue, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                        Text("YouTube, Live, R7/Record, Globoplay e páginas compatíveis", color = VexInk, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    }
                    VexStatusPill(if (proxyEnabled) "Proxy ativo" else "Conexão direta", true)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        modifier = Modifier.weight(1f),
                        label = { Text("Link do vídeo") },
                        placeholder = { Text("https://...") },
                        leadingIcon = { Icon(Icons.Default.Link, null) },
                        singleLine = true
                    )
                    VexSelector(qualities[qualityIndex], qualities, onSelect = { qualityIndex = it }, modifier = Modifier.width(180.dp))
                    Button(
                        onClick = {
                            if (!state.available) { fail("Núcleo do Extrator de Vídeos não está disponível no pacote."); return@Button }
                            busy = true; progress = 0; statusOk = true; status = "Preparando download..."
                            scope.launch {
                                val result = runCatching {
                                    VexBridge.download(url.trim(), qualityIndex, proxyEnabled, proxyServer, proxyPort, proxyUser, proxyPassword,
                                        onProgress = { progress = it }, onMessage = { if (it.isNotBlank()) status = it })
                                }.getOrElse { JSONObject().put("ok", false).put("error", it.message ?: "Falha no download.") }
                                busy = false
                                if (result.optBoolean("ok", false)) {
                                    progress = 100; statusOk = true
                                    val path = result.optString("result", "")
                                    status = if (path.isNotBlank()) "✓ Download concluído: ${File(path).name}" else "✓ Download concluído."
                                } else if (result.optBoolean("canceled", false)) {
                                    status = "Download cancelado."; statusOk = true
                                } else fail(result.optString("error", "Falha no download."))
                            }
                        },
                        enabled = !busy && url.trim().startsWith("http", true),
                        modifier = Modifier.height(56.dp)
                    ) {
                        if (busy) CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp, color = Color.White) else Icon(Icons.Default.Download, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp)); Text(if (busy) "Baixando..." else "Baixar vídeo")
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = { VexBridge.cancel(); busy = false; status = "Cancelamento solicitado." }, enabled = busy) {
                        Icon(Icons.Default.Cancel, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(5.dp)); Text("Cancelar")
                    }
                    OutlinedButton(onClick = {
                        if (busy) return@OutlinedButton
                        busy = true; status = "Atualizando yt-dlp..."; statusOk = true
                        scope.launch {
                            val r = runCatching { VexBridge.updateYtDlp(proxyEnabled, proxyServer, proxyPort, proxyUser, proxyPassword) { if (it.isNotBlank()) status = it } }
                                .getOrElse { JSONObject().put("ok", false).put("error", it.message ?: "Falha ao atualizar yt-dlp.") }
                            busy = false
                            if (r.optBoolean("ok", false)) { status = "✓ yt-dlp atualizado."; statusOk = true; onRefresh() }
                            else fail(r.optString("error", "Falha ao atualizar yt-dlp."))
                        }
                    }, enabled = !busy && state.available) {
                        Icon(Icons.Default.Update, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(5.dp)); Text("Atualizar yt-dlp")
                    }
                    OutlinedButton(onClick = ::openFolder) {
                        Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(5.dp)); Text("Abrir pasta de vídeos")
                    }
                    Spacer(Modifier.weight(1f))
                    Text("MP4 • qualidade real escolhida", color = VexMuted, fontSize = 10.sp)
                }
                LinearProgressIndicator(progress = { (progress / 100f).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(7.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (statusOk) Icons.Default.Info else Icons.Default.Error, null, tint = if (statusOk) VexBlue else VexRed, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(7.dp)); Text(status, color = if (statusOk) VexInk else VexRed, fontSize = 10.5.sp, modifier = Modifier.weight(1f))
                    Text("$progress%", color = VexInk, fontWeight = FontWeight.Bold, fontSize = 10.5.sp)
                }
                if (!state.available && state.error.isNotBlank()) {
                    VexNotice(state.error, VexRed)
                } else {
                    Text(
                        "yt-dlp ${if (state.ytDlp) "OK" else "ausente"}  •  FFmpeg ${if (state.ffmpeg) "OK" else "ausente"}  •  FFprobe ${if (state.ffprobe) "OK" else "ausente"}  •  Deno ${if (state.deno) "OK" else "ausente"}",
                        color = VexMuted, fontSize = 9.5.sp
                    )
                }
            }
        }

        VexExpandableCard("Conexão / Proxy", if (proxyEnabled) "Proxy ${proxyServer.ifBlank { "configurado" }}:${proxyPort.ifBlank { "?" }}" else "Conexão direta", Icons.Default.VpnLock, proxyExpanded, { proxyExpanded = !proxyExpanded }) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = proxyEnabled, onCheckedChange = { proxyEnabled = it }); Spacer(Modifier.width(8.dp))
                Text("Usar proxy", color = VexInk, fontWeight = FontWeight.Bold)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(proxyServer, { proxyServer = it }, label = { Text("Servidor") }, singleLine = true, modifier = Modifier.weight(1f), enabled = proxyEnabled)
                OutlinedTextField(proxyPort, { proxyPort = it }, label = { Text("Porta") }, singleLine = true, modifier = Modifier.width(130.dp), enabled = proxyEnabled)
                OutlinedTextField(proxyUser, { proxyUser = it }, label = { Text("Usuário") }, singleLine = true, modifier = Modifier.weight(0.8f), enabled = proxyEnabled)
                OutlinedTextField(proxyPassword, { proxyPassword = it }, label = { Text("Senha") }, singleLine = true, visualTransformation = PasswordVisualTransformation(), modifier = Modifier.weight(0.8f), enabled = proxyEnabled)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(onClick = {
                    scope.launch {
                        val r = VexBridge.saveProxy(proxyEnabled, proxyServer, proxyPort)
                        if (r.optBoolean("ok", false)) { status = "✓ Configuração de proxy salva. Usuário e senha permanecem apenas nesta sessão."; statusOk = true; onStateChange(state.copy(proxyEnabled = proxyEnabled, proxyServer = proxyServer, proxyPort = proxyPort)) }
                        else fail(r.optString("error", "Não foi possível salvar o proxy."))
                    }
                }, enabled = state.available) { Icon(Icons.Default.Save, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(5.dp)); Text("Salvar configuração") }
                Spacer(Modifier.width(10.dp)); Text("Usuário e senha do proxy não são gravados.", color = VexMuted, fontSize = 10.sp)
            }
        }

        VexExpandableCard("Globoplay", if (state.globoplaySession) "Sessão autenticada salva e protegida pelo Windows" else "Sessão não salva", Icons.Default.Lock, globoExpanded, { globoExpanded = !globoExpanded }) {
            VexNotice("O login é feito somente na página oficial do Globoplay. A senha não é recebida pelo Monitor. A sessão/cookies é protegida pelo Windows, como no Extrator original.", VexBlue)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    if (busy) return@Button
                    busy = true; status = "Abrindo login oficial do Globoplay..."; statusOk = true
                    scope.launch {
                        val r = runCatching { VexBridge.globoplayLogin(proxyEnabled, proxyServer, proxyPort, proxyUser, proxyPassword) }
                            .getOrElse { JSONObject().put("ok", false).put("error", it.message ?: "Falha no login Globoplay.") }
                        busy = false
                        if (r.optBoolean("ok", false)) { status = "✓ Sessão do Globoplay salva."; statusOk = true; onRefresh() }
                        else if (r.optString("error").isNotBlank()) fail(r.optString("error")) else { status = "Login do Globoplay fechado sem salvar."; statusOk = true }
                    }
                }, enabled = state.available && !busy) { Icon(Icons.Default.Login, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(5.dp)); Text("Entrar / atualizar sessão Globoplay") }
                OutlinedButton(onClick = {
                    scope.launch {
                        val r = VexBridge.deleteGloboplaySession()
                        if (r.optBoolean("ok", false)) { status = "Sessão Globoplay apagada."; statusOk = true; onRefresh() }
                        else fail(r.optString("error", "Falha ao apagar sessão."))
                    }
                }, enabled = state.available && state.globoplaySession) { Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(5.dp)); Text("Apagar sessão") }
            }
            Text("DRM, assinatura sem acesso, bloqueio regional e outras restrições continuam sendo respeitados; o aplicativo não tenta contornar proteção de conteúdo.", color = VexMuted, fontSize = 9.5.sp)
        }
        Spacer(Modifier.height(4.dp))
    }
}


private fun localSourceAtGlobal(index: Int, globalMs: Long, clips: List<VexClip>): Long {
    val before = clips.take(index.coerceAtLeast(0)).sumOf { it.selectedDurationMs }
    return (globalMs - before).coerceIn(0, clips.getOrNull(index)?.selectedDurationMs ?: 0L)
}

private fun pickVideoFiles(defaultDir: String): List<File> {
    val dialog = FileDialog(null as Frame?, "Adicionar vídeos à timeline", FileDialog.LOAD).apply {
        isMultipleMode = true
        directory = defaultDir.takeIf { it.isNotBlank() && File(it).isDirectory }
        filenameFilter = java.io.FilenameFilter { _, name -> name.substringAfterLast('.', "").lowercase() in setOf("mp4", "mkv", "webm", "mov", "avi", "m4v") }
        isVisible = true
    }
    return dialog.files?.toList().orEmpty()
}

private fun vexTime(ms: Long): String {
    val safe = ms.coerceAtLeast(0)
    val millis = safe % 1000
    val total = safe / 1000
    val sec = total % 60
    val min = (total / 60) % 60
    val hour = total / 3600
    return "%02d:%02d:%02d.%03d".format(hour, min, sec, millis)
}

private fun vexParseTime(text: String): Long {
    val parts = text.trim().replace(',', '.').split(':')
    require(parts.size in 2..3) { "Use MM:SS.mmm ou HH:MM:SS.mmm" }
    val h: Long; val m: Long; val s: Double
    if (parts.size == 2) { h = 0; m = parts[0].toLong(); s = parts[1].toDouble() }
    else { h = parts[0].toLong(); m = parts[1].toLong(); s = parts[2].toDouble() }
    require(h >= 0 && m in 0..59 && s >= 0 && s < 60) { "Tempo inválido." }
    return ((h * 3600 + m * 60) * 1000 + s * 1000).roundToInt().toLong()
}

@Composable
private fun VexSelector(selected: String, options: List<String>, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Icon(Icons.Default.HighQuality, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(5.dp)); Text(selected, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.weight(1f)); Icon(Icons.Default.ArrowDropDown, null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEachIndexed { i, value -> DropdownMenuItem(text = { Text(value) }, onClick = { open = false; onSelect(i) }) }
        }
    }
}

@Composable
private fun VexStringSelector(selected: String, options: List<String>, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { open = true }, modifier = Modifier.fillMaxWidth().height(56.dp)) {
            Text(selected, maxLines = 1, overflow = TextOverflow.Ellipsis); Spacer(Modifier.weight(1f)); Icon(Icons.Default.ArrowDropDown, null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { value -> DropdownMenuItem(text = { Text(value) }, onClick = { open = false; onSelect(value) }) }
        }
    }
}

@Composable
private fun VexExpandableCard(title: String, subtitle: String, icon: ImageVector, expanded: Boolean, onToggle: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Surface(color = Color.White, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, VexBorder), modifier = Modifier.fillMaxWidth()) {
        Column {
            TextButton(onClick = onToggle, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 13.dp, vertical = 8.dp)) {
                Icon(icon, null, tint = VexBlue, modifier = Modifier.size(19.dp)); Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) { Text(title, color = VexInk, fontWeight = FontWeight.Bold, fontSize = 11.5.sp); Text(subtitle, color = VexMuted, fontSize = 9.5.sp) }
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = VexMuted)
            }
            if (expanded) { HorizontalDivider(color = VexBorder); Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp), content = content) }
        }
    }
}

@Composable
private fun VexNotice(text: String, accent: Color) {
    Surface(color = if (accent == VexRed) Color(0xFFFFEEF0) else VexSoftBlue, shape = RoundedCornerShape(9.dp), border = BorderStroke(1.dp, if (accent == VexRed) Color(0xFFF0BFC7) else VexBorder), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(9.dp), verticalAlignment = Alignment.Top) { Icon(Icons.Default.Info, null, tint = accent, modifier = Modifier.size(15.dp)); Spacer(Modifier.width(7.dp)); Text(text, color = VexInk, fontSize = 9.5.sp, modifier = Modifier.weight(1f)) }
    }
}

@Composable
private fun VexStatusPill(text: String, ok: Boolean) {
    Surface(color = if (ok) Color(0xFFEAF9F3) else Color(0xFFFFEEF0), shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, if (ok) Color(0xFFBDEBD8) else Color(0xFFF0BFC7))) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (ok) Icons.Default.CheckCircle else Icons.Default.Error, null, tint = if (ok) VexGreen else VexRed, modifier = Modifier.size(14.dp)); Spacer(Modifier.width(5.dp)); Text(text, color = VexInk, fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
