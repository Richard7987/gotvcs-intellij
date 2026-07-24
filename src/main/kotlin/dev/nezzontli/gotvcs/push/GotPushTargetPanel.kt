package dev.nezzontli.gotvcs.push

import com.intellij.dvcs.push.PushTargetPanel
import com.intellij.dvcs.push.ui.PushTargetEditorListener
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.SimpleTextAttributes

/**
 * A read-only target panel: got sends to whichever remote/branch got.conf
 * and the work tree resolve by default, so there is nothing to edit here
 * (unlike Git, which lets you retarget a push to an arbitrary branch).
 */
class GotPushTargetPanel(private val target: GotPushTarget) : PushTargetPanel<GotPushTarget>() {

    override fun render(renderer: ColoredTreeCellRenderer, isSelected: Boolean, isActive: Boolean, text: String?) {
        renderer.append(target.presentation, SimpleTextAttributes.REGULAR_ATTRIBUTES)
    }

    override fun getValue(): GotPushTarget = target

    override fun fireOnCancel() = Unit

    override fun fireOnChange() = Unit

    override fun verify(): ValidationInfo? = null

    override fun setFireOnChangeAction(action: Runnable) = Unit

    override fun addTargetEditorListener(listener: PushTargetEditorListener) = Unit
}
