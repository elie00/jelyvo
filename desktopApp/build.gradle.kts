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
    implementation(project(":music-youtube"))
    implementation(project(":music-ui"))

    implementation(compose.desktop.currentOs)
    implementation(libs.compose.multiplatform.material3)

    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    implementation(libs.kotlinx.coroutines.core)

    // JavaFX ships per-platform natives; this client targets Apple Silicon only.
    implementation(variantOf(libs.javafx.base) { classifier("mac-aarch64") })
    implementation(variantOf(libs.javafx.graphics) { classifier("mac-aarch64") })
    implementation(variantOf(libs.javafx.media) { classifier("mac-aarch64") })

    testImplementation(libs.junit)
}

// Same vendored typeface as Android, copied onto the desktop classpath.
val copyJelyvoFont by tasks.registering(Copy::class) {
    from(rootProject.files("music-ui/fonts/nunito.ttf", "music-ui/fonts/dm_sans.ttf"))
    into(layout.buildDirectory.dir("generated/jelyvoFont/font"))
}

sourceSets["main"].resources.srcDir(layout.buildDirectory.dir("generated/jelyvoFont"))

tasks.named("processResources") { dependsOn(copyJelyvoFont) }

// The app icon lives once, as fastlane's launcher icon; macOS needs it as an .icns.
val jelyvoIconDir = layout.buildDirectory.dir("generated/jelyvoIcon")
val generateMacIcon by tasks.registering(Exec::class) {
    val source = rootProject.file("fastlane/metadata/android/en-US/images/icon.png")
    inputs.file(source)
    inputs.file(file("make-icns.sh"))
    outputs.dir(jelyvoIconDir)
    commandLine(
        "bash",
        file("make-icns.sh").absolutePath,
        source.absolutePath,
        jelyvoIconDir.get().asFile.absolutePath,
    )
}

compose.desktop {
    application {
        mainClass = "ca.ilianokokoro.umihi.music.desktop.MainKt"

        nativeDistributions {
            // jlink prunes anything unreferenced by static analysis. NewPipe reaches sun.misc.Unsafe
            // reflectively while extracting playlists, so without jdk.unsupported the packaged app
            // opens an album page and fails with NoClassDefFoundError where the dev run succeeds.
            modules("jdk.unsupported", "java.naming", "jdk.crypto.ec")

            targetFormats(TargetFormat.Dmg)
            packageName = "Jelyvo"
            // macOS rejects a leading zero in an app version, so the preview line starts at 1.0.0.
            packageVersion = project.findProperty("jelyvoDesktopVersion")?.toString() ?: "1.0.0"
            description = "Material YouTube Music player for macOS"
            vendor = "Jelyvo"

            macOS {
                bundleID = "ca.ilianokokoro.umihi.music.desktop"
                dockName = "Jelyvo"
                iconFile.set(jelyvoIconDir.map { it.file("Jelyvo.icns") })
            }
        }
    }
}

// jpackage reads the icon while assembling the bundle, so it must exist first.
listOf("createDistributable", "packageDmg").forEach { name ->
    tasks.matching { it.name == name }.configureEach { dependsOn(generateMacIcon) }
}
