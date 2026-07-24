package dev.nezzontli.gotvcs.checkin

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.CommitContext
import com.intellij.openapi.vcs.checkin.CheckinEnvironment
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import dev.nezzontli.gotvcs.cli.GotCommandLineWrapper
import dev.nezzontli.gotvcs.log.GotVcsRefreshNotifier
import dev.nezzontli.gotvcs.repo.GotRepositoryManager
import java.io.File

/** Set by GotCommitAndSendExecutor on the CommitContext it's handed; read here once the commit succeeds. */
val PUSH_AFTER_COMMIT_KEY: Key<Boolean> = Key.create("Got.Commit.PushAfterCommit")

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
    ): MutableList<VcsException> {
        val exceptions = doCommit(changes, commitMessage)
        if (exceptions.isEmpty() && commitContext.getUserData(PUSH_AFTER_COMMIT_KEY) == true) {
            openPushDialogAfterCommit(changes)
        }
        return exceptions
    }

    /**
     * GotCommitAndSendExecutor's session is CommitSession.VCS_COMMIT itself
     * (see that class) so this same, already-correct commit() path runs --
     * unversioned/deleted files included -- exactly as for the plain
     * "Commit" button; PUSH_AFTER_COMMIT_KEY just tells us to also open the
     * Push dialog afterwards. Opening it is a UI action and must happen on
     * the EDT, unlike commit() itself which may run on a background thread.
     */
    private fun openPushDialogAfterCommit(changes: List<Change>) {
        val vcsManager = ProjectLevelVcsManager.getInstance(project)
        val repositoryManager = project.getService(GotRepositoryManager::class.java)
        val affectedRoots = changes.mapNotNull { it.afterRevision?.file ?: it.beforeRevision?.file }
            .mapNotNull { vcsManager.getVcsRootFor(it) }
            .toSet()
        for (root in affectedRoots) {
            repositoryManager.getRepositoryForRoot(root)?.update()
        }

        ApplicationManager.getApplication().invokeLater {
            val pushAction = ActionManager.getInstance().getAction("Vcs.Push") ?: return@invokeLater
            val dataContext = SimpleDataContext.getProjectContext(project)
            val event = AnActionEvent.createEvent(
                dataContext,
                Presentation(),
                "GotCommitAndSend",
                ActionUiKind.NONE,
                null,
            )
            ActionUtil.performAction(pushAction, event)
        }
    }

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
        notifyRootsChanged(filePaths)
        return exceptions
    }

    /** Marks the affected roots dirty and pings the Log tab's refresher, if one is registered, so neither needs a manual reload after a commit. */
    private fun notifyRootsChanged(filePaths: List<FilePath>) {
        val vcsManager = ProjectLevelVcsManager.getInstance(project)
        val notifier = project.getService(GotVcsRefreshNotifier::class.java)
        filePaths.mapNotNull { vcsManager.getVcsRootFor(it) }.toSet().forEach { notifier.notifyChanged(it) }
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
