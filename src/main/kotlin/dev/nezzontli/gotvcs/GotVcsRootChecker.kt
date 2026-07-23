package dev.nezzontli.gotvcs

import com.intellij.openapi.vcs.VcsKey
import com.intellij.openapi.vcs.VcsRootChecker
import com.intellij.openapi.vfs.VirtualFile
import java.io.File

class GotVcsRootChecker : VcsRootChecker() {

    override fun getSupportedVcs(): VcsKey = GotVcs.getKey()

    // isRoot(String) está deprecado (scheduled for removal, confirmado con
    // el Plugin Verifier) a favor de esta sobrecarga con VirtualFile.
    override fun isRoot(file: VirtualFile): Boolean = File(file.path, ".got").isDirectory

    override fun isVcsDir(path: String): Boolean = path == ".got"
}
