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
import top.yukonga.miuix.kmp.blur.BlurBlendMode
import top.yukonga.miuix.kmp.blur.BlurDefaults
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

/**
 * 「页面内容」的快照。提示条这类浮层要模糊的是当前页面，
 * 而不是首页壁纸——首页不在组合里时，拿壁纸那层会采到失效内容（渲染成黑灰块）。
 */
val LocalMeowPageBackdrop = staticCompositionLocalOf<LayerBackdrop?> { null }

/** 真正的背景模糊需要 RuntimeShader（Android 12+）；不支持时退化成半透明色块 */
@Composable
fun rememberBlurSupported(): Boolean = remember { isRuntimeShaderSupported() }

/**
 * 玻璃材质的配方，**全部照抄 miuix 官方示例的 ColorBlendToken**
 * （compose-miuix-ui/miuix，example/.../component/blend/ColorBlendToken.kt，Apache-2.0）。
 *
 * 为什么不自创：这些配方是「颜色 + 混合模式」的组合（Overlay / HardLight / ColorBurn…），
 * 只用「白色 + 透明度」调不出玻璃的质感，最后只会得到一块灰雾。
 */
object MeowGlassTokens {

    /** 中性玻璃：小胶囊、按钮这类小面积元素 */
    val PuredRegularLight = listOf(
        BlendColorEntry(Color(0x340034F9), BlurBlendMode.Overlay),
        BlendColorEntry(Color(0xB3FFFFFF), BlurBlendMode.HardLight),
    )
    val PuredRegularDark = listOf(
        BlendColorEntry(Color(0x75000000), BlurBlendMode.ColorBurn),
        BlendColorEntry(Color(0x52000000), BlurBlendMode.SrcOver),
    )

    /** 更淡一档：需要透出更多背景时用 */
    val ThinLight = listOf(
        BlendColorEntry(Color(0x307A7A7A), BlurBlendMode.PlusLighter),
        BlendColorEntry(Color(0x5EFFFFFF), BlurBlendMode.PlusLighter),
        BlendColorEntry(Color(0x66FF6666), BlurBlendMode.SrcOver),
    )
    val ThinDark = listOf(
        BlendColorEntry(Color(0x969C9C9C), BlurBlendMode.PlusDarker),
        BlendColorEntry(Color(0x66000000), BlurBlendMode.SrcOver),
    )

    /** 带一点灰彩的大面积玻璃：计算器面板这类 */
    val ColoredRegularLight = listOf(
        BlendColorEntry(Color(0x803F3F3F), BlurBlendMode.Overlay),
        BlendColorEntry(Color(0x1CE6E6E6), BlurBlendMode.PlusLighter),
    )
    val ColoredRegularDark = listOf(
        BlendColorEntry(Color(0x70000000), BlurBlendMode.Overlay),
        BlendColorEntry(Color(0x14000000), BlurBlendMode.SrcOver),
    )
}

/** 玻璃档位：只有三档，避免每个调用点各写一套数字 */
enum class MeowGlassTone {
    /** 小胶囊、快捷按钮 */
    Pured,

    /** 提示条：要透一点但字要清楚 */
    Thin,

    /** 大面板：计算器 */
    Colored,
}

@Composable
fun Modifier.meowGlassSurface(
    shape: Shape,
    enabled: Boolean = true,
    tone: MeowGlassTone = MeowGlassTone.Pured,
    backdropOverride: LayerBackdrop? = null,
): Modifier {
    val fallback = MiuixTheme.colorScheme.surfaceContainer
    if (!enabled) return background(fallback, shape).clip(shape)

    val backdrop = backdropOverride ?: LocalMeowBackdrop.current
    if (backdrop == null || !rememberBlurSupported()) {
        // 没有 RuntimeShader 时退化成实心卡片色，至少保证可读
        return background(fallback, shape).clip(shape)
    }

    val dark = MiuixTheme.colorScheme.onBackground.luminance() > 0.5f
    val blend = when (tone) {
        MeowGlassTone.Pured -> if (dark) MeowGlassTokens.PuredRegularDark else MeowGlassTokens.PuredRegularLight
        MeowGlassTone.Thin -> if (dark) MeowGlassTokens.ThinDark else MeowGlassTokens.ThinLight
        MeowGlassTone.Colored -> if (dark) MeowGlassTokens.ColoredRegularDark else MeowGlassTokens.ColoredRegularLight
    }

    return this
        .textureBlur(
            backdrop = backdrop,
            shape = shape,
            // 模糊半径与噪声都用库默认值，不自己发明
            colors = BlurDefaults.blurColors(blendColors = blend),
            highlight = if (dark) Highlight.GlassStrokeSmallDark else Highlight.GlassStrokeSmallLight,
        )
        .clip(shape)
}

/**
 * 壁纸平均亮度（0 暗 ~ 1 亮），用于自动选择前景文字颜色。
 *
 * 为什么不给壁纸蒙一层黑/白：那会改变壁纸本身的颜色（用户明确不接受）。
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
