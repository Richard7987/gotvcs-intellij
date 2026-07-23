package dev.nezzontli.gotvcs

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.history.DiffFromHistoryHandler
import com.intellij.openapi.vcs.history.VcsAbstractHistorySession
import com.intellij.openapi.vcs.history.VcsAppendableHistorySessionPartner
import com.intellij.openapi.vcs.history.VcsDependentHistoryComponents
import com.intellij.openapi.vcs.history.VcsFileRevision
import com.intellij.openapi.vcs.history.VcsHistoryProvider
import com.intellij.openapi.vcs.history.VcsHistorySession
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.util.ui.ColumnInfo
import java.io.File

class GotVcsHistoryProvider(
    private val project: Project,
    private val commandLine: GotCommandLineWrapper,
) : VcsHistoryProvider {

    override fun getUICustomization(session: VcsHistorySession, forShortcutRegistration: javax.swing.JComponent) =
        VcsDependentHistoryComponents.createOnlyColumns(emptyArray<ColumnInfo<*, *>>())

    override fun getAdditionalActions(refresher: Runnable): Array<AnAction> = emptyArray()

    override fun isDateOmittable(): Boolean = false

    override fun getHelpId(): String? = null

    override fun supportsHistoryForDirectories(): Boolean = true

    override fun getHistoryDiffHandler(): DiffFromHistoryHandler? = null

    override fun canShowHistoryFor(virtualFile: com.intellij.openapi.vfs.VirtualFile): Boolean = true

    @Throws(VcsException::class)
    override fun createSessionFor(filePath: FilePath): VcsHistorySession {
        val vcsRoot = ProjectLevelVcsManager.getInstance(project).getVcsRootFor(filePath)
            ?: throw VcsException("$filePath no pertenece a ningún work tree got")
        val workDir = File(vcsRoot.path)
        val relativePath = VfsUtilCore.getRelativePath(filePath.virtualFile ?: vcsRoot, vcsRoot)
            ?: throw VcsException("No se pudo resolver la ruta relativa de $filePath en $workDir")

        val entries = commandLine.log(workDir, relativePath.ifEmpty { null })
        val revisions: List<VcsFileRevision> = entries.map { GotFileRevision(workDir, relativePath, commandLine, it) }
        val baseRevision = GotRevisionNumber(commandLine.baseCommit(workDir))
        return GotVcsHistorySession(revisions, baseRevision)
    }

    @Throws(VcsException::class)
    override fun reportAppendableHistory(filePath: FilePath, partner: VcsAppendableHistorySessionPartner) {
        try {
            val session = createSessionFor(filePath) as VcsAbstractHistorySession
            partner.reportCreatedEmptySession(session)
            session.revisionList.forEach { partner.acceptRevision(it) }
        } catch (e: VcsException) {
            partner.reportException(e)
        }
    }
}
