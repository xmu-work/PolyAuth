import fs from 'node:fs'
import path from 'node:path'
import {fileURLToPath} from 'node:url'

const token = process.env.APIFOX_ACCESS_TOKEN
if (!token) {
  throw new Error('缺少 APIFOX_ACCESS_TOKEN 环境变量')
}

const projectArg = process.argv.find((arg) => arg.startsWith('--project='))
const projectId = projectArg?.slice('--project='.length) || '8971596'
const projectRoot = path.resolve(
  path.dirname(fileURLToPath(import.meta.url)),
  '..',
  '..',
)
const specPath = path.join(projectRoot, 'api', 'openapi.json')
const specText = fs.readFileSync(specPath, 'utf8')
JSON.parse(specText)

const response = await fetch(
  `https://api.apifox.com/v1/projects/${projectId}/import-openapi?locale=zh-CN`,
  {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${token}`,
      'X-Apifox-Api-Version': '2024-03-28',
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({
      input: specText,
      options: {
        endpointOverwriteBehavior: 'OVERWRITE_EXISTING',
        schemaOverwriteBehavior: 'OVERWRITE_EXISTING',
        updateFolderOfChangedEndpoint: false,
        prependBasePath: false,
        deleteUnmatchedResources: true,
      },
    }),
  },
)

const responseText = await response.text()
if (!response.ok) {
  throw new Error(`Apifox import failed: HTTP ${response.status} ${responseText}`)
}

const payload = JSON.parse(responseText)
const counters = payload.data?.counters
if (!counters) {
  throw new Error(`Apifox import response missing counters: ${responseText}`)
}

console.log(JSON.stringify(counters, null, 2))
if (
  counters.endpointFailed > 0 ||
  counters.schemaFailed > 0 ||
  counters.endpointFolderFailed > 0 ||
  counters.schemaFolderFailed > 0
) {
  throw new Error('Apifox import completed with failures')
}
