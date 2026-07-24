package dev.nezzontli.gotvcs.changes

import com.intellij.openapi.vcs.history.VcsRevisionNumber

/** Identifies a got work tree's base commit (resolved via `got info`). */
data class GotRevisionNumber(private val commitId: String) : VcsRevisionNumber {

    override fun asString(): String = commitId.take(12)

    override fun compareTo(other: VcsRevisionNumber?): Int =
        if (other is GotRevisionNumber) commitId.compareTo(other.commitId) else -1
}
