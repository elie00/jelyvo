package ca.ilianokokoro.umihi.music.desktop.account

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class DesktopAccountTest {
    private class Store : CredentialStore {
        var saved: String? = null
        var deleteWorks = true
        override suspend fun read() = saved
        override suspend fun write(secret: String): Boolean { saved = secret; return true }
        override suspend fun clear(): Boolean { if (deleteWorks) saved = null; return deleteWorks }
    }
    @Test fun `failed logout keeps session visible until credential is deleted`() = runBlocking {
        val store = Store()
        val account = DesktopAccount(store)
        assertTrue(account.signIn("SAPISID=example; SID=abc=="))
        store.deleteWorks = false
        assertFalse(account.signOut())
        assertNotNull(account.session.value)
        store.deleteWorks = true
        assertTrue(account.signOut())
        assertNull(account.session.value)
        assertNull(store.saved)
    }
    @Test fun `invalid cookie headers never reach credential storage`() = runBlocking {
        val store = Store()
        val account = DesktopAccount(store)
        for (header in listOf("", "SAPISID=", "SID=abc", "SAPISID=x\r\nInjected=y", "SAPISID=" + "a".repeat(33000))) {
            assertFalse(account.signIn(header))
            assertNull(store.saved)
        }
    }
    @Test fun `secure cookie and equals signs are parsed without exposing header in summary`() {
        val session = DesktopYoutubeSession("__Secure-3PAPISID=abc==; SID=secret")
        assertTrue(session.looksAuthenticated)
        assertEquals("abc==", session.cookieValues["__Secure-3PAPISID"])
        assertFalse(session.summary.contains("secret"))
    }
}
