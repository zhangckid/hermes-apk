package win.catgo.gpt.network

import org.junit.Assert.*
import org.junit.Test

class HermesPromptTrackerTest {
    @Test fun repeatedRenderingRetainsIdentityAndNeverEnablesDuplicateSubmissions() {
        val tracker = HermesPromptTracker()
        val raw = PromptFixtures.question()
        tracker.append(raw.toByteArray()); tracker.refresh()
        val id = tracker.current!!.id
        val lease = tracker.begin(id)!!
        assertNull(tracker.begin(id))
        assertTrue(tracker.valid(lease))
        tracker.sent(lease)
        tracker.append(raw.toByteArray()); tracker.refresh()
        assertEquals(id, tracker.current!!.id)
        assertTrue(tracker.busy)
        assertNull(tracker.begin(id))
    }

    @Test fun newQuestionAndSessionResetRevokeOldCallbacks() {
        val tracker = HermesPromptTracker()
        tracker.append(PromptFixtures.question().toByteArray()); tracker.refresh()
        val old = tracker.begin(tracker.current!!.id)!!
        tracker.append(PromptFixtures.question("另一个问题？").toByteArray()); tracker.refresh()
        assertFalse(tracker.valid(old))
        tracker.failed(old)
        val next = tracker.current!!.id
        assertNotEquals(old.id, next)
        assertNull(tracker.begin(old.id))
        assertNotNull(tracker.begin(next))
        tracker.clear()
        tracker.append(PromptFixtures.question().toByteArray()); tracker.refresh()
        assertNotEquals(next, tracker.current!!.id)
        assertFalse(tracker.valid(old))
    }

    @Test fun outputBeforeMutexDeliveryInvalidatesTheSendLease() {
        val tracker = HermesPromptTracker()
        tracker.append(PromptFixtures.question().toByteArray()); tracker.refresh()
        val lease = tracker.begin(tracker.current!!.id)!!
        tracker.append("\u001b[0m".toByteArray())
        assertFalse(tracker.valid(lease))
        tracker.failed(lease)
        assertTrue(tracker.ready)
    }

    @Test fun silentExpiredPasswordRequestCannotReceiveASecret() {
        var now = 0L
        val tracker = HermesPromptTracker { now }
        tracker.append(PromptFixtures.secret().toByteArray()); tracker.refresh()
        val id = tracker.current!!.id
        now = 45_001
        assertNull(tracker.begin(id))
    }

    @Test fun countdownUpdatesDoNotResetTheUsersDraftIdentity() {
        val tracker = HermesPromptTracker()
        tracker.append(PromptFixtures.question(seconds = 40).toByteArray()); tracker.refresh()
        val id = tracker.current!!.id
        tracker.append(PromptFixtures.question(seconds = 39).toByteArray()); tracker.refresh()
        assertEquals(id, tracker.current!!.id)
    }

    @Test fun halfPaintedPanelCannotBeSubmittedEvenIfTheOldBordersRemain() {
        val tracker = HermesPromptTracker()
        tracker.append(PromptFixtures.approval().toByteArray()); tracker.refresh()
        tracker.append("\u001b[?25l\u001b[3;1H".toByteArray()); tracker.refresh()
        assertFalse(tracker.ready)
        assertNull(tracker.begin(tracker.current!!.id))
        tracker.append("\u001b[?25h".toByteArray()); tracker.refresh()
        assertTrue(tracker.ready)
    }

    @Test fun finishedPanelClearsWaitingState() {
        val tracker = HermesPromptTracker()
        tracker.append(PromptFixtures.question().toByteArray()); tracker.refresh()
        tracker.append("\u001b[2J\u001b[HAnswer received".toByteArray()); tracker.refresh()
        assertNull(tracker.current)
        assertFalse(tracker.busy)
        assertFalse(tracker.ready)
    }
}
