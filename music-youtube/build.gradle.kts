// InnerTube wire layer, shared by every JVM client of Jelyvo.
// Like :music-core it is a plain Kotlin module — no Android, Media3, Room or Compose dependency —
// so the macOS client reaches YouTube Music through exactly the same code as the Android app.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.jetbrains.kotlin.serialization)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    api(project(":music-core"))

    api(libs.kotlinx.serialization.json)
    api(libs.okhttp)
    api(libs.newpipeextractor)
    implementation(libs.kotlinx.coroutines.core)

    testImplementation(libs.junit)
}
