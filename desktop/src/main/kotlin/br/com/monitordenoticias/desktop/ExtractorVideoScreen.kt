package br.com.monitordenoticias.desktop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.atomic.AtomicReference

private val ExBg = Color(0xFF070A12)
private val ExCard = Color(0xFF0D1421)
private val ExBorder = Color(0xFF25314A)
private val ExText = Color(0xFFF1F5FB)
private val ExMuted = Color(0xFF93A1B8)
private val ExPurple = Color(0xFF743AF3)
private val ExBlue = Color(0xFF256EFF)
private val ExCyan = Color(0xFF22D3EE)
private val ExDanger = Color(0xFFB93B4C)

private enum class ExtractorPage(val label: String) {
    DOWNLOAD("Download"), HISTORY("Histórico"), SETTINGS("Configurações")
}

private data class DirectQuality(val label: String, val selector: String, val compat: String)

private val DIRECT_QUALITIES = listOf(
    DirectQuality("360p", "bv*[height<=360][ext=mp4]+ba[ext=m4a]/b[height<=360][ext=mp4]/bv*[height<=360]+ba/b[height<=360]/b", "b[height<=360][ext=mp4]/b[height<=360]/b"),
    DirectQuality("480p", "bv*[height<=480][ext=mp4]+ba[ext=m4a]/b[height<=480][ext=mp4]/bv*[height<=480]+ba/b[height<=480]/b", "b[height<=480][ext=mp4]/b[height<=480]/b"),
    DirectQuality("720p HD", "bv*[height<=720][ext=mp4]+ba[ext=m4a]/b[height<=720][ext=mp4]/bv*[height<=720]+ba/b[height<=720]/b", "b[height<=720][ext=mp4]/b[height<=720]/b"),
    DirectQuality("1080p Full HD", "bv*[height<=1080][ext=mp4]+ba[ext=m4a]/b[height<=1080][ext=mp4]/bv*[height<=1080]+ba/b[height<=1080]/b", "b[height<=1080][ext=mp4]/b[height<=1080]/b"),
    DirectQuality("Melhor disponível", "bv*[ext=mp4]+ba[ext=m4a]/b[ext=mp4]/bv*+ba/b", "b[ext=mp4]/b")
)

private class ExtractorRuntime {
    val appDir: Path = discoverAppDir()
    val binDir: File = appDir.resolve("bin").toFile()
    val videosDir: File = appDir.resolve("Videos").toFile().apply { mkdirs() }
    val ytDlp = File(binDir, "yt-dlp.exe")
    val ytDlpStable = File(binDir, "yt-dlp-stable.exe")
    val ffmpeg = File(binDir, "ffmpeg.exe")
    val ffprobe = File(binDir, "ffprobe.exe")
    val deno = File(binDir, "deno.exe")
    val process = AtomicReference<Process?>(null)

    companion object {
        private fun discoverAppDir(): Path {
            val cwd = Paths.get(System.getProperty("user.dir", ".")).toAbsolutePath().normalize()
            val candidates = listOf(cwd, cwd.parent ?: cwd)
            return candidates.firstOrNull { it.resolve("bin").toFile().exists() } ?: cwd
        }
    }
}

@Composable
fun ExtractorVideoScreen(onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    val runtime = remember { ExtractorRuntime() }
    var page by remember { mutableStateOf(ExtractorPage.DOWNLOAD) }
    var url by remember { mutableStateOf("") }
    var qualityIndex by remember { mutableIntStateOf(1) }
    var status by remember { mutableStateOf("Cole o link, escolha a qualidade e clique em BAIXAR VÍDEO.") }
    var progress by remember { mutableFloatStateOf(0f) }
    var busy by remember { mutableStateOf(false) }
    var history by remember { mutableStateOf(listOf<String>()) }
    var proxy by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().background(ExBg).padding(18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("EXTRATOR DE VÍDEOS", color = ExText, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold)
                Text("Motor compatível com o fluxo Windows Portable v3.0.1", color = ExMuted, fontSize = 12.sp)
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
            ExtractorPage.DOWNLOAD -> {
                Surface(
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
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = ExText, unfocusedTextColor = ExText,
                                focusedBorderColor = ExPurple, unfocusedBorderColor = ExBorder,
                                cursorColor = ExCyan
                            )
                        )

                        Text("Qualidade do vídeo", color = ExText, fontWeight = FontWeight.SemiBold)
                        DIRECT_QUALITIES.forEachIndexed { index, item ->
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
                            onClick = {
                                val chosen = DIRECT_QUALITIES[qualityIndex]
                                busy = true
                                progress = 0f
                                status = "Iniciando download direto em ${chosen.label}..."
                                scope.launch {
                                    val result = runDirectDownload(runtime, url.trim(), chosen, proxy) { pct, msg ->
                                        progress = pct / 100f
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
                            },
                            modifier = Modifier.fillMaxWidth().height(50.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = ExPurple)
                        ) { Text("↓  BAIXAR VÍDEO", fontWeight = FontWeight.Bold) }

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { runCatching { Desktop.getDesktop().open(runtime.videosDir) } }) {
                                Icon(Icons.Default.FolderOpen, null); Spacer(Modifier.width(6.dp)); Text("Abrir Videos")
                            }
                            OutlinedButton(
                                enabled = busy,
                                onClick = {
                                    runtime.process.getAndSet(null)?.destroyForcibly()
                                    busy = false
                                    status = "Download cancelado."
                                },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = ExDanger)
                            ) { Text("CANCELAR") }
                        }

                        LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth(), color = ExCyan, trackColor = Color(0xFF101827))
                        Text("${(progress * 100).toInt()}%", color = ExText, fontWeight = FontWeight.Bold)
                        Text(status, color = ExMuted)
                        Text(
                            "Binários: yt-dlp=${runtime.ytDlp.exists()} • stable=${runtime.ytDlpStable.exists()} • ffmpeg=${runtime.ffmpeg.exists()} • ffprobe=${runtime.ffprobe.exists()} • deno=${runtime.deno.exists()}",
                            color = ExMuted, fontSize = 10.sp
                        )
                    }
                }
            }
            ExtractorPage.HISTORY -> {
                Surface(Modifier.fillMaxSize(), color = ExCard, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, ExBorder)) {
                    Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("HISTÓRICO", color = ExText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        if (history.isEmpty()) Text("Nenhum download nesta sessão.", color = ExMuted)
                        history.forEach { Text(it, color = ExText, fontSize = 12.sp) }
                    }
                }
            }
            ExtractorPage.SETTINGS -> {
                Surface(Modifier.fillMaxSize(), color = ExCard, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, ExBorder)) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("CONFIGURAÇÕES", color = ExText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                        Text("Proxy opcional", color = ExText, fontWeight = FontWeight.SemiBold)
                        OutlinedTextField(
                            value = proxy, onValueChange = { proxy = it }, modifier = Modifier.fillMaxWidth(),
                            placeholder = { Text("http://usuario:senha@servidor:porta", color = ExMuted) }, singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(focusedTextColor = ExText, unfocusedTextColor = ExText, focusedBorderColor = ExPurple, unfocusedBorderColor = ExBorder)
                        )
                        Text("Globoplay, login interno, sessão DPAPI, atualização atômica do yt-dlp e rotas especiais serão conectados ao mesmo motor v3.0.1 nesta tela sem alterar o Editor de PDF.", color = ExMuted)
                    }
                }
            }
        }
    }
}

@Composable
private fun ExtractorTab(label: String, selected: Boolean, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Surface(
        color = if (selected) ExPurple else ExCard,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, if (selected) ExBlue else ExBorder),
        modifier = Modifier.clickable { onClick() }
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Color.White, modifier = Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text(label, color = Color.White)
        }
    }
}

private suspend fun runDirectDownload(
    runtime: ExtractorRuntime,
    url: String,
    quality: DirectQuality,
    proxy: String,
    update: (Int, String) -> Unit
): Result<File> = withContext(Dispatchers.IO) {
    runCatching {
        require(runtime.ytDlp.exists()) { "yt-dlp.exe não encontrado na pasta bin." }
        require(runtime.ffmpeg.exists()) { "ffmpeg.exe não encontrado na pasta bin." }
        runtime.videosDir.mkdirs()

        val before = runtime.videosDir.listFiles()?.associateBy { it.absolutePath.lowercase() } ?: emptyMap()
        val cmd = mutableListOf(
            runtime.ytDlp.absolutePath,
            "--no-playlist", "--newline", "--progress", "--windows-filenames",
            "--trim-filenames", "180", "--continue",
            "--retries", "10", "--fragment-retries", "10",
            "--retry-sleep", "http:linear=1::3", "--retry-sleep", "fragment:linear=1::3",
            "--socket-timeout", "30",
            "--ffmpeg-location", runtime.binDir.absolutePath,
            "-f", quality.selector,
            "--merge-output-format", "mp4", "--remux-video", "mp4",
            "-o", File(runtime.videosDir, "%(title).150B [%(id)s].%(ext)s").absolutePath,
            "--print", "after_move:FINAL_FILE:%(filepath)s"
        )
        if (runtime.deno.exists()) cmd += listOf("--js-runtimes", "deno:${runtime.deno.absolutePath}")
        if (proxy.isNotBlank()) cmd += listOf("--proxy", proxy.trim())
        cmd += url

        val pb = ProcessBuilder(cmd).redirectErrorStream(true)
        pb.directory(runtime.appDir.toFile())
        val process = pb.start()
        runtime.process.set(process)
        var finalPath = ""
        val reader = BufferedReader(InputStreamReader(process.inputStream, Charsets.UTF_8))
        val percentRegex = Regex("(\\d{1,3}(?:\\.\\d+)?)%")
        reader.forEachLine { line ->
            if (line.startsWith("FINAL_FILE:")) finalPath = line.substringAfter("FINAL_FILE:").trim().trim('"')
            val pct = percentRegex.find(line)?.groupValues?.getOrNull(1)?.toDoubleOrNull()?.toInt()
            if (pct != null) update(pct.coerceIn(0, 99), line.takeLast(220))
            else if (line.contains("[download]") || line.contains("[Merger]") || line.contains("[ffmpeg]", true)) update((pct ?: 0).coerceIn(0, 99), line.takeLast(220))
        }
        val code = process.waitFor()
        runtime.process.compareAndSet(process, null)
        if (code != 0) error("O yt-dlp encerrou com código $code. Verifique rede, proxy, autenticação, disponibilidade ou formato do vídeo.")

        val direct = finalPath.takeIf { it.isNotBlank() }?.let(::File)?.takeIf { it.exists() && it.length() > 1024 }
        if (direct != null) return@runCatching direct

        runtime.videosDir.listFiles()
            ?.filter { it.isFile && it.length() > 1024 && !before.containsKey(it.absolutePath.lowercase()) }
            ?.maxByOrNull { it.lastModified() }
            ?: error("O processo terminou, mas nenhum arquivo de vídeo válido foi criado.")
    }
}
