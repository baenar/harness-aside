package com.github.baenar.harnessaside

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Frame bookkeeping and the icon files themselves.
 *
 * Loading icons needs a running IDE, so these check the two things that break without one:
 * which file each frame maps to, and whether that file is actually on the classpath. A
 * frame count edited in Kotlin but not in `tools/generate-icons.sh` fails here rather than
 * as a missing-icon exception at runtime.
 */
class ClaudeStatusIconsTest {

    @Test
    fun `only busy states animate`() {
        assertTrue(ClaudeStatusIcons.isAnimated(ClaudeStatus.THINKING))
        assertTrue(ClaudeStatusIcons.isAnimated(ClaudeStatus.WAITING))
        assertFalse(ClaudeStatusIcons.isAnimated(ClaudeStatus.IDLE))
        assertFalse("no status yet is a still icon too", ClaudeStatusIcons.isAnimated(null))
    }

    @Test
    fun `still states always use the plain glyph`() {
        assertEquals("harnessAside", ClaudeStatusIcons.frameName(ClaudeStatus.IDLE, 0))
        assertEquals("harnessAside", ClaudeStatusIcons.frameName(null, 7))
        assertEquals(0, ClaudeStatusIcons.frameCount(ClaudeStatus.IDLE))
    }

    @Test
    fun `frames are zero-padded and wrap around`() {
        assertEquals("walk_00", ClaudeStatusIcons.frameName(ClaudeStatus.THINKING, 0))
        assertEquals("walk_05", ClaudeStatusIcons.frameName(ClaudeStatus.THINKING, 5))
        assertEquals("kick_03", ClaudeStatusIcons.frameName(ClaudeStatus.WAITING, 3))

        val walk = ClaudeStatusIcons.WALK_FRAMES
        assertEquals("the loop restarts", "walk_00", ClaudeStatusIcons.frameName(ClaudeStatus.THINKING, walk))
        assertEquals("walk_01", ClaudeStatusIcons.frameName(ClaudeStatus.THINKING, walk * 3 + 1))
    }

    @Test
    fun `a frame counter that never resets stays in range`() {
        // The animator increments forever while Claude is busy, so any counter value must
        // still land on a frame that exists.
        val walk = (0 until ClaudeStatusIcons.WALK_FRAMES).map { "walk_%02d".format(it) }
        val kick = (0 until ClaudeStatusIcons.KICK_FRAMES).map { "kick_%02d".format(it) }

        for (frame in listOf(0, 1, 999, Int.MAX_VALUE - 1, Int.MAX_VALUE)) {
            assertTrue(walk.contains(ClaudeStatusIcons.frameName(ClaudeStatus.THINKING, frame)))
            assertTrue(kick.contains(ClaudeStatusIcons.frameName(ClaudeStatus.WAITING, frame)))
        }
    }

    @Test
    fun `every frame file exists on the classpath`() {
        val names = buildList {
            add(ClaudeStatusIcons.frameName(null, 0))
            repeat(ClaudeStatusIcons.WALK_FRAMES) { add(ClaudeStatusIcons.frameName(ClaudeStatus.THINKING, it)) }
            repeat(ClaudeStatusIcons.KICK_FRAMES) { add(ClaudeStatusIcons.frameName(ClaudeStatus.WAITING, it)) }
        }
        assertEquals("no duplicate frames", names.size, names.toSet().size)

        for (name in names) {
            val resource = javaClass.getResource("/icons/$name.svg")
            assertNotNull("missing icon: /icons/$name.svg — rerun tools/generate-icons.sh", resource)
        }
    }

    @Test
    fun `frame files are white, the one colour the tool window button uses`() {
        val svg = javaClass.getResource("/icons/walk_00.svg")!!.readText()
        assertTrue("frames must be white, not Claude orange", svg.contains("#FFFFFF"))
        assertFalse(svg.contains("#D97757"))
    }
}
