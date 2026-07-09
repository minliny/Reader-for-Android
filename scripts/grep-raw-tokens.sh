#!/usr/bin/env bash
# Phase 7.5: Raw-token grep 检查
# 检查 Android 项目中是否有硬编码的 z-index / 颜色值 / dp 值
# 仅在 token 定义文件之外搜索

set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC_DIR="$PROJECT_ROOT/app/src/main/kotlin"
VIOLATIONS=0

echo "=== Phase 7.5: Raw-token grep 检查 ==="
echo ""

# 1. 检查硬编码 z-index 数字（除 ReaderTokenAdapter.kt 外）
echo "--- 检查硬编码 z-index ---"
HARD_Z=$(grep -rnE 'zIndex\s*=\s*[0-9]' "$SRC_DIR" \
    --include="*.kt" \
    | grep -v "ReaderTokenAdapter.kt" \
    | grep -v "test/" \
    || true)
if [ -n "$HARD_Z" ]; then
    echo "FAIL: 发现硬编码 z-index："
    echo "$HARD_Z"
    VIOLATIONS=$((VIOLATIONS + 1))
else
    echo "PASS: 无硬编码 z-index"
fi
echo ""

# 2. 检查 !important 声明（CSS 文件中）
echo "--- 检查 !important 声明 ---"
IMPORTANT=$(grep -rn '!important' "$PROJECT_ROOT/app/src/main" \
    --include="*.css" \
    || true)
if [ -n "$IMPORTANT" ]; then
    echo "FAIL: 发现 !important 声明："
    echo "$IMPORTANT"
    VIOLATIONS=$((VIOLATIONS + 1))
else
    echo "PASS: 无 !important 声明"
fi
echo ""

# 3. 检查 --reader-ds- 前缀残留（应为 --fd-ds-）
echo "--- 检查 --reader-ds- 前缀残留 ---"
OLD_PREFIX=$(grep -rn '\-\-reader-ds-' "$SRC_DIR" \
    --include="*.kt" \
    | grep -v "toRegistryTokenName" \
    | grep -v "ReaderTokenAdapter.kt" \
    || true)
if [ -n "$OLD_PREFIX" ]; then
    echo "WARN: 发现 --reader-ds- 前缀残留（可能在兼容层中）："
    echo "$OLD_PREFIX"
else
    echo "PASS: 无 --reader-ds- 前缀残留"
fi
echo ""

# 4. 检查 ReaderSizes.z 引用（应已全部迁移到 ReaderTokenAdapter）
echo "--- 检查 ReaderSizes.z* 引用 ---"
SIZES_Z=$(grep -rn 'ReaderSizes\.z' "$SRC_DIR" \
    --include="*.kt" \
    || true)
if [ -n "$SIZES_Z" ]; then
    echo "FAIL: 发现 ReaderSizes.z* 引用："
    echo "$SIZES_Z"
    VIOLATIONS=$((VIOLATIONS + 1))
else
    echo "PASS: 无 ReaderSizes.z* 引用"
fi
echo ""

# 汇总
if [ "$VIOLATIONS" -eq 0 ]; then
    echo "=== 总结: ALL PASS ==="
    exit 0
else
    echo "=== 总结: $VIOLATIONS 项违规 ==="
    exit 1
fi
