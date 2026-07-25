package ca.ilianokokoro.umihi.music.core.cast

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Pure-JVM guard for the two places where CastOptionsProvider is referenced as a STRING that no
 * compiler checks: the manifest meta-data value, and the R8 keep rule. Renaming or moving the class
 * breaks Cast at runtime (manifest) or in release builds only (keep rule) — both silent failures.
 *
 * No android.* is touched: both files are read as text.
 */
class CastManifestWiringTest {

    private val expectedClassName = "ca.ilianokokoro.umihi.music.core.cast.CastOptionsProvider"

    private fun moduleFile(relative: String): File {
        // Gradle runs unit tests with the module directory as working dir; fall back to the repo
        // root so the test also passes when run from an IDE with a different working dir.
        val direct = File(relative)
        return if (direct.exists()) direct else File("app", relative)
    }

    @Test
    fun `manifest OPTIONS_PROVIDER_CLASS_NAME points at the real class`() {
        val manifest = moduleFile("src/main/AndroidManifest.xml").readText()
        assertTrue(
            "OPTIONS_PROVIDER_CLASS_NAME meta-data missing",
            manifest.contains("com.google.android.gms.cast.framework.OPTIONS_PROVIDER_CLASS_NAME")
        )
        assertTrue(
            "manifest does not name $expectedClassName",
            manifest.contains(expectedClassName)
        )
    }

    @Test
    fun `the release keep rule names the real class`() {
        val rules = moduleFile("proguard-rules.pro").readText()
        assertTrue(
            "no keep rule for $expectedClassName - Cast would break only in release",
            rules.contains(expectedClassName)
        )
    }

    @Test
    fun `the class is where both strings say it is`() {
        val source = moduleFile(
            "src/main/java/" + expectedClassName.replace('.', '/') + ".kt"
        )
        assertTrue("missing ${source.path}", source.exists())
    }
}
