# -*- coding: utf-8 -*-
"""把 概要设计图/*.svg 渲染为 2 倍 PNG，便于目视检查排版。"""
import os
import re
import subprocess
import tempfile
from pathlib import Path

DESIGN = Path(__file__).resolve().parents[1]
FIG_DIR = DESIGN / "概要设计图"
OUT = FIG_DIR / "preview"
EDGE = r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe"
CHROME = r"C:\Program Files\Google\Chrome\Application\chrome.exe"

def browser():
    for p in (EDGE, CHROME):
        if os.path.exists(p):
            return p
    raise SystemExit("no browser")

def main():
    OUT.mkdir(exist_ok=True)
    b = browser()
    for svg in sorted(FIG_DIR.glob("*.svg")):
        text = svg.read_text(encoding="utf-8")
        m = re.search(r'width="(\d+)"\s+height="(\d+)"', text)
        if not m:
            print("skip", svg.name)
            continue
        w, h = int(m.group(1)), int(m.group(2))
        with tempfile.TemporaryDirectory(prefix="fig-") as tmp:
            tmp = Path(tmp)
            html = tmp / "p.html"
            import shutil
            shutil.copy2(svg, tmp / svg.name)
            html.write_text(
                "<!doctype html><html><head><meta charset='utf-8'>"
                "<style>html,body{margin:0;background:#fff}img{display:block}</style></head>"
                f"<body><img src='{svg.name}' width='{w}' height='{h}'></body></html>",
                encoding="utf-8",
            )
            out = OUT / (svg.stem + ".png")
            cmd = [b, "--headless=new", "--disable-gpu", "--hide-scrollbars",
                   f"--user-data-dir={tmp / 'prof'}", "--force-device-scale-factor=2",
                   f"--window-size={w},{h}", f"--screenshot={out}", html.as_uri()]
            subprocess.run(cmd, capture_output=True, timeout=120)
            print(out.name, out.stat().st_size if out.exists() else "FAILED")

if __name__ == "__main__":
    main()
