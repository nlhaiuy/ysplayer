package com.example.glassplayer.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.glassplayer.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 封面位图 + 从封面提取的主色（用于背景光斑）。 */
data class CoverImage(val bitmap: ImageBitmap, val color: Color)

private val coverCache = LruCache<String, CoverImage>(32)

private fun loadCover(path: String, sample: Int): CoverImage? {
    val bmp = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
        ?: return null
    // 缩到 1x1 取平均色，再提高饱和度/明度，让背景光斑更鲜艳
    val px = Bitmap.createScaledBitmap(bmp, 1, 1, true).getPixel(0, 0)
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(px, hsv)
    hsv[1] = maxOf(hsv[1], 0.45f)
    hsv[2] = hsv[2].coerceIn(0.55f, 0.95f)
    val tuned = Color(android.graphics.Color.HSVToColor(hsv))
    return CoverImage(bmp.asImageBitmap(), tuned)
}

@Composable
fun rememberCover(path: String?, sample: Int = 1): State<CoverImage?> {
    val key = "$path@$sample"
    return produceState<CoverImage?>(initialValue = path?.let { coverCache.get(key) }, key) {
        if (path == null) {
            value = null
        } else if (value == null) {
            value = withContext(Dispatchers.IO) {
                loadCover(path, sample)?.also { coverCache.put(key, it) }
            }
        }
    }
}

fun placeholderHue(track: Track?): Float =
    (((track?.title?.hashCode() ?: 0) and 0x7fffffff) % 360).toFloat()

@Composable
fun CoverArt(
    track: Track?,
    modifier: Modifier = Modifier,
    sample: Int = 2,
    cornerRadius: Dp = 12.dp,
) {
    val cover by rememberCover(track?.artPath, sample)
    Box(
        modifier = modifier.clip(RoundedCornerShape(cornerRadius)),
        contentAlignment = Alignment.Center,
    ) {
        val c = cover
        if (c != null) {
            Image(
                bitmap = c.bitmap,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            val hue = placeholderHue(track)
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            listOf(
                                Color.hsv(hue, 0.45f, 0.78f),
                                Color.hsv((hue + 40f) % 360f, 0.55f, 0.45f),
                            ),
                        ),
                    ),
            )
            Icon(
                imageVector = Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.fillMaxSize(0.42f),
            )
        }
    }
}
