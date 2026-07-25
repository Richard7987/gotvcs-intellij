package dev.nezzontli.gotvcs.checkout

import com.intellij.dvcs.DvcsRememberedInputs
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.RoamingType
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

/** Backs the URL history dropdown and remembered parent directory in the Clone dialog's "Got" tab. */
@State(name = "GotRememberedInputs", storages = [Storage("vcs-inputs.xml", roamingType = RoamingType.DISABLED)])
class GotRememberedInputs : DvcsRememberedInputs(), PersistentStateComponent<DvcsRememberedInputs.State> {
    companion object {
        @JvmStatic
        fun getInstance(): GotRememberedInputs =
            ApplicationManager.getApplication().getService(GotRememberedInputs::class.java)
    }
}
