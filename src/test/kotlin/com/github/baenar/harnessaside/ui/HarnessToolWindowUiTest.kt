package com.github.baenar.harnessaside.ui

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.toolWindow.ToolWindowHeadlessManagerImpl
import com.intellij.ui.content.ContentManagerEvent
import com.intellij.ui.content.ContentManagerListener
import javax.swing.JPanel

/** Against the platform's real content manager: the swap is what the single-tab invariant rests on. */
class HarnessToolWindowUiTest : BasePlatformTestCase() {

    fun `test replacing content leaves one and never empties the tool window`() {
        val toolWindow = ToolWindowHeadlessManagerImpl.MockToolWindow(project)
        val cm = toolWindow.contentManager
        var emptied = false
        cm.addContentManagerListener(object : ContentManagerListener {
            override fun contentRemoved(event: ContentManagerEvent) {
                emptied = emptied || cm.contentCount == 0
            }
        })

        HarnessToolWindowUi.show(toolWindow, JPanel(), "first")
        HarnessToolWindowUi.show(toolWindow, JPanel(), "second")

        assertEquals(1, cm.contentCount)
        assertEquals("second", cm.selectedContent?.displayName)
        assertFalse("reaching zero would bring the launcher back over the new session", emptied)
    }
}
