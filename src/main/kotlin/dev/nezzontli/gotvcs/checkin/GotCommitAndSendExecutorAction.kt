package dev.nezzontli.gotvcs.checkin

import com.intellij.openapi.vcs.changes.actions.BaseCommitExecutorAction

/** Renders as the secondary (gray) toolbar button, next to the primary blue "Commit" button. */
class GotCommitAndSendExecutorAction : BaseCommitExecutorAction() {
    override val executorId: String = COMMIT_AND_SEND_EXECUTOR_ID
}
