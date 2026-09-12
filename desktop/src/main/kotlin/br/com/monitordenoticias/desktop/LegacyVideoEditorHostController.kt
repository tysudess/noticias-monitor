package br.com.monitordenoticias.desktop

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.win32.StdCallLibrary
import org.json.JSONObject
import java.awt.BorderLayout
import java.awt.Color
import java.awt.EventQueue
import java.awt.Panel
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import javax.swing.Timer
import kotlin.concurrent.thread

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
    private val showSent = AtomicBoolean(false)

    private var process: Process? = null
    private var writer: BufferedWriter? = null
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

    private fun startHost() {
        if (process?.isAlive == true) return
        runCatching {
            val exe = findHostExe()
            val p = ProcessBuilder(exe.absolutePath)
                .directory(exe.parentFile)
                .redirectErrorStream(true)
                .start()
            process = p
            writer = BufferedWriter(OutputStreamWriter(p.outputStream, Charsets.UTF_8))
            thread(name = "legacy-video-editor-host-reader", isDaemon = true) {
                BufferedReader(InputStreamReader(p.inputStream, Charsets.UTF_8)).useLines { lines ->
                    lines.forEach { line -> handleHostLine(line) }
                }
            }
        }.onFailure {
            lastError.set(it.message ?: "Falha ao iniciar o Editor de Vídeo original.")
        }
    }

    private fun handleHostLine(line: String) {
        runCatching {
            val obj = JSONObject(line)
            when (obj.optString("type")) {
                "hwnd" -> {
                    hostHwnd.set(obj.optLong("value", 0L))
                    scheduleAttach()
                }
                "error" -> lastError.set(obj.optString("message", "Falha no Editor de Vídeo original."))
            }
        }.onFailure {
            lastError.set("Falha ao interpretar resposta do editor: ${it.message}")
        }
    }

    @Synchronized
    private fun send(action: String, block: (JSONObject.() -> Unit)? = null) {
        startHost()
        val out = writer ?: return
        runCatching {
            val obj = JSONObject().put("action", action)
            block?.invoke(obj)
            out.write(obj.toString())
            out.newLine()
            out.flush()
        }.onFailure {
            lastError.set(it.message ?: "Falha ao comunicar com o Editor de Vídeo original.")
        }
    }

    private fun scheduleAttach() {
        EventQueue.invokeLater {
            if (attachWindow()) return@invokeLater
            attachTimer?.stop()
            var attempts = 0
            attachTimer = Timer(120) {
                attempts++
                if (attachWindow() || attempts >= 80) {
                    attachTimer?.stop()
                    if (!attached.get() && attempts >= 80 && lastError.get() == null) {
                        lastError.set("Não foi possível embutir o Editor de Vídeo original na aba.")
                    }
                }
            }.also { it.start() }
        }
    }

    private fun attachWindow(): Boolean {
        if (!panel.isDisplayable) return false
        val childValue = hostHwnd.get()
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
            u.ShowWindow(child, User32Ext.SW_SHOW)
            attached.set(true)

            send("resize") {
                put("width", w)
                put("height", h)
            }
            if (showSent.compareAndSet(false, true)) send("show")
            true
        }.getOrElse {
            lastError.set("Falha ao embutir Editor de Vídeo Qt: ${it.message}")
            false
        }
    }

    private fun resizeChild() {
        if (!attached.get() || !panel.isDisplayable) return
        val childValue = hostHwnd.get()
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
                send("resize") {
                    put("width", w)
                    put("height", h)
                }
            }.onFailure {
                lastError.set("Falha ao redimensionar Editor de Vídeo Qt: ${it.message}")
            }
        }
    }

    fun consumeError(): String? = lastError.getAndSet(null)

    fun stopAndDispose() {
        attachTimer?.stop()
        attachTimer = null
        send("quit")
        runCatching { writer?.close() }
        runCatching {
            process?.waitFor(1800, java.util.concurrent.TimeUnit.MILLISECONDS)
            if (process?.isAlive == true) process?.destroyForcibly()
        }
        writer = null
        process = null
        hostHwnd.set(0L)
        attached.set(false)
        showSent.set(false)
    }
}
