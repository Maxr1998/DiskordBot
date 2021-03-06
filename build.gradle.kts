import com.github.benmanes.gradle.versions.updates.DependencyUpdatesTask
import com.github.benmanes.gradle.versions.updates.gradle.GradleReleaseChannel
import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar
import io.gitlab.arturbosch.detekt.Detekt
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

allprojects {
    repositories {
        mavenCentral()
        maven { url = uri(SONATYPE_SNAPSHOTS_REPO) }
    }

    tasks.wrapper {
        distributionType = Wrapper.DistributionType.ALL
    }
}

plugins {
    application
    kotlin("jvm")
    kotlin("plugin.serialization")
    alias(libs.plugins.detekt)
    alias(libs.plugins.shadow)
    alias(libs.plugins.dependencyUpdates)
}

val applicationName = "diskord-bot"
group = "de.maxr1998"
version = "1.0.0"

detekt {
    buildUponDefaultConfig = true
    allRules = false
    config = files("$projectDir/detekt.yml")
    autoCorrect = true
}

application {
    mainClass.set("de.maxr1998.diskord.MainKt")
}

dependencies {
    // Core
    implementation(libs.koin)
    implementation(libs.coroutines)
    implementation(libs.diskord) {
        exclude("org.slf4j", "slf4j-simple")
    }
    implementation(libs.bundles.ktor)
    implementation(libs.bundles.exposed)
    implementation(libs.sqlitejdbc)
    implementation(libs.jsoup)

    // Logging
    implementation(libs.bundles.logging)
    testImplementation(libs.bundles.kotest)


    // Auto formatting with detekt
    detektPlugins(libs.detekt.formatting)
}

tasks {
    withType<Detekt> {
        reports {
            html.required.set(true)
            xml.required.set(false)
            txt.required.set(true)
            sarif.required.set(true)
        }
    }

    withType<KotlinCompile> {
        kotlinOptions {
            jvmTarget = JavaVersion.VERSION_17.toString()
            @Suppress("SuspiciousCollectionReassignment")
            freeCompilerArgs = listOf("-opt-in=kotlin.RequiresOptIn")
        }
    }

    withType<ShadowJar> {
        archiveBaseName.set(applicationName)
        archiveVersion.set(project.version as String)
        append("META-INF/LICENSE")
        append("META-INF/LICENSE.txt")
        append("META-INF/NOTICE")
        append("META-INF/NOTICE.txt")
    }

    withType<Test> {
        useJUnitPlatform()
    }

    // Configure dependency updates task
    withType<DependencyUpdatesTask> {
        gradleReleaseChannel = GradleReleaseChannel.CURRENT.id

        doFirst {
            project.repositories.removeAll { repo ->
                repo is MavenArtifactRepository && repo.url.toString() == SONATYPE_SNAPSHOTS_REPO
            }
        }

        rejectVersionIf {
            val candidateType = classifyVersion(candidate.version)
            val currentType = classifyVersion(currentVersion)

            val accept = when (candidateType) {
                // Always accept stable updates
                VersionType.STABLE -> true
                // Accept milestone updates for current milestone and unstable
                VersionType.MILESTONE -> currentType != VersionType.STABLE
                // Only accept unstable for current unstable
                VersionType.UNSTABLE -> currentType == VersionType.UNSTABLE
            }

            !accept
        }
    }
}