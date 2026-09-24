import { access, readFile, readdir } from 'node:fs/promises'
import { constants } from 'node:fs'
import { resolve } from 'node:path'

const requiredArtifacts = [
  'out/main/index.js',
  'out/preload/index.cjs',
  'out/renderer/index.html'
]

await Promise.all(requiredArtifacts.map((artifact) =>
  access(resolve(artifact), constants.R_OK)
))

const mainBundle = await readFile(resolve('out/main/index.js'), 'utf8')
if (!mainBundle.includes('../preload/index.cjs')) {
  throw new Error('主进程产物没有加载实际生成的 preload/index.cjs')
}

const rendererFiles = await listFiles(resolve('out/renderer'))
const rendererText = (await Promise.all(rendererFiles
  .filter((file) => file.endsWith('.js') || file.endsWith('.html'))
  .map((file) => readFile(file, 'utf8')))).join('\n')
for (const forbidden of ['ipcRenderer', 'Authorization', 'Bearer ', 'ADEN_API_BASE_URL']) {
  if (rendererText.includes(forbidden)) throw new Error(`renderer 产物泄露受限能力标识：${forbidden}`)
}

console.log(`构建产物检查通过：${requiredArtifacts.length} 个入口均可读取`)

async function listFiles(directory) {
  const entries = await readdir(directory, { withFileTypes: true })
  const nested = await Promise.all(entries.map((entry) => {
    const path = resolve(directory, entry.name)
    return entry.isDirectory() ? listFiles(path) : [path]
  }))
  return nested.flat()
}
