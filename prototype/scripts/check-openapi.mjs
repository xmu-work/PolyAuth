import fs from 'node:fs'
import path from 'node:path'
import {fileURLToPath} from 'node:url'

const projectRoot = path.resolve(
  path.dirname(fileURLToPath(import.meta.url)),
  '..',
  '..',
)
const spec = JSON.parse(
  fs.readFileSync(path.join(projectRoot, 'api', 'openapi.json'), 'utf8'),
)

const requiredPaths = [
  '/auth/login',
  '/auth/logout',
  '/auth/me',
  '/auth/permissions/{permission}',
  '/auth/departments',
  '/auth/api-permissions/{operationId}',
  '/auth/departments/{departmentId}/users',
  '/auth/users/{userId}',
  '/open/permissions',
  '/logs',
  '/logs/export',
]
const removedPaths = [
  '/systems/data-scopes',
  '/systems/role-data-scopes',
  '/roles/{roleId}/data-scopes',
  '/roles/{roleId}/data-scopes/{bindingId}',
  '/open/authorize/resource',
  '/open/data-scopes',
]

for (const endpoint of requiredPaths) {
  if (!spec.paths[endpoint]) {
    throw new Error(`缺少必需路径: ${endpoint}`)
  }
}
for (const endpoint of removedPaths) {
  if (spec.paths[endpoint]) {
    throw new Error(`仍包含已删除路径: ${endpoint}`)
  }
}

const serialized = JSON.stringify(spec)
const refs = [...serialized.matchAll(/"\$ref":"#\/components\/([^"]+)"/g)]
  .map((match) => match[1])
  .map((value) => value.replaceAll('~1', '/').replaceAll('~0', '~'))

for (const ref of new Set(refs)) {
  const [section, ...parts] = ref.split('/')
  let current = spec.components?.[section]
  for (const part of parts) {
    current = current?.[part]
  }
  if (current === undefined) {
    throw new Error(`悬空引用: #/components/${ref}`)
  }
}

const operationCount = Object.values(spec.paths)
  .reduce((count, item) => count + Object.keys(item).length, 0)
console.log('OpenAPI check passed')
console.log(`paths=${Object.keys(spec.paths).length}`)
console.log(`operations=${operationCount}`)
console.log(`schemas=${Object.keys(spec.components.schemas).length}`)
