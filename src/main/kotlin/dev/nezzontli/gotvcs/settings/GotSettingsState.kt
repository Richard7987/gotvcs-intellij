package dev.nezzontli.gotvcs.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.util.xmlb.XmlSerializerUtil

/**
 * Configurable paths from Settings > Version Control > got. An empty string
 * means "use automatic detection" (see GotCommandLineWrapper): the got
 * binary in /run/current-system/sw/bin/got or PATH, and SSH_AUTH_SOCK from
 * the environment or the conventional gpg-agent socket path.
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
