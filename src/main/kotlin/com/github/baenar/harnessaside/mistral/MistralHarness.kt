package com.github.baenar.harnessaside.mistral

import com.github.baenar.harnessaside.core.Harness
import com.github.baenar.harnessaside.manul.ManulInk
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader
import java.awt.Color
import java.awt.LinearGradientPaint
import javax.swing.Icon

/** Harness adapter for Mistral Vibe (`vibe`). Status comes from [MistralStatusHooks]. */
object MistralHarness : Harness {
    override val id = "mistral"
    override val displayName = "Mistral Vibe"
    override val executable = "vibe"
    override val icon: Icon by lazy { IconLoader.getIcon("/icons/mistral.svg", MistralHarness::class.java) }

    /** The logo's five bands, top to bottom, each a flat colour, over the manul's 24 units (as in the logo, 3 high from y=4.5). */
    override val manulInk = ManulInk(
        LinearGradientPaint(
            0f, 0f, 0f, 24f,
            floatArrayOf(0f, 0.3125f, 0.3126f, 0.4375f, 0.4376f, 0.5625f, 0.5626f, 0.6875f, 0.6876f, 1f),
            arrayOf(0xFFAF01, 0xFFAF01, 0xFF8204, 0xFF8204, 0xFA500F, 0xFA500F, 0xE51300, 0xE51300, 0xC4001D, 0xC4001D).map(::Color).toTypedArray(),
        ),
    )

    override fun installStatusHooks(project: Project) {
        MistralStatusHooks.forProject(project)?.install()
    }
}
