package dev.nezzontli.gotvcs

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.util.ExecUtil
import com.intellij.openapi.vcs.VcsException
import java.io.File
import java.nio.charset.StandardCharsets

data class GotStatusEntry(val code: Char, val stagedCode: Char, val path: String)

data class GotLogEntry(val commitId: String, val author: String, val date: String, val message: String)

data class GotUpdateEntry(val code: Char, val path: String)

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
        // GeneralCommandLine usa por defecto un snapshot de entorno cacheado
        // por la plataforma (EnvironmentUtil), no el entorno real del proceso
        // de IntelliJ. En este setup (gpg-agent con soporte ssh) ese snapshot
        // puede no coincidir con SSH_AUTH_SOCK actual, y got/ssh fallan con
        // "Permission denied (publickey)" aunque el agente esté vivo y con la
        // clave cargada. Se fuerza explícitamente el valor real del proceso.
        System.getenv("SSH_AUTH_SOCK")?.let { commandLine.environment["SSH_AUTH_SOCK"] = it }
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
    fun catAt(workDir: File, commitId: String, relativePath: String): String =
        run(workDir, "cat", "-c", commitId, "-P", relativePath)

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

    /** Historial vía `got log`, formato verbose por defecto (bloques separados por líneas de guiones). */
    @Throws(VcsException::class)
    fun log(workDir: File, relativePath: String?, limit: Int = 500): List<GotLogEntry> {
        val args = mutableListOf("log", "-l", limit.toString())
        if (relativePath != null) args.add(relativePath)
        val output = run(workDir, *args.toTypedArray())

        val entries = mutableListOf<GotLogEntry>()
        var commitId: String? = null
        var author: String? = null
        var date: String? = null
        val message = StringBuilder()

        fun flush() {
            val id = commitId
            if (id != null) {
                entries.add(GotLogEntry(id, author.orEmpty(), date.orEmpty(), message.toString().trim('\n')))
            }
            commitId = null
            author = null
            date = null
            message.setLength(0)
        }

        for (line in output.lineSequence()) {
            when {
                line.isBlank() && commitId == null -> Unit
                line.all { it == '-' } && line.isNotEmpty() -> flush()
                line.startsWith("commit ") -> commitId = line.removePrefix("commit ").trim().substringBefore(' ')
                line.startsWith("from: ") -> author = line.removePrefix("from: ").trim()
                line.startsWith("date: ") -> date = line.removePrefix("date: ").trim()
                line.startsWith(" ") -> message.appendLine(line.trimStart(' '))
            }
        }
        flush()
        return entries
    }

    @Throws(VcsException::class)
    fun fetch(workDir: File) {
        // -v también se reenvía a ssh(1): diagnóstico temporal para el fallo
        // de "Permission denied (publickey)" reportado en vivo (Fase 6).
        run(workDir, "fetch", "-v")
    }

    /** `got update`, formato de estado similar a `got status` (código + 2 espacios + path). */
    @Throws(VcsException::class)
    fun update(workDir: File): List<GotUpdateEntry> {
        val output = run(workDir, "update")
        return output.lineSequence()
            .filter { it.isNotBlank() && it[0] != ' ' }
            .mapNotNull { line ->
                if (line.length < 3) return@mapNotNull null
                GotUpdateEntry(line[0], line.substring(3))
            }
            .toList()
    }
}
