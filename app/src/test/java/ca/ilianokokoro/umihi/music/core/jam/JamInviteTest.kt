package ca.ilianokokoro.umihi.music.core.jam

import ca.ilianokokoro.umihi.music.ui.components.qrMatrix
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class JamInviteTest {
    private val server = "https://jam.example"
    private val encoded = "https%3A%2F%2Fjam.example"

    @Test
    fun `reads the link the invite page opens`() {
        assertEquals(
            JamInvite(server, "ABC123"),
            JamInvites.parse("jelyvo://party/ABC123?server=$encoded"),
        )
    }

    @Test
    fun `a link naming no server means the default one`() {
        val invite = JamInvites.parse("jelyvo://party/ABC123")!!
        assertEquals(DEFAULT_JAM_SERVER_URL, invite.serverUrl)
        assertTrue(invite.isOnDefaultServer)
    }

    @Test
    fun `reads a code typed in lower case`() {
        assertEquals("ABC123", JamInvites.parse("jelyvo://party/abc123")?.code)
    }

    @Test
    fun `refuses other links`() {
        for (link in listOf(
            "https://music.youtube.com/watch?v=abc",
            "jelyvo://other/ABC123",
            "not a link",
            "",
        )) {
            assertNull(link, JamInvites.parse(link))
        }
    }

    @Test
    fun `refuses a code that cannot be one`() {
        for (code in listOf("", "ABC12", "ABC1234", "ABC-23", "../../x")) {
            assertNull(code, JamInvites.parse("jelyvo://party/$code"))
        }
    }

    @Test
    fun `refuses a server that is not plain https`() {
        for (bad in listOf(
            "http%3A%2F%2Fjam.example",
            "ftp%3A%2F%2Fjam.example",
            "https%3A%2F%2Fuser%40jam.example",
            "https%3A%2F%2Fjam.example%3Fx%3D1",
            "https%3A%2F%2F",
            "",
        )) {
            assertNull(bad, JamInvites.parse("jelyvo://party/ABC123?server=$bad"))
        }
    }

    @Test
    fun `an invite says which server it is on`() {
        val invite = JamInvites.parse("jelyvo://party/ABC123?server=$encoded")!!
        assertFalse(invite.isOnDefaultServer)
        assertEquals("jam.example", invite.serverHost)
    }

    @Test
    fun `the shared link goes to the server's invite page`() {
        assertEquals("https://jam.example/invite/ABC123", JamInvites.link(server, "ABC123"))
    }

    @Test
    fun `an invitation fits a QR code`() {
        val matrix = qrMatrix(JamInvites.link(DEFAULT_JAM_SERVER_URL, "ABC123"))
        assertEquals(matrix.width, matrix.height)
        assertTrue(matrix.width >= 21)
    }
}

class JamStatusTest {
    private fun party(hostOnly: Boolean, hostIsMe: Boolean) = JamStatus.InParty(
        code = "ABC123",
        members = listOf(
            PartyMember(memberId = "me", isHost = hostIsMe),
            PartyMember(memberId = "other", isHost = !hostIsMe),
        ),
        myMemberId = "me",
        hostOnlyControl = hostOnly,
    )

    @Test
    fun `being host comes from the members, so it passes on`() {
        assertTrue(party(hostOnly = false, hostIsMe = true).isHost)
        assertFalse(party(hostOnly = false, hostIsMe = false).isHost)
    }

    @Test
    fun `everyone controls the music until the host says otherwise`() {
        assertTrue(party(hostOnly = false, hostIsMe = false).canControl)
        assertFalse(party(hostOnly = true, hostIsMe = false).canControl)
        assertTrue(party(hostOnly = true, hostIsMe = true).canControl)
    }
}
