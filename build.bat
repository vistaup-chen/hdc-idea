@echo off
chcp 65001 >nul
setlocal

:: HDC Idea 一键编译脚本
:: 用法:
::   build.bat            → 编译并打包（默认）
::   build.bat run        → 编译并在 DevEco Studio sandbox 中启动 IDE 测试
::   build.bat clean      → 清理构建产物

if "%~1"=="clean" goto :clean
if "%~1"=="run"   goto :run

:: ── 自动定位 DevEco Studio 自带的 JBR ──────────────────────────────
if not defined JAVA_HOME (
    set "JBR_CANDIDATES=^
C:\Program Files\Huawei\DevEco Studio\jbr;^
C:\Program Files (x86)\Huawei\DevEco Studio\jbr;^
%USERPROFILE%\AppData\Local\Huawei\DevEco Studio\jbr;^
%USERPROFILE%\AppData\Local\Programs\DevEco Studio\jbr"
    for %%p in (%JBR_CANDIDATES%) do (
        if exist "%%p\bin\java.exe" (
            set "JAVA_HOME=%%p"
            echo [build] 自动检测到 JAVA_HOME=%%p
            goto :found_jbr
        )
    )
    echo [build] 未找到 DevEco Studio JBR，尝试使用系统 JAVA_HOME
    if not defined JAVA_HOME (
        echo [build] 错误: 未设置 JAVA_HOME，请安装 DevEco Studio 或手动设置 JAVA_HOME =exit /b 1
    )
)
:found_jbr

set "JAVA_HOME=%JAVA_HOME:\=/%"
echo.
echo ═══ 开始构建 HDC Idea ═══
echo JAVA_HOME = %JAVA_HOME%
echo.

:: buildSearchableOptions 需 IDE build ≥ 243，sandbox 用的是 IC-232 会崩溃
:: 该任务只是生成设置页搜索索引，跳过不影响插件功能
call gradlew.bat buildPlugin -x buildSearchableOptions -Dorg.gradle.jvmargs=-Xmx512m
if errorlevel 1 (
    echo.
    echo ═══ 构建失败 ═══
    exit /b 1
)

echo.
echo ═══ 构建成功 ═══
for %%f in (build\distributions\hdc-idea-*.zip) do echo 产物: %%f

endlocal
exit /b 0

:clean
call gradlew.bat clean
exit /b 0

:run
call gradlew.bat runIde
exit /b 0
