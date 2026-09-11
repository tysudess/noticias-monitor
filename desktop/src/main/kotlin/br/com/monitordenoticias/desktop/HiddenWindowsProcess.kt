package br.com.monitordenoticias.desktop

import java.io.File
import java.util.concurrent.TimeUnit

internal object HiddenWindowsProcess {
    private val isWindows: Boolean = System.getProperty("os.name", "").contains("Windows", ignoreCase = true)

    fun start(command: List<String>, directory: File, environment: Map<String, String> = emptyMap()): Process {
        require(command.isNotEmpty()) { "Comando vazio." }
        if (!isWindows) {
            return ProcessBuilder(command)
                .directory(directory)
                .redirectErrorStream(true)
                .also { it.environment().putAll(environment) }
                .start()
        }

        val executableName = runCatching { File(command.first()).name.lowercase() }.getOrDefault("")
        if (executableName == "powershell.exe") {
            val args = command.drop(1).toMutableList()
            if (args.none { it.equals("-WindowStyle", ignoreCase = true) }) {
                args.add(0, "Hidden")
                args.add(0, "-WindowStyle")
            }
            return ProcessBuilder(listOf(command.first()) + args)
                .directory(directory)
                .redirectErrorStream(true)
                .also { it.environment().putAll(environment) }
                .start()
        }

        val script = buildString {
            append("& ")
            command.forEachIndexed { index, arg ->
                if (index > 0) append(' ')
                append('\'')
                append(arg.replace("'", "''"))
                append('\'')
            }
            append(" 2>&1; exit \$LASTEXITCODE")
        }
        return ProcessBuilder(
            "powershell.exe",
            "-NoLogo",
            "-NoProfile",
            "-NonInteractive",
            "-ExecutionPolicy", "Bypass",
            "-WindowStyle", "Hidden",
            "-Command", script
        )
            .directory(directory)
            .redirectErrorStream(true)
            .also { it.environment().putAll(environment) }
            .start()
    }

    fun destroyTree(process: Process?) {
        if (process == null) return
        if (!isWindows) {
            runCatching { process.descendants().forEach { it.destroyForcibly() } }
            runCatching { process.destroyForcibly() }
            return
        }

        val pid = runCatching { process.pid() }.getOrNull()
        if (pid != null) {
            runCatching {
                ProcessBuilder(
                    "powershell.exe",
                    "-NoLogo",
                    "-NoProfile",
                    "-NonInteractive",
                    "-WindowStyle", "Hidden",
                    "-Command", "& taskkill.exe /PID $pid /T /F 2>`${'$'}null; exit 0"
                )
                    .redirectErrorStream(true)
                    .start()
                    .waitFor(8, TimeUnit.SECONDS)
            }
        }
        runCatching { process.destroyForcibly() }
    }
}
