import sys
from svglib import Svg


W, H = 1820, 1220
s = Svg(W, H, "统一应用软件权限管理系统 — RBAC 核心 E-R 图")

ENTITY_FILL = "#eaf2fd"
ENTITY_STROKE = "#1f4e8c"
REL_FILL = "#fff4cf"
REL_STROKE = "#9a7a22"
SELF_COLOR = "#5b7da6"


def entity(x, y, w, h, title, table, fields):
    s.raw(f'<rect x="{x}" y="{y}" width="{w}" height="{h}" rx="8" fill="#fff" stroke="{ENTITY_STROKE}" stroke-width="2"/>')
    s.raw(
        f'<path d="M{x + 8},{y} H{x + w - 8} Q{x + w},{y} {x + w},{y + 8} V{y + 48} '
        f'H{x} V{y + 8} Q{x},{y} {x + 8},{y} Z" fill="{ENTITY_FILL}" stroke="{ENTITY_STROKE}" stroke-width="2"/>'
    )
    s.raw(f'<text x="{x + 16}" y="{y + 22}" font-size="18" font-weight="bold" fill="#173b6b">{title}</text>')
    s.raw(f'<text x="{x + 16}" y="{y + 41}" font-size="12" fill="#49678f">{table}</text>')
    for i, field in enumerate(fields):
        fy = y + 72 + i * 24
        s.raw(f'<text x="{x + 16}" y="{fy}" font-size="13" fill="#202a36">{field}</text>')


def diamond(cx, cy, text, sub="", w=136, h=70):
    pts = f"{cx},{cy - h / 2} {cx + w / 2},{cy} {cx},{cy + h / 2} {cx - w / 2},{cy}"
    s.raw(f'<polygon points="{pts}" fill="{REL_FILL}" stroke="{REL_STROKE}" stroke-width="2"/>')
    ty = cy + 5 if not sub else cy - 5
    s.raw(f'<text x="{cx}" y="{ty}" text-anchor="middle" font-size="14" font-weight="bold" fill="#5e4810">{text}</text>')
    if sub:
        s.raw(f'<text x="{cx}" y="{cy + 14}" text-anchor="middle" font-size="11" fill="#755c16">{sub}</text>')


def path(points, dashed=False, color="#394b61"):
    d = " ".join(("M" if i == 0 else "L") + f"{x},{y}" for i, (x, y) in enumerate(points))
    dash = ' stroke-dasharray="7,5"' if dashed else ""
    s.raw(f'<path d="{d}" fill="none" stroke="{color}" stroke-width="2"{dash}/>')


def card(x, y, text, color="#b03030"):
    w = max(28, len(text) * 11 + 12)
    s.raw(f'<rect x="{x - w / 2}" y="{y - 14}" width="{w}" height="20" rx="5" fill="#fff" stroke="none"/>')
    s.raw(f'<text x="{x}" y="{y}" text-anchor="middle" font-size="15" font-weight="bold" fill="{color}">{text}</text>')


# Relationships are drawn first so entity cards remain cleanly on top.

# 1. 多系统 1:N 前端权限（水平对齐）
path([(420, 335), (680, 335)])
diamond(550, 335, "定义", "系统拥有权限", w=142, h=70)
card(450, 316, "1")
card(650, 316, "N")

# 2. 多系统 1:N 后端 API（顶部正交折线）
path([(250, 240), (250, 102), (1550, 102), (1550, 240)])
diamond(1200, 102, "拥有", "系统登记接口", w=146, h=66)
card(272, 175, "1")
card(1572, 175, "N")

# 3. 前端权限 1:N 后端 API（水平对齐，留足边距）
path([(1060, 350), (1360, 350)])
diamond(1210, 350, "映射", "权限→API", w=136, h=70)
card(1096, 331, "1")
card(1324, 331, "N")

# 4. 前端权限 1:N 自关联（父子菜单 / 按钮）
path([(810, 240), (810, 166), (930, 166), (930, 240)], color=SELF_COLOR)
s.raw(f'<path d="M930,240 l-7,-12 M930,240 l7,-12" fill="none" stroke="{SELF_COLOR}" stroke-width="2"/>')
card(870, 158, "1 : N")
s.raw('<text x="870" y="198" text-anchor="middle" font-size="12" fill="#49678f">父子菜单 / 按钮</text>')

# 5. 权限 M:N 角色（垂直中轴对齐 x=870）
path([(870, 460), (870, 600)])
diamond(870, 530, "授权", "iam_role_permission", w=214, h=76)
card(896, 482, "M")
card(896, 584, "N")

# 6. 角色 1:N 自关联（RBAC1 角色继承，可选）
path([(1040, 650), (1210, 650), (1210, 726), (1040, 726)], dashed=True, color=SELF_COLOR)
s.raw(f'<path d="M1040,726 l12,-7 M1040,726 l12,7" fill="none" stroke="{SELF_COLOR}" stroke-width="2"/>')
card(1125, 642, "1 : N")
s.raw('<text x="1125" y="693" text-anchor="middle" font-size="12" fill="#49678f">角色继承（可选）</text>')

# 7. 角色 M:N 用户（垂直中轴对齐 x=870）
path([(870, 775), (870, 915)])
diamond(870, 845, "分配", "iam_user_role", w=186, h=76)
card(896, 797, "N")
card(896, 899, "M")

# 8. 组织 1:N 用户（水平对齐，留足边距）
path([(440, 1012), (690, 1012)])
diamond(565, 1012, "隶属于", "组织→用户", w=140, h=70)
card(470, 993, "1")
card(660, 993, "N")

# 9. 组织 1:N 自关联（上下级组织层级）
path([(200, 910), (200, 832), (320, 832), (320, 910)], color=SELF_COLOR)
s.raw(f'<path d="M320,910 l-7,-12 M320,910 l7,-12" fill="none" stroke="{SELF_COLOR}" stroke-width="2"/>')
card(260, 824, "1 : N")
s.raw('<text x="260" y="868" text-anchor="middle" font-size="12" fill="#49678f">上下级组织</text>')

# Six business entities.
entity(80, 240, 340, 190, "多系统", "iam_system", [
    "PK  id",
    "UK  system_code",
    "     system_name",
    "     status / callback_url",
])

entity(680, 240, 380, 220, "权限（前端菜单/按钮树）", "iam_permission", [
    "PK  id",
    "FK  system_id  → iam_system",
    "FK  parent_id  → iam_permission（自关联）",
    "UK  system_id + permission_code",
    "     permission_type：MENU / BUTTON",
    "     route_path / action / sort_no",
])

entity(1360, 240, 380, 220, "API（后端接口）", "iam_api", [
    "PK  id",
    "FK  system_id    → iam_system",
    "FK  permission_id → iam_permission",
    "UK  system_id + api_code",
    "UK  api_path + http_method",
    "     api_name / status",
])

entity(700, 600, 340, 175, "角色", "iam_role", [
    "PK  id",
    "UK  role_code",
    "     role_name / status",
    "FK  parent_id → iam_role（角色继承，可选）",
])

entity(80, 910, 360, 205, "分层组织部门", "iam_org", [
    "PK  id",
    "FK  parent_id → iam_org（自关联）",
    "UK  org_code",
    "     org_name / org_type / org_level",
    "     ancestors：分层路径",
])

entity(690, 915, 360, 195, "用户", "iam_user", [
    "PK  id",
    "UK  user_no",
    "FK  org_id → iam_org",
    "     name_enc / phone_enc（加密）",
    "     account_status / post",
])

# Legend and design notes.
s.raw('<line x1="60" y1="1148" x2="1760" y2="1148" stroke="#c9d1da" stroke-width="1"/>')
s.raw(f'<polygon points="80,1180 108,1167 136,1180 108,1193" fill="{REL_FILL}" stroke="{REL_STROKE}" stroke-width="2"/>')
s.raw('<text x="150" y="1185" font-size="13" fill="#303946">联系（多对多关系表不画成独立实体）</text>')
s.raw(f'<rect x="412" y="1168" width="28" height="24" rx="5" fill="#fff" stroke="{ENTITY_STROKE}" stroke-width="2"/>')
s.raw('<text x="452" y="1185" font-size="13" fill="#303946">六个核心业务实体</text>')
s.raw(f'<path d="M590,1180 H626" fill="none" stroke="{SELF_COLOR}" stroke-width="2"/>')
s.raw(f'<path d="M626,1180 l-8,-5 M626,1180 l-8,5" fill="none" stroke="{SELF_COLOR}" stroke-width="2"/>')
s.raw('<text x="638" y="1185" font-size="13" fill="#303946">自关联（层级 / 继承）</text>')
s.raw('<text x="802" y="1185" font-size="13" fill="#8a2b2b">1 / N / M 为参与基数</text>')
s.raw('<text x="975" y="1185" font-size="13" fill="#303946">鉴权链：请求 API → 所属权限 → 用户角色授予的权限；前端隐藏菜单不能替代后端 API 校验</text>')

s.save(sys.argv[1])
