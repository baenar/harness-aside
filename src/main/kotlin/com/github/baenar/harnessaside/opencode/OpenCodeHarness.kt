package com.github.baenar.harnessaside.opencode

import com.github.baenar.harnessaside.core.HARNESS_ID_KEY
import com.github.baenar.harnessaside.core.Harness
import com.github.baenar.harnessaside.core.HarnessStatusManager
import com.github.baenar.harnessaside.core.HarnessTerminalService
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.wm.ToolWindow
import javax.swing.Icon

/** Harness adapter for OpenCode (`opencode`). Status comes from [OpenCodeStatusPlugin]. */
object OpenCodeHarness : Harness {
    override val id: String = "opencode"
    override val displayName: String = "OpenCode"
    override val executable: String = "opencode"
    override val icon: Icon by lazy {
        IconLoader.getIcon("/icons/opencode.svg", OpenCodeHarness::class.java)
    }

    override fun openSession(
        project: Project,
        toolWindow: ToolWindow,
        continueSession: Boolean,
        sessionId: String?,
    ) {
        project.service<HarnessStatusManager>().ensureStarted()
        setupStatusHooks(project)
        project.service<HarnessTerminalService>().openTerminalTab(
            toolWindow = toolWindow,
            command = executable,
            title = displayName,
            userData = mapOf(HARNESS_ID_KEY to id),
        )
    }

    override fun setupStatusHooks(project: Project) {
        OpenCodeStatusPlugin.homes(project).forEach(OpenCodeStatusPlugin::install)
    }
}
