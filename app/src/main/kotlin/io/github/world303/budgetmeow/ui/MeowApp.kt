package io.github.world303.budgetmeow.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.world303.budgetmeow.AppContainer
import io.github.world303.budgetmeow.core.backup.BackupService
import io.github.world303.budgetmeow.core.model.MeowSettings
import io.github.world303.budgetmeow.core.model.ThemeMode
import io.github.world303.budgetmeow.feature.about.AboutScreen
import io.github.world303.budgetmeow.feature.history.HistoryScreen
import io.github.world303.budgetmeow.feature.history.SearchScreen
import io.github.world303.budgetmeow.feature.home.HomeScreen
import io.github.world303.budgetmeow.feature.settings.AccountScreen
import io.github.world303.budgetmeow.feature.settings.SettingsScreen
import io.github.world303.budgetmeow.feature.stats.StatsScreen
import io.github.world303.budgetmeow.ui.component.InputDialog
import io.github.world303.budgetmeow.ui.theme.MeowTheme
import io.github.world303.budgetmeow.ui.theme.isScheduledDarkNow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.SnackbarHostState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.io.File

sealed interface Screen {
    data object Home : Screen
    data object History : Screen
    data object Search : Screen
    data object Stats : Screen
    data object Settings : Screen
    data object Account : Screen
    data object About : Screen
}

@Composable
fun MeowApp(container: AppContainer) {
    val settingsState by container.settings.state.collectAsState()
    var bootstrapped by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        container.settings.bootstrap()
        bootstrapped = true
    }

    if (!bootstrapped || settingsState.loading) {
        LoadingScreen()
        return
    }

    val profile = settingsState.activeProfile
    if (profile == null) {
        LoadingScreen()
        return
    }

    val store = remember(profile.id) { container.budgetFor(profile.id) }
    val budgetState by store.state.collectAsState()
    LaunchedEffect(profile.id) { store.load() }

    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current

    // 定时深色：每分钟重算一次
    var tick by remember { mutableIntStateOf(0) }
    LaunchedEffect(profile.settings.theme) {
        while (true) {
            delay(60_000)
            tick++
        }
    }
    val darkOverride = remember(tick, profile.settings) {
        if (profile.settings.theme == ThemeMode.Scheduled) isScheduledDarkNow(profile.settings) else null
    }

    // ---------- 文件选择器 ----------
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val ok = runCatching {
                    val json = BackupService.exportJson(store.snapshot(), profile.id)
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openOutputStream(uri)?.use {
                            it.write(json.toByteArray(Charsets.UTF_8))
                        } ?: error("写入失败")
                    }
                }.isSuccess
                snackbar.showSnackbar(if (ok) "备份已导出" else "导出失败")
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val result = runCatching {
                    val text = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                            ?: error("读取失败")
                    }
                    BackupService.importJson(text, profile.id)
                }
                result.onSuccess { imported ->
                    store.importDataset(imported.dataset)
                    val suffix = if (imported.skipped > 0) "，跳过 ${imported.skipped} 条异常记录" else ""
                    snackbar.showSnackbar("恢复成功$suffix")
                }.onFailure {
                    snackbar.showSnackbar(it.message ?: "导入失败")
                }
            }
        }
    }

    val avatarLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val temp = withContext(Dispatchers.IO) {
                    runCatching {
                        val file = File(context.cacheDir, "avatar_pick.png")
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            file.outputStream().use { output -> input.copyTo(output) }
                        }
                        file
                    }.getOrNull()
                }
                if (temp != null) {
                    container.settings.updateAvatar(temp)
                    snackbar.showSnackbar("头像已更新")
                }
            }
        }
    }

    // ---------- 导航 ----------
    var stack by remember(profile.id) { mutableStateOf(listOf<Screen>(Screen.Home)) }
    fun push(screen: Screen) {
        stack = stack + screen
    }

    fun pop() {
        if (stack.size > 1) stack = stack.dropLast(1)
    }

    BackHandler(enabled = stack.size > 1) { pop() }

    var editBudget by remember { mutableStateOf(false) }

    MeowTheme(settings = profile.settings, darkOverride = darkOverride) {
        Box(modifier = Modifier.fillMaxSize()) {
        when (stack.last()) {
            Screen.Home -> HomeScreen(
                store = store,
                profile = profile,
                onOpenHistory = { push(Screen.History) },
                onOpenSearch = { push(Screen.Search) },
                onOpenStats = { push(Screen.Stats) },
                onOpenSettings = { push(Screen.Settings) },
                onHeaderModeChange = { mode ->
                    scope.launch {
                        container.settings.updateSettings { it.copy(headerDisplayMode = mode) }
                    }
                },
                snackbar = snackbar,
            )

            Screen.History -> HistoryScreen(
                store = store,
                onBack = { pop() },
                onOpenSearch = { push(Screen.Search) },
                snackbar = snackbar,
            )

            Screen.Search -> SearchScreen(
                store = store,
                onBack = { pop() },
                snackbar = snackbar,
            )

            Screen.Stats -> StatsScreen(
                store = store,
                onBack = { pop() },
                snackbar = snackbar,
            )

            Screen.Settings -> SettingsScreen(
                profile = profile,
                month = store.month,
                onBack = { pop() },
                onUpdateSettings = { transform: (MeowSettings) -> MeowSettings ->
                    scope.launch { container.settings.updateSettings(transform) }
                },
                onOpenAccount = { push(Screen.Account) },
                onOpenAbout = { push(Screen.About) },
                onExport = { exportLauncher.launch("budget_backup_${store.month}.json") },
                onImport = { importLauncher.launch(arrayOf("application/json", "text/plain")) },
                onRenameNickname = { name ->
                    scope.launch { container.settings.updateNickname(name) }
                },
                onPickAvatar = { avatarLauncher.launch("image/*") },
                onEditBudget = { editBudget = true },
                onClearMonth = { scope.launch { store.clearAllRecords() } },
                onResetAll = { scope.launch { store.resetAccount() } },
                snackbar = snackbar,
            )

            Screen.Account -> AccountScreen(
                profiles = settingsState.profiles,
                activeId = profile.id,
                onBack = { pop() },
                onSwitch = { id ->
                    scope.launch {
                        container.settings.switchProfile(id)
                        stack = listOf(Screen.Home)
                    }
                },
                onCreate = { name ->
                    scope.launch {
                        container.settings.createProfile(name)
                        stack = listOf(Screen.Home)
                    }
                },
                onDelete = { id ->
                    scope.launch {
                        container.settings.deleteProfile(id)
                        container.dropBudget(id)
                    }
                },
                snackbar = snackbar,
            )

            Screen.About -> AboutScreen(
                onBack = { pop() },
                snackbar = snackbar,
            )

            else -> Unit
        }

        if (editBudget) {
            InputDialog(
                show = true,
                title = "修改本月预算",
                initialValue = budgetState.initialBudget.let { if (it > 0) it.toString() else "" },
                placeholder = "新的初始预算",
                maxLength = 12,
                keyboardType = KeyboardType.Decimal,
                onConfirm = { value ->
                    editBudget = false
                    val amount = value.toDoubleOrNull()
                    if (amount != null && amount > 0) {
                        scope.launch { store.updateInitialBudget(amount) }
                    } else {
                        scope.launch { snackbar.showSnackbar("请输入大于 0 的金额") }
                    }
                },
                onDismiss = { editBudget = false },
            )
        }
        }
    }
}

@Composable
private fun LoadingScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "载入账本…",
            style = MiuixTheme.textStyles.footnote1,
            color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
        )
    }
}
