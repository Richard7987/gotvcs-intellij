package dev.nezzontli.gotvcs

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
import java.io.File

/**
 * Acción simple de menú para "got send" (el equivalente a "push"). No usa el
 * diálogo nativo de Push de IntelliJ (Ctrl+Shift+K): eso requiere un
 * PushSupport respaldado por Repository/RepositoryManager completos, similar
 * en tamaño a todo lo demás de este plugin junto. Un botón que corre
 * `got send` y reporta el resultado cubre el caso de uso real sin esa
 * complejidad.
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
                    group.createNotification("got send: cambios enviados correctamente", NotificationType.INFORMATION)
                        .notify(project)
                } else {
                    group.createNotification("got send falló", errors.joinToString("\n"), NotificationType.ERROR)
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
