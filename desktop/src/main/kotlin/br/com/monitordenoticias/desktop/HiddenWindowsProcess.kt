package br.com.monitordenoticias.desktop

import java.io.File
import java.util.concurrent.TimeUnit

internal object HiddenWindowsProcess {
    private val isWindows: Boolean = System.getProperty("os.name", "").contains("Windows", ignoreCase = true)

    fun start(command: List<String>, directory: File): Process {
        require(command.isNotEmpty()) { "Comando vazio." }
        if (!isWindows) {
            return ProcessBuilder(command)
                .directory(directory)
                .redirectErrorStream(true)
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
                    "powershell.exe", "-NoLogo", "-NoProfile", "-NonInteractive", "-WindowStyle", "Hidden",
                    "-Command", "Stop-Process -Id $pid -Force -ErrorAction SilentlyContinue; Get-CimInstance Win32_Process | Where-Object ParentProcessId -eq $pid | ForEach-Object { Stop-Process -Id \$_.ProcessId -Force -ErrorAction SilentlyContinue }"
                ).start().waitFor(5, TimeUnit.SECONDS)
            }
        }
        runCatching { process.destroyForcibly() }
    }
}
