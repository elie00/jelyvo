package ca.ilianokokoro.umihi.music

import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class FrenchResourcesCompletenessTest {

    @Test
    fun `every translatable default resource has a French value`() {
        val resources = findResourcesDirectory()
        val defaultKeys = resourceKeys(File(resources, "values/strings.xml"))
        val frenchKeys = resourceKeys(File(resources, "values-fr/strings.xml"))
        val missing = defaultKeys - frenchKeys

        assertTrue(
            "Missing French resources: ${missing.sorted().joinToString()}",
            missing.isEmpty(),
        )
    }

    private fun resourceKeys(file: File): Set<String> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val resources = document.documentElement.childNodes
        return buildSet {
            for (index in 0 until resources.length) {
                val element = resources.item(index) as? Element ?: continue
                if (element.tagName !in RESOURCE_TAGS) continue
                if (element.getAttribute("translatable") == "false") continue
                element.getAttribute("name").takeIf(String::isNotBlank)?.let(::add)
            }
        }
    }

    private fun findResourcesDirectory(): File {
        val workingDirectory = requireNotNull(System.getProperty("user.dir"))
        var directory = File(workingDirectory).absoluteFile
        repeat(6) {
            listOf(
                File(directory, "src/main/res"),
                File(directory, "app/src/main/res"),
            ).firstOrNull(File::isDirectory)?.let { return it }
            directory = directory.parentFile ?: error(
                "Could not find app/src/main/res from $workingDirectory"
            )
        }
        error("Could not find app/src/main/res from $workingDirectory")
    }

    companion object {
        private val RESOURCE_TAGS = setOf("string", "plurals", "string-array")
    }
}
