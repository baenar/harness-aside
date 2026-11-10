package com.github.baenar.harnessaside.core

import com.intellij.icons.AllIcons
import javax.swing.Icon

/** A plain shell in the side tool window: no CLI started, no hooks installed. */
object TerminalHarness : Harness {
    override val displayName = "Terminal"
    override val executable: String? = null
    override val icon: Icon get() = AllIcons.Nodes.Console
}
