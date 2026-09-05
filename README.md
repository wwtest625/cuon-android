# Cuon AI 工作台 (Android MVP 版)

基于 Kotlin + Jetpack Compose + Room + 大模型的多意图个人日程与待办任务管理应用。

---

## 调试方式推荐：主机 (Windows) 还是 WSL？

### 最佳推荐方案：在 **Windows 主机端的 Android Studio** 中打开并调试
1. **原因**：真机通过 USB 连接到电脑主机时，USB 驱动直接挂载在 Windows 上，Windows 版 Android Studio 可以获得**极致顺畅的真机投屏 (Device Mirroring)、Logcat 秒级输出、断点调试与即时编译 (Apply Changes)**。
2. **打开方式**：
   * 打开 Windows 上的 Android Studio；
   * 点击 `Open`，路径输入：
     ```text
     \\wsl$\Ubuntu\home\cuon-android
     ```
   * 手机插上 USB 线，开启【开发者选项 -> USB 调试】，在 Android Studio 顶部设备栏直接选择你的手机，点击绿色运行按钮即可！

---

### 备选方案：在 **WSL 命令行** 下进行 ADB 真机调试
如果你习惯在 Linux 终端通过命令行 `adb install / adb logcat` 调试：
1. **WSL 与 Windows 共享 ADB Server**：
   Windows 端的 ADB 默认监听 `127.0.0.1:5037`。在 WSL 当前终端执行：
   ```bash
   export ADB_SERVER_SOCKET=tcp:127.0.0.1:5037
   ```
2. 在 Windows 终端启动一次：`adb devices`；
3. 回到 WSL 终端运行 `adb devices`，即可直接穿透识别到连接在 Windows 上的真机！

---

## MVP 核心功能实现清单

1. **语音输入 (Speech-to-Text)**：
   * 调用系统级 `RecognizerIntent.ACTION_RECOGNIZE_SPEECH`，无需依赖任何臃肿的第三方语音 SDK，真机原生秒唤起。
2. **AI 智能意图拆解 (Intent Atomization)**：
   * `AiParserService`：自动区分【特定时间的日程】与【普通任务】，并提取地点、标签、优先级。
   * 支持接入 DashScope (Qwen) / OpenAI / 兼容端点；**无 Key 或离线时自动启用极速规则解析器**，保证 0 延迟。
3. **Local-First 极速体验**：
   * Room Database 启用 `WAL` (Write-Ahead Logging) 预写日志，并发读写 < 5ms。
   * 首页通过 Kotlin Flow 响应式驱动，支持打勾完成、滑动删除。
