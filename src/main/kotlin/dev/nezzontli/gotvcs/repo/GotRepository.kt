package dev.nezzontli.gotvcs.repo

import com.intellij.dvcs.repo.Repository
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vfs.VirtualFile
import dev.nezzontli.gotvcs.GotVcs
import dev.nezzontli.gotvcs.cli.GotCommandLineWrapper
import java.io.File

/**
 * getCurrentBranchName()/getCurrentRevision() are called from UI code (the
 * branch widget, the Push dialog's tree model) that runs on the EDT, so they
 * cannot shell out to `got info` synchronously (IntelliJ's
 * OSProcessHandler#checkEdtAndReadAction guard trips on that). State is
 * read once eagerly and only re-read in update(), which the platform calls
 * off the EDT during a repository refresh.
 */
class GotRepository(
    private val project: Project,
    private val root: VirtualFile,
    private val commandLine: GotCommandLineWrapper,
    parentDisposable: Disposable,
) : Repository {

    private var disposed = false

    @Volatile
    private var branch: String? = null

    @Volatile
    private var revision: String? = null

    init {
        Disposer.register(parentDisposable, this)
        refreshState()
    }

    private fun refreshState() {
        val workDir = File(root.path)
        branch = try {
            commandLine.currentBranch(workDir)
        } catch (e: VcsException) {
            null
        }
        revision = try {
            commandLine.baseCommit(workDir)
        } catch (e: VcsException) {
            null
        }
    }

    override fun getRoot(): VirtualFile = root

    override fun getPresentableUrl(): String = root.presentableUrl

    override fun getProject(): Project = project

    override fun getState(): Repository.State = Repository.State.NORMAL

    override fun getCurrentBranchName(): String? = branch

    override fun getVcs(): AbstractVcs = ProjectLevelVcsManager.getInstance(project).findVcsByName(GotVcs.NAME)!!

    override fun getCurrentRevision(): String? = revision

    override fun isFresh(): Boolean = false

    override fun update() = refreshState()

    override fun toLogString(): String = "GotRepository{root=$root}"

    override fun dispose() {
        disposed = true
    }

    override fun isDisposed(): Boolean = disposed
}
