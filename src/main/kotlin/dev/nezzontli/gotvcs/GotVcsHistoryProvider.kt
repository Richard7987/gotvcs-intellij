package dev.nezzontli.gotvcs

import com.intellij.openapi.actionSystem.AnAction
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
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.util.ui.ColumnInfo
import java.io.File

class GotVcsHistoryProvider(
    private val project: Project,
    private val commandLine: GotCommandLineWrapper,
) : VcsHistoryProvider {

    private val LOG = Logger.getInstance(GotVcsHistoryProvider::class.java)

    override fun getUICustomization(session: VcsHistorySession, forShortcutRegistration: javax.swing.JComponent) =
        VcsDependentHistoryComponents.createOnlyColumns(emptyArray<ColumnInfo<*, *>>())

    override fun getAdditionalActions(refresher: Runnable): Array<AnAction> = emptyArray()

    override fun isDateOmittable(): Boolean = false

    override fun getHelpId(): String? = null

    override fun supportsHistoryForDirectories(): Boolean = true

    override fun getHistoryDiffHandler(): DiffFromHistoryHandler? = null

    override fun canShowHistoryFor(virtualFile: com.intellij.openapi.vfs.VirtualFile): Boolean = true

    private data class RootAndPath(val workDir: File, val relativePath: String)

    @Throws(VcsException::class)
    private fun resolveRootAndPath(filePath: FilePath): RootAndPath {
        val vcsRoot = ProjectLevelVcsManager.getInstance(project).getVcsRootFor(filePath)
            ?: throw VcsException("$filePath no pertenece a ningún work tree got")
        val workDir = File(vcsRoot.path)
        val relativePath = VfsUtilCore.getRelativePath(filePath.virtualFile ?: vcsRoot, vcsRoot)
            ?: throw VcsException("No se pudo resolver la ruta relativa de $filePath en $workDir")
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
            // El panel de historial solo muestra e.message (a veces como
            // "Unknown error" si es null); loggeamos el stack completo para
            // poder diagnosticar fallos que no sean VcsException nuestras.
            LOG.warn("Fallo al construir el historial got para $filePath", e)
            throw VcsException("${e.javaClass.simpleName}: ${e.message ?: "sin mensaje"}", e)
        }
    }

    @Throws(VcsException::class)
    override fun reportAppendableHistory(filePath: FilePath, partner: VcsAppendableHistorySessionPartner) {
        try {
            val (workDir, relativePath) = resolveRootAndPath(filePath)
            val baseRevision = GotRevisionNumber(commandLine.baseCommit(workDir))

            // reportCreatedEmptySession espera una sesión REALMENTE vacía: el
            // panel de "Show History" usa este camino (no createSessionFor).
            // partner.acceptRevision() ya actualiza esta misma sesión por su
            // cuenta -- llamar además session.appendRevision() duplicaba cada
            // commit (confirmado en vivo dos veces: primero por pasar una
            // sesión ya poblada, y de nuevo acá por agregarlas por ambos
            // caminos a la vez).
            val session = GotVcsHistorySession(emptyList(), baseRevision)
            partner.reportCreatedEmptySession(session)

            val entries = commandLine.log(workDir, relativePath.ifEmpty { null })
            for (entry in entries) {
                partner.acceptRevision(GotFileRevision(workDir, relativePath, commandLine, entry))
            }
        } catch (e: VcsException) {
            partner.reportException(e)
        } catch (e: Exception) {
            LOG.warn("Fallo al construir el historial got (appendable) para $filePath", e)
            partner.reportException(VcsException("${e.javaClass.simpleName}: ${e.message ?: "sin mensaje"}", e))
        }
    }
}
