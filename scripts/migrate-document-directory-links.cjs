'use strict';

const fs = require('node:fs');
const path = require('node:path');

const workspaceRoot = path.resolve(__dirname, '..');
const reverse = process.argv.includes('--reverse');
const apply = process.argv.includes('--apply');

const forwardSegments = new Map([
  ['docs', '文档'],
  ['architecture', '架构'],
  ['commercial-interview-contracts', '商业面试契约'],
  ['projects', '项目'],
  ['01-production-scheduling', '01-生产排程'],
  ['02-agent-desktop-execution', '02-Agent桌面执行'],
  ['03-ai-fashion-styling-quotation', '03-AI服装搭配报价'],
  ['archive', '归档'],
  ['features', '功能'],
  ['FEAT-RUOYI-LOGIN-001-homepage-login', 'FEAT-RUOYI-LOGIN-001-首页登录'],
  ['phases', '阶段'],
  ['window-prompts', '窗口提示词'],
  ['product', '产品'],
  ['prototypes', '原型'],
  ['FEAT-INTERVIEW-001', 'FEAT-INTERVIEW-001-面试教练原型'],
  ['reference', '参考'],
  ['review', '评审'],
  ['setup', '环境配置'],
  ['specs', '规范'],
  ['decisions', '决策'],
  ['development-records', '开发记录'],
  ['FEAT-INTERVIEW-001-ai-interview-coach', 'FEAT-INTERVIEW-001-AI面试教练'],
  ['FEAT-INTERVIEW-002-commercial-pilot', 'FEAT-INTERVIEW-002-商业邀测'],
  ['contracts', '契约'],
  ['evidence', '证据'],
  ['FEAT-QBANK-001-public-catalog', 'FEAT-QBANK-001-公共题库'],
  ['FEAT-RUOYI-PORTAL-LOGIN-001', 'FEAT-RUOYI-PORTAL-LOGIN-001-门户登录'],
  ['licenses', '许可'],
  ['research', '调研'],
  ['exports', '导出'],
]);

const segmentMap = reverse
  ? new Map([...forwardSegments].map(([oldName, newName]) => [newName, oldName]))
  : forwardSegments;
const currentDocumentRoot = path.join(workspaceRoot, reverse ? '文档' : 'docs');
const futureDocumentRoot = path.join(workspaceRoot, reverse ? 'docs' : '文档');

function isInside(root, candidate) {
  const normalizedRoot = path.resolve(root).toLowerCase();
  const normalizedCandidate = path.resolve(candidate).toLowerCase();
  return normalizedCandidate === normalizedRoot
    || normalizedCandidate.startsWith(`${normalizedRoot}${path.sep}`);
}

function walk(directory, extensions, output) {
  if (!fs.existsSync(directory)) return;
  for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
    if (entry.name === 'node_modules' || entry.name === 'vendor' || entry.name === 'target') continue;
    const fullPath = path.join(directory, entry.name);
    if (entry.isDirectory()) walk(fullPath, extensions, output);
    if (entry.isFile() && extensions.has(path.extname(entry.name).toLowerCase())) output.push(fullPath);
  }
}

function splitTarget(rawTarget) {
  const wrapped = rawTarget.startsWith('<') && rawTarget.endsWith('>');
  const value = wrapped ? rawTarget.slice(1, -1) : rawTarget;
  const tailIndex = value.search(/[?#]/);
  const pathname = tailIndex === -1 ? value : value.slice(0, tailIndex);
  const tail = tailIndex === -1 ? '' : value.slice(tailIndex);
  return { wrapped, pathname, tail };
}

function convertTarget(rawTarget, sourceFile) {
  const { wrapped, pathname, tail } = splitTarget(rawTarget);
  if (!pathname
      || /^(?:[a-z][a-z\d+.-]*:|#|\/|\\|[a-z]:[\\/])/i.test(pathname)) {
    return rawTarget;
  }

  let decodedPathname;
  try {
    decodedPathname = decodeURIComponent(pathname);
  } catch {
    decodedPathname = pathname;
  }
  const resolvedTarget = path.resolve(path.dirname(sourceFile), decodedPathname);
  if (!isInside(currentDocumentRoot, resolvedTarget)
      && !isInside(futureDocumentRoot, resolvedTarget)) {
    return rawTarget;
  }

  const convertedPathname = pathname
    .split(/([/\\])/)
    .map((segment) => segmentMap.get(segment) || segment)
    .join('');
  const converted = `${convertedPathname}${tail}`;
  return wrapped ? `<${converted}>` : converted;
}

const targetFiles = [];
for (const fixedPath of [
  path.join(workspaceRoot, 'README.md'),
  path.join(workspaceRoot, 'AGENTS.md'),
  path.join(workspaceRoot, 'ruoyi-backend', 'AI-MIGRATION.md'),
]) {
  if (fs.existsSync(fixedPath)) targetFiles.push(fixedPath);
}
walk(currentDocumentRoot, new Set(['.md', '.html']), targetFiles);
walk(path.join(workspaceRoot, 'prototype'), new Set(['.md', '.html']), targetFiles);

const uniqueFiles = [...new Set(targetFiles)].sort();
const markdownLink = /(!?\[[^\]\r\n]*\]\()(<[^>\r\n]+>|[^)\s]+)((?:\s+["'][^"'\r\n]*["'])?\))/g;
const htmlLink = /((?:href|src)\s*=\s*["'])([^"']+)(["'])/gi;
let changedLinks = 0;
const changedFiles = [];

for (const file of uniqueFiles) {
  const source = fs.readFileSync(file, 'utf8');
  let fileChanges = 0;
  const pattern = path.extname(file).toLowerCase() === '.html' ? htmlLink : markdownLink;
  const updated = source.replace(pattern, (full, prefix, target, suffix) => {
    const converted = convertTarget(target, file);
    if (converted !== target) fileChanges += 1;
    return `${prefix}${converted}${suffix}`;
  });
  if (fileChanges === 0) continue;
  changedLinks += fileChanges;
  changedFiles.push({
    path: path.relative(workspaceRoot, file).replaceAll('\\', '/'),
    links: fileChanges,
  });
  if (apply) fs.writeFileSync(file, updated, 'utf8');
}

console.log(JSON.stringify({
  mode: reverse ? 'reverse' : 'forward',
  apply,
  scannedFiles: uniqueFiles.length,
  changedFiles: changedFiles.length,
  changedLinks,
  files: changedFiles,
}, null, 2));
