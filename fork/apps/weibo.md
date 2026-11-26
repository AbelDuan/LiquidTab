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

## 第二轮：采样优先级（2026-11-26 晚）

第一轮之后仍会偶发「页面浅、栏深」，日志 `[DarkMode] … dark=true via=auto contentDark=null
appNight=true sysNight=true forceDarkDisabled=true surfaces=0 labels=0 source=false` 暴露了两件事：

1. 微博用自己的主题强制浅色时，进程 Configuration 仍是 `NIGHT_YES`（系统 auto 夜间），
   所以 `appNight` 不能单独作为判据；
2. 微博底栏由本模块自绘，原生行的文字被隐藏，`contentDark` 一直是 `null`；顶栏宿主更是
   `navigationSource == null`（虚拟宿主），于是判定落到 `systemNightMode()`。

补两条：

- `updateDetectedContentTheme()` 现在**先采「栏后面那层不透明底色」**（源视图往上 4 层的
  `ColorDrawable` 且 alpha ≥ 200），亮度 < 96 判为深色页面；只有采不到底色时才退回文字亮度。
  玻璃坐在什么颜色上，就用什么颜色决定深浅。
- `isDarkGlass()` 入口增加按需采样：`themeMode == SYSTEM && detectedContentDark == null` 时先跑一次，
  因为自绘型适配器（微博）不会经过原来的 pre-draw 探针路径。

诊断日志加了 `surfaces / labels / source` 三个计数，能直接看出采样是否拿到东西。

实测（竖屏内屏，页面浅色）：顶栏胶囊与底栏都是浅灰白玻璃，图标深色。

## 栏色跟随页面（custom.6）

微博开着系统「自动」夜间，进程的 `Configuration` 报 `NIGHT_YES`，但它自己用浅色主题渲染页面
（`forceDarkDisabled=true`）。原来的 AUTO 判定先看这个开关，于是栏永远是深色玻璃，和浅色页面撞色。

`isDarkGlass()` 的 AUTO 分支现在先取**宿主背后真实绘制的不透明底色**：

1. 有原生行（`navigationSource`）就看它的父容器链；
2. 顶栏这类没有原生行的宿主，从自己的 `parent` 开始往上 6 层；
3. 再退到 `window.decorView.background`、主题 `android.R.attr.colorBackground`。

采到（alpha ≥ 200）就以它为准：浅色页面 → 浅色栏，深色页面 → 深色栏；alpha 不足或只有渐变
时才落到原来的开关判断。采样 300 ms 缓存，日志多打一个 `pageSurfaceDark=`。

实测微博首页：`dark=false via=auto ... appNight=true pageSurfaceDark=false`，顶栏与底栏都是浅色玻璃。
