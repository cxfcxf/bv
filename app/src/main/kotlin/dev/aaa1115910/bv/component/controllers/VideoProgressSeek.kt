package dev.aaa1115910.bv.component.controllers

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.PreviewParameterProvider
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import dev.aaa1115910.bv.ui.theme.BVTheme

@Composable
fun VideoProgressSeek(
    modifier: Modifier = Modifier,
    duration: Long,
    position: Long,
    bufferedPercentage: Int,
    isPersistentSeek: Boolean,
    focused: Boolean = false
) {
    val activeColor = MaterialTheme.colorScheme.primary
    // 获得焦点时轨道变粗并长出滑块，让"现在能拖动"这件事一眼可见
    val trackHeight by animateDpAsState(
        targetValue = when {
            isPersistentSeek -> 2.dp
            focused -> 8.dp
            else -> 5.dp
        },
        label = "SeekTrackHeight"
    )
    val thumbRadius by animateDpAsState(
        targetValue = if (!isPersistentSeek && focused) 11.dp else 0.dp,
        label = "SeekThumbRadius"
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(if (isPersistentSeek) 2.dp else 24.dp)
    ) {
        val track = trackHeight.toPx()
        val thumb = thumbRadius.toPx()
        // 两端留出滑块半径，否则进度为 0 或 100% 时滑块会被裁掉
        val inset = maxOf(thumb, track / 2)
        val startX = inset
        val span = (size.width - inset * 2).coerceAtLeast(0f)
        val progress = if (duration > 0) {
            (position / duration.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }
        val buffered = (bufferedPercentage / 100f).coerceIn(0f, 1f)

        drawLine(
            color = Color.White.copy(alpha = 0.25f),
            start = Offset(startX, center.y),
            end = Offset(startX + span, center.y),
            strokeWidth = track,
            cap = StrokeCap.Round
        )
        if (!isPersistentSeek && buffered > 0f) {
            drawLine(
                color = Color.White.copy(alpha = 0.45f),
                start = Offset(startX, center.y),
                end = Offset(startX + span * buffered, center.y),
                strokeWidth = track,
                cap = StrokeCap.Round
            )
        }
        if (progress > 0f) {
            drawLine(
                color = activeColor,
                start = Offset(startX, center.y),
                end = Offset(startX + span * progress, center.y),
                strokeWidth = track,
                cap = StrokeCap.Round
            )
        }
        if (thumb > 0f) {
            val thumbX = startX + span * progress
            // 深色描边保证滑块在亮画面上也看得清
            drawCircle(
                color = Color.Black.copy(alpha = 0.35f),
                radius = thumb + 2.dp.toPx(),
                center = Offset(thumbX, center.y)
            )
            drawCircle(
                color = Color.White,
                radius = thumb,
                center = Offset(thumbX, center.y)
            )
        }
    }
}


@Preview(device = "id:tv_1080p")
@Composable
private fun SeekPreview() {
    BVTheme {
        VideoProgressSeek(
            duration = 1000,
            position = 300,
            bufferedPercentage = 50,
            isPersistentSeek = true
        )
    }
}

@Preview(device = "id:tv_1080p")
@Composable
private fun SeekWithThumbPreview(@PreviewParameter(ProgressProvider::class) data: Triple<Long, Long, Int>) {
    BVTheme {
        VideoProgressSeek(
            duration = data.first,
            position = data.second,
            bufferedPercentage = data.third,
            isPersistentSeek = false
        )
    }
}

private class ProgressProvider : PreviewParameterProvider<Triple<Long, Long, Int>> {
    override val values = sequenceOf(
        Triple(1234_000L, 0L, 3),
        Triple(1234_000L, 234_000L, 24),
        Triple(1234_000L, 555_000L, 57),
        Triple(1234_000L, 1234_000L, 100)
    )
}