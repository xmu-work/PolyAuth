"""SVG / PNG exporter for the RBAC UML models.

The Draw.io pages remain the editable source.  This module renders the same
geometry (``ClassDiagram`` / ``DrawioSequence``) to standalone SVG so the
documentation can be regenerated without the Draw.io desktop application, and
rasterises the SVG to a 2x PNG with the locally installed Chromium browser.

Usage (usually through ``build_uml_catalog.py``)::

    from build_drawio_uml import structured_auth_class
    from uml_render import class_svg, svg_to_png

    svg = class_svg(structured_auth_class())
    Path("x.svg").write_text(svg, encoding="utf-8")
    svg_to_png(Path("x.svg"), Path("x.png"))
"""
from __future__ import annotations

import html
import re
import shutil
import subprocess
import tempfile
import time
from pathlib import Path
from xml.sax.saxutils import escape

from build_drawio_uml import (
    ClassDiagram,
    DrawioSequence,
    PALETTE,
    text_width,
)

FONT = "Microsoft YaHei, PingFang SC, Noto Sans CJK SC, sans-serif"
INK = "#172033"
MUTED = "#5a6678"
LINE = "#415269"
GRID = "#d9e2ec"
BLUE = "#1f4e8c"
EXCEPTION = "#8e3f4f"


# ---------------------------------------------------------------------------
# low level primitives
# ---------------------------------------------------------------------------

def _txt(
    x: float,
    y: float,
    value: str,
    size: float = 13,
    color: str = INK,
    anchor: str = "middle",
    weight: str = "normal",
    italic: bool = False,
) -> str:
    extra = ""
    if weight != "normal":
        extra += f' font-weight="{weight}"'
    if italic:
        extra += ' font-style="italic"'
    return (
        f'<text x="{x:.1f}" y="{y:.1f}" font-size="{size:.1f}" '
        f'fill="{color}" text-anchor="{anchor}"{extra} '
        f'font-family="{FONT}">{escape(value)}</text>'
    )


def _rect(
    x: float,
    y: float,
    w: float,
    h: float,
    fill: str = "#ffffff",
    stroke: str = LINE,
    width: float = 1.5,
    rx: float = 0,
    dash: str = "",
    opacity: float = 1.0,
) -> str:
    dash_attr = f' stroke-dasharray="{dash}"' if dash else ""
    return (
        f'<rect x="{x:.1f}" y="{y:.1f}" width="{w:.1f}" height="{h:.1f}" '
        f'rx="{rx:.1f}" fill="{fill}" fill-opacity="{opacity:.2f}" '
        f'stroke="{stroke}" stroke-width="{width:.1f}"{dash_attr}/>'
    )


def _line(
    x1: float, y1: float, x2: float, y2: float,
    color: str = LINE, width: float = 1.5, dash: str = "",
) -> str:
    dash_attr = f' stroke-dasharray="{dash}"' if dash else ""
    return (
        f'<line x1="{x1:.1f}" y1="{y1:.1f}" x2="{x2:.1f}" y2="{y2:.1f}" '
        f'stroke="{color}" stroke-width="{width:.1f}"{dash_attr}/>'
    )


def _path(points: list[tuple[float, float]], color: str = LINE,
          width: float = 1.5, dash: str = "") -> str:
    if not points:
        return ""
    d = "M" + " L".join(f"{x:.1f},{y:.1f}" for x, y in points)
    dash_attr = f' stroke-dasharray="{dash}"' if dash else ""
    return (
        f'<path d="{d}" fill="none" stroke="{color}" '
        f'stroke-width="{width:.1f}" stroke-linejoin="round"{dash_attr}/>'
    )


def _unit(start: tuple[float, float], end: tuple[float, float]) -> tuple[float, float]:
    dx, dy = end[0] - start[0], end[1] - start[1]
    length = (dx * dx + dy * dy) ** 0.5 or 1.0
    return dx / length, dy / length


def _open_arrow(tip: tuple[float, float], prev: tuple[float, float],
                color: str = LINE, size: float = 12) -> str:
    ux, uy = _unit(prev, tip)
    px, py = -uy, ux
    back = (tip[0] - ux * size, tip[1] - uy * size)
    left = (back[0] + px * size * 0.45, back[1] + py * size * 0.45)
    right = (back[0] - px * size * 0.45, back[1] - py * size * 0.45)
    return (
        _line(tip[0], tip[1], left[0], left[1], color, 1.5)
        + _line(tip[0], tip[1], right[0], right[1], color, 1.5)
    )


def _filled_arrow(tip: tuple[float, float], prev: tuple[float, float],
                  color: str = INK, size: float = 10) -> str:
    ux, uy = _unit(prev, tip)
    px, py = -uy, ux
    back = (tip[0] - ux * size, tip[1] - uy * size)
    left = (back[0] + px * size * 0.42, back[1] + py * size * 0.42)
    right = (back[0] - px * size * 0.42, back[1] - py * size * 0.42)
    return (
        f'<polygon points="{tip[0]:.1f},{tip[1]:.1f} '
        f'{left[0]:.1f},{left[1]:.1f} {right[0]:.1f},{right[1]:.1f}" '
        f'fill="{color}" stroke="{color}" stroke-width="1"/>'
    )


def _hollow_triangle(tip: tuple[float, float], prev: tuple[float, float],
                     color: str = LINE, size: float = 14) -> str:
    ux, uy = _unit(prev, tip)
    px, py = -uy, ux
    back = (tip[0] - ux * size, tip[1] - uy * size)
    left = (back[0] + px * size * 0.55, back[1] + py * size * 0.55)
    right = (back[0] - px * size * 0.55, back[1] - py * size * 0.55)
    return (
        f'<polygon points="{tip[0]:.1f},{tip[1]:.1f} '
        f'{left[0]:.1f},{left[1]:.1f} {right[0]:.1f},{right[1]:.1f}" '
        f'fill="#ffffff" stroke="{color}" stroke-width="1.4"/>'
    )


def _svg_open(width: float, height: float) -> list[str]:
    return [
        f'<svg xmlns="http://www.w3.org/2000/svg" width="{width:.0f}" '
        f'height="{height:.0f}" viewBox="0 0 {width:.0f} {height:.0f}" '
        f'font-family="{FONT}">',
        f'<rect width="{width:.0f}" height="{height:.0f}" fill="#ffffff"/>',
    ]


def _headings(parts: list[str], width: float, title: str, subtitle: str) -> None:
    parts.append(_txt(width / 2, 46, title, 27, INK, "middle", "bold"))
    if subtitle:
        parts.append(_txt(42, 84, subtitle, 15, MUTED, "start"))


def _footer(parts: list[str], width: float, height: float, footer: str) -> None:
    if not footer:
        return
    y = height - 70
    parts.append(_line(42, y, width - 42, y, GRID, 1))
    for index, line in enumerate(_wrap(footer, width - 84, 13, max_lines=3)):
        parts.append(_txt(42, y + 24 + index * 19, line, 13, MUTED, "start"))


# ---------------------------------------------------------------------------
# text helpers
# ---------------------------------------------------------------------------

def _wrap(value: str, max_width: float, size: float, max_lines: int = 4) -> list[str]:
    """Greedy wrap; prefers a break right after the opening parenthesis."""
    if text_width(value, size) <= max_width:
        return [value]
    split_at = value.find("(")
    if 0 < split_at < len(value) - 1:
        head, tail = value[: split_at + 1], value[split_at + 1:]
        if text_width(head, size) <= max_width and text_width(tail, size) <= max_width:
            return [head, tail]
    tokens: list[str] = []
    current = ""
    for char in value:
        current += char
        if char in " ,;、，":
            tokens.append(current)
            current = ""
    if current:
        tokens.append(current)
    lines: list[str] = []
    current = ""
    for token in tokens:
        candidate = current + token
        if current and text_width(candidate.rstrip(), size) > max_width:
            lines.append(current.rstrip())
            current = token.lstrip()
        else:
            current = candidate
    if current.strip():
        lines.append(current.rstrip())
    if len(lines) <= max_lines:
        return lines
    head = lines[: max_lines - 1]
    rest = "".join(lines[max_lines - 1:])
    while rest and len(head) < max_lines:
        cut = len(rest)
        while cut > 0 and text_width(rest[:cut], size) > max_width:
            cut -= 1
        head.append(rest[:cut])
        rest = rest[cut:]
    return head


def _fit_size(value: str, max_width: float, size: float, floor: float = 9.5) -> float:
    measured = text_width(value, size)
    if measured <= max_width:
        return size
    return max(floor, size * max_width / measured)


def _raw_label(value: str) -> str:
    value = value.replace("<br/>", "\n")
    return html.unescape(value).replace("\n", " ")


# ---------------------------------------------------------------------------
# class diagram
# ---------------------------------------------------------------------------

def _box_metrics(box) -> tuple[float, float, float, list[str], list[str]]:
    stereotype = len(box.header_parts) > 1
    header_h = 64.0 if stereotype else 50.0
    split = box.members.index("\x00")
    attributes = box.members[:split]
    operations = box.members[split + 1:]
    attr_h = max(34.0, 18 + len(attributes) * 21) if attributes else 24.0
    op_h = max(34.0, 18 + len(operations) * 21) if operations else 24.0
    return header_h, attr_h, op_h, attributes, operations


def _class_box_svg(box) -> list[str]:
    fill, stroke = PALETTE.get(box.role, ("#ffffff", INK))
    header_h, attr_h, _op_h, attributes, operations = _box_metrics(box)
    parts = [_rect(box.x, box.y, box.w, box.h, "#ffffff", stroke, 2, 7)]
    parts.append(
        f'<path d="M{box.x + 7:.1f},{box.y:.1f} H{box.x + box.w - 7:.1f} '
        f'Q{box.x + box.w:.1f},{box.y:.1f} {box.x + box.w:.1f},{box.y + 7:.1f} '
        f'V{box.y + header_h:.1f} H{box.x:.1f} V{box.y + 7:.1f} '
        f'Q{box.x:.1f},{box.y:.1f} {box.x + 7:.1f},{box.y:.1f} Z" '
        f'fill="{fill}" stroke="none"/>'
    )
    parts.append(_line(box.x, box.y + header_h,
                       box.x + box.w, box.y + header_h, stroke, 1.5))
    parts.append(_line(box.x, box.y + header_h + attr_h,
                       box.x + box.w, box.y + header_h + attr_h, stroke, 1.2))

    name = box.label
    title_size = _fit_size(name, box.w - 28, 19.0, 13.0)
    if len(box.header_parts) > 1:
        parts.append(_txt(box.x + box.w / 2, box.y + 22,
                          box.header_parts[0], 12, MUTED, "middle", italic=True))
        parts.append(_txt(box.x + box.w / 2, box.y + 46, name,
                          title_size, INK, "middle", "bold"))
    else:
        parts.append(_txt(box.x + box.w / 2, box.y + 31, name,
                          title_size, INK, "middle", "bold"))

    y = box.y + header_h + 6
    for item in attributes:
        parts.append(_txt(box.x + 12, y + 13, item,
                          _fit_size(item, box.w - 24, 13.0), INK, "start"))
        y += 21
    y = box.y + header_h + attr_h + 6
    for item in operations:
        parts.append(_txt(box.x + 12, y + 13, item,
                          _fit_size(item, box.w - 24, 13.0), INK, "start"))
        y += 21
    return parts


def _class_edge_svg(edge: dict) -> list[str]:
    points = [(float(x), float(y)) for x, y in edge["points"]]
    kind = edge["kind"]
    dashed = kind in {"dependency", "implementation"}
    parts = [_path(points, LINE,
                   1.7 if kind == "association" else 1.5,
                   "7 5" if dashed else "")]
    tip, prev = points[-1], points[-2]
    if kind == "implementation":
        parts.append(_hollow_triangle(tip, prev, LINE))
    else:
        parts.append(_open_arrow(tip, prev, LINE))
    return parts


def class_svg(diagram: ClassDiagram) -> str:
    diagram.assert_no_overlap()
    diagram.assert_edges_clear_of_boxes()
    parts = _svg_open(diagram.width, diagram.height)
    _headings(parts, diagram.width, diagram.title, diagram.subtitle)

    for edge in diagram.edges:
        parts.extend(_class_edge_svg(edge))
    for box in diagram.boxes:
        parts.extend(_class_box_svg(box))

    for edge in diagram.edges:
        if edge["kind"] != "association" or not edge.get("role"):
            continue
        lx, ly = edge["label_at"]
        role = str(edge["role"])
        width = max(64.0, text_width(role, 13) + 14)
        parts.append(_rect(lx - width / 2, ly - 18, width, 22,
                           "#ffffff", "none", 0, 0, "", 0.94))
        parts.append(_txt(lx, ly - 1, role, 13, BLUE, "middle"))

    _footer(parts, diagram.width, diagram.height, diagram.footer)
    parts.append("</svg>")
    return "\n".join(parts)


# ---------------------------------------------------------------------------
# sequence diagram
# ---------------------------------------------------------------------------

def _lifeline_svg(line, height: float) -> list[str]:
    parts = [
        _rect(line.x - line.width / 2, line.box_y, line.width, line.box_h,
              "#f8fafc", "#4f647a", 2, 6),
        _line(line.x, line.box_y + line.box_h, line.x, height - 54,
              "#7b8a9d", 1.4, "7 5"),
    ]
    if line.meta:
        parts.append(_txt(line.x, line.box_y + 22, "«MetaClass»",
                          line.font_size, MUTED, "middle", italic=True))
        parts.append(_txt(line.x, line.box_y + 46, f": {line.class_name}",
                          line.font_size, INK, "middle", "bold"))
    else:
        parts.append(_txt(
            line.x,
            line.box_y + line.box_h / 2 + line.font_size * 0.36,
            f"{line.object_name}: {line.class_name}",
            line.font_size, INK, "middle",
        ))
    return parts


def _fragment_svg(frag) -> list[str]:
    return [_rect(frag.x1, frag.y1, frag.x2 - frag.x1, frag.y2 - frag.y1,
                  "#f7fbff", "#72839a", 1.4, 0, "5 4", 0.72)]


def _fragment_tab_svg(frag) -> list[str]:
    """The tab and its label are painted above the lifelines so they stay legible."""
    x1, y1 = frag.x1, frag.y1
    label_width = text_width(frag.label, 13.5) + 22
    return [
        f'<path d="M{x1:.1f},{y1:.1f} H{x1 + 64:.1f} V{y1 + 24:.1f} '
        f'H{x1 + 15:.1f} L{x1:.1f},{y1 + 34:.1f} Z" fill="#eef4fb" '
        f'stroke="#72839a" stroke-width="1.4"/>',
        _rect(x1 + 66, y1 + 1, label_width, 23, "#ffffff", "none", 0, 0, "", 0.92),
        _txt(x1 + 32, y1 + 17, frag.kind, 13.5, "#40546d", "middle", "bold"),
        _txt(x1 + 77, y1 + 18, frag.label, 13.5, "#40546d", "start"),
    ]


def _message_svg(message) -> list[str]:
    source_x = DrawioSequence._side(message.source, message.target.lifeline.x)
    target_x = DrawioSequence._side(message.target, message.source.lifeline.x)
    y = message.y
    is_reply = message.kind in {"reply", "exception"}
    color = EXCEPTION if message.kind == "exception" else LINE
    parts = []
    if is_reply:
        parts.append(_line(source_x, y, target_x, y, color, 1.5, "6 4"))
        parts.append(_open_arrow((target_x, y), (source_x, y), color, 10))
    else:
        parts.append(_line(source_x, y, target_x, y, INK, 1.6))
        parts.append(_filled_arrow((target_x, y), (source_x, y), INK, 9))

    label = _raw_label(message.label)
    if not label:
        return parts
    gap = max(140.0, abs(target_x - source_x) - 26)
    size = 14.0
    lines = _wrap(label, gap, size, max_lines=3)
    while size > 11.0 and any(text_width(item, size) > gap for item in lines):
        size -= 0.5
        lines = _wrap(label, gap, size, max_lines=3)
    center = (source_x + target_x) / 2
    for index, item in enumerate(lines):
        baseline = y - 10 - (len(lines) - 1 - index) * 17
        width = text_width(item, size)
        parts.append(_rect(center - width / 2 - 4, baseline - size + 1,
                           width + 8, size + 6, "#ffffff", "none", 0, 0, "", 0.93))
        parts.append(_txt(center, baseline, item, size, color))
    return parts


def _note_svg(x: float, y: float, width: float, lines: list[str]) -> list[str]:
    height = 22 + len(lines) * 19
    fold = 16
    parts = [
        f'<path d="M{x:.1f},{y:.1f} H{x + width - fold:.1f} '
        f'L{x + width:.1f},{y + fold:.1f} V{y + height:.1f} '
        f'H{x:.1f} Z" fill="#fffbe8" stroke="#b18b2f" stroke-width="1.4"/>',
        f'<path d="M{x + width - fold:.1f},{y:.1f} '
        f'V{y + fold:.1f} H{x + width:.1f}" fill="none" '
        f'stroke="#b18b2f" stroke-width="1.2"/>',
    ]
    for index, item in enumerate(lines):
        parts.append(_txt(x + 11, y + 20 + index * 19, item, 13, "#5d4a18", "start"))
    return parts


def sequence_svg(sequence: DrawioSequence) -> str:
    sequence.assert_valid()
    parts = _svg_open(sequence.width, sequence.height)
    _headings(parts, sequence.width, sequence.title, sequence.subtitle)

    # fragments stay below messages and execution specifications
    for fragment in sequence.fragments:
        parts.extend(_fragment_svg(fragment))
    for line in sequence.lifelines:
        parts.extend(_lifeline_svg(line, sequence.height))
    for act in sequence.activations:
        parts.append(_rect(act.x0, act.y1, act.width, act.y2 - act.y1,
                           "#e7eef7", "#415269", 1.2, 0))
    for message in sequence.messages:
        parts.extend(_message_svg(message))

    for x1, x2, y, label in sequence.guards:
        parts.append(_line(x1, y, x2, y, "#8a9aac", 1.1, "5 4"))
        width = text_width(label, 13) + 18
        parts.append(_rect(x1 + 8, y - 21, width, 23,
                           "#ffffff", "none", 0, 0, "", 0.95))
        parts.append(_txt(x1 + 17, y - 5, label, 13, "#4f647a", "start"))

    for x, y, width, lines in sequence.notes:
        parts.extend(_note_svg(x, y, width, lines))
    for fragment in sequence.fragments:
        parts.extend(_fragment_tab_svg(fragment))

    _footer(parts, sequence.width, sequence.height, sequence.footer)
    parts.append("</svg>")
    return "\n".join(parts)


# ---------------------------------------------------------------------------
# rasterisation
# ---------------------------------------------------------------------------

_BROWSERS = (
    r"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe",
    r"C:\Program Files\Microsoft\Edge\Application\msedge.exe",
    r"C:\Program Files\Google\Chrome\Application\chrome.exe",
    r"C:\Program Files (x86)\Google\Chrome\Application\chrome.exe",
)


def _browser() -> str | None:
    for candidate in _BROWSERS:
        if Path(candidate).exists():
            return candidate
    for name in ("msedge", "chrome", "google-chrome", "chromium"):
        found = shutil.which(name)
        if found:
            return found
    return None


def svg_to_png(svg_path: Path, png_path: Path, scale: int = 2) -> bool:
    """Render ``svg_path`` to ``png_path`` with headless Edge/Chrome."""
    browser = _browser()
    if browser is None:
        print(f"[warn] no Chromium browser found; skip PNG for {svg_path.name}")
        return False
    text = svg_path.read_text(encoding="utf-8")
    match = re.search(r'width="(\d+)"\s+height="(\d+)"', text)
    if not match:
        raise ValueError(f"cannot read canvas size from {svg_path}")
    width, height = int(match.group(1)), int(match.group(2))
    with tempfile.TemporaryDirectory(prefix="uml-png-") as tmp:
        tmp_dir = Path(tmp)
        shutil.copy2(svg_path, tmp_dir / "in.svg")
        (tmp_dir / "page.html").write_text(
            "<!doctype html><html><head><meta charset='utf-8'>"
            "<style>html,body{margin:0;background:#fff;overflow:hidden}"
            "img{display:block}</style></head>"
            f"<body><img src='in.svg' width='{width}' height='{height}'></body></html>",
            encoding="utf-8",
        )
        out = tmp_dir / "out.png"
        profile = tmp_dir / "profile"
        command = [
            browser, "--headless=new", "--disable-gpu", "--hide-scrollbars",
            "--no-first-run", "--disable-extensions",
            f"--user-data-dir={profile}",
            f"--force-device-scale-factor={scale}",
            f"--window-size={width},{height}",
            f"--screenshot={out}",
            (tmp_dir / "page.html").as_uri(),
        ]
        subprocess.run(command, capture_output=True, timeout=180)
        for _ in range(40):
            if out.exists() and out.stat().st_size > 0:
                break
            time.sleep(0.25)
        if not out.exists():
            print(f"[warn] browser produced no PNG for {svg_path.name}")
            return False
        png_path.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(out, png_path)
        return True
