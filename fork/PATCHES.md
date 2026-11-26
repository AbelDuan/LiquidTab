# 补丁清单

基线：见 [`upstream-base`](upstream-base)。

## 小红书

| 提交 | 说明 |
| --- | --- |
| `0db21fa` `fix(xhs): hide native search hint layer` | 只隐藏搜索图标时，9.50/9.55 顶栏会剩一个带背景的搜索胶囊（提示文案或热词）。新增 `XhsSearchChrome`，按结构隐藏整层/胶囊。 |
| `5b76f2e` `fix(xhs): discover native search capsule in tab band` | 9.55 的放大镜是 `TextView` 的 compound drawable，不是独立图标；改为扫描顶栏带内、标签右侧、带背景的文字胶囊。 |
| `f8be902` `feat(xhs): support PadHomeTabLayout` | 内屏走 `PadHomeTabLayout`，与 `HomeTabLayout` 是兄弟类；补 hook，否则内屏完全退回原生头部。 |

## 美团

| 提交 | 说明 |
| --- | --- |
| `d5004ea` `fix(meituan): follow the native slot count` | 12.69.402 撤掉视频 tab、底栏多包一层 `PTLinearLayout`，行内只剩 4 个槽；不再写死 5，选中/点击/静态切片全部改按 slot 序号，并新增 `navigationHiddenMask` 钩子避免「视频位」误伤真实 tab。 |

| `4d5a934` `fix(geometry): keep the glass bar inside the app's own window` | 栏宽取 content 与 displayMetrics 的交集（原来取较大者），折叠内屏与平行视界不再超宽。 |
| `584a058` `fix(geometry): re-derive the bar when the host window resizes` | host 监听 content 布局变化并重算几何；美团打开 `refreshHostGeometryOnRebind`。冷启动内屏不再保留错误宽度。 |

## 微博

| 提交 | 说明 |
| --- | --- |
| `ac4928b` `fix(theme): let a force-dark-disabled app keep its own light palette` | 声明 `disablePlatformForceDark` 的应用（微博）改为以自身 Configuration 判定深浅，系统 auto/全局 dark_mode 不再把浅色页面配成深色玻璃；不写死浅色，微博切深色仍变深。 |
| `34478e4` | 判定诊断改走 XposedBridge 日志（每 host ≤4 条）。 |

## CI / 仓库

| 提交 | 说明 |
| --- | --- |
| `1d8900d` | custom 分支、构建与同步脚本、README |
| `390abc5` | 上游 release 监视 workflow |
| `a8f150a` / `39fe371` / `b19f6e6` | Linux runner：SDK 37.0 安装、CRLF `gradlew`、跳过 `lintVitalRelease` |

补丁原则：

- 只动应用目录，共享层改动要单独说明；
- 按结构绑定，不按版本号；
- 失败保留原生 UI，不吞异常。
