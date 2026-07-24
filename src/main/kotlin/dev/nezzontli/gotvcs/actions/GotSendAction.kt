package dev.nezzontli.gotvcs.actions

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vfs.VirtualFile
import dev.nezzontli.gotvcs.GotVcs
import dev.nezzontli.gotvcs.cli.GotCommandLineWrapper
import java.io.File

/**
 * Simple menu action for `got send` (the equivalent of "push"). This does
 * not hook into IntelliJ's native Push dialog (Ctrl+Shift+K): that requires
 * a PushSupport backed by a full Repository/RepositoryManager model,
 * comparable in size to the rest of this plugin combined. A single action
 * that runs `got send` and reports the result covers the real use case
 * without that complexity.
 */
class GotSendAction : AnAction() {

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project?.let { gotRoots(it).isNotEmpty() } ?: false
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val roots = gotRoots(project)
        if (roots.isEmpty()) return

        val commandLine = GotCommandLineWrapper()
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "got send", true) {
            override fun run(indicator: ProgressIndicator) {
                val errors = mutableListOf<String>()
                for (root in roots) {
                    try {
                        commandLine.send(File(root.path))
                    } catch (ex: VcsException) {
                        errors.add("${root.path}: ${ex.message}")
                    }
                }
                val group = NotificationGroupManager.getInstance().getNotificationGroup("got")
                if (errors.isEmpty()) {
                    group.createNotification("got send: changes sent successfully", NotificationType.INFORMATION)
                        .notify(project)
                } else {
                    group.createNotification("got send failed", errors.joinToString("\n"), NotificationType.ERROR)
                        .notify(project)
                }
            }
        })
    }

    private fun gotRoots(project: Project): List<VirtualFile> {
        val vcsManager = ProjectLevelVcsManager.getInstance(project)
        val gotVcs = vcsManager.findVcsByName(GotVcs.NAME) ?: return emptyList()
        return vcsManager.getRootsUnderVcs(gotVcs).toList()
    }
}
