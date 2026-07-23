package dev.nezzontli.gotvcs

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

/**
 * Rutas configurables desde Settings > Version Control > got. Vacío ("")
 * significa "usar la detección automática" (ver GotCommandLineWrapper):
 * binario got en /run/current-system/sw/bin/got o PATH, y SSH_AUTH_SOCK de
 * System.getenv() o el socket fijo de gpg-agent.
 */
@State(name = "GotVcsSettings", storages = [Storage("gotvcs.xml")])
class GotSettingsState : PersistentStateComponent<GotSettingsState> {

    var gotBinaryPath: String = ""
    var sshAuthSock: String = ""

    override fun getState(): GotSettingsState = this

    override fun loadState(state: GotSettingsState) {
        XmlSerializerUtil.copyBean(state, this)
    }

    companion object {
        @JvmStatic
        fun getInstance(): GotSettingsState =
            ApplicationManager.getApplication().getService(GotSettingsState::class.java)
    }
}
