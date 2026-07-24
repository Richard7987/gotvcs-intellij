package dev.nezzontli.gotvcs.log

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcs.log.VcsLogRefresher
import java.util.concurrent.ConcurrentHashMap

/**
 * Central "something changed" signal for a got root: marks it dirty (so the
 * Commit/Local Changes panel re-scans instead of waiting on the platform's
 * own lazily-triggered refresh) and forwards to the Log tab's refresher, if
 * one is currently registered (i.e. the Log tab is open). Called after any
 * of our own commit/push/update operations.
 */
class GotVcsRefreshNotifier(private val project: Project) {

    private val logRefreshers = ConcurrentHashMap<VirtualFile, VcsLogRefresher>()

    fun registerLogRefresher(root: VirtualFile, refresher: VcsLogRefresher) {
        logRefreshers[root] = refresher
    }

    fun unregisterLogRefresher(root: VirtualFile) {
        logRefreshers.remove(root)
    }

    fun notifyChanged(root: VirtualFile) {
        VcsDirtyScopeManager.getInstance(project).rootDirty(root)
        logRefreshers[root]?.refresh(root)
    }
}
