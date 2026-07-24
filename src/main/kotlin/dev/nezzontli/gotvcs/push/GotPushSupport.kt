package dev.nezzontli.gotvcs.push

import com.intellij.dvcs.push.PushSupport
import com.intellij.dvcs.push.PushTargetPanel
import com.intellij.dvcs.repo.RepositoryManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.AbstractVcs
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import dev.nezzontli.gotvcs.GotVcs
import dev.nezzontli.gotvcs.cli.GotCommandLineWrapper
import dev.nezzontli.gotvcs.repo.GotRepository
import dev.nezzontli.gotvcs.repo.GotRepositoryManager

class GotPushSupport(private val project: Project) : PushSupport<GotRepository, GotPushSource, GotPushTarget>() {

    private val commandLine = GotCommandLineWrapper()
    private val pusher = GotPusher(commandLine)
    private val outgoingCommitsProvider = GotOutgoingCommitsProvider(commandLine)

    override fun getVcs(): AbstractVcs = ProjectLevelVcsManager.getInstance(project).findVcsByName(GotVcs.NAME)!!

    override fun getPusher() = pusher

    override fun getOutgoingCommitsProvider() = outgoingCommitsProvider

    override fun getDefaultTarget(repository: GotRepository): GotPushTarget? {
        val branch = repository.currentBranchName ?: return null
        return GotPushTarget("origin", branch)
    }

    /**
     * PushSupport's two-arg overload has a *non-abstract* default body that
     * always returns null (unlike the one-arg version, which is abstract) --
     * the platform calls this one, not the one-arg override above, so
     * without this it silently marks every repo model with an "empty
     * target" error and skips loading outgoing commits entirely.
     */
    override fun getDefaultTarget(repository: GotRepository, source: GotPushSource): GotPushTarget? =
        getDefaultTarget(repository)

    override fun getSource(repository: GotRepository): GotPushSource =
        GotPushSource(repository.currentBranchName ?: "")

    override fun getRepositoryManager(): RepositoryManager<GotRepository> =
        project.getService(GotRepositoryManager::class.java)

    override fun createTargetPanel(
        repository: GotRepository,
        source: GotPushSource,
        target: GotPushTarget?,
    ): PushTargetPanel<GotPushTarget> = GotPushTargetPanel(target ?: GotPushTarget("origin", source.presentation))

    override fun isForcePushAllowed(repository: GotRepository, target: GotPushTarget): Boolean = true

    override fun isSilentForcePushAllowed(target: GotPushTarget): Boolean = false

    override fun saveSilentForcePushTarget(target: GotPushTarget) = Unit
}
