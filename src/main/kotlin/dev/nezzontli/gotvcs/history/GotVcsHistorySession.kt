package dev.nezzontli.gotvcs.history

import com.intellij.openapi.vcs.history.VcsAbstractHistorySession
import com.intellij.openapi.vcs.history.VcsFileRevision
import com.intellij.openapi.vcs.history.VcsHistorySession
import com.intellij.openapi.vcs.history.VcsRevisionNumber

class GotVcsHistorySession(
    revisions: List<VcsFileRevision>,
    private val baseRevision: VcsRevisionNumber,
) : VcsAbstractHistorySession(revisions, baseRevision) {

    override fun calcCurrentRevisionNumber(): VcsRevisionNumber = baseRevision

    override fun copy(): VcsHistorySession = GotVcsHistorySession(revisionList, baseRevision)
}
