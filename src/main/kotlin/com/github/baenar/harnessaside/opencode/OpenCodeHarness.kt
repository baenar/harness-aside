package com.github.baenar.harnessaside.opencode

import com.github.baenar.harnessaside.core.Harness
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

/** Harness adapter for OpenCode (`opencode`). Status comes from [OpenCodeStatusPlugin]. */
object OpenCodeHarness : Harness {
    override val displayName = "OpenCode"
    override val executable = "opencode"
    override val icon: Icon by lazy { IconLoader.getIcon("/icons/opencode.svg", OpenCodeHarness::class.java) }

    override fun installStatusHooks(project: Project) {
        OpenCodeStatusPlugin.homes(project).forEach(OpenCodeStatusPlugin::install)
    }
}
