package dev.nezzontli.gotvcs.checkin

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.rollback.RollbackEnvironment
import com.intellij.openapi.vcs.rollback.RollbackProgressListener
import com.intellij.openapi.vfs.VirtualFile
import dev.nezzontli.gotvcs.cli.GotCommandLineWrapper
import java.io.File

class GotRollbackEnvironment(
    private val project: Project,
    private val commandLine: GotCommandLineWrapper,
) : RollbackEnvironment {

    override fun getRollbackOperationName(): String = "Revert"

    override fun rollbackChanges(
        changes: MutableList<out Change>,
        exceptions: MutableList<VcsException>,
        listener: RollbackProgressListener,
    ) {
        listener.determinate()
        val filePaths = changes.mapNotNull { it.afterRevision?.file ?: it.beforeRevision?.file }
        revertPaths(filePaths, exceptions, listener)
    }

    override fun rollbackMissingFileDeletion(
        files: MutableList<out FilePath>,
        exceptions: MutableList<in VcsException>,
        listener: RollbackProgressListener,
    ) {
        listener.determinate()
        revertPaths(files, exceptions, listener)
    }

    override fun rollbackModifiedWithoutCheckout(
        files: MutableList<out VirtualFile>,
        exceptions: MutableList<in VcsException>,
        listener: RollbackProgressListener,
    ) = Unit

    private fun revertPaths(
        filePaths: List<FilePath>,
        exceptions: MutableList<in VcsException>,
        listener: RollbackProgressListener,
    ) {
        val vcsManager = ProjectLevelVcsManager.getInstance(project)
        val byRoot = mutableMapOf<File, MutableList<String>>()
        for (filePath in filePaths) {
            val vcsRoot = vcsManager.getVcsRootFor(filePath) ?: continue
            val workDir = File(vcsRoot.path)
            val relativePath = workDir.toPath().relativize(File(filePath.path).toPath()).toString()
            byRoot.getOrPut(workDir) { mutableListOf() }.add(relativePath)
        }
        for ((workDir, paths) in byRoot) {
            try {
                commandLine.revert(workDir, paths)
                paths.forEach { listener.accept(File(workDir, it)) }
            } catch (e: VcsException) {
                exceptions.add(e)
            }
        }
    }
}
