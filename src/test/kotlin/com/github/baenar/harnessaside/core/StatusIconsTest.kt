package com.github.baenar.harnessaside.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Icon mapping and classpath resource verification for Manul icons. */
class StatusIconsTest {

    @Test
    fun `only the working and waiting states animate`() {
        val animated = setOf(HarnessStatus.THINKING, HarnessStatus.EXECUTING_TOOL, HarnessStatus.WAITING)
        for (status in HarnessStatus.entries) {
            assertEquals(status.name, status in animated, StatusIcons.isAnimated(status))
        }
    }

    @Test
    fun `states map to their own frames, which wrap around`() {
        assertEquals(StatusIcons.forStatus(HarnessStatus.THINKING, 3), StatusIcons.forStatus(HarnessStatus.EXECUTING_TOOL, 3))
        assertEquals(StatusIcons.forStatus(HarnessStatus.THINKING, 0), StatusIcons.forStatus(HarnessStatus.THINKING, 8))
        assertEquals(StatusIcons.forStatus(HarnessStatus.WAITING, 0), StatusIcons.forStatus(HarnessStatus.WAITING, 8))
        assertNotEquals(StatusIcons.forStatus(HarnessStatus.THINKING, 0), StatusIcons.forStatus(HarnessStatus.WAITING, 0))
        assertNotEquals(StatusIcons.forStatus(HarnessStatus.THINKING, 0), StatusIcons.forStatus(HarnessStatus.IDLE, 0))
    }

    @Test
    fun `every Manul icon file and frame exists on the classpath`() {
        val frames = (0 until 8).flatMap { listOf("manul_work_%02d".format(it), "manul_help_%02d".format(it)) }
        for (name in listOf("manul-default", "manul-working", "manul-help", "harnessAside") + frames) {
            assertNotNull("missing icon: /icons/$name.svg", javaClass.getResource("/icons/$name.svg"))
            assertNotNull("missing dark icon: /icons/${name}_dark.svg", javaClass.getResource("/icons/${name}_dark.svg"))
        }
    }

    @Test
    fun `brand icons exist on the classpath`() {
        assertNotNull("missing claude icon", javaClass.getResource("/icons/claude.svg"))
        assertNotNull("missing antigravity icon", javaClass.getResource("/icons/antigravity.svg"))
        assertNotNull("missing opencode icon", javaClass.getResource("/icons/opencode.svg"))
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
