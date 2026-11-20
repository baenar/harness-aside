package com.github.baenar.harnessaside.ui

import com.intellij.util.xmlb.XmlSerializer
import org.junit.Assert.assertEquals
import org.junit.Test

/** The tabs only come back after a restart if the platform can write and read them. */
class HarnessTabsStateTest {

    @Test
    fun `tabs survive being saved and loaded`() {
        val saved = HarnessTabsState.Saved().apply {
            tabs = mutableListOf(
                HarnessTabsState.SavedTab().apply { seed = -42; harness = "claude"; session = "2b0a7c1e-4d88-4b3c-9d55-5f9d1a201111" },
                HarnessTabsState.SavedTab().apply { seed = 7 },
            )
            selected = 1
        }

        val loaded = XmlSerializer.deserialize(XmlSerializer.serialize(saved), HarnessTabsState.Saved::class.java)

        assertEquals(1, loaded.selected)
        assertEquals(listOf(-42 to "claude", 7 to null), loaded.tabs.map { it.seed to it.harness })
        assertEquals("2b0a7c1e-4d88-4b3c-9d55-5f9d1a201111", loaded.tabs[0].session)
    }
}
