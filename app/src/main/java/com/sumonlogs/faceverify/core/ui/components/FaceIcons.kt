package com.sumonlogs.faceverify.core.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Stroke icons from the design (24×24 viewBox). SVG circles/rects are written as arc paths. */
enum class FaceGlyph(vararg val paths: String) {
    Close("M18 6L6 18M6 6l12 12"),
    Back("M15 18l-6-6 6-6"),
    Forward("M9 18l6-6-6-6"),
    Up("M6 15l6-6 6 6"),
    Person("M8,9a4,4 0 1,0 8,0a4,4 0 1,0 -8,0", "M5 21c1.5-4 4-6 7-6s5.5 2 7 6"),
    Check("M5 12l5 5 9-10"),
    Trash("M3 6h18", "M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6", "M10 11v6M14 11v6", "M9 6V4a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2"),
    Sun(
        "M8,12a4,4 0 1,0 8,0a4,4 0 1,0 -8,0",
        "M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4",
    ),
    Glasses(
        "M3,14a3.5,3.5 0 1,0 7,0a3.5,3.5 0 1,0 -7,0",
        "M14,14a3.5,3.5 0 1,0 7,0a3.5,3.5 0 1,0 -7,0",
        "M10 14h4M3 14l1-6M21 14l-1-6",
    ),
    Phone("M9,2h6a2,2 0 0 1 2,2v16a2,2 0 0 1 -2,2h-6a2,2 0 0 1 -2,-2v-16a2,2 0 0 1 2,-2z", "M11 18h2"),
    Camera(
        "M4,7h3l2,-3h6l2,3h3a1,1 0 0 1 1,1v11a1,1 0 0 1 -1,1h-16a1,1 0 0 1 -1,-1v-11a1,1 0 0 1 1,-1z",
        "M8.5,13a3.5,3.5 0 1,0 7,0a3.5,3.5 0 1,0 -7,0",
    ),
}

@Composable
fun FaceIcon(
    glyph: FaceGlyph,
    contentDescription: String?,
    size: Dp,
    strokeWidth: Float,
    tint: Color,
    modifier: Modifier = Modifier,
) {
    val vector = remember(glyph, strokeWidth) {
        ImageVector.Builder(
            name = glyph.name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).apply {
            glyph.paths.forEach { data ->
                addPath(
                    pathData = addPathNodes(data),
                    stroke = SolidColor(Color.Black),
                    strokeLineWidth = strokeWidth,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()
    }
    Icon(imageVector = vector, contentDescription = contentDescription, tint = tint, modifier = modifier.size(size))
}
