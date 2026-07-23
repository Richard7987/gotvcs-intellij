package dev.nezzontli.gotvcs

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.VcsKey
import com.intellij.openapi.vcs.changes.ChangeProvider
import com.intellij.openapi.vcs.checkin.CheckinEnvironment
import com.intellij.openapi.vcs.diff.DiffProvider
import com.intellij.openapi.vcs.history.VcsHistoryProvider
import com.intellij.openapi.vcs.rollback.RollbackEnvironment
import com.intellij.openapi.vcs.update.UpdateEnvironment

class GotVcs(project: Project) : AbstractVcs(project, NAME) {

    private val commandLine = GotCommandLineWrapper()
    private val changeProvider = GotChangeProvider(commandLine)
    private val diffProvider = GotDiffProvider(project, commandLine)
    private val checkinEnvironment = GotCheckinEnvironment(project, commandLine)
    private val rollbackEnvironment = GotRollbackEnvironment(project, commandLine)
    private val historyProvider = GotVcsHistoryProvider(project, commandLine)
    private val updateEnvironment = GotUpdateEnvironment(commandLine)

    override fun getDisplayName(): String = NAME

    override fun getChangeProvider(): ChangeProvider = changeProvider

    override fun getDiffProvider(): DiffProvider = diffProvider

    override fun getCheckinEnvironment(): CheckinEnvironment = checkinEnvironment

    override fun getRollbackEnvironment(): RollbackEnvironment = rollbackEnvironment

    override fun getVcsHistoryProvider(): VcsHistoryProvider = historyProvider

    override fun getUpdateEnvironment(): UpdateEnvironment = updateEnvironment

    companion object {
        const val NAME = "got"

        private val KEY = createKey(NAME)

        @JvmStatic
        fun getKey(): VcsKey = KEY
    }
}
