@echo off
rem ============================================================================
rem  [ENCODING NOTICE] This file is saved as GBK / ANSI (code page 936).
rem  Do NOT re-save it as UTF-8, otherwise cmd.exe will mis-parse the Chinese
rem  text below and fail with "is not recognized as an internal or external
rem  command" errors.  （本文件为 GBK 编码，请勿另存为 UTF-8）
rem ============================================================================
rem
rem  Java工程分析工具 - Windows 启动脚本
rem
rem  用法：
rem    开发期：在项目根目录双击本脚本（自动使用 target\project-analysis-java.jar）
rem    交付后：把 project-analysis-java.jar 与本脚本放同一目录，双击即可
rem
rem  可选环境变量：
rem    CG_PORT        服务端口，默认 8080
rem    CG_ADDR        监听地址，默认 127.0.0.1（仅本机）。部署到服务器供他人访问时设为 0.0.0.0
rem    CALLGRAPH_HOME 数据目录，默认 D:\.callgraph（D 盘不可用时回退用户目录）
rem ============================================================================

cd /d "%~dp0"

set "PORT=8080"
if not "%CG_PORT%"=="" set "PORT=%CG_PORT%"
set "ADDR=127.0.0.1"
if not "%CG_ADDR%"=="" set "ADDR=%CG_ADDR%"

echo ============================================================
echo   Java工程分析工具
echo ============================================================
echo.

rem ---- 1) 定位 jar：优先同目录（交付布局），其次 target（开发布局） ----
set "JAR="
if exist "project-analysis-java.jar" set "JAR=project-analysis-java.jar"
if "%JAR%"=="" if exist "target\project-analysis-java.jar" set "JAR=target\project-analysis-java.jar"
if "%JAR%"=="" (
    echo [错误] 找不到 project-analysis-java.jar
    echo        请把 jar 与本脚本放在同一目录；
    echo        或先在项目根目录执行：mvn clean package -DskipTests
    echo.
    pause
    exit /b 1
)
echo [信息] 使用 jar  : %JAR%

rem ---- 2) 检查 Java（必需） ----
where java >nul 2>nul
if errorlevel 1 (
    echo [错误] 未检测到 java 命令。
    echo        请安装 JDK 11 及以上版本，并把 %%JAVA_HOME%%\bin 加入 PATH。
    echo        注意：必须是 JDK（不是 JRE），否则无构建工具的老工程无法编译。
    echo.
    pause
    exit /b 1
)
set "JAVA_VER="
for /f "tokens=3" %%v in ('java -version 2^>^&1 ^| findstr /i "version"') do set "JAVA_VER=%%v"
echo [信息] Java 版本: %JAVA_VER%

rem ---- 3) 检查 Maven / Git（缺失只告警，不中断） ----
where mvn >nul 2>nul
if errorlevel 1 (
    echo [警告] 未检测到 mvn：导入项目时无法编译工程，请安装 Maven 并加入 PATH。
) else (
    echo [信息] Maven    : 已检测到
)
where git >nul 2>nul
if errorlevel 1 (
    echo [警告] 未检测到 git：Git 仓库导入与分支切换不可用，请安装 Git 并加入 PATH。
) else (
    echo [信息] Git      : 已检测到
)

rem ---- 4) 数据目录（与程序内 CallgraphPaths 的优先级保持一致） ----
if defined CALLGRAPH_HOME (
    set "DATA_DIR=%CALLGRAPH_HOME%"
) else (
    if exist "D:\" (
        set "DATA_DIR=D:\.callgraph"
    ) else (
        set "DATA_DIR=%USERPROFILE%\.callgraph"
    )
)
echo [信息] 数据目录: %DATA_DIR%
echo [信息] 监听地址: %ADDR%:%PORT%
echo [信息] 本机访问: http://127.0.0.1:%PORT%
if not "%ADDR%"=="127.0.0.1" (
    echo [警告] 已对外网卡监听：其他机器可用 http://^<本机IP^>:%PORT% 访问，请确保网络隔离。
)
echo.
echo 启动中…… 关闭本窗口即可停止服务。
echo ============================================================
echo.

rem ---- 5) 后台等端口就绪后再开浏览器（避免打开过早显示无法访问） ----
start "" /b powershell -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -Command "$p=%PORT%; for($i=0;$i -lt 60;$i++){ try{ $c=New-Object Net.Sockets.TcpClient; $c.Connect('127.0.0.1',$p); $c.Close(); Start-Process ('http://127.0.0.1:'+$p); break } catch { Start-Sleep -Seconds 1 } }"

rem ---- 6) 前台启动服务：关窗即停 ----
java -jar "%JAR%" --server.address=%ADDR% --server.port=%PORT%

echo.
echo 服务已退出。
pause
