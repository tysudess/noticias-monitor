package br.com.monitordenoticias.desktop

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Launch
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinUser
import com.sun.jna.ptr.IntByReference
import kotlinx.coroutines.delay
import java.awt.BorderLayout
import java.awt.Canvas
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.io.File
import java.util.concurrent.Executors
import javax.swing.JPanel
import javax.swing.SwingUtilities

private const val GWL_STYLE = -16
private const val WS_CHILD = 0x40000000
private const val WS_POPUP = -0x80000000
private const val WS_CAPTION = 0x00C00000
private const val WS_THICKFRAME = 0x00040000
private const val SW_HIDE = 0
private const val SW_SHOW = 5

/**
 * Hospeda os programas Windows originais dentro do painel do Monitor.
 *
 * Os extratores continuam sendo executáveis independentes e preservam seu
 * próprio runtime, armazenamento e lógica. O Monitor apenas inicia, incorpora
 * a janela nativa e redimensiona o HWND para a área da aba.
 */
private class IntegratedToolSession(
    private val displayName: String,
    private val relativeExe: String,
    private val titleHints: List<String>,
    private val videoControl: Boolean = false
) {
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "integrated-${displayName.lowercase().replace(' ', '-')}").apply { isDaemon = true }
    }

    @Volatile var status: String = "Pronto para abrir"
        private set

    @Volatile private var process: Process? = null
    @Volatile private var hwnd: HWND? = null
    @Volatile private var attachedCanvas: Canvas? = null
    @Volatile private var originalStyle: Int? = null
    @Volatile private var requestedPage: String = ""

    private val controlFile: File by lazy {
        File(System.getProperty("java.io.tmpdir"), "monitor-de-noticias/integracoes/video-page.txt").also {
            it.parentFile?.mkdirs()
        }
    }

    fun attach(canvas: Canvas, page: String = "") {
        if (page.isNotBlank()) setPage(page)
        executor.execute {
            try {
                val exe = resolveExecutable(relativeExe)
                if (exe == null) {
                    status = "$displayName não foi encontrado no pacote."
                    return@execute
                }

                var running = process
                if (running == null || !running.isAlive) {
                    status = "Abrindo $displayName..."
                    val builder = ProcessBuilder(exe.absolutePath)
                        .directory(exe.parentFile)
                        .redirectErrorStream(true)
                    if (videoControl) {
                        builder.environment()["MONITOR_VIDEO_CONTROL"] = controlFile.absolutePath
                        builder.environment()["MONITOR_VIDEO_PAGE"] = requestedPage.ifBlank { "download" }
                    }
                    running = builder.start()
                    process = running
                    hwnd = null
                    originalStyle = null
                }

                val window = hwnd ?: waitForWindow(running.pid(), 30_000L)
                if (window == null) {
                    status = "O programa abriu, mas a janela ainda não pôde ser incorporada."
                    return@execute
                }
                hwnd = window

                if (!canvas.isDisplayable) return@execute
                val parentPointer = runCatching { Native.getComponentPointer(canvas) }.getOrNull()
                if (parentPointer == null || parentPointer == Pointer.NULL) {
                    status = "Aguardando área de exibição..."
                    return@execute
                }

                val user32 = User32.INSTANCE
                if (originalStyle == null) originalStyle = user32.GetWindowLong(window, GWL_STYLE)
                val baseStyle = originalStyle ?: user32.GetWindowLong(window, GWL_STYLE)
                val childStyle = (baseStyle and WS_POPUP.inv() and WS_CAPTION.inv() and WS_THICKFRAME.inv()) or WS_CHILD
                user32.ShowWindow(window, SW_HIDE)
                user32.SetWindowLong(window, GWL_STYLE, childStyle)
                user32.SetParent(window, HWND(parentPointer))
                attachedCanvas = canvas
                resize(canvas)
                user32.ShowWindow(window, SW_SHOW)
                status = "$displayName integrado • funcionalidades originais preservadas"
            } catch (e: Exception) {
                status = "Falha ao abrir $displayName: ${e.message ?: e.javaClass.simpleName}"
            }
        }
    }

    fun detach(canvas: Canvas) {
        executor.execute {
            if (attachedCanvas !== canvas) return@execute
            val window = hwnd ?: return@execute
            runCatching {
                User32.INSTANCE.ShowWindow(window, SW_HIDE)
                User32.INSTANCE.SetParent(window, null)
                originalStyle?.let { User32.INSTANCE.SetWindowLong(window, GWL_STYLE, it) }
            }
            attachedCanvas = null
        }
    }

    fun resize(canvas: Canvas) {
        if (attachedCanvas !== canvas) return
        val window = hwnd ?: return
        if (!canvas.isDisplayable) return
        val w = canvas.width.coerceAtLeast(1)
        val h = canvas.height.coerceAtLeast(1)
        runCatching { User32.INSTANCE.MoveWindow(window, 0, 0, w, h, true) }
    }

    fun setPage(page: String) {
        if (!videoControl || page.isBlank()) return
        requestedPage = page.lowercase()
        runCatching {
            controlFile.parentFile?.mkdirs()
            controlFile.writeText(requestedPage, Charsets.UTF_8)
        }
    }

    fun restart(page: String = "") {
        if (page.isNotBlank()) setPage(page)
        executor.execute {
            runCatching { process?.destroyForcibly() }
            process = null
            hwnd = null
            originalStyle = null
            attachedCanvas = null
            status = "Reiniciando $displayName..."
        }
    }

    fun openDetached(page: String = "") {
        if (page.isNotBlank()) setPage(page)
        executor.execute {
            try {
                var running = process
                val exe = resolveExecutable(relativeExe)
                if (exe == null) {
                    status = "$displayName não foi encontrado no pacote."
                    return@execute
                }
                if (running == null || !running.isAlive) {
                    val builder = ProcessBuilder(exe.absolutePath)
                        .directory(exe.parentFile)
                        .redirectErrorStream(true)
                    if (videoControl) {
                        builder.environment()["MONITOR_VIDEO_CONTROL"] = controlFile.absolutePath
                        builder.environment()["MONITOR_VIDEO_PAGE"] = requestedPage.ifBlank { "download" }
                    }
                    running = builder.start()
                    process = running
                }
                val window = hwnd ?: waitForWindow(running.pid(), 30_000L)
                if (window != null) {
                    hwnd = window
                    User32.INSTANCE.ShowWindow(window, SW_HIDE)
                    User32.INSTANCE.SetParent(window, null)
                    originalStyle?.let { User32.INSTANCE.SetWindowLong(window, GWL_STYLE, it) }
                    attachedCanvas = null
                    User32.INSTANCE.ShowWindow(window, SW_SHOW)
                    User32.INSTANCE.SetForegroundWindow(window)
                    status = "$displayName aberto em janela separada"
                }
            } catch (e: Exception) {
                status = "Falha ao abrir $displayName: ${e.message ?: e.javaClass.simpleName}"
            }
        }
    }

    fun shutdown() {
        runCatching { process?.destroyForcibly() }
        process = null
        hwnd = null
        attachedCanvas = null
        executor.shutdownNow()
    }

    private fun waitForWindow(pid: Long, timeoutMs: Long): HWND? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            if (process?.isAlive == false) return null
            findTopLevelWindow(pid)?.let { return it }
            Thread.sleep(250)
        }
        return findTopLevelWindow(pid)
    }

    private fun findTopLevelWindow(pid: Long): HWND? {
        var fallback: HWND? = null
        var matched: HWND? = null
        val user32 = User32.INSTANCE
        user32.EnumWindows(WinUser.WNDENUMPROC { candidate, _ ->
            val processId = IntByReference()
            user32.GetWindowThreadProcessId(candidate, processId)
            if (processId.value.toLong() != pid || !user32.IsWindowVisible(candidate)) {
                true
            } else {
                val buffer = CharArray(1024)
                user32.GetWindowText(candidate, buffer, buffer.size)
                val title = Native.toString(buffer).trim()
                if (title.isNotBlank() && fallback == null) fallback = candidate
                val titleMatches = titleHints.any { title.contains(it, ignoreCase = true) }
                if (titleMatches) {
                    matched = candidate
                    false
                } else {
                    true
                }
            }
        }, null)
        return matched ?: fallback
    }
}

private class IntegratedToolSwingHost(
    private val session: IntegratedToolSession,
    private val page: String
) : JPanel(BorderLayout()) {
    private val nativeCanvas = Canvas()

    init {
        background = java.awt.Color.WHITE
        nativeCanvas.background = java.awt.Color.WHITE
        add(nativeCanvas, BorderLayout.CENTER)
        nativeCanvas.addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent?) {
                session.resize(nativeCanvas)
            }
        })
    }

    override fun addNotify() {
        super.addNotify()
        SwingUtilities.invokeLater { session.attach(nativeCanvas, page) }
    }

    override fun removeNotify() {
        session.detach(nativeCanvas)
        super.removeNotify()
    }
}

private val integratedNewsSession = IntegratedToolSession(
    displayName = "Extrator de Matérias",
    relativeExe = "tools/ExtratorNoticias/Extrator de Materias.exe",
    titleHints = listOf("Extrator de Matérias", "Extrator de Materias")
)

private val integratedVideoSession = IntegratedToolSession(
    displayName = "Extrator de Vídeos",
    relativeExe = "tools/ExtratorVideos/ExtratorVideos.exe",
    titleHints = listOf("Extrator de Vídeos", "Extrator de Videos", "ExtratorVideos"),
    videoControl = true
)

@Composable
fun V5IntegratedNewsExtractorScreen() {
    IntegratedToolScreen(
        title = "Extrator de Matérias",
        subtitle = "Aplicativo original v1.25.19 incorporado sem alterar suas funções.",
        session = integratedNewsSession,
        page = ""
    )
}

@Composable
fun V5IntegratedVideoDownloadScreen() {
    IntegratedToolScreen(
        title = "Extrator de Vídeos • Download",
        subtitle = "Download original com YouTube, Live, R7/Record, Globoplay, proxy e demais recursos preservados.",
        session = integratedVideoSession,
        page = "download"
    )
}

@Composable
fun V5IntegratedVideoEditorScreen() {
    IntegratedToolScreen(
        title = "Extrator de Vídeos • Editor",
        subtitle = "Editor e timeline originais com cortes, união, compressão e desfazer preservados.",
        session = integratedVideoSession,
        page = "editor"
    )
}

@Composable
private fun IntegratedToolScreen(
    title: String,
    subtitle: String,
    session: IntegratedToolSession,
    page: String
) {
    var status by remember(session) { mutableStateOf(session.status) }
    var hostGeneration by remember { mutableIntStateOf(0) }

    LaunchedEffect(session, page) {
        session.setPage(page)
        while (true) {
            status = session.status
            delay(300)
        }
    }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(
            color = Color.White,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(10.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD5E4F3)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                Modifier.padding(horizontal = 13.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.Verified, null, tint = Color(0xFF08A86F), modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(9.dp))
                Column(Modifier.weight(1f)) {
                    Text(title, color = Color(0xFF0A1F4B), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                    Text(subtitle, color = Color(0xFF58739F), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(status, color = Color(0xFF087AF7), fontSize = 10.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                OutlinedButton(
                    onClick = { session.restart(page); hostGeneration++ },
                    modifier = Modifier.height(36.dp),
                    contentPadding = PaddingValues(horizontal = 11.dp)
                ) {
                    Icon(Icons.Default.Refresh, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Reiniciar", fontSize = 10.sp)
                }
                Spacer(Modifier.width(7.dp))
                OutlinedButton(
                    onClick = { session.openDetached(page) },
                    modifier = Modifier.height(36.dp),
                    contentPadding = PaddingValues(horizontal = 11.dp)
                ) {
                    Icon(Icons.Default.Launch, null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Abrir em janela", fontSize = 10.sp)
                }
            }
        }

        key(hostGeneration, page) {
            SwingPanel(
                factory = { IntegratedToolSwingHost(session, page) },
                modifier = Modifier.fillMaxWidth().weight(1f),
                background = Color.White
            )
        }
    }
}

fun shutdownIntegratedTools() {
    integratedNewsSession.shutdown()
    integratedVideoSession.shutdown()
}

private fun resolveExecutable(relativePath: String): File? {
    val roots = linkedSetOf<File>()
    fun addWithParents(file: File?) {
        var current = file?.absoluteFile
        repeat(8) {
            if (current == null) return@repeat
            roots += current!!
            current = current!!.parentFile
        }
    }

    addWithParents(File(System.getProperty("user.dir", ".")))
    ProcessHandle.current().info().command().orElse(null)?.let { addWithParents(File(it).parentFile) }
    System.getProperty("java.class.path", "").split(File.pathSeparatorChar)
        .filter { it.isNotBlank() }
        .forEach { addWithParents(File(it).parentFile) }

    return roots.asSequence()
        .map { File(it, relativePath) }
        .firstOrNull { it.isFile }
}
