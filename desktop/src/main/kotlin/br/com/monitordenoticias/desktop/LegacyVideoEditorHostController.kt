package br.com.monitordenoticias.desktop

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.win32.StdCallLibrary
import java.awt.BorderLayout
import java.awt.Color
import java.awt.EventQueue
import java.awt.Panel
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import javax.swing.Timer

/**
 * Ponte mínima entre a aba Compose/Swing do Monitor e o editor PySide6 original.
 *
 * Regra de projeto: o motor do editor NÃO é reimplementado aqui. Este controlador
 * apenas inicia o host original, obtém o HWND por arquivo temporário e o torna
 * filho nativo do painel da aba. Não depende de stdin/stdout, pois o host é
 * empacotado pelo PyInstaller em modo --windowed.
 */
internal class LegacyVideoEditorHostController {
    private interface User32Ext : StdCallLibrary {
        fun SetParent(child: HWND?, newParent: HWND?): HWND?
        fun GetWindowLongW(hwnd: HWND?, index: Int): Int
        fun SetWindowLongW(hwnd: HWND?, index: Int, value: Int): Int
        fun SetWindowPos(hwnd: HWND?, insertAfter: HWND?, x: Int, y: Int, width: Int, height: Int, flags: Int): Boolean
        fun ShowWindow(hwnd: HWND?, cmd: Int): Boolean

        companion object {
            val INSTANCE: User32Ext = Native.load("user32", User32Ext::class.java)
            const val GWL_STYLE = -16
            const val WS_CHILD = 0x40000000
            const val WS_VISIBLE = 0x10000000
            const val WS_POPUP = -0x80000000
            const val WS_CAPTION = 0x00C00000
            const val WS_THICKFRAME = 0x00040000
            const val WS_MINIMIZEBOX = 0x00020000
            const val WS_MAXIMIZEBOX = 0x00010000
            const val WS_SYSMENU = 0x00080000
            const val SW_SHOW = 5
            const val SWP_NOZORDER = 0x0004
            const val SWP_NOACTIVATE = 0x0010
            const val SWP_FRAMECHANGED = 0x0020
        }
    }

    val panel = object : Panel(BorderLayout()) {
        override fun addNotify() {
            super.addNotify()
            background = Color(5, 7, 15)
            scheduleAttach()
        }
    }.apply {
        background = Color(5, 7, 15)
        isFocusable = false
        addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent?) = resizeChild()
            override fun componentShown(e: ComponentEvent?) = scheduleAttach()
        })
    }

    private val hostHwnd = AtomicLong(0L)
    private val lastError = AtomicReference<String?>(null)
    private val attached = AtomicBoolean(false)

    private val bridgeDir: File = Files.createTempDirectory("monitor-video-editor-bridge-").toFile()
    private val hwndFile = File(bridgeDir, "hwnd.txt")
    private val showFile = File(bridgeDir, "show.flag")
    private val quitFile = File(bridgeDir, "quit.flag")
    private val errorFile = File(bridgeDir, "error.txt")

    private var process: Process? = null
    private var attachTimer: Timer? = null

    init {
        startHost()
    }

    private fun findHostExe(): File {
        val base = File(System.getProperty("user.dir"))
        val parent = base.parentFile ?: base
        val candidates = listOf(
            File(base, "bin/video-editor-host/video-editor-host.exe"),
            File(base, "bin/video-editor-host.exe"),
            File(parent, "bin/video-editor-host/video-editor-host.exe"),
            File(parent, "bin/video-editor-host.exe")
        )
        return candidates.firstOrNull { it.isFile }
            ?: throw IllegalStateException("video-editor-host.exe não encontrado no Portable.")
    }

    @Synchronized
    private fun startHost() {
        if (process?.isAlive == true) return
        runCatching {
            bridgeDir.mkdirs()
            hwndFile.delete()
            showFile.delete()
            quitFile.delete()
            errorFile.delete()

            val exe = findHostExe()
            val p = ProcessBuilder(
                exe.absolutePath,
                "--bridge-dir",
                bridgeDir.absolutePath
            )
                .directory(exe.parentFile)
                .start()
            process = p
        }.onFailure {
            lastError.set(it.message ?: "Falha ao iniciar o Editor de Vídeo original.")
        }
    }

    private fun refreshHostState(): Long {
        errorFile.takeIf { it.isFile }?.let { file ->
            runCatching { file.readText(Charsets.UTF_8).trim() }
                .getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?.let { lastError.compareAndSet(null, it) }
        }

        val cached = hostHwnd.get()
        if (cached != 0L) return cached

        val value = runCatching {
            if (!hwndFile.isFile) return@runCatching 0L
            hwndFile.readText(Charsets.UTF_8).trim().toLongOrNull() ?: 0L
        }.getOrDefault(0L)
        if (value != 0L) hostHwnd.compareAndSet(0L, value)
        return hostHwnd.get()
    }

    private fun scheduleAttach() {
        EventQueue.invokeLater {
            if (attachWindow()) return@invokeLater
            attachTimer?.stop()
            var attempts = 0
            attachTimer = Timer(120) {
                attempts++

                val p = process
                if (p != null && !p.isAlive) {
                    attachTimer?.stop()
                    val detail = errorFile.takeIf { it.isFile }
                        ?.let { runCatching { it.readText(Charsets.UTF_8).trim() }.getOrNull() }
                        ?.takeIf { it.isNotBlank() }
                    lastError.set(detail ?: "O Editor de Vídeo original encerrou antes de ser embutido.")
                    return@Timer
                }

                if (attachWindow() || attempts >= 100) {
                    attachTimer?.stop()
                    if (!attached.get() && attempts >= 100 && lastError.get() == null) {
                        lastError.set("Não foi possível embutir o Editor de Vídeo original na aba.")
                    }
                }
            }.also { it.start() }
        }
    }

    private fun attachWindow(): Boolean {
        if (!panel.isDisplayable) return false
        val childValue = refreshHostState()
        if (childValue == 0L) return false

        return runCatching {
            val parentPtr = Native.getComponentPointer(panel)
            if (parentPtr == null || Pointer.nativeValue(parentPtr) == 0L) return@runCatching false

            val parent = HWND(parentPtr)
            val child = HWND(Pointer(childValue))
            val u = User32Ext.INSTANCE

            u.SetParent(child, parent)
            val style = u.GetWindowLongW(child, User32Ext.GWL_STYLE)
            val removeMask = User32Ext.WS_POPUP or User32Ext.WS_CAPTION or User32Ext.WS_THICKFRAME or
                User32Ext.WS_MINIMIZEBOX or User32Ext.WS_MAXIMIZEBOX or User32Ext.WS_SYSMENU
            val newStyle = (style and removeMask.inv()) or User32Ext.WS_CHILD or User32Ext.WS_VISIBLE
            u.SetWindowLongW(child, User32Ext.GWL_STYLE, newStyle)

            val w = panel.width.coerceAtLeast(1)
            val h = panel.height.coerceAtLeast(1)
            u.SetWindowPos(
                child,
                null,
                0,
                0,
                w,
                h,
                User32Ext.SWP_NOZORDER or User32Ext.SWP_NOACTIVATE or User32Ext.SWP_FRAMECHANGED
            )

            // O host só chama QWidget.show() depois que este sinal existe.
            showFile.writeText("show", Charsets.UTF_8)
            u.ShowWindow(child, User32Ext.SW_SHOW)
            attached.set(true)
            true
        }.getOrElse {
            lastError.set("Falha ao embutir Editor de Vídeo Qt: ${it.message}")
            false
        }
    }

    private fun resizeChild() {
        if (!attached.get() || !panel.isDisplayable) return
        val childValue = refreshHostState()
        if (childValue == 0L) return
        EventQueue.invokeLater {
            runCatching {
                val child = HWND(Pointer(childValue))
                val w = panel.width.coerceAtLeast(1)
                val h = panel.height.coerceAtLeast(1)
                User32Ext.INSTANCE.SetWindowPos(
                    child,
                    null,
                    0,
                    0,
                    w,
                    h,
                    User32Ext.SWP_NOZORDER or User32Ext.SWP_NOACTIVATE
                )
            }.onFailure {
                lastError.set("Falha ao redimensionar Editor de Vídeo Qt: ${it.message}")
            }
        }
    }

    fun consumeError(): String? = lastError.getAndSet(null)

    fun stopAndDispose() {
        attachTimer?.stop()
        attachTimer = null

        runCatching {
            bridgeDir.mkdirs()
            quitFile.writeText("quit", Charsets.UTF_8)
        }

        runCatching {
            process?.waitFor(2500, TimeUnit.MILLISECONDS)
            if (process?.isAlive == true) process?.destroyForcibly()
        }

        process = null
        hostHwnd.set(0L)
        attached.set(false)
        runCatching { bridgeDir.deleteRecursively() }
    }
}
