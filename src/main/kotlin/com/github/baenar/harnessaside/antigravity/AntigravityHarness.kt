package com.github.baenar.harnessaside.antigravity

import com.github.baenar.harnessaside.core.HARNESS_ID_KEY
import com.github.baenar.harnessaside.core.Harness
import com.github.baenar.harnessaside.core.HarnessStatusManager
import com.github.baenar.harnessaside.core.HarnessStatusServer
import com.github.baenar.harnessaside.core.HarnessTerminalService
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.wm.ToolWindow
import javax.swing.Icon

/**
 * Harness adapter for Antigravity CLI (`agy`).
 */
object AntigravityHarness : Harness {
    override val id: String = "antigravity"
    override val displayName: String = "Antigravity CLI"
    override val executable: String = "agy"
    override val icon: Icon by lazy {
        IconLoader.getIcon("/icons/antigravity.svg", AntigravityHarness::class.java)
    }

    override fun openSession(
        project: Project,
        toolWindow: ToolWindow,
        continueSession: Boolean,
        sessionId: String?,
    ) {
        project.service<HarnessStatusServer>().ensureStarted()
        project.service<HarnessStatusManager>().ensureStarted()
        setupStatusHooks(project)
        val terminalService = project.service<HarnessTerminalService>()
        terminalService.openTerminalTab(
            toolWindow = toolWindow,
            command = executable,
            title = displayName,
            userData = mapOf(HARNESS_ID_KEY to id),
        )
    }

    override fun setupStatusHooks(project: Project) {
        val hooks = AntigravityStatusHooks.forProject(project) ?: return
        hooks.install()
    }
}
