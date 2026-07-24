package dev.nezzontli.gotvcs.changes

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.ContentRevision
import com.intellij.openapi.vcs.diff.DiffProvider
import com.intellij.openapi.vcs.diff.ItemLatestState
import com.intellij.openapi.vcs.history.VcsRevisionNumber
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcsUtil.VcsUtil
import dev.nezzontli.gotvcs.cli.GotCommandLineWrapper
import java.io.File

/**
 * Feeds the modified-lines gutter and "Show Diff" actions from the work
 * tree's base commit (`got info` + `got cat -c :base`). Only compares
 * against the current base; comparing against arbitrary historical
 * revisions is out of scope for this provider.
 */
class GotDiffProvider(
    private val project: Project,
    private val commandLine: GotCommandLineWrapper,
) : DiffProvider {

    private fun rootAndRelativePath(file: VirtualFile): Pair<File, String>? {
        val vcsRoot = ProjectLevelVcsManager.getInstance(project).getVcsRootFor(file) ?: return null
        val relativePath = VfsUtilCore.getRelativePath(file, vcsRoot) ?: return null
        return File(vcsRoot.path) to relativePath
    }

    private fun baseRevisionOrNull(workDir: File): VcsRevisionNumber? = try {
        GotRevisionNumber(commandLine.baseCommit(workDir))
    } catch (e: VcsException) {
        null
    }

    override fun getCurrentRevision(file: VirtualFile): VcsRevisionNumber? {
        val (workDir, _) = rootAndRelativePath(file) ?: return null
        return baseRevisionOrNull(workDir)
    }

    override fun getLastRevision(virtualFile: VirtualFile): ItemLatestState? {
        val (workDir, _) = rootAndRelativePath(virtualFile) ?: return null
        val revision = baseRevisionOrNull(workDir) ?: return null
        return ItemLatestState(revision, true, true)
    }

    override fun getLastRevision(filePath: FilePath): ItemLatestState? {
        val virtualFile = filePath.virtualFile ?: return null
        return getLastRevision(virtualFile)
    }

    override fun createFileContent(revisionNumber: VcsRevisionNumber, selectedFile: VirtualFile): ContentRevision? {
        val (workDir, relativePath) = rootAndRelativePath(selectedFile) ?: return null
        val filePath = VcsUtil.getFilePath(selectedFile)
        return GotContentRevision(filePath, workDir, relativePath, commandLine, revisionNumber)
    }

    override fun getLatestCommittedRevision(vcsRoot: VirtualFile): VcsRevisionNumber? =
        baseRevisionOrNull(File(vcsRoot.path))
}
