"""生成双后端 Java 包图，并输出 SVG 与 2 倍 PNG。"""
from __future__ import annotations

import subprocess
from pathlib import Path

from svglib import Svg


ROOT = Path(__file__).resolve().parents[2]
OUT = ROOT / "design"
SVG_PATH = OUT / "包图_双后端.svg"
PNG_PATH = OUT / "包图_双后端.png"


def package_parts(svg: Svg, x: int, y: int, width: int, height: int,
                  name: str, children: list[str], color: str) -> None:
    """绘制 UML 包框，子包或关键类放在包体中。"""
    tab_width = min(230, max(130, len(name) * 12 + 30))
    svg.raw(
        f'<path d="M{x},{y} H{x + tab_width - 14} '
        f'Q{x + tab_width},{y} {x + tab_width},{y + 14} '
        f'V{y + 34} H{x} Z" fill="{color}" stroke="#29445f" stroke-width="1.6"/>'
    )
    svg.raw(
        f'<rect x="{x}" y="{y + 34}" width="{width}" height="{height - 34}" '
        f'rx="5" fill="#ffffff" stroke="#29445f" stroke-width="1.6"/>'
    )
    svg.text(x + tab_width / 2, y + 23, name, 15, "#172033", weight="bold", bg=False)
    start_y = y + 70
    for index, child in enumerate(children):
        child_y = start_y + index * 31
        svg.raw(
            f'<rect x="{x + 18}" y="{child_y - 20}" width="{width - 36}" height="25" '
            f'rx="4" fill="#f7fafc" stroke="#d5dee8" stroke-width="1"/>'
        )
        svg.raw(
            f'<text x="{x + 31}" y="{child_y - 3}" font-size="13.5" '
            f'fill="#25384d">{child}</text>'
        )


def dependency(svg: Svg, points: list[tuple[int, int]], label: str,
               label_pos: tuple[int, int]) -> None:
    """绘制 UML 依赖：虚线 + 空心箭头。"""
    path = "M" + " L".join(f"{x},{y}" for x, y in points)
    svg.raw(
        f'<path d="{path}" fill="none" stroke="#65768a" stroke-width="1.7" '
        f'stroke-dasharray="9,6" marker-end="url(#package-arrow)"/>'
    )
    svg.text(label_pos[0], label_pos[1], label, 12.5, "#4a5c70")


def build() -> None:
    svg = Svg(2400, 1500, "PolyAuth 后端包图（当前代码依赖）")
    svg.raw(
        '<defs><marker id="package-arrow" markerWidth="13" markerHeight="12" '
        'refX="11" refY="6" orient="auto" markerUnits="strokeWidth">'
        '<path d="M0,0 L12,6 L0,12" fill="#ffffff" stroke="#65768a" '
        'stroke-width="1.5"/></marker></defs>'
    )
    svg.text(
        1200,
        78,
        "左侧：结构化后端按功能过程分层；右侧：OOP 后端由领域模型和端口隔离基础设施。",
        14,
        "#526579",
        bg=False,
    )
    svg.raw('<line x1="1200" y1="110" x2="1200" y2="1430" stroke="#d7e0e9" stroke-width="2"/>')
    svg.text(600, 132, "结构化后端 · com.polyauth.structured", 20, "#1f4e8c", weight="bold", bg=False)
    svg.text(1800, 132, "OOP 后端 · com.polyauth.oop", 20, "#1f4e8c", weight="bold", bg=False)

    # 先在底层画依赖线，再由包框覆盖线头，保证线不穿过包内部。
    dependency(svg, [(600, 350), (600, 435)], "interfaces → application",
               (720, 397))
    dependency(svg, [(600, 660), (600, 760)], "application → infrastructure",
               (737, 712))
    dependency(svg, [(240, 1030), (295, 1130)], "infrastructure → common",
               (380, 1090))
    dependency(svg, [(1640, 350), (1490, 350)], "interfaces → application",
               (1565, 325))
    dependency(svg, [(1490, 670), (1490, 785)], "application → domain",
               (1610, 730))
    dependency(svg, [(1490, 1035), (1490, 1160)], "infrastructure → domain ports",
               (1675, 1100))

    package_parts(
        svg, 210, 190, 780, 160, "interfaces",
        ["admin：Controller、DTO、VO", "identity：统一身份契约",
         "AuthInterceptor、RequestIdFilter"],
        "#dceaf8",
    )
    package_parts(
        svg, 210, 435, 780, 225, "application",
        ["auth / account / organization", "role / permission / datascope",
         "decision / identity / log", "线性业务过程与事务边界"],
        "#dceaf8",
    )
    package_parts(
        svg, 210, 760, 780, 270, "infrastructure",
        ["persistence.entity：JPA 实体", "persistence.repository：Spring Data",
         "cache：Redis、锁、幂等", "crypto / logging / scheduling"],
        "#f8eddc",
    )
    package_parts(
        svg, 70, 1130, 450, 180, "common",
        ["Result / PageResult", "ErrorCode / BusinessException", "枚举与公共规则"],
        "#eef2f6",
    )

    package_parts(
        svg, 1370, 190, 900, 160, "interfaces",
        ["controller：AuthController、RoleController", "filter / advice / security"],
        "#dceaf8",
    )
    package_parts(
        svg, 1370, 435, 900, 235, "application",
        ["command / result", "usecase：认证、角色、权限查询", "port：JWT、密码、审计、加密"],
        "#dceaf8",
    )
    package_parts(
        svg, 1370, 785, 900, 250, "domain",
        ["model：Role、RoleGrant、UserAccount", "service：RoleHierarchy、PermissionResolver",
         "repository：只返回领域对象", "不依赖 Spring / JPA / interfaces"],
        "#e3f4e8",
    )
    package_parts(
        svg, 1370, 1160, 900, 230, "infrastructure",
        ["persistence.entity / mapper / adapter", "crypto：AES、PBKDF2、HMAC",
         "security：JWT 实现", "实现 application.port 与 domain.repository"],
        "#f8eddc",
    )

    svg.raw(
        '<rect x="70" y="1385" width="2260" height="70" rx="8" '
        'fill="#f7fafc" stroke="#cbd7e3" stroke-width="1.2"/>'
    )
    svg.text(
        120,
        1416,
        "结构化：interfaces → application → infrastructure，业务规则集中在 Service；"
        "OOP：interfaces → application → domain，infrastructure 通过端口实现并反向适配。",
        14,
        "#25384d",
        anchor="start",
        bg=False,
    )
    svg.text(
        120,
        1442,
        "依赖箭头表示编译期 import 方向；OOP 的 domain 不依赖 infrastructure，"
        "避免数据库与 JWT 实现反向侵入领域层。",
        13,
        "#5a6979",
        anchor="start",
        bg=False,
    )

    OUT.mkdir(parents=True, exist_ok=True)
    svg.save(SVG_PATH)
    subprocess.run(
        ["rsvg-convert", "-z", "2", "-o", str(PNG_PATH), str(SVG_PATH)],
        check=True,
    )
    print(f"generated {SVG_PATH.relative_to(ROOT)}")
    print(f"generated {PNG_PATH.relative_to(ROOT)}")


if __name__ == "__main__":
    build()
