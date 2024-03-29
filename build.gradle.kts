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
    alias(libs.plugins.kotlin)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.detekt)
    alias(libs.plugins.shadow)
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
}