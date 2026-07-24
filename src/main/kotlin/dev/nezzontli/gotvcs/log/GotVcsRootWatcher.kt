package dev.nezzontli.gotvcs.log

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import dev.nezzontli.gotvcs.repo.GotRepositoryManager

/**
 * Detects got state changes made outside the IDE (e.g. `got commit`/`got
 * send` run in a terminal): commit/push/update already ping
 * GotVcsRefreshNotifier directly when done through the plugin, but external
 * changes only show up as raw VFS events under a work tree's `.got/`
 * directory, with no higher-level "VCS changed" signal to hook into.
 */
class GotVcsRootWatcher : ProjectActivity {
    override suspend fun execute(project: Project) {
        project.messageBus.connect().subscribe(
            VirtualFileManager.VFS_CHANGES,
            object : BulkFileListener {
                override fun after(events: MutableList<out VFileEvent>) {
                    val repositoryManager = project.getService(GotRepositoryManager::class.java)
                    val repositories = repositoryManager.repositories
                    if (repositories.isEmpty()) return

                    val notifier = project.getService(GotVcsRefreshNotifier::class.java)
                    val changedRoots = mutableSetOf<VirtualFile>()
                    for (event in events) {
                        val path = event.path
                        for (repository in repositories) {
                            if (repository.root in changedRoots) continue
                            if (path.startsWith("${repository.root.path}/.got/")) {
                                changedRoots.add(repository.root)
                            }
                        }
                    }
                    changedRoots.forEach { notifier.notifyChanged(it) }
                }
            },
        )
    }
}
