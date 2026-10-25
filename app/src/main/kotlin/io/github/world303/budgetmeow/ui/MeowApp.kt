package io.github.world303.budgetmeow.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import io.github.world303.budgetmeow.AppContainer
import io.github.world303.budgetmeow.core.backup.BackupService
import io.github.world303.budgetmeow.core.model.MeowSettings
import io.github.world303.budgetmeow.core.model.BUDGET_LIMIT_JOKE
import io.github.world303.budgetmeow.core.model.MAX_MONTHLY_BUDGET
import io.github.world303.budgetmeow.core.model.ThemeMode
import io.github.world303.budgetmeow.core.update.UpdateChecker
import io.github.world303.budgetmeow.core.update.UpdateInfo
import io.github.world303.budgetmeow.core.update.UpdateInstaller
import io.github.world303.budgetmeow.core.update.UpdateSource
import io.github.world303.budgetmeow.core.EulaStore
import io.github.world303.budgetmeow.feature.about.APP_VERSION
import io.github.world303.budgetmeow.feature.about.AboutScreen
import io.github.world303.budgetmeow.feature.history.HistoryScreen
import io.github.world303.budgetmeow.feature.history.SearchScreen
import io.github.world303.budgetmeow.feature.onboarding.ConsentScreen
import io.github.world303.budgetmeow.feature.home.HomeScreen
import io.github.world303.budgetmeow.feature.settings.AccountScreen
import io.github.world303.budgetmeow.feature.settings.SettingsScreen
import io.github.world303.budgetmeow.feature.stats.StatsScreen
import io.github.world303.budgetmeow.ui.component.InputDialog
import io.github.world303.budgetmeow.ui.component.MeowToast
import io.github.world303.budgetmeow.ui.component.MeowToastHost
import io.github.world303.budgetmeow.ui.component.UpdateDialog
import io.github.world303.budgetmeow.ui.theme.LocalMeowBackdrop
import io.github.world303.budgetmeow.ui.theme.MeowTheme
import io.github.world303.budgetmeow.ui.theme.isScheduledDarkNow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
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
fun MeowApp(container: AppContainer, activity: androidx.activity.ComponentActivity) {
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
    val syncState by container.sync.state.collectAsState()
    LaunchedEffect(profile.id) {
        store.load()
        container.sync.refresh(profile.id)
    }
    // 已登录的话，启动时自动同步一次（失败不打扰人，界面里能看到状态）
    LaunchedEffect(profile.id, syncState.loggedIn) {
        if (syncState.loggedIn) {
            container.sync.sync(profile.id)
        }
    }

    val scope = rememberCoroutineScope()
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

    // ---------- 更新 ----------
    val installer = remember { UpdateInstaller(context) }
    var updateInfo by remember { mutableStateOf<UpdateInfo?>(null) }
    var updateProgress by remember { mutableIntStateOf(-1) }

    val checkUpdate: (Boolean) -> Unit = { silent ->
        val source = UpdateSource.fromWire(profile.settings.updateSource)
        scope.launch {
            UpdateChecker.check(source)
                .onSuccess { info ->
                    if (UpdateChecker.isNewer(info.latestVersion, APP_VERSION)) {
                        updateInfo = info
                        updateProgress = -1
                    } else if (!silent) {
                        MeowToast.success("已是最新版本")
                    }
                }
                .onFailure { if (!silent) MeowToast.error(it.message ?: "检查失败") }
        }
    }

    // 开启自动检查时，每次启动静默查一次，发现新版本直接问要不要更新
    LaunchedEffect(profile.id, profile.settings.autoCheckUpdate) {
        if (profile.settings.autoCheckUpdate) checkUpdate(true)
    }

    // ---------- 全屏模式 ----------
    LaunchedEffect(profile.settings.fullScreen) {
        val window = activity.window
        val controller = WindowCompat.getInsetsController(window, window.decorView)
        if (profile.settings.fullScreen) {
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
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
                MeowToast.show(if (ok) "备份已导出" else "导出失败")
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
                    MeowToast.show("恢复成功$suffix")
                }.onFailure {
                    MeowToast.show(it.message ?: "导入失败")
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
                    MeowToast.show("头像已更新")
                }
            }
        }
    }

    val wallpaperLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val temp = withContext(Dispatchers.IO) {
                    runCatching {
                        val file = File(context.cacheDir, "wallpaper_pick.png")
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            file.outputStream().use { output -> input.copyTo(output) }
                        }
                        file
                    }.getOrNull()
                }
                if (temp != null) {
                    container.settings.setWallpaper(temp)
                    MeowToast.show("壁纸已更新")
                }
            }
        }
    }

    // ---------- 导航 ----------
    var stack by remember(profile.id) { mutableStateOf(listOf<Screen>(Screen.Home)) }
    var forward by remember { mutableStateOf(true) }

    fun push(screen: Screen) {
        forward = true
        stack = stack + screen
    }

    fun pop() {
        forward = false
        if (stack.size > 1) stack = stack.dropLast(1)
    }

    BackHandler(enabled = stack.size > 1) { pop() }

    var editBudget by remember { mutableStateOf(false) }

    val backdrop = rememberLayerBackdrop()

    val eula = remember { EulaStore(context) }
    var consentAccepted by remember { mutableStateOf(eula.hasAccepted()) }

    CompositionLocalProvider(LocalMeowBackdrop provides backdrop) {
    MeowTheme(settings = profile.settings, darkOverride = darkOverride) {
        if (!consentAccepted) {
            ConsentScreen(
                onAccept = {
                    eula.accept()
                    consentAccepted = true
                },
                onDecline = { activity.finishAffinity() },
            )
            return@MeowTheme
        }

        // 根 Scaffold 只做弹层宿主：内边距清零，不影响各页面自己的 Scaffold
        Scaffold(
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            containerColor = MiuixTheme.colorScheme.background,
        ) { _ ->
            Box(modifier = Modifier.fillMaxSize()) {
                AnimatedContent(
                    targetState = stack.last(),
                    transitionSpec = {
                        if (forward) {
                            (slideInHorizontally(animationSpec = tween(300)) { it / 5 } +
                                fadeIn(animationSpec = tween(220))) togetherWith
                                fadeOut(animationSpec = tween(140))
                        } else {
                            fadeIn(animationSpec = tween(240)) togetherWith
                                (slideOutHorizontally(animationSpec = tween(260)) { it / 5 } +
                                    fadeOut(animationSpec = tween(180)))
                        }
                    },
                    label = "meow-screen",
                ) { screen ->
                when (screen) {
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
                cloudLoggedIn = syncState.loggedIn,
                onSyncNow = {
                    scope.launch {
                        container.sync.sync(profile.id)
                            .onSuccess { MeowToast.show(it) }
                            .onFailure { MeowToast.show(it.message ?: "同步失败") }
                    }
                },
            )

            Screen.History -> HistoryScreen(
                store = store,
                onBack = { pop() },
                onOpenSearch = { push(Screen.Search) },
                onOpenStats = { push(Screen.Stats) },
            )

            Screen.Search -> SearchScreen(
                store = store,
                onBack = { pop() },
            )

            Screen.Stats -> StatsScreen(
                store = store,
                onBack = { pop() },
            )

            Screen.Settings -> SettingsScreen(
                profile = profile,
                month = store.month,
                budget = budgetState.initialBudget,
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
                onPickWallpaper = { wallpaperLauncher.launch("image/*") },
                onRemoveWallpaper = { scope.launch { container.settings.setWallpaper(null) } },
                syncState = syncState,
                onSyncNow = {
                    scope.launch {
                        container.sync.sync(profile.id)
                            .onSuccess { MeowToast.show(it) }
                            .onFailure { MeowToast.show(it.message ?: "同步失败") }
                    }
                },
                onEditBudget = { editBudget = true },
                onClearMonth = { scope.launch { store.clearAllRecords() } },
                onResetAll = { scope.launch { store.resetAccount() } },
            )

            Screen.Account -> AccountScreen(
                profiles = settingsState.profiles,
                activeId = profile.id,
                syncState = syncState,
                onBack = { pop() },
                onLogin = { email, password ->
                    scope.launch {
                        container.sync.login(profile.id, email, password)
                            .onSuccess {
                                MeowToast.show("已登录 $it，正在同步…")
                                container.sync.sync(profile.id)
                                    .onSuccess { msg -> MeowToast.show(msg) }
                                    .onFailure { err -> MeowToast.show(err.message ?: "同步失败") }
                            }
                            .onFailure { MeowToast.show(it.message ?: "登录失败") }
                    }
                },
                onRegister = { email, password ->
                    scope.launch {
                        container.sync.register(profile.id, email, password)
                            .onSuccess {
                                MeowToast.show("注册成功，正在同步…")
                                container.sync.sync(profile.id)
                            }
                            .onFailure { MeowToast.show(it.message ?: "注册失败") }
                    }
                },
                onLogout = {
                    scope.launch {
                        container.sync.logout(profile.id)
                        MeowToast.show("已退出登录，云端数据保留")
                    }
                },
                onSyncNow = {
                    scope.launch {
                        container.sync.sync(profile.id)
                            .onSuccess { MeowToast.show(it) }
                            .onFailure { MeowToast.show(it.message ?: "同步失败") }
                    }
                },
                onDeleteAccount = { pwd ->
                    scope.launch {
                        container.sync.deleteAccount(profile.id, pwd)
                            .onSuccess { MeowToast.show("云端账号已注销") }
                            .onFailure { MeowToast.show(it.message ?: "注销失败") }
                    }
                },
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
            )

            Screen.About -> AboutScreen(
                onCheckUpdate = { checkUpdate(false) },
                autoCheckUpdate = profile.settings.autoCheckUpdate,
                updateSource = UpdateSource.fromWire(profile.settings.updateSource),
                onUpdateSourceChange = { newSource ->
                    scope.launch {
                        container.settings.updateSettings { it.copy(updateSource = newSource.wire) }
                    }
                },
                onToggleAutoCheck = { enabled ->
                    scope.launch {
                        container.settings.updateSettings { it.copy(autoCheckUpdate = enabled) }
                    }
                },
                onBack = { pop() },
            )

                    else -> Unit
                }
                }

                val pending = updateInfo
                if (pending != null) {
                    UpdateDialog(
                        info = pending,
                        currentVersion = APP_VERSION,
                        progress = updateProgress,
                        onDismiss = {
                            if (updateProgress < 0) {
                                updateInfo = null
                                updateProgress = -1
                            }
                        },
                        onUpdate = {
                            if (!installer.canInstallPackages()) {
                                installer.openInstallPermission()
                                MeowToast.warning("请允许「安装未知应用」后再点更新")
                            } else {
                                scope.launch {
                                    updateProgress = 0
                                    installer.download(pending) { percent -> updateProgress = percent }
                                        .onSuccess { file ->
                                            installer.install(file)
                                                .onSuccess {
                                                    updateInfo = null
                                                    updateProgress = -1
                                                }
                                                .onFailure {
                                                    updateProgress = -1
                                                    MeowToast.error("安装失败：${it.message}")
                                                }
                                        }
                                        .onFailure {
                                            updateProgress = -1
                                            MeowToast.error(it.message ?: "下载失败")
                                        }
                                }
                            }
                        },
                    )
                }

                MeowToastHost(
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .statusBarsPadding(),
                )

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
                        scope.launch { MeowToast.show("请输入大于 0 的金额") }
                    }
                },
                    onDismiss = { editBudget = false },
                )
            }
            }
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
