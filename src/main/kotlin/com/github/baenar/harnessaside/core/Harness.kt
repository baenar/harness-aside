package com.github.baenar.harnessaside.core

import com.intellij.openapi.project.Project
import javax.swing.Icon

/**
 * An AI coding harness CLI runnable inside the dedicated tool window.
 *
 * Both functions run off the EDT, right before the terminal opens, so they may touch files.
 */
interface Harness {
    /** Human-readable name, e.g. "Claude Code". */
    val displayName: String

    /** Binary on PATH, or null for a plain shell. */
    val executable: String?

    val icon: Icon

    /** What gets typed into the new terminal; null leaves it at the prompt. */
    fun launchCommand(project: Project): String? = executable

    /** Installs whatever pushes this harness's status to the IDE. */
    fun installStatusHooks(project: Project) {}
}
