package dev.nezzontli.gotvcs.checkin

import com.intellij.openapi.vcs.changes.CommitContext
import com.intellij.openapi.vcs.changes.CommitExecutor
import com.intellij.openapi.vcs.changes.CommitSession

const val COMMIT_AND_SEND_EXECUTOR_ID = "Got.Commit.And.Send"

/**
 * Flags the CommitContext and returns CommitSession.VCS_COMMIT (the "run
 * the normal commit" sentinel) rather than reimplementing commit, so the
 * platform's own handling of unversioned/deleted files still applies.
 * GotCheckinEnvironment checks PUSH_AFTER_COMMIT_KEY once the commit
 * succeeds and opens the native Push dialog from there.
 */
class GotCommitAndSendExecutor : CommitExecutor {

    override fun getActionText(): String = "Commit and Send"

    override fun getId(): String = COMMIT_AND_SEND_EXECUTOR_ID

    override fun useDefaultAction(): Boolean = false

    override fun requiresSyncCommitChecks(): Boolean = true

    override fun createCommitSession(context: CommitContext): CommitSession {
        context.putUserData(PUSH_AFTER_COMMIT_KEY, true)
        return CommitSession.VCS_COMMIT
    }
}
