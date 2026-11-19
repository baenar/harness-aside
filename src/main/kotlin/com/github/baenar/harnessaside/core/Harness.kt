package com.github.baenar.harnessaside.core

import com.github.baenar.harnessaside.manul.ManulInk
import com.intellij.openapi.project.Project
import javax.swing.Icon

/**
 * An AI coding harness CLI runnable inside the dedicated tool window.
 *
 * Both functions run off the EDT, right before the terminal opens, so they may touch files.
 */
interface Harness {
    /** Stable identifier, saved with multi-session tabs. */
    val id: String

    /** Human-readable name, e.g. "Claude Code". */
    val displayName: String

    /** Binary on PATH, or null for a plain shell. */
    val executable: String?

    val icon: Icon

    /** What its multi-session tab's manul is painted with. */
    val manulInk: ManulInk get() = ManulInk.THEME

    /**
     * What gets typed into the new terminal; null leaves it at the prompt. [sessionId] is set for a
     * multi-session tab: a harness that can, keeps that tab on its own conversation.
     */
    fun launchCommand(project: Project, sessionId: String?): String? = executable

    /** Installs whatever pushes this harness's status to the IDE. */
    fun installStatusHooks(project: Project) {}
}
