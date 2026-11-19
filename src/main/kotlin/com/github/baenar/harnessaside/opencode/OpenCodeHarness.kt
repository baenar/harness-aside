package com.github.baenar.harnessaside.opencode

import com.github.baenar.harnessaside.core.Harness
import com.github.baenar.harnessaside.manul.ManulInk
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader
import java.awt.Color
import javax.swing.Icon

/** Harness adapter for OpenCode (`opencode`). Status comes from [OpenCodeStatusPlugin]. */
object OpenCodeHarness : Harness {
    override val id = "opencode"
    override val displayName = "OpenCode"
    override val executable = "opencode"
    override val icon: Icon by lazy { IconLoader.getIcon("/icons/opencode.svg", OpenCodeHarness::class.java) }
    override val manulInk = ManulInk(Color(0xF1ECEC), shadow = Color(0x4B, 0x46, 0x46, 0x99)) // the logo's white and gray

    override fun installStatusHooks(project: Project) {
        OpenCodeStatusPlugin.homes(project).forEach(OpenCodeStatusPlugin::install)
    }
}
