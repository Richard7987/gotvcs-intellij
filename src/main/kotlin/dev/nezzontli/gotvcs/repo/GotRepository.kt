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

class GotRepository(
    private val project: Project,
    private val root: VirtualFile,
    private val commandLine: GotCommandLineWrapper,
    parentDisposable: Disposable,
) : Repository {

    private var disposed = false

    init {
        Disposer.register(parentDisposable, this)
    }

    override fun getRoot(): VirtualFile = root

    override fun getPresentableUrl(): String = root.presentableUrl

    override fun getProject(): Project = project

    override fun getState(): Repository.State = Repository.State.NORMAL

    override fun getCurrentBranchName(): String? = try {
        commandLine.currentBranch(File(root.path))
    } catch (e: VcsException) {
        null
    }

    override fun getVcs(): AbstractVcs = ProjectLevelVcsManager.getInstance(project).findVcsByName(GotVcs.NAME)!!

    override fun getCurrentRevision(): String? = try {
        commandLine.baseCommit(File(root.path))
    } catch (e: VcsException) {
        null
    }

    override fun isFresh(): Boolean = false

    override fun update() = Unit

    override fun toLogString(): String = "GotRepository{root=$root}"

    override fun dispose() {
        disposed = true
    }

    override fun isDisposed(): Boolean = disposed
}
