"""UML 类图与顺序图的轻量 SVG 生成器。

该模块只覆盖本项目 UML 图所需能力，并主动检查类框重叠、连线穿框、
lifeline 文本溢出和执行条端点。
"""
from __future__ import annotations

import math
import re
from dataclasses import dataclass, field
from pathlib import Path
from xml.sax.saxutils import escape


FONT = "Microsoft YaHei, Noto Sans CJK SC, SimHei, sans-serif"
TEXT = "#172033"
MUTED = "#5a6678"
BLUE = "#1f4e8c"
BLUE_FILL = "#e8f1fc"
INK = "#27364a"
LINE = "#415269"
PANEL = "#ffffff"
GRID = "#d9e2ec"


def text_width(value: str, size: float) -> float:
    """保守估计文本宽度，中文按 1 em、英文按 0.62 em 计算。"""
    width = 0.0
    for char in value:
        if char == "\t":
            width += size * 2
        elif ord(char) > 0x2E80:
            width += size
        elif char.isupper():
            width += size * 0.68
        elif char in "ilI1.,:;'|!":
            width += size * 0.34
        elif char in "mwMW@#%&":
            width += size * 0.82
        else:
            width += size * 0.58
    return width


def _esc(value: str) -> str:
    return escape(str(value))


def _rect_contains(rect: tuple[float, float, float, float],
                   point: tuple[float, float], padding: float = 0.0) -> bool:
    x, y, w, h = rect
    px, py = point
    return (x + padding < px < x + w - padding
            and y + padding < py < y + h - padding)


def _segment_hits_rect(a: tuple[float, float], b: tuple[float, float],
                       rect: tuple[float, float, float, float], padding: float = 6.0) -> bool:
    x, y, w, h = rect
    x0, y0, x1, y1 = x + padding, y + padding, x + w - padding, y + h - padding
    ax, ay = a
    bx, by = b

    def inside(px: float, py: float) -> bool:
        return x0 <= px <= x1 and y0 <= py <= y1

    if inside(ax, ay) or inside(bx, by):
        return True
    edges = [((x0, y0), (x1, y0)), ((x1, y0), (x1, y1)),
             ((x1, y1), (x0, y1)), ((x0, y1), (x0, y0))]
    return any(_segments_intersect((ax, ay), (bx, by), c, d) for c, d in edges)


def _segments_intersect(a: tuple[float, float], b: tuple[float, float],
                        c: tuple[float, float], d: tuple[float, float]) -> bool:
    def orientation(p: tuple[float, float], q: tuple[float, float],
                    r: tuple[float, float]) -> float:
        return (q[1] - p[1]) * (r[0] - q[0]) - (q[0] - p[0]) * (r[1] - q[1])

    def on_segment(p: tuple[float, float], q: tuple[float, float],
                   r: tuple[float, float]) -> bool:
        return (min(p[0], r[0]) <= q[0] <= max(p[0], r[0])
                and min(p[1], r[1]) <= q[1] <= max(p[1], r[1]))

    o1 = orientation(a, b, c)
    o2 = orientation(a, b, d)
    o3 = orientation(c, d, a)
    o4 = orientation(c, d, b)
    eps = 1e-6
    if o1 * o2 < -eps and o3 * o4 < -eps:
        return True
    if abs(o1) <= eps and on_segment(a, c, b):
        return True
    if abs(o2) <= eps and on_segment(a, d, b):
        return True
    if abs(o3) <= eps and on_segment(c, a, d):
        return True
    if abs(o4) <= eps and on_segment(c, b, d):
        return True
    return False


@dataclass
class Box:
    box_id: str
    x: float
    y: float
    w: float
    h: float
    label: str

    @property
    def rect(self) -> tuple[float, float, float, float]:
        return self.x, self.y, self.w, self.h


@dataclass
class ClassBox(Box):
    header_parts: list[str] = field(default_factory=list)
    members: list[str] = field(default_factory=list)
    role: str = "class"


@dataclass
class Activation:
    lifeline: str
    x: float
    y1: float
    y2: float
    width: float = 12

    @property
    def x0(self) -> float:
        return self.x - self.width / 2

    @property
    def x1(self) -> float:
        return self.x + self.width / 2


class ClassDiagram:
    """带几何断言的三区 UML 类图。"""

    PALETTE = {
        "service": ("#e8f1fc", "#1f4e8c"),
        "caller": ("#f5f1fb", "#6b5b95"),
        "interface": ("#eef7f1", "#3d7a57"),
        "domain": ("#fffaf0", "#a17a2c"),
        "infra": ("#fdf2f2", "#9a4c4c"),
        "enum": ("#f1f5f9", "#52657a"),
    }

    def __init__(self, width: float, height: float, title: str, subtitle: str = ""):
        self.width = width
        self.height = height
        self.title = title
        self.subtitle = subtitle
        self.boxes: list[ClassBox] = []
        self.edges: list[dict] = []
        self.labels: list[str] = []
        self.footer = ""

    def add_box(self, box_id: str, x: float, y: float, width: float,
                name: str, attributes: list[str] | None = None,
                operations: list[str] | None = None, stereotype: str | None = None,
                role: str = "class") -> ClassBox:
        attributes = attributes or []
        operations = operations or []
        header_parts = ([stereotype] if stereotype else []) + [name]
        header_h = 64 if stereotype else 50
        attr_h = max(34, 18 + len(attributes) * 21) if attributes else 24
        op_h = max(34, 18 + len(operations) * 21) if operations else 24
        height = header_h + attr_h + op_h
        box = ClassBox(box_id, x, y, width, height, name, header_parts,
                       attributes + ["\x00"] + operations, role)
        self.boxes.append(box)
        return box

    def _draw_box(self, box: ClassBox) -> str:
        fill, stroke = self.PALETTE.get(box.role, ("#fff", INK))
        parts = [
            f'<rect x="{box.x}" y="{box.y}" width="{box.w}" height="{box.h}" '
            f'rx="7" fill="{PANEL}" stroke="{stroke}" stroke-width="1.8"/>'
        ]
        header_h = 64 if len(box.header_parts) > 1 else 50
        parts.append(
            f'<path d="M{box.x + 7},{box.y} H{box.x + box.w - 7} '
            f'Q{box.x + box.w},{box.y} {box.x + box.w},{box.y + 7} '
            f'V{box.y + header_h} H{box.x} V{box.y + 7} '
            f'Q{box.x},{box.y} {box.x + 7},{box.y} Z" '
            f'fill="{fill}" stroke="{stroke}" stroke-width="1.8"/>'
        )
        split = box.members.index("\x00")
        attributes = box.members[:split]
        operations = box.members[split + 1:]
        attr_h = max(34, 18 + len(attributes) * 21) if attributes else 24
        attr_y = box.y + header_h
        parts.append(
            f'<line x1="{box.x}" y1="{attr_y + attr_h}" x2="{box.x + box.w}" '
            f'y2="{attr_y + attr_h}" stroke="{stroke}" stroke-width="1.2"/>'
        )

        if len(box.header_parts) == 2:
            parts.append(
                f'<text x="{box.x + box.w / 2}" y="{box.y + 22}" text-anchor="middle" '
                f'font-size="12.5" font-style="italic" fill="{MUTED}">'
                f'{_esc(box.header_parts[0])}</text>'
            )
            title_y = box.y + 45
        else:
            title_y = box.y + 32
        name_size = 16.5
        if text_width(box.header_parts[-1], name_size) > box.w - 24:
            name_size = max(13, (box.w - 24) / max(1, len(box.header_parts[-1]) * 0.72))
        parts.append(
            f'<text x="{box.x + box.w / 2}" y="{title_y}" text-anchor="middle" '
            f'font-size="{name_size:.1f}" font-weight="700" fill="{TEXT}">'
            f'{_esc(box.header_parts[-1])}</text>'
        )

        y = attr_y + 22
        for line in attributes:
            parts.append(
                f'<text x="{box.x + 14}" y="{y}" font-size="12.8" '
                f'fill="#26364a">{_esc(line)}</text>'
            )
            y += 21
        y = attr_y + attr_h + 22
        for line in operations:
            parts.append(
                f'<text x="{box.x + 14}" y="{y}" font-size="12.8" '
                f'fill="#26364a">{_esc(line)}</text>'
            )
            y += 21
        return "\n".join(parts)

    def association(self, points: list[tuple[float, float]], role: str,
                    label_at: tuple[float, float] | None = None,
                    source: str | None = None, target: str | None = None) -> None:
        self.edges.append({
            "points": points,
            "kind": "association",
            "source": source,
            "target": target,
        })
        if label_at is None:
            (ax, ay), (bx, by) = points[-2], points[-1]
            label_at = ((ax + bx) / 2, (ay + by) / 2 - 9)
        self.labels.append(
            f'<rect x="{label_at[0] - text_width(role, 12) / 2 - 4}" '
            f'y="{label_at[1] - 13}" width="{text_width(role, 12) + 8}" height="18" '
            f'rx="3" fill="#fff" opacity="0.96"/>'
            f'<text x="{label_at[0]}" y="{label_at[1]}" text-anchor="middle" '
            f'font-size="12" fill="{BLUE}">{_esc(role)}</text>'
        )

    def dependency(self, points: list[tuple[float, float]],
                   source: str | None = None, target: str | None = None) -> None:
        self.edges.append({
            "points": points,
            "kind": "dependency",
            "source": source,
            "target": target,
        })

    def implementation(self, points: list[tuple[float, float]],
                       source: str | None = None, target: str | None = None) -> None:
        self.edges.append({
            "points": points,
            "kind": "implementation",
            "source": source,
            "target": target,
        })

    def assert_no_overlap(self) -> None:
        for index, left in enumerate(self.boxes):
            if left.x < 20 or left.y < 90 or left.x + left.w > self.width - 20:
                raise AssertionError(f"类框越界: {left.label}")
            if left.y + left.h > self.height - 45:
                raise AssertionError(f"类框越界: {left.label}")
            for right in self.boxes[index + 1:]:
                gap_x = max(right.x - (left.x + left.w), left.x - (right.x + right.w))
                gap_y = max(right.y - (left.y + left.h), left.y - (right.y + right.h))
                if gap_x < 24 and gap_y < 24:
                    raise AssertionError(f"类框重叠或留白不足: {left.label} / {right.label}")

    def assert_edges_clear_of_boxes(self) -> None:
        by_id = {box.box_id: box for box in self.boxes}
        endings = {"association": "open-arrow", "dependency": "open-arrow",
                   "implementation": "hollow-triangle"}
        for edge in self.edges:
            ignored = {edge.get("source"), edge.get("target")}
            for box in self.boxes:
                if box.box_id in ignored:
                    continue
                for a, b in zip(edge["points"], edge["points"][1:]):
                    if _segment_hits_rect(a, b, box.rect, padding=7):
                        raise AssertionError(
                            f"连线穿过非端点类框: {box.label}; edge={edge}"
                        )
            if edge["kind"] not in endings:
                raise AssertionError(f"未知关系类型: {edge['kind']}")

    def _draw_edge(self, edge: dict) -> str:
        points = edge["points"]
        d = "M" + " L".join(f"{x:.1f},{y:.1f}" for x, y in points)
        marker = {"association": "open-arrow", "dependency": "open-arrow",
                  "implementation": "hollow-triangle"}[edge["kind"]]
        dash = ' stroke-dasharray="8,5"' if edge["kind"] in {"dependency", "implementation"} else ""
        return (
            f'<path d="{d}" fill="none" stroke="{LINE}" stroke-width="1.7"{dash} '
            f'marker-end="url(#{marker})"/>'
        )

    def save(self, path: Path) -> None:
        self.assert_no_overlap()
        self.assert_edges_clear_of_boxes()
        for box in self.boxes:
            for member in box.members:
                if member == "\x00":
                    continue
                if text_width(member, 12.8) > box.w - 28:
                    raise AssertionError(f"类成员文本溢出: {box.label}: {member}")
            for index, part in enumerate(box.header_parts):
                size = 12.5 if len(box.header_parts) > 1 and index == 0 else 16.5
                if text_width(part, size) > box.w - 24:
                    raise AssertionError(f"类名文本溢出: {box.label}: {part}")

        edges = "\n".join(self._draw_edge(edge) for edge in self.edges)
        boxes = "\n".join(self._draw_box(box) for box in self.boxes)
        subtitle = ""
        if self.subtitle:
            subtitle = (
                f'<text x="42" y="74" font-size="13.5" fill="{MUTED}">'
                f'{_esc(self.subtitle)}</text>'
            )
        footer = ""
        if self.footer:
            footer = (
                f'<line x1="42" y1="{self.height - 34}" x2="{self.width - 42}" '
                f'y2="{self.height - 34}" stroke="{GRID}"/>'
                f'<text x="42" y="{self.height - 13}" font-size="12" fill="{MUTED}">'
                f'{_esc(self.footer)}</text>'
            )
        svg = f'''<svg xmlns="http://www.w3.org/2000/svg" width="{self.width:.0f}" height="{self.height:.0f}" viewBox="0 0 {self.width:.0f} {self.height:.0f}" font-family="{FONT}">
<defs>
  <marker id="open-arrow" markerWidth="10" markerHeight="10" refX="9" refY="4" orient="auto" markerUnits="strokeWidth">
    <path d="M0,0 L9,4 L0,8" fill="none" stroke="{LINE}" stroke-width="1.6"/>
  </marker>
  <marker id="filled-arrow" markerWidth="10" markerHeight="10" refX="9" refY="4" orient="auto" markerUnits="strokeWidth">
    <path d="M0,0 L9,4 L0,8 Z" fill="{INK}"/>
  </marker>
  <marker id="hollow-triangle" markerWidth="13" markerHeight="12" refX="11" refY="5" orient="auto" markerUnits="strokeWidth">
    <path d="M0,0 L12,5 L0,10 Z" fill="#fff" stroke="{LINE}" stroke-width="1.4"/>
  </marker>
</defs>
<rect width="{self.width:.0f}" height="{self.height:.0f}" fill="#fff"/>
<text x="{self.width / 2:.0f}" y="38" text-anchor="middle" font-size="25" font-weight="700" fill="{TEXT}">{_esc(self.title)}</text>
{subtitle}
{edges}
{boxes}
{"".join(self.labels)}
{footer}
</svg>
'''
        path.write_text(svg, encoding="utf-8")


@dataclass
class Lifeline:
    key: str
    object_name: str
    class_name: str
    x: float
    meta: bool = False
    box_width: float = 0
    box_y: float = 96
    box_h: float = 48

    @property
    def label(self) -> str:
        return self.class_name

    @property
    def connector_y(self) -> float:
        return self.box_y + self.box_h + 6


class SequenceDiagram:
    """顺序图生成器；执行条和消息端点使用统一几何计算。"""

    def __init__(self, width: float, height: float, title: str, footer: str = ""):
        self.width = width
        self.height = height
        self.title = title
        self.footer = footer
        self.lifelines: list[Lifeline] = []
        self._line_by_key: dict[str, Lifeline] = {}
        self.activations: list[Activation] = []
        self.messages: list[str] = []
        self.fragments: list[dict] = []
        self.branch_guards: list[tuple[float, float, float, str]] = []
        self.notes: list[str] = []
        self.next_number = 1

    def lifeline(self, key: str, object_name: str, class_name: str, x: float,
                 meta: bool = False) -> Lifeline:
        if meta:
            display = f"«MetaClass»\n: {class_name}"
            width = max(118, text_width(f"«MetaClass» : {class_name}", 13) + 34)
        else:
            display = f"{object_name}: {class_name}"
            width = max(130, text_width(display, 13) + 34)
        line = Lifeline(key, object_name, class_name, x, meta, width)
        self.lifelines.append(line)
        self._line_by_key[key] = line
        return line

    def line(self, key: str) -> Lifeline:
        return self._line_by_key[key]

    def activation(self, key: str, y1: float, y2: float,
                   width: float = 12) -> Activation:
        if y2 - y1 < 12:
            raise AssertionError(f"执行条过短: {key} {y1}-{y2}")
        line = self.line(key)
        if y1 < line.connector_y:
            raise AssertionError(f"执行条早于 lifeline: {key}")
        act = Activation(key, line.x, y1, y2, width)
        self.activations.append(act)
        return act

    def _side(self, act: Activation | None, line: Lifeline,
              toward_x: float) -> float:
        if act:
            return act.x1 if toward_x > act.x else act.x0
        return line.x

    def sync(self, source_key: str, target_key: str, y: float,
             target_end: float, label: str,
             source_act: Activation | None = None,
             target_act: Activation | None = None,
             number: bool = True) -> Activation:
        target_line = self.line(target_key)
        source_line = self.line(source_key)
        act = target_act or self.activation(target_key, y, max(y + 24, target_end))
        if abs(act.y1 - y) > 0.1:
            raise AssertionError(f"同步消息未落在执行条顶边: {target_key}")
        if act.y2 < target_end - 0.1:
            raise AssertionError(f"同步调用结束时目标执行条已关闭: {target_key}")
        left_to_right = target_line.x > source_line.x
        sx = self._side(source_act, source_line, target_line.x)
        ex = act.x0 + 4 if left_to_right else act.x1 - 4
        prefix = f"{self.next_number:02d} " if number else ""
        if number:
            self.next_number += 1
        self._message(sx, y, ex, y, f"{prefix}{label}", "sync")
        return act

    def reply(self, source_key: str, target_key: str, y: float, label: str,
              source_act: Activation | None = None,
              target_act: Activation | None = None,
              exception: bool = False) -> None:
        source_line = self.line(source_key)
        target_line = self.line(target_key)
        if source_act and not (source_act.y1 - 0.1 <= y <= source_act.y2 + 0.1):
            raise AssertionError(f"回复箭头未从执行条发出: {source_key}@{y}")
        if target_act and not (target_act.y1 - 0.1 <= y <= target_act.y2 + 0.1):
            raise AssertionError(f"回复箭头未落到执行条: {target_key}@{y}")
        sx = self._side(source_act, source_line, target_line.x)
        tx = self._side(target_act, target_line, source_line.x)
        suffix = " «exception»" if exception else ""
        self._message(sx, y, tx, y, f"{label}{suffix}", "reply")

    def _message(self, x1: float, y1: float, x2: float, y2: float,
                 label: str, kind: str) -> None:
        dash = ' stroke-dasharray="8,5"' if kind == "reply" else ""
        marker = "open-arrow" if kind == "reply" else "filled-arrow"
        color = "#9a3f3f" if "«exception»" in label else INK
        self.messages.append(
            f'<line x1="{x1:.1f}" y1="{y1:.1f}" x2="{x2:.1f}" y2="{y2:.1f}" '
            f'stroke="{color}" stroke-width="1.6"{dash} marker-end="url(#{marker})"/>'
        )
        cx = (x1 + x2) / 2
        box_w = text_width(label, 12.5) + 10
        self.messages.append(
            f'<rect x="{cx - box_w / 2:.1f}" y="{y1 - 22:.1f}" width="{box_w:.1f}" '
            f'height="19" rx="3" fill="#fff" opacity="0.97"/>'
            f'<text x="{cx:.1f}" y="{y1 - 8:.1f}" text-anchor="middle" '
            f'font-size="12.5" fill="{color}">{_esc(label)}</text>'
        )

    def note(self, x: float, y: float, width: float, lines: list[str],
             fill: str = "#fffbe8", stroke: str = "#b18b2f") -> None:
        height = 16 + len(lines) * 18
        path = (f"M{x},{y} H{x + width - 10} L{x + width},{y + 10} "
                f"V{y + height} H{x} Z")
        parts = [f'<path d="{path}" fill="{fill}" stroke="{stroke}" stroke-width="1.2"/>']
        for i, line in enumerate(lines):
            parts.append(
                f'<text x="{x + 10}" y="{y + 18 + i * 18}" font-size="12.2" '
                f'fill="#5d4a18">{_esc(line)}</text>'
            )
        self.notes.append("\n".join(parts))

    def fragment(self, kind: str, label: str, x1: float, x2: float,
                 y1: float, y2: float,
                 contained: list[Activation] | None = None,
                 allow_spanning: list[Activation] | None = None) -> None:
        if y2 <= y1 or x2 <= x1:
            raise AssertionError(f"无效控制片段: {kind}")
        self.fragments.append({
            "kind": kind,
            "label": label,
            "x1": x1,
            "x2": x2,
            "y1": y1,
            "y2": y2,
            "contained": contained or [],
            "allow_spanning": allow_spanning or [],
        })

    def guard(self, x1: float, x2: float, y: float, label: str) -> None:
        """在 alt 片段内绘制分支分隔线与守卫条件。"""
        if x2 <= x1:
            raise AssertionError("无效分支守卫")
        self.branch_guards.append((x1, x2, y, label))

    def _draw_fragments(self) -> str:
        parts = []
        for frag in self.fragments:
            x1, x2 = frag["x1"], frag["x2"]
            y1, y2 = frag["y1"], frag["y2"]
            parts.append(
                f'<rect x="{x1}" y="{y1}" width="{x2 - x1}" height="{y2 - y1}" '
                f'fill="#f7fbff" fill-opacity="0.72" stroke="#6b7f98" '
                f'stroke-width="1.2"/>'
            )
            tab_w = max(76, text_width(frag["label"], 12.5) + 24)
            parts.append(
                f'<path d="M{x1},{y1} h{tab_w - 12} l12,12 v18 h-{tab_w} Z" '
                f'fill="#edf4fb" stroke="#6b7f98" stroke-width="1.2"/>'
                f'<text x="{x1 + 9}" y="{y1 + 20}" font-size="12.5" '
                f'font-weight="700" fill="#40546d">{_esc(frag["label"])}</text>'
            )
        for x1, x2, y, guard in self.branch_guards:
            tag_w = text_width(guard, 12) + 18
            parts.append(
                f'<line x1="{x1}" y1="{y}" x2="{x2}" y2="{y}" '
                f'stroke="#8a9aac" stroke-width="1.05" stroke-dasharray="5,4"/>'
                f'<rect x="{x1 + 12}" y="{y - 16}" width="{tag_w}" height="20" '
                f'rx="3" fill="#fff"/>'
                f'<text x="{x1 + 21}" y="{y - 2}" font-size="12" '
                f'fill="#4f647a">{_esc(guard)}</text>'
            )
        return "\n".join(parts)

    def assert_valid(self) -> None:
        for line in self.lifelines:
            label = f"«MetaClass» : {line.class_name}" if line.meta else f"{line.object_name}: {line.class_name}"
            if text_width(label, 13) > line.box_width - 18:
                raise AssertionError(f"lifeline 文本溢出: {label}")
            if line.x - line.box_width / 2 < 20 or line.x + line.box_width / 2 > self.width - 20:
                raise AssertionError(f"lifeline 越界: {label}")
        ordered = sorted(self.lifelines, key=lambda item: item.x)
        for left, right in zip(ordered, ordered[1:]):
            if left.x + left.box_width / 2 + 14 > right.x - right.box_width / 2:
                raise AssertionError(
                    f"lifeline 框重叠: {left.class_name} / {right.class_name}"
                )
        for act in self.activations:
            if act.y2 > self.height - 74:
                raise AssertionError(f"执行条越界: {act.lifeline}")
        for frag in self.fragments:
            for act in frag["contained"]:
                if act.x0 < frag["x1"] + 12 or act.x1 > frag["x2"] - 12:
                    raise AssertionError(f"{frag['kind']} 未完整包含横向执行条")
                if act.y1 < frag["y1"] + 26 or act.y2 > frag["y2"] - 12:
                    raise AssertionError(f"{frag['kind']} 未完整包含纵向执行条")
            for act in self.activations:
                if act in frag["contained"] or act in frag["allow_spanning"]:
                    continue
                intersects_x = act.x1 > frag["x1"] and act.x0 < frag["x2"]
                crosses_top = act.y1 < frag["y1"] < act.y2
                crosses_bottom = act.y1 < frag["y2"] < act.y2
                inside = act.y1 >= frag["y1"] and act.y2 <= frag["y2"]
                if inside:
                    continue
                if intersects_x and (crosses_top or crosses_bottom):
                    raise AssertionError(
                        f"控制片段穿过未声明执行条: {frag['kind']} / {act.lifeline}"
                    )

    def save(self, path: Path) -> None:
        self.assert_valid()
        lifelines = []
        for line in self.lifelines:
            fill = "#f5f1fb" if line.meta else "#f8fafc"
            stroke = "#6b5b95" if line.meta else "#4f647a"
            lifelines.append(
                f'<line x1="{line.x}" y1="{line.connector_y}" x2="{line.x}" '
                f'y2="{self.height - 64}" stroke="#7b8a9d" stroke-width="1.15" '
                f'stroke-dasharray="7,6"/>'
                f'<rect x="{line.x - line.box_width / 2}" y="{line.box_y}" '
                f'width="{line.box_width}" height="{line.box_h}" rx="5" '
                f'fill="{fill}" stroke="{stroke}" stroke-width="1.6"/>'
            )
            if line.meta:
                lifelines.append(
                    f'<text x="{line.x}" y="{line.box_y + 20}" text-anchor="middle" '
                    f'font-size="11.6" font-style="italic" fill="{MUTED}">«MetaClass»</text>'
                    f'<text x="{line.x}" y="{line.box_y + 39}" text-anchor="middle" '
                    f'font-size="13.2" font-weight="700" fill="{TEXT}">: {_esc(line.class_name)}</text>'
                )
            else:
                text = f"{line.object_name}: {line.class_name}"
                lifelines.append(
                    f'<text x="{line.x}" y="{line.box_y + 29}" text-anchor="middle" '
                    f'font-size="13.2" fill="{TEXT}">{_esc(text)}</text>'
                )

        activations = []
        for act in self.activations:
            activations.append(
                f'<rect x="{act.x0:.1f}" y="{act.y1:.1f}" width="{act.width}" '
                f'height="{act.y2 - act.y1:.1f}" fill="#e7eef7" stroke="#4f647a" '
                f'stroke-width="1.2"/>'
            )
        footer = ""
        if self.footer:
            footer = (
                f'<line x1="36" y1="{self.height - 42}" x2="{self.width - 36}" '
                f'y2="{self.height - 42}" stroke="{GRID}"/>'
                f'<text x="36" y="{self.height - 19}" font-size="12" fill="{MUTED}">'
                f'{_esc(self.footer)}</text>'
            )
        svg = f'''<svg xmlns="http://www.w3.org/2000/svg" width="{self.width:.0f}" height="{self.height:.0f}" viewBox="0 0 {self.width:.0f} {self.height:.0f}" font-family="{FONT}">
<defs>
  <marker id="filled-arrow" markerWidth="10" markerHeight="10" refX="9" refY="4" orient="auto" markerUnits="strokeWidth">
    <path d="M0,0 L9,4 L0,8 Z" fill="{INK}"/>
  </marker>
  <marker id="open-arrow" markerWidth="10" markerHeight="10" refX="9" refY="4" orient="auto" markerUnits="strokeWidth">
    <path d="M0,0 L9,4 L0,8" fill="none" stroke="{INK}" stroke-width="1.6"/>
  </marker>
</defs>
<rect width="{self.width:.0f}" height="{self.height:.0f}" fill="#fff"/>
<text x="{self.width / 2:.0f}" y="38" text-anchor="middle" font-size="25" font-weight="700" fill="{TEXT}">{_esc(self.title)}</text>
<text x="36" y="72" font-size="13" fill="{MUTED}">同步调用使用实心箭头并编号；回复与异常使用虚线空心箭头，不编号。</text>
{self._draw_fragments()}
{''.join(lifelines)}
{''.join(activations)}
{''.join(self.messages)}
{''.join(self.notes)}
{footer}
</svg>
'''
        path.write_text(svg, encoding="utf-8")


def render_png(svg_path: Path, png_path: Path, zoom: int = 2) -> None:
    import subprocess
    subprocess.run(
        ["rsvg-convert", f"--zoom={zoom}", "--background-color=white",
         str(svg_path), "-o", str(png_path)],
        check=True,
    )
