package br.com.monitordenoticias.desktop

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.platform.win32.WinDef.HWND
import org.json.JSONObject
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Panel
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.thread

internal class QtEmbeddedVideoPreviewController {
    private interface User32Ext : StdCallLibrary {
        fun SetParent(child: HWND?, newParent: HWND?): HWND?
        fun GetWindowLongW(hwnd: HWND?, index: Int): Int
        fun SetWindowLongW(hwnd: HWND?, index: Int, value: Int): Int
        fun MoveWindow(hwnd: HWND?, x: Int, y: Int, width: Int, height: Int, repaint: Boolean): Boolean
        fun ShowWindow(hwnd: HWND?, cmd: Int): Boolean

        companion object {
            val INSTANCE: User32Ext = Native.load("user32", User32Ext::class.java)
            const val GWL_STYLE = -16
            const val WS_CHILD = 0x40000000
            const val WS_VISIBLE = 0x10000000
            const val WS_POPUP = -0x80000000
            const val SW_SHOW = 5
        }
    }

    val panel = object : Panel(BorderLayout()) {
        override fun addNotify() {
            super.addNotify()
            background = Color(2, 10, 16)
            attachWindow()
        }
    }.apply {
        background = Color(2, 10, 16)
        isFocusable = false
        addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent?) = resizeChild()
            override fun componentShown(e: ComponentEvent?) = attachWindow()
        })
    }

    private val currentMs = AtomicLong(0L)
    private val durationMs = AtomicLong(0L)
    private val lastError = AtomicReference<String?>(null)
    private val hostHwnd = AtomicLong(0L)

    @Volatile var currentPath: String = ""
        private set
    @Volatile var playing: Boolean = false
        private set

    private var process: Process? = null
    private var writer: BufferedWriter? = null

    init {
        startHost()
    }

    private fun findHostExe(): File {
        val base = File(System.getProperty("user.dir"))
        val parent = base.parentFile ?: base
        val candidates = listOf(
            File(base, "bin/video-preview-host/video-preview-host.exe"),
            File(base, "bin/video-preview-host.exe"),
            File(base, "video-preview-host.exe"),
            File(parent, "bin/video-preview-host/video-preview-host.exe"),
            File(parent, "bin/video-preview-host.exe")
        )
        return candidates.firstOrNull { it.isFile }
            ?: throw IllegalStateException("video-preview-host.exe não encontrado no Portable.")
    }

    private fun startHost() {
        if (process?.isAlive == true) return
        runCatching {
            val exe = findHostExe()
            val p = ProcessBuilder(exe.absolutePath)
                .redirectErrorStream(true)
                .start()
            process = p
            writer = BufferedWriter(OutputStreamWriter(p.outputStream, Charsets.UTF_8))
            thread(name = "video-preview-host-reader", isDaemon = true) {
                BufferedReader(InputStreamReader(p.inputStream, Charsets.UTF_8)).useLines { lines ->
                    lines.forEach { line -> handleHostLine(line) }
                }
            }
        }.onFailure {
            lastError.set(it.message ?: "Falha ao iniciar o preview PySide6.")
        }
    }

    private fun handleHostLine(line: String) {
        runCatching {
            val obj = JSONObject(line)
            when (obj.optString("type")) {
                "hwnd" -> {
                    hostHwnd.set(obj.optLong("value", 0L))
                    attachWindow()
                }
                "tick" -> {
                    currentMs.set(obj.optLong("position", 0L).coerceAtLeast(0L))
                    durationMs.set(obj.optLong("duration", 0L).coerceAtLeast(0L))
                    playing = obj.optBoolean("playing", false)
                }
                "state" -> playing = obj.optBoolean("playing", false)
                "error" -> lastError.set(obj.optString("message", "Falha no preview PySide6."))
            }
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
        }.onFailure { lastError.set(it.message ?: "Falha ao comunicar com o preview.") }
    }

    private fun attachWindow() {
        if (!panel.isDisplayable) return
        val childValue = hostHwnd.get()
        if (childValue == 0L) return
        runCatching {
            val parentPtr = Native.getComponentPointer(panel)
            val parent = HWND(parentPtr)
            val child = HWND(Pointer(childValue))
            val u = User32Ext.INSTANCE
            u.SetParent(child, parent)
            val style = u.GetWindowLongW(child, User32Ext.GWL_STYLE)
            val newStyle = (style or User32Ext.WS_CHILD or User32Ext.WS_VISIBLE) and User32Ext.WS_POPUP.inv()
            u.SetWindowLongW(child, User32Ext.GWL_STYLE, newStyle)
            u.ShowWindow(child, User32Ext.SW_SHOW)
            resizeChild()
        }.onFailure { lastError.set("Falha ao embutir preview Qt: ${it.message}") }
    }

    private fun resizeChild() {
        val childValue = hostHwnd.get()
        if (childValue == 0L || !panel.isDisplayable) return
        runCatching {
            val child = HWND(Pointer(childValue))
            User32Ext.INSTANCE.MoveWindow(child, 0, 0, panel.width.coerceAtLeast(1), panel.height.coerceAtLeast(1), true)
        }
    }

    fun load(path: String, sourcePositionMs: Long, autoPlay: Boolean = false) {
        val file = File(path)
        if (!file.exists()) {
            lastError.set("Arquivo não encontrado: ${file.absolutePath}")
            return
        }
        currentPath = file.absolutePath
        currentMs.set(sourcePositionMs.coerceAtLeast(0L))
        send("load") {
            put("path", file.absolutePath)
            put("position", sourcePositionMs.coerceAtLeast(0L))
            put("autoplay", autoPlay)
        }
    }

    fun seek(sourcePositionMs: Long) = send("seek") { put("position", sourcePositionMs.coerceAtLeast(0L)) }
    fun play() = send("play")
    fun pause() = send("pause")
    fun setVolume(value: Double) = send("volume") { put("value", value.coerceIn(0.0, 1.0)) }
    fun currentPositionMs(): Long = currentMs.get()
    fun mediaDurationMs(): Long = durationMs.get()
    fun consumeError(): String? = lastError.getAndSet(null)

    fun stopAndDispose() {
        send("quit")
        runCatching { writer?.close() }
        runCatching { process?.destroy() }
        writer = null
        process = null
        hostHwnd.set(0L)
        currentPath = ""
        currentMs.set(0L)
        durationMs.set(0L)
        playing = false
    }
}
