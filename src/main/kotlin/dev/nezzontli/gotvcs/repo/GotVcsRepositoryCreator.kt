package dev.nezzontli.gotvcs.repo

import com.intellij.dvcs.repo.Repository
import com.intellij.dvcs.repo.VcsRepositoryCreator
import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsKey
import com.intellij.openapi.vfs.VirtualFile
import dev.nezzontli.gotvcs.GotVcs
import dev.nezzontli.gotvcs.cli.GotCommandLineWrapper
import java.io.File

class GotVcsRepositoryCreator : VcsRepositoryCreator {

    override fun getVcsKey(): VcsKey = GotVcs.getKey()

    override fun createRepositoryIfValid(project: Project, root: VirtualFile, parentDisposable: Disposable): Repository? {
        if (!File(root.path, ".got").isDirectory) return null
        return GotRepository(project, root, GotCommandLineWrapper(), parentDisposable)
    }
}
