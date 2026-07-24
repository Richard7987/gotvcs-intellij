package dev.nezzontli.gotvcs.log

import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.ContentRevision
import com.intellij.openapi.vcs.history.VcsRevisionNumber
import dev.nezzontli.gotvcs.changes.GotRevisionNumber
import dev.nezzontli.gotvcs.cli.GotCommandLineWrapper
import java.io.File

/**
 * File content at an arbitrary historical commit (`got cat -c <commit> -P`),
 * for the Log tab's per-commit diff. Unlike GotContentRevision (which is
 * pinned to the work tree's current base commit), this one is parametrized
 * by whichever commit the Log tab is showing -- called from background log
 * loading, never from EDT-bound UI rendering, so it may shell out lazily.
 */
class GotCommitContentRevision(
    private val filePath: FilePath,
    private val workDir: File,
    private val relativePath: String,
    private val commandLine: GotCommandLineWrapper,
    private val commitId: String,
) : ContentRevision {

    @Throws(VcsException::class)
    override fun getContent(): String = commandLine.catAt(workDir, commitId, relativePath)

    override fun getFile(): FilePath = filePath

    override fun getRevisionNumber(): VcsRevisionNumber = GotRevisionNumber(commitId)
}
