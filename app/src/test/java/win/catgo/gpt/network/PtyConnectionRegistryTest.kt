package win.catgo.gpt.network

import org.junit.Assert.*
import org.junit.Test

class PtyConnectionRegistryTest {
    @Test fun learningSessionIdDoesNotTurnReconnectIntoAnotherCliResume() {
        val registry = PtyConnectionRegistry()
        val initial = registry.resolve("a", null, true)
        val reconnect = registry.resolve("a", "learned-session", false)
        assertEquals(initial.channel, reconnect.channel)
        assertNull(reconnect.resume)
        assertFalse(reconnect.fresh)
        assertTrue(initial.fresh)
    }

    @Test fun retryBeforeSessionDiscoveryDoesNotRepeatFresh() {
        val registry = PtyConnectionRegistry()
        registry.resolve("a", null, true)
        assertFalse(registry.resolve("a", null, true).fresh)
    }

    @Test fun openedHistoryKeepsItsOriginalResumeTarget() {
        val registry = PtyConnectionRegistry()
        registry.resolve("a", "original", false)
        assertEquals("original", registry.resolve("a", "descendant", false).resume)
        assertEquals("other", registry.resolve("b", "other", false).resume)
        assertNotEquals(registry.resolve("a", null, false).channel, registry.resolve("b", null, false).channel)
    }

    @Test fun clearingAccountStateDoesNotReusePreviousTargets() {
        val registry = PtyConnectionRegistry()
        registry.resolve("a", "old-account", false)
        registry.clear()
        val next = registry.resolve("a", null, true)
        assertNull(next.resume)
        assertTrue(next.fresh)
    }
}
