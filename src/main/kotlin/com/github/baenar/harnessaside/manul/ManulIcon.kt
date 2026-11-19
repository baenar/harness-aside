package com.github.baenar.harnessaside.manul

import com.github.baenar.harnessaside.core.HarnessStatus
import com.intellij.ui.JBColor
import com.intellij.util.ui.JBUI
import java.awt.AlphaComposite
import java.awt.BasicStroke
import java.awt.Color
import java.awt.Component
import java.awt.Graphics
import java.awt.Graphics2D
import java.awt.RenderingHints
import java.awt.Shape
import java.awt.geom.Ellipse2D
import java.awt.geom.Path2D
import javax.swing.Icon

/** One seed's manul, in every state a session can be in. EDT only. */
class Manul(seed: Int) {
    private val pick = ManulComposer.pick(seed)
    private val icons = HashMap<Pair<String, Int>, Icon>()

    /** [size] is in unscaled pixels. Like the tool window button, idle is still and the rest animate. */
    fun icon(status: HarnessStatus, frame: Int, size: Int): Icon {
        val name = when (status) {
            HarnessStatus.IDLE, HarnessStatus.DONE -> "manul-default"
            HarnessStatus.THINKING, HarnessStatus.EXECUTING_TOOL -> "manul_work_%02d".format(frame.mod(8))
            HarnessStatus.WAITING -> "manul_help_%02d".format(frame.mod(8))
        }
        return icons.getOrPut(name to size) { ManulIcon(pick, name, size) }
    }
}

/**
 * A composed manul, drawn with Java2D from the composer's SVG: no image files, sharp at any scale, and in the
 * current theme's colours. It reads only what [ManulComposer] writes: `path` (M L H V Q C Z, absolute and
 * relative), `circle`, `ellipse`, and `g` with fill, stroke, stroke-width or a translate.
 */
class ManulIcon(pick: ManulComposer.Pick, name: String, private val size: Int) : Icon {
    private val light by lazy { drawing(ManulComposer.render(name, pick, ManulComposer.LIGHT)) }
    private val dark by lazy { drawing(ManulComposer.render(name, pick, ManulComposer.DARK)) }

    override fun getIconWidth() = JBUI.scale(size)
    override fun getIconHeight() = JBUI.scale(size)

    override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
        val g2 = g.create() as Graphics2D
        try {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
            g2.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE)
            g2.translate(x, y)
            g2.scale(iconWidth / 24.0, iconHeight / 24.0)
            for (op in if (JBColor.isBright()) light else dark) op.paint(g2)
        } finally {
            g2.dispose()
        }
    }

    private class Op(val shape: Shape, val fill: Color?, val stroke: Color?, val width: Float, val opacity: Float) {
        fun paint(g: Graphics2D) {
            if (opacity < 1f) g.composite = AlphaComposite.getInstance(AlphaComposite.SRC_OVER, opacity)
            fill?.let { g.color = it; g.fill(shape) }
            stroke?.let {
                g.color = it
                g.stroke = BasicStroke(width, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND)
                g.draw(shape)
            }
            if (opacity < 1f) g.composite = AlphaComposite.SrcOver
        }
    }

    private companion object {
        private val TAG = Regex("<(/?)([a-z]+)([^>]*?)(/?)>")
        private val ATTRIBUTE = Regex("([a-z-]+)=\"([^\"]*)\"")
        private val TRANSLATE = Regex("translate\\(([-\\d.]+), ([-\\d.]+)\\)")

        /** Inherited attributes plus the translation, one level per open element. */
        private class Context(val attrs: Map<String, String>, val dx: Double, val dy: Double)

        fun drawing(svg: String): List<Op> {
            val ops = mutableListOf<Op>()
            val stack = ArrayDeque<Context>().apply { add(Context(emptyMap(), 0.0, 0.0)) }
            for (m in TAG.findAll(svg)) {
                val (closing, tag, rest, selfClosing) = m.destructured
                if (closing.isNotEmpty()) {
                    stack.removeLast()
                    continue
                }
                val parent = stack.last()
                val own = ATTRIBUTE.findAll(rest).associate { it.groupValues[1] to it.groupValues[2] }
                val move = own["transform"]?.let { TRANSLATE.find(it) }?.destructured
                val context = Context(
                    parent.attrs + own,
                    parent.dx + (move?.component1()?.toDouble() ?: 0.0),
                    parent.dy + (move?.component2()?.toDouble() ?: 0.0),
                )
                shape(tag, own, context)?.let { shape ->
                    val a = context.attrs
                    ops += Op(
                        shape,
                        a["fill"]?.let(::color),
                        a["stroke"]?.let(::color),
                        a["stroke-width"]?.toFloat() ?: 1f,
                        own["opacity"]?.toFloat() ?: 1f,
                    )
                }
                if (selfClosing.isEmpty()) stack.add(context)
            }
            return ops
        }

        private fun color(value: String): Color? = if (value == "none") null else Color.decode(value)

        private fun shape(tag: String, a: Map<String, String>, at: Context): Shape? {
            fun n(key: String) = a.getValue(key).toDouble()
            return when (tag) {
                "path" -> path(a.getValue("d"), at.dx, at.dy)
                "circle" -> Ellipse2D.Double(n("cx") - n("r") + at.dx, n("cy") - n("r") + at.dy, 2 * n("r"), 2 * n("r"))
                "ellipse" -> Ellipse2D.Double(n("cx") - n("rx") + at.dx, n("cy") - n("ry") + at.dy, 2 * n("rx"), 2 * n("ry"))
                else -> null
            }
        }

        private fun path(d: String, dx: Double, dy: Double): Path2D {
            val p = Path2D.Double()
            var x = 0.0
            var y = 0.0
            var startX = 0.0
            var startY = 0.0
            for ((c, a) in parse(d)) {
                val rel = c.isLowerCase()
                val ox = if (rel) x else 0.0
                val oy = if (rel) y else 0.0
                when (c.uppercaseChar()) {
                    'M' -> {
                        x = ox + a[0]; y = oy + a[1]; startX = x; startY = y
                        p.moveTo(x + dx, y + dy)
                    }
                    'L' -> { x = ox + a[0]; y = oy + a[1]; p.lineTo(x + dx, y + dy) }
                    'H' -> { x = ox + a[0]; p.lineTo(x + dx, y + dy) }
                    'V' -> { y = oy + a[0]; p.lineTo(x + dx, y + dy) }
                    'Q' -> {
                        p.quadTo(ox + a[0] + dx, oy + a[1] + dy, ox + a[2] + dx, oy + a[3] + dy)
                        x = ox + a[2]; y = oy + a[3]
                    }
                    'C' -> {
                        p.curveTo(ox + a[0] + dx, oy + a[1] + dy, ox + a[2] + dx, oy + a[3] + dy, ox + a[4] + dx, oy + a[5] + dy)
                        x = ox + a[4]; y = oy + a[5]
                    }
                    'Z' -> { p.closePath(); x = startX; y = startY }
                }
            }
            return p
        }
    }
}
