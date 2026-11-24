"""Build the RBAC UML catalogue as a native, editable Draw.io document.

The generated file contains four class diagrams and four sequence diagrams.
Geometry checks are performed before the file is written. The Draw.io MCP
server is used afterwards to inspect the loaded pages and export PNG/SVG.
"""
from __future__ import annotations

import html
import math
from dataclasses import dataclass, field
from pathlib import Path
from xml.sax.saxutils import escape, quoteattr

ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "design"

TEXT = "#172033"
MUTED = "#5a6678"
INK = "#27364a"
LINE = "#415269"
GRID = "#d9e2ec"
BLUE = "#1f4e8c"

PALETTE = {
    "service": ("#e8f1fc", "#1f4e8c"),
    "caller": ("#f5f1fb", "#6b5b95"),
    "interface": ("#eef7f1", "#3d7a57"),
    "domain": ("#fffaf0", "#a17a2c"),
    "infra": ("#fdf2f2", "#9a4c4c"),
    "enum": ("#f1f5f9", "#52657a"),
}

CLASS_EDGE_STYLE = {
    "association": (
        "endArrow=open;endFill=0;endSize=10;html=1;rounded=0;"
        "edgeStyle=none;strokeWidth=1.7;strokeColor=#415269;"
    ),
    "dependency": (
        "endArrow=open;endFill=0;endSize=10;html=1;rounded=0;"
        "edgeStyle=none;dashed=1;dashPattern=7 5;strokeWidth=1.5;"
        "strokeColor=#415269;"
    ),
    "implementation": (
        "endArrow=block;endFill=0;endSize=12;html=1;rounded=0;"
        "edgeStyle=none;dashed=1;dashPattern=7 5;strokeWidth=1.5;"
        "strokeColor=#415269;"
    ),
}


def text_width(value: str, size: float) -> float:
    """Conservative width estimation for mixed Chinese and Latin text."""
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


@dataclass
class ClassBox:
    box_id: str
    x: float
    y: float
    w: float
    h: float
    label: str
    header_parts: list[str] = field(default_factory=list)
    members: list[str] = field(default_factory=list)
    role: str = "class"

    @property
    def rect(self) -> tuple[float, float, float, float]:
        return self.x, self.y, self.w, self.h


class ClassDiagram:
    """Class model used only by the current Draw.io exporter."""

    def __init__(
        self,
        width: float,
        height: float,
        title: str,
        subtitle: str = "",
    ):
        self.width = width
        self.height = height
        self.title = title
        self.subtitle = subtitle
        self.boxes: list[ClassBox] = []
        self.edges: list[dict] = []
        self.footer = ""

    def add_box(
        self,
        box_id: str,
        x: float,
        y: float,
        width: float,
        name: str,
        attributes: list[str] | None = None,
        operations: list[str] | None = None,
        stereotype: str | None = None,
        role: str = "class",
    ) -> ClassBox:
        attributes = attributes or []
        operations = operations or []
        header_parts = ([stereotype] if stereotype else []) + [name]
        header_h = 64 if stereotype else 50
        attr_h = max(34, 18 + len(attributes) * 21) if attributes else 24
        op_h = max(34, 18 + len(operations) * 21) if operations else 24
        box = ClassBox(
            box_id,
            x,
            y,
            width,
            header_h + attr_h + op_h,
            name,
            header_parts,
            attributes + ["\x00"] + operations,
            role,
        )
        self.boxes.append(box)
        return box

    def association(
        self,
        points: list[tuple[float, float]],
        role: str,
        label_at: tuple[float, float] | None = None,
        source: str | None = None,
        target: str | None = None,
    ) -> None:
        if label_at is None:
            (ax, ay), (bx, by) = points[-2], points[-1]
            label_at = ((ax + bx) / 2, (ay + by) / 2 - 9)
        self.edges.append(
            {
                "points": points,
                "kind": "association",
                "source": source,
                "target": target,
                "role": role,
                "label_at": label_at,
            }
        )

    def dependency(
        self,
        points: list[tuple[float, float]],
        source: str | None = None,
        target: str | None = None,
    ) -> None:
        self.edges.append(
            {
                "points": points,
                "kind": "dependency",
                "source": source,
                "target": target,
            }
        )

    def assert_no_overlap(self) -> None:
        for index, left in enumerate(self.boxes):
            if left.x < 20 or left.y < 90 or left.x + left.w > self.width - 20:
                raise AssertionError(f"类框越界: {left.label}")
            if left.y + left.h > self.height - 45:
                raise AssertionError(f"类框越界: {left.label}")
            for right in self.boxes[index + 1:]:
                gap_x = max(
                    right.x - (left.x + left.w),
                    left.x - (right.x + right.w),
                )
                gap_y = max(
                    right.y - (left.y + left.h),
                    left.y - (right.y + right.h),
                )
                if gap_x < 24 and gap_y < 24:
                    raise AssertionError(
                        f"类框重叠或留白不足: {left.label} / {right.label}"
                    )

    def assert_edges_clear_of_boxes(self) -> None:
        for edge in self.edges:
            ignored = {edge.get("source"), edge.get("target")}
            for box in self.boxes:
                if box.box_id in ignored:
                    continue
                for start, end in zip(edge["points"], edge["points"][1:]):
                    if _rect_hits_segment(box.rect, start, end):
                        raise AssertionError(
                            f"连线穿过非端点类框: {box.label}; edge={edge}"
                        )
            if edge["kind"] not in CLASS_EDGE_STYLE:
                raise AssertionError(f"未知关系类型: {edge['kind']}")


def _esc(value: object) -> str:
    return escape(str(value))


def _html(value: object) -> str:
    return html.escape(str(value), quote=False)


def _attr(value: object) -> str:
    return quoteattr(str(value))


def _rect_hits_segment(
    rect: tuple[float, float, float, float],
    start: tuple[float, float],
    end: tuple[float, float],
    padding: float = 7.0,
) -> bool:
    """Conservative rectangle/segment intersection test."""
    x, y, width, height = rect
    x0, y0 = x + padding, y + padding
    x1, y1 = x + width - padding, y + height - padding
    ax, ay = start
    bx, by = end

    def inside(px: float, py: float) -> bool:
        return x0 <= px <= x1 and y0 <= py <= y1

    if inside(ax, ay) or inside(bx, by):
        return True

    def cross(
        p: tuple[float, float],
        q: tuple[float, float],
        r: tuple[float, float],
    ) -> float:
        return (q[0] - p[0]) * (r[1] - p[1]) - (q[1] - p[1]) * (r[0] - p[0])

    def intersects(
        p: tuple[float, float],
        q: tuple[float, float],
        r: tuple[float, float],
        s: tuple[float, float],
    ) -> bool:
        c1 = cross(p, q, r)
        c2 = cross(p, q, s)
        c3 = cross(r, s, p)
        c4 = cross(r, s, q)
        return (
            ((c1 > 0 > c2) or (c1 < 0 < c2))
            and ((c3 > 0 > c4) or (c3 < 0 < c4))
        )

    edges = [
        ((x0, y0), (x1, y0)),
        ((x1, y0), (x1, y1)),
        ((x1, y1), (x0, y1)),
        ((x0, y1), (x0, y0)),
    ]
    return any(intersects((ax, ay), (bx, by), a, b) for a, b in edges)


class XmlCanvas:
    """Small XML canvas whose coordinates are already in Draw.io units."""

    def __init__(
        self,
        width: float,
        height: float,
        title: str,
        subtitle: str = "",
        footer: str = "",
    ):
        self.width = width
        self.height = height
        self.title = title
        self.subtitle = subtitle
        self.footer = footer
        self.cells: list[str] = []

    def vertex(
        self,
        cell_id: str,
        x: float,
        y: float,
        width: float,
        height: float,
        value: str = "",
        style: str = "",
        parent: str = "1",
    ) -> None:
        self.cells.append(
            f'<mxCell id={_attr(cell_id)} value={_attr(value)} '
            f'style={_attr(style)} vertex="1" parent={_attr(parent)}>'
            f'<mxGeometry x="{x:.1f}" y="{y:.1f}" width="{width:.1f}" '
            f'height="{height:.1f}" as="geometry"/></mxCell>'
        )

    def edge(
        self,
        cell_id: str,
        source: str | None,
        target: str | None,
        points: list[tuple[float, float]],
        style: str,
        value: str = "",
        parent: str = "1",
    ) -> None:
        endpoint_points = ""
        if points:
            first_x, first_y = points[0]
            last_x, last_y = points[-1]
            endpoint_points = (
                f'<mxPoint x="{first_x:.1f}" y="{first_y:.1f}" as="sourcePoint"/>'
                f'<mxPoint x="{last_x:.1f}" y="{last_y:.1f}" as="targetPoint"/>'
            )
        waypoints = "".join(
            f'<mxPoint x="{x:.1f}" y="{y:.1f}"/>' for x, y in points[1:-1]
        )
        source_attr = f" source={_attr(source)}" if source else ""
        target_attr = f" target={_attr(target)}" if target else ""
        self.cells.append(
            f'<mxCell id={_attr(cell_id)} value={_attr(value)} '
            f'style={_attr(style)} edge="1" parent={_attr(parent)}'
            f"{source_attr}{target_attr}>"
            f'<mxGeometry relative="1" as="geometry">'
            f"{endpoint_points}"
            f'<Array as="points">{waypoints}</Array>'
            "</mxGeometry></mxCell>"
        )

    def add_headings(self) -> None:
        self.vertex(
            "title",
            36,
            14,
            self.width - 72,
            42,
            _html(self.title),
            "text;html=1;align=center;verticalAlign=middle;fontSize=27;"
            "fontStyle=1;fontColor=#172033;fontFamily=Microsoft YaHei;",
        )
        if self.subtitle:
            self.vertex(
                "subtitle",
                42,
                61,
                self.width - 84,
                34,
                _html(self.subtitle),
                "text;html=1;align=left;verticalAlign=middle;fontSize=15;"
                "fontColor=#5a6678;fontFamily=Microsoft YaHei;whiteSpace=wrap;",
            )

    def add_footer(self) -> None:
        if not self.footer:
            return
        y = self.height - 70
        self.edge(
            "footer-line",
            None,
            None,
            [(42, y), (self.width - 42, y)],
            "endArrow=none;html=1;strokeColor=#d9e2ec;strokeWidth=1;",
        )
        self.vertex(
            "footer",
            42,
            y + 5,
            self.width - 84,
            38,
            _html(self.footer),
            "text;html=1;align=left;verticalAlign=middle;fontSize=13;"
            "fontColor=#5a6678;fontFamily=Microsoft YaHei;whiteSpace=wrap;",
        )

    def page_xml(self, page_id: str = "page") -> str:
        return (
            f'<diagram id={_attr(page_id)} name={_attr(self.title)}>'
            f'<mxGraphModel dx="{self.width:.0f}" dy="{self.height:.0f}" '
            'grid="1" gridSize="10" guides="1" tooltips="1" connect="1" '
            'arrows="1" fold="1" page="1" pageScale="1" '
            f'pageWidth="{self.width:.0f}" pageHeight="{self.height:.0f}" '
            'math="0" shadow="0" background="#ffffff"><root>'
            '<mxCell id="0"/><mxCell id="1" parent="0"/>'
            + "".join(self.cells)
            + "</root></mxGraphModel></diagram>"
        )


def _class_value(
    stereotype: str | None,
    name: str,
    attributes: list[str],
    operations: list[str],
    fill: str,
    stroke: str,
    box_width: float,
) -> str:
    title_size = 19.0
    if text_width(name, title_size) > box_width - 24:
        title_size = max(14.0, (box_width - 24) / max(1, len(name) * 0.72))
    header = ""
    if stereotype:
        header += (
            f'<div style="font-size:12px;color:#5a6678;text-align:center;'
            f'font-style:italic;line-height:16px;">{_html(stereotype)}</div>'
        )
    header += (
        f'<div style="font-size:{title_size:.1f}px;font-weight:700;'
        f'text-align:center;line-height:24px;">{_html(name)}</div>'
    )
    attr_lines = "<br/>".join(_html(item) for item in attributes)
    op_lines = "<br/>".join(_html(item) for item in operations)
    inner_width = max(160.0, box_width - 5)
    return (
        '<div style="font-family:Microsoft YaHei;color:#172033;'
        'line-height:18px;font-size:13px;box-sizing:border-box;'
        f'width:{inner_width:.1f}px;display:block;min-height:100%;">'
        f'<div style="background:{fill};border-bottom:1.5px solid {stroke};'
        f'padding:6px 8px;box-sizing:border-box;width:100%;">{header}</div>'
        f'<div style="border-bottom:1.2px solid {stroke};padding:6px 10px;'
        f'min-height:28px;box-sizing:border-box;width:100%;">{attr_lines}</div>'
        f'<div style="padding:6px 10px;min-height:28px;box-sizing:border-box;'
        f'width:100%;">{op_lines}</div>'
        "</div>"
    )


def class_page(diagram: ClassDiagram) -> XmlCanvas:
    diagram.assert_no_overlap()
    diagram.assert_edges_clear_of_boxes()
    canvas = XmlCanvas(
        diagram.width,
        diagram.height,
        diagram.title,
        diagram.subtitle,
        diagram.footer,
    )
    canvas.add_headings()

    for index, edge in enumerate(diagram.edges):
        style = CLASS_EDGE_STYLE[edge["kind"]]
        canvas.edge(
            f"edge-{index}",
            edge.get("source"),
            edge.get("target"),
            [(float(x), float(y)) for x, y in edge["points"]],
            style,
        )

    for box in diagram.boxes:
        fill, stroke = PALETTE.get(box.role, ("#ffffff", INK))
        split = box.members.index("\x00")
        attributes = box.members[:split]
        operations = box.members[split + 1 :]
        stereotype = box.header_parts[0] if len(box.header_parts) > 1 else None
        value = _class_value(
            stereotype,
            box.label,
            attributes,
            operations,
            fill,
            stroke,
            box.w,
        )
        style = (
            "rounded=1;absoluteArcSize=1;arcSize=7;whiteSpace=wrap;html=1;"
            "align=left;verticalAlign=top;spacing=0;fillColor=#ffffff;"
            f"strokeColor={stroke};strokeWidth=2;"
        )
        canvas.vertex(box.box_id, box.x, box.y, box.w, box.h, value, style)

    for index, edge in enumerate(diagram.edges):
        if edge["kind"] != "association" or not edge.get("role"):
            continue
        lx, ly = edge["label_at"]
        role = str(edge["role"])
        width = max(64.0, text_width(role, 13) + 14)
        canvas.vertex(
            f"role-{index}",
            lx - width / 2,
            ly - 18,
            width,
            22,
            _html(role),
            "text;html=1;align=center;verticalAlign=middle;fontSize=13;"
            "fontColor=#1f4e8c;fillColor=#ffffff;rounded=0;"
            "fontFamily=Microsoft YaHei;",
        )

    canvas.add_footer()
    return canvas


def structured_auth_class() -> ClassDiagram:
    d = ClassDiagram(
        1900,
        1160,
        "结构化后端 · 认证核心类图",
        "以 AuthService 为中心；对象字段形成带 role name 的关联，实体仅通过仓储签名参与。",
    )
    d.add_box(
        "controller", 120, 115, 540, "IdentityAuthController",
        ["- authService: AuthService", "- identityService: IdentityService"],
        ["+ login(LoginRequest, HttpServletRequest): LoginResponse",
         "+ logout(String): void", "+ currentUser(Long, String): IdentityCurrentUserVO"],
        "«controller»", "caller",
    )
    d.add_box(
        "interceptor", 1240, 115, 540, "AuthInterceptor",
        ["- authService: AuthService", "- apiService: ApiService",
         "- decisionService: DecisionService", "- logService: LogService"],
        ["+ preHandle(...): boolean", "+ afterCompletion(...): void"],
        "«component»", "caller",
    )
    d.add_box(
        "auth", 680, 470, 540, "AuthService",
        ["- userRepository: IamUserRepository", "- tokenRepository: IamTokenRepository",
         "- cryptoComponent: CryptoComponent", "- logService: LogService"],
        ["+ login(LoginRequest): LoginResponse", "+ logout(String): void",
         "+ authenticateSession(String): AuthenticatedSession",
         "+ revokeUserTokens(Long): void",
         "+ changePassword(Long, String, String): void"],
        "«service»", "service",
    )
    d.add_box(
        "crypto", 140, 520, 430, "CryptoComponent", [],
        ["+ verifyPassword(String, String): boolean", "+ hash(String): String",
         "+ hashPassword(String): String", "+ decrypt(String): String"],
        "«component»", "infra",
    )
    d.add_box(
        "log", 1330, 520, 430, "LogService", [],
        ["+ record(Long, String, String, ...): void",
         "+ recordPermissionChange(Long, ...): void"],
        "«service»", "infra",
    )
    d.add_box(
        "user_repo", 180, 875, 500, "IamUserRepository", [],
        ["+ findByUserNo(String): Optional<IamUser>",
         "+ findById(Long): Optional<IamUser>", "+ save(IamUser): IamUser",
         "+ findByOrgId(Long): List<IamUser>"],
        "«interface · Spring Data JPA»", "interface",
    )
    d.add_box(
        "token_repo", 1220, 875, 500, "IamTokenRepository", [],
        ["+ findByTokenHash(String): Optional<IamToken>",
         "+ findByUserIdAndStatus(Long, String): List<IamToken>",
         "+ save(IamToken): IamToken"],
        "«interface · Spring Data JPA»", "interface",
    )

    d.association(
        [(390, 306), (390, 392), (760, 392), (760, 470)],
        "authService", (575, 380), "controller", "auth",
    )
    d.association(
        [(1510, 327), (1510, 393), (1140, 393), (1140, 470)],
        "authService", (1325, 381), "interceptor", "auth",
    )
    d.association([(680, 590), (570, 590)], "cryptoComponent",
                  (625, 577), "auth", "crypto")
    d.association([(1220, 590), (1330, 590)], "logService",
                  (1275, 577), "auth", "log")
    d.association([(830, 745), (620, 830), (430, 875)],
                  "userRepository", (525, 842), "auth", "user_repo")
    d.association([(1070, 745), (1350, 830), (1470, 875)],
                  "tokenRepository", (1255, 842), "auth", "token_repo")

    d.footer = (
        "关联：实线空心箭头；role name 为实际字段名。"
        " LoginRequest、LoginResponse、IamUser、IamToken 作为方法参数/返回值参与签名。"
    )
    return d


def structured_decision_class() -> ClassDiagram:
    d = ClassDiagram(
        2280,
        1510,
        "结构化后端 · 权限决策类图",
        "以 DecisionService（PDP）为中心，调用方在四周，四个 Repository 在下方形成单条判定链。",
    )
    d.add_box(
        "interceptor", 90, 110, 500, "AuthInterceptor",
        ["- authService: AuthService", "- apiService: ApiService",
         "- decisionService: DecisionService", "- logService: LogService"],
        ["+ preHandle(...): boolean", "+ afterCompletion(...): void"],
        "«component»", "caller",
    )
    d.add_box(
        "identity", 720, 100, 620, "IdentityService",
        ["- userRepository: IamUserRepository", "- orgRepository: IamOrgRepository",
         "- apiRepository: IamApiRepository", "- systemRepository: IamSystemRepository",
         "- permissionRepository: IamPermissionRepository",
         "- decisionService: DecisionService", "- cryptoComponent: CryptoComponent"],
        ["+ getCurrentUser(Long, String): IdentityProfile",
         "+ hasPermissionCode(Long, String, String): boolean",
         "+ hasApiOperation(Long, String, String): boolean",
         "+ pageDepartments(...): IdentityPageSlice"],
        "«service»", "caller",
    )
    d.add_box(
        "open", 1480, 110, 500, "OpenApiController",
        ["- decisionService: DecisionService"],
        ["+ permissions(...): Result<Set<Long>>"],
        "«controller»", "caller",
    )
    d.add_box(
        "role_service", 1690, 620, 520, "RoleService",
        ["- roleRepository: IamRoleRepository", "- userRepository: IamUserRepository",
         "- userRoleRepository: IamUserRoleRepository",
         "- permissionRepository: IamPermissionRepository",
         "- rolePermissionRepository: IamRolePermissionRepository",
         "- roleExclusionRepository: IamRoleExclusionRepository",
         "- decisionService: DecisionService", "- logService: LogService",
         "- lockExecutor: BusinessLockExecutor"],
        ["+ grantRole(...): IamUserRole", "+ checkGrantBoundary(Long, Long)",
         "+ bindRolePermissions(...): void", "+ collectRolePermissionIds(Long)"],
        "«service»", "caller",
    )
    d.add_box(
        "decision", 760, 570, 700, "DecisionService",
        ["- userRepository: IamUserRepository",
         "- userRoleRepository: IamUserRoleRepository",
         "- roleRepository: IamRoleRepository",
         "- rolePermissionRepository: IamRolePermissionRepository"],
        ["+ hasPermission(Long, String, Long): boolean",
         "+ getUserPermissions(Long, String): Set<Long>",
         "+ getUserRoleCodes(Long): Set<String>", "- activeRoleIds(Long): Set<Long>",
         "- collectActiveRolesWithAncestors(Set<Long>): Set<Long>"],
        "«service»", "service",
    )
    d.add_box(
        "user_repo", 80, 1235, 440, "IamUserRepository", [],
        ["+ findById(Long): Optional<IamUser>",
         "+ findByUserNo(String): Optional<IamUser>",
         "+ save(IamUser): IamUser"],
        "«interface»", "interface",
    )
    d.add_box(
        "user_role_repo", 600, 1235, 460, "IamUserRoleRepository", [],
        ["+ findByUserIdAndStatus(Long, String): List<IamUserRole>",
         "+ findByUserIdAndRoleId(Long, Long): Optional<IamUserRole>",
         "+ save(IamUserRole): IamUserRole"],
        "«interface»", "interface",
    )
    d.add_box(
        "role_repo", 1140, 1235, 460, "IamRoleRepository", [],
        ["+ findAllById(Iterable<Long>): List<IamRole>",
         "+ findByRoleCode(String): Optional<IamRole>",
         "+ save(IamRole): IamRole"],
        "«interface»", "interface",
    )
    d.add_box(
        "role_perm_repo", 1740, 1235, 460, "IamRolePermissionRepository", [],
        ["+ findGrantedPermissions(Collection<Long>, String): List<...>",
         "+ findByRoleId(Long): List<IamRolePermission>",
         "+ save(IamRolePermission): IamRolePermission"],
        "«interface»", "interface",
    )

    d.association(
        [(330, 322), (330, 470), (820, 470), (820, 570)],
        "decisionService", (585, 458), "interceptor", "decision",
    )
    d.association([(1020, 417), (1020, 570)], "decisionService",
                  (1080, 500), "identity", "decision")
    d.association(
        [(1730, 301), (1730, 500), (1460, 500), (1460, 570)],
        "decisionService", (1600, 488), "open", "decision",
    )
    d.association([(1690, 750), (1460, 750)], "decisionService",
                  (1575, 737), "role_service", "decision")
    d.association(
        [(880, 845), (880, 1030), (300, 1030), (300, 1235)],
        "userRepository", (570, 1018), "decision", "user_repo",
    )
    d.association(
        [(980, 845), (980, 1110), (820, 1110), (820, 1235)],
        "userRoleRepository", (850, 1098), "decision", "user_role_repo",
    )
    d.association(
        [(1080, 845), (1080, 1150), (1360, 1150), (1360, 1235)],
        "roleRepository", (1250, 1138), "decision", "role_repo",
    )
    d.association(
        [(1260, 845), (1260, 1040), (1960, 1040), (1960, 1235)],
        "rolePermissionRepository", (1710, 1028), "decision", "role_perm_repo",
    )
    d.footer = (
        "判定链：用户状态 → 有效期内的用户角色 → 启用角色及父角色 → "
        "角色权限候选（系统/操作过滤）→ 权限 ID 集合。"
    )
    return d


def oop_auth_class() -> ClassDiagram:
    d = ClassDiagram(
        2100,
        1300,
        "面向对象后端 · 认证用例类图",
        "以 AuthenticationUseCase 为中心；应用层只依赖端口，不直接依赖 JPA 或 JWT 实现。",
    )
    d.add_box(
        "controller", 100, 120, 530, "AuthController",
        ["- authenticationUseCase: AuthenticationUseCase",
         "- operationAccessUseCase: OperationAccessUseCase"],
        ["+ login(LoginRequestDTO, HttpServletRequest): LoginResult",
         "+ logout(String): void", "+ apiPermission(...): ApiPermissionCheckVO"],
        "«controller»", "caller",
    )
    d.add_box(
        "interceptor", 1450, 115, 540, "AuthInterceptor",
        ["- authenticationUseCase: AuthenticationUseCase",
         "- operationAccessUseCase: OperationAccessUseCase",
         "- auditTrail: AuditTrail"],
        ["+ preHandle(...): boolean", "+ afterCompletion(...): void"],
        "«component»", "caller",
    )
    d.add_box(
        "auth", 690, 470, 720, "AuthenticationUseCase",
        ["- userRepository: UserAccountRepository",
         "- tokenRepository: TokenSessionRepository",
         "- passwordVerifier: PasswordVerifier", "- nameCipher: NameCipher",
         "- tokenSecretHasher: TokenSecretHasher", "- tokenIssuer: AccessTokenIssuer",
         "- auditTrail: AuditTrail", "- clock: Clock"],
        ["+ login(LoginCommand): LoginResult",
         "+ authenticate(String): AuthenticatedPrincipal",
         "+ logout(String): void", "+ validate(String): boolean", "+ userNo(Long): String"],
        "«service»", "service",
    )
    d.add_box(
        "password", 100, 500, 430, "PasswordVerifier", [],
        ["+ verify(String, String): boolean"], "«interface»", "interface",
    )
    d.add_box(
        "name", 100, 735, 430, "NameCipher", [],
        ["+ decrypt(String): String"], "«interface»", "interface",
    )
    d.add_box(
        "issuer", 1510, 490, 460, "AccessTokenIssuer", [],
        ["+ issue(String, List<String>): IssuedToken",
         "+ read(String): AccessTokenClaims"], "«interface»", "interface",
    )
    d.add_box(
        "hasher", 1510, 710, 460, "TokenSecretHasher", [],
        ["+ hash(String): String"], "«interface»", "interface",
    )
    d.add_box(
        "user_repo", 160, 1000, 430, "UserAccountRepository", [],
        ["+ findById(Long): Optional<UserAccount>",
         "+ findByUserNo(String): Optional<UserAccount>",
         "+ save(UserAccount): UserAccount"], "«interface»", "interface",
    )
    d.add_box(
        "token_repo", 650, 1000, 430, "TokenSessionRepository", [],
        ["+ findByTokenHash(String): Optional<TokenSession>",
         "+ save(TokenSession): void"], "«interface»", "interface",
    )
    d.add_box(
        "audit", 1160, 1000, 430, "AuditTrail", [],
        ["+ recordLogin(...): void", "+ recordRequest(...): void",
         "+ recordPermissionChange(...): void"], "«interface»", "interface",
    )
    d.add_box(
        "clock", 1650, 1000, 350, "Clock", [],
        ["+ instant(): Instant", "+ millis(): long"], "«JDK»", "enum",
    )

    d.association(
        [(350, 311), (350, 390), (760, 390), (760, 470)],
        "authenticationUseCase", (555, 378), "controller", "auth",
    )
    d.association(
        [(1720, 306), (1720, 390), (1340, 390), (1340, 470)],
        "authenticationUseCase", (1530, 378), "interceptor", "auth",
    )
    d.association([(690, 550), (530, 550)], "passwordVerifier",
                  (610, 537), "auth", "password")
    d.association([(690, 795), (530, 795)], "nameCipher",
                  (610, 782), "auth", "name")
    d.association([(1410, 550), (1510, 550)], "tokenIssuer",
                  (1460, 537), "auth", "issuer")
    d.association([(1410, 765), (1510, 765)], "tokenSecretHasher",
                  (1460, 752), "auth", "hasher")
    d.association(
        [(800, 829), (800, 915), (375, 915), (375, 1000)],
        "userRepository", (580, 903), "auth", "user_repo",
    )
    d.association(
        [(970, 829), (970, 935), (865, 935), (865, 1000)],
        "tokenRepository", (915, 923), "auth", "token_repo",
    )
    d.association(
        [(1120, 829), (1120, 920), (1375, 920), (1375, 1000)],
        "auditTrail", (1245, 908), "auth", "audit",
    )
    d.association(
        [(1300, 829), (1300, 945), (1825, 945), (1825, 1000)],
        "clock", (1560, 933), "auth", "clock",
    )
    d.footer = (
        "右侧与下方为应用端口。具体实现位于 infrastructure："
        "JwtAccessTokenIssuer、Jpa*RepositoryAdapter、Pbkdf2PasswordVerifier、AesNameCipher 等。"
    )
    return d


def oop_permission_class() -> ClassDiagram:
    d = ClassDiagram(
        2400,
        1510,
        "面向对象后端 · 权限查询类图",
        "以 PermissionAccessUseCase 为中心；仓储只返回授权事实，PermissionResolver 负责纯计算。",
    )
    d.add_box(
        "operation", 880, 100, 640, "OperationAccessUseCase",
        ["- operationCatalogRepository: OperationCatalogRepository",
         "- permissionAccessUseCase: PermissionAccessUseCase"],
        ["+ requireRequest(Long, String, String, String): void",
         "+ operationAllowed(Long, String, String): boolean"],
        "«service»", "caller",
    )
    d.add_box(
        "role_grant_repo", 100, 500, 450, "RoleGrantRepository", [],
        ["+ findByUserId(Long): List<RoleGrant>",
         "+ findByUserIdAndRoleId(Long, Long): Optional<RoleGrant>",
         "+ save(RoleGrant): RoleGrant"], "«interface»", "interface",
    )
    d.add_box(
        "role_repo", 100, 760, 450, "RoleRepository", [],
        ["+ findById(Long): Optional<Role>", "+ findAll(): List<Role>",
         "+ save(Role): Role"], "«interface»", "interface",
    )
    d.add_box(
        "catalog_repo", 100, 1040, 450, "PermissionCatalogRepository", [],
        ["+ findFact(Long): Optional<PermissionFact>",
         "+ findFacts(Collection<Long>): List<PermissionFact>"],
        "«interface»", "interface",
    )
    d.add_box(
        "user_repo", 1830, 500, 500, "UserAccountRepository", [],
        ["+ findById(Long): Optional<UserAccount>",
         "+ findByUserNo(String): Optional<UserAccount>",
         "+ save(UserAccount): UserAccount"], "«interface»", "interface",
    )
    d.add_box(
        "role_perm_repo", 1830, 760, 500, "RolePermissionRepository", [],
        ["+ findByRoleId(Long): List<RolePermissionGrant>",
         "+ findByRoleIds(Collection<Long>): List<RolePermissionGrant>",
         "+ bind(...): void"], "«interface»", "interface",
    )
    d.add_box(
        "clock", 1900, 1040, 400, "Clock", [],
        ["+ instant(): Instant"], "«JDK»", "enum",
    )
    d.add_box(
        "permission", 800, 480, 700, "PermissionAccessUseCase",
        ["- userRepository: UserAccountRepository",
         "- roleGrantRepository: RoleGrantRepository",
         "- roleRepository: RoleRepository",
         "- rolePermissionRepository: RolePermissionRepository",
         "- permissionCatalogRepository: PermissionCatalogRepository",
         "- clock: Clock"],
        ["+ permissions(Long, String): Set<Long>",
         "+ hasPermission(Long, String, Long): boolean"],
        "«service»", "service",
    )
    d.add_box(
        "resolver", 800, 1120, 520, "PermissionResolver", [],
        ["+ resolve(List<RoleGrant>, Map<Long, Role>, ...): Set<Long>"],
        "«utility service»", "domain",
    )
    d.add_box(
        "hierarchy", 1380, 1120, 480, "RoleHierarchy", [],
        ["+ activeRoleIds(Set<Long>, Function<...>): Set<Long>",
         "+ ensureNoCycle(Long, Long, Function<...>): void"],
        "«utility service»", "domain",
    )

    d.association([(1200, 324), (1200, 480)], "permissionAccessUseCase",
                  (1330, 410), "operation", "permission")
    d.association([(800, 570), (550, 570)], "roleGrantRepository",
                  (675, 557), "permission", "role_grant_repo")
    d.association(
        [(800, 650), (620, 650), (620, 760), (550, 760)],
        "roleRepository", (660, 712), "permission", "role_repo",
    )
    d.association([(1500, 580), (1830, 580)], "userRepository",
                  (1665, 567), "permission", "user_repo")
    d.association([(1500, 690), (1830, 690)], "rolePermissionRepository",
                  (1665, 677), "permission", "role_perm_repo")
    d.association(
        [(900, 734), (900, 930), (520, 930), (520, 1040)],
        "permissionCatalogRepository", (680, 918), "permission", "catalog_repo",
    )
    d.association(
        [(1350, 734), (1350, 960), (2040, 960), (2040, 1040)],
        "clock", (1695, 948), "permission", "clock",
    )
    d.dependency([(1100, 734), (1100, 1120)], "permission", "resolver")
    d.dependency([(1320, 1190), (1380, 1190)], "resolver", "hierarchy")
    d.footer = (
        "PermissionResolver 与 RoleHierarchy 仅接收已读取的事实并做静态纯计算；"
        "数据库、映射和时钟注入均通过端口隔离。"
    )
    return d


@dataclass
class DrawioLifeline:
    key: str
    object_name: str
    class_name: str
    x: float
    width: float
    meta: bool = False
    font_size: float = 14.0
    box_y: float = 108
    box_h: float = 62

    @property
    def cell_id(self) -> str:
        return f"ll-{self.key}"

    @property
    def connector_y(self) -> float:
        return self.box_y + self.box_h


@dataclass
class DrawioActivation:
    key: str
    lifeline: DrawioLifeline
    y1: float
    y2: float
    width: float = 14
    cell_id: str = ""

    @property
    def x0(self) -> float:
        return self.lifeline.x - self.width / 2

    @property
    def x1(self) -> float:
        return self.lifeline.x + self.width / 2


@dataclass
class DrawioMessage:
    cell_id: str
    source: DrawioActivation
    target: DrawioActivation
    y: float
    label: str
    kind: str


@dataclass
class DrawioFragment:
    kind: str
    label: str
    x1: float
    x2: float
    y1: float
    y2: float
    contained: list[DrawioActivation] = field(default_factory=list)
    spanning: list[DrawioActivation] = field(default_factory=list)


class DrawioSequence:
    """Sequence builder with explicit execution-specification geometry."""

    def __init__(
        self,
        width: float,
        height: float,
        title: str,
        subtitle: str,
        footer: str = "",
    ):
        self.width = width
        self.height = height
        self.title = title
        self.subtitle = subtitle
        self.footer = footer
        self.lifelines: list[DrawioLifeline] = []
        self.lines: dict[str, DrawioLifeline] = {}
        self.activations: list[DrawioActivation] = []
        self.messages: list[DrawioMessage] = []
        self.fragments: list[DrawioFragment] = []
        self.guards: list[tuple[float, float, float, str]] = []
        self.notes: list[tuple[float, float, float, list[str]]] = []
        self.next_number = 1

    def lifeline(
        self,
        key: str,
        object_name: str,
        class_name: str,
        x: float,
        *,
        meta: bool = False,
        max_width: float = 280,
    ) -> DrawioLifeline:
        label = (
            f"«MetaClass» : {class_name}"
            if meta
            else f"{object_name}: {class_name}"
        )
        width = min(max_width, max(180.0, text_width(label, 14) + 34))
        font_size = 14.0
        measured = text_width(label, font_size)
        if measured > width - 20:
            font_size = max(10.5, font_size * (width - 20) / measured)
        if text_width(label, font_size) > width - 18:
            raise AssertionError(f"lifeline 文本溢出: {label}")
        line = DrawioLifeline(
            key, object_name, class_name, x, width, meta, font_size
        )
        self.lifelines.append(line)
        self.lines[key] = line
        return line

    def activation(
        self,
        key: str,
        y1: float,
        y2: float,
        *,
        name: str | None = None,
    ) -> DrawioActivation:
        if y2 - y1 < 18:
            raise AssertionError(f"执行条过短: {key} {y1}-{y2}")
        line = self.lines[key]
        if y1 < line.connector_y:
            raise AssertionError(f"执行条早于 lifeline: {key}")
        act = DrawioActivation(
            f"{key}-{len(self.activations) + 1}",
            line,
            float(y1),
            float(y2),
            cell_id=name or f"act-{len(self.activations) + 1}",
        )
        self.activations.append(act)
        return act

    @staticmethod
    def _side(act: DrawioActivation, toward_x: float) -> float:
        return act.x1 if toward_x > act.lifeline.x else act.x0

    def _label(self, value: str, width: float) -> str:
        if text_width(value, 14) <= width - 18:
            return _html(value)
        split_at = value.find("(")
        if 0 < split_at < len(value) - 1:
            left = value[: split_at + 1]
            right = value[split_at + 1 :]
            return f"{_html(left)}<br/>{_html(right)}"
        return _html(value)

    def sync(
        self,
        source_key: str,
        target_key: str,
        y: float,
        label: str,
        source_act: DrawioActivation,
        target_act: DrawioActivation,
        *,
        number: bool = True,
        number_value: int | None = None,
    ) -> None:
        if not source_act.y1 <= y <= source_act.y2:
            raise AssertionError(f"同步消息未从执行条发出: {source_key}@{y}")
        if abs(target_act.y1 - y) > 0.1:
            raise AssertionError(f"同步消息未落在执行条顶边: {target_key}@{y}")
        if target_act.y2 < y:
            raise AssertionError(f"同步调用结束时目标执行条已关闭: {target_key}")
        prefix = ""
        if number:
            value = self.next_number if number_value is None else number_value
            prefix = f"{value:02d} "
            if number_value is None:
                self.next_number += 1
        gap = abs(
            target_act.lifeline.x - source_act.lifeline.x
        )
        self.messages.append(
            DrawioMessage(
                f"msg-{len(self.messages) + 1}",
                source_act,
                target_act,
                y,
                self._label(prefix + label, max(180.0, gap - 6)),
                "sync",
            )
        )

    def reply(
        self,
        source_key: str,
        target_key: str,
        y: float,
        label: str,
        source_act: DrawioActivation,
        target_act: DrawioActivation,
        *,
        exception: bool = False,
    ) -> None:
        if not source_act.y1 - 0.1 <= y <= source_act.y2 + 0.1:
            raise AssertionError(f"回复箭头未从执行条发出: {source_key}@{y}")
        if not target_act.y1 - 0.1 <= y <= target_act.y2 + 0.1:
            raise AssertionError(f"回复箭头未落到执行条: {target_key}@{y}")
        gap = abs(target_act.lifeline.x - source_act.lifeline.x)
        self.messages.append(
            DrawioMessage(
                f"msg-{len(self.messages) + 1}",
                source_act,
                target_act,
                y,
                self._label(label, max(180.0, gap - 6)),
                "exception" if exception else "reply",
            )
        )

    def fragment(
        self,
        kind: str,
        label: str,
        x1: float,
        x2: float,
        y1: float,
        y2: float,
        contained: list[DrawioActivation] | None = None,
        spanning: list[DrawioActivation] | None = None,
    ) -> None:
        self.fragments.append(
            DrawioFragment(
                kind,
                label,
                x1,
                x2,
                y1,
                y2,
                contained or [],
                spanning or [],
            )
        )

    def guard(self, x1: float, x2: float, y: float, label: str) -> None:
        self.guards.append((x1, x2, y, label))

    def note(self, x: float, y: float, width: float, lines: list[str]) -> None:
        self.notes.append((x, y, width, lines))

    def assert_valid(self) -> None:
        ordered = sorted(self.lifelines, key=lambda line: line.x)
        for left, right in zip(ordered, ordered[1:]):
            if left.x + left.width / 2 + 12 > right.x - right.width / 2:
                raise AssertionError(
                    f"lifeline 框重叠: {left.class_name} / {right.class_name}"
                )
        for line in ordered:
            if line.x - line.width / 2 < 24:
                raise AssertionError(f"lifeline 越界: {line.class_name}")
            if line.x + line.width / 2 > self.width - 24:
                raise AssertionError(f"lifeline 越界: {line.class_name}")
        for act in self.activations:
            if act.y2 > self.height - 72:
                raise AssertionError(f"执行条越界: {act.key}")
        for frag in self.fragments:
            for act in frag.contained:
                if act.x0 < frag.x1 + 12 or act.x1 > frag.x2 - 12:
                    raise AssertionError(
                        f"{frag.kind} 未完整包含横向执行条: {act.key}"
                    )
                if act.y1 < frag.y1 + 30 or act.y2 > frag.y2 - 12:
                    raise AssertionError(
                        f"{frag.kind} 未完整包含纵向执行条: {act.key}"
                    )
            for act in self.activations:
                if act in frag.contained or act in frag.spanning:
                    continue
                intersects_x = act.x1 > frag.x1 and act.x0 < frag.x2
                crosses_top = act.y1 < frag.y1 < act.y2
                crosses_bottom = act.y1 < frag.y2 < act.y2
                if intersects_x and (crosses_top or crosses_bottom):
                    raise AssertionError(
                        f"控制片段穿过未声明执行条: {frag.kind} / {act.key}"
                    )

    def to_canvas(self) -> XmlCanvas:
        self.assert_valid()
        canvas = XmlCanvas(
            self.width,
            self.height,
            self.title,
            self.subtitle,
            self.footer,
        )
        canvas.add_headings()

        for index, frag in enumerate(self.fragments):
            canvas.vertex(
                f"frag-{index}",
                frag.x1,
                frag.y1,
                frag.x2 - frag.x1,
                frag.y2 - frag.y1,
                _html(frag.kind),
                "html=1;shape=umlFrame;dashed=1;dashPattern=5 4;"
                "strokeWidth=1.4;strokeColor=#72839a;fillColor=#f7fbff;"
                "fontSize=14;fontStyle=1;fontColor=#40546d;"
                "fontFamily=Microsoft YaHei;size=24;",
            )
            canvas.vertex(
                f"frag-label-{index}",
                frag.x1 + 62,
                frag.y1,
                max(120.0, frag.x2 - frag.x1 - 76),
                28,
                _html(frag.label),
                "text;html=1;align=left;verticalAlign=middle;fontSize=13.5;"
                "fontColor=#40546d;fontFamily=Microsoft YaHei;",
            )

        for line in self.lifelines:
            if line.meta:
                label = (
                    '<div style="font-family:Microsoft YaHei;text-align:center;'
                    f'line-height:20px;font-size:{line.font_size:.1f}px;">'
                    '<i>«MetaClass»</i><br/>'
                    f'<b>: {_html(line.class_name)}</b></div>'
                )
            else:
                label = (
                    '<div style="font-family:Microsoft YaHei;text-align:center;'
                    f'line-height:22px;font-size:{line.font_size:.1f}px;">'
                    f"{_html(line.object_name)}: {_html(line.class_name)}</div>"
                )
            canvas.vertex(
                line.cell_id,
                line.x - line.width / 2,
                line.box_y,
                line.width,
                self.height - line.box_y - 54,
                label,
                "html=1;shape=umlLifeline;perimeter=lifelinePerimeter;"
                "container=1;dropTarget=0;collapsible=0;recursiveResize=0;"
                "outlineConnect=0;portConstraint=eastwest;lifelineDashed=0;"
                "strokeWidth=2;rounded=1;absoluteArcSize=1;arcSize=6;"
                "lifelineColor=#7b8a9d;size=62;fillColor=#f8fafc;"
                "strokeColor=#4f647a;fontSize=13.5;fontColor=#172033;",
            )

        for act in self.activations:
            canvas.vertex(
                act.cell_id,
                (act.lifeline.width - act.width) / 2,
                act.y1 - act.lifeline.box_y,
                act.width,
                act.y2 - act.y1,
                "",
                "html=1;points=[];perimeter=orthogonalPerimeter;"
                "outlineConnect=0;targetShapes=umlLifeline;"
                "portConstraint=eastwest;fillColor=#e7eef7;"
                "strokeColor=#415269;strokeWidth=1.2;",
                parent=act.lifeline.cell_id,
            )

        for message in self.messages:
            source_x = self._side(
                message.source, message.target.lifeline.x
            )
            target_x = self._side(
                message.target, message.source.lifeline.x
            )
            style = (
                "endArrow=block;endFill=1;endSize=9;verticalAlign=bottom;"
                "spacingBottom=3;edgeStyle=sequenceEdgeStyle;"
                "libavoidRouting=0;curved=0;rounded=0;fontSize=14;"
                "labelBackgroundColor=#ffffff;strokeWidth=1.6;"
                "strokeColor=#27364a;fontFamily=Microsoft YaHei;"
            )
            if message.kind in {"reply", "exception"}:
                style = (
                    "dashed=1;fixDash=1;dashPattern=6 4;"
                    "endArrow=open;endFill=0;endSize=10;verticalAlign=bottom;"
                    "spacingBottom=3;edgeStyle=sequenceEdgeStyle;"
                    "libavoidRouting=0;curved=0;rounded=0;fontSize=14;"
                    "labelBackgroundColor=#ffffff;strokeWidth=1.5;"
                    "strokeColor=#27364a;fontFamily=Microsoft YaHei;"
                )
            if message.kind == "exception":
                style = style.replace(
                    "strokeColor=#27364a", "strokeColor=#8e3f4f"
                ).replace("fontColor=#27364a", "")
            points = [
                (source_x, message.y),
                ((source_x + target_x) / 2, message.y),
                (target_x, message.y),
            ]
            canvas.edge(
                message.cell_id,
                message.source.cell_id,
                message.target.cell_id,
                points,
                style,
                message.label,
            )

        for index, (x1, x2, y, label) in enumerate(self.guards):
            canvas.edge(
                f"guard-line-{index}",
                None,
                None,
                [(x1, y), (x2, y)],
                "endArrow=none;html=1;dashed=1;dashPattern=5 4;"
                "strokeColor=#8a9aac;strokeWidth=1.1;",
            )
            canvas.vertex(
                f"guard-text-{index}",
                x1 + 12,
                y - 20,
                max(170.0, text_width(label, 13) + 18),
                23,
                _html(label),
                "text;html=1;align=left;verticalAlign=middle;fontSize=13;"
                "fontColor=#4f647a;fillColor=#ffffff;"
                "fontFamily=Microsoft YaHei;",
            )

        for index, (x, y, width, lines) in enumerate(self.notes):
            value = "<br/>".join(_html(item) for item in lines)
            canvas.vertex(
                f"note-{index}",
                x,
                y,
                width,
                22 + len(lines) * 19,
                value,
                "shape=note;html=1;align=left;verticalAlign=top;"
                "whiteSpace=wrap;spacing=10;fontSize=13;"
                "fillColor=#fffbe8;strokeColor=#b18b2f;"
                "fontColor=#5d4a18;fontFamily=Microsoft YaHei;",
            )

        canvas.add_footer()
        return canvas


def structured_login_sequence() -> DrawioSequence:
    s = DrawioSequence(
        2920,
        1940,
        "结构化后端 · 登录成功与凭证失败",
        "账号查询与密码校验位于同一账号解析链；成功分支写入令牌和登录审计，失败分支直接返回统一认证异常。",
        "同步调用编号；回复与异常不编号。alt 覆盖成功和失败，失败分支中的 opt 仅表示“已找到用户但密码错误”。",
    )
    s.lifeline("ui", "web", "LoginPage", 180, max_width=210)
    s.lifeline("controller", "controller", "IdentityAuthController", 520)
    s.lifeline("service", "authService", "AuthService", 930)
    s.lifeline("user_repo", "userRepository", "IamUserRepository", 1300)
    s.lifeline("crypto", "cryptoComponent", "CryptoComponent", 1690)
    s.lifeline("token_repo", "tokenRepository", "IamTokenRepository", 2100)
    s.lifeline("log", "logService", "LogService", 2510, max_width=230)

    ui_act = s.activation("ui", 170, 1830, name="act-ui")
    controller_act = s.activation(
        "controller", 170, 1830, name="act-controller"
    )
    service_act = s.activation("service", 235, 1800, name="act-service")

    s.sync(
        "ui",
        "controller",
        170,
        "login(username, password, systems)",
        ui_act,
        controller_act,
    )
    s.sync(
        "controller",
        "service",
        235,
        "authService.login(request)",
        controller_act,
        service_act,
    )

    user_ok = s.activation("user_repo", 430, 500, name="act-user-ok")
    crypto_ok = s.activation("crypto", 560, 650, name="act-crypto-ok")
    hash_act = s.activation("crypto", 710, 790, name="act-hash")
    token_act = s.activation("token_repo", 850, 930, name="act-token")
    user_save = s.activation("user_repo", 990, 1060, name="act-user-save")
    decrypt_act = s.activation("crypto", 1120, 1200, name="act-decrypt")
    log_ok = s.activation("log", 1260, 1340, name="act-log-ok")

    user_fail = s.activation("user_repo", 1490, 1560, name="act-user-fail")
    crypto_fail = s.activation("crypto", 1610, 1680, name="act-crypto-fail")
    log_fail = s.activation("log", 1710, 1770, name="act-log-fail")

    s.fragment(
        "alt",
        "登录校验",
        420,
        2850,
        300,
        1810,
        [user_ok, crypto_ok, hash_act, token_act, user_save,
         decrypt_act, log_ok, user_fail, crypto_fail, log_fail],
        [ui_act, controller_act, service_act],
    )
    s.guard(420, 2850, 385, "[账号正常且密码正确]")

    s.sync(
        "service",
        "user_repo",
        430,
        "findByUserNo(username)",
        service_act,
        user_ok,
    )
    s.reply(
        "user_repo",
        "service",
        500,
        "Optional<IamUser>(未删除)",
        user_ok,
        service_act,
    )
    s.sync(
        "service",
        "crypto",
        560,
        "verifyPassword(password, passwordHash)",
        service_act,
        crypto_ok,
    )
    s.reply("crypto", "service", 650, "true", crypto_ok, service_act)
    s.sync(
        "service",
        "crypto",
        710,
        "hash(accessToken)",
        service_act,
        hash_act,
    )
    s.reply("crypto", "service", 790, "tokenHash", hash_act, service_act)
    s.sync(
        "service",
        "token_repo",
        850,
        "save(IamToken)",
        service_act,
        token_act,
    )
    s.sync(
        "service",
        "user_repo",
        990,
        "save(user.lastLoginAt = now)",
        service_act,
        user_save,
    )
    s.sync(
        "service",
        "crypto",
        1120,
        "decrypt(nameEnc)",
        service_act,
        decrypt_act,
    )
    s.reply(
        "crypto",
        "service",
        1200,
        "displayName",
        decrypt_act,
        service_act,
    )
    s.sync(
        "service",
        "log",
        1260,
        "record(..., LOGIN, true, ...)",
        service_act,
        log_ok,
    )
    s.reply(
        "service",
        "controller",
        1390,
        "LoginResponse",
        service_act,
        controller_act,
    )
    s.reply(
        "controller",
        "ui",
        1450,
        "200 LoginResponse",
        controller_act,
        ui_act,
    )

    s.guard(420, 2850, 1465, "[账号不存在或密码错误]")
    s.sync(
        "service",
        "user_repo",
        1490,
        "findByUserNo(username)",
        service_act,
        user_fail,
    )
    s.reply(
        "user_repo",
        "service",
        1560,
        "Optional.empty() / Optional<IamUser>",
        user_fail,
        service_act,
    )

    s.fragment(
        "opt",
        "用户存在但密码校验失败",
        1230,
        2850,
        1575,
        1695,
        [crypto_fail],
        [ui_act, controller_act, service_act, user_fail],
    )
    s.sync(
        "service",
        "crypto",
        1610,
        "verifyPassword(...) = false",
        service_act,
        crypto_fail,
    )
    s.reply(
        "crypto",
        "service",
        1680,
        "false",
        crypto_fail,
        service_act,
    )
    s.sync(
        "service",
        "log",
        1710,
        "record(..., LOGIN, false, ...)",
        service_act,
        log_fail,
    )
    s.reply(
        "service",
        "controller",
        1775,
        "BusinessException(UNAUTHENTICATED)",
        service_act,
        controller_act,
        exception=True,
    )
    s.reply(
        "controller",
        "ui",
        1810,
        "401 账号或密码错误",
        controller_act,
        ui_act,
    )
    return s


def structured_management_sequence() -> DrawioSequence:
    s = DrawioSequence(
        4380,
        2100,
        "结构化后端 · 管理端默认拒绝鉴权",
        "公共认证、账号名读取与 API 映射在 alt 外；权限判定从用户、有效角色、父角色循环到 granted permissions 逐层推进。",
        "API 未登记、权限未映射或功能权限不通过均抛出 NO_FUNCTION_PERM；afterCompletion 另行记录请求审计。",
    )
    s.lifeline("browser", "browser", "管理端页面", 180, max_width=210)
    s.lifeline("interceptor", "authInterceptor", "AuthInterceptor", 600)
    s.lifeline("auth", "authService", "AuthService", 1040)
    s.lifeline("api", "apiService", "ApiService", 1430)
    s.lifeline("decision", "decisionService", "DecisionService", 1840)
    s.lifeline("user_repo", "userRepository", "IamUserRepository", 2270)
    s.lifeline("grant_repo", "userRoleRepository", "IamUserRoleRepository", 2700)
    s.lifeline("role_repo", "roleRepository", "IamRoleRepository", 3130)
    s.lifeline("perm_repo", "rolePermissionRepository",
               "IamRolePermissionRepository", 3570, max_width=350)
    s.lifeline("log", "logService", "LogService", 4070, max_width=230)

    browser_act = s.activation("browser", 170, 1980, name="act-browser")
    interceptor_act = s.activation(
        "interceptor", 170, 1980, name="act-interceptor"
    )
    auth_act = s.activation("auth", 240, 420, name="act-auth")
    username_act = s.activation("auth", 470, 550, name="act-username")
    api_act = s.activation("api", 600, 720, name="act-api")
    decision_act = s.activation(
        "decision", 850, 1690, name="act-decision"
    )
    user_act = s.activation("user_repo", 930, 1010, name="act-user")
    grant_act = s.activation("grant_repo", 1070, 1160, name="act-grant")
    role_act = s.activation("role_repo", 1300, 1380, name="act-role")
    perm_act = s.activation("perm_repo", 1480, 1570, name="act-perm")
    log_act = s.activation("log", 1770, 1870, name="act-log")

    s.sync(
        "browser",
        "interceptor",
        170,
        "preHandle(GET /roles?page=1)",
        browser_act,
        interceptor_act,
    )
    s.sync(
        "interceptor",
        "auth",
        240,
        "authenticateSession(token)",
        interceptor_act,
        auth_act,
    )
    s.reply(
        "auth",
        "interceptor",
        420,
        "AuthenticatedSession(userId, systemCode)",
        auth_act,
        interceptor_act,
    )
    s.sync(
        "interceptor",
        "auth",
        470,
        "getUserNo(userId)",
        interceptor_act,
        username_act,
    )
    s.reply(
        "auth",
        "interceptor",
        550,
        "username",
        username_act,
        interceptor_act,
    )
    s.sync(
        "interceptor",
        "api",
        600,
        "findByRequest(pattern, GET)",
        interceptor_act,
        api_act,
    )
    s.reply(
        "api",
        "interceptor",
        720,
        "IamApi(permissionId)",
        api_act,
        interceptor_act,
    )

    s.fragment(
        "alt",
        "默认拒绝判定",
        500,
        4290,
        780,
        1710,
        [user_act, grant_act, role_act, perm_act],
        [browser_act, interceptor_act, decision_act],
    )
    s.guard(500, 4290, 815, "[API 已登记且功能权限通过]")
    s.sync(
        "interceptor",
        "decision",
        850,
        "hasPermission(userId, ADMIN, permissionId)",
        interceptor_act,
        decision_act,
    )
    s.sync(
        "decision",
        "user_repo",
        930,
        "findById(userId)",
        decision_act,
        user_act,
    )
    s.reply(
        "user_repo",
        "decision",
        1010,
        "user(status=NORMAL, undeleted)",
        user_act,
        decision_act,
    )
    s.sync(
        "decision",
        "grant_repo",
        1070,
        "findByUserIdAndStatus(userId, ACTIVE)",
        decision_act,
        grant_act,
    )
    s.reply(
        "grant_repo",
        "decision",
        1160,
        "grants within [effectiveTime, expireTime)",
        grant_act,
        decision_act,
    )

    s.fragment(
        "loop",
        "while (!toVisit.isEmpty())",
        2980,
        3320,
        1240,
        1430,
        [role_act],
        [decision_act, browser_act, interceptor_act],
    )
    s.sync(
        "decision",
        "role_repo",
        1300,
        "findAllById(toVisit)",
        decision_act,
        role_act,
    )
    s.reply(
        "role_repo",
        "decision",
        1380,
        "active roles; next = parentId",
        role_act,
        decision_act,
    )
    s.note(
        2920,
        1320,
        260,
        ["visited 防止继承成环", "每层查询拥有独立执行条"],
    )

    s.sync(
        "decision",
        "perm_repo",
        1480,
        "findGrantedPermissions(roleIds, systemCode)",
        decision_act,
        perm_act,
    )
    s.reply(
        "perm_repo",
        "decision",
        1570,
        "GrantedPermissionView[]; allowedAction covers requiredAction",
        perm_act,
        decision_act,
    )
    s.reply(
        "decision",
        "interceptor",
        1660,
        "true",
        decision_act,
        interceptor_act,
    )
    s.note(
        520,
        1635,
        560,
        ["preHandle 返回 true", "后续由 Spring MVC 调用目标控制器"],
    )

    s.guard(500, 4290, 1710, "[API 未登记、permissionId 为空或权限不通过]")
    s.sync(
        "interceptor",
        "log",
        1770,
        "recordAudit(request, response, exception)",
        interceptor_act,
        log_act,
    )
    s.reply(
        "interceptor",
        "browser",
        1900,
        "BusinessException(NO_FUNCTION_PERM) → 403",
        interceptor_act,
        browser_act,
        exception=True,
    )
    return s


def structured_role_permission_sequence() -> DrawioSequence:
    s = DrawioSequence(
        3320,
        1740,
        "结构化后端 · 角色前端权限授予",
        "循环前读取角色和已有绑定；loop 内逐项校验并保存；循环后重新读取快照，仅在集合变化时记录权限变更审计。",
        "批量授权上限 200；已绑定权限跳过。loop 完整包含权限查询与新增绑定的独立执行条。",
    )
    s.lifeline("client", "bindPanel", "RolePermissionBindCard", 190, max_width=250)
    s.lifeline("controller", "roleController", "RoleController", 650)
    s.lifeline("service", "roleService", "RoleService", 1070)
    s.lifeline("role_repo", "roleRepository", "IamRoleRepository", 1470)
    s.lifeline("binding_repo", "rolePermissionRepository",
               "IamRolePermissionRepository", 1930, max_width=340)
    s.lifeline("perm_repo", "permissionRepository",
               "IamPermissionRepository", 2410, max_width=300)
    s.lifeline("log", "logService", "LogService", 2860, max_width=230)

    client_act = s.activation("client", 170, 1580, name="act-client")
    controller_act = s.activation(
        "controller", 170, 1580, name="act-controller"
    )
    service_act = s.activation("service", 235, 1510, name="act-service")
    role_act = s.activation("role_repo", 310, 400, name="act-role")
    existing_act = s.activation(
        "binding_repo", 450, 540, name="act-existing"
    )
    perm_act = s.activation("perm_repo", 650, 740, name="act-permission")
    save_act = s.activation("binding_repo", 810, 890, name="act-save")
    current_act = s.activation(
        "binding_repo", 1050, 1140, name="act-current"
    )
    log_act = s.activation("log", 1230, 1320, name="act-log")

    s.sync(
        "client",
        "controller",
        170,
        "bindPermissions(roleId, permissionIds, allowedAction)",
        client_act,
        controller_act,
    )
    s.sync(
        "controller",
        "service",
        235,
        "bindRolePermissions(...)",
        controller_act,
        service_act,
    )
    s.sync(
        "service",
        "role_repo",
        310,
        "findById(roleId)",
        service_act,
        role_act,
    )
    s.reply(
        "role_repo",
        "service",
        400,
        "IamRole(status=ACTIVE)",
        role_act,
        service_act,
    )
    s.sync(
        "service",
        "binding_repo",
        450,
        "findByRoleId(roleId)",
        service_act,
        existing_act,
    )
    s.reply(
        "binding_repo",
        "service",
        540,
        "existing bindings",
        existing_act,
        service_act,
    )

    s.fragment(
        "loop",
        "for (permissionId : permissionIds)",
        1810,
        2790,
        600,
        980,
        [perm_act, save_act],
        [client_act, controller_act, service_act],
    )
    s.sync(
        "service",
        "perm_repo",
        650,
        "findById(permissionId)",
        service_act,
        perm_act,
    )
    s.reply(
        "perm_repo",
        "service",
        740,
        "IamPermission(status=ACTIVE)",
        perm_act,
        service_act,
    )
    s.fragment(
        "opt",
        "bound.add(permissionId)",
        1800,
        2795,
        770,
        960,
        [save_act],
        [client_act, controller_act, service_act, perm_act],
    )
    s.sync(
        "service",
        "binding_repo",
        810,
        "save(new IamRolePermission(...))",
        service_act,
        save_act,
    )
    s.note(
        2460,
        835,
        300,
        ["已绑定权限直接跳过", "每轮执行条彼此独立"],
    )

    s.sync(
        "service",
        "binding_repo",
        1050,
        "findByRoleId(roleId) → currentPermissionIds",
        service_act,
        current_act,
    )
    s.reply(
        "binding_repo",
        "service",
        1140,
        "after permissionIds",
        current_act,
        service_act,
    )
    s.fragment(
        "opt",
        "!before.equals(after)",
        1800,
        2940,
        1190,
        1350,
        [log_act],
        [client_act, controller_act, service_act],
    )
    s.sync(
        "service",
        "log",
        1230,
        "recordPermissionChange(..., BIND_ROLE_PERMISSION, ...)",
        service_act,
        log_act,
    )
    s.reply(
        "service",
        "controller",
        1400,
        "void",
        service_act,
        controller_act,
    )
    s.reply(
        "controller",
        "client",
        1470,
        "200 Result.success()",
        controller_act,
        client_act,
    )
    return s


def oop_permission_sequence() -> DrawioSequence:
    s = DrawioSequence(
        3000,
        1850,
        "面向对象后端 · 按 operationId 查询权限",
        "AuthController → OperationAccessUseCase → OperationCatalogRepository → PermissionAccessUseCase；静态领域服务负责角色展开和权限聚合。",
        "本页给出端到端主流程；仓储读取与静态计算细部见“面向对象后端 · 权限决议细部”。",
    )
    s.lifeline("controller", "authController", "AuthController", 170,
               max_width=250)
    s.lifeline("operation", "operationAccessUseCase", "OperationAccessUseCase", 600,
               max_width=300)
    s.lifeline("operation_repo", "operationCatalogRepository",
               "OperationCatalogRepository", 1060, max_width=350)
    s.lifeline("permission", "permissionAccessUseCase", "PermissionAccessUseCase", 1540,
               max_width=330)
    s.lifeline("clock", "clock", "Clock", 1960, max_width=190)
    s.lifeline("resolver", "permissionResolver", "PermissionResolver", 2360,
               meta=True, max_width=290)
    s.lifeline("hierarchy", "roleHierarchy", "RoleHierarchy", 2760,
               meta=True, max_width=260)

    controller_act = s.activation(
        "controller", 170, 1770, name="act-controller"
    )
    operation_act = s.activation(
        "operation", 170, 1770, name="act-operation"
    )
    operation_repo_act = s.activation(
        "operation_repo", 260, 350, name="act-operation-repo"
    )
    permission_act = s.activation(
        "permission", 430, 1590, name="act-permission"
    )
    clock_act = s.activation("clock", 500, 570, name="act-clock")
    resolver_act = s.activation(
        "resolver", 680, 1450, name="act-resolver"
    )
    hierarchy_act = s.activation(
        "hierarchy", 740, 1330, name="act-hierarchy"
    )

    s.sync(
        "controller",
        "operation",
        170,
        "operationAllowed(userId, systemCode, operationId)",
        controller_act,
        operation_act,
    )
    s.sync(
        "operation",
        "operation_repo",
        260,
        "findByOperation(systemCode, operationId)",
        operation_act,
        operation_repo_act,
    )
    s.reply(
        "operation_repo",
        "operation",
        350,
        "ApiPermissionLookup(permissionId)",
        operation_repo_act,
        operation_act,
    )
    s.sync(
        "operation",
        "permission",
        430,
        "hasPermission(userId, systemCode, permissionId)",
        operation_act,
        permission_act,
    )
    s.sync(
        "permission",
        "clock",
        500,
        "instant()",
        permission_act,
        clock_act,
    )
    s.reply(
        "clock",
        "permission",
        570,
        "Instant now",
        clock_act,
        permission_act,
    )
    s.note(
        1490,
        890,
        430,
        ["读取用户、角色授权、角色、", "角色权限绑定和权限事实", "细部调用见下页"],
    )
    s.sync(
        "permission",
        "resolver",
        680,
        "PermissionResolver.resolve(grants, roles, ...)",
        permission_act,
        resolver_act,
    )
    s.sync(
        "resolver",
        "hierarchy",
        740,
        "RoleHierarchy.activeRoleIds(directRoleIds, lookup)",
        resolver_act,
        hierarchy_act,
    )
    s.reply(
        "hierarchy",
        "resolver",
        1330,
        "effectiveRoleIds",
        hierarchy_act,
        resolver_act,
    )
    s.reply(
        "resolver",
        "permission",
        1450,
        "Set<Long>",
        resolver_act,
        permission_act,
    )
    s.reply(
        "permission",
        "operation",
        1550,
        "contains(permissionId) → boolean",
        permission_act,
        operation_act,
    )
    s.reply(
        "operation",
        "controller",
        1730,
        "ApiPermissionCheckVO(operationId, true)",
        operation_act,
        controller_act,
    )
    return s


def oop_permission_detail_sequence() -> DrawioSequence:
    s = DrawioSequence(
        3400,
        1800,
        "面向对象后端 · 权限决议细部",
        "PermissionAccessUseCase 通过五个端口读取授权事实，再交给 PermissionResolver 与 RoleHierarchy 做纯计算。",
        "PermissionResolver 与 RoleHierarchy 为 MetaClass。RoleHierarchy 内部循环基于内存映射并用 visited 防环；权限按系统、启用状态和操作覆盖过滤。",
    )
    s.lifeline("permission", "permissionAccessUseCase", "PermissionAccessUseCase", 200,
               max_width=330)
    s.lifeline("user_repo", "userRepository", "UserAccountRepository", 560,
               max_width=310)
    s.lifeline("grant_repo", "roleGrantRepository", "RoleGrantRepository", 940,
               max_width=290)
    s.lifeline("role_repo", "roleRepository", "RoleRepository", 1320,
               max_width=260)
    s.lifeline("perm_repo", "rolePermissionRepository",
               "RolePermissionRepository", 1700, max_width=320)
    s.lifeline("catalog_repo", "permissionCatalogRepository",
               "PermissionCatalogRepository", 2100, max_width=360)
    s.lifeline("resolver", "permissionResolver", "PermissionResolver", 2550,
               meta=True, max_width=280)
    s.lifeline("hierarchy", "roleHierarchy", "RoleHierarchy", 3000,
               meta=True, max_width=250)

    permission_act = s.activation(
        "permission", 170, 1660, name="act-permission"
    )
    user_act = s.activation("user_repo", 500, 580, name="act-user")
    grant_act = s.activation("grant_repo", 650, 730, name="act-grant")
    role_act = s.activation("role_repo", 800, 880, name="act-role")
    perm_act = s.activation("perm_repo", 950, 1030, name="act-perm")
    facts_act = s.activation("catalog_repo", 1100, 1180, name="act-facts")
    resolver_act = s.activation(
        "resolver", 1260, 1640, name="act-resolver"
    )
    hierarchy_act = s.activation(
        "hierarchy", 1330, 1510, name="act-hierarchy"
    )

    s.next_number = 4
    s.sync(
        "permission",
        "user_repo",
        500,
        "findById(userId)",
        permission_act,
        user_act,
    )
    s.reply(
        "user_repo",
        "permission",
        580,
        "UserAccount(canAuthenticate=true)",
        user_act,
        permission_act,
    )
    s.sync(
        "permission",
        "grant_repo",
        650,
        "findByUserId(userId)",
        permission_act,
        grant_act,
    )
    s.reply(
        "grant_repo",
        "permission",
        730,
        "List<RoleGrant>",
        grant_act,
        permission_act,
    )
    s.sync(
        "permission",
        "role_repo",
        800,
        "findAll()",
        permission_act,
        role_act,
    )
    s.reply(
        "role_repo",
        "permission",
        880,
        "Map<roleId, Role>",
        role_act,
        permission_act,
    )
    s.sync(
        "permission",
        "perm_repo",
        950,
        "findByRoleIds(roles.keySet())",
        permission_act,
        perm_act,
    )
    s.reply(
        "perm_repo",
        "permission",
        1030,
        "List<RolePermissionGrant>",
        perm_act,
        permission_act,
    )
    s.sync(
        "permission",
        "catalog_repo",
        1100,
        "findFacts(permissionIds)",
        permission_act,
        facts_act,
    )
    s.reply(
        "catalog_repo",
        "permission",
        1180,
        "List<PermissionFact>",
        facts_act,
        permission_act,
    )
    s.sync(
        "permission",
        "resolver",
        1260,
        "PermissionResolver.resolve(grants, roles, ...)",
        permission_act,
        resolver_act,
    )
    s.sync(
        "resolver",
        "hierarchy",
        1330,
        "RoleHierarchy.activeRoleIds(directRoleIds, lookup)",
        resolver_act,
        hierarchy_act,
    )
    s.note(
        3130,
        1340,
        270,
        ["BFS 循环", "visited 防止继承成环"],
    )
    s.reply(
        "hierarchy",
        "resolver",
        1510,
        "effectiveRoleIds",
        hierarchy_act,
        resolver_act,
    )
    s.reply(
        "resolver",
        "permission",
        1640,
        "Set<Long>",
        resolver_act,
        permission_act,
    )
    return s


@dataclass(frozen=True)
class PageSpec:
    page_id: str
    svg_name: str
    png_name: str
    canvas: XmlCanvas


def build() -> list[PageSpec]:
    OUT.mkdir(parents=True, exist_ok=True)
    pages = [
        PageSpec(
            "class-structured-auth",
            "类图_结构化后端_认证.svg",
            "类图_结构化后端_认证.png",
            class_page(structured_auth_class()),
        ),
        PageSpec(
            "class-structured-decision",
            "类图_结构化后端_权限决策.svg",
            "类图_结构化后端_权限决策.png",
            class_page(structured_decision_class()),
        ),
        PageSpec(
            "class-oop-auth",
            "类图_面向对象后端_认证.svg",
            "类图_面向对象后端_认证.png",
            class_page(oop_auth_class()),
        ),
        PageSpec(
            "class-oop-permission",
            "类图_面向对象后端_权限决策.svg",
            "类图_面向对象后端_权限决策.png",
            class_page(oop_permission_class()),
        ),
        PageSpec(
            "sequence-structured-login",
            "顺序图_结构化后端_登录.svg",
            "顺序图_结构化后端_登录.png",
            structured_login_sequence().to_canvas(),
        ),
        PageSpec(
            "sequence-structured-management",
            "顺序图_结构化后端_管理端鉴权.svg",
            "顺序图_结构化后端_管理端鉴权.png",
            structured_management_sequence().to_canvas(),
        ),
        PageSpec(
            "sequence-structured-role-permission",
            "顺序图_结构化后端_角色权限授予.svg",
            "顺序图_结构化后端_角色权限授予.png",
            structured_role_permission_sequence().to_canvas(),
        ),
        PageSpec(
            "sequence-oop-permission",
            "顺序图_面向对象后端_权限查询.svg",
            "顺序图_面向对象后端_权限查询.png",
            oop_permission_sequence().to_canvas(),
        ),
        PageSpec(
            "sequence-oop-permission-detail",
            "顺序图_面向对象后端_权限决议细部.svg",
            "顺序图_面向对象后端_权限决议细部.png",
            oop_permission_detail_sequence().to_canvas(),
        ),
    ]

    drawio = (
        '<mxfile host="drawio-mcp" agent="Codex">'
        + "".join(
            page.canvas.page_xml(page.page_id)
            for page in pages
        )
        + "</mxfile>"
    )
    path = OUT / "RBAC_UML.drawio"
    path.write_text(drawio, encoding="utf-8")
    print(f"generated {path.relative_to(ROOT)}")
    for page in pages:
        print(
            f"page {page.page_id}: "
            f"{page.canvas.width:.0f}x{page.canvas.height:.0f}, "
            f"{len(page.canvas.cells)} cells"
        )
    return pages


if __name__ == "__main__":
    build()
