# 统一权限平台 · 管理后台前端

依据《前端工程契约与模块设计说明书》V2.1（`docs/06_前端契约与模块设计.md`）完成前端框架搭建。

## 技术栈

Vue 3（仅 `<script setup>`）+ TypeScript(strict) + Vite 5 + Pinia + Vue Router 4 + Element Plus + Axios + pnpm + Vitest

## 常用命令

```bash
pnpm install      # 安装依赖（lock 文件入库）
pnpm dev          # 启动开发服务（默认 5173，请求代理到真实后端）
pnpm build        # vue-tsc 类型检查 + 生产构建
pnpm test         # Vitest 组件与 API 契约测试
pnpm typecheck    # 仅类型检查
pnpm lint         # ESLint + Stylelint
```

## 已完成的框架能力

- 登录、会话恢复、登出与未授权跳转
- 按权限过滤的动态路由、侧边菜单、`v-perm` 元素级权限指令与 60 秒权限同步
- 每次菜单切换先调用后端权限校验接口，确保对应的认证、审计和 Logback 日志可追踪
- 主布局、顶栏、面包屑、多页签、403/404/500 页面
- Pinia 状态层、Axios 契约封装、完整 API/类型目录、分页组合式函数与字典 Store
- 组织、用户、角色、授权、功能权限、统一日志等页面全部连接真实后端
- 功能权限页提供可浏览权限树、父权限选择、完整操作码和角色权限批量授权
- API 独立登记、权限映射查询、已有 API 映射与解除映射
- 顶栏自助修改密码、角色成员查看与动态组织范围加载

## 后端联调

前端不包含 Mock 数据或 Mock 插件。开发服务器通过 Vite proxy 将所有 `/api/v1` 请求转发到 `http://localhost:8080`，因此启动前端前必须先启动后端和数据库。

## 目录契约（契约 2.1）

`api / types / stores / router / directives / composables / layout / views / utils / constants / styles`

## UI 实现三原则（契约 9.1）

1. 布局容器（grid/flex/间距）与视觉组件（颜色/圆角/阴影/字体）分离
2. 设计令牌先行：`src/styles/tokens.css` 为唯一视觉来源，禁止硬编码色值与 px
3. 一个组件只渲染一个独立视觉区块

## 待办

- husky + lint-staged 提交即检（契约第 12 章，后续接入）
