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
 * Ponte mínima entre a aba do Monitor e o AdvancedVideoEditorWidget300 original.
 *
 * O motor antigo não é reimplementado aqui. Diferente da V18, o host Qt recebe
 * o HWND do painel ANTES de se tornar visível e faz o vínculo pelo próprio Qt.
 * Este controlador apenas inicia o processo, valida a paternidade nativa e
 * mantém o tamanho do único host dentro da aba.
 */
internal class LegacyVideoEditorHostController {
    private interface User32Ext : StdCallLibrary {
        fun GetParent(hwnd: HWND?): HWND?
        fun SetWindowPos(hwnd: HWND?, insertAfter: HWND?, x: Int, y: Int, width: Int, height: Int, flags: Int): Boolean
        fun ShowWindow(hwnd: HWND?, cmd: Int): Boolean

        companion object {
            val INSTANCE: User32Ext = Native.load("user32", User32Ext::class.java)
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
            EventQueue.invokeLater {
                startHostForPanel()
                scheduleAttach()
            }
        }
    }.apply {
        background = Color(5, 7, 15)
        isFocusable = false
        addComponentListener(object : ComponentAdapter() {
            override fun componentResized(e: ComponentEvent?) = resizeChild()
            override fun componentShown(e: ComponentEvent?) {
                startHostForPanel()
                scheduleAttach()
            }
        })
    }

    private val hostHwnd = AtomicLong(0L)
    private val lastError = AtomicReference<String?>(null)
    private val attached = AtomicBoolean(false)

    private val bridgeDir: File = Files.createTempDirectory("monitor-video-editor-bridge-").toFile()
    private val hwndFile = File(bridgeDir, "hwnd.txt")
    private val quitFile = File(bridgeDir, "quit.flag")
    private val errorFile = File(bridgeDir, "error.txt")

    private var process: Process? = null
    private var attachTimer: Timer? = null

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

    private fun panelNativeValue(): Long {
        if (!panel.isDisplayable) return 0L
        val ptr = runCatching { Native.getComponentPointer(panel) }.getOrNull() ?: return 0L
        return Pointer.nativeValue(ptr)
    }

    @Synchronized
    private fun startHostForPanel() {
        if (process?.isAlive == true) return
        val parentHwnd = panelNativeValue()
        if (parentHwnd == 0L) return

        runCatching {
            bridgeDir.mkdirs()
            hwndFile.delete()
            quitFile.delete()
            errorFile.delete()
            hostHwnd.set(0L)
            attached.set(false)

            val exe = findHostExe()
            process = ProcessBuilder(
                exe.absolutePath,
                "--bridge-dir", bridgeDir.absolutePath,
                "--parent-hwnd", parentHwnd.toString()
            )
                .directory(exe.parentFile)
                .start()
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
                if (process?.isAlive != true) startHostForPanel()

                val p = process
                if (p != null && !p.isAlive) {
                    val detail = errorFile.takeIf { it.isFile }
                        ?.let { runCatching { it.readText(Charsets.UTF_8).trim() }.getOrNull() }
                        ?.takeIf { it.isNotBlank() }
                    if (detail != null) lastError.compareAndSet(null, detail)
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
        val parentValue = panelNativeValue()
        if (parentValue == 0L) return false
        val childValue = refreshHostState()
        if (childValue == 0L) return false

        return runCatching {
            val child = HWND(Pointer(childValue))
            val parent = User32Ext.INSTANCE.GetParent(child)
            val actualParent = parent?.getPointer()?.let { Pointer.nativeValue(it) } ?: 0L
            if (actualParent != parentValue) {
                return@runCatching false
            }

            val w = panel.width.coerceAtLeast(1)
            val h = panel.height.coerceAtLeast(1)
            User32Ext.INSTANCE.SetWindowPos(
                child,
                null,
                0,
                0,
                w,
                h,
                User32Ext.SWP_NOZORDER or User32Ext.SWP_NOACTIVATE or User32Ext.SWP_FRAMECHANGED
            )
            User32Ext.INSTANCE.ShowWindow(child, User32Ext.SW_SHOW)
            attached.set(true)
            true
        }.getOrElse {
            lastError.set("Falha ao validar Editor de Vídeo Qt dentro da aba: ${it.message}")
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
            process?.waitFor(3000, TimeUnit.MILLISECONDS)
            if (process?.isAlive == true) process?.destroyForcibly()
        }

        process = null
        hostHwnd.set(0L)
        attached.set(false)
        runCatching { bridgeDir.deleteRecursively() }
    }
}
