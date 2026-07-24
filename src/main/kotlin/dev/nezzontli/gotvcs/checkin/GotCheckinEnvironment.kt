package dev.nezzontli.gotvcs.checkin

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.CommitContext
import com.intellij.openapi.vcs.checkin.CheckinEnvironment
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import dev.nezzontli.gotvcs.cli.GotCommandLineWrapper
import java.io.File

/**
 * got has no git-style staging index (aside from `got stage`, which this
 * plugin does not use): instead, exactly the paths selected in the Commit
 * panel are committed via `got commit -m msg <paths>`.
 */
class GotCheckinEnvironment(
    private val project: Project,
    private val commandLine: GotCommandLineWrapper,
) : CheckinEnvironment {

    override fun getHelpId(): String? = null

    override fun getCheckinOperationName(): String = "Commit"

    override fun isRefreshAfterCommitNeeded(): Boolean = true

    // CheckinEnvironment.commit() has three overloads with default bodies
    // that delegate 2-args -> CommitContext -> NullableFunction, the last of
    // which is a no-op. The platform calls the CommitContext overload
    // directly rather than the 2-arg one, so both must be overridden or the
    // commit silently never runs while the IDE still reports success.
    override fun commit(changes: MutableList<out Change>, commitMessage: String): MutableList<VcsException> =
        doCommit(changes, commitMessage)

    override fun commit(
        changes: MutableList<out Change>,
        commitMessage: String,
        commitContext: CommitContext,
        feedback: MutableSet<in String>,
    ): MutableList<VcsException> = doCommit(changes, commitMessage)

    private fun doCommit(changes: List<Change>, commitMessage: String): MutableList<VcsException> =
        commitFilePaths(changes.mapNotNull { it.afterRevision?.file ?: it.beforeRevision?.file }, commitMessage)

    /**
     * Exposed for GotCommitAndSendExecutor: unlike the default "Commit"
     * button (a VCS_COMMIT session, for which the platform automatically
     * calls scheduleUnversionedFilesForAddition()/scheduleMissingFileForDeletion()
     * before committing), a custom CommitExecutor's session is on its own
     * for that -- confirmed by decompiling ChangesViewCommitWorkflowHandler,
     * whose addUnversionedFiles() is a no-op unless CommitSessionInfo.isVcsCommit()
     * is true. This lets that caller commit an explicit path list (tracked
     * changes plus whatever it scheduled for add/deletion itself) in one go.
     */
    fun commitFilePaths(filePaths: List<FilePath>, commitMessage: String): MutableList<VcsException> {
        val exceptions = mutableListOf<VcsException>()
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
