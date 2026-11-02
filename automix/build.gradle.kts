// Automix's transition planner and analysis. Kept apart because this module is under the AGPL,
// not the GPL of the rest of Jelyvo: see NOTICE.md. Plain Kotlin, with no Android dependency.
plugins {
    alias(libs.plugins.kotlin.jvm)
}

kotlin {
    jvmToolchain(21)
}

dependencies {
    testImplementation(kotlin("test-junit"))
}
