package dev.nezzontli.gotvcs

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.VcsKey
import com.intellij.openapi.vcs.changes.ChangeProvider

class GotVcs(project: Project) : AbstractVcs(project, NAME) {

    private val commandLine = GotCommandLineWrapper()
    private val changeProvider = GotChangeProvider(commandLine)

    override fun getDisplayName(): String = NAME

    override fun getChangeProvider(): ChangeProvider = changeProvider

    companion object {
        const val NAME = "got"

        private val KEY = createKey(NAME)

        @JvmStatic
        fun getKey(): VcsKey = KEY
    }
}
