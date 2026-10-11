package com.github.baenar.harnessaside.core

import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindow
import javax.swing.Icon

/**
 * Common contract for an AI coding harness CLI runnable inside the dedicated tool window.
 */
interface Harness {
    /** Unique identifier for the harness, e.g. "claude", "antigravity", "opencode". */
    val id: String

    /** Human-readable display name, e.g. "Claude Code", "Antigravity CLI", "OpenCode". */
    val displayName: String

    /** Default binary name on PATH. */
    val executable: String

    /** Default icon representing this harness. */
    val icon: Icon?

    /** Starts or resumes a session of this harness in [toolWindow]. */
    fun openSession(
        project: Project,
        toolWindow: ToolWindow,
        continueSession: Boolean = true,
        sessionId: String? = null,
    )

    /** Offers or sets up status hooks / integrations for this harness, if applicable. */
    fun setupStatusHooks(project: Project) {}
}
