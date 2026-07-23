package dev.nezzontli.gotvcs

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.CommitContext
import com.intellij.openapi.vcs.checkin.CheckinEnvironment
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import java.io.File

/**
 * got no tiene un índice/staging equivalente al de git en el flujo normal
 * (aparte de `got stage`, que no usamos aquí): en vez de eso, se commitean
 * exactamente los paths seleccionados por el usuario en el panel de Commit,
 * vía `got commit -m msg <paths>`. Ver got(1), sección commit.
 */
class GotCheckinEnvironment(
    private val project: Project,
    private val commandLine: GotCommandLineWrapper,
) : CheckinEnvironment {

    override fun getHelpId(): String? = null

    override fun getCheckinOperationName(): String = "Commit"

    override fun isRefreshAfterCommitNeeded(): Boolean = true

    // CheckinEnvironment.commit() tiene 3 sobrecargas con implementación
    // default: 2-args -> delega en la de CommitContext -> delega en la de
    // NullableFunction -> devuelve null (no-op). La plataforma invoca
    // directamente la de CommitContext, no la de 2 args -- si solo se
    // sobreescribe esta última, el commit real nunca corre y el IDE igual
    // reporta éxito (verificado en vivo con javap -c sobre la interfaz).
    override fun commit(changes: MutableList<out Change>, commitMessage: String): MutableList<VcsException> =
        doCommit(changes, commitMessage)

    override fun commit(
        changes: MutableList<out Change>,
        commitMessage: String,
        commitContext: CommitContext,
        feedback: MutableSet<in String>,
    ): MutableList<VcsException> = doCommit(changes, commitMessage)

    private fun doCommit(changes: List<Change>, commitMessage: String): MutableList<VcsException> {
        val exceptions = mutableListOf<VcsException>()
        val filePaths = changes.mapNotNull { it.afterRevision?.file ?: it.beforeRevision?.file }
        for ((workDir, paths) in groupByRoot(filePaths)) {
            try {
                commandLine.commit(workDir, commitMessage, paths)
            } catch (e: VcsException) {
                exceptions.add(e)
            }
        }
        return exceptions
    }

    override fun scheduleMissingFileForDeletion(files: MutableList<out FilePath>): MutableList<VcsException> {
        val exceptions = mutableListOf<VcsException>()
        for ((workDir, paths) in groupByRoot(files)) {
            try {
                commandLine.remove(workDir, paths)
            } catch (e: VcsException) {
                exceptions.add(e)
            }
        }
        return exceptions
    }

    override fun scheduleUnversionedFilesForAddition(files: MutableList<out VirtualFile>): MutableList<VcsException> {
        val exceptions = mutableListOf<VcsException>()
        val vcsManager = ProjectLevelVcsManager.getInstance(project)
        val byRoot = mutableMapOf<File, MutableList<String>>()
        for (file in files) {
            val vcsRoot = vcsManager.getVcsRootFor(file) ?: continue
            val relativePath = VfsUtilCore.getRelativePath(file, vcsRoot) ?: continue
            byRoot.getOrPut(File(vcsRoot.path)) { mutableListOf() }.add(relativePath)
        }
        for ((workDir, paths) in byRoot) {
            try {
                commandLine.add(workDir, paths)
            } catch (e: VcsException) {
                exceptions.add(e)
            }
        }
        return exceptions
    }

    private fun groupByRoot(filePaths: List<FilePath>): Map<File, List<String>> {
        val vcsManager = ProjectLevelVcsManager.getInstance(project)
        val byRoot = mutableMapOf<File, MutableList<String>>()
        for (filePath in filePaths) {
            val vcsRoot = vcsManager.getVcsRootFor(filePath) ?: continue
            val workDir = File(vcsRoot.path)
            val relativePath = workDir.toPath().relativize(File(filePath.path).toPath()).toString()
            byRoot.getOrPut(workDir) { mutableListOf() }.add(relativePath)
        }
        return byRoot
    }
}
