# -*- coding: utf-8 -*-
"""把 docs/07_概要设计说明书.html 渲染为 PDF。

优先使用本机 Chromium 内核浏览器（Edge / Chrome）无头模式导出。
用法：python docs/tools/html2pdf.py [输出文件.pdf]

说明：
- 使用临时 user-data-dir，避免浏览器已运行时配置目录被锁定；
- 依次尝试 --headless=new 与旧版 --headless；
- 打印浏览器 stderr 与 PDF 页数/页面尺寸，便于定位问题。
"""
import os
import re
import shutil
import subprocess
import sys
import tempfile
import time
from pathlib import Path

try:
    import fitz  # PyMuPDF，可选：存在时自动加页脚页码
except ImportError:  # pragma: no cover
    fitz = None

DESIGN_DIR = Path(__file__).resolve().parents[1]
HTML = DESIGN_DIR / "07_概要设计说明书.html"
DEFAULT_PDF = DESIGN_DIR / "组号_统一应用软件权限管理系统_概要设计说明书.pdf"


def candidates():
    paths = [
        os.path.expandvars(r"%ProgramFiles%\Google\Chrome\Application\chrome.exe"),
        os.path.expandvars(r"%ProgramFiles(x86)%\Google\Chrome\Application\chrome.exe"),
        os.path.expandvars(r"%LOCALAPPDATA%\Google\Chrome\Application\chrome.exe"),
        os.path.expandvars(r"%ProgramFiles(x86)%\Microsoft\Edge\Application\msedge.exe"),
        os.path.expandvars(r"%ProgramFiles%\Microsoft\Edge\Application\msedge.exe"),
        "/Applications/Google Chrome.app/Contents/MacOS/Google Chrome",
        "/usr/bin/google-chrome",
        "/usr/bin/chromium-browser",
    ]
    for name in ("chrome", "msedge", "chromium", "google-chrome"):
        found = shutil.which(name)
        if found:
            paths.append(found)
    seen = set()
    for p in paths:
        if p and p not in seen:
            seen.add(p)
            yield p


def page_info(pdf: Path):
    # 优先用 PyMuPDF 读取真实页数：增量保存后按字节正则统计会把 MediaBox 重复计数
    if fitz is not None:
        try:
            doc = fitz.open(pdf)
            sizes = [(pg.rect.width, pg.rect.height) for pg in doc]
            doc.close()
            if sizes:
                w, h = sizes[0]
                return len(sizes), w, h, sum(1 for a, b in sizes if a > b)
        except Exception:
            pass
    try:
        data = pdf.read_bytes()
    except OSError:
        return None
    boxes = re.findall(rb"/MediaBox\s*\[\s*0\s+0\s+([0-9.]+)\s+([0-9.]+)\s*\]", data)
    if not boxes:
        return None
    w, h = float(boxes[0][0]), float(boxes[0][1])
    landscape = sum(1 for a, b in boxes if float(a) > float(b))
    return len(boxes), w, h, landscape


def try_print(browser: str, pdf: Path, headless_flag: str) -> bool:
    with tempfile.TemporaryDirectory(prefix="iam-pdf-") as tmp:
        cmd = [
            browser,
            headless_flag,
            "--disable-gpu",
            "--hide-scrollbars",
            "--allow-file-access-from-files",
            "--no-pdf-header-footer",
            "--run-all-compositor-stages-before-draw",
            "--virtual-time-budget=30000",
            f"--user-data-dir={tmp}",
            f"--print-to-pdf={pdf}",
            HTML.as_uri(),
        ]
        print("执行：", " ".join(cmd))
        proc = subprocess.run(cmd, capture_output=True, text=True,
                              encoding="utf-8", errors="replace", timeout=180)
        out = (proc.stdout or "").strip()
        err = (proc.stderr or "").strip()
        if out:
            print("浏览器输出：", out[:1200])
        if err:
            print("浏览器错误：", err[:1200])
        return proc.returncode == 0 and pdf.exists() and pdf.stat().st_size > 4096


def add_page_numbers(pdf: Path) -> bool:
    """在 PDF 页脚居中写入“- 当前页 / 总页数 -”；无 PyMuPDF 时跳过。"""
    if fitz is None:
        print("未安装 PyMuPDF，跳过页脚页码（不影响正文）。")
        return False
    try:
        doc = fitz.open(pdf)
        total = doc.page_count
        for index, page in enumerate(doc):
            if index == 0:  # 封面不显示页码
                continue
            rect = page.rect
            box = fitz.Rect(0, rect.height - 24, rect.width, rect.height - 5)
            page.insert_textbox(box, f"- {index + 1} / {total} -", fontsize=8.5,
                                fontname="helv", color=(0.42, 0.42, 0.42), align=1)
        doc.saveIncr()
        doc.close()
        return True
    except Exception as exc:  # 页码失败不应阻断交付
        print("页脚页码写入失败（忽略）：", exc)
        return False


def publish(tmp_pdf: Path, pdf: Path):
    """把临时 PDF 发布为正式文件；若目标被查看器占用则重试，最后另存新版。"""
    for _ in range(15):
        try:
            os.replace(tmp_pdf, pdf)
            return pdf
        except PermissionError:
            time.sleep(2)
    alt = pdf.with_name(pdf.stem + "_更新版.pdf")
    os.replace(tmp_pdf, alt)
    print(f"目标文件正被查看器占用，已另存为：{alt}")
    print("关闭 PDF 查看器后，用资源管理器把“_更新版”重命名为正式文件名即可。")
    return alt


def main():
    pdf = Path(sys.argv[1]).resolve() if len(sys.argv) > 1 else DEFAULT_PDF
    tmp_pdf = pdf.with_name(pdf.stem + ".tmp.pdf")
    if not HTML.exists():
        print("找不到 HTML 源文件：", HTML)
        return 1
    browsers = [p for p in candidates() if os.path.exists(p)]
    if not browsers:
        print("未找到 Edge / Chrome。请用浏览器打开 HTML 后 Ctrl+P → 另存为 PDF（勾选“背景图形”）。")
        return 2
    print("HTML：", HTML)
    for browser in browsers:
        print("\n尝试浏览器：", browser)
        for flag in ("--headless=new", "--headless"):
            try:
                if tmp_pdf.exists():
                    tmp_pdf.unlink()
                ok = try_print(browser, tmp_pdf, flag)
            except (subprocess.TimeoutExpired, OSError) as exc:
                print("失败：", exc)
                continue
            if ok:
                add_page_numbers(tmp_pdf)
                final = publish(tmp_pdf, pdf)
                info = page_info(final)
                if info:
                    pages, w, h, landscape = info
                    print(f"完成：{final}（{final.stat().st_size / 1024:.0f} KB，{pages} 页）")
                    print(f"页面尺寸：{w:.0f} x {h:.0f} pt；横向页 {landscape} 张")
                else:
                    print(f"完成：{final}（{final.stat().st_size / 1024:.0f} KB）")
                return 0
            print("该模式未生成有效 PDF，尝试下一种。")
    print("\n全部尝试失败。请打开 HTML，用浏览器 Ctrl+P → 另存为 PDF，并勾选“背景图形”。")
    return 3


if __name__ == "__main__":
    sys.exit(main())
