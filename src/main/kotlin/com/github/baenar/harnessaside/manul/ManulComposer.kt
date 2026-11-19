package com.github.baenar.harnessaside.manul

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Composes a manul from five parts, picked by a seed, and renders it as SVG.
 */
object ManulComposer {

    const val LIGHT = "#6C707E"
    const val DARK = "#CED0D6"

    data class Pick(val head: String, val ears: String, val eyes: String, val whiskers: String, val mouth: String)

    /** One random part per slot, from a seeded RNG. */
    fun pick(seed: Int): Pick {
        val rand = Mulberry32(seed)
        fun <T> one(parts: Map<String, T>) = parts.keys.elementAt((rand.next() * parts.size).toInt())
        return Pick(one(HEADS), one(EARS), one(EYES), one(WHISKERS), one(MOUTHS))
    }

    /** `manul-default`, or one of the 8 `manul_work_NN` and 8 `manul_help_NN` animation frames. */
    fun render(name: String, pick: Pick, color: String): String {
        if (name == "manul-default") {
            val label = "Manul — domyślne logo"
            return "${root("mn mn4", label, color)}\n<title>$label</title>\n<style>$DARK_RULE</style>\n${face(pick, color)}\n</svg>\n"
        }
        val i = name.takeLast(2).toInt()
        if (name.startsWith("manul_work_")) {
            val left = if (i % 2 == 0) "23" else "22.2"
            val right = if (i % 2 == 0) "22.2" else "23"
            val dots = DOT_CONFIGS[i]
            return "${root("mn mn4 mn4-work", "Manul pracuje (klatka $i)", color)}\n" +
                "${face(pick, color, move = EYE_OFFSETS[i], eyeGroup = "")}\n" +
                "<path stroke=\"$color\" fill=\"none\" d=\"M6.4 ${left}q1.6-1.6 3.2 0\"/>\n" +
                "<path stroke=\"$color\" fill=\"none\" d=\"M14.4 ${right}q1.6-1.6 3.2 0\"/>\n" +
                "<g fill=\"$color\" stroke=\"none\">\n" +
                listOf("9", "12", "15").zip(dots).joinToString("") { (cx, dot) ->
                    "<circle cx=\"$cx\" cy=\"${js(dot.first)}\" r=\"1.1\" opacity=\"${js(dot.second)}\" fill=\"$color\"/>\n"
                } +
                "</g>\n</svg>\n"
        }
        return "${root("mn mn4 mn4-help", "Manul potrzebuje pomocy (klatka $i)", color)}\n" +
            "<g transform=\"translate(${js(SHAKE_DX[i])}, 0)\">\n${face(pick, color, wide = true)}\n</g>\n" +
            "${if (i < 6) bang(color) else ""}\n</svg>\n"
    }

    // ------------------------------------------------------------------ catalog

    private class Head(
        val outer: Pt, val inner: Pt, val crown: String, val cheek: String, val chin: String,
        val eyesDx: Double, val eyesY: Double, val muzzle: Pt, val whisker: Pt,
    )

    private val HEADS = linkedMapOf(
        "classic" to Head(3.5 to 10.0, 9.5 to 8.0, "Q12 7.4 14.5 8", "L22 12.5L20.8 14L21.8 16.5L19.5 17.2", "Q12 21.5 4.5 17.2", 4.0, 11.5, 12.0 to 14.0, 5.4 to 14.4),
        "round" to Head(3.8 to 9.6, 9.5 to 7.9, "Q12 7.3 14.5 7.9", "C21.6 11 22 13 21.2 15", "C20 17.6 16.6 19.3 12 19.3C7.4 19.3 4 17.6 2.8 15", 4.0, 11.5, 12.0 to 14.0, 5.4 to 14.4),
        "pancake" to Head(3.5 to 10.8, 9.5 to 9.6, "Q12 9.3 14.5 9.6", "C21.6 11.6 22 12.8 22 14C22 16.6 20.2 17.8 17.4 18", "Q12 19.1 6.6 18", 5.2, 12.8, 12.0 to 13.2, 4.6 to 13.8),
        "fluffy" to Head(3.5 to 10.0, 9.5 to 8.0, "Q12 7.4 14.5 8", "L22 12L20.4 12.8L22 15L20.2 15.4L21.4 18L18.4 17.6", "Q12 21 5.6 17.6", 4.0, 11.5, 12.0 to 14.0, 5.8 to 14.0),
        "chonk" to Head(4.6 to 9.6, 9.5 to 8.2, "Q12 7.8 14.5 8.2", "C20.8 10.8 22 12.8 22 15", "C22 17.6 18 19.3 12 19.3C6 19.3 2 17.6 2 15", 3.8, 12.0, 12.0 to 14.2, 4.6 to 14.8),
        "heart" to Head(3.5 to 9.6, 9.5 to 7.9, "Q12 9 14.5 7.9", "C22 11 22.2 13.4 21 15.8", "C19.8 18 15.6 18.8 12 19.4C8.4 18.8 4.2 18 3 15.8", 4.0, 11.5, 12.0 to 14.0, 6.0 to 13.4),
        "shield" to Head(3.5 to 9.6, 9.5 to 8.0, "Q12 7.6 14.5 8", "L21.6 11L21.6 16.4", "L12 19.4L2.4 16.4", 4.0, 11.5, 12.0 to 13.7, 5.6 to 13.2),
        "hexagon" to Head(4.0 to 9.6, 9.5 to 8.0, "L14.5 8", "L22 13.6L17 19.2", "L7 19.2", 4.0, 11.5, 12.0 to 14.0, 6.0 to 14.2),
        "loaf" to Head(3.5 to 9.6, 9.5 to 8.4, "L14.5 8.4", "C21.4 10 22 10.8 22 12L22 16C22 18 20.6 19.2 18.6 19.2", "L5.4 19.2", 4.0, 11.8, 12.0 to 14.0, 5.0 to 14.4),
        "lion" to Head(3.5 to 10.0, 9.5 to 8.0, "Q12 7.4 14.5 8", "C21 12 21.2 14 22 16.8L18.6 17", "Q12 20.9 5.4 17", 4.0, 11.5, 12.0 to 14.0, 5.8 to 13.8),
        "cloud" to Head(3.5 to 10.0, 9.5 to 8.0, "Q12 7.4 14.5 8", "C22.2 10.2 22.3 13 20.8 13.4C22.3 13.6 22.3 16.6 20.4 16.8C20.4 18.2 18.6 18.8 17 18.2", "Q12 20.2 7 18.2", 4.0, 11.5, 12.0 to 14.0, 5.4 to 14.2),
        "bean" to Head(3.5 to 9.8, 9.5 to 7.8, "Q12 10.4 14.5 7.8", "C21.6 11 22 12.6 22 14.4C22 17.4 20.4 19 17.6 19", "L6.4 19", 4.0, 11.8, 12.0 to 14.0, 5.0 to 14.4),
        "wedge" to Head(3.5 to 9.8, 9.5 to 8.2, "Q12 7.8 14.5 8.2", "C21.6 10.2 22 10.8 22 11.6L18.4 18.2C18 18.9 17.2 19.2 16.4 19.2", "L7.6 19.2", 4.0, 11.5, 12.0 to 14.0, 6.0 to 13.6),
    )

    /** Left ear, from outer (3.5,10) to inner (9.5,8); the right ear is the mirror, reversed. */
    private class Ear(val d: String, val detail: String? = null, val right: String? = null, val rightDetail: String? = null)

    private const val POINTY = "C3.5 10 5 3.8 5 3.8C5 3.8 9.5 8 9.5 8"
    private const val FOLDED = "C3.5 10 5 7.8 5 7.8C5 7.8 3.2 6.6 3.2 6.6C3.2 6.6 6.6 4.8 6.6 4.8C7.8 4.8 8.8 6.4 9.5 8"
    private const val FOLD_LINE = "M6.6 4.8L5 7.8"

    private val EARS = linkedMapOf(
        "classic" to Ear("C3 7.5 4.5 5.5 6.5 5.5C8 5.5 9 6.5 9.5 8"),
        "nub" to Ear("C3.4 8.6 4.6 7.2 6 7.2C7.4 7.2 9 7.2 9.5 8"),
        "pointy" to Ear(POINTY),
        "tall" to Ear("C3.5 10 4.6 1.6 4.6 1.6C4.6 1.6 7.3 7.4 7.3 7.4C7.3 7.4 9.5 8 9.5 8"),
        "tufted" to Ear("C3.3 8.2 3.6 6.6 4.4 5.6C4.4 5.6 4.6 1.6 4.6 1.6C4.6 1.6 6 5 6 5C7.6 5 9 6.4 9.5 8"),
        "folded" to Ear(FOLDED, detail = FOLD_LINE),
        "notched" to Ear("C3.2 8 3.6 6 4.8 5.4C4.8 5.4 6.4 7.2 6.4 7.2C6.4 7.2 7.8 5.8 7.8 5.8C8.6 6.4 9.2 7 9.5 8"),
        "airplane" to Ear("C3.1 9.2 2.5 8.4 2 7.4C4.2 6.2 7.6 6.6 9.5 8"),
        "square" to Ear("C3.5 10 3.6 6.6 3.6 6.6C3.6 5.8 4.2 5.2 5 5.2C5 5.2 7 5.2 7 5.2C7.8 5.2 8.4 5.8 8.6 6.6C8.6 6.6 9.5 8 9.5 8"),
        "inner-arc" to Ear("C2.9 7.2 4.3 4.8 6.4 4.8C7.8 4.8 8.8 6.4 9.5 8", detail = "M5 8.9Q6.4 6.7 7.9 8.3"),
        "bat" to Ear("C2.6 7.8 2.2 4.6 3 1.8C5.4 3 8.4 5.4 9.5 8"),
        "torn" to Ear("C3.5 10 3.8 5.2 3.8 5.2C3.8 5.2 5.2 7.2 5.2 7.2C5.2 7.2 6.6 4.8 6.6 4.8C6.6 4.8 8 7 8 7C8 7 9.5 8 9.5 8"),
        "curled" to Ear("C4.6 8.4 4.8 6.2 2.8 4.8C5.6 3.4 8.4 5 9.5 8"),
        "mismatched" to Ear(POINTY, right = FOLDED, rightDetail = FOLD_LINE),
    )

    private sealed interface Shape
    private class Dot(val x: Double, val y: Double, val r: Double) : Shape
    private class Ell(val x: Double, val y: Double, val rx: Double, val ry: Double) : Shape
    private class Ring(val x: Double, val y: Double, val r: Double, val sw: Double = 1.2) : Shape
    private class Line(val d: String, val sw: Double = 1.4) : Shape
    private class Solid(val d: String) : Shape

    /** Authored around (0,0) for the left eye (+x points at the nose); the right eye is mirrored. */
    private class Eye(
        val left: List<Shape>,
        val right: List<Shape>? = null,
        /** The help frames' form; defaults to the idle one scaled by 1.3. */
        val wide: Eye? = null,
        val mid: ((dx: Double, k: Double) -> Shape)? = null,
    )

    private val EYES = linkedMapOf(
        "classic" to Eye(listOf(Dot(0.0, 0.0, 1.4)), wide = Eye(listOf(Dot(0.0, 0.0, 1.8)))),
        "big" to Eye(listOf(Dot(0.0, 0.0, 1.9))),
        "tiny" to Eye(listOf(Dot(0.0, 0.0, 1.0))),
        "ring" to Eye(listOf(Ring(0.0, 0.0, 1.4))),
        "ring-pupil" to Eye(listOf(Ring(0.0, 0.0, 1.7), Dot(0.0, 0.0, 0.9))),
        "slit" to Eye(listOf(Ell(0.0, 0.0, 0.9, 1.9))),
        "almond" to Eye(listOf(Solid("M-2.1 -.8Q-.2 -1.9 2.1 .8Q.2 1.9 -2.1 -.8Z"))),
        "squint" to Eye(listOf(Line("M-1 -1.1L1 0L-1 1.1", 1.2))),
        "dash" to Eye(listOf(Line("M-1.7 0L1.7 0", 1.5))),
        "happy" to Eye(listOf(Line("M-1.7 .8Q0 -1.6 1.7 .8"))),
        "sleepy" to Eye(listOf(Solid("M-1.8 -.4L1.8 -.4C1.8 .9 1 1.7 0 1.7C-1 1.7 -1.8 .9 -1.8 -.4Z"))),
        "angry" to Eye(listOf(Solid("M-1.8 -1.1L1.7 .3C1.5 1.2 .8 1.7 0 1.7C-1.2 1.7 -2 .4 -1.8 -1.1Z"))),
        "cross" to Eye(listOf(Line("M-1 -1L1 1M1 -1L-1 1", 1.2))),
        "wink" to Eye(listOf(Dot(0.0, 0.0, 1.4)), right = listOf(Line("M-1.7 .5Q0 -1.1 1.7 .5"))),
        "diamond" to Eye(listOf(Solid("M0 -1.7L1.7 0L0 1.7L-1.7 0Z"))),
        "side-eye" to Eye(listOf(Ring(0.0, 0.0, 1.6), Dot(0.5, 0.0, 0.9)), right = listOf(Ring(0.0, 0.0, 1.6), Dot(-0.5, 0.0, 0.9))),
        "mismatch" to Eye(listOf(Dot(0.0, 0.0, 1.9)), right = listOf(Dot(0.0, 0.0, 1.0))),
        "glasses" to Eye(listOf(Ring(0.0, 0.0, 1.7)), mid = { dx, k -> Line("M${js(-dx + 1.7 * k)} 0L${js(dx - 1.7 * k)} 0", 1.2) }),
    )

    /** Left side in classic coordinates, shifted by the head's anchor and mirrored. */
    private class Whisker(
        val d: String? = null, val sw: Double? = null,
        /** The left-authored form of the other side, for asymmetric variants. */
        val right: String? = null,
        val keepX: Boolean = false, val dots: List<Pt> = emptyList(),
    )

    private val WHISKERS = linkedMapOf(
        "classic" to Whisker("M5.4 14.4L6.6 16.4"),
        "none" to Whisker(),
        "single" to Whisker("M5 15L7.4 15"),
        "double" to Whisker("M5 14.2L7.2 14.2M5.2 16.2L7.4 16.2", 1.4),
        "triple" to Whisker("M5 13.8L7 13.8M5 15.3L7.2 15.3M5.4 16.8L7.4 16.8", 1.2),
        "fan" to Whisker("M7.6 14.8L5.2 13.6M7.6 15.1L5 15.2M7.6 15.4L5.6 16.8", 1.2),
        "droop" to Whisker("M7.6 14.2Q5.4 14.2 5.2 16.4", 1.4),
        "curl" to Whisker("M7.6 16.4Q5.4 16.4 5 14", 1.4),
        "long" to Whisker("M6.6 15L1.75 14.6", keepX = true),
        "pad-dots" to Whisker(dots = listOf(5.8 to 14.6, 8.0 to 16.4)),
        "stripes" to Whisker("M4.8 13.8Q6 13.4 7.2 14.2M5 16.2Q6.2 15.4 7.6 16.2", 1.3),
        "kinked" to Whisker("M4.9 14.2L7 15.1L5.4 16.6", 1.4),
        "asym" to Whisker("M5 15L7.4 15", 1.4, right = "M5 14.2L7.2 14.2M5.2 16.2L7.4 16.2"),
    )

    /** Classic coordinates, muzzle at (12,14). sym: left side, mirrored. mid: drawn as is. */
    private class Mouth(
        val sym: String? = null, val mid: String? = null,
        val nose: String? = null, val noNose: Boolean = false, val shapes: List<Shape> = emptyList(),
    )

    private const val NOSE = "M11 14h2l-1 1z"
    private const val W = "M12 15.4Q11.2 16.6 10 16"
    private const val HEART = "M12 15.5C11.2 15 10.2 14.3 10.2 13.4C10.2 12.6 11.4 12.4 12 13.3C12.6 12.4 13.8 12.6 13.8 13.4C13.8 14.3 12.8 15 12 15.5Z"

    private val MOUTHS = linkedMapOf(
        "classic" to Mouth(sym = W),
        "y" to Mouth(mid = "M12 15.4L12 16", sym = "M12 16L10.4 17.1"),
        "wide-w" to Mouth(sym = "M12 15.4Q11.2 17 9.9 16.6Q9.1 16.3 8.9 15.4"),
        "flat" to Mouth(sym = "M12 16.8L9.8 16.8"),
        "frown" to Mouth(sym = "M12 17Q11 15.6 9.8 17"),
        "smile" to Mouth(sym = "M12 17Q10 17 9.2 15.4"),
        "smirk" to Mouth(mid = "M10.4 16.6Q12.4 17.4 14.6 15.6"),
        "open" to Mouth(shapes = listOf(Ell(12.0, 16.7, 1.1, 0.9))),
        "gasp" to Mouth(nose = "M11 13.2h2l-1 1z", shapes = listOf(Ell(12.0, 16.4, 1.7, 1.5))),
        "fang" to Mouth(sym = W, mid = "M13 16.4L13.5 17.3L14.1 16.2"),
        "fangs" to Mouth(sym = W + "M11 16.4L10.5 17.3L9.9 16.2"),
        "blep" to Mouth(sym = W, shapes = listOf(Solid("M11.1 16.4L12.9 16.4L12.9 17.1Q12 18.5 11.1 17.1Z"))),
        "wavy" to Mouth(mid = "M9.6 16.9Q10.8 15.8 12 16.7Q13.2 17.6 14.4 16.5"),
        "grr" to Mouth(mid = "M9.2 16.4L10.6 17.4L12 16.4L13.4 17.4L14.8 16.4"),
        "heart-nose" to Mouth(sym = W, noNose = true, shapes = listOf(Solid(HEART))),
        "button" to Mouth(noNose = true, shapes = listOf(Dot(12.0, 15.0, 1.3))),
    )

    // ------------------------------------------------------------------ frames

    private val EYE_OFFSETS = listOf(0.0, 0.0, 1.0, 1.0, 0.0, 0.0, -1.0, -1.0)
    private val ON = 1.0 to 1.0
    private val OFF = 2.0 to 0.35
    private val DOT_CONFIGS = listOf(
        listOf(ON, OFF, OFF), listOf(OFF, ON, OFF), listOf(OFF, OFF, ON), listOf(OFF, OFF, OFF),
        listOf(ON, OFF, OFF), listOf(OFF, ON, OFF), listOf(OFF, OFF, ON), listOf(OFF, OFF, OFF),
    )
    private val SHAKE_DX = listOf(0.0, 0.7, 1.0, 0.7, 0.0, -0.7, -1.0, -0.7)
    private const val DARK_RULE = "@media(prefers-color-scheme:dark){.mn{color:#CED0D6}}"

    private fun root(cls: String, label: String, c: String) =
        "<svg xmlns=\"http://www.w3.org/2000/svg\" class=\"$cls\" color=\"$c\" viewBox=\"0 0 24 24\" width=\"20\" height=\"20\" " +
            "fill=\"none\" stroke=\"$c\" stroke-width=\"2\" stroke-linecap=\"round\" stroke-linejoin=\"round\" role=\"img\" aria-label=\"$label\">"

    private fun bang(c: String) =
        "<g class=\"bang\">\n<path stroke=\"$c\" fill=\"none\" d=\"M12 .9V2.4\"/>\n<circle cx=\"12\" cy=\"4.4\" r=\".9\" fill=\"$c\" stroke=\"none\"/>\n</g>"

    // ------------------------------------------------------------------ parts

    private fun face(pick: Pick, c: String, wide: Boolean = false, move: Double = 0.0, eyeGroup: String? = null): String {
        val head = HEADS.getValue(pick.head)
        val ear = EARS.getValue(pick.ears)
        val eye = eyes(pick, head, c, wide, move, grouped = eyeGroup != null)
        return listOf(
            "<path stroke=\"$c\" fill=\"none\" d=\"${outline(head, ear)}\"/>",
            earDetail(head, ear, c),
            whiskers(pick, head, c),
            if (eyeGroup != null) "<g ${eyeGroup}fill=\"$c\" stroke=\"none\">\n$eye\n</g>" else eye,
            mouth(pick, head, c),
        ).filter { it.isNotEmpty() }.joinToString("\n")
    }

    private fun outline(head: Head, ear: Ear): String {
        val back = mirrorBack(mx(head.outer), segsOf(head.cheek)).toMutableList()
        if (back.last().c == 'L') back.removeAt(back.lastIndex) // Z draws it
        return print(listOf(Seg('M', listOf(head.outer.first, head.outer.second)))) + segStr(fitted(ear.d, head)) + head.crown +
            segStr(mirrorBack(head.outer, fitted(ear.right ?: ear.d, head))) + head.cheek + head.chin + segStr(back) + "Z"
    }

    private fun earDetail(head: Head, ear: Ear, c: String): String {
        val right = if (ear.right != null) ear.rightDetail else ear.detail
        val d = (ear.detail?.let { segStr(fitted(it, head)) } ?: "") + (right?.let { flip(segStr(fitted(it, head))) } ?: "")
        return if (d.isEmpty()) "" else "<path stroke=\"$c\" stroke-width=\"1.2\" fill=\"none\" d=\"$d\"/>"
    }

    private fun eyes(pick: Pick, head: Head, c: String, wide: Boolean, move: Double, grouped: Boolean): String {
        val v = EYES.getValue(pick.eyes)
        val form = (if (wide) v.wide else null) ?: v
        val k = if (wide && v.wide == null) 1.3 else 1.0
        val dx = head.eyesDx
        fun side(shapes: List<Shape>, cx: Double, sx: Double) = shapes.map { draw(it, c, cx, head.eyesY, sx, k, grouped) }
        return (
            side(form.left, 12 - dx + move, 1.0) +
                side(form.right ?: form.left, 12 + dx + move, -1.0) +
                (v.mid?.let { side(listOf(it(dx, k)), 12 + move, 1.0) } ?: emptyList())
            ).joinToString("\n")
    }

    private fun whiskers(pick: Pick, head: Head, c: String): String {
        val v = WHISKERS.getValue(pick.whiskers)
        val dx = if (v.keepX) 0.0 else head.whisker.first - 5.4
        val dy = head.whisker.second - 14.4
        val out = mutableListOf<String>()
        if (v.d != null) {
            out += "<path stroke=\"$c\" stroke-width=\"${js(v.sw ?: 1.5)}\" fill=\"none\" d=\"${shift(v.d, dx, dy)}${flip(shift(v.right ?: v.d, dx, dy))}\"/>"
        }
        for ((x, y) in v.dots) for (px in listOf(x + dx, 24 - x - dx)) out += draw(Dot(px, y + dy, 0.9), c)
        return out.joinToString("\n")
    }

    private fun mouth(pick: Pick, head: Head, c: String): String {
        val v = MOUTHS.getValue(pick.mouth)
        val dy = head.muzzle.second - 14
        val out = mutableListOf<String>()
        if (!v.noNose) out += "<path stroke=\"$c\" fill=\"$c\" stroke-width=\"1.2\" d=\"${shift(v.nose ?: NOSE, 0.0, dy)}\"/>"
        val sym = v.sym?.let { shift(it, 0.0, dy) } ?: ""
        val d = sym + (if (sym.isEmpty()) "" else flip(sym)) + (v.mid?.let { shift(it, 0.0, dy) } ?: "")
        if (d.isNotEmpty()) out += "<path stroke=\"$c\" stroke-width=\"1.4\" fill=\"none\" d=\"$d\"/>"
        for (s in v.shapes) out += draw(s, c, cy = dy)
        return out.joinToString("\n")
    }

    private fun draw(s: Shape, c: String, cx: Double = 0.0, cy: Double = 0.0, sx: Double = 1.0, k: Double = 1.0, grouped: Boolean = false): String {
        val fill = "fill=\"$c\"" + if (grouped) "" else " stroke=\"none\""
        fun x(n: Double) = num(cx + sx * k * n)
        fun y(n: Double) = num(cy + k * n)
        return when (s) {
            is Dot -> "<circle cx=\"${x(s.x)}\" cy=\"${y(s.y)}\" r=\"${num(k * s.r)}\" $fill/>"
            is Ell -> "<ellipse cx=\"${x(s.x)}\" cy=\"${y(s.y)}\" rx=\"${num(k * s.rx)}\" ry=\"${num(k * s.ry)}\" $fill/>"
            is Ring -> "<circle cx=\"${x(s.x)}\" cy=\"${y(s.y)}\" r=\"${num(k * s.r)}\" stroke=\"$c\" stroke-width=\"${js(s.sw)}\" fill=\"none\"/>"
            is Line -> "<path stroke=\"$c\" stroke-width=\"${js(s.sw)}\" fill=\"none\" d=\"${xf(s.d, sx * k, k, cx, cy)}\"/>"
            is Solid -> "<path $fill d=\"${xf(s.d, sx * k, k, cx, cy)}\"/>"
        }
    }

    // ------------------------------------------------------------------ path helpers

    private class PSeg(val c: Char, val p: List<Pt>)

    private fun segsOf(d: String) = parse(d).map { s -> PSeg(s.c, s.a.chunked(2) { it[0] to it[1] }) }
    private fun segStr(segs: List<PSeg>) = print(segs.map { s -> Seg(s.c, s.p.flatMap { listOf(it.first, it.second) }) })
    private fun mx(p: Pt) = (24 - p.first) to p.second

    /** The run of [segs] (which starts at [start]) mirrored around x=12 and walked backwards. */
    private fun mirrorBack(start: Pt, segs: List<PSeg>): List<PSeg> {
        val from = listOf(start) + segs.map { it.p.last() }
        return segs.mapIndexed { i, s -> PSeg(s.c, (s.p.dropLast(1).reversed() + from[i]).map(::mx)) }.reversed()
    }

    /**
     * Ears are drawn against the classic base (3.5,10)->(9.5,8), then re-expressed as (position along the base,
     * height above it) on the target head's own base.
     */
    private fun fit(q: Pt, head: Head): Pt {
        val (x, y) = q
        val u = (x - 3.5) / 6
        val h = 10 - 2 * u - y
        return (head.outer.first + u * (head.inner.first - head.outer.first)) to
            (head.outer.second + u * (head.inner.second - head.outer.second) - h)
    }

    private fun fitted(d: String, head: Head) = segsOf(d).map { s -> PSeg(s.c, s.p.map { fit(it, head) }) }

    /** x' = sx*x + tx, y' = sy*y + ty. Relative commands only scale. */
    private fun xf(d: String, sx: Double, sy: Double, tx: Double, ty: Double) = print(
        parse(d).map { (c, a) ->
            val abs = c < 'a'
            val vert = c == 'V' || c == 'v'
            val horiz = c == 'H' || c == 'h'
            Seg(c, a.mapIndexed { i, n ->
                if (vert || (!horiz && i % 2 == 1)) sy * n + (if (abs) ty else 0.0) else sx * n + (if (abs) tx else 0.0)
            })
        },
    )

    private fun shift(d: String, dx: Double, dy: Double) = xf(d, 1.0, 1.0, dx, dy)
    private fun flip(d: String) = xf(d, -1.0, 1.0, 24.0, 0.0)
    private fun print(segs: List<Seg>) = segs.joinToString("") { s -> s.c + s.a.joinToString(" ") { num(it) } }

    /** JavaScript's `String(+n.toFixed(2))`: half away from zero on the exact binary value, no trailing zeros. */
    private fun num(n: Double): String =
        BigDecimal(n).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString().let { if (it == "-0") "0" else it }

    /** A number as JavaScript prints it in a template literal, for the values left unrounded. */
    private fun js(n: Double): String = if (n % 1.0 == 0.0) n.toLong().toString() else n.toString()

    private class Mulberry32(private var seed: Int) {
        fun next(): Double {
            seed += 0x6D2B79F5
            var t = (seed xor (seed ushr 15)) * (1 or seed)
            t = (t + (t xor (t ushr 7)) * (61 or t)) xor t
            return (t xor (t ushr 14)).toUInt().toDouble() / 4294967296.0
        }
    }
}

internal typealias Pt = Pair<Double, Double>

/** One path command and its numbers, from a `d` attribute. */
internal data class Seg(val c: Char, val a: List<Double>)

private val SEGMENT = Regex("([a-zA-Z])([^a-zA-Z]*)")
private val NUMBER = Regex("-?\\d*\\.?\\d+")

internal fun parse(d: String): List<Seg> = SEGMENT.findAll(d).map { m ->
    Seg(m.groupValues[1][0], NUMBER.findAll(m.groupValues[2]).map { it.value.toDouble() }.toList())
}.toList()
