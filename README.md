# 掌驭 PalmLink

安卓手机远程控制安卓手机。一个 APK 装两台手机，启动时选角色。

- **控制端**：看对方屏幕，点、滑、返回、主页
- **被控端**：共享屏幕，用无障碍服务接收手势

> 当前状态：**S0 工程骨架**。10 屏界面已定稿，屏幕流与触控注入尚未接入。

---

## 构建

本地不需要装任何 Android 工具链，推代码即出包。

### 云端构建（推荐）

推送代码到 `main` 分支，GitHub Actions 自动构建，产物在 **Actions → 最新一次 run → Artifacts → PalmLink-debug**。

打 tag 会自动创建 Release 并附上 APK，方便手机直接下载：

```bash
git tag v0.1.0 && git push origin v0.1.0
```

### 本地构建（需要 JDK 17 + Android SDK 36）

```bash
gradle :app:assembleDebug
# 产物：app/build/outputs/apk/debug/app-debug.apk
```

仓库没有提交 Gradle wrapper，本地构建需自备 Gradle 8.13，或先执行 `gradle wrapper --gradle-version 8.13` 生成。

---

## 安装

1. 把 APK 传到安卓手机（微信文件传输 / 数据线 / 网盘均可）
2. 点击安装，系统提示「未知来源应用」时允许
3. 首次打开会看到启动页，说明骨架已跑通

---

## 技术选型

| 项 | 版本 |
|---|---|
| Kotlin | 2.2.21 |
| Jetpack Compose | BOM 2025.10.01 |
| AGP / Gradle | 8.13.2 / 8.13 |
| compileSdk / targetSdk / minSdk | 36 / 35 / 26 |
| 媒体传输 | WebRTC `io.github.webrtc-sdk:android`（待接入） |

架构与实施计划见 `docs/` 与项目方案文档。

---

## 已知系统限制

这些不是 bug，是 Android 的硬约束，任何同类 App 都一样：

1. **被控端每次会话都必须确认录屏**：`MediaProjection` 的系统弹窗无法绕过
2. **锁屏状态下无法注入输入**：系统禁止无障碍服务操作安全锁屏
3. **无法播放/注入连续触摸流**：`dispatchGesture` 一次只能派发一个手势，不适合游戏
4. **iOS 无法被控制**：所以两端都必须是 Android
5. **支付、验证码等安全界面双方都可见**：涉及资金操作时请先断开

---

## 目录

```
app/                    单 APK 入口（控制端 + 被控端双角色）
docs/                   方案与技术文档
.github/workflows/      云编译
gradle/libs.versions.toml  依赖版本集中管理
```
