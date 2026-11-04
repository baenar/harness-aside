package com.github.baenar.harnessaside.core

import com.github.baenar.harnessaside.antigravity.AntigravityHarness
import com.github.baenar.harnessaside.claude.ClaudeHarness
import com.github.baenar.harnessaside.opencode.OpenCodeHarness

/**
 * Registry of supported AI coding harnesses.
 *
 * Provides a modular extension point for adding harnesses (Claude, Antigravity, OpenCode, etc.).
 */
object HarnessRegistry {

    private val harnesses = mutableMapOf<String, Harness>(
        TerminalHarness.id to TerminalHarness,
        ClaudeHarness.id to ClaudeHarness,
        AntigravityHarness.id to AntigravityHarness,
        OpenCodeHarness.id to OpenCodeHarness,
    )

    /** All currently registered harnesses. */
    val registered: List<Harness>
        get() = harnesses.values.toList()

    /** Register a new harness. */
    fun register(harness: Harness) {
        harnesses[harness.id] = harness
    }

    /** Find a harness by its id (e.g. "claude"). */
    fun find(id: String): Harness? = harnesses[id]

    /** The default harness to start when none is specified. */
    val default: Harness get() = ClaudeHarness
}
