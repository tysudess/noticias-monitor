package br.com.monitordenoticias.desktop

import java.io.File
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.Properties
import java.util.concurrent.TimeUnit

internal class ExtractorPortableStateStore(baseDir: File) {
    private val dataDir = File(baseDir, "data/extractor").apply { mkdirs() }
    private val settingsFile = File(dataDir, "settings.properties")
    private val historyFile = File(dataDir, "history.txt")
    private val proxyFile = File(dataDir, "proxy.dpapi")

    fun loadQualityIndex(defaultValue: Int = 1): Int = runCatching {
        val props = Properties()
        if (settingsFile.exists()) settingsFile.inputStream().use(props::load)
        props.getProperty("qualityIndex")?.toIntOrNull()?.coerceIn(0, EXTRACTOR_QUALITIES.lastIndex)
            ?: defaultValue.coerceIn(0, EXTRACTOR_QUALITIES.lastIndex)
    }.getOrDefault(defaultValue.coerceIn(0, EXTRACTOR_QUALITIES.lastIndex))

    fun saveQualityIndex(value: Int) {
        val safe = value.coerceIn(0, EXTRACTOR_QUALITIES.lastIndex)
        val props = Properties()
        if (settingsFile.exists()) runCatching { settingsFile.inputStream().use(props::load) }
        props.setProperty("qualityIndex", safe.toString())
        settingsFile.outputStream().use { props.store(it, "Extractor de Videos portable settings") }
    }

    fun loadHistory(limit: Int = 50): List<String> = runCatching {
        if (!historyFile.exists()) return@runCatching emptyList()
        historyFile.readLines(Charsets.UTF_8)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()
            .take(limit.coerceAtLeast(1))
    }.getOrDefault(emptyList())

    fun addHistory(path: String, limit: Int = 50): List<String> {
        val normalized = path.trim()
        if (normalized.isBlank()) return loadHistory(limit)
        val updated = (listOf(normalized) + loadHistory(limit * 2))
            .distinct()
            .take(limit.coerceAtLeast(1))
        historyFile.writeText(updated.joinToString(System.lineSeparator()), Charsets.UTF_8)
        return updated
    }

    fun clearHistory(): Boolean = !historyFile.exists() || historyFile.delete()

    fun saveProxy(proxy: String): Result<Unit> = runCatching {
        val value = proxy.trim()
        if (value.isBlank()) {
            deleteProxy()
            return@runCatching
        }
        val plainB64 = Base64.getEncoder().encodeToString(value.toByteArray(StandardCharsets.UTF_8))
        val encrypted = runPowerShell(
            """
            Add-Type -AssemblyName System.Security
            ${'$'}bytes=[Convert]::FromBase64String('$plainB64')
            ${'$'}enc=[System.Security.Cryptography.ProtectedData]::Protect(${'$'}bytes,${'$'}null,[System.Security.Cryptography.DataProtectionScope]::CurrentUser)
            [Convert]::ToBase64String(${'$'}enc)
            """.trimIndent()
        ).trim()
        require(encrypted.isNotBlank()) { "O Windows não retornou os dados protegidos do proxy." }
        proxyFile.writeText(encrypted, Charsets.UTF_8)
    }

    fun loadProxy(): String = runCatching {
        if (!proxyFile.exists() || proxyFile.length() == 0L) return@runCatching ""
        val encrypted = proxyFile.readText(Charsets.UTF_8).trim()
        val plainB64 = runPowerShell(
            """
            Add-Type -AssemblyName System.Security
            ${'$'}enc=[Convert]::FromBase64String('$encrypted')
            ${'$'}plain=[System.Security.Cryptography.ProtectedData]::Unprotect(${'$'}enc,${'$'}null,[System.Security.Cryptography.DataProtectionScope]::CurrentUser)
            [Convert]::ToBase64String(${'$'}plain)
            """.trimIndent()
        ).trim()
        String(Base64.getDecoder().decode(plainB64), StandardCharsets.UTF_8)
    }.getOrDefault("")

    fun deleteProxy(): Boolean = !proxyFile.exists() || proxyFile.delete()

    private fun runPowerShell(script: String): String {
        val process = HiddenWindowsProcess.start(
            listOf(
                "powershell.exe", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
                "-Command", script
            ),
            dataDir
        )
        val output = process.inputStream.bufferedReader(Charsets.UTF_8).readText()
        val finished = process.waitFor(30, TimeUnit.SECONDS)
        if (!finished) {
            HiddenWindowsProcess.destroyTree(process)
            error("Tempo excedido ao acessar o armazenamento seguro do Windows.")
        }
        if (process.exitValue() != 0) error(output.takeLast(1000))
        return output
    }
}
