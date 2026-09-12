package br.com.monitordenoticias.desktop

import java.awt.BorderLayout
import java.awt.Desktop
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Window
import java.io.File
import java.net.URI
import javax.swing.JButton
import javax.swing.JDialog
import javax.swing.JFileChooser
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JScrollPane
import javax.swing.JTextArea
import javax.swing.SwingUtilities
import javax.swing.filechooser.FileNameExtensionFilter

internal class GloboplayLoginWindow(
    private val owner: Window?,
    private val engine: ExtractorVideoEngine,
    @Suppress("UNUSED_PARAMETER") private val proxyUrl: String,
    private val onSessionChanged: (Boolean, String) -> Unit
) {
    private val store = GloboplaySessionStore(engine.appDir.toFile())
    private var dialog: JDialog? = null
    private var pendingCookies: String? = null

    fun open() {
        SwingUtilities.invokeLater { buildAndShow() }
    }

    private fun buildAndShow() {
        if (dialog?.isDisplayable == true) {
            dialog?.toFront()
            return
        }

        val status = JLabel("Use o navegador normal do Windows para fazer login no Globoplay.")
        val openButton = JButton("ABRIR GLOBOPLAY")
        val importButton = JButton("IMPORTAR COOKIES.TXT")
        val saveButton = JButton("SALVAR SESSÃO E VOLTAR")
        val closeButton = JButton("FECHAR")

        val instructions = JTextArea(
            """
            Login seguro do Globoplay

            1. Clique em ABRIR GLOBOPLAY.
            2. Faça login normalmente no site oficial usando seu navegador.
            3. Exporte os cookies do Globoplay em formato Netscape (cookies.txt).
            4. Clique em IMPORTAR COOKIES.TXT e selecione esse arquivo.
            5. Clique em SALVAR SESSÃO E VOLTAR.

            O Monitor filtra apenas cookies dos domínios Globo/Globoplay e salva a sessão usando a proteção DPAPI do Windows.
            Sua senha não é lida nem armazenada pelo Monitor.
            """.trimIndent()
        ).apply {
            isEditable = false
            lineWrap = true
            wrapStyleWord = true
            border = null
        }

        val d = JDialog(owner, "Login Globoplay — Extrator de Vídeos", java.awt.Dialog.ModalityType.MODELESS).apply {
            layout = BorderLayout(10, 10)
            minimumSize = Dimension(720, 420)
            preferredSize = Dimension(820, 480)
            add(status, BorderLayout.NORTH)
            add(JScrollPane(instructions), BorderLayout.CENTER)
            add(JPanel(FlowLayout(FlowLayout.RIGHT)).apply {
                add(openButton)
                add(importButton)
                add(saveButton)
                add(closeButton)
            }, BorderLayout.SOUTH)
            defaultCloseOperation = JDialog.DISPOSE_ON_CLOSE
        }
        dialog = d

        openButton.addActionListener {
            val result = runCatching {
                check(Desktop.isDesktopSupported()) { "O navegador padrão do Windows não está disponível." }
                val desktop = Desktop.getDesktop()
                check(desktop.isSupported(Desktop.Action.BROWSE)) { "Ação de abrir navegador não está disponível." }
                desktop.browse(URI("https://globoplay.globo.com/"))
            }
            status.text = result.fold(
                onSuccess = { "Globoplay aberto no navegador. Faça login e depois importe o cookies.txt." },
                onFailure = { "Não foi possível abrir o navegador: ${it.message.orEmpty()}" }
            )
        }

        importButton.addActionListener {
            val chooser = JFileChooser(File(System.getProperty("user.home", "."))).apply {
                dialogTitle = "Selecionar cookies.txt do Globoplay"
                fileFilter = FileNameExtensionFilter("Arquivo de cookies (*.txt)", "txt")
                isAcceptAllFileFilterUsed = true
            }
            if (chooser.showOpenDialog(d) != JFileChooser.APPROVE_OPTION) return@addActionListener

            val selected = chooser.selectedFile
            val result = runCatching {
                require(selected.isFile) { "Arquivo não encontrado." }
                require(selected.length() in 1..5_000_000) { "Arquivo de cookies vazio ou grande demais." }
                sanitizeNetscapeCookies(selected.readText(Charsets.UTF_8))
            }
            result.fold(
                onSuccess = { sanitized ->
                    pendingCookies = sanitized
                    val count = sanitized.lineSequence().count { it.isNotBlank() && !it.startsWith("# Netscape") && !it.startsWith("# Generated") }
                    status.text = "$count cookies Globo/Globoplay importados. Agora clique em SALVAR SESSÃO E VOLTAR."
                },
                onFailure = {
                    pendingCookies = null
                    status.text = "Falha ao importar cookies: ${it.message.orEmpty()}"
                }
            )
        }

        saveButton.addActionListener {
            val netscape = pendingCookies
            if (netscape.isNullOrBlank()) {
                status.text = "Importe primeiro um cookies.txt válido do Globoplay."
                return@addActionListener
            }
            val result = store.saveNetscapeCookies(netscape)
            result.fold(
                onSuccess = {
                    status.text = "Sessão salva com proteção do Windows."
                    onSessionChanged(true, "Sessão Globoplay salva com segurança.")
                    close()
                },
                onFailure = {
                    status.text = "Falha ao salvar sessão: ${it.message.orEmpty()}"
                    onSessionChanged(store.hasSavedSession(), status.text)
                }
            )
        }

        closeButton.addActionListener { close() }
        d.addWindowListener(object : java.awt.event.WindowAdapter() {
            override fun windowClosed(e: java.awt.event.WindowEvent?) {
                dialog = null
                pendingCookies = null
            }
        })
        d.pack()
        d.setLocationRelativeTo(owner)
        d.isVisible = true
    }

    private fun sanitizeNetscapeCookies(raw: String): String {
        val accepted = raw.lineSequence()
            .map { it.trimEnd() }
            .filter { it.isNotBlank() }
            .filterNot { it.startsWith("#") && !it.startsWith("#HttpOnly_") }
            .mapNotNull { line ->
                val fields = line.split('\t')
                if (fields.size < 7) return@mapNotNull null
                val domainToken = fields[0]
                val host = domainToken.removePrefix("#HttpOnly_").trim().trimStart('.').lowercase()
                if (!isAllowedGloboHost(host)) return@mapNotNull null
                line
            }
            .distinct()
            .toList()

        require(accepted.isNotEmpty()) {
            "O arquivo não contém cookies dos domínios globo.com/globoplay.com em formato Netscape."
        }
        return buildString {
            appendLine("# Netscape HTTP Cookie File")
            appendLine("# Generated by Monitor de Notícias — Extrator de Vídeos")
            accepted.forEach { appendLine(it) }
        }
    }

    private fun isAllowedGloboHost(value: String): Boolean {
        val host = value.lowercase().trim().trimStart('.')
        return host == "globo.com" || host.endsWith(".globo.com") ||
            host == "globoplay.com" || host.endsWith(".globoplay.com")
    }

    private fun close() {
        SwingUtilities.invokeLater {
            dialog?.dispose()
            dialog = null
            pendingCookies = null
        }
    }
}
