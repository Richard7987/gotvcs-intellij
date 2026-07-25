package dev.nezzontli.gotvcs.checkout

import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.CheckoutProvider
import com.intellij.openapi.vcs.ui.VcsCloneComponent
import com.intellij.openapi.vcs.ui.cloneDialog.VcsCloneDialogComponentStateListener

/** Adds "Got" to the Version control dropdown of the Clone Repository dialog. */
class GotCheckoutProvider : CheckoutProvider {

    override fun getVcsName(): String = "Got"

    override fun buildVcsCloneComponent(
        project: Project,
        modalityState: ModalityState,
        listener: VcsCloneDialogComponentStateListener,
    ): VcsCloneComponent = GotCloneDialogComponent(project, modalityState, listener)
}
