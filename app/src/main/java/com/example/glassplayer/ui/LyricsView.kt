package com.example.glassplayer.ui

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.glassplayer.LyricLine
import kotlin.math.abs

/**
 * 歌词滚动视图（仿 Apple Music）：
 *  - 当前行放大、全亮；远离当前行的越来越暗、越来越模糊
 *  - 当前行自动滚到视口上方 30% 处
 *  - 点击任意一行跳转到该行时间
 *  - 上下边缘渐隐
 */
@Composable
fun LyricsView(
    lyrics: List<LyricLine>,
    activeLine: Int,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val listState = rememberLazyListState()

    BoxWithConstraints(modifier.fillMaxSize()) {
        val topPad = maxHeight * 0.30f
        val bottomPad = maxHeight * 0.55f

        // 换歌（歌词列表变化）时回到顶部
        LaunchedEffect(lyrics) { listState.scrollToItem(0) }

        // 当前行变化时滚动；用户正在手动拖动时不抢控制权
        LaunchedEffect(activeLine, lyrics) {
            if (activeLine >= 0 && !listState.isScrollInProgress) {
                val targetPx = (listState.layoutInfo.viewportSize.height * 0.30f).toInt()
                listState.animateScrollToItem(activeLine, -targetPx)
            }
        }

        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(top = topPad, bottom = bottomPad),
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
                .drawWithContent {
                    drawContent()
                    drawRect(
                        brush = Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.10f to Color.Black,
                            0.88f to Color.Black,
                            1f to Color.Transparent,
                        ),
                        blendMode = BlendMode.DstIn,
                    )
                },
        ) {
            itemsIndexed(lyrics, key = { i, _ -> i }) { i, line ->
                val ref = if (activeLine < 0) -1 else activeLine
                LyricRow(
                    line = line,
                    active = i == activeLine,
                    distance = abs(i - ref),
                    onClick = { onSeek(line.startMs) },
                )
            }
        }
    }
}

@Composable
private fun LyricRow(
    line: LyricLine,
    active: Boolean,
    distance: Int,
    onClick: () -> Unit,
) {
    val alpha by animateFloatAsState(
        targetValue = when {
            active -> 1f
            distance == 1 -> 0.50f
            else -> 0.32f
        },
        animationSpec = tween(450),
        label = "lyricAlpha",
    )
    val scale by animateFloatAsState(
        targetValue = if (active) 1f else 0.90f,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 300f),
        label = "lyricScale",
    )
    val blurRadius by animateDpAsState(
        targetValue = if (active) 0.dp else (distance.coerceAtMost(5) * 1.4f).dp,
        animationSpec = tween(450),
        label = "lyricBlur",
    )

    Column(
        Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = 8.dp, vertical = 10.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                this.alpha = alpha
                transformOrigin = TransformOrigin(0f, 0.5f)
            }
            .blur(blurRadius, BlurredEdgeTreatment.Unbounded),
    ) {
        Text(
            text = line.lines.first(),
            color = Color.White,
            fontSize = 30.sp,
            lineHeight = 36.sp,
            fontWeight = FontWeight.Bold,
        )
        // 第二行及之后（比如翻译）用小一号的字
        line.lines.drop(1).forEach {
            Text(
                text = it,
                color = Color.White.copy(alpha = 0.75f),
                fontSize = 18.sp,
                lineHeight = 24.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}
