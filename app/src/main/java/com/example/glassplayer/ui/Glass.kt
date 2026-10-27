package com.example.glassplayer.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/**
 * 「液态玻璃」表面：
 *  1. 背景模糊（Haze，Android 12+ 使用 RenderEffect，更低版本退化为半透明着色）
 *  2. 对角线方向的体光（左上亮 → 中间透 → 右下微亮）
 *  3. 左上角的镜面高光
 *  4. 一圈渐变高光描边：左上最亮、右下次之、中间几乎透明——这是 iOS 26 玻璃边缘的标志
 */
fun Modifier.glass(
    haze: HazeState,
    shape: Shape,
    tint: Color = Color.White.copy(alpha = 0.10f),
    blur: Dp = 28.dp,
): Modifier = this
    .clip(shape)
    .hazeEffect(haze) {
        blurRadius = blur
        noiseFactor = 0.03f
        tints = listOf(HazeTint(tint))
    }
    .drawWithContent {
        val outline = shape.createOutline(size, layoutDirection, this)
        val diagonalEnd = Offset(size.width, size.height)

        // 体光
        drawOutline(
            outline,
            Brush.linearGradient(
                0f to Color.White.copy(alpha = 0.20f),
                0.45f to Color.White.copy(alpha = 0.03f),
                1f to Color.White.copy(alpha = 0.08f),
                start = Offset.Zero,
                end = diagonalEnd,
            ),
        )
        // 左上角镜面高光
        drawOutline(
            outline,
            Brush.radialGradient(
                colors = listOf(Color.White.copy(alpha = 0.26f), Color.Transparent),
                center = Offset(size.width * 0.18f, 0f),
                radius = size.minDimension * 0.9f,
            ),
        )

        drawContent()

        // 高光描边
        drawOutline(
            outline,
            Brush.linearGradient(
                0f to Color.White.copy(alpha = 0.85f),
                0.35f to Color.White.copy(alpha = 0.10f),
                0.65f to Color.White.copy(alpha = 0.04f),
                1f to Color.White.copy(alpha = 0.45f),
                start = Offset.Zero,
                end = diagonalEnd,
            ),
            style = Stroke(width = 1.2.dp.toPx()),
        )
    }

/** 按下时 Q 弹缩放（iOS 26 按钮的手感）。 */
fun Modifier.bounceClick(enabled: Boolean = true, onClick: () -> Unit): Modifier = composed {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.88f else 1f,
        animationSpec = spring(dampingRatio = 0.42f, stiffness = Spring.StiffnessMedium),
        label = "bounce",
    )
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = source,
            indication = null,
            enabled = enabled,
            onClick = onClick,
        )
}

@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String?,
    haze: HazeState,
    modifier: Modifier = Modifier,
    size: Dp = 52.dp,
    iconSize: Dp = 24.dp,
    highlighted: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .size(size)
            .bounceClick(onClick = onClick)
            .glass(
                haze = haze,
                shape = CircleShape,
                tint = Color.White.copy(alpha = if (highlighted) 0.30f else 0.08f),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = Color.White,
            modifier = Modifier.size(iconSize),
        )
    }
}

@Composable
fun GlassPill(
    text: String,
    haze: HazeState,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .height(48.dp)
            .bounceClick(onClick = onClick)
            .glass(haze, CircleShape, tint = Color.White.copy(alpha = 0.14f))
            .padding(horizontal = 26.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
    }
}
