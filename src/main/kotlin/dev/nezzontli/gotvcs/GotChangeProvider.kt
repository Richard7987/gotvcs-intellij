package dev.nezzontli.gotvcs

import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangeListManagerGate
import com.intellij.openapi.vcs.changes.ChangeProvider
import com.intellij.openapi.vcs.changes.ChangelistBuilder
import com.intellij.openapi.vcs.changes.CurrentContentRevision
import com.intellij.openapi.vcs.changes.LocallyDeletedChange
import com.intellij.openapi.vcs.changes.VcsDirtyScope
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.vcsUtil.VcsUtil
import java.io.File

class GotChangeProvider(private val commandLine: GotCommandLineWrapper) : ChangeProvider {

    override fun getChanges(
        dirtyScope: VcsDirtyScope,
        builder: ChangelistBuilder,
        progress: ProgressIndicator,
        addGate: ChangeListManagerGate,
    ) {
        for (root in dirtyScope.affectedContentRoots) {
            val workDir = File(root.path)
            val entries = commandLine.status(workDir)

            for (entry in entries) {
                val absoluteFile = File(workDir, entry.path)
                val filePath = VcsUtil.getFilePath(absoluteFile, false)

                when (entry.code) {
                    '?' -> builder.processUnversionedFile(filePath)

                    '!' -> builder.processLocallyDeletedFile(LocallyDeletedChange(filePath))

                    'M', 'm', 'C' -> {
                        val status = if (entry.code == 'C') FileStatus.MERGED_WITH_CONFLICTS else FileStatus.MODIFIED
                        val before = GotContentRevision(filePath, workDir, entry.path, commandLine)
                        val after = CurrentContentRevision(filePath)
                        builder.processChange(Change(before, after, status), GotVcs.getKey())
                    }

                    'A' -> {
                        val after = CurrentContentRevision(filePath)
                        builder.processChange(Change(null, after, FileStatus.ADDED), GotVcs.getKey())
                    }

                    'D' -> {
                        val before = GotContentRevision(filePath, workDir, entry.path, commandLine)
                        builder.processChange(Change(before, null, FileStatus.DELETED), GotVcs.getKey())
                    }

                    else -> Unit
                }
            }
        }
    }

    override fun isModifiedDocumentTrackingRequired(): Boolean = false

    override fun doCleanup(files: MutableList<out VirtualFile>) = Unit
}
