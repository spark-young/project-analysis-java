#!/usr/bin/env bash
# ============================================================================
#  Java工程分析工具 - Linux / macOS 启动脚本
#
#  用法：
#    bash start.sh          （开发期在项目根目录 / 交付后与 jar 同目录）
#    chmod +x start.sh && ./start.sh
#
#  可选环境变量：
#    CG_PORT        服务端口，默认 8080
#    CG_ADDR        监听地址，默认 127.0.0.1（仅本机）。部署到服务器供他人访问时设为 0.0.0.0
#    CALLGRAPH_HOME 数据目录，默认 ~/.callgraph
# ============================================================================

set -u
cd "$(dirname "$0")" || exit 1

PORT="${CG_PORT:-8080}"
ADDR="${CG_ADDR:-127.0.0.1}"

echo "============================================================"
echo "  Java工程分析工具"
echo "============================================================"
echo

# ---- 1) 定位 jar：优先同目录（交付布局），其次 target（开发布局） ----
JAR=""
if [ -f "project-analysis-java.jar" ]; then
    JAR="project-analysis-java.jar"
elif [ -f "target/project-analysis-java.jar" ]; then
    JAR="target/project-analysis-java.jar"
else
    echo "[错误] 找不到 project-analysis-java.jar"
    echo "       请把 jar 与本脚本放在同一目录；"
    echo "       或先在项目根目录执行：mvn clean package -DskipTests"
    exit 1
fi
echo "[信息] 使用 jar  : $JAR"

# ---- 2) 检查 Java（必需） ----
if ! command -v java >/dev/null 2>&1; then
    echo "[错误] 未检测到 java 命令。"
    echo "       请安装 JDK 11 及以上版本，并配置 JAVA_HOME / PATH。"
    echo "       注意：必须是 JDK（不是 JRE），否则无构建工具的老工程无法编译。"
    exit 1
fi
echo "[信息] Java 版本: $(java -version 2>&1 | head -n 1)"

# ---- 3) 检查 Maven / Git（缺失只告警，不中断） ----
if command -v mvn >/dev/null 2>&1; then
    echo "[信息] Maven    : 已检测到"
else
    echo "[警告] 未检测到 mvn：导入项目时无法编译工程，请安装 Maven 并加入 PATH。"
fi
if command -v git >/dev/null 2>&1; then
    echo "[信息] Git      : 已检测到"
else
    echo "[警告] 未检测到 git：Git 仓库导入与分支切换不可用，请安装 Git 并加入 PATH。"
fi

# ---- 4) 数据目录（与程序内 CallgraphPaths 的优先级保持一致） ----
DATA_DIR="${CALLGRAPH_HOME:-$HOME/.callgraph}"
echo "[信息] 数据目录: $DATA_DIR"
echo "[信息] 监听地址: $ADDR:$PORT"
echo "[信息] 本机访问: http://127.0.0.1:$PORT"
if [ "$ADDR" != "127.0.0.1" ]; then
    echo "[警告] 已对外网卡监听：其他机器可用 http://<本机IP>:$PORT 访问，请确保网络隔离。"
fi
echo
echo "启动中…… 按 Ctrl+C 即可停止服务。"
echo "============================================================"
echo

# ---- 5) 后台等端口就绪后再开浏览器（避免打开过早显示无法访问） ----
(
    i=0
    while [ "$i" -lt 60 ]; do
        if (exec 3<>"/dev/tcp/127.0.0.1/$PORT") 2>/dev/null; then
            exec 3<&- 2>/dev/null
            if command -v xdg-open >/dev/null 2>&1; then
                xdg-open "http://127.0.0.1:$PORT" >/dev/null 2>&1
            elif command -v open >/dev/null 2>&1; then
                open "http://127.0.0.1:$PORT" >/dev/null 2>&1
            fi
            break
        fi
        i=$((i + 1))
        sleep 1
    done
) &

# ---- 6) 前台启动服务：Ctrl+C 即停 ----
java -jar "$JAR" --server.address="$ADDR" --server.port="$PORT"
