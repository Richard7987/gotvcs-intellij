package dev.nezzontli.gotvcs.history

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.history.DiffFromHistoryHandler
import com.intellij.openapi.vcs.history.VcsAppendableHistorySessionPartner
import com.intellij.openapi.vcs.history.VcsDependentHistoryComponents
import com.intellij.openapi.vcs.history.VcsFileRevision
import com.intellij.openapi.vcs.history.VcsHistoryProvider
import com.intellij.openapi.vcs.history.VcsHistorySession
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.ui.ColumnInfo
import dev.nezzontli.gotvcs.changes.GotRevisionNumber
import dev.nezzontli.gotvcs.cli.GotCommandLineWrapper
import java.io.File

class GotVcsHistoryProvider(
    private val project: Project,
    private val commandLine: GotCommandLineWrapper,
) : VcsHistoryProvider {

    private val logger = Logger.getInstance(GotVcsHistoryProvider::class.java)

    override fun getUICustomization(session: VcsHistorySession, forShortcutRegistration: javax.swing.JComponent) =
        VcsDependentHistoryComponents.createOnlyColumns(emptyArray<ColumnInfo<*, *>>())

    override fun getAdditionalActions(refresher: Runnable): Array<AnAction> = emptyArray()

    override fun isDateOmittable(): Boolean = false

    override fun getHelpId(): String? = null

    override fun supportsHistoryForDirectories(): Boolean = true

    override fun getHistoryDiffHandler(): DiffFromHistoryHandler? = null

    override fun canShowHistoryFor(virtualFile: VirtualFile): Boolean = true

    private data class RootAndPath(val workDir: File, val relativePath: String)

    @Throws(VcsException::class)
    private fun resolveRootAndPath(filePath: FilePath): RootAndPath {
        val vcsRoot = ProjectLevelVcsManager.getInstance(project).getVcsRootFor(filePath)
            ?: throw VcsException("$filePath does not belong to any got work tree")
        val workDir = File(vcsRoot.path)
        val relativePath = VfsUtilCore.getRelativePath(filePath.virtualFile ?: vcsRoot, vcsRoot)
            ?: throw VcsException("Could not resolve the relative path of $filePath under $workDir")
        return RootAndPath(workDir, relativePath)
    }

    @Throws(VcsException::class)
    override fun createSessionFor(filePath: FilePath): VcsHistorySession {
        try {
            val (workDir, relativePath) = resolveRootAndPath(filePath)
            val entries = commandLine.log(workDir, relativePath.ifEmpty { null })
            val revisions: List<VcsFileRevision> = entries.map { GotFileRevision(workDir, relativePath, commandLine, it) }
            val baseRevision = GotRevisionNumber(commandLine.baseCommit(workDir))
            return GotVcsHistorySession(revisions, baseRevision)
        } catch (e: VcsException) {
            throw e
        } catch (e: Exception) {
            logger.warn("Failed to build got history for $filePath", e)
            throw VcsException("${e.javaClass.simpleName}: ${e.message ?: "no message"}", e)
        }
    }

    @Throws(VcsException::class)
    override fun reportAppendableHistory(filePath: FilePath, partner: VcsAppendableHistorySessionPartner) {
        try {
            val (workDir, relativePath) = resolveRootAndPath(filePath)
            val baseRevision = GotRevisionNumber(commandLine.baseCommit(workDir))

            // reportCreatedEmptySession expects a session with no revisions
            // yet: partner.acceptRevision() below appends to that same
            // session on its own, so passing an already-populated one here
            // would duplicate every entry.
            val session = GotVcsHistorySession(emptyList(), baseRevision)
            partner.reportCreatedEmptySession(session)

            val entries = commandLine.log(workDir, relativePath.ifEmpty { null })
            for (entry in entries) {
                partner.acceptRevision(GotFileRevision(workDir, relativePath, commandLine, entry))
            }
        } catch (e: VcsException) {
            partner.reportException(e)
        } catch (e: Exception) {
            logger.warn("Failed to build got history (appendable) for $filePath", e)
            partner.reportException(VcsException("${e.javaClass.simpleName}: ${e.message ?: "no message"}", e))
        }
    }
}
