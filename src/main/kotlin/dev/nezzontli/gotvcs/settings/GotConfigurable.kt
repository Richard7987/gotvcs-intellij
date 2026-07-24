package dev.nezzontli.gotvcs.settings

import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.JTextField

class GotConfigurable : Configurable {

    private var panel: JPanel? = null
    private var gotBinaryField: TextFieldWithBrowseButton? = null
    private var sshAuthSockField: JTextField? = null

    override fun getDisplayName(): String = "got"

    override fun createComponent(): JComponent {
        val binaryField = TextFieldWithBrowseButton().apply {
            addBrowseFolderListener(
                "got Binary Path",
                "Leave empty to auto-detect (/run/current-system/sw/bin/got or PATH)",
                null,
                FileChooserDescriptorFactory.createSingleFileDescriptor(),
            )
        }
        val sockField = JTextField()

        gotBinaryField = binaryField
        sshAuthSockField = sockField

        val built = FormBuilder.createFormBuilder()
            .addLabeledComponent("got binary:", binaryField)
            .addTooltip("Leave empty to auto-detect (/run/current-system/sw/bin/got or PATH)")
            .addLabeledComponent("SSH_AUTH_SOCK:", sockField)
            .addTooltip("Leave empty to use the IDE process's own value, falling back to /run/user/<uid>/gnupg/S.gpg-agent.ssh")
            .addComponentFillVertically(JPanel(), 0)
            .panel

        panel = built
        reset()
        return built
    }

    override fun isModified(): Boolean {
        val settings = GotSettingsState.getInstance()
        return gotBinaryField?.text.orEmpty() != settings.gotBinaryPath ||
            sshAuthSockField?.text.orEmpty() != settings.sshAuthSock
    }

    override fun apply() {
        val settings = GotSettingsState.getInstance()
        settings.gotBinaryPath = gotBinaryField?.text.orEmpty()
        settings.sshAuthSock = sshAuthSockField?.text.orEmpty()
    }

    override fun reset() {
        val settings = GotSettingsState.getInstance()
        gotBinaryField?.text = settings.gotBinaryPath
        sshAuthSockField?.text = settings.sshAuthSock
    }

    override fun disposeUIResources() {
        panel = null
        gotBinaryField = null
        sshAuthSockField = null
    }
}
