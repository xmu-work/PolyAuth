"""Auto-layout helpers for Service-centric class diagrams and sequence flows.

``ClassScene`` keeps the skill's rules (service in the geometric centre, callers
on top, collaborators at the sides, repositories at the bottom, orthogonal
edges that never cross a box) without hand-tuning every coordinate.

``SeqFlow`` keeps every message and execution specification on a monotonic
time cursor, so nested calls, replies and ``alt`` / ``opt`` / ``loop``
fragments are generated consistently.  The heavier geometry invariants are
still checked by ``ClassDiagram`` and ``DrawioSequence`` when the page is
rendered.
"""
from __future__ import annotations

from contextlib import contextmanager
from dataclasses import dataclass, field

from build_drawio_uml import (
    ClassDiagram,
    DrawioActivation,
    DrawioSequence,
    text_width,
)


# ---------------------------------------------------------------------------
# class diagrams
# ---------------------------------------------------------------------------

def _box_width(name: str, attributes: list[str], operations: list[str],
               minimum: float = 330, maximum: float = 790) -> float:
    lines = [name, *attributes, *operations]
    widest = max(text_width(item, 13) for item in lines) + 52
    title = text_width(name, 19) + 56
    return min(maximum, max(minimum, widest, title))


def _box_height(stereotype: str | None,
                attributes: list[str], operations: list[str]) -> float:
    header = 64.0 if stereotype else 50.0
    attr_h = max(34.0, 18 + len(attributes) * 21) if attributes else 24.0
    op_h = max(34.0, 18 + len(operations) * 21) if operations else 24.0
    return header + attr_h + op_h


@dataclass
class SceneNode:
    key: str
    name: str
    attributes: list[str]
    operations: list[str]
    side: str
    stereotype: str | None = None
    role: str = "class"
    arrow: str = "node"          # "node" -> arrow at this box, "service" -> arrow at service
    role_name: str | None = None
    kind: str = "association"    # association | dependency | implementation
    order: int = 0
    width: float = 0
    height: float = 0
    x: float = 0
    y: float = 0

    @property
    def bottom(self) -> float:
        return self.y + self.height

    @property
    def right(self) -> float:
        return self.x + self.width

    @property
    def cx(self) -> float:
        return self.x + self.width / 2

    @property
    def cy(self) -> float:
        return self.y + self.height / 2


class ClassScene:
    """Service-centric class diagram with orthogonal, box-avoiding edges."""

    TOP_GAP = 82.0
    ROW_GAP = 86.0
    COL_GAP = 58.0

    def __init__(self, title: str, subtitle: str, footer: str = "",
                 min_width: float = 1960):
        self.title = title
        self.subtitle = subtitle
        self.footer = footer
        self.min_width = min_width
        self.nodes: list[SceneNode] = []
        self.extra_edges: list[dict] = []
        self.service_node: SceneNode | None = None

    def service(self, key: str, name: str, attributes: list[str],
                operations: list[str], stereotype: str = "«service»",
                role: str = "service") -> SceneNode:
        node = SceneNode(key, name, attributes, operations, "center",
                         stereotype, role)
        self.nodes.append(node)
        self.service_node = node
        return node

    def node(self, key: str, name: str, attributes: list[str] | None,
             operations: list[str] | None, side: str,
             stereotype: str | None = None, role: str = "interface",
             arrow: str = "node", role_name: str | None = None,
             kind: str = "association", order: int = 0) -> SceneNode:
        node = SceneNode(
            key, name, attributes or [], operations or [], side,
            stereotype, role, arrow, role_name, kind, order,
        )
        self.nodes.append(node)
        return node

    def extra(self, kind: str, points: list[tuple[float, float]],
              source: str, target: str, role_name: str | None = None,
              label_at: tuple[float, float] | None = None) -> None:
        self.extra_edges.append({
            "kind": kind, "points": points, "source": source,
            "target": target, "role": role_name, "label_at": label_at,
        })

    # -- layout ------------------------------------------------------------
    def _measure(self) -> None:
        for node in self.nodes:
            node.width = _box_width(node.name, node.attributes, node.operations)
            node.height = _box_height(node.stereotype, node.attributes, node.operations)

    def _widths(self) -> float:
        top = [n for n in self.nodes if n.side == "top"]
        bottom = [n for n in self.nodes if n.side == "bottom"]
        left = [n for n in self.nodes if n.side == "left"]
        right = [n for n in self.nodes if n.side == "right"]
        service = self.service_node

        def row_width(items: list[SceneNode]) -> float:
            return sum(item.width for item in items) + self.TOP_GAP * max(0, len(items) - 1)

        top_w = row_width(top)
        bottom_w = row_width(bottom)
        left_w = max((n.width for n in left), default=0)
        right_w = max((n.width for n in right), default=0)
        return max(
            self.min_width,
            top_w + 200,
            bottom_w + 200,
            service.width + 2 * max(left_w, right_w) + 260,
        )

    def _place(self, width: float) -> float:
        top = sorted([n for n in self.nodes if n.side == "top"], key=lambda n: n.order)
        bottom = sorted([n for n in self.nodes if n.side == "bottom"], key=lambda n: n.order)
        left = sorted([n for n in self.nodes if n.side == "left"], key=lambda n: n.order)
        right = sorted([n for n in self.nodes if n.side == "right"], key=lambda n: n.order)
        service = self.service_node

        top_y = 118.0
        top_height = max((n.height for n in top), default=0.0)
        if top:
            total = sum(n.width for n in top) + self.TOP_GAP * (len(top) - 1)
            cursor = (width - total) / 2
            for node in top:
                node.x = cursor
                node.y = top_y
                cursor += node.width + self.TOP_GAP

        service.y = top_y + top_height + (168 if top else 260)
        service.x = (width - service.width) / 2

        def stack(items: list[SceneNode], x: float) -> None:
            if not items:
                return
            total = sum(item.height for item in items) + self.COL_GAP * (len(items) - 1)
            cursor = service.cy - total / 2
            cursor = max(132.0, cursor)
            for item in items:
                item.x = x
                item.y = cursor
                cursor += item.height + self.COL_GAP

        stack(left, 60.0)
        stack(right, width - 60.0 - max((n.width for n in right), default=0))

        for node in bottom:
            node.y = 0  # set after the row is known

        lowest = max(
            [service.bottom]
            + [n.bottom for n in left]
            + [n.bottom for n in right]
        )
        bottom_y = lowest + 250
        if bottom:
            total = sum(n.width for n in bottom) + self.ROW_GAP * (len(bottom) - 1)
            cursor = (width - total) / 2
            for node in bottom:
                node.x = cursor
                node.y = bottom_y
                cursor += node.width + self.ROW_GAP
            lowest = max(lowest, bottom_y)

        height = bottom_y + max((n.height for n in bottom), default=0) + 104
        return max(height, service.bottom + 160)

    def _points_to_service(self, node: SceneNode, width: float) -> tuple[
            list[tuple[float, float]], tuple[float, float]]:
        service = self.service_node
        assert service is not None
        if node.side == "top":
            cx = node.cx
            sx = min(max(cx, service.x + 64), service.x + service.width - 64)
            gutter = service.y - 72
            if abs(cx - sx) < 1:
                points = [(cx, node.bottom), (sx, service.y)]
                label_at = (cx + 14, (node.bottom + service.y) / 2)
            else:
                points = [(cx, node.bottom), (cx, gutter), (sx, gutter), (sx, service.y)]
                label_at = ((cx + sx) / 2, gutter - 13)
        elif node.side == "left":
            lo = service.y + 54
            hi = service.bottom - 54
            ny = node.cy
            if lo <= ny <= hi:
                points = [(service.x, ny), (node.right, ny)]
                label_at = ((service.x + node.right) / 2, ny - 13)
            else:
                anchor = min(max(ny, lo), hi)
                lane = service.x - 48
                points = [(service.x, anchor), (lane, anchor), (lane, ny),
                          (node.right, ny)]
                label_at = (lane + (node.right - lane) * 0.36, ny - 13)
        elif node.side == "right":
            lo = service.y + 54
            hi = service.bottom - 54
            ny = node.cy
            if lo <= ny <= hi:
                points = [(service.right, ny), (node.x, ny)]
                label_at = ((service.right + node.x) / 2, ny - 13)
            else:
                anchor = min(max(ny, lo), hi)
                lane = service.right + 48
                points = [(service.right, anchor), (lane, anchor), (lane, ny),
                          (node.x, ny)]
                label_at = (lane + (node.x - lane) * 0.36, ny - 13)
        else:  # bottom
            bottom_nodes = sorted(
                [n for n in self.nodes if n.side == "bottom"], key=lambda n: n.order
            )
            index = bottom_nodes.index(node)
            count = len(bottom_nodes)
            sx = service.x + service.width * (index + 0.5) / count
            sx = min(max(sx, service.x + 64), service.right - 64)
            gutter = min(n.y for n in bottom_nodes) - 62 - index * 26
            bx = node.cx
            if abs(sx - bx) < 1:
                points = [(sx, service.bottom), (bx, node.y)]
            else:
                points = [(sx, service.bottom), (sx, gutter), (bx, gutter), (bx, node.y)]
            role_text = node.role_name or ""
            role_w = max(64.0, text_width(role_text, 13) + 14) if role_text else 0
            if role_text and bx + role_w / 2 + 14 < width - 40:
                label_at = (bx + role_w / 2 + 14, node.y - 12)
            elif role_text:
                label_at = (bx - role_w / 2 - 14, node.y - 12)
            else:
                label_at = (bx, node.y - 12)
        return points, label_at

    def build(self) -> ClassDiagram:
        if self.service_node is None:
            raise AssertionError("ClassScene 缺少中心 Service")
        self._measure()
        width = self._widths()
        height = self._place(width)
        diagram = ClassDiagram(width, height, self.title, self.subtitle)
        for node in self.nodes:
            diagram.add_box(
                node.key, node.x, node.y, node.width, node.name,
                node.attributes, node.operations, node.stereotype, node.role,
            )
        for node in self.nodes:
            if node.side == "center":
                continue
            points, label_at = self._points_to_service(node, width)
            if node.arrow == "service":
                source, target = node.key, self.service_node.key
                points = list(reversed(points))
            else:
                source, target = self.service_node.key, node.key
            if node.kind == "association":
                diagram.association(points, node.role_name or "",
                                    label_at, source, target)
            else:
                diagram.dependency(points, source, target)
        for extra in self.extra_edges:
            if extra["kind"] == "association":
                diagram.association(
                    extra["points"], extra.get("role") or "",
                    extra.get("label_at"), extra["source"], extra["target"],
                )
            else:
                diagram.dependency(
                    extra["points"], extra["source"], extra["target"],
                )
        diagram.footer = self.footer
        return diagram


# ---------------------------------------------------------------------------
# sequence diagrams
# ---------------------------------------------------------------------------

class SeqFlow:
    """Monotonic sequence builder with automatic execution specifications."""

    def __init__(self, title: str, subtitle: str, footer: str = "",
                 start_y: float = 244, gap: float = 32, step: float = 210):
        self.s = DrawioSequence(100, 100, title, subtitle, footer)
        self.cursor = start_y
        self.start_y = start_y
        self.gap = gap
        self.step = step
        self.last_y = start_y
        self.stacks: dict[str, list[DrawioActivation]] = {}
        self.roots: dict[str, DrawioActivation] = {}
        self.frag_stack: list[dict] = []

    # -- lifelines ---------------------------------------------------------
    def lifelines(self, specs: list[tuple]) -> None:
        """specs entries: (key, object_name, class_name[, max_width[, meta]])."""
        x = 70.0
        for spec in specs:
            key, object_name, class_name = spec[0], spec[1], spec[2]
            max_width = spec[3] if len(spec) > 3 else 280
            meta = spec[4] if len(spec) > 4 else False
            label = (f"«MetaClass» : {class_name}" if meta
                     else f"{object_name}: {class_name}")
            width = min(max_width, max(180.0, text_width(label, 14) + 34))
            center = x + width / 2
            self.s.lifeline(key, object_name, class_name, center,
                            meta=meta, max_width=max_width)
            self.stacks.setdefault(key, [])
            x += width + self.step
        self.s.width = x - self.step + 70

    def root(self, *keys: str) -> None:
        for key in keys:
            act = self.s.activation(key, self.start_y, self.start_y + 4000,
                                    name=f"act-{key}")
            self.stacks[key] = [act]
            self.roots[key] = act

    # -- messages ----------------------------------------------------------
    def _top(self, key: str) -> DrawioActivation:
        stack = self.stacks.get(key) or []
        if not stack:
            raise AssertionError(f"lifeline {key} 没有处于激活状态的执行条")
        return stack[-1]

    def entry(self, source_key: str, target_key: str, label: str) -> None:
        source = self.roots[source_key]
        target = self.roots[target_key]
        y = self.cursor
        self.s.sync(source_key, target_key, y, label, source, target)
        self.last_y = y
        self.cursor = y + self.gap

    def begin(self, source_key: str, target_key: str, label: str,
              height: float = 44) -> DrawioActivation:
        source = self._top(source_key)
        y = self.cursor
        target = self.s.activation(target_key, y, y + 4000,
                                   name=f"act-{target_key}-{len(self.s.activations)}")
        target.caller_act = source
        target.caller_key = source_key
        self.stacks.setdefault(target_key, []).append(target)
        self.s.sync(source_key, target_key, y, label, source, target)
        self.last_y = y
        self.cursor = y + height
        return target

    def end(self, target_key: str, reply: str | None = None,
            exception: bool = False) -> None:
        act = self.stacks[target_key].pop()
        y = self.cursor + 14
        act.y2 = max(y, act.y1 + 44)
        caller = act.caller_act
        if reply is not None:
            self.s.reply(caller.lifeline.key, target_key, y, reply, act, caller,
                         exception=exception)
            self.last_y = y
            self.cursor = y + self.gap
        else:
            self.cursor = act.y2 + self.gap

    def call(self, source_key: str, target_key: str, label: str,
             reply: str | None = None, *, height: float = 44,
             exception: bool = False) -> None:
        self.begin(source_key, target_key, label, height=height)
        self.end(target_key, reply, exception=exception)

    def fire(self, source_key: str, target_key: str, label: str,
             height: float = 52) -> None:
        source = self._top(source_key)
        y = self.cursor
        target = self.s.activation(target_key, y, y + height,
                                   name=f"act-{target_key}-{len(self.s.activations)}")
        self.s.sync(source_key, target_key, y, label, source, target)
        self.last_y = y
        self.cursor = y + height + self.gap

    def reply_from(self, act: DrawioActivation, target_key: str, label: str,
                   exception: bool = False) -> None:
        """Reply from an already-open activation without closing it (alt branches)."""
        target = self._top(target_key)
        y = self.cursor
        self.s.reply(act.lifeline.key, target_key, y, label, act, target,
                     exception=exception)
        self.last_y = y
        self.cursor = y + self.gap

    def reply_to(self, source_key: str, target_key: str, label: str,
                 exception: bool = False) -> None:
        source = self._top(source_key)
        target = self._top(target_key)
        y = self.cursor
        self.s.reply(source_key, target_key, y, label, source, target,
                     exception=exception)
        self.last_y = y
        self.cursor = y + self.gap

    def number_from(self, value: int) -> None:
        self.s.next_number = value

    # -- fragments ---------------------------------------------------------
    @contextmanager
    def fragment(self, kind: str, label: str):
        start = len(self.s.activations)
        start_msg = len(self.s.messages)
        open_now = [act for stack in self.stacks.values() for act in stack]
        # keep the frame top clear of the previous execution specification
        finished = [act.y2 for act in self.s.activations if act.y2 < 3000]
        if finished:
            self.cursor = max(self.cursor, max(finished) + 90)
        context = {"branches": []}
        self.frag_stack.append(context)
        try:
            yield context
        finally:
            self.frag_stack.pop()
        acts = self.s.activations[start:]
        messages = self.s.messages[start_msg:]
        if not acts and not messages:
            raise AssertionError(f"控制片段 {kind} 没有内容: {label}")
        top_ys = [act.y1 for act in acts] + [msg.y for msg in messages]
        bottom_ys = [act.y2 for act in acts] + [msg.y for msg in messages]
        y1 = min(top_ys) - 48
        y2 = max(bottom_ys) + 28
        depth = len(self.frag_stack)
        x1 = min(line.x - line.width / 2 for line in self.s.lifelines) - 26 + depth * 16
        x2 = max(line.x + line.width / 2 for line in self.s.lifelines) + 26 - depth * 16
        self.s.fragment(kind, label, x1, x2, y1, y2,
                        contained=acts, spanning=open_now)
        for branch_y, branch_label in context["branches"]:
            self.s.guard(x1 + 12, x2 - 12, branch_y, branch_label)

    def branch(self, label: str) -> None:
        if not self.frag_stack:
            raise AssertionError("branch 必须位于控制片段内")
        self.frag_stack[-1]["branches"].append(
            (self.cursor - self.gap / 2, label)
        )

    def note(self, key: str, dy: float, width: float, lines: list[str]) -> None:
        line = self.s.lines[key]
        self.s.note(line.x + 26, self.cursor - self.gap + dy, width, lines)

    # -- finish ------------------------------------------------------------
    def finish(self) -> DrawioSequence:
        last = self.last_y
        root_ids = {id(act) for act in self.roots.values()}
        for act in self.s.activations:
            if id(act) in root_ids:
                continue
            if act.y2 > 3000:
                raise AssertionError(f"执行条未结束: {act.key}")
        for key, stack in self.stacks.items():
            for act in stack:
                if id(act) not in root_ids:
                    raise AssertionError(f"lifeline {key} 仍有未关闭的执行条")
        for act in self.roots.values():
            act.y2 = last + 26
        self.s.height = last + 168
        for line in self.s.lifelines:
            if line.x + line.width / 2 > self.s.width - 24:
                raise AssertionError(f"lifeline 越界: {line.class_name}")
        return self.s

    def to_canvas(self):
        return self.finish().to_canvas()
