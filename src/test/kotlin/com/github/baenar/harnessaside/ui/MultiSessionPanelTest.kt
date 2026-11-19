package com.github.baenar.harnessaside.ui

import com.intellij.openapi.components.service
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.toolWindow.ToolWindowHeadlessManagerImpl
import com.intellij.util.ui.UIUtil
import javax.swing.JButton

/** The tabs before any terminal starts: what is restored, and what "+" adds. */
class MultiSessionPanelTest : BasePlatformTestCase() {

    private val saved get() = project.service<HarnessTabsState>().saved

    fun `test restored tabs keep their order, seeds and selection`() {
        saved.tabs = mutableListOf(
            HarnessTabsState.SavedTab().apply { seed = 11 },
            HarnessTabsState.SavedTab().apply { seed = 22 },
        )
        saved.selected = 1

        val panel = MultiSessionPanel(project, ToolWindowHeadlessManagerImpl.MockToolWindow(project))
        Disposer.register(testRootDisposable, panel)

        assertEquals(listOf(11, 22), saved.tabs.map { it.seed })
        assertEquals(1, saved.selected)
        assertEquals("Launcher", panel.title)
    }

    fun `test the plus button adds a tab with its own seed and selects it`() {
        saved.tabs = mutableListOf()
        val panel = MultiSessionPanel(project, ToolWindowHeadlessManagerImpl.MockToolWindow(project))
        Disposer.register(testRootDisposable, panel)
        assertEquals("an empty strip starts with a launcher tab", 1, saved.tabs.size)

        UIUtil.findComponentsOfType(panel, JButton::class.java).single { it.toolTipText == "New tab" }.doClick()

        assertEquals(2, saved.tabs.size)
        assertEquals(1, saved.selected)
        assertEquals(2, saved.tabs.map { it.seed }.distinct().size)
    }
}
