package dev.nezzontli.gotvcs.repo

import com.intellij.dvcs.ui.DvcsStatusWidget
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory

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

    class Factory : StatusBarWidgetFactory {
        override fun getId(): String = WIDGET_ID

        override fun getDisplayName(): String = "got Branch Widget"

        override fun isAvailable(project: Project): Boolean =
            project.getService(GotRepositoryManager::class.java).repositories.isNotEmpty()

        override fun createWidget(project: Project): StatusBarWidget = GotBranchWidget(project)

        override fun isEnabledByDefault(): Boolean = true
    }
}
