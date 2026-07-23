package dev.nezzontli.gotvcs

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.util.ExecUtil
import com.intellij.openapi.vcs.VcsException
import java.io.File
import java.nio.charset.StandardCharsets

data class GotStatusEntry(val code: Char, val stagedCode: Char, val path: String)

/**
 * Centraliza todas las invocaciones al binario `got`. La ruta de abajo es la
 * del store de Nix resuelta al momento de escribir este código
 * (readlink -f /etc/profiles/per-user/ale/bin/idea muestra el store path del
 * IDE; el de got se resolvió igual desde /run/current-system/sw/bin/got).
 * Cambia con cada rebuild del sistema -> deuda para GotConfigurable (Fase 6).
 */
class GotCommandLineWrapper {

    private fun binaryPath(): String {
        val nixPath = File("/run/current-system/sw/bin/got")
        return if (nixPath.canExecute()) nixPath.path else "got"
    }

    private fun run(workDir: File, vararg args: String): String {
        val commandLine = GeneralCommandLine(binaryPath(), *args)
            .withWorkDirectory(workDir)
            .withCharset(StandardCharsets.UTF_8)
        val output = try {
            ExecUtil.execAndGetOutput(commandLine)
        } catch (e: ExecutionException) {
            throw VcsException("No se pudo ejecutar got ${args.joinToString(" ")}: ${e.message}", e)
        }
        if (output.exitCode != 0) {
            throw VcsException("got ${args.joinToString(" ")} salió con código ${output.exitCode}: ${output.stderr.trim()}")
        }
        return output.stdout
    }

    @Throws(VcsException::class)
    fun status(workDir: File): List<GotStatusEntry> {
        val output = run(workDir, "status")
        return output.lineSequence()
            .filter { it.isNotBlank() }
            .map { line ->
                val code = line[0]
                val stagedCode = line.getOrElse(1) { ' ' }
                val path = line.substring(minOf(3, line.length))
                GotStatusEntry(code, stagedCode, path)
            }
            .toList()
    }

    @Throws(VcsException::class)
    fun catAtBase(workDir: File, relativePath: String): String =
        run(workDir, "cat", "-c", ":base", "-P", relativePath)

    @Throws(VcsException::class)
    fun baseCommit(workDir: File): String {
        val output = run(workDir, "info")
        val line = output.lineSequence().firstOrNull { it.trimStart().startsWith("work tree base commit:") }
            ?: throw VcsException("got info no reportó un commit base para $workDir")
        return line.substringAfter("work tree base commit:").trim()
    }

    @Throws(VcsException::class)
    fun commit(workDir: File, message: String, paths: List<String>) {
        if (paths.isEmpty()) return
        run(workDir, *(arrayOf("commit", "-m", message) + paths))
    }

    @Throws(VcsException::class)
    fun add(workDir: File, paths: List<String>) {
        if (paths.isEmpty()) return
        run(workDir, *(arrayOf("add") + paths))
    }

    @Throws(VcsException::class)
    fun remove(workDir: File, paths: List<String>) {
        if (paths.isEmpty()) return
        run(workDir, *(arrayOf("remove", "-f") + paths))
    }

    @Throws(VcsException::class)
    fun revert(workDir: File, paths: List<String>) {
        if (paths.isEmpty()) return
        run(workDir, *(arrayOf("revert", "-R") + paths))
    }
}
