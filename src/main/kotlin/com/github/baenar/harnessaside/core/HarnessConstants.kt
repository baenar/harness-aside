package com.github.baenar.harnessaside.core

/** Identifier of the right-docked tool window in plugin.xml. */
const val TOOL_WINDOW_ID = "Harness Aside"

/**
 * POSIX hook command reporting [state] to [HarnessStatusServer]. It does nothing outside our terminal and
 * never fails, because a failing hook could break the harness running it.
 */
fun postStatusCommand(state: String): String =
    "[ -n \"\$HARNESS_ASIDE_IDE_PORT\" ] && curl -s -m 2 --connect-timeout 1 -X POST \"http://127.0.0.1:\$HARNESS_ASIDE_IDE_PORT/status?state=$state&token=\$HARNESS_ASIDE_IDE_TOKEN\" >/dev/null 2>&1 || true"
