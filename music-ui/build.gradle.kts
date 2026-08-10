// Jelyvo's shared visual identity: one definition of the palette, typography and shapes for every
// client. Android and macOS both consume it, so the desktop app cannot drift from the phone.
//
// Nunito ships here as a vendored variable font rather than an Android downloadable font: Play
// Services has no macOS counterpart, and on a device without it the phone silently fell back to
// the system typeface.
plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kmp.library)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.jetbrains.compose)
}

kotlin {
    androidLibrary {
        namespace = "ca.ilianokokoro.umihi.music.ui.theme"
        compileSdk = 37
        minSdk = 24
    }
    jvm("desktop")

    jvmToolchain(21)

    sourceSets {
        commonMain.dependencies {
            implementation(compose.runtime)
            implementation(compose.foundation)
            implementation(compose.ui)
            implementation(compose.components.resources)
            api(libs.compose.multiplatform.material3)
        }
    }
}

compose.resources {
    publicResClass = true
    packageOfResClass = "ca.ilianokokoro.umihi.music.ui.theme.resources"
}
