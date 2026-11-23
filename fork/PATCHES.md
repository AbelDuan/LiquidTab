# 补丁清单

基线：见 [`upstream-base`](upstream-base)。

## 小红书

| 提交 | 说明 |
| --- | --- |
| `0db21fa` `fix(xhs): hide native search hint layer` | 只隐藏搜索图标时，9.50/9.55 顶栏会剩一个带背景的搜索胶囊（提示文案或热词）。新增 `XhsSearchChrome`，按结构隐藏整层/胶囊。 |
| `5b76f2e` `fix(xhs): discover native search capsule in tab band` | 9.55 的放大镜是 `TextView` 的 compound drawable，不是独立图标；改为扫描顶栏带内、标签右侧、带背景的文字胶囊。 |
| `f8be902` `feat(xhs): support PadHomeTabLayout` | 内屏走 `PadHomeTabLayout`，与 `HomeTabLayout` 是兄弟类；补 hook，否则内屏完全退回原生头部。 |

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
