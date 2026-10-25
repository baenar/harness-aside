package com.github.baenar.harnessaside.claude

import com.github.baenar.harnessaside.core.HarnessStatus
import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

/**
 * Tool-window button icons featuring animated Manul:
 * - [DEFAULT] (`manul-default.svg`) — Manul idle / default tool window icon
 * - `manul_work_00.svg` .. `07.svg` — Manul working (thinking / tool execution)
 * - `manul_help_00.svg` .. `07.svg` — Manul needs help (waiting for approval / input)
 */
object ClaudeStatusIcons {

    const val WORK_FRAMES = 8
    const val HELP_FRAMES = 8

    /** Default / idle icon (also registered in plugin.xml). */
    val DEFAULT: Icon by lazy { load("manul-default") }
    val BASE: Icon get() = DEFAULT

    /** Dedicated Claude Code brand icon. */
    val CLAUDE: Icon by lazy { load("claude") }

    private val WORK: List<Icon> by lazy { (0 until WORK_FRAMES).map { load(frameName(ClaudeStatus.THINKING, it)) } }
    private val HELP_FRAMES_LIST: List<Icon> by lazy { (0 until HELP_FRAMES).map { load(frameName(ClaudeStatus.WAITING, it)) } }

    fun isAnimatedHarness(status: HarnessStatus?): Boolean =
        status == HarnessStatus.THINKING ||
            status == HarnessStatus.EXECUTING_TOOL ||
            status == HarnessStatus.WAITING

    fun forHarnessStatus(status: HarnessStatus?, frame: Int = 0): Icon = when (status) {
        HarnessStatus.THINKING,
        HarnessStatus.EXECUTING_TOOL -> WORK[frame.mod(WORK_FRAMES)]
        HarnessStatus.WAITING,
        HarnessStatus.ERROR -> HELP_FRAMES_LIST[frame.mod(HELP_FRAMES)]
        else -> DEFAULT
    }

    fun frameName(status: ClaudeStatus?, frame: Int = 0): String = when (status) {
        ClaudeStatus.THINKING -> "manul_work_%02d".format(frame.mod(WORK_FRAMES))
        ClaudeStatus.WAITING -> "manul_help_%02d".format(frame.mod(HELP_FRAMES))
        else -> "manul-default"
    }

    private fun load(name: String): Icon =
        IconLoader.getIcon("/icons/$name.svg", ClaudeStatusIcons::class.java)
}
