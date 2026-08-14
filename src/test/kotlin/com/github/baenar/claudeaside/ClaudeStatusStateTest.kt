package com.github.baenar.claudeaside

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The rules deciding what the tool-window button shows. */
class ClaudeStatusStateTest {

    private val state = ClaudeStatusState()

    @Test
    fun `shows nothing until a tab reports a status`() {
        assertNull(state.visibleStatus())
        state.openTab("a", "Claude")
        assertNull(state.visibleStatus())
    }

    @Test
    fun `follows the first tab and ignores the others`() {
        state.openTab("first", "Claude")
        state.openTab("second", "Claude 2")

        state.setStatus("second", ClaudeStatus.WAITING)
        assertNull("a later tab must not drive the button", state.visibleStatus())

        state.setStatus("first", ClaudeStatus.THINKING)
        assertEquals(ClaudeStatus.THINKING, state.visibleStatus())
    }

    @Test
    fun `promotes the next-oldest tab when the first one closes`() {
        state.openTab("first", "Claude")
        state.openTab("second", "Claude 2")
        state.setStatus("first", ClaudeStatus.THINKING)
        state.setStatus("second", ClaudeStatus.WAITING)

        state.closeTab("first")

        assertEquals("second", state.primarySession())
        assertEquals(ClaudeStatus.WAITING, state.visibleStatus())
    }

    @Test
    fun `closing the last tab clears the button`() {
        state.openTab("only", "Claude")
        state.setStatus("only", ClaudeStatus.THINKING)
        state.closeTab("only")

        assertNull(state.primarySession())
        assertNull(state.visibleStatus())
    }

    @Test
    fun `reopening a closed session starts from a clean slate`() {
        state.openTab("a", "Claude")
        state.setStatus("a", ClaudeStatus.WAITING)
        state.closeTab("a")
        state.openTab("a", "Claude")

        assertNull(state.visibleStatus())
    }

    @Test
    fun `a cleared status file leaves the button with nothing to show`() {
        state.openTab("a", "Claude")
        state.setStatus("a", ClaudeStatus.THINKING)
        state.clearStatus("a")

        assertNull(state.visibleStatus())
    }

    // --- the "notify me" signal ------------------------------------------------------

    @Test
    fun `only a fresh waiting on the first tab asks for a balloon`() {
        state.openTab("first", "Claude")
        state.openTab("second", "Claude 2")

        assertTrue(state.setStatus("first", ClaudeStatus.WAITING))
        assertFalse("repeat of the same state is not news", state.setStatus("first", ClaudeStatus.WAITING))
        assertFalse("other states never notify", state.setStatus("first", ClaudeStatus.THINKING))
        assertFalse("a background tab never notifies", state.setStatus("second", ClaudeStatus.WAITING))

        // Leaving and re-entering the state is news again.
        assertTrue(state.setStatus("first", ClaudeStatus.WAITING))
    }

    // --- interrupts ------------------------------------------------------------------

    @Test
    fun `the first look at a transcript only records a baseline`() {
        // A resumed conversation is usually full of old interrupts; none of them mean
        // "Claude just stopped".
        assertFalse(state.observeInterrupt("a", "old-interrupt"))
    }

    @Test
    fun `a new interrupt after the baseline is reported once`() {
        state.observeInterrupt("a", "old-interrupt")

        assertTrue(state.observeInterrupt("a", "new-interrupt"))
        assertFalse("the same record must not fire twice", state.observeInterrupt("a", "new-interrupt"))
        assertTrue(state.observeInterrupt("a", "newer-interrupt"))
    }

    @Test
    fun `an interrupt in a transcript that had none is reported`() {
        // Fresh session: baseline is "no interrupts yet", then the user hits Ctrl+C.
        assertFalse(state.observeInterrupt("a", null))
        assertTrue(state.observeInterrupt("a", "first-interrupt"))
    }

    @Test
    fun `losing sight of an interrupt is not itself an interrupt`() {
        state.observeInterrupt("a", "old")
        // The record scrolled out of the tail window that gets read.
        assertFalse(state.observeInterrupt("a", null))
    }

    @Test
    fun `interrupt baselines are per session`() {
        state.observeInterrupt("a", "x")
        assertFalse("session b has no baseline yet", state.observeInterrupt("b", "x"))
        assertTrue(state.observeInterrupt("b", "y"))
    }

    @Test
    fun `closing a tab forgets its interrupt baseline`() {
        state.openTab("a", "Claude")
        state.observeInterrupt("a", "old")
        state.closeTab("a")
        state.openTab("a", "Claude")

        assertFalse("a reopened tab baselines again", state.observeInterrupt("a", "old"))
    }

    @Test
    fun `baselining swallows a record that has already been acted on`() {
        // The keyboard watcher resets the icon the instant the user hits Ctrl+C. Claude may
        // write the matching record moments later, once Claude is busy again — re-baselining
        // when a busy stretch starts is what stops that from blanking the icon mid-turn.
        state.observeInterrupt("a", "before")
        state.baselineInterrupt("a", "the-ctrl-c-record")

        assertFalse(state.observeInterrupt("a", "the-ctrl-c-record"))
        assertTrue("a genuinely new one still counts", state.observeInterrupt("a", "a-later-one"))
    }

    @Test
    fun `baselining a session that was never seen still counts as seen`() {
        state.baselineInterrupt("a", null)
        assertTrue(state.observeInterrupt("a", "first"))
    }

    @Test
    fun `remembers tab names for the notification text`() {
        state.openTab("a", "Claude 2")
        assertEquals("Claude 2", state.tabName("a"))
        assertNull(state.tabName("unknown"))
    }
}
