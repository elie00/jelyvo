// Jelyvo's macOS client: Compose Multiplatform on the JVM, consuming :music-core.
// No Android, Media3 or Room dependency may enter this module — the shared core stays platform-neutral.
import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.jetbrains.compose)
    alias(libs.plugins.jetbrains.kotlin.serialization)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    implementation(project(":music-core"))

    implementation(compose.desktop.currentOs)
    implementation(libs.compose.multiplatform.material3)

    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
}

compose.desktop {
    application {
        mainClass = "ca.ilianokokoro.umihi.music.desktop.MainKt"

        nativeDistributions {
            targetFormats(TargetFormat.Dmg)
            packageName = "Jelyvo"
            packageVersion = project.findProperty("jelyvoDesktopVersion")?.toString() ?: "0.1.0"
            description = "Material YouTube Music player for macOS"
            vendor = "Jelyvo"

            macOS {
                bundleID = "ca.ilianokokoro.umihi.music.desktop"
                dockName = "Jelyvo"
            }
        }
    }
}
