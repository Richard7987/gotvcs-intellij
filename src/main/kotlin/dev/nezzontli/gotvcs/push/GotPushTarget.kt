package dev.nezzontli.gotvcs.push

import com.intellij.dvcs.push.PushTarget

/**
 * got does not expose a cheap way to tell ahead of time whether a send would
 * be a no-op, so [hasSomethingToPush] always returns true: `got send` itself
 * reports "nothing to send" if that turns out to be the case.
 */
class GotPushTarget(val remoteName: String, val branch: String) : PushTarget {
    override fun hasSomethingToPush(): Boolean = true
    override fun getPresentation(): String = "$remoteName/$branch"
}
