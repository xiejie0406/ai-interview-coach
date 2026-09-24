/* 定向交付核查；只读取智能选品项目事实源，只写本原型 verification/document-results.json。 */
'use strict';

const assert = require('node:assert/strict');
const crypto = require('node:crypto');
const fs = require('node:fs');
const path = require('node:path');

const prototypeRoot = __dirname;
const repositoryRoot = path.resolve(prototypeRoot, '../..');
const projectRoot = path.join(repositoryRoot, '文档', '项目', '智能选品项目');
const generatedRoot = path.join(prototypeRoot, 'documents');

const sourceFiles = [
  path.join(projectRoot, '产品', '产品需求文档.md'),
  path.join(projectRoot, '原型', '服装工作台原型设计与生产任务转交.md'),
  path.join(projectRoot, 'README.md'),
  path.join(projectRoot, '原型', '服装工作台原型验证记录.md'),
  path.join(projectRoot, '调研', '2026-09-11-专项市场调研.md'),
  path.join(prototypeRoot, 'README.md'),
  path.join(prototypeRoot, 'verification', 'requirements-map.md'),
  path.join(prototypeRoot, 'assets', 'SOURCES.md'),
];

const markdownLinkPattern =
  /!?\[[^\]\r\n]*\]\((<[^>\r\n]+>|[^)\s]+)(?:\s+["'][^"'\r\n]*["'])?\)/g;
const htmlLinkPattern = /(?:href|src)\s*=\s*["']([^"']+)["']/gi;
const broken = [];
const checkedFiles = [];
let localLinks = 0;

function sha256(content) {
  return crypto.createHash('sha256').update(content).digest('hex');
}

function relativeToRepository(file) {
  return path.relative(repositoryRoot, file).replaceAll('\\', '/');
}

function lineNumberAt(content, index) {
  return content.slice(0, index).split(/\r?\n/).length;
}

function checkTarget(file, content, match, rawTarget) {
  let target = rawTarget.trim();
  if (target.startsWith('<') && target.endsWith('>')) {
    target = target.slice(1, -1);
  }
  const pathname = target.split('#')[0].split('?')[0];
  if (
    !pathname ||
    /^(?:[a-z][a-z\d+.-]*:|#|\/|\\|[a-z]:[\\/])/i.test(pathname)
  ) {
    return;
  }

  localLinks += 1;
  let decodedPathname = pathname;
  try {
    decodedPathname = decodeURIComponent(pathname);
  } catch {
    // 保留原值，由存在性检查给出明确失败。
  }
  const resolvedTarget = path.resolve(path.dirname(file), decodedPathname);
  if (!fs.existsSync(resolvedTarget)) {
    broken.push({
      file: relativeToRepository(file),
      line: lineNumberAt(content, match.index),
      href: target,
    });
  }
}

function checkMarkdown(file) {
  const content = fs.readFileSync(file, 'utf8');
  checkedFiles.push(relativeToRepository(file));
  for (const match of content.matchAll(markdownLinkPattern)) {
    checkTarget(file, content, match, match[1]);
  }
}

function checkHtml(file) {
  const content = fs.readFileSync(file, 'utf8');
  checkedFiles.push(relativeToRepository(file));
  for (const match of content.matchAll(htmlLinkPattern)) {
    checkTarget(file, content, match, match[1]);
  }
}

for (const file of sourceFiles) checkMarkdown(file);

const prd = fs.readFileSync(path.join(projectRoot, '产品', '产品需求文档.md'), 'utf8');
const requirements = [...prd.matchAll(/^### REQ (\d+)/gm)].map((match) => match[1]);
const acceptanceCriteria = [...prd.matchAll(/^\| AC (\d+) 对应/gm)].map((match) => match[1]);
assert.equal(requirements.length, 17);
assert.equal(new Set(requirements).size, 17);
assert.equal(acceptanceCriteria.length, 32);
assert.equal(new Set(acceptanceCriteria).size, 32);

const manifest = JSON.parse(
  fs.readFileSync(path.join(generatedRoot, 'source-manifest.json'), 'utf8'),
);
for (const item of manifest) {
  const canonicalSource = path.join(repositoryRoot, '文档', ...item.source.split('/'));
  const sourceCopy = path.join(generatedRoot, 'sources', item.id + '.md');
  const generatedHtml = path.join(generatedRoot, item.id + '.html');
  assert.equal(sha256(fs.readFileSync(canonicalSource)), item.sha256);
  assert.equal(sha256(fs.readFileSync(sourceCopy)), item.copySha256);
  checkMarkdown(sourceCopy);
  checkHtml(generatedHtml);
}

const report = {
  checkedAt: new Date().toISOString(),
  scope: '智能选品项目事实源及服装原型派生 Markdown/HTML',
  files: checkedFiles.length,
  localLinks,
  broken,
  requirements: requirements.length,
  acceptanceCriteria: acceptanceCriteria.length,
  derivedCopies: manifest.length,
  prdSha256: sha256(prd),
};

fs.writeFileSync(
  path.join(prototypeRoot, 'verification', 'document-results.json'),
  JSON.stringify(report, null, 2) + '\n',
  'utf8',
);
console.log(JSON.stringify(report, null, 2));
assert.equal(broken.length, 0, '存在本地断链');
