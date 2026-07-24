package dev.nezzontli.gotvcs.push

import com.intellij.dvcs.push.OutgoingCommitsProvider
import com.intellij.dvcs.push.OutgoingResult
import com.intellij.dvcs.push.PushSpec
import com.intellij.dvcs.push.VcsError
import com.intellij.openapi.vcs.VcsException
import dev.nezzontli.gotvcs.cli.GotCommandLineWrapper
import dev.nezzontli.gotvcs.repo.GotRepository
import java.io.File

/**
 * Lists commits reachable from the current branch but not yet known to be on
 * `refs/remotes/<remote>/<branch>` locally (via `got log -x`). This reflects
 * the last `got fetch`, not a live round-trip to the server, same as Git's
 * own outgoing-commits preview.
 */
class GotOutgoingCommitsProvider(private val commandLine: GotCommandLineWrapper) :
    OutgoingCommitsProvider<GotRepository, GotPushSource, GotPushTarget>() {

    override fun getOutgoingCommits(
        repository: GotRepository,
        pushSpec: PushSpec<GotPushSource, GotPushTarget>,
        initial: Boolean,
    ): OutgoingResult {
        val workDir = File(repository.root.path)
        val target = pushSpec.target
        return try {
            val remoteHash = commandLine.remoteBranchHash(workDir, target.remoteName, target.branch)
            val commits = commandLine.outgoingCommitIds(workDir, remoteHash)
                .map { id -> GotFullCommitDetails(commandLine.catCommit(workDir, id), repository.root) }
            OutgoingResult(commits, emptyList())
        } catch (e: VcsException) {
            OutgoingResult(emptyList(), listOf(VcsError(e.message)))
        }
    }
}
