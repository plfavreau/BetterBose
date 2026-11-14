package org.pilou.betterbose.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

private const val VIEWPORT = 24f
private const val STROKE = 1.8f

private fun circle(cx: Float, cy: Float, r: Float) =
    "M${cx - r},$cy a$r,$r 0 1,0 ${2 * r},0 a$r,$r 0 1,0 ${-2 * r},0"

private fun ellipse(cx: Float, cy: Float, rx: Float, ry: Float) =
    "M${cx - rx},$cy a$rx,$ry 0 1,0 ${2 * rx},0 a$rx,$ry 0 1,0 ${-2 * rx},0"

/** Builds a 24x24 outline icon (tinted by the caller) from SVG path strings. */
private fun outline(name: String, vararg paths: String, filled: List<String> = emptyList()): ImageVector =
    ImageVector.Builder(name, 24.dp, 24.dp, VIEWPORT, VIEWPORT).apply {
        paths.forEach {
            addPath(
                addPathNodes(it), stroke = SolidColor(Color.Black), strokeLineWidth = STROKE,
                strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round,
            )
        }
        filled.forEach { addPath(addPathNodes(it), fill = SolidColor(Color.Black)) }
    }.build()

object Icons {
    val quiet = outline("quiet", "M12 3a9 9 0 1 0 9 9", "M12 7a5 5 0 1 0 5 5", filled = listOf(circle(12f, 12f, 1.2f)))
    val aware = outline("aware", "M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12z", circle(12f, 12f, 3f))
    val immersion = outline("immersion", circle(12f, 12f, 9f), ellipse(12f, 12f, 4f, 9f), "M3 12h18")
    val cinema = outline("cinema", "M5 5h14a2 2 0 0 1 2 2v8a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2V7a2 2 0 0 1 2-2z", "M8 21h8")
    val custom = outline("custom", circle(12f, 12f, 9f), circle(12f, 12f, 4f))
    val chevron = outline("chevron", "M9 6l6 6-6 6")
    val close = outline("close", "M6 6l12 12", "M18 6L6 18")
    val bolt = outline("bolt", "M13 2L4 14h7l-1 8 9-12h-7l1-8z")
    val bluetooth = outline("bluetooth", "M7 7l10 10-5 5V2l5 5L7 17")
    val gear = outline(
        "gear", circle(12f, 12f, 3f),
        "M19.4 15a1.7 1.7 0 0 0 .3 1.8l.1.1a2 2 0 1 1-2.8 2.8l-.1-.1a1.7 1.7 0 0 0-1.8-.3 1.7 1.7 0 0 0-1 1.5V21" +
            "a2 2 0 1 1-4 0v-.1a1.7 1.7 0 0 0-1.1-1.5 1.7 1.7 0 0 0-1.8.3l-.1.1a2 2 0 1 1-2.8-2.8l.1-.1" +
            "a1.7 1.7 0 0 0 .3-1.8 1.7 1.7 0 0 0-1.5-1H3a2 2 0 1 1 0-4h.1a1.7 1.7 0 0 0 1.5-1.1 1.7 1.7 0 0 0-.3-1.8" +
            "l-.1-.1a2 2 0 1 1 2.8-2.8l.1.1a1.7 1.7 0 0 0 1.8.3H9a1.7 1.7 0 0 0 1-1.5V3a2 2 0 1 1 4 0v.1" +
            "a1.7 1.7 0 0 0 1 1.5 1.7 1.7 0 0 0 1.8-.3l.1-.1a2 2 0 1 1 2.8 2.8l-.1.1a1.7 1.7 0 0 0-.3 1.8V9" +
            "a1.7 1.7 0 0 0 1.5 1H21a2 2 0 1 1 0 4h-.1a1.7 1.7 0 0 0-1.5 1z",
    )

    fun forMode(name: String): ImageVector = when (name.lowercase()) {
        "quiet" -> quiet
        "aware" -> aware
        "immersion" -> immersion
        "cinema" -> cinema
        else -> custom
    }
}
