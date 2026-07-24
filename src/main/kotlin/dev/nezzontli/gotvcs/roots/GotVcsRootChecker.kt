package dev.nezzontli.gotvcs.roots

import com.intellij.openapi.vcs.VcsKey
import com.intellij.openapi.vcs.VcsRootChecker
import com.intellij.openapi.vfs.VirtualFile
import dev.nezzontli.gotvcs.GotVcs
import java.io.File

class GotVcsRootChecker : VcsRootChecker() {

    override fun getSupportedVcs(): VcsKey = GotVcs.getKey()

    override fun isRoot(file: VirtualFile): Boolean = File(file.path, ".got").isDirectory

    override fun isVcsDir(path: String): Boolean = path == ".got"
}
