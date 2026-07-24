package dev.nezzontli.gotvcs.log

import com.intellij.openapi.Disposable
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.VcsKey
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.Consumer
import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.VcsLogProperties
import com.intellij.vcs.log.VcsLogProvider
import com.intellij.vcs.log.VcsLogRefManager
import com.intellij.vcs.log.VcsRef
import com.intellij.vcs.log.VcsUser
import com.intellij.vcs.log.impl.HashImpl
import com.intellij.vcs.log.util.VcsUserUtil
import com.intellij.vcsUtil.VcsUtil
import dev.nezzontli.gotvcs.GotVcs
import dev.nezzontli.gotvcs.cli.GotCommandLineWrapper
import dev.nezzontli.gotvcs.cli.GotChangedPath
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * Backs the platform's Log tab entirely by shelling out to `got` (no local
 * index/cache beyond GotCommandLineWrapper's in-memory commit cache) -- same
 * philosophy as the rest of this plugin: no daemon, no persisted state.
 */
class GotVcsLogProvider(private val commandLine: GotCommandLineWrapper = GotCommandLineWrapper()) : VcsLogProvider {

    override val supportedVcs: VcsKey = GotVcs.getKey()

    override val referenceManager: VcsLogRefManager = GotVcsLogRefManager()

    // getCurrentBranch()/getCurrentUser() are called from EDT-bound UI code
    // (CurrentBranchHighlighter.update(), on every visible-pack refresh -- and
    // likely an equivalent "my commits" highlighter for getCurrentUser), so
    // neither can shell out to `got` synchronously -- same class of bug fixed
    // earlier for GotRepository/GotBranchWidget. readAllHashes() always runs
    // off the EDT (it's the Log tab's own background data loading), and
    // always runs before those highlighters can fire, so precomputing both
    // here and serving them from cache is safe.
    private val currentBranchCache = ConcurrentHashMap<String, String>()
    private val currentUserCache = ConcurrentHashMap<String, VcsUser>()

    override fun readAllHashes(root: VirtualFile, commitConsumer: Consumer<in com.intellij.vcs.log.TimedVcsCommit>): VcsLogProvider.LogData {
        val workDir = File(root.path)
        try {
            val branch = commandLine.currentBranch(workDir)
            if (branch != null) {
                currentBranchCache[root.path] = branch
                val tipId = commandLine.refs(workDir).firstOrNull { !it.isRemote && !it.isTag && it.name == branch }?.hash
                val commitObject = tipId?.let { commandLine.catCommit(workDir, it) }
                if (commitObject != null) {
                    currentUserCache[root.path] = VcsUserUtil.createUser(commitObject.authorName, commitObject.authorEmail)
                }
            } else {
                currentBranchCache.remove(root.path)
            }
        } catch (e: VcsException) {
            currentBranchCache.remove(root.path)
        }
        val ids = commandLine.allCommitIds(workDir)
        val users = mutableSetOf<VcsUser>()
        for (id in ids) {
            val commitObject = commandLine.catCommit(workDir, id)
            users.add(VcsUserUtil.createUser(commitObject.authorName, commitObject.authorEmail))
            commitConsumer.consume(GotFullCommitDetails(commitObject, root))
        }
        val refs = buildRefs(workDir, root)
        return SimpleLogData(refs, users)
    }

    override fun readMetadata(
        root: VirtualFile,
        hashes: List<String>,
        consumer: Consumer<in com.intellij.vcs.log.VcsCommitMetadata>,
    ) {
        val workDir = File(root.path)
        for (id in hashes) {
            consumer.consume(GotFullCommitDetails(commandLine.catCommit(workDir, id), root))
        }
    }

    override fun readFullDetails(
        root: VirtualFile,
        hashes: List<String>,
        commitConsumer: Consumer<in com.intellij.vcs.log.VcsFullCommitDetails>,
    ) {
        val workDir = File(root.path)
        for (id in hashes) {
            val commitObject = commandLine.catCommit(workDir, id)
            val parentId = commitObject.parents.firstOrNull()
            val changes = try {
                commandLine.changedPaths(workDir, id).map { toChange(workDir, id, parentId, it) }
            } catch (e: VcsException) {
                emptyList()
            }
            commitConsumer.consume(GotFullCommitDetails(commitObject, root, changes))
        }
    }

    private fun toChange(workDir: File, commitId: String, parentId: String?, changed: GotChangedPath): Change {
        val filePath = VcsUtil.getFilePath(File(workDir, changed.path), false)
        val before = parentId?.let { GotCommitContentRevision(filePath, workDir, changed.path, commandLine, it) }
        val after = GotCommitContentRevision(filePath, workDir, changed.path, commandLine, commitId)
        return when (changed.code) {
            'A' -> Change(null, after)
            'D' -> Change(before, null)
            else -> Change(before, after)
        }
    }

    private fun buildRefs(workDir: File, root: VirtualFile): Set<VcsRef> {
        val entries = try {
            commandLine.refs(workDir)
        } catch (e: VcsException) {
            emptyList()
        }
        return entries.map { entry ->
            val type = when {
                entry.isTag -> GotRefType.TAG
                entry.isRemote -> GotRefType.REMOTE_BRANCH
                else -> GotRefType.LOCAL_BRANCH
            }
            SimpleVcsRef(HashImpl.build(entry.hash), entry.name, type, root)
        }.toSet()
    }

    /** Best-effort: the tip commit's author, precomputed by readAllHashes(). got repos in practice have a single author, so this is accurate enough. */
    override fun getCurrentUser(root: VirtualFile): VcsUser =
        currentUserCache[root.path] ?: VcsUserUtil.createUser("", "")

    override fun getContainingBranches(root: VirtualFile, commitHash: Hash): Collection<String> = emptyList()

    override fun <T> getPropertyValue(property: VcsLogProperties.VcsLogProperty<T>): T? {
        @Suppress("UNCHECKED_CAST")
        return when (property) {
            VcsLogProperties.LIGHTWEIGHT_BRANCHES -> true as T
            else -> false as T
        }
    }

    override fun getCurrentBranch(root: VirtualFile): String? = currentBranchCache[root.path]

    override fun subscribeToRootRefreshEvents(
        roots: Collection<VirtualFile>,
        refresher: com.intellij.vcs.log.VcsLogRefresher,
    ): Disposable = Disposable { }

    private class SimpleLogData(
        override val refs: Set<VcsRef>,
        override val users: Set<VcsUser>,
    ) : VcsLogProvider.LogData

    private class SimpleVcsRef(
        private val hash: Hash,
        private val name: String,
        private val type: com.intellij.vcs.log.VcsRefType,
        private val root: VirtualFile,
    ) : VcsRef {
        override fun getCommitHash(): Hash = hash
        override fun getName(): String = name
        override fun getType(): com.intellij.vcs.log.VcsRefType = type
        override fun getRoot(): VirtualFile = root
    }
}
