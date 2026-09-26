import fs from 'node:fs'
import path from 'path'

const REGISTER_ID = 'virtual:svg-icons-register'
const NAMES_ID = 'virtual:svg-icons-names'
const RESOLVED_REGISTER_ID = `\0${REGISTER_ID}`
const RESOLVED_NAMES_ID = `\0${NAMES_ID}`
const SVG_DOM_ID = '__svg__icons__dom__'

function listSvgFiles(directory) {
  return fs.readdirSync(directory, { withFileTypes: true })
    .flatMap(entry => {
      const entryPath = path.join(directory, entry.name)
      return entry.isDirectory()
        ? listSvgFiles(entryPath)
        : entry.isFile() && entry.name.endsWith('.svg') ? [entryPath] : []
    })
    .sort((left, right) => left.localeCompare(right))
}

function attribute(attributes, name) {
  const match = attributes.match(new RegExp(`\\b${name}\\s*=\\s*(["'])(.*?)\\1`, 'i'))
  return match?.[2]
}

function numericDimension(attributes, name) {
  const value = attribute(attributes, name)
  return value && /^\d+(?:\.\d+)?(?:px)?$/.test(value)
    ? value.replace(/px$/, '')
    : undefined
}

function symbolId(iconRoot, filename) {
  const relative = path.relative(iconRoot, filename).replaceAll('\\', '/')
  const name = relative.replace(/\.svg$/i, '').replaceAll('/', '-')
  if (!/^[A-Za-z0-9][A-Za-z0-9_-]*$/.test(name)) {
    throw new Error(`SVG 文件名不能生成安全 symbol ID: ${relative}`)
  }
  return `icon-${name}`
}

export function compileSvgSymbol(iconRoot, filename) {
  const content = fs.readFileSync(filename, 'utf8')
  if (/<(?:script|foreignObject)\b|\bon[a-z]+\s*=|(?:xlink:)?href\s*=\s*["'](?:https?:|\/\/|data:|javascript:)/i.test(content)) {
    throw new Error(`SVG 含不允许的可执行或外部内容: ${filename}`)
  }

  const root = content.match(/<svg\b([^>]*)>/i)
  const closingIndex = content.toLowerCase().lastIndexOf('</svg>')
  if (!root || closingIndex < root.index + root[0].length) {
    throw new Error(`SVG 根元素不完整: ${filename}`)
  }

  const attributes = root[1]
  const declaredViewBox = attribute(attributes, 'viewBox')
  const width = numericDimension(attributes, 'width')
  const height = numericDimension(attributes, 'height')
  const viewBox = declaredViewBox ?? (width && height ? `0 0 ${width} ${height}` : undefined)
  if (!viewBox || !/^[0-9eE+.,\s-]+$/.test(viewBox)) {
    throw new Error(`SVG 必须提供安全 viewBox 或数字 width/height: ${filename}`)
  }

  const bodyStart = root.index + root[0].length
  const body = content.slice(bodyStart, closingIndex)
    .replace(/\bstroke="(?:[A-Za-z]+|#[0-9A-Fa-f]{3,8})"/, 'stroke="currentColor"')
  return `<symbol viewBox="${viewBox}" id="${symbolId(iconRoot, filename)}">${body}</symbol>`
}

function createModules(pluginContext, iconRoot) {
  const files = listSvgFiles(iconRoot)
  files.forEach(file => pluginContext.addWatchFile(file))
  const symbols = files.map(file => compileSvgSymbol(iconRoot, file)).join('')
  const names = files.map(file => symbolId(iconRoot, file))
  const registerCode = `
if (typeof window !== 'undefined') {
  function loadSvgSprite() {
    const body = document.body
    let svgDom = document.getElementById(${JSON.stringify(SVG_DOM_ID)})
    if (!svgDom) {
      svgDom = document.createElementNS('http://www.w3.org/2000/svg', 'svg')
      svgDom.id = ${JSON.stringify(SVG_DOM_ID)}
      svgDom.style.position = 'absolute'
      svgDom.style.width = '0'
      svgDom.style.height = '0'
      svgDom.setAttribute('aria-hidden', 'true')
      body.insertBefore(svgDom, body.firstChild)
    }
    svgDom.innerHTML = ${JSON.stringify(symbols)}
  }
  if (document.readyState === 'loading') {
    document.addEventListener('DOMContentLoaded', loadSvgSprite, { once: true })
  } else {
    loadSvgSprite()
  }
}
export default {}
`
  return { registerCode, namesCode: `export default ${JSON.stringify(names)}` }
}

export default function createSvgIcon() {
  const iconRoot = path.resolve(process.cwd(), 'src/assets/icons/svg')
  return {
    name: 'ruoyi-local-svg-sprite',
    enforce: 'pre',
    resolveId(id) {
      if (id === REGISTER_ID) return RESOLVED_REGISTER_ID
      if (id === NAMES_ID) return RESOLVED_NAMES_ID
      return null
    },
    load(id) {
      if (id !== RESOLVED_REGISTER_ID && id !== RESOLVED_NAMES_ID) return null
      const modules = createModules(this, iconRoot)
      return id === RESOLVED_REGISTER_ID ? modules.registerCode : modules.namesCode
    },
    handleHotUpdate({ file, server }) {
      if (!file.endsWith('.svg') || !path.resolve(file).startsWith(`${iconRoot}${path.sep}`)) return
      for (const id of [RESOLVED_REGISTER_ID, RESOLVED_NAMES_ID]) {
        const module = server.moduleGraph.getModuleById(id)
        if (module) server.moduleGraph.invalidateModule(module)
      }
    }
  }
}
