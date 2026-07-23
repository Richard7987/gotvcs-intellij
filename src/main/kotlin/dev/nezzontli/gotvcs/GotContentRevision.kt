package dev.nezzontli.gotvcs

import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.ContentRevision
import com.intellij.openapi.vcs.history.VcsRevisionNumber
import java.io.File

/** Contenido de un archivo en el commit base del work tree, vía `got cat -c :base`. */
class GotContentRevision(
    private val filePath: FilePath,
    private val workDir: File,
    private val relativePath: String,
    private val commandLine: GotCommandLineWrapper,
) : ContentRevision {

    @Throws(VcsException::class)
    override fun getContent(): String = commandLine.catAtBase(workDir, relativePath)

    override fun getFile(): FilePath = filePath

    override fun getRevisionNumber(): VcsRevisionNumber = try {
        GotRevisionNumber(commandLine.baseCommit(workDir))
    } catch (e: VcsException) {
        VcsRevisionNumber.NULL
    }
}
