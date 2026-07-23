package dev.nezzontli.gotvcs

import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.ContentRevision
import com.intellij.openapi.vcs.history.VcsRevisionNumber
import java.io.File

/**
 * Contenido de un archivo en el commit base del work tree, vía `got cat -c :base`.
 *
 * [revisionNumber] debe venir ya resuelto por quien construye esta instancia
 * (fuera del EDT). getRevisionNumber() se llama desde el renderer del árbol
 * de Commit y del título del diff, ambos en el hilo de UI: si esta clase
 * ejecutara `got info` ahí mismo, IntelliJ lo reporta como "Synchronous
 * execution on EDT" (ver OSProcessHandler#checkEdtAndReadAction).
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
