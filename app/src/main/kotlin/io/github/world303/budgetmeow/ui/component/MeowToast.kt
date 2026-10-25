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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.world303.budgetmeow.core.store.MessageKind
import io.github.world303.budgetmeow.ui.theme.MeowPalette
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
                val textColor = when (message.kind) {
                    MessageKind.Error -> MeowPalette.Red
                    MessageKind.Warning -> MeowPalette.Orange
                    MessageKind.Success -> MiuixTheme.colorScheme.onSurface
                    MessageKind.Info -> MiuixTheme.colorScheme.onSurface
                }
                Box(
                    modifier = Modifier
                        // 往下让一让：原来紧贴状态栏，会压住首页顶栏那排按钮
                        .padding(start = 28.dp, end = 28.dp, top = 76.dp, bottom = 10.dp)
                        .meowGlassSurface(
                            shape = RoundedCornerShape(20.dp),
                            enabled = true,
                            blurRadius = 22f,
                            // 提示条要看清字，着色比普通玻璃重一些，避免在彩色壁纸上发灰
                            tintColor = MiuixTheme.colorScheme.surface,
                            tintAlpha = 0.9f,
                        )
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                ) {
                    Text(
                        text = message.text,
                        style = MiuixTheme.textStyles.footnote1,
                        color = textColor,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}
