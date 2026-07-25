plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.4.10"
    id("org.jetbrains.intellij.platform") version "2.18.1"
}

group = "dev.nezzontli"
version = "0.2.1"

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

// Local development can point at an installed IDE (see gradle.properties);
// CI and other contributors fall back to downloading a matching IDE build.
val localIdePath = providers.gradleProperty("ideLocalPath").orNull

dependencies {
    intellijPlatform {
        if (!localIdePath.isNullOrBlank()) {
            local(localIdePath)
        } else {
            intellijIdea("2026.1.4")
        }
        bundledModule("com.intellij.modules.vcs")
        // Repository/RepositoryManager and the DVCS status widget base
        // classes live here, not under com.intellij.modules.vcs.
        bundledModule("intellij.platform.vcs.dvcs")
        bundledModule("intellij.platform.vcs.dvcs.impl")
        // Referenced by OutgoingResult's commit list type (com.intellij.dvcs.push).
        bundledModule("intellij.platform.vcs.log")
        // Hash/VcsUser (vcs.shared), HashImpl/VcsUserImpl (vcs.impl.shared) and
        // GraphCommit (vcs.log.graph) back the outgoing-commits VcsFullCommitDetails.
        bundledModule("intellij.platform.vcs.shared")
        bundledModule("intellij.platform.vcs.impl.shared")
        bundledModule("intellij.platform.vcs.log.graph")
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "261"
        }

        changeNotes = """
            <ul>
                <li>Clone repositories over ssh:// from the native Clone
                Repository dialog ("Got" in the Version control dropdown)</li>
                <li>GOT_AUTHOR now falls back to git config's user.name/
                user.email when a repo doesn't define its own author</li>
            </ul>
        """.trimIndent()
    }

    publishing {
        token = providers.environmentVariable("PUBLISH_TOKEN")
    }
}

tasks {
    withType<JavaCompile> {
        sourceCompatibility = "21"
        targetCompatibility = "21"
    }
    withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile> {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_21)
            // Without this, Kotlin generates a forwarding override in every
            // class implementing a Kotlin platform interface with default
            // methods (e.g. VcsLogProvider), for binary compatibility with
            // pre-Kotlin-1.4 consumers we don't need to support. Those
            // forwarders show up to the Plugin Verifier as "deprecated
            // method overridden/invoked" even though nothing in this plugin
            // calls them directly.
            jvmDefault.set(org.jetbrains.kotlin.gradle.dsl.JvmDefaultMode.NO_COMPATIBILITY)
        }
    }
}
