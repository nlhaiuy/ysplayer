package com.example.glassplayer.ui

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.lerp
import com.example.glassplayer.Track
import dev.chrisbanes.haze.HazeState

fun formatTime(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val m = total / 60
    val s = total % 60
    return "$m:${s.toString().padStart(2, '0')}"
}

/**
 * 全屏动态背景：封面模糊放大 + 两团缓慢漂移的主色光斑 + 暗色罩。
 * 玻璃控件会模糊这一层，所以它需要作为 hazeSource。
 */
@Composable
fun GlassBackdrop(track: Track?, modifier: Modifier = Modifier) {
    val cover by rememberCover(track?.artPath, 4)
    val fallback = if (track == null) Color(0xFF3A4A8F) else Color.hsv(placeholderHue(track), 0.5f, 0.65f)
    val base by animateColorAsState(cover?.color ?: fallback, tween(900), label = "baseColor")
    val second = lerp(base, Color(0xFF6A5BFF), 0.5f)

    val t = rememberInfiniteTransition(label = "drift")
    val p1 by t.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(16000, easing = LinearEasing), RepeatMode.Reverse),
        label = "p1",
    )
    val p2 by t.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(23000, easing = LinearEasing), RepeatMode.Reverse),
        label = "p2",
    )

    Box(modifier.background(Color(0xFF07070C))) {
        Crossfade(targetState = cover, animationSpec = tween(700), label = "coverFade") { c ->
            if (c != null) {
                Image(
                    bitmap = c.bitmap,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = 1.6f
                            scaleY = 1.6f
                            alpha = 0.6f
                        }
                        .blur(48.dp),
                )
            }
        }
        Canvas(Modifier.fillMaxSize()) {
            val w = size.width
            val h = size.height
            drawRect(
                Brush.radialGradient(
                    colors = listOf(base.copy(alpha = 0.80f), Color.Transparent),
                    center = Offset(w * (0.15f + 0.6f * p1), h * (0.15f + 0.25f * p2)),
                    radius = w * 1.15f,
                ),
            )
            drawRect(
                Brush.radialGradient(
                    colors = listOf(second.copy(alpha = 0.60f), Color.Transparent),
                    center = Offset(w * (0.85f - 0.6f * p2), h * (0.85f - 0.3f * p1)),
                    radius = w * 1.25f,
                ),
            )
            drawRect(
                Brush.verticalGradient(
                    listOf(Color.Black.copy(alpha = 0.25f), Color.Black.copy(alpha = 0.55f)),
                ),
            )
        }
    }
}

/** 进度条：iOS Music 风格，细条，拖动时变粗。 */
@Composable
fun GlassSeekBar(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    val fraction = when {
        dragging -> dragFraction
        durationMs > 0 -> (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
        else -> 0f
    }
    val trackHeight by animateDpAsState(
        targetValue = if (dragging) 14.dp else 6.dp,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
        label = "trackHeight",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(36.dp)
            .pointerInput(durationMs) {
                detectTapGestures(onTap = { o ->
                    onSeek((o.x / size.width * durationMs).toLong().coerceIn(0L, durationMs))
                })
            }
            .pointerInput(durationMs) {
                detectHorizontalDragGestures(
                    onDragStart = { o ->
                        dragging = true
                        dragFraction = (o.x / size.width).coerceIn(0f, 1f)
                    },
                    onDragEnd = {
                        dragging = false
                        onSeek((dragFraction * durationMs).toLong())
                    },
                    onDragCancel = { dragging = false },
                    onHorizontalDrag = { change, _ ->
                        change.consume()
                        dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                    },
                )
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(trackHeight)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.22f)),
        ) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction)
                    .background(Color.White.copy(alpha = 0.92f)),
            )
        }
    }
}

/** 玻璃分段控件，选中块带弹簧滑动。 */
@Composable
fun GlassSegmented(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    haze: HazeState,
    modifier: Modifier = Modifier,
) {
    val segWidth = 78.dp
    val offset by animateDpAsState(
        targetValue = segWidth * selected,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 450f),
        label = "segOffset",
    )
    Box(
        modifier
            .width(segWidth * options.size + 8.dp)
            .height(44.dp)
            .glass(haze, CircleShape)
            .padding(4.dp),
    ) {
        Box(
            Modifier
                .offset(x = offset)
                .width(segWidth)
                .fillMaxHeight()
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.24f)),
        )
        Row {
            options.forEachIndexed { i, label ->
                Box(
                    Modifier
                        .width(segWidth)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                        ) { onSelect(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        color = Color.White.copy(alpha = if (i == selected) 1f else 0.7f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}
