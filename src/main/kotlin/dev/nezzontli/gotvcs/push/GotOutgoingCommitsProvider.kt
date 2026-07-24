package dev.nezzontli.gotvcs.push

import com.intellij.dvcs.push.OutgoingCommitsProvider
import com.intellij.dvcs.push.OutgoingResult
import com.intellij.dvcs.push.PushSpec
import com.intellij.dvcs.push.VcsError
import com.intellij.openapi.diagnostic.Logger
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

    private val logger = Logger.getInstance(GotOutgoingCommitsProvider::class.java)

    override fun getOutgoingCommits(
        repository: GotRepository,
        pushSpec: PushSpec<GotPushSource, GotPushTarget>,
        initial: Boolean,
    ): OutgoingResult {
        val workDir = File(repository.root.path)
        val target = pushSpec.target
        logger.warn("got outgoing commits: workDir=$workDir remote=${target.remoteName} branch=${target.branch}")
        return try {
            val remoteHash = commandLine.remoteBranchHash(workDir, target.remoteName, target.branch)
            logger.warn("got outgoing commits: remoteHash=$remoteHash")
            val ids = commandLine.outgoingCommitIds(workDir, remoteHash)
            logger.warn("got outgoing commits: ids=$ids")
            val commits = ids.map { id -> GotFullCommitDetails(commandLine.catCommit(workDir, id), repository.root) }
            OutgoingResult(commits, emptyList())
        } catch (e: VcsException) {
            logger.warn("got outgoing commits failed", e)
            OutgoingResult(emptyList(), listOf(VcsError(e.message)))
        } catch (e: Exception) {
            logger.error("got outgoing commits failed unexpectedly", e)
            OutgoingResult(emptyList(), listOf(VcsError(e.message ?: e.toString())))
        }
    }
}
