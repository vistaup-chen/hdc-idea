#!/usr/bin/env bash
# HDC Idea 一键编译脚本
# 用法:
#   ./build.sh        → 编译并打包（默认）
#   ./build.sh run    → 编译并在 sandbox 中启动 IDE 测试
#   ./build.sh clean  → 清理构建产物

set -e

cd "$(dirname "$0")"

if [ "$1" = "clean" ]; then
    ./gradlew clean
    exit 0
fi

if [ "$1" = "run" ]; then
    ./gradlew runIde
    exit 0
fi

# ── 自动定位 DevEco Studio 自带的 JBR ───────────────────────────────
if [ -z "$JAVA_HOME" ]; then
    JBR_CANDIDATES=(
        "$HOME/Applications/DevEco Studio.app/Contents/jbr"   # macOS
        "/opt/DevEco Studio/jbr"                               # Linux
        "$HOME/DevEcoStudio/jbr"
    )
    for jbr in "${JBR_CANDIDATES[@]}"; do
        if [ -x "$jbr/bin/java" ]; then
            export JAVA_HOME="$jbr"
            echo "[build] 自动检测到 JAVA_HOME=$jbr"
            break
        fi
    done
    if [ -z "$JAVA_HOME" ]; then
        echo "[build] 未找到 DevEco Studio JBR，尝试使用系统 JAVA_HOME"
    fi
fi

echo ""
echo "═══ 开始构建 HDC Idea ═══"
echo "JAVA_HOME = ${JAVA_HOME:-未设置}"
echo ""

# buildSearchableOptions 需 IDE build ≥ 243，sandbox 用的是 IC-232 会崩溃
# 该任务只是生成设置页搜索索引，跳过不影响插件功能
./gradlew buildPlugin -x buildSearchableOptions

echo ""
echo "═══ 构建成功 ═══"
ls -lh build/distributions/hdc-idea-*.zip
