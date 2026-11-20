package com.github.baenar.harnessaside.antigravity

import com.github.baenar.harnessaside.core.Harness
import com.github.baenar.harnessaside.manul.ManulInk
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader
import java.awt.Color
import java.awt.RadialGradientPaint
import java.awt.TexturePaint
import java.awt.geom.Rectangle2D
import java.awt.image.BufferedImage
import javax.swing.Icon

/** Harness adapter for Antigravity CLI (`agy`). */
object AntigravityHarness : Harness {
    override val id = "antigravity"
    override val displayName = "Antigravity CLI"
    override val executable = "agy"
    override val icon: Icon by lazy { IconLoader.getIcon("/icons/antigravity.svg", AntigravityHarness::class.java) }

    /** The logo's blurred colours over its blue, laid across the manul's 24 units. */
    override val manulInk: ManulInk by lazy {
        val n = 96
        val image = BufferedImage(n, n, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        g.color = Color(0x3186FF)
        g.fillRect(0, 0, n, n)
        fun blob(x: Float, y: Float, r: Float, rgb: Int) {
            g.paint = RadialGradientPaint(x * n, y * n, r * n, floatArrayOf(0f, 1f), arrayOf(Color(rgb), Color(rgb, true)))
            g.fillRect(0, 0, n, n)
        }
        blob(0.1f, 0.4f, 0.45f, 0x00B95C) // green, left
        blob(0.3f, 0.15f, 0.4f, 0xFFE432) // yellow, top left
        blob(0.55f, 0.05f, 0.35f, 0xFBBC04) // amber, top
        blob(0.9f, 0.2f, 0.4f, 0xFC413D) // red, top right
        blob(0.85f, 0.55f, 0.3f, 0x749BFF) // light blue, right
        g.dispose()
        ManulInk(TexturePaint(image, Rectangle2D.Double(0.0, 0.0, 24.0, 24.0)))
    }

    override fun installStatusHooks(project: Project) {
        AntigravityStatusHooks.forProject(project)?.install()
    }
}
