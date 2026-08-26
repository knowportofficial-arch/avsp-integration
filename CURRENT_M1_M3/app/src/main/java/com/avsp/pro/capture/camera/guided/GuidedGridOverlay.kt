package com.avsp.pro.capture.camera.guided

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

/** M6 feature #10 — simple rule-of-thirds grid overlay drawn over the preview. */
@Composable
fun GuidedGridOverlay(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val strokeWidth = 2f
        val thirdWidth = size.width / 3f
        val thirdHeight = size.height / 3f
        val lineColor = Color.White.copy(alpha = 0.55f)

        for (i in 1..2) {
            drawLine(
                color = lineColor,
                start = androidx.compose.ui.geometry.Offset(thirdWidth * i, 0f),
                end = androidx.compose.ui.geometry.Offset(thirdWidth * i, size.height),
                strokeWidth = strokeWidth
            )
            drawLine(
                color = lineColor,
                start = androidx.compose.ui.geometry.Offset(0f, thirdHeight * i),
                end = androidx.compose.ui.geometry.Offset(size.width, thirdHeight * i),
                strokeWidth = strokeWidth
            )
        }
    }
}
