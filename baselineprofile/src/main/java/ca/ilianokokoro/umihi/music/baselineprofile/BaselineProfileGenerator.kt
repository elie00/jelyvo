package ca.ilianokokoro.umihi.music.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generates a Baseline Profile for the app. It exercises the critical user journeys (cold start and
 * navigating between the main tabs) so ART can AOT-compile those hot paths, speeding up startup and
 * screen loads in release builds.
 *
 * Run with: ./gradlew :app:generateStandaloneBaselineProfile (requires a rooted device/emulator).
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun generate() = rule.collect(
        packageName = PACKAGE_NAME,
        includeInStartupProfile = true,
    ) {
        pressHome()
        startActivityAndWait()

        device.waitForIdle()

        // Best-effort navigation to capture more screens; failures don't invalidate the profile.
        runCatching {
            device.findObject(By.text("Search"))?.click()
            device.wait(Until.hasObject(By.text("Search")), 3_000)
            device.waitForIdle()

            device.findObject(By.text("Settings"))?.click()
            device.waitForIdle()

            device.findObject(By.text("Home"))?.click()
            device.waitForIdle()
        }
    }

    companion object {
        private const val PACKAGE_NAME = "ca.ilianokokoro.umihi.music"
    }
}
