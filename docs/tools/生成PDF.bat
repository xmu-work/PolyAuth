@echo off
chcp 65001 >nul
cd /d "%~dp0"
echo ============================================
echo  生成《概要设计说明书》PDF
echo ============================================
where py >nul 2>nul
if %errorlevel%==0 (
  py -3 "%~dp0html2pdf.py"
) else (
  where python >nul 2>nul
  if %errorlevel%==0 (
    python "%~dp0html2pdf.py"
  ) else (
    echo 未找到 Python，改为直接调用 Edge...
    if exist "%ProgramFiles(x86)%\Microsoft\Edge\Application\msedge.exe" (
      "%ProgramFiles(x86)%\Microsoft\Edge\Application\msedge.exe" --headless=new --disable-gpu --hide-scrollbars --allow-file-access-from-files --no-pdf-header-footer --virtual-time-budget=30000 --print-to-pdf="%~dp0..\组号_统一应用软件权限管理系统_概要设计说明书.pdf" "%~dp0..\07_概要设计说明书.html"
    ) else (
      echo 未找到 Edge。请手动打开 HTML 后 Ctrl+P，勾选“背景图形”，另存为 PDF。
    )
  )
)
echo.
echo 完成或已输出诊断信息。PDF 位于 design 目录。
pause
