package dev.nezzontli.gotvcs

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.VcsKey
import com.intellij.openapi.vcs.changes.ChangeProvider
import com.intellij.openapi.vcs.diff.DiffProvider

class GotVcs(project: Project) : AbstractVcs(project, NAME) {

    private val commandLine = GotCommandLineWrapper()
    private val changeProvider = GotChangeProvider(commandLine)
    private val diffProvider = GotDiffProvider(project, commandLine)

    override fun getDisplayName(): String = NAME

    override fun getChangeProvider(): ChangeProvider = changeProvider

    override fun getDiffProvider(): DiffProvider = diffProvider

    companion object {
        const val NAME = "got"

        private val KEY = createKey(NAME)

        @JvmStatic
        fun getKey(): VcsKey = KEY
    }
}
