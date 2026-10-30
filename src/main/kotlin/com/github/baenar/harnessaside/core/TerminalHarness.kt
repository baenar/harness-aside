package com.github.baenar.harnessaside.core

import com.intellij.icons.AllIcons
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import javax.swing.Icon

/** A plain shell in the side tool window: no CLI started, no hooks installed. */
object TerminalHarness : Harness {
    override val id: String = "terminal"
    override val displayName: String = "Terminal"
    override val executable: String = ""
    override val icon: Icon get() = AllIcons.Nodes.Console

    override fun openSession(
        project: Project,
        toolWindow: ToolWindow,
        continueSession: Boolean,
        sessionId: String?,
    ) {
        project.service<HarnessTerminalService>().openTerminalTab(
            toolWindow = toolWindow,
            command = null,
            title = displayName,
            userData = mapOf(HARNESS_ID_KEY to id),
        )
    }
}
