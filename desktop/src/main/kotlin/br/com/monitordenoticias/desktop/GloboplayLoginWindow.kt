package br.com.monitordenoticias.desktop

import java.io.File
import javax.swing.SwingUtilities

/**
 * Login oficial do Globoplay em navegador Chromium embutido, reproduzindo o fluxo
 * da versão portátil v3.0.1 original (PySide6/Qt WebEngine).
 * A senha nunca é recebida pelo Monitor; somente o cookie jar retornado pelo
 * helper é protegido com DPAPI pelo GloboplaySessionStore.
 */
internal class GloboplayLoginWindow(
    private val owner: java.awt.Window?,
    private val engine: ExtractorVideoEngine,
    private val proxyUrl: String,
    private val onSessionChanged: (Boolean, String) -> Unit
) {
    private val store = GloboplaySessionStore(engine.appDir.toFile())
    @Volatile private var activeProcess: Process? = null

    fun open() {
        val helper = File(engine.binDir, "GloboplayLoginHelper/GloboplayLoginHelper.exe")
        if (!helper.isFile) {
            onSessionChanged(false, "Navegador interno do Globoplay não encontrado no pacote.")
            return
        }

        val dataDir = File(engine.appDir.toFile(), "data/extractor").apply { mkdirs() }
        val profileDir = File(dataDir, "globoplay-web-profile").apply { mkdirs() }
        val output = File.createTempFile("globoplay-session-", ".cookies", dataDir)
        output.deleteOnExit()

        val process = runCatching {
            ProcessBuilder(
                helper.absolutePath,
                "--output", output.absolutePath,
                "--profile-dir", profileDir.absolutePath
            )
                .directory(engine.appDir.toFile())
                .redirectOutput(ProcessBuilder.Redirect.DISCARD)
                .redirectError(ProcessBuilder.Redirect.DISCARD)
                .also { builder ->
                    if (proxyUrl.isNotBlank()) {
                        builder.environment()["MONITOR_GLOBOPLAY_PROXY"] = proxyUrl.trim()
                    }
                }
                .start()
        }.getOrElse {
            output.delete()
            onSessionChanged(false, "Não foi possível abrir o navegador interno do Globoplay: ${it.message.orEmpty()}")
            return
        }
        activeProcess = process

        Thread({
            val result = runCatching {
                val exit = process.waitFor()
                activeProcess = null
                if (exit != 0) {
                    return@runCatching Pair(false, "Login do Globoplay cancelado ou não concluído.")
                }
                require(output.isFile && output.length() > 0L) {
                    "O navegador interno não retornou cookies da sessão."
                }
                val netscape = output.readText(Charsets.UTF_8)
                val cookieCount = netscape.lineSequence().count {
                    it.isNotBlank() && !it.startsWith("#")
                }
                require(cookieCount > 0) { "Nenhum cookie Globo/Globoplay foi capturado." }
                store.saveNetscapeCookies(netscape).getOrThrow()
                Pair(true, "Sessão Globoplay salva com segurança ($cookieCount cookies).")
            }.getOrElse {
                Pair(store.hasSavedSession(), "Falha ao salvar sessão Globoplay: ${it.message.orEmpty()}")
            }

            runCatching { output.delete() }
            SwingUtilities.invokeLater {
                onSessionChanged(result.first, result.second)
            }
        }, "globoplay-login-waiter").apply {
            isDaemon = true
            start()
        }
    }
}
