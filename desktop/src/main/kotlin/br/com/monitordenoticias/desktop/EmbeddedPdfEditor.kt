package br.com.monitordenoticias.desktop

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.SwingPanel
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinUser
import java.awt.BorderLayout
import java.awt.Canvas
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.io.File
import javax.swing.JPanel

private const val PDF_EDITOR_VERSION = "0.3.1"

@Composable
fun EmbeddedPdfEditorScreen() {
    var status by remember { mutableStateOf("Preparando Editor de PDF v$PDF_EDITOR_VERSION...") }
    var error by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().background(Color(0xFFF3F8FE))) {
        Surface(
            color = Color.White,
            tonalElevation = 1.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Editor de PDF v$PDF_EDITOR_VERSION", style = MaterialTheme.typography.titleMedium)
                Text(status, style = MaterialTheme.typography.bodySmall, color = Color(0xFF58739F))
            }
        }

        error?.let {
            Surface(color = Color(0xFFFFECEF), modifier = Modifier.fillMaxWidth()) {
                Text(it, color = Color(0xFFB42335), modifier = Modifier.padding(12.dp))
            }
        }

        SwingPanel(
            modifier = Modifier.fillMaxSize(),
            factory = {
                val host = PdfEditorNativeHost(
                    onStatus = { status = it },
                    onError = { error = it }
                )
                host.start()
                host
            }
        )
    }
}

private class PdfEditorNativeHost(
    private val onStatus: (String) -> Unit,
    private val onError: (String) -> Unit
) : JPanel(BorderLayout()) {
    private val canvas = Canvas()
    private var process: Process? = null
    private var childWindow: HWND? = null

    init {
        background = java.awt.Color(0xF3, 0xF8, 0xFE)
        add(canvas, BorderLayout.CENTER)
        canvas.addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent?) = resizeChild()
            override fun componentShown(e: ComponentEvent?) = resizeChild()
        })
    }

    fun start() {
        Thread({
            try {
                val exe = resolveEditorExe()
                    ?: throw IllegalStateException("Editor-de-PDF.exe v$PDF_EDITOR_VERSION não encontrado no pacote do Monitor.")
                onStatus("Iniciando editor original...")
                val p = ProcessBuilder(exe.absolutePath)
                    .directory(exe.parentFile)
                    .start()
                process = p

                val hwnd = waitForTopLevelWindow(p.pid(), 20_000)
                    ?: throw IllegalStateException("A janela original do Editor de PDF não respondeu a tempo.")
                childWindow = hwnd

                java.awt.EventQueue.invokeAndWait {
                    embedWindow(hwnd)
                    onStatus("Editor original v$PDF_EDITOR_VERSION integrado")
                }
            } catch (t: Throwable) {
                onError(t.message ?: "Falha ao iniciar o Editor de PDF.")
                onStatus("Editor indisponível")
            }
        }, "pdf-editor-embed").apply { isDaemon = true }.start()
    }

    private fun resolveEditorExe(): File? {
        val candidates = buildList {
            val cwd = File(System.getProperty("user.dir", "."))
            add(File(cwd, "tools/EditorPDF/Editor-de-PDF.exe"))
            add(File(cwd, "EditorPDF/Editor-de-PDF.exe"))
            val code = runCatching {
                File(PdfEditorNativeHost::class.java.protectionDomain.codeSource.location.toURI()).parentFile
            }.getOrNull()
            if (code != null) {
                add(File(code, "tools/EditorPDF/Editor-de-PDF.exe"))
                add(File(code.parentFile ?: code, "tools/EditorPDF/Editor-de-PDF.exe"))
            }
        }
        return candidates.firstOrNull { it.isFile }
    }

    private fun waitForTopLevelWindow(pid: Long, timeoutMs: Long): HWND? {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            var found: HWND? = null
            User32.INSTANCE.EnumWindows({ hwnd, _ ->
                val outPid = com.sun.jna.ptr.IntByReference()
                User32.INSTANCE.GetWindowThreadProcessId(hwnd, outPid)
                if (outPid.value.toLong() == pid && User32.INSTANCE.IsWindowVisible(hwnd)) {
                    found = hwnd
                    false
                } else true
            }, null)
            if (found != null) return found
            Thread.sleep(200)
        }
        return null
    }

    private fun embedWindow(hwnd: HWND) {
        canvas.addNotify()
        val hostPointer: Pointer = Native.getComponentPointer(canvas)
        val hostHwnd = HWND(hostPointer)

        User32.INSTANCE.SetParent(hwnd, hostHwnd)
        val style = User32.INSTANCE.GetWindowLong(hwnd, WinUser.GWL_STYLE)
        val embeddedStyle = style and WinUser.WS_CAPTION.inv() and WinUser.WS_THICKFRAME.inv() and
            WinUser.WS_MINIMIZEBOX.inv() and WinUser.WS_MAXIMIZEBOX.inv() and WinUser.WS_SYSMENU.inv()
        User32.INSTANCE.SetWindowLong(hwnd, WinUser.GWL_STYLE, embeddedStyle or WinUser.WS_CHILD)
        User32.INSTANCE.ShowWindow(hwnd, WinUser.SW_SHOW)
        resizeChild()
    }

    private fun resizeChild() {
        val hwnd = childWindow ?: return
        val w = canvas.width.coerceAtLeast(1)
        val h = canvas.height.coerceAtLeast(1)
        User32.INSTANCE.MoveWindow(hwnd, 0, 0, w, h, true)
    }

    override fun removeNotify() {
        childWindow?.let { hwnd ->
            runCatching { User32.INSTANCE.PostMessage(hwnd, WinUser.WM_CLOSE, null, null) }
        }
        runCatching { process?.destroy() }
        childWindow = null
        process = null
        super.removeNotify()
    }
}
