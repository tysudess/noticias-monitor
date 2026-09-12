package br.com.monitordenoticias.desktop

import java.io.File
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.concurrent.TimeUnit

internal class GloboplaySessionStore(private val baseDir: File) {
    private val sessionDir = File(baseDir, "data/extractor").apply { mkdirs() }
    private val encryptedFile = File(sessionDir, "globoplay.session.dpapi")

    fun hasSavedSession(): Boolean = encryptedFile.exists() && encryptedFile.length() > 0

    fun saveNetscapeCookies(cookieText: String): Result<Unit> = runCatching {
        require(cookieText.isNotBlank()) { "A sessão do Globoplay está vazia." }
        val plain = Base64.getEncoder().encodeToString(cookieText.toByteArray(StandardCharsets.UTF_8))
        val protected = runPowerShell(
            script = """
                Add-Type -AssemblyName System.Security
                ${'$'}inputB64=[Console]::In.ReadToEnd().Trim()
                ${'$'}bytes=[Convert]::FromBase64String(${'$'}inputB64)
                ${'$'}enc=[System.Security.Cryptography.ProtectedData]::Protect(${'$'}bytes,${'$'}null,[System.Security.Cryptography.DataProtectionScope]::CurrentUser)
                [Convert]::ToBase64String(${'$'}enc)
            """.trimIndent(),
            stdinText = plain
        ).trim()
        require(protected.isNotBlank()) { "O Windows não retornou os dados protegidos da sessão." }
        encryptedFile.writeText(protected, Charsets.UTF_8)
    }

    fun createRuntimeCookieFile(): File? {
        if (!hasSavedSession()) return null
        return runCatching {
            val encrypted = encryptedFile.readText(Charsets.UTF_8).trim()
            val decodedB64 = runPowerShell(
                script = """
                    Add-Type -AssemblyName System.Security
                    ${'$'}inputB64=[Console]::In.ReadToEnd().Trim()
                    ${'$'}enc=[Convert]::FromBase64String(${'$'}inputB64)
                    ${'$'}plain=[System.Security.Cryptography.ProtectedData]::Unprotect(${'$'}enc,${'$'}null,[System.Security.Cryptography.DataProtectionScope]::CurrentUser)
                    [Convert]::ToBase64String(${'$'}plain)
                """.trimIndent(),
                stdinText = encrypted
            ).trim()
            val cookieText = String(Base64.getDecoder().decode(decodedB64), StandardCharsets.UTF_8)
            File.createTempFile("globoplay-session-", ".cookies.txt").apply {
                writeText(cookieText, Charsets.UTF_8)
                deleteOnExit()
            }
        }.getOrNull()
    }

    fun deleteSavedSession(): Boolean = !encryptedFile.exists() || encryptedFile.delete()

    private fun runPowerShell(script: String, stdinText: String): String {
        val command = listOf(
            "powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
            "-Command", script
        )
        val process = HiddenWindowsProcess.start(command, sessionDir)
        val output = StringBuilder()
        val readerThread = Thread({
            runCatching {
                process.inputStream.bufferedReader(Charsets.UTF_8).useLines { lines ->
                    lines.forEach { line ->
                        output.appendLine(line)
                    }
                }
            }
        }, "globoplay-dpapi-output").apply {
            isDaemon = true
            start()
        }

        process.outputStream.bufferedWriter(Charsets.UTF_8).use { writer ->
            writer.write(stdinText)
            writer.flush()
        }

        val ok = process.waitFor(120, TimeUnit.SECONDS)
        if (!ok) {
            HiddenWindowsProcess.destroyTree(process)
            readerThread.join(1500)
            error("Tempo excedido ao acessar a proteção segura do Windows para a sessão do Globoplay.")
        }

        readerThread.join(5000)
        val result = output.toString()
        if (process.exitValue() != 0) {
            error(result.takeLast(1000).ifBlank { "O Windows não conseguiu proteger a sessão do Globoplay." })
        }
        return result
    }
}
