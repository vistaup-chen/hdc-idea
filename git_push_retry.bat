@echo off
:: 通过在指定目录反复推送至 GitHub 直到成功

:: 重试次数标记
set /a times=0

:: 清屏
cls

:: 检查外部传入的地址
if "%1"=="" (
	echo 未传入地址，使用当前目录
	goto START_COMMIT
) else (
	echo 跳转到指定目录：%1
	cd /d %1
	goto START_COMMIT
)

:START_COMMIT
:: =========================================================
:: 【核心修改点】把暂存和提交放到循环外面，只在最开始执行一次！
:: =========================================================
git add .
git commit -m "auto commit by retry script at %date% %time%" >nul 2>&1

:: 进入纯粹的 push 重试循环
:AA
echo.
set /a times+=1
echo 当前尝试推送第 %times% 次

:: 执行推送命令
git push origin main

:: 检查上一条命令（git push）的执行结果
if %errorlevel% EQU 0 (
	goto BB
) else (
	echo [提示] 推送失败，5 秒后进行第 %times% 次重试...
	choice /t 5 /d y /n >nul
	goto AA
)

:BB
echo.
echo.
echo ==========================================
echo  推送成功！共尝试了 %times% 次。
echo ==========================================
@pause