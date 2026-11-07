package com.github.baenar.harnessaside.core

import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

/**
 * Tool-window button icons featuring animated Manul:
 * - `manul-default.svg`: idle (also registered in plugin.xml)
 * - `manul_work_00.svg` .. `07.svg`: working (thinking / tool execution)
 * - `manul_help_00.svg` .. `07.svg`: needs help (waiting for approval / input)
 */
object StatusIcons {

    private const val FRAMES = 8

    private val DEFAULT: Icon by lazy { load("manul-default") }
    private val WORK: List<Icon> by lazy { frames("work") }
    private val HELP: List<Icon> by lazy { frames("help") }

    fun isAnimated(status: HarnessStatus): Boolean = status != HarnessStatus.IDLE

    fun forStatus(status: HarnessStatus, frame: Int): Icon = when (status) {
        HarnessStatus.IDLE -> DEFAULT
        HarnessStatus.THINKING, HarnessStatus.EXECUTING_TOOL -> WORK[frame.mod(FRAMES)]
        HarnessStatus.WAITING -> HELP[frame.mod(FRAMES)]
    }

    private fun frames(kind: String): List<Icon> = List(FRAMES) { load("manul_${kind}_%02d".format(it)) }

    private fun load(name: String): Icon = IconLoader.getIcon("/icons/$name.svg", StatusIcons::class.java)
}
