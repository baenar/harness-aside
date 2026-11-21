package com.github.baenar.harnessaside.core

import com.github.baenar.harnessaside.claude.ClaudeSessions

/** Identifier of the right-docked tool window in plugin.xml. */
const val TOOL_WINDOW_ID = "Harness Aside"

/**
 * POSIX hook command reporting [state] to [HarnessStatusServer]. It does nothing outside our terminal and
 * never fails, because a failing hook could break the harness running it.
 */
fun postStatusCommand(state: String): String =
    "[ -n \"\$HARNESS_ASIDE_IDE_PORT\" ] && curl -s -m 2 --connect-timeout 1 -X POST \"http://127.0.0.1:\$HARNESS_ASIDE_IDE_PORT/status?state=$state&token=\$HARNESS_ASIDE_IDE_TOKEN\" >/dev/null 2>&1 || true"

/**
 * True when the hook config under [userHome] is read by a harness running natively on Windows. A Windows IDE with
 * the project inside WSL writes to the distro's home, where the harness runs its hooks under `sh`.
 */
fun isWindowsTarget(osName: String, userHome: String): Boolean =
    osName.startsWith("Windows", ignoreCase = true) && !ClaudeSessions.isWsl(userHome)
