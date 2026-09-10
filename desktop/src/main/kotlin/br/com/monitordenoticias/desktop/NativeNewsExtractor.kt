package br.com.monitordenoticias.desktop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sun.jna.platform.win32.Crypt32Util
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.awt.Desktop
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.Base64

private val NexInk = Color(0xFF0A1F4B)
private val NexMuted = Color(0xFF58739F)
private val NexBlue = Color(0xFF087AF7)
private val NexGreen = Color(0xFF08A86F)
private val NexRed = Color(0xFFD92F43)
private val NexBg = Color(0xFFF3F8FE)
private val NexBorder = Color(0xFFD5E4F3)
private val NexSoftBlue = Color(0xFFEAF4FF)

private data class NativeExtractResult(
    val ok: Boolean,
    val error: String = "",
    val formatted: String = "",
    val url: String = "",
    val source: String = "",
    val title: String = "",
    val subtitle: String = "",
    val author: String = "",
    val date: String = "",
    val engineVersion: String = "",
    val repeated: Boolean = false
)

private data class ReviewIssue(
    val word: String,
    val offset: Int,
    val length: Int,
    val suggestions: List<String>
)

private object NativeNewsExtractorService {
    private const val FOLDER = "ExtratorMaterias"
    private const val LAST_FILE = "materia-extraida.txt"
    private const val HISTORY_FILE = "materias-extraidas.txt"
    private const val CREDENTIALS_FILE = "credenciais-assinante.json"
    private const val SEP = "\n" + "#".repeat(70) + "\n\n"

    private fun downloadsDir(): File {
        val home = File(System.getProperty("user.home", "."))
        val winDownloads = File(home, "Downloads")
        return File(if (winDownloads.exists() || System.getProperty("os.name").contains("Windows", true)) winDownloads else home, FOLDER)
    }

    fun folder(): File = downloadsDir()
    fun lastFile(): File = File(downloadsDir(), LAST_FILE)
    fun historyFile(): File = File(downloadsDir(), HISTORY_FILE)

    private fun credentialFile(): File {
        val appData = System.getenv("APPDATA")?.takeIf { it.isNotBlank() }?.let(::File)
            ?: File(System.getProperty("user.home", "."), ".monitor-de-noticias")
        return File(File(appData, "MonitorDeNoticias"), CREDENTIALS_FILE)
    }

    fun loadSubscriberUser(): String {
        return runCatching {
            val f = credentialFile()
            if (!f.isFile) return ""
            JSONObject(f.readText(Charsets.UTF_8)).optString("usuario", "")
        }.getOrDefault("")
    }

    fun hasSubscriberPassword(): Boolean {
        return runCatching {
            val f = credentialFile()
            f.isFile && JSONObject(f.readText(Charsets.UTF_8)).optString("payload", "").isNotBlank()
        }.getOrDefault(false)
    }

    fun saveSubscriber(user: String, password: String) {
        require(user.isNotBlank()) { "Informe o usuário/e-mail da assinatura." }
        val previousPassword = if (password.isBlank()) loadSubscriberPassword() else ""
        val finalPassword = password.ifBlank { previousPassword }
        require(finalPassword.isNotBlank()) { "Informe a senha da assinatura." }
        val payload = JSONObject().put("usuario", user.trim()).put("senha", finalPassword).toString()
        val encrypted = Crypt32Util.cryptProtectData(payload.toByteArray(StandardCharsets.UTF_8))
        val out = JSONObject()
            .put("usuario", user.trim())
            .put("payload", Base64.getEncoder().encodeToString(encrypted))
            .put("formato", 1)
        val file = credentialFile()
        file.parentFile?.mkdirs()
        file.writeText(out.toString(2), Charsets.UTF_8)
    }

    private fun loadSubscriberPassword(): String {
        return runCatching {
            val f = credentialFile()
            if (!f.isFile) return ""
            val encoded = JSONObject(f.readText(Charsets.UTF_8)).optString("payload", "")
            if (encoded.isBlank()) return ""
            val clear = Crypt32Util.cryptUnprotectData(Base64.getDecoder().decode(encoded))
            JSONObject(String(clear, StandardCharsets.UTF_8)).optString("senha", "")
        }.getOrDefault("")
    }

    fun deleteSubscriber() {
        runCatching { credentialFile().delete() }
    }

    suspend fun extract(url: String, useProxy: Boolean, proxyUser: String, proxyPassword: String): NativeExtractResult =
        withContext(Dispatchers.IO) {
            val req = JSONObject()
                .put("action", "extract")
                .put("url", url.trim())
                .put("usarProxy", useProxy)
                .put("usuarioProxy", proxyUser)
                .put("senhaProxy", proxyPassword)
            val response = callBridge(req)
            if (!response.optBoolean("ok", false)) {
                return@withContext NativeExtractResult(false, response.optString("erro", "Falha na extração."))
            }
            val formatted = response.optString("formatado", "")
            val finalUrl = response.optString("url", url.trim())
            val repeated = saveResult(finalUrl, formatted)
            NativeExtractResult(
                ok = true,
                formatted = formatted,
                url = finalUrl,
                source = response.optString("veiculo", ""),
                title = response.optString("titulo", ""),
                subtitle = response.optString("subtitulo", ""),
                author = response.optString("autor", ""),
                date = response.optString("data", ""),
                engineVersion = response.optString("versaoMotor", ""),
                repeated = repeated
            )
        }

    suspend fun review(text: String): List<ReviewIssue> = withContext(Dispatchers.IO) {
        val response = callBridge(JSONObject().put("action", "review").put("texto", text))
        if (!response.optBoolean("ok", false)) throw IllegalStateException(response.optString("erro", "Falha na revisão."))
        val arr = response.optJSONArray("issues") ?: JSONArray()
        buildList {
            for (i in 0 until arr.length()) {
                val item = arr.optJSONObject(i) ?: continue
                val suggestions = item.optJSONArray("sugestoes")
                add(
                    ReviewIssue(
                        word = item.optString("palavra", ""),
                        offset = item.optInt("offset", 0),
                        length = item.optInt("length", item.optString("palavra", "").length),
                        suggestions = buildList {
                            if (suggestions != null) for (j in 0 until suggestions.length()) add(suggestions.optString(j))
                        }.filter { it.isNotBlank() }
                    )
                )
            }
        }
    }

    suspend fun engineVersion(): String = withContext(Dispatchers.IO) {
        runCatching { callBridge(JSONObject().put("action", "state")).optString("versaoMotor", "1.25.19") }.getOrDefault("1.25.19")
    }

    private fun saveResult(url: String, formatted: String): Boolean {
        val folder = downloadsDir()
        folder.mkdirs()
        lastFile().writeText(formatted, Charsets.UTF_8)
        val history = historyFile()
        val repeated = history.isFile && history.readText(Charsets.UTF_8).contains(url, ignoreCase = true)
        if (!repeated) history.appendText(formatted + SEP, Charsets.UTF_8)
        return repeated
    }

    fun saveEdited(text: String) {
        downloadsDir().mkdirs()
        lastFile().writeText(text, Charsets.UTF_8)
    }

    fun openFolder() = openPath(downloadsDir().also { it.mkdirs() })
    fun openLast() = openPath(lastFile())
    fun openHistory() = openPath(historyFile())

    private fun openPath(file: File) {
        if (!file.exists()) throw IllegalStateException("O arquivo ainda não foi criado.")
        if (!Desktop.isDesktopSupported()) throw IllegalStateException("Abertura de arquivos não disponível.")
        Desktop.getDesktop().open(file)
    }

    private fun callBridge(request: JSONObject): JSONObject {
        val core = resolveCore() ?: throw IllegalStateException("Núcleo do Extrator de Notícias não encontrado no pacote.")
        val node = File(core, "node.exe")
        val bridge = File(core, "monitor-bridge.js")
        if (!node.isFile || !bridge.isFile) throw IllegalStateException("Runtime do Extrator de Notícias incompleto.")

        val p = ProcessBuilder(node.absolutePath, bridge.absolutePath)
            .directory(core)
            .redirectErrorStream(false)
            .start()
        p.outputStream.bufferedWriter(Charsets.UTF_8).use { it.write(request.toString()) }
        val stdout = p.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        val stderr = p.errorStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        p.waitFor()
        if (stdout.isBlank()) throw IllegalStateException(stderr.ifBlank { "O motor não retornou resposta." }.take(800))
        return JSONObject(stdout)
    }

    private fun resolveCore(): File? {
        val roots = linkedSetOf<File>()
        fun addParents(start: File?) {
            var f = start?.absoluteFile
            repeat(8) {
                if (f == null) return@repeat
                roots += f!!
                f = f!!.parentFile
            }
        }
        addParents(File(System.getProperty("user.dir", ".")))
        ProcessHandle.current().info().command().orElse(null)?.let { addParents(File(it).parentFile) }
        System.getProperty("java.class.path", "").split(File.pathSeparatorChar)
            .filter { it.isNotBlank() }
            .forEach { addParents(File(it).parentFile) }
        return roots.asSequence().map { File(it, "tools/ExtratorNoticiasCore") }.firstOrNull { it.isDirectory }
    }
}

@Composable
fun V5NativeNewsExtractorScreen() {
    val scope = rememberCoroutineScope()
    var url by remember { mutableStateOf("") }
    var useProxy by remember { mutableStateOf(false) }
    var proxyUser by remember { mutableStateOf("") }
    var proxyPassword by remember { mutableStateOf("") }
    var subscriberUser by remember { mutableStateOf(NativeNewsExtractorService.loadSubscriberUser()) }
    var subscriberPassword by remember { mutableStateOf("") }
    var subscriberSaved by remember { mutableStateOf(NativeNewsExtractorService.hasSubscriberPassword()) }
    var busy by remember { mutableStateOf(false) }
    var reviewing by remember { mutableStateOf(false) }
    var status by remember { mutableStateOf("Pronto para receber um link.") }
    var statusOk by remember { mutableStateOf(true) }
    var result by remember { mutableStateOf<NativeExtractResult?>(null) }
    var editedText by remember { mutableStateOf("") }
    var issues by remember { mutableStateOf<List<ReviewIssue>>(emptyList()) }
    var engineVersion by remember { mutableStateOf("1.25.19") }
    var proxyExpanded by remember { mutableStateOf(false) }
    var subscriberExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) { engineVersion = NativeNewsExtractorService.engineVersion() }

    fun reportError(message: String) {
        status = message
        statusOk = false
    }

    Column(
        Modifier.fillMaxSize().background(NexBg).verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Surface(
            color = Color.White,
            shape = RoundedCornerShape(13.dp),
            border = BorderStroke(1.dp, NexBorder),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(Modifier.padding(15.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(38.dp).background(NexSoftBlue, RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) { Icon(Icons.Default.Description, null, tint = NexBlue, modifier = Modifier.size(22.dp)) }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Extrator de Notícias", color = NexInk, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                        Text("Motor original v$engineVersion integrado ao Monitor", color = NexMuted, fontSize = 11.sp)
                    }
                    Surface(
                        color = if (statusOk) Color(0xFFEAF9F3) else Color(0xFFFFEEF0),
                        shape = RoundedCornerShape(20.dp),
                        border = BorderStroke(1.dp, if (statusOk) Color(0xFFBDEBD8) else Color(0xFFF0BFC7))
                    ) {
                        Row(Modifier.padding(horizontal = 11.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (statusOk) Icons.Default.CheckCircle else Icons.Default.Error, null, tint = if (statusOk) NexGreen else NexRed, modifier = Modifier.size(15.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(status, color = NexInk, fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = url,
                        onValueChange = { url = it },
                        modifier = Modifier.weight(1f),
                        label = { Text("Link da matéria") },
                        placeholder = { Text("https://exemplo.com/noticia/materia-completa") },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Default.Link, null) }
                    )
                    Spacer(Modifier.width(10.dp))
                    Button(
                        onClick = {
                            if (url.isBlank()) { reportError("Cole o link da matéria."); return@Button }
                            busy = true; statusOk = true; status = "Lendo e limpando a matéria..."
                            issues = emptyList()
                            scope.launch {
                                val r = runCatching { NativeNewsExtractorService.extract(url, useProxy, proxyUser, proxyPassword) }
                                    .getOrElse { NativeExtractResult(false, it.message ?: "Falha na extração.") }
                                busy = false
                                if (!r.ok) reportError(r.error)
                                else {
                                    result = r
                                    editedText = r.formatted
                                    statusOk = true
                                    status = if (r.repeated) "✓ Matéria salva. A URL já existia no histórico." else "✓ Matéria salva e adicionada ao histórico."
                                }
                            }
                        },
                        enabled = !busy && url.trim().startsWith("http", true),
                        modifier = Modifier.height(56.dp)
                    ) {
                        if (busy) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = Color.White)
                        else Icon(Icons.Default.Download, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(7.dp)); Text(if (busy) "Extraindo..." else "Extrair matéria")
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { url = ""; result = null; editedText = ""; issues = emptyList(); status = "Pronto para receber um link."; statusOk = true }) {
                        Icon(Icons.Default.Clear, null, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(5.dp)); Text("Limpar")
                    }
                    OutlinedButton(onClick = { runCatching { NativeNewsExtractorService.openFolder() }.onFailure { reportError(it.message ?: "Falha ao abrir pasta.") } }) {
                        Icon(Icons.Default.FolderOpen, null, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(5.dp)); Text("Abrir pasta")
                    }
                    OutlinedButton(onClick = { runCatching { NativeNewsExtractorService.openHistory() }.onFailure { reportError(it.message ?: "Falha ao abrir histórico.") } }) {
                        Icon(Icons.Default.History, null, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(5.dp)); Text("Histórico")
                    }
                    OutlinedButton(onClick = { runCatching { NativeNewsExtractorService.openLast() }.onFailure { reportError(it.message ?: "Falha ao abrir último TXT.") } }) {
                        Icon(Icons.Default.Description, null, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(5.dp)); Text("Último TXT")
                    }
                }
            }
        }

        NativeExpandableCard(
            title = "Conexão / Proxy",
            subtitle = if (useProxy) "Proxy corporativo ativo para o extrator" else "Conexão direta; proxy opcional",
            icon = Icons.Default.VpnLock,
            expanded = proxyExpanded,
            onToggle = { proxyExpanded = !proxyExpanded }
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(checked = useProxy, onCheckedChange = { useProxy = it })
                Spacer(Modifier.width(8.dp))
                Text("Usar proxy corporativo", color = NexInk, fontWeight = FontWeight.SemiBold)
            }
            if (useProxy) {
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(proxyUser, { proxyUser = it }, label = { Text("Usuário") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(proxyPassword, { proxyPassword = it }, label = { Text("Senha") }, modifier = Modifier.weight(1f), singleLine = true, visualTransformation = PasswordVisualTransformation())
                }
                Text("Servidor e porta continuam sendo lidos da configuração original do extrator.", color = NexMuted, fontSize = 10.sp)
            }
        }

        NativeExpandableCard(
            title = "Acesso de assinante",
            subtitle = if (subscriberSaved) "Valor / Globo salvo com proteção local do Windows" else "Valor / Globo — nenhum acesso salvo",
            icon = Icons.Default.Lock,
            expanded = subscriberExpanded,
            onToggle = { subscriberExpanded = !subscriberExpanded }
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(subscriberUser, { subscriberUser = it }, label = { Text("Usuário / e-mail") }, modifier = Modifier.weight(1f), singleLine = true)
                OutlinedTextField(subscriberPassword, { subscriberPassword = it }, label = { Text(if (subscriberSaved) "Nova senha (opcional)" else "Senha") }, modifier = Modifier.weight(1f), singleLine = true, visualTransformation = PasswordVisualTransformation())
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = {
                    runCatching { NativeNewsExtractorService.saveSubscriber(subscriberUser, subscriberPassword) }
                        .onSuccess { subscriberSaved = true; subscriberPassword = ""; statusOk = true; status = "✓ Acesso de assinante salvo com proteção do Windows." }
                        .onFailure { reportError(it.message ?: "Falha ao salvar acesso.") }
                }) { Icon(Icons.Default.Save, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(5.dp)); Text("Salvar acesso") }
                OutlinedButton(onClick = {
                    NativeNewsExtractorService.deleteSubscriber(); subscriberSaved = false; subscriberUser = ""; subscriberPassword = ""; status = "Acesso de assinante removido."; statusOk = true
                }, enabled = subscriberSaved) { Icon(Icons.Default.Delete, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(5.dp)); Text("Apagar acesso") }
            }
        }

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.Top) {
            Surface(
                color = Color.White,
                shape = RoundedCornerShape(13.dp),
                border = BorderStroke(1.dp, NexBorder),
                modifier = Modifier.weight(0.36f)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("DADOS IDENTIFICADOS", color = NexBlue, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Matéria extraída", color = NexInk, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    NativeMeta("Título", result?.title.orEmpty().ifBlank { "Aguardando extração" }, Icons.Default.Title)
                    NativeMeta("Veículo", result?.source.orEmpty().ifBlank { "—" }, Icons.Default.Newspaper)
                    NativeMeta("Data", result?.date.orEmpty().ifBlank { "—" }, Icons.Default.CalendarMonth)
                    NativeMeta("Autor", result?.author.orEmpty().ifBlank { "—" }, Icons.Default.Person)
                    NativeMeta("Subtítulo", result?.subtitle.orEmpty().ifBlank { "—" }, Icons.Default.Subject)
                    NativeMeta("Link", result?.url.orEmpty().ifBlank { "—" }, Icons.Default.Link)
                }
            }

            Surface(
                color = Color.White,
                shape = RoundedCornerShape(13.dp),
                border = BorderStroke(1.dp, NexBorder),
                modifier = Modifier.weight(0.64f)
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("CONTEÚDO", color = NexBlue, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
                            Text("Texto da matéria", color = NexInk, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                        Text(if (editedText.isBlank()) "Nenhuma matéria extraída" else "${editedText.length} caracteres", color = NexMuted, fontSize = 10.sp)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(
                            onClick = {
                                if (editedText.isBlank()) return@OutlinedButton
                                reviewing = true; statusOk = true; status = "Revisando PT-BR em modo rígido..."
                                scope.launch {
                                    runCatching { NativeNewsExtractorService.review(editedText) }
                                        .onSuccess { found -> issues = found; statusOk = true; status = if (found.isEmpty()) "✓ Nenhum erro ortográfico encontrado pelo modo rígido PT-BR." else "Revisão concluída: ${found.size} ocorrência(s) para conferir." }
                                        .onFailure { reportError(it.message ?: "Falha na revisão rígida.") }
                                    reviewing = false
                                }
                            },
                            enabled = editedText.isNotBlank() && !reviewing
                        ) {
                            if (reviewing) CircularProgressIndicator(Modifier.size(15.dp), strokeWidth = 2.dp)
                            else Icon(Icons.Default.Spellcheck, null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(5.dp)); Text("Revisar PT-BR rígido")
                        }
                        OutlinedButton(onClick = {
                            if (editedText.isNotBlank()) {
                                Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(editedText), null)
                                status = "✓ Texto copiado para a área de transferência."; statusOk = true
                            }
                        }, enabled = editedText.isNotBlank()) { Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(5.dp)); Text("Copiar texto") }
                        OutlinedButton(onClick = { runCatching { NativeNewsExtractorService.saveEdited(editedText) }.onSuccess { status = "✓ Alterações salvas no último TXT."; statusOk = true }.onFailure { reportError(it.message ?: "Falha ao salvar.") } }, enabled = editedText.isNotBlank()) {
                            Icon(Icons.Default.Save, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(5.dp)); Text("Salvar edição")
                        }
                    }

                    if (issues.isNotEmpty()) {
                        Surface(color = Color(0xFFFFF8E8), shape = RoundedCornerShape(10.dp), border = BorderStroke(1.dp, Color(0xFFF0D9A1))) {
                            Column(Modifier.padding(9.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text("Sugestões do corretor rígido", color = NexInk, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                issues.take(12).forEach { issue ->
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                        Text(issue.word, color = NexRed, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.widthIn(min = 70.dp))
                                        if (issue.suggestions.isEmpty()) Text("sem sugestão", color = NexMuted, fontSize = 10.sp)
                                        else issue.suggestions.take(4).forEach { suggestion ->
                                            AssistChip(onClick = {
                                                val start = issue.offset.coerceIn(0, editedText.length)
                                                val end = (start + issue.length).coerceIn(start, editedText.length)
                                                if (editedText.substring(start, end) == issue.word) {
                                                    editedText = editedText.substring(0, start) + suggestion + editedText.substring(end)
                                                    issues = emptyList()
                                                }
                                            }, label = { Text(suggestion, fontSize = 9.sp) })
                                        }
                                    }
                                }
                                if (issues.size > 12) Text("+ ${issues.size - 12} ocorrência(s) adicionais", color = NexMuted, fontSize = 9.sp)
                            }
                        }
                    }

                    Surface(
                        color = Color(0xFFFBFDFF),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, NexBorder),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 360.dp)
                    ) {
                        if (editedText.isBlank()) {
                            Column(Modifier.fillMaxWidth().padding(34.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(7.dp)) {
                                Icon(Icons.Default.Article, null, tint = Color(0xFF9BB4D2), modifier = Modifier.size(34.dp))
                                Text("O conteúdo aparecerá aqui", color = NexInk, fontWeight = FontWeight.Bold)
                                Text("Informe um link acima e clique em “Extrair matéria”.", color = NexMuted, fontSize = 11.sp)
                            }
                        } else {
                            BasicTextField(
                                value = editedText,
                                onValueChange = { editedText = it; issues = emptyList() },
                                textStyle = TextStyle(color = NexInk, fontSize = 12.sp, lineHeight = 18.sp),
                                modifier = Modifier.fillMaxWidth().padding(13.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun NativeExpandableCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(color = Color.White, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, NexBorder), modifier = Modifier.fillMaxWidth()) {
        Column {
            TextButton(onClick = onToggle, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 13.dp, vertical = 9.dp)) {
                Icon(icon, null, tint = NexBlue, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                    Text(title, color = NexInk, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text(subtitle, color = NexMuted, fontSize = 10.sp)
                }
                Icon(if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = NexMuted)
            }
            if (expanded) {
                HorizontalDivider(color = NexBorder)
                Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(9.dp), content = content)
            }
        }
    }
}

@Composable
private fun NativeMeta(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Surface(color = Color(0xFFF7FAFE), shape = RoundedCornerShape(9.dp), border = BorderStroke(1.dp, Color(0xFFE2ECF6)), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(9.dp), verticalAlignment = Alignment.Top) {
            Icon(icon, null, tint = NexBlue, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(label, color = NexMuted, fontSize = 9.sp)
                Text(value, color = NexInk, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}
