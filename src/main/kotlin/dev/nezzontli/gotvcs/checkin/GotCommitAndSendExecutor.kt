package dev.nezzontli.gotvcs.checkin

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.ChangeListManagerImpl
import com.intellij.openapi.vcs.changes.CommitContext
import com.intellij.openapi.vcs.changes.CommitExecutor
import com.intellij.openapi.vcs.changes.CommitSession
import com.intellij.vcsUtil.VcsUtil
import dev.nezzontli.gotvcs.repo.GotRepositoryManager

const val COMMIT_AND_SEND_EXECUTOR_ID = "Got.Commit.And.Send"

/**
 * Commits exactly like the default "Commit" button (delegates to the same
 * CheckinEnvironment), then opens the native Push dialog (same action as
 * Ctrl+Shift+K) so the user can review and send from there.
 */
class GotCommitAndSendExecutor(
    private val project: Project,
    private val checkinEnvironment: GotCheckinEnvironment,
) : CommitExecutor {

    override fun getActionText(): String = "Commit and Send"

    override fun getId(): String = COMMIT_AND_SEND_EXECUTOR_ID

    override fun createCommitSession(context: CommitContext): CommitSession = session

    private val session = object : CommitSession {
        // execute() runs on a background thread (same as the default commit
        // action), but opening the Push dialog is a UI action and must
        // happen on the EDT -- see com.intellij.execution.process
        // .OSProcessHandler#checkEdtAndReadAction-style threading rules.
        override fun execute(changes: MutableCollection<out Change>, commitMessage: String?) {
            val message = commitMessage.orEmpty()

            // Unlike the default "Commit" button, a custom CommitExecutor's
            // session does NOT get scheduleUnversionedFilesForAddition() /
            // scheduleMissingFileForDeletion() called automatically by the
            // platform first (verified by decompiling
            // ChangesViewCommitWorkflowHandler.addUnversionedFiles(), which
            // is a no-op unless CommitSessionInfo.isVcsCommit() is true) --
            // and `got commit -m msg <path>` errors with "no changes to
            // commit" for a path that hasn't been `got add`-ed yet (verified
            // directly against the CLI). So new/deleted files must be
            // staged here ourselves before the actual commit.
            val changeListManager = ChangeListManager.getInstance(project) as ChangeListManagerImpl
            val unversionedFiles = changeListManager.unversionedFilesPaths.mapNotNull { it.virtualFile }
            val deletedPaths = changeListManager.deletedFiles.map { it.path }

            if (unversionedFiles.isNotEmpty()) {
                checkinEnvironment.scheduleUnversionedFilesForAddition(unversionedFiles.toMutableList())
            }
            if (deletedPaths.isNotEmpty()) {
                checkinEnvironment.scheduleMissingFileForDeletion(deletedPaths.toMutableList())
            }

            val trackedPaths = changes.mapNotNull { it.afterRevision?.file ?: it.beforeRevision?.file }
            val unversionedFilePaths = unversionedFiles.map { VcsUtil.getFilePath(it) }
            val allPaths: List<FilePath> = (trackedPaths + unversionedFilePaths + deletedPaths).distinct()

            val errors = checkinEnvironment.commitFilePaths(allPaths, message)
            if (errors.isNotEmpty()) {
                val group = NotificationGroupManager.getInstance().getNotificationGroup("got")
                group.createNotification(
                    "got commit failed",
                    errors.joinToString("\n") { it.message.orEmpty() },
                    NotificationType.ERROR,
                ).notify(project)
                return
            }

            // GotRepository's cached branch/revision (used to resolve the
            // Push dialog's target) is normally refreshed by the platform's
            // own post-commit VCS-root-dirty machinery, but that happens
            // asynchronously and can race with the dialog opening right
            // below -- refreshing it here ourselves, still on this
            // background thread, guarantees the dialog sees the commit that
            // was just made instead of possibly-stale cached state.
            val vcsManager = ProjectLevelVcsManager.getInstance(project)
            val repositoryManager = project.getService(GotRepositoryManager::class.java)
            val affectedRoots = allPaths.mapNotNull { vcsManager.getVcsRootFor(it) }.toSet()
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
    }
}
