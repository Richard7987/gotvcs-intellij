package dev.nezzontli.gotvcs.checkout

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.vcs.CheckoutProvider
import com.intellij.openapi.vcs.ui.VcsCloneComponent
import com.intellij.openapi.vcs.ui.cloneDialog.VcsCloneDialogComponentStateListener
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.wm.impl.welcomeScreen.cloneableProjects.CloneableProjectsService
import com.intellij.ui.JBColor
import com.intellij.ui.TextFieldWithHistory
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.FormBuilder
import dev.nezzontli.gotvcs.GotVcs
import dev.nezzontli.gotvcs.cli.GotCommandLineWrapper
import java.awt.Dimension
import java.io.File
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import javax.swing.JComponent
import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

private const val SSH_URL_PREFIX = "ssh://"

/**
 * Backs the "Got" entry of the Version control dropdown in the Clone
 * Repository dialog. This is a plain [VcsCloneComponent] implementation
 * (URL field + directory field) rather than a subclass of the platform's
 * `DvcsCloneDialogComponent` (which Git4Idea uses for the same UI) --
 * that base class and its `MainPanelCustomizer` are `@ApiStatus.Internal`
 * and fail `verifyPlugin`.
 */
class GotCloneDialogComponent(
    private val project: Project,
    @Suppress("UNUSED_PARAMETER") modalityState: ModalityState,
    private var dialogStateListener: VcsCloneDialogComponentStateListener,
) : VcsCloneComponent {

    private val urlField = TextFieldWithHistory().apply {
        setHistorySize(-1)
        history = GotRememberedInputs.getInstance().visitedUrls
        // The combo box's own max-size hint otherwise caps it well short of
        // the panel's width, unlike the (already full-width) directory field.
        preferredSize = Dimension(400, preferredSize.height)
        maximumSize = Dimension(Int.MAX_VALUE, preferredSize.height)
    }

    private val directoryField = TextFieldWithBrowseButton().apply {
        addBrowseFolderListener(
            project,
            FileChooserDescriptorFactory.createSingleFolderDescriptor().withTitle("Destination Directory"),
        )
    }

    private val errorLabel = JBLabel().apply {
        foreground = JBColor.RED
        isVisible = false
    }

    private val panel = FormBuilder.createFormBuilder()
        .addLabeledComponent("URL:", urlField)
        .addLabeledComponent("Directory:", directoryField)
        .addComponent(errorLabel)
        .panel

    // Tracks the last directory this component filled in on its own, so
    // typing a URL keeps suggesting a directory until the user overrides it.
    private var lastSuggestedDirectory: String? = null

    private var listenersAttached = false

    init {
        directoryField.text = suggestedParentDir()
    }

    override fun getView(): JComponent = panel

    override fun getPreferredFocusedComponent(): JComponent = urlField

    override fun isOkEnabled(): Boolean = doValidateAll().isEmpty()

    override fun doValidateAll(): List<ValidationInfo> {
        val url = urlField.text.trim()
        if (url.isEmpty()) return listOf(ValidationInfo("URL cannot be empty", urlField))
        if (!url.startsWith(SSH_URL_PREFIX)) {
            return listOf(
                ValidationInfo("Got only supports cloning over ssh:// (e.g. ssh://user@host:port/path/repo.git)", urlField)
            )
        }

        val directory = directoryField.text.trim()
        if (directory.isEmpty()) return listOf(ValidationInfo("Directory cannot be empty", directoryField.textField))
        val workTreeDir = Paths.get(directory).toAbsolutePath()
        if (Files.exists(workTreeDir)) {
            return listOf(ValidationInfo("Directory already exists", directoryField.textField))
        }
        if (Files.exists(bareRepoDirFor(workTreeDir))) {
            return listOf(ValidationInfo("${bareRepoDirFor(workTreeDir)} already exists", directoryField.textField))
        }
        return emptyList()
    }

    override fun onComponentSelected(dialogStateListener: VcsCloneDialogComponentStateListener) {
        this.dialogStateListener = dialogStateListener
        updateState()
        if (listenersAttached) return
        listenersAttached = true

        urlField.textEditor.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = onUrlChanged()
            override fun removeUpdate(e: DocumentEvent) = onUrlChanged()
            override fun changedUpdate(e: DocumentEvent) = onUrlChanged()
            private fun onUrlChanged() {
                if (directoryField.text.isBlank() || directoryField.text == lastSuggestedDirectory) {
                    directoryField.text = suggestedParentDir()
                }
                updateState()
            }
        })
        directoryField.textField.document.addDocumentListener(object : DocumentListener {
            override fun insertUpdate(e: DocumentEvent) = updateState()
            override fun removeUpdate(e: DocumentEvent) = updateState()
            override fun changedUpdate(e: DocumentEvent) = updateState()
        })
    }

    // Only surface a validation message once both fields have real content --
    // the "cannot be empty" errors from doValidateAll() would otherwise flash
    // on every keystroke while a field is still being typed into.
    private fun updateState() {
        val errors = doValidateAll()
        dialogStateListener.onOkActionEnabled(errors.isEmpty())
        val bothFieldsFilled = urlField.text.isNotBlank() && directoryField.text.isNotBlank()
        val message = errors.firstOrNull()?.message.takeIf { bothFieldsFilled }
        errorLabel.text = message.orEmpty()
        errorLabel.isVisible = message != null
    }

    // CloneableProjectsService (the same path Git4Idea uses) is what adds the
    // cloned directory to Recent Projects once CloneTask.run() returns
    // SUCCESS; a plain Task.Backgroundable wouldn't register it anywhere.
    override fun doClone(listener: CheckoutProvider.Listener) {
        val cloneUrl = urlField.text.trim()
        val workTreeDir = Paths.get(directoryField.text.trim()).toAbsolutePath()
        val parentDir = workTreeDir.parent
        val bareRepoDir = bareRepoDirFor(workTreeDir)

        GotRememberedInputs.getInstance().addUrl(cloneUrl)
        GotRememberedInputs.getInstance().setCloneParentDir(parentDir.toString())

        val cloneProject = project
        val task = object : CloneableProjectsService.CloneTask {
            override fun taskInfo() = CloneableProjectsService.CloneTaskInfo(
                "Cloning $cloneUrl...",
                "Cancel cloning $cloneUrl",
                "Clone",
                "Clone the repository",
                "Clone failed",
                "Clone canceled",
                "Stop cloning?",
                "Stop cloning $cloneUrl?",
            )

            override fun run(indicator: ProgressIndicator): CloneableProjectsService.CloneStatus {
                indicator.isIndeterminate = true
                val commandLine = GotCommandLineWrapper()
                try {
                    Files.createDirectories(parentDir)
                    commandLine.clone(parentDir.toFile(), cloneUrl, bareRepoDir.toFile())
                    commandLine.checkout(parentDir.toFile(), bareRepoDir.toFile(), workTreeDir.toFile())
                } catch (e: Exception) {
                    NotificationGroupManager.getInstance().getNotificationGroup("got")
                        .createNotification("got clone failed", e.message.orEmpty(), NotificationType.ERROR)
                        .notify(cloneProject)
                    return CloneableProjectsService.CloneStatus.FAILURE
                }

                LocalFileSystem.getInstance().refreshAndFindFileByIoFile(workTreeDir.toFile())
                listener.directoryCheckedOut(workTreeDir.toFile(), GotVcs.getKey())
                listener.checkoutCompleted()
                return CloneableProjectsService.CloneStatus.SUCCESS
            }
        }
        CloneableProjectsService.getInstance().runCloneTask(workTreeDir, task)
    }

    override fun dispose() {}

    /** got clone always produces a bare repository; the work tree is a separate `got checkout` step. */
    private fun bareRepoDirFor(workTreeDir: Path): Path =
        workTreeDir.resolveSibling("${workTreeDir.fileName}.git")

    private fun suggestedParentDir(): String {
        val parent = GotRememberedInputs.getInstance().cloneParentDir?.takeIf { it.isNotBlank() }
            ?: (System.getProperty("user.home") + File.separator + "IdeaProjects")
        val repoName = urlField.text.trim().trimEnd('/').substringAfterLast('/').removeSuffix(".git")
        val suggestion = if (repoName.isBlank()) parent else Paths.get(parent, repoName).toString()
        lastSuggestedDirectory = suggestion
        return suggestion
    }
}
