plugins {
    id("java")
    id("org.jetbrains.kotlin.jvm") version "2.4.10"
    id("org.jetbrains.intellij.platform") version "2.18.1"
}

group = "dev.nezzontli"
version = "0.1.0"

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
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "261"
        }

        changeNotes = """
            Read-only file status, native diff, commit/rollback, history,
            update/fetch, a Send action, and a Settings panel for the got
            binary path and SSH_AUTH_SOCK.
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
        }
    }
}
