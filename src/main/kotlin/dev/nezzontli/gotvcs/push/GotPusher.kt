package dev.nezzontli.gotvcs.push

import com.intellij.dvcs.push.PushSpec
import com.intellij.dvcs.push.Pusher
import com.intellij.dvcs.push.VcsPushOptionValue
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.vcs.VcsException
import dev.nezzontli.gotvcs.cli.GotCommandLineWrapper
import dev.nezzontli.gotvcs.repo.GotRepository
import java.io.File

class GotPusher(private val commandLine: GotCommandLineWrapper) :
    Pusher<GotRepository, GotPushSource, GotPushTarget>() {

    override fun push(
        pushSpecs: MutableMap<GotRepository, PushSpec<GotPushSource, GotPushTarget>>,
        forcePushOptionValue: VcsPushOptionValue?,
        force: Boolean,
    ) {
        val errors = mutableListOf<String>()
        for (repository in pushSpecs.keys) {
            try {
                commandLine.send(File(repository.root.path))
            } catch (e: VcsException) {
                errors.add("${repository.root.path}: ${e.message}")
            }
        }

        val project = pushSpecs.keys.firstOrNull()?.project ?: return
        val group = NotificationGroupManager.getInstance().getNotificationGroup("got")
        if (errors.isEmpty()) {
            group.createNotification("got send: changes sent successfully", NotificationType.INFORMATION).notify(project)
        } else {
            group.createNotification("got send failed", errors.joinToString("\n"), NotificationType.ERROR).notify(project)
        }
    }
}
