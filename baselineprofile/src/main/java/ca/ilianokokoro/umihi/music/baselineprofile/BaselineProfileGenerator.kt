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
 *
 * The generated profiles under app/src/standaloneRelease are never hand-edited to follow a source
 * deletion. AGP resolves every rule against the R8 output and drops the ones that no longer match,
 * one line at a time - after 026e614 the rules for the removed updater classes still reach
 * expandStandaloneReleaseArtProfileWildcards and reach minifyStandaloneReleaseWithR8 zero times,
 * while MainActivity and DatastoreRepository keep every live entry. A stale rule therefore costs
 * nothing in the APK; it is cleared by the next run of this generator, not by editing its output.
 */
@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun startup() = rule.collect(
        packageName = PACKAGE_NAME,
        maxIterations = 3,
        stableIterations = 1,
        includeInStartupProfile = true,
    ) {
        pressHome()
        startActivityAndWait()
    }

    @Test
    fun criticalJourneys() = rule.collect(
        packageName = PACKAGE_NAME,
        maxIterations = 3,
        stableIterations = 1,
        includeInStartupProfile = false,
    ) {
        pressHome()
        startActivityAndWait()
        device.waitForIdle()

        // These screens benefit from AOT compilation but must not pollute startup dex layout.
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
