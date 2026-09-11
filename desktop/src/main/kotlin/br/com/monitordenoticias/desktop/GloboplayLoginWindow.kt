package br.com.monitordenoticias.desktop

import javafx.application.Platform
import javafx.embed.swing.JFXPanel
import javafx.scene.Scene
import javafx.scene.web.WebView
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Window
import java.net.Authenticator
import java.net.CookieHandler
import java.net.CookieManager
import java.net.CookiePolicy
import java.net.HttpCookie
import java.net.PasswordAuthentication
import java.net.URI
import javax.swing.JButton
import javax.swing.JDialog
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingUtilities

internal class GloboplayLoginWindow(
    private val owner: Window?,
    private val engine: ExtractorVideoEngine,
    private val proxyUrl: String,
    private val onSessionChanged: (Boolean, String) -> Unit
) {
    private val store = GloboplaySessionStore(engine.appDir.toFile())
    private val cookieManager = CookieManager(null, CookiePolicy.ACCEPT_ALL)
    private var previousCookieHandler: CookieHandler? = null
    private var previousAuthenticator: Authenticator? = null
    private val previousProxyProperties = linkedMapOf<String, String?>()
    private var dialog: JDialog? = null

    fun open() {
        if (!Platform.isFxApplicationThread()) {
            runCatching { Platform.setImplicitExit(false) }
        }
        SwingUtilities.invokeLater { buildAndShow() }
    }

    private fun buildAndShow() {
        if (dialog?.isDisplayable == true) {
            dialog?.toFront()
            return
        }
        applyNetworkContext()

        val fxPanel = JFXPanel()
        val status = JLabel("Faça login apenas pela página oficial do Globoplay. O Monitor não armazena sua senha.")
        val openButton = JButton("ABRIR GLOBOPLAY")
        val saveButton = JButton("SALVAR SESSÃO E VOLTAR")
        val closeButton = JButton("FECHAR")

        val d = JDialog(owner, "Login Globoplay — Extrator de Vídeos", DialogModality.MODELESS.awtValue).apply {
            layout = BorderLayout(8, 8)
            minimumSize = Dimension(980, 720)
            preferredSize = Dimension(1120, 820)
            add(status, BorderLayout.NORTH)
            add(fxPanel, BorderLayout.CENTER)
            add(JPanel(FlowLayout(FlowLayout.RIGHT)).apply {
                add(openButton)
                add(saveButton)
                add(closeButton)
            }, BorderLayout.SOUTH)
            defaultCloseOperation = JDialog.DISPOSE_ON_CLOSE
        }
        dialog = d

        fun loadHome() {
            Platform.runLater {
                val webView = fxPanel.scene?.root as? WebView ?: return@runLater
                webView.engine.load("https://globoplay.globo.com/")
            }
        }

        Platform.runLater {
            val webView = WebView()
            webView.engine.isJavaScriptEnabled = true
            fxPanel.scene = Scene(webView)
            webView.engine.load("https://globoplay.globo.com/")
        }

        openButton.addActionListener { loadHome() }
        saveButton.addActionListener {
            val netscape = buildNetscapeCookieJar()
            if (netscape.lineSequence().count { it.isNotBlank() && !it.startsWith("#") } == 0) {
                status.text = "Nenhum cookie do Globoplay foi capturado. Conclua o login e tente salvar novamente."
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
            override fun windowClosed(e: java.awt.event.WindowEvent?) = restoreNetworkContext()
            override fun windowClosing(e: java.awt.event.WindowEvent?) = restoreNetworkContext()
        })
        d.pack()
        d.setLocationRelativeTo(owner)
        d.isVisible = true
    }

    private fun close() {
        SwingUtilities.invokeLater {
            dialog?.dispose()
            dialog = null
            restoreNetworkContext()
        }
    }

    private fun applyNetworkContext() {
        previousCookieHandler = CookieHandler.getDefault()
        CookieHandler.setDefault(cookieManager)
        previousAuthenticator = Authenticator.getDefault()

        val keys = listOf(
            "http.proxyHost", "http.proxyPort", "https.proxyHost", "https.proxyPort",
            "socksProxyHost", "socksProxyPort"
        )
        keys.forEach { previousProxyProperties[it] = System.getProperty(it) }
        keys.forEach { System.clearProperty(it) }

        if (proxyUrl.isBlank()) return
        runCatching {
            val uri = URI(proxyUrl.trim())
            val host = uri.host ?: return@runCatching
            val port = if (uri.port > 0) uri.port else if (uri.scheme.equals("https", true)) 443 else 8080
            if (uri.scheme.equals("socks5", true) || uri.scheme.equals("socks", true)) {
                System.setProperty("socksProxyHost", host)
                System.setProperty("socksProxyPort", port.toString())
            } else {
                System.setProperty("http.proxyHost", host)
                System.setProperty("http.proxyPort", port.toString())
                System.setProperty("https.proxyHost", host)
                System.setProperty("https.proxyPort", port.toString())
            }
            val userInfo = uri.userInfo.orEmpty()
            if (userInfo.isNotBlank()) {
                val user = userInfo.substringBefore(':')
                val pass = userInfo.substringAfter(':', "")
                Authenticator.setDefault(object : Authenticator() {
                    override fun getPasswordAuthentication(): PasswordAuthentication =
                        PasswordAuthentication(user, pass.toCharArray())
                })
            }
        }
    }

    private fun restoreNetworkContext() {
        runCatching { CookieHandler.setDefault(previousCookieHandler) }
        runCatching { Authenticator.setDefault(previousAuthenticator) }
        previousProxyProperties.forEach { (key, value) ->
            if (value == null) System.clearProperty(key) else System.setProperty(key, value)
        }
    }

    private data class CookieJarEntry(val cookie: HttpCookie, val originHost: String?)

    private fun buildNetscapeCookieJar(): String {
        val now = System.currentTimeMillis() / 1000L
        val cookieStore = cookieManager.cookieStore
        val entries = mutableListOf<CookieJarEntry>()

        cookieStore.cookies.forEach { cookie ->
            val explicitDomain = cookie.domain.orEmpty()
            if (explicitDomain.isNotBlank() && isAllowedGloboHost(explicitDomain)) {
                entries += CookieJarEntry(cookie, null)
            }
        }

        cookieStore.getURIs().forEach { uri ->
            val originHost = uri.host.orEmpty().lowercase().trim('.')
            if (!isAllowedGloboHost(originHost)) return@forEach
            cookieStore.get(uri)
                .filter { it.domain.isNullOrBlank() }
                .forEach { entries += CookieJarEntry(it, originHost) }
        }

        val body = entries.asSequence()
            .filter { it.cookie.name.isNotBlank() }
            .distinctBy { entry ->
                val domainKey = entry.cookie.domain?.takeIf { it.isNotBlank() } ?: entry.originHost.orEmpty()
                listOf(domainKey.lowercase(), entry.cookie.path.orEmpty(), entry.cookie.name).joinToString("\t")
            }
            .joinToString("\n") { entry ->
                val c = entry.cookie
                val explicitDomain = c.domain?.takeIf { it.isNotBlank() }
                val domain = if (explicitDomain != null) {
                    val clean = explicitDomain.trim().trimStart('.')
                    ".$clean"
                } else {
                    entry.originHost ?: error("Cookie host-only sem origem conhecida.")
                }
                val includeSubdomains = if (explicitDomain != null) "TRUE" else "FALSE"
                val path = c.path?.takeIf { it.isNotBlank() } ?: "/"
                val secure = if (c.secure) "TRUE" else "FALSE"
                val expires = if (c.maxAge > 0) now + c.maxAge else 0L
                listOf(domain, includeSubdomains, path, secure, expires.toString(), c.name, c.value).joinToString("\t")
            }
        return "# Netscape HTTP Cookie File\n# Generated by Monitor de Notícias — Extrator de Vídeos\n$body\n"
    }

    private fun isAllowedGloboHost(value: String): Boolean {
        val host = value.lowercase().trim().trimStart('.')
        return host == "globo.com" || host.endsWith(".globo.com") ||
            host == "globoplay.com" || host.endsWith(".globoplay.com")
    }

    private enum class DialogModality(val awtValue: java.awt.Dialog.ModalityType) {
        MODELESS(java.awt.Dialog.ModalityType.MODELESS)
    }
}
