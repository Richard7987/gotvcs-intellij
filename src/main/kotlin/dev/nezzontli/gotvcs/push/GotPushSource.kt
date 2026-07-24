package dev.nezzontli.gotvcs.push

import com.intellij.dvcs.push.PushSource

class GotPushSource(private val branch: String) : PushSource {
    override fun getPresentation(): String = branch
}
