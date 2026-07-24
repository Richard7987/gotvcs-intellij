package dev.nezzontli.gotvcs.push

import com.intellij.dvcs.push.OutgoingCommitsProvider
import com.intellij.dvcs.push.OutgoingResult
import com.intellij.dvcs.push.PushSpec
import dev.nezzontli.gotvcs.repo.GotRepository

/**
 * got has no cheap local equivalent of "commits not yet on the remote"
 * without contacting the server first, so this reports an empty preview
 * rather than an inaccurate one. The Push dialog still works; it just
 * doesn't list outgoing commits ahead of time.
 */
class GotOutgoingCommitsProvider : OutgoingCommitsProvider<GotRepository, GotPushSource, GotPushTarget>() {
    override fun getOutgoingCommits(
        repository: GotRepository,
        pushSpec: PushSpec<GotPushSource, GotPushTarget>,
        initial: Boolean,
    ): OutgoingResult = OutgoingResult(emptyList(), emptyList())
}
