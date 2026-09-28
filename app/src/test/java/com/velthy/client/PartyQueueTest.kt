import com.velthy.client.playback.detectSingleMove
import com.velthy.client.data.listentogether.JamInviteLink
import com.velthy.client.data.listentogether.ListenTogether
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The shared queue's move detection — the thing that decides whether a queue
 * edit is sent to the party as one row moving or as a whole new running order.
 *
 * Getting it wrong is not a subtle bug: a reorder sent as a replacement makes
 * every listener's player rebuild its timeline and drop whatever it had
 * prefetched, for a change that moved one entry.
 */
class PartyQueueMoveTest {

    @Test
    fun `a row moved forward is found`() {
        val move = detectSingleMove(
            oldList = listOf("a", "b", "c", "d"),
            newList = listOf("a", "c", "d", "b"),
        )
        assertEquals(1, move?.fromIndex)
        assertEquals(3, move?.toIndex)
        assertEquals("b", move?.videoId)
    }

    @Test
    fun `a row moved back is found`() {
        val move = detectSingleMove(
            oldList = listOf("a", "b", "c", "d"),
            newList = listOf("a", "d", "b", "c"),
        )
        assertEquals(3, move?.fromIndex)
        assertEquals(1, move?.toIndex)
        assertEquals("d", move?.videoId)
    }

    @Test
    fun `an unchanged list is not a move`() {
        assertNull(detectSingleMove(listOf("a", "b"), listOf("a", "b")))
    }

    @Test
    fun `a list of a different length is not a move`() {
        assertNull(detectSingleMove(listOf("a", "b"), listOf("a", "b", "c")))
        assertNull(detectSingleMove(listOf("a", "b", "c"), listOf("a", "b")))
    }

    @Test
    fun `a replacement is not a move`() {
        // Same length, different songs: nothing was moved, the queue was
        // replaced, and that has to travel as a queue rather than as a move.
        assertNull(detectSingleMove(listOf("a", "b"), listOf("x", "y")))
    }

    @Test
    fun `two changes at once are not a move`() {
        assertNull(detectSingleMove(listOf("a", "b", "c"), listOf("c", "b", "a")))
    }

    @Test
    fun `an empty list is not a move`() {
        assertNull(detectSingleMove(emptyList(), emptyList()))
        assertNull(detectSingleMove(emptyList(), listOf("a")))
    }

    @Test
    fun `a repeated id is not mistaken for a move`() {
        // The same song twice in a queue is ordinary. Swapping which copy is
        // where would be invisible in the id list, so it is not reported.
        assertNull(detectSingleMove(listOf("a", "a", "b"), listOf("a", "a", "b")))
    }
}

/**
 * Server addresses as they arrive from a settings box or an invite link.
 *
 * The refusal is the point: an address with a typo stored happily is an address
 * that fails on every request afterwards, and the failure looks like the
 * party's rather than the address's.
 */
class PartyServerAddressTest {

    @Test
    fun `a bare hostname is completed with https`() {
        assertEquals("https://party.example.com", ListenTogether.normalizeServerAddress("party.example.com"))
    }

    @Test
    fun `a scheme and port are kept`() {
        assertEquals(
            "http://192.168.1.10:8080",
            ListenTogether.normalizeServerAddress("http://192.168.1.10:8080"),
        )
    }

    @Test
    fun `a trailing slash is dropped`() {
        assertEquals(
            "https://party.example.com",
            ListenTogether.normalizeServerAddress("https://party.example.com/"),
        )
    }

    @Test
    fun `a path is kept, because a proxy at a path is ordinary`() {
        assertEquals(
            "https://example.com/jam",
            ListenTogether.normalizeServerAddress("https://example.com/jam/"),
        )
    }

    @Test
    fun `a blank address is the built-in server, not an error`() {
        assertEquals("", ListenTogether.normalizeServerAddress("   "))
    }

    @Test
    fun `an address with a space is refused`() {
        assertNull(ListenTogether.normalizeServerAddress("https://party example.com"))
    }

    @Test
    fun `a non-http scheme is refused`() {
        assertNull(ListenTogether.normalizeServerAddress("ftp://party.example.com"))
        assertNull(ListenTogether.normalizeServerAddress("file:///etc/passwd"))
    }

    @Test
    fun `a port out of range is refused`() {
        assertNull(ListenTogether.normalizeServerAddress("https://party.example.com:99999"))
    }

    @Test
    fun `a query or fragment is refused`() {
        assertNull(ListenTogether.normalizeServerAddress("https://party.example.com?x=1"))
        assertNull(ListenTogether.normalizeServerAddress("https://party.example.com#frag"))
    }

    @Test
    fun `a path that walks up is refused`() {
        assertNull(ListenTogether.normalizeServerAddress("https://party.example.com/a/../b"))
    }

    @Test
    fun `a host is required`() {
        assertNull(ListenTogether.normalizeServerAddress("https://"))
        assertFalse(ListenTogether.isUsableServerAddress("https://"))
        assertTrue(ListenTogether.isUsableServerAddress("party.example.com"))
    }
}

/**
 * Invite links, in both shapes and with a server named.
 *
 * The server half is what makes a self-hosted party shareable: a link that
 * carries it takes the listener's app to that server rather than to the one
 * this install happens to be pointed at.
 */
class PartyInviteLinkTest {

    @Test
    fun `the app scheme carries a code`() {
        assertEquals("ABC123", JamInviteLink.parse("velthy://join/ABC123"))
    }

    @Test
    fun `the web link carries a code`() {
        assertEquals("ABC123", JamInviteLink.parse("https://velthy.my.id/join/ABC123"))
    }

    @Test
    fun `a lower-case code is normalised`() {
        assertEquals("ABC123", JamInviteLink.parse("velthy://join/abc123"))
    }

    @Test
    fun `a code of the wrong length is refused`() {
        assertNull(JamInviteLink.parse("velthy://join/ABC12"))
        assertNull(JamInviteLink.parse("velthy://join/ABC1234"))
    }

    @Test
    fun `a foreign link is refused`() {
        assertNull(JamInviteLink.parse("https://example.com/join/ABC123"))
        assertNull(JamInviteLink.parse("https://velthy.my.id/other/ABC123"))
        assertNull(JamInviteLink.parse(null))
    }

    @Test
    fun `a named server is carried along`() {
        val invite = JamInviteLink.parseInvite(
            "https://velthy.my.id/join/ABC123?server=https%3A%2F%2Fparty.example.com",
        )
        assertEquals("ABC123", invite?.code)
        assertEquals("https://party.example.com", invite?.serverUrl)
    }

    @Test
    fun `a server with no scheme is completed`() {
        val invite = JamInviteLink.parseInvite("velthy://join/ABC123?server=party.example.com")
        assertEquals("https://party.example.com", invite?.serverUrl)
    }

    @Test
    fun `a link naming no server leaves it null`() {
        assertNull(JamInviteLink.parseInvite("velthy://join/ABC123")?.serverUrl)
    }

    @Test
    fun `a server that is not an address is dropped, and the code survives`() {
        // A malformed `?server=` is a malformed link, not a reason to lose the
        // party somebody was invited to.
        val invite = JamInviteLink.parseInvite("velthy://join/ABC123?server=not%20a%20host")
        assertEquals("ABC123", invite?.code)
        assertNull(invite?.serverUrl)
    }

    @Test
    fun `an invite to the built-in server does not name it`() {
        // Nothing is published for the built-in address, so an invite to it is
        // an ordinary web link.
        assertEquals("https://velthy.my.id/join/ABC123", JamInviteLink.url("ABC123", null))
        assertEquals("https://velthy.my.id/join/ABC123", JamInviteLink.url("ABC123", ""))
    }

    @Test
    fun `an invite to a custom server points at that server`() {
        assertEquals(
            "https://party.example.com/join/ABC123",
            JamInviteLink.url("ABC123", "https://party.example.com"),
        )
    }

    @Test
    fun `the scheme form names a custom server in its query`() {
        val scheme = JamInviteLink.schemeUrl("ABC123", "https://party.example.com")
        assertTrue(scheme.startsWith("velthy://join/ABC123?server="))
        assertTrue(scheme.contains("party.example.com"))
    }
}
