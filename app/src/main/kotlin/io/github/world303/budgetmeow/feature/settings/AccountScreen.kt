package io.github.world303.budgetmeow.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.world303.budgetmeow.core.model.Profile
import io.github.world303.budgetmeow.core.sync.SyncState
import io.github.world303.budgetmeow.ui.component.ConfirmDialog
import io.github.world303.budgetmeow.ui.component.InputDialog
import io.github.world303.budgetmeow.ui.component.MeowDivider
import io.github.world303.budgetmeow.ui.component.MeowPage
import io.github.world303.budgetmeow.ui.component.MeowRow
import io.github.world303.budgetmeow.ui.theme.MeowPalette
import top.yukonga.miuix.kmp.basic.Button
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Card
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.basic.TextField
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 账号页：云端账号（真登录）+ 本地配置档（本机多账本）。
 *
 * 两者是正交的：云端账号决定「同步到哪」，配置档决定「本机看哪本账」。
 */
@Composable
fun AccountScreen(
    profiles: List<Profile>,
    activeId: String,
    syncState: SyncState,
    onBack: () -> Unit,
    onSwitch: (String) -> Unit,
    onCreate: (String) -> Unit,
    onDelete: (String) -> Unit,
    onLogin: (String, String) -> Unit,
    onRegister: (String, String) -> Unit,
    onLogout: () -> Unit,
    onSyncNow: () -> Unit,
    onDeleteAccount: (String) -> Unit,
) {
    var showCreate by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<Profile?>(null) }

    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var localError by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var showDeleteAccount by remember { mutableStateOf(false) }

    MeowPage(title = "账号", onBack = onBack) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            // ---------------- 云端账号 ----------------
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                cornerRadius = 18.dp,
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    if (syncState.loggedIn) {
                        Text(text = "云端账号（当前这本账）", style = MiuixTheme.textStyles.headline2)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = syncState.email.orEmpty(),
                            style = MiuixTheme.textStyles.body1,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (syncState.lastSyncAt > 0) {
                                "上次同步 ${formatSyncTime(syncState.lastSyncAt)}"
                            } else {
                                "尚未同步过"
                            },
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = onSyncNow,
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColorsPrimary(),
                                enabled = !syncState.syncing,
                            ) {
                                Text(
                                    text = if (syncState.syncing) "同步中…" else "立即同步",
                                    color = MiuixTheme.colorScheme.onPrimary,
                                )
                            }
                            TextButton(text = "退出登录", onClick = onLogout, modifier = Modifier.weight(1f))
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        TextButton(
                            text = "注销云端账号（连同云端账目一起删除）",
                            onClick = { showDeleteAccount = true },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        Text(text = "登录云端账号（同步当前这本账）", style = MiuixTheme.textStyles.headline2)
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "登录后，这台设备和其它设备之间会按记录自动合并；不登录也能完整记账。同步只针对当前这本账，其它本机账本不受影响。",
                            style = MiuixTheme.textStyles.footnote1,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                        Spacer(modifier = Modifier.height(14.dp))
                        TextField(
                            value = email,
                            onValueChange = { email = it.take(60); localError = null },
                            label = "邮箱",
                            useLabelAsPlaceholder = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        PasswordField(
                            value = password,
                            onValueChange = { password = it.take(64); localError = null },
                            label = "密码（至少 8 位）",
                        )
                        if (localError != null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = localError!!,
                                style = MiuixTheme.textStyles.footnote2,
                                color = MeowPalette.Red,
                            )
                        }
                        Spacer(modifier = Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = {
                                    val problem = validate(email, password)
                                    if (problem != null) {
                                        localError = problem
                                        return@Button
                                    }
                                    busy = true
                                    onLogin(email.trim(), password)
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColorsPrimary(),
                                enabled = !busy,
                            ) {
                                Text(text = "登录", color = MiuixTheme.colorScheme.onPrimary)
                            }
                            TextButton(
                                text = "注册",
                                onClick = {
                                    val problem = validate(email, password)
                                    if (problem != null) {
                                        localError = problem
                                        return@TextButton
                                    }
                                    busy = true
                                    onRegister(email.trim(), password)
                                },
                                modifier = Modifier.weight(1f),
                                enabled = !busy,
                            )
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = "账号只用于同步，密码经 PBKDF2 加盐后存服务端，明文不落盘。",
                            style = MiuixTheme.textStyles.footnote2,
                            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        )
                    }
                }
            }

            // ---------------- 本地配置档 ----------------
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                cornerRadius = 16.dp,
            ) {
                Column {
                    Text(
                        text = "本机账本（互不相通）",
                        style = MiuixTheme.textStyles.footnote1,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                        modifier = Modifier.padding(start = 16.dp, top = 14.dp, bottom = 6.dp),
                    )
                    profiles.forEachIndexed { index, profile ->
                        MeowRow(
                            title = profile.nickname,
                            summary = if (profile.id == activeId) "当前使用" else "点击切换",
                            trailingText = if (profile.id == activeId) "✓" else null,
                            onClick = {
                                if (profile.id != activeId) onSwitch(profile.id)
                            },
                        )
                        if (index != profiles.lastIndex) MeowDivider()
                    }
                    MeowDivider()
                    MeowRow(
                        title = "新建本机账本",
                        summary = "比如「家庭账本」「旅行专项」",
                        showArrow = true,
                        onClick = { showCreate = true },
                    )
                    if (profiles.size > 1) {
                        val active = profiles.firstOrNull { it.id == activeId }
                        if (active != null) {
                            MeowDivider()
                            MeowRow(
                                title = "删除当前本机账本",
                                summary = "连同「${active.nickname}」的账目一起删除",
                                onClick = { pendingDelete = active },
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "本机账本用来看不同的账，云端账号用来跨设备同步；两者互不影响。",
                style = MiuixTheme.textStyles.footnote2,
                color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
    }

    if (showDeleteAccount) {
        InputDialog(
            show = true,
            title = "注销云端账号",
            initialValue = "",
            placeholder = "输入密码确认",
            maxLength = 64,
            onConfirm = { pwd ->
                showDeleteAccount = false
                onDeleteAccount(pwd)
            },
            onDismiss = { showDeleteAccount = false },
        )
    }

    if (showCreate) {
        InputDialog(
            show = true,
            title = "新建本机账本",
            initialValue = "",
            placeholder = "账本名称，最多 10 字",
            maxLength = 10,
            onConfirm = { name ->
                showCreate = false
                onCreate(name)
            },
            onDismiss = { showCreate = false },
        )
    }

    val deleting = pendingDelete
    if (deleting != null) {
        ConfirmDialog(
            show = true,
            title = "删除配置档",
            message = "「${deleting.nickname}」下的所有账目会被一起删除，无法恢复。",
            confirmText = "删除",
            destructive = true,
            onConfirm = {
                pendingDelete = null
                onDelete(deleting.id)
            },
            onDismiss = { pendingDelete = null },
        )
    }
}

private fun validate(email: String, password: String): String? = when {
    !email.contains('@') || !email.contains('.') -> "邮箱格式不对"
    password.length < 8 -> "密码至少 8 位"
    else -> null
}

@Composable
private fun PasswordField(value: String, onValueChange: (String) -> Unit, label: String) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        textStyle = MiuixTheme.textStyles.main.copy(color = MiuixTheme.colorScheme.onSurface),
        cursorBrush = SolidColor(MiuixTheme.colorScheme.primary),
        modifier = Modifier.fillMaxWidth(),
        decorationBox = { inner ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MiuixTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = 14.dp, vertical = 14.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = label,
                        style = MiuixTheme.textStyles.main,
                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                    )
                }
                inner()
            }
        },
    )
}

private fun formatSyncTime(millis: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(millis))
