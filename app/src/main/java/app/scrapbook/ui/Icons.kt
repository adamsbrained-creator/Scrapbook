package app.scrapbook.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/** Small line icons drawn in code, so the app needs no icon library. kind: menu, search, more, list, numbered, link, grid, folder, chevron, newfile, sort, down, up, star, edit, clock, sparkle, sliders, info */
@Composable
fun Ic(kind: String, dp: Dp = 24.dp, color: Color = Sb.ink) {
    Canvas(Modifier.size(dp)) {
        val w = size.width; val sw = w * 0.085f
        val st = Stroke(width = sw, cap = StrokeCap.Round, join = StrokeJoin.Round)
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) = drawLine(color, Offset(x1 * w, y1 * w), Offset(x2 * w, y2 * w), strokeWidth = sw, cap = StrokeCap.Round)
        fun dot(x: Float, y: Float, r: Float) = drawCircle(color, r * w, Offset(x * w, y * w))
        when (kind) {
            "menu" -> { line(.15f, .27f, .85f, .27f); line(.15f, .5f, .62f, .5f); line(.15f, .73f, .85f, .73f) }
            "search" -> { drawCircle(color, .26f * w, Offset(.44f * w, .44f * w), style = st); line(.64f, .64f, .85f, .85f) }
            "more" -> { dot(.5f, .2f, .07f); dot(.5f, .5f, .07f); dot(.5f, .8f, .07f) }
            "list" -> { line(.38f, .28f, .85f, .28f); line(.38f, .5f, .85f, .5f); line(.38f, .72f, .85f, .72f); dot(.17f, .28f, .05f); dot(.17f, .5f, .05f); dot(.17f, .72f, .05f) }
            "grid" -> listOf(.14f to .14f, .56f to .14f, .14f to .56f, .56f to .56f).forEach { (x, y) ->
                drawRoundRect(color, Offset(x * w, y * w), Size(.3f * w, .3f * w), CornerRadius(.07f * w), style = st)
            }
            "folder" -> { drawRoundRect(color, Offset(.1f * w, .26f * w), Size(.8f * w, .57f * w), CornerRadius(.1f * w), style = st); line(.14f, .2f, .4f, .2f) }
            "chevdown" -> { line(.28f, .4f, .5f, .62f); line(.5f, .62f, .72f, .4f) }
            "chevron" -> { line(.38f, .25f, .63f, .5f); line(.63f, .5f, .38f, .75f) }
            "newfile" -> {
                val p = Path().apply { moveTo(.22f * w, .1f * w); lineTo(.58f * w, .1f * w); lineTo(.8f * w, .32f * w); lineTo(.8f * w, .5f * w); moveTo(.22f * w, .1f * w); lineTo(.22f * w, .9f * w); lineTo(.5f * w, .9f * w) }
                drawPath(p, color, style = st); line(.62f, .62f, .62f, .86f); line(.5f, .74f, .74f, .74f)
            }
            "sort" -> { line(.16f, .2f, .16f, .8f); line(.07f, .7f, .16f, .8f); line(.25f, .7f, .16f, .8f); line(.38f, .3f, .85f, .3f); line(.38f, .5f, .7f, .5f); line(.38f, .7f, .55f, .7f) }
            "down" -> { line(.5f, .2f, .5f, .8f); line(.3f, .6f, .5f, .8f); line(.7f, .6f, .5f, .8f) }
            "up" -> { line(.5f, .8f, .5f, .2f); line(.3f, .4f, .5f, .2f); line(.7f, .4f, .5f, .2f) }
            "back" -> { line(.62f, .22f, .36f, .5f); line(.36f, .5f, .62f, .78f) }
            "plus" -> { line(.5f, .2f, .5f, .8f); line(.2f, .5f, .8f, .5f) }
            "book" -> { drawRoundRect(color, Offset(.1f * w, .2f * w), Size(.8f * w, .62f * w), CornerRadius(.1f * w), style = st); line(.5f, .22f, .5f, .8f) }
            "check" -> { drawRoundRect(color, Offset(.14f * w, .14f * w), Size(.72f * w, .72f * w), CornerRadius(.16f * w), style = st); line(.31f, .5f, .45f, .64f); line(.45f, .64f, .70f, .36f) }
            "image" -> {
                drawRoundRect(color, Offset(.08f * w, .26f * w), Size(.64f * w, .58f * w), CornerRadius(.1f * w), style = st)
                dot(.26f, .45f, .05f); line(.1f, .78f, .34f, .56f); line(.34f, .56f, .48f, .68f); line(.48f, .68f, .6f, .58f)
                line(.82f, .1f, .82f, .34f); line(.7f, .22f, .94f, .22f)
            }
            "undo" -> {
                val p = Path().apply { moveTo(.34f * w, .26f * w); lineTo(.16f * w, .44f * w); lineTo(.34f * w, .62f * w); moveTo(.16f * w, .44f * w); lineTo(.58f * w, .44f * w); quadraticBezierTo(.86f * w, .44f * w, .86f * w, .68f * w); quadraticBezierTo(.86f * w, .8f * w, .74f * w, .82f * w) }
                drawPath(p, color, style = st)
            }
            "redo" -> {
                val p = Path().apply { moveTo(.66f * w, .26f * w); lineTo(.84f * w, .44f * w); lineTo(.66f * w, .62f * w); moveTo(.84f * w, .44f * w); lineTo(.42f * w, .44f * w); quadraticBezierTo(.14f * w, .44f * w, .14f * w, .68f * w); quadraticBezierTo(.14f * w, .8f * w, .26f * w, .82f * w) }
                drawPath(p, color, style = st)
            }
            "none" -> { drawCircle(color, .34f * w, Offset(.5f * w, .5f * w), style = st); line(.28f, .72f, .72f, .28f) }
            "share" -> {
                line(.28f, .5f, .72f, .26f); line(.28f, .5f, .72f, .74f)
                dot(.26f, .5f, .1f); dot(.74f, .25f, .1f); dot(.74f, .75f, .1f)
            }
            "star" -> {
                val p = Path()
                for (i in 0 until 10) {
                    val a = Math.toRadians(-90.0 + i * 36.0); val r = if (i % 2 == 0) .46f else .2f
                    val x = (.5f + r * cos(a).toFloat()) * w; val y = (.52f + r * sin(a).toFloat()) * w
                    if (i == 0) p.moveTo(x, y) else p.lineTo(x, y)
                }
                p.close(); drawPath(p, color)
            }
            "link" -> rotate(-45f, Offset(.5f * w, .5f * w)) {
                drawRoundRect(color, Offset(.06f * w, .38f * w), Size(.52f * w, .24f * w), CornerRadius(.12f * w), style = st)
                drawRoundRect(color, Offset(.42f * w, .38f * w), Size(.52f * w, .24f * w), CornerRadius(.12f * w), style = st)
            }
            "numbered" -> {
                fun p(x: Float, y: Float) = Offset(x / 24f * w, y / 24f * w)
                line(.46f, 7f / 24f, .88f, 7f / 24f); line(.46f, .5f, .88f, .5f); line(.46f, 17f / 24f, .88f, 17f / 24f)
                val one = Path().apply { moveTo(p(4f, 5.5f).x, p(4f, 5.5f).y); lineTo(p(5.5f, 4.5f).x, p(5.5f, 4.5f).y); lineTo(p(5.5f, 9.5f).x, p(5.5f, 9.5f).y) }
                drawPath(one, color, style = st)
                val two = Path().apply {
                    moveTo(p(3.8f, 13.7f).x, p(3.8f, 13.7f).y)
                    cubicTo(p(4.4f, 12.7f).x, p(4.4f, 12.7f).y, p(6.4f, 12.8f).x, p(6.4f, 12.8f).y, p(6.3f, 14.3f).x, p(6.3f, 14.3f).y)
                    cubicTo(p(6.2f, 15.2f).x, p(6.2f, 15.2f).y, p(5f, 15.8f).x, p(5f, 15.8f).y, p(3.8f, 17.3f).x, p(3.8f, 17.3f).y)
                    lineTo(p(6.5f, 17.3f).x, p(6.5f, 17.3f).y)
                }
                drawPath(two, color, style = st)
            }
            "edit" -> {
                drawRoundRect(color, Offset(.14f * w, .22f * w), Size(.6f * w, .64f * w), CornerRadius(.1f * w), style = st)
                line(.44f, .6f, .84f, .2f); line(.38f, .66f, .44f, .6f)
            }
            "clock" -> { drawCircle(color, .37f * w, Offset(.5f * w, .5f * w), style = st); line(.5f, .28f, .5f, .5f); line(.5f, .5f, .66f, .6f) }
            "sparkle" -> {
                val p = Path().apply {
                    moveTo(.5f * w, .1f * w); quadraticBezierTo(.55f * w, .45f * w, .9f * w, .5f * w)
                    quadraticBezierTo(.55f * w, .55f * w, .5f * w, .9f * w); quadraticBezierTo(.45f * w, .55f * w, .1f * w, .5f * w)
                    quadraticBezierTo(.45f * w, .45f * w, .5f * w, .1f * w); close()
                }
                drawPath(p, color, style = st)
            }
            "sliders" -> {
                line(.14f, .3f, .26f, .3f); line(.46f, .3f, .86f, .3f); drawCircle(color, .1f * w, Offset(.36f * w, .3f * w), style = st)
                line(.14f, .7f, .54f, .7f); line(.74f, .7f, .86f, .7f); drawCircle(color, .1f * w, Offset(.64f * w, .7f * w), style = st)
            }
            "info" -> { drawCircle(color, .38f * w, Offset(.5f * w, .5f * w), style = st); dot(.5f, .34f, .045f); line(.5f, .47f, .5f, .7f) }
        }
    }
}
