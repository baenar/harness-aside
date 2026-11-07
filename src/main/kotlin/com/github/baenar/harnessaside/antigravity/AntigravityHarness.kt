package com.github.baenar.harnessaside.antigravity

import com.github.baenar.harnessaside.core.Harness
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

/** Harness adapter for Antigravity CLI (`agy`). */
object AntigravityHarness : Harness {
    override val displayName = "Antigravity CLI"
    override val executable = "agy"
    override val icon: Icon by lazy { IconLoader.getIcon("/icons/antigravity.svg", AntigravityHarness::class.java) }

    override fun installStatusHooks(project: Project) {
        AntigravityStatusHooks.forProject(project)?.install()
    }
}
