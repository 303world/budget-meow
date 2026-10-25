package io.github.world303.budgetmeow.ui.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.world303.budgetmeow.core.store.MessageKind
import androidx.compose.ui.graphics.luminance
import io.github.world303.budgetmeow.ui.theme.MeowPalette
import top.yukonga.miuix.kmp.blur.highlight.Highlight
import io.github.world303.budgetmeow.ui.theme.LocalMeowPageBackdrop
import io.github.world303.budgetmeow.ui.theme.meowGlassSurface
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 全局提示条。
 *
 * 为什么要自己写：Scaffold 的 SnackbarHost 固定在底部，会正好压在「收入/支出」那排按钮上，
 * 记完账弹一下就点不到下一个功能。这里改成从**顶部**滑下、带玻璃底、2.2 秒自动消失。
 */
object MeowToast {

    data class Message(val text: String, val kind: MessageKind = MessageKind.Info)

    private val _flow = MutableSharedFlow<Message>(extraBufferCapacity = 16)
    val flow = _flow.asSharedFlow()

    fun show(text: String, kind: MessageKind = MessageKind.Info) {
        _flow.tryEmit(Message(text, kind))
    }

    fun success(text: String) = show(text, MessageKind.Success)

    fun warning(text: String) = show(text, MessageKind.Warning)

    fun error(text: String) = show(text, MessageKind.Error)
}

@Composable
fun MeowToastHost(modifier: Modifier = Modifier) {
    val dark = MiuixTheme.colorScheme.onBackground.luminance() > 0.5f
    var current by remember { mutableStateOf<MeowToast.Message?>(null) }
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        MeowToast.flow.collect { message ->
            current = message
            visible = true
            delay(2200)
            visible = false
        }
    }

    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        AnimatedVisibility(
            visible = visible && current != null,
            enter = slideInVertically { -it } + fadeIn(),
            exit = slideOutVertically { -it } + fadeOut(),
        ) {
            val message = current
            if (message != null) {
                val baseColor = when {
                    message.kind == MessageKind.Error -> MeowPalette.Red
                    message.kind == MessageKind.Warning -> MeowPalette.Orange
                    dark -> androidx.compose.ui.graphics.Color.White
                    else -> androidx.compose.ui.graphics.Color(0xFF1B1B1F)
                }
                val textStyle = MiuixTheme.textStyles.footnote1.copy(
                    shadow = androidx.compose.ui.graphics.Shadow(
                        color = androidx.compose.ui.graphics.Color.Black.copy(alpha = if (dark) 0.55f else 0.28f),
                        offset = androidx.compose.ui.geometry.Offset(0f, 1f),
                        blurRadius = 6f,
                    ),
                )
                Box(
                    modifier = Modifier
                        // 落在顶栏下方，不压住首页那排按钮，也不至于盖住正文
                        .padding(start = 28.dp, end = 28.dp, top = 76.dp, bottom = 10.dp)
                        // 玻璃提示条：着色比卡片重一点保证看清字，但关掉受光描边，
                        // 否则在圆角里会出现一圈像"内嵌方块"的亮边
                        .meowGlassSurface(
                            shape = RoundedCornerShape(18.dp),
                            enabled = true,
                            // 模糊给足，着色给薄：白底一重就会把壁纸糊成灰白
                            blurRadius = 30f,
                            tintColor = MiuixTheme.colorScheme.surface,
                            tintAlpha = if (dark) 0.34f else 0.26f,
                            backdropOverride = LocalMeowPageBackdrop.current,
                            highlight = if (dark) {
                                Highlight.GlassStrokeSmallDark
                            } else {
                                Highlight.GlassStrokeSmallLight
                            },
                        )
                        .padding(horizontal = 18.dp, vertical = 11.dp),
                ) {
                    Text(
                        text = message.text,
                        style = textStyle,
                        color = baseColor,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                    )
                }
            }
        }
    }
}
