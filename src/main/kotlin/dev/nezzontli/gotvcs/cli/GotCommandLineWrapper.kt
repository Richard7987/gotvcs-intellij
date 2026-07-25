package dev.nezzontli.gotvcs.cli

import com.intellij.execution.ExecutionException
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.util.ExecUtil
import com.intellij.openapi.vcs.VcsException
import dev.nezzontli.gotvcs.settings.GotSettingsState
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentHashMap

data class GotStatusEntry(val code: Char, val stagedCode: Char, val path: String)

data class GotLogEntry(val commitId: String, val author: String, val date: String, val message: String)

data class GotUpdateEntry(val code: Char, val path: String)

data class GotCommitObject(
    val commitId: String,
    val parents: List<String>,
    val authorName: String,
    val authorEmail: String,
    /** Seconds since the epoch, as stored in the commit object. */
    val authorTimestamp: Long,
    val message: String,
)

data class GotChangedPath(val code: Char, val path: String)

data class GotRefEntry(val name: String, val hash: String, val isTag: Boolean, val isRemote: Boolean)

/**
 * Central entry point for every invocation of the `got` binary. The binary
 * path and SSH_AUTH_SOCK are configurable in Settings > Version Control >
 * got; when left blank they fall back to automatic detection.
 */
class GotCommandLineWrapper {

    private fun binaryPath(): String {
        GotSettingsState.getInstance().gotBinaryPath.takeIf { it.isNotBlank() }?.let { return it }
        val nixPath = File("/run/current-system/sw/bin/got")
        return if (nixPath.canExecute()) nixPath.path else "got"
    }

    /**
     * SSH_AUTH_SOCK is not guaranteed to be present in the IDE process's own
     * environment (it depends on how the desktop session launched it), and
     * that can break `got fetch`/`got send` over SSH even when an agent is
     * running. Falls back to a manual override, then to the conventional
     * gpg-agent SSH socket path for the current user.
     */
    private fun sshAuthSock(): String? {
        GotSettingsState.getInstance().sshAuthSock.takeIf { it.isNotBlank() }?.let { return it }
        System.getenv("SSH_AUTH_SOCK")?.let { return it }
        val fallback = File("/run/user/1000/gnupg/S.gpg-agent.ssh")
        return if (fallback.exists()) fallback.path else null
    }

    private fun run(workDir: File, vararg args: String): String {
        val commandLine = GeneralCommandLine(binaryPath(), *args)
            .withWorkDirectory(workDir)
            .withCharset(StandardCharsets.UTF_8)
            .withParentEnvironmentType(GeneralCommandLine.ParentEnvironmentType.SYSTEM)
        sshAuthSock()?.let { commandLine.environment["SSH_AUTH_SOCK"] = it }
        val output = try {
            ExecUtil.execAndGetOutput(commandLine)
        } catch (e: ExecutionException) {
            throw VcsException("Could not run got ${args.joinToString(" ")}: ${e.message}", e)
        }
        if (output.exitCode != 0) {
            throw VcsException("got ${args.joinToString(" ")} exited with ${output.exitCode}: ${output.stderr.trim()}")
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
            ?: throw VcsException("got info did not report a base commit for $workDir")
        return line.substringAfter("work tree base commit:").trim()
    }

    /** Current branch name (e.g. "main"), parsed from `got info`'s "work tree branch reference" line. */
    @Throws(VcsException::class)
    fun currentBranch(workDir: File): String? {
        val output = run(workDir, "info")
        val line = output.lineSequence().firstOrNull { it.trimStart().startsWith("work tree branch reference:") }
            ?: return null
        return line.substringAfter("work tree branch reference:").trim().removePrefix("refs/heads/")
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

    /** `got log` default verbose format: blocks separated by dashed lines. */
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
        run(workDir, "fetch")
    }

    /** With no arguments, `got send` targets the "origin" remote and the work tree's current branch. */
    @Throws(VcsException::class)
    fun send(workDir: File): String = run(workDir, "send")

    /**
     * `got update` status lines share `got status`'s "code + 2 spaces + path"
     * format, but a no-op update instead prints a free-text line such as
     * "Already up-to-date" that must not be mistaken for a status entry.
     */
    @Throws(VcsException::class)
    fun update(workDir: File): List<GotUpdateEntry> {
        val output = run(workDir, "update")
        return output.lineSequence()
            .filter { it.length >= 3 && it[1] == ' ' && it[2] == ' ' }
            .map { line -> GotUpdateEntry(line[0], line.substring(3)) }
            .toList()
    }

    /** Resolves `refs/remotes/<remote>/<branch>` to a commit ID via `got ref -l`, or null if it doesn't exist locally. */
    @Throws(VcsException::class)
    fun remoteBranchHash(workDir: File, remote: String, branch: String): String? {
        val output = run(workDir, "ref", "-l")
        val prefix = "refs/remotes/$remote/$branch:"
        val line = output.lineSequence().firstOrNull { it.trim().startsWith(prefix) } ?: return null
        return line.substringAfter(":").trim()
    }

    /**
     * Commit IDs reachable from the current branch but not yet known to be on
     * [remoteHash] (via `got log -x`), most recent first. If [remoteHash] is
     * null (no local knowledge of the remote branch, e.g. before any fetch),
     * this falls back to the full history up to [limit].
     */
    @Throws(VcsException::class)
    fun outgoingCommitIds(workDir: File, remoteHash: String?, limit: Int = 200): List<String> {
        val args = mutableListOf("log", "-l", limit.toString())
        if (remoteHash != null) {
            args.add("-x")
            args.add(remoteHash)
        }
        val output = run(workDir, *args.toTypedArray())
        return output.lineSequence()
            .filter { it.startsWith("commit ") }
            .map { it.removePrefix("commit ").trim().substringBefore(' ') }
            .filter { it != remoteHash }
            .toList()
    }

    private val commitAuthorPattern = Regex("""^(.*)\s+<(.+)>\s+(\d+)\s+[+-]\d{4}$""")

    // Commit objects are immutable once created, so caching by id is always safe.
    private val commitCache = ConcurrentHashMap<String, GotCommitObject>()

    /** Parses the raw commit object printed by `got cat <commit-id>` (tree/parent/author/committer/message). */
    @Throws(VcsException::class)
    fun catCommit(workDir: File, commitId: String): GotCommitObject {
        commitCache[commitId]?.let { return it }

        val lines = run(workDir, "cat", commitId).lines()
        val parents = mutableListOf<String>()
        var authorName = ""
        var authorEmail = ""
        var authorTimestamp = 0L
        var messageStart = -1

        for ((index, line) in lines.withIndex()) {
            when {
                line.startsWith("parent ") -> parents.add(line.removePrefix("parent ").trim())
                line.startsWith("author ") -> {
                    commitAuthorPattern.find(line.removePrefix("author ").trim())?.let { match ->
                        authorName = match.groupValues[1].trim()
                        authorEmail = match.groupValues[2]
                        authorTimestamp = match.groupValues[3].toLongOrNull() ?: 0L
                    }
                }
                line.startsWith("messagelen ") -> messageStart = index + 2
            }
        }

        val message = if (messageStart in lines.indices) lines.drop(messageStart).joinToString("\n").trimEnd('\n') else ""
        val commitObject = GotCommitObject(commitId, parents, authorName, authorEmail, authorTimestamp, message)
        commitCache[commitId] = commitObject
        return commitObject
    }

    /**
     * Full commit-ID history reachable from the work tree's current branch,
     * most recent first, including commits merged in from other branches
     * (`-b`; without it `got log` only shows the linear mainline). Parent
     * hashes (needed for the Log tab's graph, including real merges with
     * more than one parent) come from [catCommit], not from this bulk
     * listing -- `got log` never prints them.
     */
    @Throws(VcsException::class)
    fun allCommitIds(workDir: File): List<String> {
        val output = run(workDir, "log", "-b")
        return output.lineSequence()
            .filter { it.startsWith("commit ") }
            .map { it.removePrefix("commit ").trim().substringBefore(' ') }
            .toList()
    }

    /** Changed-file list for a single commit, via `got log -P` scoped to one commit. */
    @Throws(VcsException::class)
    fun changedPaths(workDir: File, commitId: String): List<GotChangedPath> {
        val output = run(workDir, "log", "-c", commitId, "-l", "1", "-P")
        return output.lineSequence()
            .filter { it.length >= 4 && it[0] == ' ' && it[1] in "MDAm" && it[2] == ' ' && it[3] == ' ' }
            .map { line -> GotChangedPath(line[1], line.substring(4)) }
            .toList()
    }

    private val hexHashPattern = Regex("^[0-9a-f]{40}$")

    /**
     * All refs known to the repository, via `got ref -l`. Filters out
     * symbolic pointers (e.g. `HEAD: refs/heads/main`, whose value is a ref
     * name, not a hash) and got's internal work-tree bookkeeping refs.
     */
    @Throws(VcsException::class)
    fun refs(workDir: File): List<GotRefEntry> {
        val output = run(workDir, "ref", "-l")
        val entries = mutableListOf<GotRefEntry>()
        for (line in output.lineSequence()) {
            val colon = line.indexOf(':')
            if (colon < 0) continue
            val name = line.substring(0, colon).trim()
            val value = line.substring(colon + 1).trim()
            if (!hexHashPattern.matches(value)) continue
            when {
                name.startsWith("refs/heads/") -> entries.add(GotRefEntry(name.removePrefix("refs/heads/"), value, isTag = false, isRemote = false))
                name.startsWith("refs/tags/") -> entries.add(GotRefEntry(name.removePrefix("refs/tags/"), value, isTag = true, isRemote = false))
                name.startsWith("refs/remotes/") -> entries.add(GotRefEntry(name.removePrefix("refs/remotes/"), value, isTag = false, isRemote = true))
            }
        }
        return entries
    }

    /**
     * `got clone` only ever produces a bare repository, never a work tree
     * (got has no non-bare repo layout) -- [checkout] is the separate step
     * that populates a usable `.got/` work tree from it.
     */
    @Throws(VcsException::class)
    fun clone(parentDir: File, url: String, bareRepoDir: File) {
        run(parentDir, "clone", url, bareRepoDir.absolutePath)
    }

    @Throws(VcsException::class)
    fun checkout(parentDir: File, bareRepoDir: File, workTreeDir: File) {
        run(parentDir, "checkout", bareRepoDir.absolutePath, workTreeDir.absolutePath)
    }
}
