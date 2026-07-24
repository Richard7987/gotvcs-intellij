package dev.nezzontli.gotvcs.repo

import com.intellij.dvcs.ui.DvcsStatusWidget
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.util.BaseListPopupStep
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import dev.nezzontli.gotvcs.cli.GotLogEntry

private const val WIDGET_ID = "got.branch.widget"

class GotBranchWidget(project: Project) : DvcsStatusWidget<GotRepository>(project, "got") {

    override fun ID(): String = WIDGET_ID

    override fun copy(): StatusBarWidget = GotBranchWidget(project)

    override fun guessCurrentRepository(project: Project, selectedFile: VirtualFile?): GotRepository? {
        val manager = project.getService(GotRepositoryManager::class.java)
        selectedFile?.let { manager.getRepositoryForFileQuick(it) }?.let { return it }
        return manager.repositories.firstOrNull()
    }

    override fun getFullBranchName(repository: GotRepository): String = repository.currentBranchName ?: "got"

    override fun isMultiRoot(project: Project): Boolean = project.getService(GotRepositoryManager::class.java).moreThanOneRoot()

    override fun rememberRecentRoot(path: String) = Unit

    /**
     * A quick, non-interactive preview of recent commits (from GotRepository's
     * precomputed log, itself refreshed off-EDT). The full commit graph is a
     * separate, larger "Log" tab feature not implemented yet.
     */
    override fun getWidgetPopup(project: Project, repository: GotRepository): JBPopup {
        val entries = repository.getRecentLog()
        val step = object : BaseListPopupStep<GotLogEntry>("Recent got commits", entries) {
            override fun getTextFor(value: GotLogEntry): String {
                val subject = value.message.lineSequence().firstOrNull().orEmpty()
                return "${value.commitId.take(8)}  ${value.date}  $subject"
            }

            override fun isSelectable(value: GotLogEntry): Boolean = false

            override fun onChosen(selectedValue: GotLogEntry?, finalChoice: Boolean) = null
        }
        return JBPopupFactory.getInstance().createListPopup(step)
    }

    class Factory : StatusBarWidgetFactory {
        override fun getId(): String = WIDGET_ID

        override fun getDisplayName(): String = "got Branch Widget"

        override fun isAvailable(project: Project): Boolean =
            project.getService(GotRepositoryManager::class.java).repositories.isNotEmpty()

        override fun createWidget(project: Project): StatusBarWidget = GotBranchWidget(project)

        override fun isEnabledByDefault(): Boolean = true
    }
}
