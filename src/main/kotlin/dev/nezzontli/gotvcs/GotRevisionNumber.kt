package dev.nezzontli.gotvcs

import com.intellij.openapi.vcs.history.VcsRevisionNumber

/** Identifica el commit base de un work tree got (hash resuelto vía `got info`). */
data class GotRevisionNumber(private val commitId: String) : VcsRevisionNumber {

    override fun asString(): String = commitId.take(12)

    override fun compareTo(other: VcsRevisionNumber?): Int =
        if (other is GotRevisionNumber) commitId.compareTo(other.commitId) else -1
}
