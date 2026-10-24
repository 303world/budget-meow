# 预算喵 (budget-meow)

计算器优先的离线预算记账 App。录入金额时不是一个输入框，而是一台能算式的计算器：`1200/3+58` 直接得出这一笔该记多少；长按表达式会出现放大镜，配合光标把数字改到某一位。

界面用 [Miuix](https://github.com/compose-miuix-ui/miuix)（小米 HyperOS 风格的 Compose UI 库）重写，数据全部留在本机。

> 这一版是**纯离线**版本：没有账号、没有网络请求。云端同步在 v2 计划里，数据层已按可同步的结构预留。

## 功能

- **预算初始化**：每月首次进入必须设定本月预算，自动带上月的结转与预支
- **智能记账计算器**：四则运算与括号、光标点选、长按放大镜、负数与除零拦截、大额与超预算二次确认
- **收支类型**：收入 / 支出 / 省钱 / 结转下月 / 预支下月
- **出行子账本**：独立余额、独立明细、结束后余额结转回主账本；没真正出行过的空行程会完整回滚
- **历史记录**：无限滚动、长按多选、批量撤回（反向记账）与删除（仅删流水）、单条改备注
- **复合搜索**：关键词 + 金额区间 + 日期区间，另有本月 / 近 7 天 / 近 30 天快捷区间
- **统计**：本月收入、支出、净支出、省钱、出行次数与出行支出；年度 12 个月收支柱状图
- **个性化**：默认/粉色配色、浅色/深色/定时深色、粗体字、顶栏显示模式（昵称/预算喵/我的预算/自定义）、头像
- **本地多配置档**：多份互相隔离的账本，等价于原网页版的多账号
- **备份**：导出 JSON；可导入网页版 v1/v2/v3 备份，也可以导回网页版

## 技术栈

| 层 | 选型 |
|---|---|
| 语言 / UI | Kotlin 2.4.20 + Jetpack Compose 1.12.1 |
| 设计语言 | Miuix 0.9.4（Apache-2.0） |
| 状态 | `StateFlow` + 手写 Store（不引 ViewModel/Hilt，减少构建面） |
| 持久化 | 单文件 JSON（kotlinx.serialization）+ 原子写（tmp → fsync → rename） |
| 构建 | AGP 9.4.1 / Gradle 9.7.1 / JDK 21，`minSdk 26`、`compileSdk 37` |

## 结构与分层

```
app/src/main/kotlin/io/github/world303/budgetmeow/
├── core/
│   ├── model/        领域模型、统计口径、金额格式化
│   ├── calc/         表达式解析器、计算器状态机、二次确认规则
│   ├── storage/      磁盘 DTO、原子写
│   ├── repo/         BudgetRepository（本地实现；云同步将来在这里加）
│   ├── backup/       网页版备份格式的导入导出
│   └── store/        BudgetStore（记账业务）、SettingsStore（配置档与设置）
├── feature/          首页、计算器、历史、搜索、统计、设置、账号、关于
└── ui/               主题适配、通用组件、导航壳
```

业务逻辑刻意与 Compose 解耦，`core` 里的东西全部可以在 JVM 上跑单测（`./gradlew test`）。

## 构建

需要 JDK 21 与 Android SDK 37。

```bash
./gradlew :app:assembleDebug          # 调试包
./gradlew :app:testDebugUnitTest      # 单测（计算器、统计口径、备份兼容、记账主流程）
./gradlew :app:assembleRelease        # 发布包
```

### CI

`.github/workflows/build.yml` 会在每次 push 到 `main` 时跑单测并产出 debug / release 两个 APK（Actions 页面可下载 artifact）；打 `v*` 标签会发 Release。

### 签名说明

仓库里的 `keystore/budget-meow-ci.p12` 是**公开的 CI 调试密钥**（口令 `budgetmeow`），只为让每次构建产物的签名一致、可以覆盖安装。**不要用它发布正式版本**：正式发布请换成你自己的密钥并改 `app/build.gradle.kts` 里的 `signingConfigs`。

## 数据与迁移

- 数据保存在 App 私有目录 `files/meow/`，卸载即删除，请自行导出备份。
- 备份格式与网页版一致（5 张表 + `_exportMeta.version`），所以网页版的导出文件可以直接导入；导出的文件也能导回网页版。
- 备份里不含昵称与头像。

## 路线图

- **v1（当前）**：完整离线记账
- **v2**：邮箱验证码登录 + Cloudflare Workers/D1 跨设备同步（数据层已预留 `BudgetRepository` 与 `updatedAt` 字段）
- **v1.x**：桌面壁纸、更丰富的统计维度、CSV 导出

## 许可证

GPL-3.0。衍生作品分发时必须以同一协议开源。

如果你需要闭源商用授权，可以联系作者获取**新版本**的商业许可（双许可模式）；已按 GPL-3.0 发布的版本不受此影响。

第三方组件均为 Apache-2.0（Miuix、Kotlin/Compose、kotlinx.coroutines、kotlinx.serialization）。
