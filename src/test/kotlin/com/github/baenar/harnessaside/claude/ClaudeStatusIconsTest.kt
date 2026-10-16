package com.github.baenar.harnessaside.claude

import com.github.baenar.harnessaside.core.HarnessStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Icon mapping and classpath resource verification for Manul icons.
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
    fun `harness busy states animate`() {
        assertTrue(ClaudeStatusIcons.isAnimatedHarness(HarnessStatus.THINKING))
        assertTrue(ClaudeStatusIcons.isAnimatedHarness(HarnessStatus.EXECUTING_TOOL))
        assertTrue(ClaudeStatusIcons.isAnimatedHarness(HarnessStatus.WAITING))
        assertFalse(ClaudeStatusIcons.isAnimatedHarness(HarnessStatus.IDLE))
        assertFalse(ClaudeStatusIcons.isAnimatedHarness(null))
    }

    @Test
    fun `states map to respective Manul icons`() {
        assertEquals("manul-default", ClaudeStatusIcons.frameName(ClaudeStatus.IDLE, 0))
        assertEquals("manul-default", ClaudeStatusIcons.frameName(null, 0))
        assertEquals("manul_work_00", ClaudeStatusIcons.frameName(ClaudeStatus.THINKING, 0))
        assertEquals("manul_work_07", ClaudeStatusIcons.frameName(ClaudeStatus.THINKING, 7))
        assertEquals("manul_work_00", ClaudeStatusIcons.frameName(ClaudeStatus.THINKING, 8))
        assertEquals("manul_help_00", ClaudeStatusIcons.frameName(ClaudeStatus.WAITING, 0))
        assertEquals("manul_help_07", ClaudeStatusIcons.frameName(ClaudeStatus.WAITING, 7))
        assertEquals("manul_help_00", ClaudeStatusIcons.frameName(ClaudeStatus.WAITING, 8))
    }

    @Test
    fun `every Manul icon file and frame exists on the classpath`() {
        val names = buildList {
            add("manul-default")
            add("manul-working")
            add("manul-help")
            add("harnessAside")
            repeat(ClaudeStatusIcons.WORK_FRAMES) { add(ClaudeStatusIcons.frameName(ClaudeStatus.THINKING, it)) }
            repeat(ClaudeStatusIcons.HELP_FRAMES) { add(ClaudeStatusIcons.frameName(ClaudeStatus.WAITING, it)) }
        }
        for (name in names) {
            val resource = javaClass.getResource("/icons/$name.svg")
            assertNotNull("missing icon: /icons/$name.svg", resource)
            val darkResource = javaClass.getResource("/icons/${name}_dark.svg")
            assertNotNull("missing dark icon: /icons/${name}_dark.svg", darkResource)
        }
    }

    @Test
    fun `brand icons exist on the classpath`() {
        assertNotNull("missing claude icon", javaClass.getResource("/icons/claude.svg"))
        assertNotNull("missing antigravity icon", javaClass.getResource("/icons/antigravity.svg"))
    }

    @Test
    fun `icons use theme action gray and do not use hardcoded black or currentColor`() {
        val names = listOf("manul-default", "manul_work_00", "manul_help_00")
        for (name in names) {
            val lightSvg = javaClass.getResource("/icons/$name.svg")!!.readText()
            assertFalse("icon $name must not use currentColor", lightSvg.contains("currentColor"))
            assertFalse("icon $name must not use hardcoded #1a1a1a", lightSvg.contains("#1a1a1a"))
            assertTrue("icon $name should use action gray", lightSvg.contains("#6C707E"))

            val darkSvg = javaClass.getResource("/icons/${name}_dark.svg")!!.readText()
            assertFalse("dark icon $name must not use currentColor", darkSvg.contains("currentColor"))
            assertFalse("dark icon $name must not use hardcoded #1a1a1a", darkSvg.contains("#1a1a1a"))
            assertTrue("dark icon $name should use light action gray", darkSvg.contains("#CED0D6"))
        }
    }
}
