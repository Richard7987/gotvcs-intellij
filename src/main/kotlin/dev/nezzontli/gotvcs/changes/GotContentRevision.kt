package dev.nezzontli.gotvcs.changes

import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.ContentRevision
import com.intellij.openapi.vcs.history.VcsRevisionNumber
import dev.nezzontli.gotvcs.cli.GotCommandLineWrapper
import java.io.File

/**
 * Content of a file at the work tree's base commit, via `got cat -c :base`.
 *
 * [revisionNumber] must be resolved ahead of time by the caller, off the EDT:
 * getRevisionNumber() is invoked by the Commit tree renderer and the diff
 * title, both on the UI thread, and running `got info` there would trip
 * IntelliJ's "Synchronous execution on EDT" guard
 * (see OSProcessHandler#checkEdtAndReadAction).
 */
class GotContentRevision(
    private val filePath: FilePath,
    private val workDir: File,
    private val relativePath: String,
    private val commandLine: GotCommandLineWrapper,
    private val revisionNumber: VcsRevisionNumber,
) : ContentRevision {

    @Throws(VcsException::class)
    override fun getContent(): String = commandLine.catAtBase(workDir, relativePath)

    override fun getFile(): FilePath = filePath

    override fun getRevisionNumber(): VcsRevisionNumber = revisionNumber
}
