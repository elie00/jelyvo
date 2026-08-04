import com.android.build.api.variant.BuildConfigField
import org.jetbrains.kotlin.gradle.dsl.kotlinExtension
import java.io.FileInputStream
import java.util.Properties

val appVersionName = "2.6.8"

val appVersionCode = 20608


val beta: Boolean = (project.findProperty("beta") as String?)?.toBoolean() ?: true

// APK size: -PtargetAbi=arm64-v8a (comma-separated for several) restricts packaged native libs
// to the given ABI(s). When the property is unset, builds stay universal (all ABIs).
val targetAbi: String? = project.findProperty("targetAbi") as String?
val keystorePropertiesFile: File = rootProject.file("keystore.properties")
val keystoreProperties = Properties()
if (keystorePropertiesFile.exists()) {
    keystoreProperties.load(FileInputStream(keystorePropertiesFile))
}

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.jetbrains.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.baselineprofile)
}

kotlinExtension.jvmToolchain {
    languageVersion.set(JavaLanguageVersion.of(21))
}

android {
    namespace = "ca.ilianokokoro.umihi.music"
    compileSdk {
        version = release(37)
    }
    ndkVersion = "30.0.14904198"
    buildToolsVersion = "37.0.0"

    defaultConfig {
        applicationId = "ca.ilianokokoro.umihi.music"
        minSdk = 24
        targetSdk = 37
        versionCode = appVersionCode
        versionName = "${appVersionName}${if (beta) "-beta" else ""}"

        buildConfigField("boolean", "IS_BETA", "$beta")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        if (targetAbi != null) {
            ndk {
                abiFilters += targetAbi.split(",")
            }
        }
    }


    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
                storeFile = file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
            }
        }
    }


    // A single flavor, on purpose. Upstream's "store" flavor existed only to strip the auto-updater
    // for F-Droid; 026e614 removed the updater from main, leaving store identical to standalone bar
    // a "-store" versionNameSuffix - and worse, invisible to app/src/standaloneRelease, so its APK
    // shipped without the committed baseline profile. This fork publishes nowhere (sideload only,
    // see CHANTIER.md), so the flavor is gone. The dimension stays: dropping it renames every
    // variant (standaloneRelease -> release), which would invalidate the app/src/standaloneRelease
    // source set and every gradle command in CHANTIER.md for no gain.
    flavorDimensions += "version"

    productFlavors {
        create("standalone") {
            isDefault = true
            dimension = "version"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            // Use the release keystore when present (CI); otherwise fall back to debug signing so
            // local release builds (incl. Baseline Profile generation variants) stay installable.
            signingConfig = signingConfigs.findByName("release")
                ?: signingConfigs.getByName("debug")
        }
    }

    // Universal APK only
    splits {
        abi {
            isEnable = false
            isUniversalApk = true
        }
    }

    compileOptions {
        isCoreLibraryDesugaringEnabled = true
    }


    buildFeatures {
        compose = true
        buildConfig = true
    }


    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    androidResources {
        @Suppress("UnstableApiUsage")
        generateLocaleConfig = true

        // Only ship the locales this fork actually maintains. The ~30 locales inherited from
        // upstream are frozen at 130/565 strings (23%), so picking one produced a mixed
        // 23%-translated / 77%-English interface — and generateLocaleConfig offered every one of
        // them in Android's per-app language picker. The files stay in the repo: re-listing a
        // locale here is all it takes once its translation is complete again.
        @Suppress("UnstableApiUsage")
        localeFilters += listOf("en", "fr")
    }

    sourceSets {
        getByName("androidTest").assets.srcDir("$projectDir/schemas")
    }

    // APK size: strip metadata files that R8/AGP would otherwise package. None of these are read
    // at runtime; Rhino's default Messages.properties is intentionally NOT excluded because Rhino
    // loads it for runtime error text.
    packaging {
        resources {
            excludes += listOf(
                "META-INF/**/LICENSE.txt",
                "META-INF/*.version",
                "google/protobuf/*.proto",
                "kotlin/**",
                "**/*.kotlin_builtins",
                "org/mozilla/javascript/resources/Messages_fr.properties",
                "org/mozilla/javascript/resources/Messages_zh_CN.properties",
            )
        }
    }
}

ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
}

androidComponents {
    val commitHash: String =
        (project.findProperty("gitHash") as String?) ?: "N/A"

    onVariants { variant ->
        variant.buildConfigFields?.put(
            "COMMIT_HASH",
            BuildConfigField(
                "String",
                "\"$commitHash\"",
                "commit hash"
            )
        )

        variant.outputs.forEach { output ->
            output.outputFileName.set("Jelyvo.apk")
        }
    }
}


dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.material3)
    debugImplementation(libs.androidx.ui.tooling)

    // Desugaring
    coreLibraryDesugaring(libs.desugar.jdk.libs.nio)

    // Navigation 3
    implementation(libs.nav3.runtime)
    implementation(libs.nav3.ui)
    implementation(libs.androidx.lifecycle.viewmodel.nav3)

    // Splash Screen
    implementation(libs.androidx.core.splashscreen)

    // Serialization
    implementation(libs.kotlinx.serialization.core)
    implementation(libs.kotlinx.serialization.json)

    // Viewmodel
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // Coil (images)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.androidx.palette)

    // Home screen widget
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)

    // Exoplayer
    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.session)
    implementation(libs.androidx.media3.datasource)
    implementation(libs.androidx.media3.datasource.okhttp)
    // Cast. Streamed tracks only: a receiver fetches over HTTP and can open neither the
    // app-private download files nor a content:// MediaStore URI. media3-cast pulls
    // play-services-cast-framework, absent on de-Googled devices - CastManager degrades to
    // "no Cast" there instead of crashing.
    implementation(libs.androidx.media3.cast)
    // Declared although play-services-cast-framework already brings it: app code touches
    // MediaRouter directly for route discovery.
    implementation(libs.androidx.mediarouter)
    implementation(libs.androidx.car.app)
    implementation(libs.play.services.wearable)
    implementation(project(":wear-shared"))
    implementation(project(":music-core"))

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // WebKit
    implementation(libs.androidx.webkit)

    // Icons
    implementation(libs.androidx.material.icons.extended)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Custom Activity On Crash
    implementation(libs.customactivityoncrash)

    // Workers
    implementation(libs.androidx.work.runtime.ktx)

    // Reorderable list
    implementation(libs.reorderable)

    // New Pipe Extractor
    implementation(libs.newpipeextractor)

    // Baseline Profile installer (applies the profile at runtime)
    implementation(libs.androidx.profileinstaller)
    baselineProfile(project(":baselineprofile"))

    // Unit tests
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.room.testing)

}
