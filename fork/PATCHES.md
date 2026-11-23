# 补丁清单

基线：见 [`upstream-base`](upstream-base)。

| 提交 | 范围 | 说明 |
| --- | --- | --- |
| `0db21fa fix(xhs): hide native search hint layer` | 小红书 9.50 | 只隐藏搜索图标时，顶栏会剩一个带背景的搜索提示 `TextView`（文案“搜索笔记，用户和商品”）。按结构隐藏整个搜索层，保留图标可点。 |
| `39fe371 fix(ci): normalize CRLF gradlew` | CI | 上游 `gradlew` 是 CRLF，Linux runner 无法执行。 |

补丁原则：

- 只动应用目录，共享层改动要单独说明；
- 按结构绑定，不按版本号；
- 失败保留原生 UI 并打日志。
