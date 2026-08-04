// Platform-neutral core of Jelyvo: musical identity, catalog, playback and stream contracts.
// Deliberately a plain Kotlin module — no Android, Media3, Room or Compose dependency — so a
// future macOS or Web client can consume it unchanged.
plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.jetbrains.kotlin.serialization)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    api(libs.kotlinx.coroutines.core)
    api(libs.kotlinx.serialization.core)

    testImplementation(libs.junit)
}
