# 微博适配记录

包名：`com.sina.weibo`

## 浅色模式下栏比页面深（2026-11-26）

现象：微博浅色界面（白底黑字）里，顶栏胶囊和底栏都是深灰近黑，与页面不协调；美团同场景正常。

根因：`GlassHostLayout.isDarkGlass()` 在 `themeMode = AUTO` 时走
`detectedContentDark ?: systemNightMode()`。微博底栏由本模块接管绘制，采样到的文字亮度不足以定论时会落到
`systemNightMode()`，而它的第 2/3/4 步读的是**系统级**信号（`Resources.getSystem()` 的 uiMode、
`UiModeManager.nightMode`、MIUI 的 `dark_mode`）。系统处于 auto（傍晚自动转深色）或全局深色时，
即使微博自己渲染浅色，判定也会是「深色」。

美团之所以没有这个问题，是因为它 `forceLightMode = true`（美团没有深色模式）；微博有深色模式，
不能照抄这种做法。

修复（`systemNightMode()`）：

```kotlin
if (resources.configuration.uiMode and UI_MODE_NIGHT_MASK == UI_MODE_NIGHT_YES) return true
// 已禁用平台强制深色的应用显示的就是它自己的配色
if (targetAdapter?.disablePlatformForceDark == true) return false
// …其余系统级信号只对没声明 disablePlatformForceDark 的应用生效
```

微博（`WeiboAdapter` 与它的顶栏 `PanelAdapter`）都声明了 `disablePlatformForceDark = true`，
所以栏的深浅现在严格跟随**微博自己的 Configuration**：浅色页面 → 浅色玻璃，微博切深色 → 深色玻璃。
其它应用不受影响（默认 `disablePlatformForceDark = false`，仍走系统信号）。

与微信的对比：微信靠「底栏文字采样 + `navigationSurfaceColor()` 取原生 `navigationBarColor`」判定，
微信 tab 有文字所以采样有效；微博底栏文字被本模块隐藏后采样不稳定，因此这里改用
「应用自身 Configuration 优先」这一条更直接的判据，而不是给微博写死浅色。

## 验证

| 条件 | 结果 |
| --- | --- |
| 微博浅色 + 系统 auto | 顶栏胶囊与底栏为浅色玻璃，图标深色，与白底页面协调 |
| `cmd uimode night yes`（系统深色，微博仍浅色） | 栏保持浅色 —— 与页面一致，不再被系统信号染深 |

诊断：`[OfflineGlass][DarkMode]` 日志（每个 host 最多 4 条）会打印
`dark / via / theme / contentDark / appNight / sysNight / forceDarkDisabled`，下次配色不对先看这一行。
