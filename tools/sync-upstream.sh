#!/usr/bin/env bash
# 把 custom 分支上的补丁栈搬到上游新 tag 上。
# 用法: tools/sync-upstream.sh v2.2 [远端名，默认 upstream]
set -euo pipefail

TAG="${1:?用法: tools/sync-upstream.sh <上游tag> [远端名]}"
REMOTE="${2:-upstream}"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

BASE_FILE="fork/upstream-base"
[ -f "$BASE_FILE" ] || { echo "缺少 $BASE_FILE" >&2; exit 1; }
BASE="$(tr -d '[:space:]' < "$BASE_FILE")"

git rev-parse -q --verify "refs/tags/$TAG" >/dev/null || {
  echo "本地没有 $TAG，先执行: git fetch $REMOTE --tags" >&2; exit 1
}
git rev-parse -q --verify "refs/heads/custom" >/dev/null || { echo "缺少 custom 分支" >&2; exit 1; }
[ "$BASE" != "$TAG" ] || { echo "基线已经是 $TAG，无需同步" >&2; exit 1; }

mapfile -t PATCHES < <(git rev-list --reverse "$BASE..custom")
if [ "${#PATCHES[@]}" -eq 0 ]; then
  echo "custom 相对 $BASE 没有补丁，直接重置到 $TAG"
  git switch -c "sync/$TAG" "$TAG"
else
  echo "基线: $BASE -> $TAG，补丁数: ${#PATCHES[@]}"
  git switch -c "sync/$TAG" "$TAG"
  for commit in "${PATCHES[@]}"; do
    if ! git cherry-pick "$commit"; then
      cat >&2 <<EOF

cherry-pick $commit 失败，冲突现场已保留在 sync/$TAG。
处理步骤:
  1. 按冲突文件修正，保留我们补丁的意图，同时接受上游的新结构;
  2. git add <文件> && git cherry-pick --continue
  3. 全部完成后:
       echo $TAG > fork/upstream-base && git add fork/upstream-base
       git commit -m "chore: bump upstream base to $TAG"
       git switch custom && git reset --hard sync/$TAG
       git push origin custom
  放弃本次同步: git cherry-pick --abort && git switch custom && git branch -D sync/$TAG
EOF
      exit 1
    fi
  done
fi

echo "$TAG" > "$BASE_FILE"
git add "$BASE_FILE"
git commit -m "chore: bump upstream base to $TAG"
git switch custom
git reset --hard "sync/$TAG"
echo "完成：custom 已指向 $TAG + 补丁栈。检查后执行 git push origin custom --follow-tags"
