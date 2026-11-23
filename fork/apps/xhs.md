# 小红书适配记录

包名：`com.xingin.xhs`（注意：**不是** `com.xingin.xhs.xxx` 的独立进程名）

## 已核对的结构

### 9.50.0（本机 APK 基线）

首页头部布局（`r/lcv.xml`、`r/lcw.xml`、`r/lcx.xml`、`r/nyc.xml`）：

```
ConstraintLayout 页面根
├─ FrameLayout 0x...c0d → HomeViewPager（内容）
└─ FrameLayout 头部
   ├─ ImageView 头部背景
   ├─ FrameLayout 顶栏行
   │  ├─ TextView 登录（默认 GONE）
   │  ├─ HomeTokenTextView 登录 token（默认 GONE）
   │  ├─ ImageView 菜单（0x7f0930d1）
   │  ├─ HomeTabLayout（0x7f093342，关注/发现/同城）
   │  └─ 两个装饰 View
   ├─ 分割线 View
   └─ ConstraintLayout 搜索层（0x7f094f98）
      ├─ TextToken 搜索标题（默认 GONE）
      ├─ TextView 搜索提示（0x7f09280d，文案“搜索笔记，用户和商品”，带背景、drawableStart）
      └─ ImageView 搜索图标（0x7f092e7c，contentDescription“搜索”）
```

结论：只隐藏搜索图标时，同一层的提示 `TextView` 会留下背景框和文案。

### 9.55.0（本机已安装）

- 顶部结构仍是「顶栏行 + 右侧搜索胶囊」，但搜索胶囊里的放大镜是 `TextView` 的
  compound drawable，不是独立 `ImageView`；搜索词是热词（如“调酒…”），不一定是“搜索”。
- 因此不能靠文案或图标 ID 识别，只能按“顶栏带内、标签右侧、带背景的文字胶囊”结构识别。

## 模块里的处理

`XhsTopBar` 的顶栏宿主在窗口坐标 y=0 处生成 4 个玻璃宿主：

| 角色 | 宽度 | 作用 |
| --- | --- | --- |
| 左 | 88px | 侧边栏 |
| 中 | 439px | 关注/发现/同城 |
| 频道 | 160px | 当前频道（发现页） |
| 右 | 88px | 搜索（点击原生搜索目标） |

隐藏原生控件统一走 `Session.hide()`（只把 alpha 设 0，保留点击目标）：

- `hide(nativeTabs)`：原生 HomeTabLayout；
- `hide(menuSource)` / `hide(searchSource)`：菜单和搜索图标；
- `XhsHeaderChrome.suppress`：头部背景图和分割线；
- `XhsSearchChrome.suppress`：右侧搜索胶囊（本仓库补丁）。

`XhsSearchChrome` 的识别顺序：

1. 图标的父层若只含搜索文字、不含 tabs/menu/pager，且高度不超过一行，整层隐藏；
2. 否则扫描 decor 树，找“顶栏带内、标签右侧、文字非空、自身或父层有背景”的 `TextView`，
   隐藏文字并连带隐藏窄背景包装层；
3. 找不到就保留原生界面，并在 logcat 打一次
   `LiquidTabXhsTop: search icon path=... siblings=[...]`，用这条日志继续定位。

## 排查命令

```bash
# 模块加载/顶栏宿主
logcat -d | grep -i "LiquidTabXhsTop\|OfflineGlass.*xingin"

# LSPosed 模块日志（需要 root）
F=$(ls -t /data/adb/lspd/log/modules_*.log | head -1)
grep -i "offlineglass" "$F" | tail -30
```

看到 `installed com.xingin.xhs slots=5` 说明底栏和顶栏都装上了；顶栏宿主日志形如
`host=906,0 88x88`。

## 已知风险

- `menuSource`/`searchSource` 是结构推断，应用改版后可能先失效；`ready` 失败时顶栏整体不出现，原生界面保留。
- 搜索胶囊的判定必须在顶栏带内，否则会误伤信息流卡片文字；当前实现跳过 RecyclerView 子树。
- 折叠屏内屏走 `PadHomeTabLayout`（与 `HomeTabLayout` 不是父子类）。`XhsTopBar.install` 现在同时 hook
  两个类；600dp 宽度模拟下顶栏和搜索胶囊隐藏都正常，但真实内屏仍需展开复核。
