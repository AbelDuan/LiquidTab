# 模块自我唤醒（2026-11-26）

## 现象

系统「应用策略 → LiquidTab (io.github.offlineglass)」页面：13.4 小时里
**应用唤醒 146 次（全部后台）**，其中「广播/通知」67 次（46%），其余为内容提供者等。

## 根因

hook 侧有三条「主动去问模块 App」的路径，模块 App 是独立进程，任何一条都会把它拉起，
系统随后又把它冻结 —— 于是形成「拉起 → 冻结 → 再拉起」的循环：

| 路径 | 位置 | 触发频率 |
| --- | --- | --- |
| ordered broadcast 到 `ConfigRequestReceiver` | `HookConfigReader.requestConfigSync` | 每个被 hook 进程的每次 `read()` / `readFresh()` / 宿主 attach，5 秒节流 |
| 宿主 attach 时额外的普通 `ACTION_CONFIG_REQUEST` 广播 | `GlassHostLayout.registerConfigSyncBroadcast` | 每个宿主 attach 一次 |
| `contentResolver.call` 到 `SettingsProvider` | `HookConfigReader.readStartupConfig` / `readUnpushedCurrent` | 每个目标进程启动一次 + provider 失败退避窗口后再试 |

日志里的证据：`[CfgDiag] provider call threw: java.lang.IllegalArgumentException:
Unknown authority io.github.offlineglass.settings` —— 这些调用不但没拿到配置（一直走
XSharedPreferences 兜底），还把模块进程反复拉起。

## 修复

配置只需要**单向**流动：

- **App → 目标进程**：用户在模块里保存时，`LocalSettings.publishConfig` 已经把完整 bundle
  广播给每个正在运行的目标包（`ACTION_CONFIG_RESPONSE`），目标的 `GlassHostLayout` 收到后
  直接 `acceptPushedConfig` + `applyLiveConfig`；
- **目标进程冷启动**：读模块通过 `xposedsharedprefs` 导出的偏好文件（`getRemotePreferences`）。

所以 hook 侧不再需要反向询问。改动：

1. `requestConfigSync` 改为**不发送任何广播**，直接返回内存里的推送快照（调用点无需改）；
2. `readStartupConfig`、`readUnpushedCurrent` 去掉 provider 探测（`readThroughProvider` 与
   `providerFailedAt` 一并删除）；
3. 删除宿主 attach 时多余的 `ACTION_CONFIG_REQUEST` 广播。

`ConfigRequestReceiver` 与 `SettingsProvider` 保留（设置页与部分适配器仍可能用到），但 hook
不再主动调用它们。

## 验证

- 安装后在 19:24 冷启动微博与美团：模块日志只有
  `[CfgDiag] lsposed read for com.sina.weibo: …`，**不再出现 provider 调用失败**；
- 同期 `logcat -b events` 中没有 `io.github.offlineglass` 的 `am_proc_start`
  （修复前每 5 秒一次的广播会拉起模块进程）；
- 配置修改仍然实时：模块保存 → `publishConfig` 广播 → 正在运行的目标进程即时应用。

复查方式（用户侧）：系统「应用策略」页看「应用唤醒」，比较相同使用时长的次数；
或 root 下 `logcat -b events | grep am_proc_start | grep offlineglass`。
