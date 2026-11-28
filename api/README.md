# API 设计契约

| 文件 | 说明 |
|---|---|
| `openapi.yaml` | **唯一契约源**：路径、方法、参数、请求体、响应结构与错误码以此为准。 |
| `openapi.json` | 同一份内容的 JSON 版，供 Apifox 推送/导入使用。 |

## 与双 Service 实现的约定

- 两套 Service（`structured` 与 `ddd`）共用同一份 OpenAPI 契约、同一套 Controller、DTO/VO 与数据库表结构。
- 修改接口的顺序：先改 `openapi.yaml` → 生成 `openapi.json` → 调整共享 `api` 层与 `service.port` → 分别确认两套实现行为一致。
- 黑盒契约测试位于 `backend/src/test/java/com/polyauth/contract`，通过 HTTP 验证两套策略输出等价。

## 校验与推送

```bash
cd prototype
node scripts/check-openapi.mjs
node scripts/push-apifox.mjs --project=8913225
```

推送前设置 `APIFOX_ACCESS_TOKEN`；推送使用 UTF-8 的 Node `fetch`，不要用 PowerShell `Invoke-WebRequest -Body <string>`。
