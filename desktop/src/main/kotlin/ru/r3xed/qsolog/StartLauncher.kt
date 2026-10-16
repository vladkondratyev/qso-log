package ru.r3xed.qsolog

import ru.r3xed.qsolog.data.StartApp
import ru.r3xed.qsolog.data.StartApps
import java.io.File
import java.io.IOException

/**
 * Starts the programs of "Запускать вместе с QSO-LOG": a path to a program (an .exe, a Mac .app, a Linux binary) or a
 * command found on PATH (rigctld), with its arguments. Their output is dropped.
 */
object StartLauncher {
    /** The command line for [a]: a Mac .app runs its own executable, so that QSO-LOG can close it later. */
    fun command(a: StartApp): List<String> {
        val f = File(a.target)
        val exe = if (f.isDirectory && f.name.endsWith(".app")) appExecutable(f) ?: return listOf("open", "-a", f.path) + argsFor(a, mac = true)
        else resolve(a.target)
        return listOf(exe) + StartApps.splitArgs(a.args)
    }

    private fun argsFor(a: StartApp, mac: Boolean): List<String> = StartApps.splitArgs(a.args).let { if (mac && it.isNotEmpty()) listOf("--args") + it else it }

    /** Contents/MacOS/<CFBundleExecutable> of a Mac application. */
    private fun appExecutable(app: File): String? {
        val dir = File(app, "Contents/MacOS")
        val plist = File(app, "Contents/Info.plist").takeIf { it.isFile }?.readText()
        val name = plist?.let { Regex("<key>CFBundleExecutable</key>\\s*<string>([^<]+)</string>").find(it)?.groupValues?.get(1) }
        return (name?.let { File(dir, it) }?.takeIf { it.canExecute() } ?: dir.listFiles()?.singleOrNull { it.canExecute() })?.path
    }

    /** A bare name ("rigctld") is looked for on PATH and in the usual Hamlib places. */
    private fun resolve(target: String): String {
        if (target.contains('/') || target.contains('\\')) return target
        val names = if (IS_WINDOWS) listOf("$target.exe", target) else listOf(target)
        val dirs = System.getenv("PATH").orEmpty().split(File.pathSeparatorChar) +
            listOf("/opt/homebrew/bin", "/usr/local/bin", "/usr/bin", "C:\\Program Files\\hamlib-w64\\bin", "C:\\Program Files (x86)\\hamlib-w32\\bin")
        for (d in dirs) for (n in names) File(d, n).takeIf { it.isFile && it.canExecute() }?.let { return it.path }
        return target
    }

    fun start(a: StartApp): Process {
        val cmd = command(a)
        return try {
            ProcessBuilder(cmd).redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(ProcessBuilder.Redirect.DISCARD).start()
        } catch (e: IOException) {
            throw IOException(tr("программа %s не найдена", cmd.first()))
        }
    }

    /** Whether a program with the same executable already runs (JTDX opened by hand before QSO-LOG). */
    fun running(a: StartApp): Boolean {
        val exe = runCatching { File(command(a).first()).name.lowercase() }.getOrNull() ?: return false
        if (exe == "open") return false
        return runCatching {
            ProcessHandle.allProcesses().anyMatch { p -> p.info().command().map { File(it).name.lowercase() == exe }.orElse(false) }
        }.getOrDefault(false)
    }
}
