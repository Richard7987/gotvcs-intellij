package dev.nezzontli.gotvcs.update

import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.update.UpdateSession

class GotUpdateSession(private val exceptions: MutableList<VcsException>) : UpdateSession {

    override fun getExceptions(): MutableList<VcsException> = exceptions

    override fun onRefreshFilesCompleted() = Unit

    override fun isCanceled(): Boolean = false
}
