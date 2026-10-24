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
): Modifier {
    if (!enabled) return background(tintColor, shape).clip(shape)

    val backdrop = LocalMeowBackdrop.current
    val supported = rememberBlurSupported()
    val dark = MiuixTheme.colorScheme.onBackground.luminance() > 0.5f

    if (backdrop == null || !supported) {
        // 降级：没有 RuntimeShader 时用半透明玻璃色，仍然保留圆角与层次
        return background(tintColor.copy(alpha = if (dark) 0.55f else 0.72f), shape).clip(shape)
    }

    // 液态玻璃的三件事：背景真模糊 + 一层很淡的着色 + 受光描边。
    // 着色别下重手，否则壁纸就被盖成一块死板的色块（第一版犯过这个错）。
    return this
        .textureBlur(
            backdrop = backdrop,
            shape = shape,
            blurRadius = blurRadius,
            noiseCoefficient = 0.008f,
            colors = BlurColors(
                blendColors = listOf(
                    BlendColorEntry(tintColor.copy(alpha = if (dark) 0.30f else 0.52f)),
                ),
                brightness = if (dark) 0.05f else 0.04f,
                contrast = 1.02f,
                saturation = 1.18f,
            ),
            highlight = if (dark) Highlight.GlassStrokeMiddleDark else Highlight.GlassStrokeMiddleLight,
        )
        .clip(shape)
}

/** 背景层：壁纸 → 玻璃模式下的柔和渐变 → 纯色，三档退化 */
@Composable
fun MeowWallpaperBackground(
    wallpaperPath: String?,
    glassEnabled: Boolean,
    modifier: Modifier = Modifier,
) {
    val colors = MiuixTheme.colorScheme
    // onBackground 接近白色 ⇒ 当前是深色主题
    val dark = colors.onBackground.luminance() > 0.5f

    Box(modifier = modifier.background(colors.surface)) {
        val bitmap = remember(wallpaperPath) {
            wallpaperPath?.let { runCatching { BitmapFactory.decodeFile(it) }.getOrNull() }
        }
        when {
            bitmap != null -> {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
                // 蒙层保证文字对比度：深色主题压黑保白字，浅色主题压白保深字
                if (dark) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.42f)),
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.White.copy(alpha = 0.28f)),
                    )
                }
            }

            glassEnabled -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            listOf(
                                colors.primary.copy(alpha = 0.16f),
                                colors.surface,
                                colors.secondary.copy(alpha = 0.14f),
                            ),
                        ),
                    ),
            )

            else -> Unit
        }
    }
}
