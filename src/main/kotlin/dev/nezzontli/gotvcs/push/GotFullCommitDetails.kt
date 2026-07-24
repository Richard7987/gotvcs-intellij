package dev.nezzontli.gotvcs.push

import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.VcsFullCommitDetails
import com.intellij.vcs.log.VcsUser
import com.intellij.vcs.log.impl.HashImpl
import com.intellij.vcs.log.util.VcsUserUtil
import dev.nezzontli.gotvcs.cli.GotCommitObject

/**
 * Adapts a [GotCommitObject] (parsed from `got cat <commit-id>`) to the
 * Push dialog's outgoing-commits list. got has no per-file change list
 * cheaply available for an arbitrary historical commit here, so
 * [getChanges] is empty; the dialog only needs id/author/message/parents
 * to render its commit list.
 */
class GotFullCommitDetails(
    private val commitObject: GotCommitObject,
    private val root: VirtualFile,
) : VcsFullCommitDetails {

    private val hash: Hash = HashImpl.build(commitObject.commitId)
    private val author: VcsUser = VcsUserUtil.createUser(commitObject.authorName, commitObject.authorEmail)

    override fun getId(): Hash = hash
    override fun getParents(): List<Hash> = commitObject.parents.map { HashImpl.build(it) }
    override fun getTimestamp(): Long = commitObject.authorTimestamp * 1000L
    override fun getRoot(): VirtualFile = root
    override fun getSubject(): String = commitObject.message.lineSequence().firstOrNull().orEmpty()
    override fun getAuthor(): VcsUser = author
    override fun getCommitter(): VcsUser = author
    override fun getAuthorTime(): Long = commitObject.authorTimestamp * 1000L
    override fun getCommitTime(): Long = commitObject.authorTimestamp * 1000L
    override fun getFullMessage(): String = commitObject.message
    override fun getChanges(): Collection<Change> = emptyList()
    override fun getChanges(parent: Int): Collection<Change> = emptyList()
}
