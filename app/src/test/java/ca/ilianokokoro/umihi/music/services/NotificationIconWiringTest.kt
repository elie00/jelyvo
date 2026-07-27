package ca.ilianokokoro.umihi.music.services

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Garde-fou pur JVM sur l'icone de statut de la notification de lecture. media3 pose son icone via
 * la ressource `media3_notification_small_icon` : on la surcharge par un simple fichier de meme nom,
 * donc aucun compilateur ne verifie quoi que ce soit ici. Deux pieges silencieux, tous deux deja
 * survenus :
 *
 * - un rebrand met a jour les mipmaps et oublie cette icone, qui continue d'afficher l'ancienne
 *   marque (c'est exactement ce qui s'est passe au passage Umihi -> Jelyvo) ;
 * - quelqu'un remet un PNG dans un `drawable-*dpi`, qui devient une seconde definition concurrente.
 *
 * Aucun android.* n'est touche : les fichiers sont lus comme du texte.
 */
class NotificationIconWiringTest {

    private val iconName = "media3_notification_small_icon"

    private fun moduleFile(relative: String): File {
        // Gradle lance les tests unitaires avec le repertoire du module comme working dir ; on
        // retombe sur la racine du depot pour que le test passe aussi depuis l'IDE.
        val direct = File(relative)
        return if (direct.exists()) direct else File("app", relative)
    }

    private fun resDir(): File = moduleFile("src/main/res")

    /** Les `android:pathData="..."` d'un vector, dans l'ordre du fichier. */
    private fun pathData(vector: File): List<String> =
        Regex("""android:pathData="([^"]+)"""")
            .findAll(vector.readText())
            .map { it.groupValues[1] }
            .toList()

    @Test
    fun `the status icon is a single anydpi vector`() {
        val vector = File(resDir(), "drawable-anydpi/$iconName.xml")
        assertTrue("missing ${vector.path}", vector.exists())

        val duplicates = resDir().listFiles { file -> file.isDirectory }
            .orEmpty()
            .filter { it.name.startsWith("drawable") && it.name != "drawable-anydpi" }
            .flatMap { it.listFiles().orEmpty().toList() }
            .filter { it.nameWithoutExtension == iconName }

        assertTrue(
            "several definitions of $iconName compete with the anydpi vector: " +
                    duplicates.joinToString { it.path },
            duplicates.isEmpty()
        )
    }

    @Test
    fun `the status icon draws the Jelyvo mark`() {
        val vector = File(resDir(), "drawable-anydpi/$iconName.xml")
        val brand = File(resDir(), "drawable/ic_jelyvo_monochrome.xml")
        assertTrue("missing ${brand.path}", brand.exists())

        assertEquals(
            "the status icon no longer draws the Jelyvo mark - a rebrand probably forgot it",
            pathData(brand),
            pathData(vector)
        )
    }

    @Test
    fun `the status icon is pure white on transparent`() {
        val vector = File(resDir(), "drawable-anydpi/$iconName.xml")
        val markup = vector.readText()

        // Le systeme ne garde que le canal alpha : une couleur autre que du blanc pur trahit une
        // icone dessinee pour un autre usage (et rend le rendu illisible partout ailleurs).
        val colors = Regex("""android:(?:fill|stroke)Color="([^"]+)"""")
            .findAll(markup)
            .map { it.groupValues[1] }
            .toSet()
        val allowed = setOf("#FFFFFF", "@android:color/transparent")
        assertTrue(
            "unexpected colors in the status icon: ${colors - allowed}",
            (colors - allowed).isEmpty()
        )
        assertFalse("a status icon must not carry a tint", markup.contains("android:tint"))
    }
}
