package dev.nezzontli.gotvcs

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.VcsKey
import com.intellij.openapi.vcs.changes.ChangeProvider
import com.intellij.openapi.vcs.changes.CommitExecutor
import com.intellij.openapi.vcs.checkin.CheckinEnvironment
import com.intellij.openapi.vcs.diff.DiffProvider
import com.intellij.openapi.vcs.history.VcsHistoryProvider
import com.intellij.openapi.vcs.rollback.RollbackEnvironment
import com.intellij.openapi.vcs.update.UpdateEnvironment
import com.intellij.openapi.vcs.VcsType
import dev.nezzontli.gotvcs.checkin.GotCheckinEnvironment
import dev.nezzontli.gotvcs.checkin.GotCommitAndSendExecutor
import dev.nezzontli.gotvcs.checkin.GotRollbackEnvironment
import dev.nezzontli.gotvcs.changes.GotChangeProvider
import dev.nezzontli.gotvcs.changes.GotDiffProvider
import dev.nezzontli.gotvcs.cli.GotCommandLineWrapper
import dev.nezzontli.gotvcs.history.GotVcsHistoryProvider
import dev.nezzontli.gotvcs.update.GotUpdateEnvironment

class GotVcs(project: Project) : AbstractVcs(project, NAME) {

    private val commandLine = GotCommandLineWrapper()
    private val changeProvider = GotChangeProvider(commandLine)
    private val diffProvider = GotDiffProvider(project, commandLine)
    private val checkinEnvironment = GotCheckinEnvironment(project, commandLine)
    private val rollbackEnvironment = GotRollbackEnvironment(project, commandLine)
    private val historyProvider = GotVcsHistoryProvider(project, commandLine)
    private val updateEnvironment = GotUpdateEnvironment(commandLine)
    private val commitAndSendExecutor = GotCommitAndSendExecutor()

    override fun getDisplayName(): String = NAME

    // got is a distributed VCS (local commits, explicit `got send`), same
    // shape as Git. The platform only enables its non-modal Commit tool
    // window when every active VCS reports VcsType.distributed here --
    // AbstractVcs.getType() defaults to centralized, which silently forces
    // the old modal commit dialog and greys out the Commit tool window
    // entirely (verified via CommitModeManager.canSetNonModal() bytecode).
    override fun getType(): VcsType = VcsType.distributed

    override fun getChangeProvider(): ChangeProvider = changeProvider

    override fun getDiffProvider(): DiffProvider = diffProvider

    override fun getCheckinEnvironment(): CheckinEnvironment = checkinEnvironment

    override fun getRollbackEnvironment(): RollbackEnvironment = rollbackEnvironment

    override fun getVcsHistoryProvider(): VcsHistoryProvider = historyProvider

    override fun getUpdateEnvironment(): UpdateEnvironment = updateEnvironment

    override fun getCommitExecutors(): List<CommitExecutor> = listOf(commitAndSendExecutor)

    companion object {
        const val NAME = "got"

        private val KEY = createKey(NAME)

        @JvmStatic
        fun getKey(): VcsKey = KEY
    }
}
