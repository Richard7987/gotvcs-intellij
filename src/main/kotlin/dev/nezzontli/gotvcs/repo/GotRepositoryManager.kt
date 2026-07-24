package dev.nezzontli.gotvcs.repo

import com.intellij.dvcs.repo.AbstractRepositoryManager
import com.intellij.openapi.project.Project
import dev.nezzontli.gotvcs.GotVcs

class GotRepositoryManager(project: Project) :
    AbstractRepositoryManager<GotRepository>(project, GotVcs.getKey(), "got.repository.sync") {

    override fun getRepositories(): List<GotRepository> = getRepositories(GotRepository::class.java)

    override fun isSyncEnabled(): Boolean = false
}
