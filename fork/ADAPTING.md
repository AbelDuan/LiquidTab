# 新应用版本适配手册

目标：应用改版、上游 LiquidTab 还没更新时，我们能在本 fork 里独立适配，并且下次上游更新时补丁仍然容易搬。

## 0. 先判断问题属于哪一层

| 现象 | 优先看 |
| --- | --- |
| 整个底栏/顶栏不出现、错位 | 导航发现、Hook 信号、安装器 |
| 原生控件没被替换干净（多一个框、多一行字） | 对应应用的 `*TopBar` / `*Chrome` / `*Adapter` |
| 替换后点不动、点错页 | 点击源（`performClick` 目标）和页面状态 |
| 只有某个子页面异常 | 应用目录里的页面例外/视口代码 |

先看应用目录，不要一上来改共享 `GlassHostLayout`。

## 1. 抓现场（必须做）

在目标页面停留，然后任选其一：

```bash
# 当前 Activity + 视图树（最有用）
adb shell dumpsys activity top | head -200

# 可交互元素的 resource-id / text / bounds
adb shell uiautomator dump /sdcard/window_dump.xml
adb pull /sdcard/window_dump.xml

# 看模块自己的日志
adb logcat -s LiquidTabXhsTop LiquidTabDouyin OfflineGlass
```

有 APK 时，直接看布局比在设备上猜更快：`resources.arsc` 里布局名可能被混淆（小红书是 `r/*.xml`），按资源 ID 的 4 字节小端在 `res/layout*.xml` 或 `r/*.xml` 里搜，再用 AXML 解码器打印层级。

## 2. 找“结构锚点”，不要记版本号

上游约定（必须遵守）：

- 类名/控件层级优先，`R.id` 数值只作提示；
- 不写 `if (versionName == ...)`；
- 找不到就保留原生界面并打日志，不猜、不隐藏整页。

小红书的例子（9.50）：

- 顶栏容器：`FrameLayout`，内含 `HomeTabLayout`（tabs）和搜索层；
- 搜索图标：`ImageView`，父层是搜索条的 `ConstraintLayout`；
- 残留“搜索提示框”：与图标同层的 `TextView`（带 background，文字是搜索提示）；
- 只隐藏图标，不隐藏同层文字，就会剩下一个背景框。

## 3. 改在正确的那一层

- 应用专属逻辑只放在 `app/src/main/java/io/github/offlineglass/hook/adapters/<key>/`；
- 隐藏原生控件统一走该应用 Session 的 `hide(...)`（记录原始 alpha/visibility，退出时恢复），不要直接 `setVisibility(GONE)`；
- 要保留点击能力时只把 `alpha` 设为 0，不要设 `INVISIBLE`/`GONE`；
- 新增隐藏逻辑要能处理“控件晚到”：在现有 `onPreDraw`/`scan()` 节流里执行，不要另起无限循环。

## 4. 本地验证

```bash
./gradlew :app:assembleRelease
```

装到设备后：

1. 打开目标应用首页，确认没有多余框/字；
2. 点我们自己的按钮，确认走的是原生逻辑；
3. 切到二级页再返回，确认没有恢复一半；
4. 深色/浅色、折叠/展开各看一次；
5. `adb logcat` 里没有 `LiquidTab<App>` 的契约拒绝日志。

编译通过不等于运行时通过，必须写清“在哪个应用版本、哪个页面”实测过。

## 5. 提交与出包

```bash
git switch custom
git add -A
git commit -m "fix(xhs): hide native search hint layer beside the glass search key"
git push origin custom
```

CI 自动出 APK。确认没问题再打标签：

```bash
git tag custom-v2.1.2.1
git push origin custom-v2.1.2.1
```

## 6. 让补丁容易搬回上游

- 一个应用一个提交，提交信息写 `fix(<key>): ...` / `feat(<key>): ...`；
- 不改上游的版本号、签名配置、README（除非必要）；
- 共享文件只做最小改动，并在提交说明里写“为什么必须改共享层”；
- 每次同步上游后跑一遍 `tools/export-patches.sh`，把补丁留在 `patches/` 里，冲突时可直接 `git apply` 对照。
