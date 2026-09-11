package br.com.monitordenoticias.desktop

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.TimeUnit

internal data class YtDlpUpdateResult(val updated: Boolean, val message: String)

internal class YtDlpUpdater(private val engine: ExtractorVideoEngine) {
    suspend fun update(progress: (String) -> Unit): YtDlpUpdateResult = withContext(Dispatchers.IO) {
        val target = engine.ytDlp
        val binDir = target.parentFile.apply { mkdirs() }
        val temp = File(binDir, "yt-dlp.update.tmp.exe")
        val backup = File(binDir, "yt-dlp.backup.exe")
        try {
            progress("Baixando atualização do yt-dlp...")
            download("https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp.exe", temp)
            require(temp.exists() && temp.length() > 1_000_000) { "Arquivo de atualização inválido." }

            progress("Validando novo yt-dlp...")
            val version = validate(temp)
            require(version.isNotBlank()) { "O novo yt-dlp não respondeu à validação." }

            progress("Aplicando atualização validada...")
            if (backup.exists()) backup.delete()
            if (target.exists()) Files.move(target.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING)
            try {
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
            } catch (_: Exception) {
                Files.move(temp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }

            val installedVersion = validate(target)
            require(installedVersion.isNotBlank()) { "Falha ao validar o yt-dlp após a substituição." }
            if (backup.exists()) backup.delete()
            YtDlpUpdateResult(true, "yt-dlp atualizado e validado: $installedVersion")
        } catch (e: Exception) {
            runCatching { temp.delete() }
            if ((!target.exists() || runCatching { validate(target).isBlank() }.getOrDefault(true)) && backup.exists()) {
                runCatching { Files.move(backup.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING) }
            }
            YtDlpUpdateResult(false, "Atualização não aplicada. O executável anterior foi preservado. ${e.message.orEmpty()}")
        } finally {
            runCatching { temp.delete() }
            if (target.exists() && backup.exists()) runCatching { backup.delete() }
        }
    }

    private fun download(url: String, destination: File) {
        val connection = URL(url).openConnection() as HttpURLConnection
        connection.connectTimeout = 30_000
        connection.readTimeout = 60_000
        connection.instanceFollowRedirects = true
        connection.setRequestProperty("User-Agent", "MonitorDeNoticias-Extractor/3.0.1")
        connection.inputStream.use { input -> destination.outputStream().use { output -> input.copyTo(output) } }
    }

    private fun validate(executable: File): String {
        val process = ProcessBuilder(executable.absolutePath, "--version")
            .directory(engine.appDir.toFile())
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader(Charsets.UTF_8).readText().trim()
        val finished = process.waitFor(30, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            return ""
        }
        return if (process.exitValue() == 0) output.lineSequence().firstOrNull().orEmpty() else ""
    }
}
