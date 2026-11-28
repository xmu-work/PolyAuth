"""生成双后端 Java 包图，并输出 SVG 与 2 倍高清 PNG。"""
from __future__ import annotations

import argparse
import subprocess
from pathlib import Path
from xml.sax.saxutils import escape

from svglib import Svg


ROOT = Path(__file__).resolve().parents[2]
SCRIPT_DIR = Path(__file__).resolve().parent
OUT = ROOT / "docs"
SVG_PATH = OUT / "包图_双后端.svg"
PNG_PATH = OUT / "包图_双后端.png"
RENDER_SH = SCRIPT_DIR / "render.sh"


def package_parts(
    svg: Svg,
    x: int,
    y: int,
    width: int,
    height: int,
    name: str,
    children: list[str],
    color: str,
) -> dict[str, int]:
    """绘制 UML 包框（左上标签 tab + 主体矩形），返回各边几何坐标。"""
    tab_height = 34
    tab_width = min(220, max(136, len(name) * 11 + 36))
    svg.raw(
        f'<path d="M{x},{y} H{x + tab_width - 12} '
        f'Q{x + tab_width},{y} {x + tab_width},{y + 12} '
        f'V{y + tab_height} H{x} Z" '
        f'fill="{color}" stroke="#29445f" stroke-width="1.7"/>'
    )
    svg.raw(
        f'<rect x="{x}" y="{y + tab_height}" width="{width}" '
        f'height="{height - tab_height}" rx="6" fill="#ffffff" '
        f'stroke="#29445f" stroke-width="1.7"/>'
    )
    svg.text(
        x + tab_width / 2,
        y + 23,
        name,
        15.5,
        "#172033",
        weight="bold",
        bg=False,
    )
    start_y = y + tab_height + 16
    row_height = 26
    row_step = 33
    for index, child in enumerate(children):
        rect_y = start_y + index * row_step
        svg.raw(
            f'<rect x="{x + 18}" y="{rect_y}" width="{width - 36}" '
            f'height="{row_height}" rx="4" fill="#f7fafc" '
            f'stroke="#d5dee8" stroke-width="1"/>'
        )
        svg.raw(
            f'<text x="{x + 30}" y="{rect_y + 18}" font-size="13.5" '
            f'fill="#25384d">{escape(child)}</text>'
        )
    return {
        "left": x,
        "right": x + width,
        "cx": x + width // 2,
        "top": y,
        "body_top": y + tab_height,
        "bottom": y + height,
        "body_mid_y": y + tab_height + (height - tab_height) // 2,
    }


def dependency(
    svg: Svg,
    points: list[tuple[int, int]],
    labels: list[tuple[int, int, str, str]],
    *,
    dip: bool = False,
) -> None:
    """绘制 UML 依赖：虚线 + 开放箭头，并放置文字标签。"""
    stroke = "#1d6b52" if dip else "#5b6f86"
    marker = "package-arrow-dip" if dip else "package-arrow"
    path = "M" + " L".join(f"{x},{y}" for x, y in points)
    svg.raw(
        f'<path d="{path}" fill="none" stroke="{stroke}" stroke-width="1.9" '
        f'stroke-dasharray="8,5" marker-end="url(#{marker})"/>'
    )
    text_color = "#195c46" if dip else "#45586d"
    weight = "bold" if dip else "normal"
    for lx, ly, text, anchor in labels:
        svg.text(lx, ly, text, 13, text_color, anchor=anchor, weight=weight, bg=False)


def build_svg() -> None:
    width, height = 2400, 1440
    svg = Svg(width, height, "PolyAuth 后端包图（当前代码依赖）")
    svg.raw(
        "<defs>"
        '<marker id="package-arrow" markerWidth="14" markerHeight="14" '
        'refX="11" refY="7" orient="auto" markerUnits="userSpaceOnUse">'
        '<path d="M2,2 L11,7 L2,12" fill="none" stroke="#5b6f86" '
        'stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/></marker>'
        '<marker id="package-arrow-dip" markerWidth="14" markerHeight="14" '
        'refX="11" refY="7" orient="auto" markerUnits="userSpaceOnUse">'
        '<path d="M2,2 L11,7 L2,12" fill="none" stroke="#1d6b52" '
        'stroke-width="2.1" stroke-linecap="round" stroke-linejoin="round"/></marker>'
        "</defs>"
    )

    svg.text(
        width / 2,
        78,
        "左侧：结构化后端按功能过程分层；右侧：OOP 后端由领域模型与输出端口隔离基础设施（依赖倒置）。",
        14.5,
        "#526579",
        bg=False,
    )

    divider_x = 1120
    svg.raw(
        f'<line x1="{divider_x}" y1="112" x2="{divider_x}" y2="1248" '
        'stroke="#d7e0e9" stroke-width="2"/>'
    )

    left_x, box_w = 180, 830
    right_x = 1230
    left_cx = left_x + box_w // 2
    right_cx = right_x + box_w // 2

    svg.text(
        left_cx,
        140,
        "结构化后端 · com.polyauth.structured",
        20,
        "#1f4e8c",
        weight="bold",
        bg=False,
    )
    svg.text(
        right_cx,
        140,
        "OOP 后端 · com.polyauth.oop",
        20,
        "#1f4e8c",
        weight="bold",
        bg=False,
    )

    # 统一 4 层垂直节奏，左右完全齐平且与底部注释区留出充裕间距
    y_l1, h_l1 = 176, 164
    y_l2, h_l2 = 434, 198
    y_l3, h_l3 = 726, 198
    y_l4, h_l4 = 1024, 198

    # ---------- 左侧：结构化后端 ----------
    s_if = package_parts(
        svg,
        left_x,
        y_l1,
        box_w,
        h_l1,
        "interfaces",
        [
            "admin：Controller、DTO、VO（认证、账号、组织、角色、权限、接口、系统、日志）",
            "identity / openapi：统一身份契约与开放接口入口",
            "AuthInterceptor、OpenApiAuthInterceptor、IdempotencyFilter、RequestIdFilter",
        ],
        "#dceaf8",
    )
    s_app = package_parts(
        svg,
        left_x,
        y_l2,
        box_w,
        h_l2,
        "application",
        [
            "auth / account / organization：认证会话、账号与组织过程",
            "role / permission / api / system：角色、权限、接口与系统管理",
            "decision / identity / openapi / log：鉴权决策、统一身份与审计日志",
            "expiration / idempotency：过期清理与幂等控制（线性过程与事务边界）",
        ],
        "#dceaf8",
    )
    s_infra = package_parts(
        svg,
        left_x,
        y_l3,
        box_w,
        h_l3,
        "infrastructure",
        [
            "persistence.entity：JPA 数据库实体（IamUser、IamRole 等）",
            "persistence.repository：Spring Data JPA 仓储接口",
            "cache：Redis 权限缓存、业务锁、幂等与防重放",
            "crypto / logging / scheduling：加解密、切面日志与定时过期任务",
        ],
        "#f8eddc",
    )
    s_common = package_parts(
        svg,
        left_x,
        y_l4,
        box_w,
        h_l4,
        "common",
        [
            "result：Result / PageResult 统一响应包装",
            "exception：BusinessException / GlobalExceptionHandler",
            "enums：ErrorCode、AccountStatus、OrgType、RoleType、OperationAction",
            "公共契约与基础规则（被 interfaces / application / infrastructure 共用）",
        ],
        "#eef2f6",
    )

    # ---------- 右侧：OOP 后端 ----------
    o_if = package_parts(
        svg,
        right_x,
        y_l1,
        box_w,
        h_l1,
        "interfaces",
        [
            "controller：AuthController、RoleController、IdentityAuthController 等",
            "dto / vo / common / openapi：入参 DTO、响应 VO 与统一错误结构",
            "filter / advice / security：拦截器、全局异常处理与幂等过滤",
        ],
        "#dceaf8",
    )
    o_app = package_parts(
        svg,
        right_x,
        y_l2,
        box_w,
        h_l2,
        "application",
        [
            "command / result / exception：用例命令、结果模型与应用错误码",
            "usecase：认证、角色、账号、组织、权限查询与统一身份编排",
            "port：JWT、密码、审计、加密、幂等与防重放输出端口接口",
            "编排用例流程，仅依赖 domain 与自身 port 抽象",
        ],
        "#dceaf8",
    )
    o_dom = package_parts(
        svg,
        right_x,
        y_l3,
        box_w,
        h_l3,
        "domain",
        [
            "model：Role、RoleGrant、UserAccount、Organization 等领域模型",
            "service：RoleHierarchy、PermissionResolver 领域服务",
            "repository：纯领域仓储接口（只接收并返回领域对象）",
            "exception：领域规则异常（不依赖 Spring / JPA / interfaces）",
        ],
        "#e3f4e8",
    )
    o_infra = package_parts(
        svg,
        right_x,
        y_l4,
        box_w,
        h_l4,
        "infrastructure",
        [
            "persistence.entity / mapper / repository / adapter：JPA 持久化适配",
            "crypto：AES 姓名加密、PBKDF2 口令校验、HMAC 令牌摘要",
            "security / cache / logging：JWT 签发、本地幂等与防重放、切面日志",
            "实现 domain.repository 与 application.port（反向适配领域与用例）",
        ],
        "#f8eddc",
    )

    # ---------- 左侧依赖箭头（自上而下分层 + 外侧共享 common） ----------
    dependency(
        svg,
        [(s_if["cx"], s_if["bottom"]), (s_app["cx"], s_app["body_top"] - 1)],
        [(s_if["cx"] + 16, (s_if["bottom"] + s_app["body_top"]) // 2 + 5, "interfaces → application", "start")],
    )
    dependency(
        svg,
        [(s_app["cx"], s_app["bottom"]), (s_infra["cx"], s_infra["body_top"] - 1)],
        [(s_app["cx"] + 16, (s_app["bottom"] + s_infra["body_top"]) // 2 + 5, "application → infrastructure", "start")],
    )
    dependency(
        svg,
        [(s_infra["cx"], s_infra["bottom"]), (s_common["cx"], s_common["body_top"] - 1)],
        [(s_infra["cx"] + 16, (s_infra["bottom"] + s_common["body_top"]) // 2 + 5, "infrastructure → common", "start")],
    )
    # 左侧外走廊：interfaces / application 共享依赖 common
    bus_x = 128
    svg.raw(
        f'<path d="M{s_if["left"]},{s_if["body_mid_y"]} H{bus_x} '
        f'M{s_app["left"]},{s_app["body_mid_y"]} H{bus_x}" '
        'fill="none" stroke="#5b6f86" stroke-width="1.6" stroke-dasharray="8,5"/>'
    )
    dependency(
        svg,
        [(bus_x, s_if["body_mid_y"]), (bus_x, s_common["body_mid_y"]), (s_common["left"] - 1, s_common["body_mid_y"])],
        [
            (bus_x - 12, 822, "各层共用", "end"),
            (bus_x - 12, 842, "common", "end"),
        ],
    )

    # ---------- 右侧依赖箭头（interfaces → application → domain；infrastructure 反向依赖 domain 与 application.port） ----------
    dependency(
        svg,
        [(o_if["cx"], o_if["bottom"]), (o_app["cx"], o_app["body_top"] - 1)],
        [(o_if["cx"] + 16, (o_if["bottom"] + o_app["body_top"]) // 2 + 5, "interfaces → application", "start")],
    )
    dependency(
        svg,
        [(o_app["cx"], o_app["bottom"]), (o_dom["cx"], o_dom["body_top"] - 1)],
        [(o_app["cx"] + 16, (o_app["bottom"] + o_dom["body_top"]) // 2 + 5, "application → domain", "start")],
    )
    # 依赖倒置 1：infrastructure 向上指向 domain（实现 domain.repository）
    dependency(
        svg,
        [(o_infra["cx"], o_infra["body_top"]), (o_dom["cx"], o_dom["bottom"] + 1)],
        [
            (o_infra["cx"] + 16, (o_dom["bottom"] + o_infra["body_top"]) // 2 - 2, "infrastructure → domain", "start"),
            (o_infra["cx"] + 16, (o_dom["bottom"] + o_infra["body_top"]) // 2 + 18, "（依赖倒置：实现 domain.repository）", "start"),
        ],
        dip=True,
    )
    # 依赖倒置 2：infrastructure 右侧绕行向上指向 application.port
    port_row_y = y_l2 + 34 + 16 + 2 * 33 + 13  # 正对 application 内第 3 行 port
    route_x = 2112
    dependency(
        svg,
        [
            (o_infra["right"], o_infra["body_mid_y"]),
            (route_x, o_infra["body_mid_y"]),
            (route_x, port_row_y),
            (o_app["right"] + 1, port_row_y),
        ],
        [
            (route_x + 14, 818, "infrastructure →", "start"),
            (route_x + 14, 838, "application.port", "start"),
            (route_x + 14, 858, "（实现输出端口）", "start"),
        ],
        dip=True,
    )

    # ---------- 底部图例与架构对比说明区（与最底层包框保持 50px 安全间距） ----------
    footer_y, footer_h = 1272, 126
    svg.raw(
        f'<rect x="80" y="{footer_y}" width="2240" height="{footer_h}" rx="8" '
        'fill="#f7fafc" stroke="#cbd7e3" stroke-width="1.3"/>'
    )
    # 图例行
    legend_y = footer_y + 28
    svg.raw(
        f'<line x1="120" y1="{legend_y}" x2="178" y2="{legend_y}" '
        'stroke="#5b6f86" stroke-width="1.9" stroke-dasharray="8,5" '
        'marker-end="url(#package-arrow)"/>'
    )
    svg.text(192, legend_y + 5, "常规单向依赖（编译期 import 方向）", 13.5, "#45586d", anchor="start", bg=False)
    svg.raw(
        f'<line x1="470" y1="{legend_y}" x2="528" y2="{legend_y}" '
        'stroke="#1d6b52" stroke-width="2" stroke-dasharray="8,5" '
        'marker-end="url(#package-arrow-dip)"/>'
    )
    svg.text(
        542,
        legend_y + 5,
        "依赖倒置（infrastructure 反向实现 domain.repository 与 application.port）",
        13.5,
        "#195c46",
        anchor="start",
        weight="bold",
        bg=False,
    )

    svg.text(
        120,
        footer_y + 66,
        "架构对比：结构化后端沿 interfaces → application → infrastructure → common 向下编排过程，"
        "业务规则集中在 Service；OOP 后端沿 interfaces → application → domain 逐层收敛，由 infrastructure 反向适配。",
        14,
        "#25384d",
        anchor="start",
        bg=False,
    )
    svg.text(
        120,
        footer_y + 98,
        "编译期约束：UML 包图虚线箭头指向被依赖方；OOP 的 domain 层不依赖 Spring / JPA / JWT，"
        "避免持久化与安全框架反向侵入核心领域规则。",
        13.5,
        "#526579",
        anchor="start",
        bg=False,
    )

    OUT.mkdir(parents=True, exist_ok=True)
    svg.save(SVG_PATH)
    print(f"generated {SVG_PATH.relative_to(ROOT)}")


def render_png() -> None:
    subprocess.run(["zsh", str(RENDER_SH), str(SVG_PATH), str(PNG_PATH)], check=True)
    print(f"generated {PNG_PATH.relative_to(ROOT)}")


def main() -> None:
    parser = argparse.ArgumentParser(description="生成双后端 Java 包图 SVG/PNG")
    parser.add_argument("--svg-only", action="store_true", help="仅生成 SVG，不导出 PNG")
    args = parser.parse_args()
    build_svg()
    if not args.svg_only:
        render_png()


if __name__ == "__main__":
    main()
