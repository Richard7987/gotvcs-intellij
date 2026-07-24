package dev.nezzontli.gotvcs.log

import com.intellij.ui.JBColor
import com.intellij.vcs.log.VcsRefType
import java.awt.Color

/** got only really has local branches, remote-tracking branches, and tags -- no stashes or other ref kinds. */
enum class GotRefType(private val branch: Boolean, private val color: Color) : VcsRefType {
    LOCAL_BRANCH(true, JBColor(0x59A869, 0x59A869)),
    REMOTE_BRANCH(true, JBColor(0x8888C1, 0x8888C1)),
    TAG(false, JBColor(0xD6BF55, 0xD6BF55)),
    ;

    override fun isBranch(): Boolean = branch

    override fun getBackgroundColor(): Color = color
}
