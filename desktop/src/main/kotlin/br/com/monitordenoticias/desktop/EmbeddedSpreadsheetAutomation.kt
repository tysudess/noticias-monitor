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

private const val SPREADSHEET_AUTOMATION_VERSION = "1.1.1"
private const val SPREADSHEET_AUTOMATION_EXE = "AutomacaoPlanilhas-Windows-Portable-v1.1.1.exe"

@Composable
fun EmbeddedSpreadsheetAutomationScreen() {
    var status by remember { mutableStateOf("Preparando Automação de Planilhas v$SPREADSHEET_AUTOMATION_VERSION...") }
    var error by remember { mutableStateOf<String?>(null) }

    Column(Modifier.fillMaxSize().background(Color(0xFFF3F8FE))) {
        Surface(color = Color.White, tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Automação de Planilhas v$SPREADSHEET_AUTOMATION_VERSION", style = MaterialTheme.typography.titleMedium)
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
                val host = SpreadsheetAutomationNativeHost(
                    onStatus = { status = it },
                    onError = { error = it }
                )
                host.start()
                host
            }
        )
    }
}

private class SpreadsheetAutomationNativeHost(
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
                val exe = resolveAutomationExe()
                    ?: throw IllegalStateException("$SPREADSHEET_AUTOMATION_EXE não encontrado no pacote do Monitor.")
                onStatus("Iniciando automação original...")
                val p = ProcessBuilder(exe.absolutePath)
                    .directory(exe.parentFile)
                    .start()
                process = p

                val hwnd = waitForTopLevelWindow(p.pid(), 30_000)
                    ?: throw IllegalStateException("A janela original da Automação de Planilhas não respondeu a tempo.")
                childWindow = hwnd

                java.awt.EventQueue.invokeAndWait {
                    embedWindow(hwnd)
                    onStatus("Automação original v$SPREADSHEET_AUTOMATION_VERSION integrada")
                }
            } catch (t: Throwable) {
                onError(t.message ?: "Falha ao iniciar a Automação de Planilhas.")
                onStatus("Automação indisponível")
            }
        }, "spreadsheet-automation-embed").apply { isDaemon = true }.start()
    }

    private fun resolveAutomationExe(): File? {
        val candidates = buildList {
            val cwd = File(System.getProperty("user.dir", "."))
            add(File(cwd, "tools/AutomacaoPlanilhas/$SPREADSHEET_AUTOMATION_EXE"))
            add(File(cwd, "AutomacaoPlanilhas/$SPREADSHEET_AUTOMATION_EXE"))
            val code = runCatching {
                File(SpreadsheetAutomationNativeHost::class.java.protectionDomain.codeSource.location.toURI()).parentFile
            }.getOrNull()
            if (code != null) {
                add(File(code, "tools/AutomacaoPlanilhas/$SPREADSHEET_AUTOMATION_EXE"))
                add(File(code.parentFile ?: code, "tools/AutomacaoPlanilhas/$SPREADSHEET_AUTOMATION_EXE"))
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
            Thread.sleep(250)
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
        User32.INSTANCE.MoveWindow(hwnd, 0, 0, canvas.width.coerceAtLeast(1), canvas.height.coerceAtLeast(1), true)
    }

    override fun removeNotify() {
        childWindow?.let { hwnd -> runCatching { User32.INSTANCE.PostMessage(hwnd, WinUser.WM_CLOSE, null, null) } }
        runCatching { process?.destroy() }
        childWindow = null
        process = null
        super.removeNotify()
    }
}
