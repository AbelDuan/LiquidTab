#!/usr/bin/env bash
# 把 fork/upstream-base..custom 的补丁导出成可重放的 .patch 文件。
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
BASE="$(tr -d '[:space:]' < fork/upstream-base)"
mkdir -p patches
rm -f patches/*.patch
git format-patch --no-signature --zero-commit -o patches "$BASE..custom" >/dev/null
ls -1 patches
