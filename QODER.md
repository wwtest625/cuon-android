# Cuon Android · 项目记忆

本项目的实现约定与踩坑记录。全局规则看 `/root/.qoder/AGENTS.md`；这里只放本仓库特有的东西。

## 构建

- 用**本机 `gradle`**，别用 `./gradlew`——WSL 里 gradle wrapper 走 HTTPS 下载会因代理 TLS 拦截报 PKIX 错误。项目要求 Gradle 8.7，本机已装。
- 常用组合：`gradle assembleDebug testDebugUnitTest`（编译 + 单元测试）。

## 数据与生命周期约定

### 任务状态三层语义（别混用）
- `isCompleted + completedAt` → **完成/归档**（历史可查）
- `deletedAt` → **软删除进回收站**，30 天后由 `CalendarAutoArchiver.TRASH_RETENTION_DAYS` 统一定义，冷启动与每日晨报双触发 `purgeExpiredTrash`
- 物理 `DELETE` → 只在回收站"彻底删除"和过期清理里出现，UI 不直接触发

### Room 迁移
每次加字段：`TaskEntity` 加可空字段 → `AppDatabase` version++ → 补 `MIGRATION_N_N1` 一条 `ALTER TABLE tasks ADD COLUMN <name> <TYPE> DEFAULT NULL` → 加进 `addMigrations(...)` 列表。三处漏一处就崩，参考 v4→v5 `deletedAt` 迁移。

### 归档时间锚点
`completedAt` 语义是**事件本身结束的时刻**（`endTime ?: startTime`），不是归档动作发生的时刻。历史按事件结束日期分组才准确，别写 `System.currentTimeMillis()`。

## ReminderScheduler 闹钟管理

**规则：任何 `schedule*Alarm` 都必须有对应的 `cancelAlarm` 分支。** 之前踩过：改期到过去 / 转成待办 / 去掉 endTime，`scheduleReminder` 早退 return 了，旧 `ACTION_EVENT_END` 闹钟仍在系统里挂着，到点弹通知，行为诡异。

**requestCode 分配约定**（避免两条闹钟互相覆盖）：
- 提前提醒（`ACTION_TASK_REMINDER`）：`requestCode = task.id.toInt()`
- 日程结束确认（`ACTION_EVENT_END`）：`requestCode = task.id.toInt() + AlarmReceiver.END_ALARM_OFFSET`（1_000_000_000）
- 通知按钮（`ACTION_ARCHIVE_CONFIRM` 等）：`requestCode = task.id.toInt() + AlarmReceiver.ARCHIVE_ACTION_OFFSET`（2_000_000_000）

改这三条时同步改 `cancelReminder`。

## 共享 helper 的返回值

`RecurrenceAdvancer.advance(taskDao, task)` 返回 `TaskEntity?`（**完整实体**），不返回 `Long?`。原因：只返 id 的话，调用方必须自己重建 `shift(task.startTime)/shift(task.endTime)`——两处曾各写一份，`CalendarAutoArchiver` 那份用了未 shift 的原始时间给 `scheduleReminder`，触发 `triggerTime <= now` 立刻 cancel，闹钟哑掉。**新增/修改跨包共享的 suspend helper，返回值优先给完整实体，让调用方一行就能用后续 API。**

## UI ↔ ViewModel 依赖

`ui.components.*` 不要 import `ui.CuonViewModel`。业务常量（例如 `TRASH_RETENTION_DAYS`）放 `reminder.CalendarAutoArchiver` 或 `data.local`，UI 从那里取。踩过一次反向依赖，修在 37bc90d。

## 测试

- 单元测试位置：`app/src/test/java/com/cuon/app/**`，Room 层用纯函数拆分策略（例：`CalendarAutoArchiver.shouldArchive` 是 pure，可脱 DB 测；`scanAndArchive` 是薄封装）
- 归档、软删、晨报调度这类时间敏感逻辑，**至少覆盖五类边界**：今日/昨日/前天 × 有提醒/无提醒 × 是否已完成/是否回收站

## 端到端跑一次真机验证的套路

设备没 sqlite3，用「拉库 → 本地 Python 改 → 推回 → 清 WAL/SHM → force-stop 后重启 App」：
```bash
adb exec-out run-as com.cuon.app cat databases/cuon_database > /tmp/x.db
# 本地 sqlite3 改数据
adb push /tmp/x.db /data/local/tmp/x.db
adb shell run-as com.cuon.app rm databases/cuon_database-wal databases/cuon_database-shm
adb shell run-as com.cuon.app cp /data/local/tmp/x.db databases/cuon_database
adb shell am force-stop com.cuon.app && adb shell monkey -p com.cuon.app 1
```
`run-as` 只在 debuggable build 生效。
