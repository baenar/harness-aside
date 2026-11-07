package com.github.baenar.harnessaside.core

import com.github.baenar.harnessaside.antigravity.AntigravityHarness
import com.github.baenar.harnessaside.claude.ClaudeHarness
import com.github.baenar.harnessaside.opencode.OpenCodeHarness

/** The harnesses the launcher offers, in display order. */
object HarnessRegistry {
    val registered: List<Harness> = listOf(TerminalHarness, ClaudeHarness, AntigravityHarness, OpenCodeHarness)
}
