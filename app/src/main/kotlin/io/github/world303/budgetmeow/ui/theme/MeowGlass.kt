package io.github.world303.budgetmeow.ui.theme

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.LayerBackdrop
import top.yukonga.miuix.kmp.blur.highlight.Highlight
import top.yukonga.miuix.kmp.blur.isRuntimeShaderSupported
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 整个 App 共享一块「背景快照」。玻璃层模糊的是它，而不是自己身上那层内容。
 * 由 MeowApp 创建，HomeScreen 把壁纸挂上 layerBackdrop。
 */
val LocalMeowBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }

/** 真正的背景模糊需要 RuntimeShader（Android 12+）；不支持时退化成半透明色块 */
@Composable
fun rememberBlurSupported(): Boolean = remember { isRuntimeShaderSupported() }

@Composable
fun Modifier.meowGlassSurface(
    shape: Shape,
    enabled: Boolean,
    blurRadius: Float = 26f,
    tintColor: Color = MiuixTheme.colorScheme.surfaceContainer,
    tintAlpha: Float? = null,
    showHighlight: Boolean = true,
): Modifier {
    if (!enabled) return background(tintColor, shape).clip(shape)

    val backdrop = LocalMeowBackdrop.current
    val supported = rememberBlurSupported()
    val dark = MiuixTheme.colorScheme.onBackground.luminance() > 0.5f

    if (backdrop == null || !supported) {
        return background(tintColor.copy(alpha = tintAlpha ?: if (dark) 0.55f else 0.72f), shape).clip(shape)
    }

    return this
        .textureBlur(
            backdrop = backdrop,
            shape = shape,
            blurRadius = blurRadius,
            noiseCoefficient = 0.008f,
            colors = BlurColors(
                blendColors = listOf(
                    BlendColorEntry(
                        tintColor.copy(alpha = tintAlpha ?: if (dark) 0.30f else 0.52f),
                    ),
                ),
                brightness = if (dark) 0.05f else 0.04f,
                contrast = 1.02f,
                saturation = 1.18f,
            ),
            highlight = when {
                !showHighlight -> null
                dark -> Highlight.GlassStrokeMiddleDark
                else -> Highlight.GlassStrokeMiddleLight
            },
        )
        .clip(shape)
}

/**
 * 壁纸平均亮度（0 暗 ~ 1 亮），用于自动选择文字颜色。
 *
 * 为什么要这么做：给壁纸压一层黑/白蒙层会改变壁纸本身的颜色（用户明确不接受）。
 * 更好的办法是壁纸不动，按它的明暗切换前景文字的黑白，再配一层反向投影兜底。
 */
@Composable
fun rememberWallpaperTone(wallpaperPath: String?): Float? = remember(wallpaperPath) {
    if (wallpaperPath == null) return@remember null
    runCatching {
        val options = BitmapFactory.Options().apply { inSampleSize = 24 }
        val bitmap = BitmapFactory.decodeFile(wallpaperPath, options) ?: return@runCatching null
        var sum = 0.0
        var count = 0
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                val color = bitmap.getPixel(x, y)
                val r = (color shr 16 and 0xFF) / 255.0
                val g = (color shr 8 and 0xFF) / 255.0
                val b = (color and 0xFF) / 255.0
                sum += 0.2126 * r + 0.7152 * g + 0.0722 * b
                count++
            }
        }
        bitmap.recycle()
        if (count == 0) null else (sum / count).toFloat()
    }.getOrNull()
}

/** 背景层：壁纸原样显示（不压暗也不提亮），没壁纸时用主题色做一层很淡的渐变 */
@Composable
fun MeowWallpaperBackground(
    wallpaperPath: String?,
    glassEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = MiuixTheme.colorScheme

    Box(modifier = modifier.background(colors.surface)) {
        val bitmap = remember(wallpaperPath) {
            wallpaperPath?.let { runCatching { BitmapFactory.decodeFile(it) }.getOrNull() }
        }
        when {
            bitmap != null -> Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )

            glassEnabled -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            listOf(
                                colors.primary.copy(alpha = 0.18f),
                                colors.surface,
                                colors.secondary.copy(alpha = 0.16f),
                            ),
                        ),
                    ),
            )

            else -> Unit
        }
    }
}
