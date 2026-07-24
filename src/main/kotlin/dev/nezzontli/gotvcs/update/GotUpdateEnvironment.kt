package dev.nezzontli.gotvcs.update

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.Ref
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.update.FileGroup
import com.intellij.openapi.vcs.update.SequentialUpdatesContext
import com.intellij.openapi.vcs.update.UpdateEnvironment
import com.intellij.openapi.vcs.update.UpdateSession
import com.intellij.openapi.vcs.update.UpdatedFiles
import dev.nezzontli.gotvcs.GotVcs
import dev.nezzontli.gotvcs.cli.GotCommandLineWrapper
import java.io.File

/**
 * "Update Project" runs `got fetch` (best-effort: got.conf may not have a
 * remote configured) followed by `got update` for each selected root.
 */
class GotUpdateEnvironment(private val commandLine: GotCommandLineWrapper) : UpdateEnvironment {

    override fun fillGroups(updatedFiles: UpdatedFiles) = Unit

    override fun updateDirectories(
        contentRoots: Array<out FilePath>,
        updatedFiles: UpdatedFiles,
        progressIndicator: ProgressIndicator,
        context: Ref<SequentialUpdatesContext>,
    ): UpdateSession {
        val exceptions = mutableListOf<VcsException>()
        val workDirs = contentRoots.map { File(it.path) }.distinct()

        for (workDir in workDirs) {
            try {
                commandLine.fetch(workDir)
            } catch (e: VcsException) {
                exceptions.add(e)
            }

            try {
                for (entry in commandLine.update(workDir)) {
                    val groupId = when (entry.code) {
                        'U', 'G' -> FileGroup.UPDATED_ID
                        'C', '#' -> FileGroup.MERGED_WITH_CONFLICT_ID
                        'D' -> FileGroup.REMOVED_FROM_REPOSITORY_ID
                        'A', '!' -> FileGroup.CREATED_ID
                        else -> continue
                    }
                    updatedFiles.getGroupById(groupId)?.add(
                        File(workDir, entry.path).path,
                        GotVcs.getKey(),
                        null,
                    )
                }
            } catch (e: VcsException) {
                exceptions.add(e)
            }
        }

        return GotUpdateSession(exceptions)
    }

    override fun createConfigurable(files: MutableCollection<FilePath>): Configurable? = null

    override fun validateOptions(files: MutableCollection<FilePath>): Boolean = true
}
