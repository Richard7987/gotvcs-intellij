package dev.nezzontli.gotvcs

import com.intellij.openapi.vcs.RepositoryLocation
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.history.VcsFileRevision
import com.intellij.openapi.vcs.history.VcsRevisionNumber
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Una entrada de `got log` adaptada al modelo de historial de IntelliJ. */
class GotFileRevision(
    private val workDir: File,
    private val relativePath: String,
    private val commandLine: GotCommandLineWrapper,
    private val entry: GotLogEntry,
) : VcsFileRevision {

    // Formato real de `got log`: "Thu Jul 23 05:30:12 2026 UTC".
    private val dateFormat = SimpleDateFormat("EEE MMM d HH:mm:ss yyyy zzz", Locale.US)

    override fun getRevisionNumber(): VcsRevisionNumber = GotRevisionNumber(entry.commitId)

    override fun getRevisionDate(): Date = try {
        dateFormat.parse(entry.date)
    } catch (e: Exception) {
        Date(0)
    }

    override fun getAuthor(): String = entry.author

    override fun getCommitMessage(): String = entry.message

    override fun getBranchName(): String? = null

    override fun getChangedRepositoryPath(): RepositoryLocation? = null

    @Throws(VcsException::class)
    override fun loadContent(): ByteArray = getContent()

    @Throws(VcsException::class)
    override fun getContent(): ByteArray = commandLine.catAt(workDir, entry.commitId, relativePath).toByteArray()
}
