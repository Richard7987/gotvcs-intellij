package dev.nezzontli.gotvcs

import com.intellij.openapi.vcs.VcsKey
import com.intellij.openapi.vcs.VcsRootChecker
import java.io.File

class GotVcsRootChecker : VcsRootChecker() {

    override fun getSupportedVcs(): VcsKey = GotVcs.getKey()

    override fun isRoot(path: String): Boolean = File(path, ".got").isDirectory

    override fun isVcsDir(path: String): Boolean = path == ".got"
}
