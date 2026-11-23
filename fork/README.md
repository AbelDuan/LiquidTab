# LiquidTab 自定义维护仓库

上游：<https://github.com/big-dictator/LiquidTab>（GPL-3.0）
本仓库：<https://github.com/AbelDuan/LiquidTab>

这个 fork 只做三件事：

1. 保留上游全部历史与 tag，随时能看清上游改了什么。
2. `custom` 分支放我们自己的补丁（默认分支），每个补丁一个独立提交，便于回滚。
3. GitHub Actions 自动出未签名/已签名 APK，原仓库没适配的新版应用，我们自己改完就能装。

## 分支约定

| 分支/标签 | 用途 |
| --- | --- |
| `main` | 上游原样镜像，只用于 `git fetch upstream` 和对照，不直接构建 |
| `custom` | 生产分支：`v2.1.2` + 我们的补丁，默认分支，CI 从这里出包 |
| `custom-v*` | 我们自己的发布标签，推送后自动创建 GitHub Release |
| `v*`（上游标签） | 上游每个版本的源码快照，`fork/upstream-base` 记录当前补丁基于哪个标签 |

`fork/upstream-base` 是当前补丁栈的基线。同步上游时脚本会读它，并把补丁逐个 cherry-pick 到新 tag 上。

## 快速同步上游

上游发新版本（比如 `v2.2`）后：

```bash
git remote add upstream https://github.com/big-dictator/LiquidTab.git  # 只需一次
git fetch upstream --tags
tools/sync-upstream.sh v2.2
git push origin custom --follow-tags
```

脚本会：

1. 从 `v2.2` 建 `sync/v2.2` 分支；
2. 把 `v2.1.2..custom` 的补丁逐个 cherry-pick 过去；
3. 更新 `fork/upstream-base`；
4. 把 `custom` 重置到同步结果（只在成功后执行）。

有冲突时脚本会停下，保留 `sync/v2.2` 现场。按提示解决后 `git cherry-pick --continue`，再手工把 `custom` 指过去。**不要**用 GitHub 网页的 “Sync fork” 覆盖 `custom`。

补丁导出/重放：

```bash
tools/export-patches.sh        # 导出到 patches/
```

## 出包

推送到 `custom` 或手动 `workflow_dispatch` 即构建：

- 有 `KEYSTORE_B64` 等 secrets：直接产出已签名 APK + SHA256；
- 没有 secrets：产出 `-unsigned.apk`，下载后本地用 `apksigner` 签。

发布标签：

```bash
git tag custom-v2.1.2.1
git push origin custom-v2.1.2.1
```

CI 会创建 Release 并附 APK。

## 安装

本机（HyperOS / KernelSU）允许跨签名覆盖安装，可直接盖官方包并保留数据；其他设备若签名不同，需要先卸载官方版（会丢模块设置）。

## 补丁清单

见 [`PATCHES.md`](PATCHES.md)。每个补丁都应满足：

- 只动某个应用目录，或只动该应用会用到的共享点；
- 不按版本号 gate 行为，用结构/语义绑定；
- 失败时保留原生界面并打日志，不白屏、不吞异常。

## 新应用版本适配

上游没跟上某个应用改版时，看 [`ADAPTING.md`](ADAPTING.md)。
