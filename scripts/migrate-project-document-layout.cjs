'use strict';

const crypto = require('node:crypto');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const apply = process.argv.includes('--apply');
const unexpectedArguments = process.argv.slice(2).filter((argument) => argument !== '--apply');

if (unexpectedArguments.length) {
  throw new Error(`Unsupported arguments: ${unexpectedArguments.join(', ')}`);
}

const name = {
  docs: '\u6587\u6863',
  projects: '\u9879\u76ee',
  ai: 'AI\u9762\u8bd5\u6559\u7ec3',
  production: '01-\u751f\u4ea7\u6392\u7a0b\u4e0e\u8f66\u95f4\u8c03\u5ea6',
  aden: '02-Aden\u684c\u9762\u667a\u80fd\u6267\u884c\u5e73\u53f0',
  fashion: '03-\u670d\u88c5\u667a\u80fd\u9009\u54c1\u4e0e\u62a5\u4ef7',
  product: '\u4ea7\u54c1',
  feature: '\u529f\u80fd',
  architecture: '\u67b6\u6784',
  research: '\u8c03\u7814',
  reference: '\u53c2\u8003',
  decisions: '\u51b3\u7b56',
  records: '\u5f00\u53d1\u8bb0\u5f55',
  license: '\u8bb8\u53ef',
  archive: '\u5f52\u6863',
  specs: '\u89c4\u8303',
  original: '\u539f\u59cb\u9700\u6c42',
};

const sourceProject = {
  production: '01-\u751f\u4ea7\u6392\u7a0b',
  aden: '02-Agent\u684c\u9762\u6267\u884c',
  fashion: '03-AI\u670d\u88c5\u642d\u914d\u62a5\u4ef7',
};

const docsRoot = path.join(root, name.docs);
const wordRoot = path.join(root, '0word\u9700\u6c42\u6587\u6863');
const projectRoot = path.join(docsRoot, name.projects);
const targetProject = {
  ai: path.join(projectRoot, name.ai),
  production: path.join(projectRoot, name.production),
  aden: path.join(projectRoot, name.aden),
  fashion: path.join(projectRoot, name.fashion),
};

function toPosix(value) {
  return value.replaceAll('\\', '/');
}

function relative(value) {
  return toPosix(path.relative(root, value));
}

function key(value) {
  return path.resolve(value).toLowerCase();
}

function isInside(base, candidate) {
  const relativePath = path.relative(path.resolve(base), path.resolve(candidate));
  return relativePath === '' || (!relativePath.startsWith('..') && !path.isAbsolute(relativePath));
}

function walkFiles(directory, output = []) {
  if (!fs.existsSync(directory)) return output;
  for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
    const fullPath = path.join(directory, entry.name);
    if (entry.isDirectory()) walkFiles(fullPath, output);
    if (entry.isFile()) output.push(fullPath);
  }
  return output;
}

function walkDirectories(directory, output = []) {
  if (!fs.existsSync(directory)) return output;
  for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
    if (!entry.isDirectory()) continue;
    const fullPath = path.join(directory, entry.name);
    output.push(fullPath);
    walkDirectories(fullPath, output);
  }
  return output;
}

function joinRelative(...segments) {
  return toPosix(path.join(...segments));
}

const projectRelative = {
  ai: joinRelative(name.projects, name.ai),
  production: joinRelative(name.projects, name.production),
  aden: joinRelative(name.projects, name.aden),
  fashion: joinRelative(name.projects, name.fashion),
};

const exactMoves = new Map([
  [
    joinRelative(name.research, name.projects, 'README.md'),
    joinRelative(name.projects, 'README.md'),
  ],
  [
    joinRelative(name.product, name.projects, sourceProject.aden, 'README.md'),
    joinRelative(projectRelative.aden, 'README.md'),
  ],
  [
    joinRelative(name.product, name.projects, sourceProject.fashion, 'README.md'),
    joinRelative(projectRelative.fashion, 'README.md'),
  ],
  [
    joinRelative(name.records, '2026-09-11-\u751f\u4ea7\u6392\u7a0b\u6570\u636e\u5e9346\u8868\u6536\u655b.md'),
    joinRelative(projectRelative.production, name.records, '2026-09-11-\u751f\u4ea7\u6392\u7a0b\u6570\u636e\u5e9346\u8868\u6536\u655b.md'),
  ],
  [
    joinRelative(name.decisions, 'PROJ-FASHION-003-\u5de5\u7a0b\u4e0eAI\u8fb9\u754c\u51b3\u7b56.md'),
    joinRelative(projectRelative.fashion, name.decisions, 'PROJ-FASHION-003-\u5de5\u7a0b\u4e0eAI\u8fb9\u754c\u51b3\u7b56.md'),
  ],
]);

const prefixRules = [
  [joinRelative(name.product, name.projects, sourceProject.production), joinRelative(projectRelative.production, name.product)],
  [joinRelative(name.product, name.projects, sourceProject.aden), joinRelative(projectRelative.aden, name.product)],
  [joinRelative(name.product, name.projects, sourceProject.fashion), joinRelative(projectRelative.fashion, name.product)],
  [joinRelative(name.feature, 'FEAT-ADEN-001-\u684c\u9762\u6267\u884c\u5e95\u5ea7'), joinRelative(projectRelative.aden, name.feature, 'FEAT-ADEN-001-\u684c\u9762\u6267\u884c\u5e95\u5ea7')],
  [joinRelative(name.feature, 'FEAT-FASHION-001-\u670d\u88c5\u667a\u80fd\u9009\u54c1\u751f\u4ea7\u9996\u7248'), joinRelative(projectRelative.fashion, name.feature, 'FEAT-FASHION-001-\u670d\u88c5\u667a\u80fd\u9009\u54c1\u751f\u4ea7\u9996\u7248')],
  [joinRelative(name.architecture, name.projects, sourceProject.production), joinRelative(projectRelative.production, name.architecture)],
  [joinRelative(name.architecture, name.projects, sourceProject.aden), joinRelative(projectRelative.aden, name.architecture)],
  [joinRelative(name.architecture, name.projects, sourceProject.fashion), joinRelative(projectRelative.fashion, name.architecture)],
  [joinRelative(name.research, name.projects, sourceProject.production), joinRelative(projectRelative.production, name.research)],
  [joinRelative(name.research, name.projects, sourceProject.aden), joinRelative(projectRelative.aden, name.research)],
  [joinRelative(name.research, name.projects, sourceProject.fashion), joinRelative(projectRelative.fashion, name.research)],
  [joinRelative(name.archive, name.projects, sourceProject.production), joinRelative(projectRelative.production, name.archive)],
  [joinRelative(name.archive, name.projects, sourceProject.fashion), joinRelative(projectRelative.fashion, name.archive)],
  [name.product, joinRelative(projectRelative.ai, name.product)],
  [name.feature, joinRelative(projectRelative.ai, name.feature)],
  [name.architecture, joinRelative(projectRelative.ai, name.architecture)],
  [name.research, joinRelative(projectRelative.ai, name.research)],
  [name.reference, joinRelative(projectRelative.ai, name.reference)],
  [name.decisions, joinRelative(projectRelative.ai, name.decisions)],
  [name.license, joinRelative(projectRelative.ai, name.license)],
  [name.records, joinRelative(projectRelative.ai, name.records)],
  [name.archive, joinRelative(projectRelative.ai, name.archive)],
].sort((left, right) => right[0].length - left[0].length);

const keptDocuments = new Set([
  'README.md',
  joinRelative(name.archive, 'README.md'),
  joinRelative(name.records, 'README.md'),
  joinRelative(name.records, '2026-09-10-\u6587\u6863\u7ed3\u6784\u6536\u655b.md'),
  joinRelative(name.records, '2026-09-11-\u6587\u6863\u76ee\u5f55\u4e2d\u6587\u547d\u540d\u6620\u5c04.csv'),
  joinRelative(name.records, '2026-09-11-\u6587\u6863\u4e2d\u6587\u547d\u540d\u8fc1\u79fb.md'),
  joinRelative(name.records, '2026-09-11-\u6587\u6863\u4e2d\u6587\u547d\u540d\u6620\u5c04.csv'),
]);

function classifyDocument(documentPath) {
  const documentRelative = toPosix(path.relative(docsRoot, documentPath));
  if (keptDocuments.has(documentRelative)
      || documentRelative === name.specs
      || documentRelative.startsWith(`${name.specs}/`)) {
    return null;
  }
  if (exactMoves.has(documentRelative)) {
    return path.join(docsRoot, exactMoves.get(documentRelative));
  }
  for (const [sourcePrefix, targetPrefix] of prefixRules) {
    if (documentRelative === sourcePrefix || documentRelative.startsWith(`${sourcePrefix}/`)) {
      const suffix = documentRelative.slice(sourcePrefix.length).replace(/^\//, '');
      return path.join(docsRoot, targetPrefix, suffix);
    }
  }
  throw new Error(`Unknown document path: ${relative(documentPath)}`);
}

const wordMoves = new Map([
  [
    '\u751f\u4ea7\u6392\u4ea7\u7cfb\u7edf\u9700\u6c42\u6587\u6863_PRD_V1.0.docx',
    path.join(targetProject.production, name.original, '\u751f\u4ea7\u6392\u4ea7\u7cfb\u7edf\u9700\u6c42\u6587\u6863_PRD_V1.0.docx'),
  ],
  [
    'Aden_Agent\u684c\u9762\u667a\u80fd\u6267\u884c\u5e73\u53f0_PRD_V1.0.docx',
    path.join(targetProject.aden, name.original, 'Aden_Agent\u684c\u9762\u667a\u80fd\u6267\u884c\u5e73\u53f0_PRD_V1.0.docx'),
  ],
  [
    'AI\u670d\u88c5\u667a\u80fd\u642d\u914d\u4e0e\u62a5\u4ef7\u7cfb\u7edf_\u9700\u6c42\u6587\u6863.docx',
    path.join(targetProject.fashion, name.original, 'AI\u670d\u88c5\u667a\u80fd\u642d\u914d\u4e0e\u62a5\u4ef7\u7cfb\u7edf_\u9700\u6c42\u6587\u6863.docx'),
  ],
]);

const documentFiles = walkFiles(docsRoot).sort();
const moves = [];
for (const source of documentFiles) {
  const target = classifyDocument(source);
  if (target) moves.push({ source, target, kind: 'document' });
}

const actualWordFiles = walkFiles(wordRoot).sort();
const expectedWordSources = new Set([...wordMoves.keys()].map((fileName) => key(path.join(wordRoot, fileName))));
for (const source of actualWordFiles) {
  if (!expectedWordSources.has(key(source))) {
    throw new Error(`Unknown original requirement file: ${relative(source)}`);
  }
}
for (const [fileName, target] of wordMoves) {
  const source = path.join(wordRoot, fileName);
  if (!fs.existsSync(source)) throw new Error(`Missing original requirement file: ${relative(source)}`);
  moves.push({ source, target, kind: 'original-requirement' });
}

const allowedEmptyDirectory = path.join(
  docsRoot,
  name.research,
  name.projects,
  sourceProject.fashion,
  '\u5bfc\u51fa',
);
for (const directory of walkDirectories(docsRoot)) {
  if (fs.readdirSync(directory).length !== 0) continue;
  if (key(directory) !== key(allowedEmptyDirectory)) {
    throw new Error(`Unknown empty document directory: ${relative(directory)}`);
  }
}

const sourceKeys = new Set();
const targetKeys = new Set();
for (const move of moves) {
  if (!isInside(root, move.source) || !isInside(root, move.target)) {
    throw new Error(`Move escapes workspace: ${relative(move.source)} -> ${relative(move.target)}`);
  }
  if (sourceKeys.has(key(move.source))) throw new Error(`Duplicate source: ${relative(move.source)}`);
  if (targetKeys.has(key(move.target))) throw new Error(`Duplicate target: ${relative(move.target)}`);
  sourceKeys.add(key(move.source));
  targetKeys.add(key(move.target));
  if (fs.existsSync(move.target)) throw new Error(`Occupied target: ${relative(move.target)}`);
  move.sha256 = crypto.createHash('sha256').update(fs.readFileSync(move.source)).digest('hex');
}

const movedBySource = new Map(moves.map((move) => [key(move.source), move.target]));
const keptAbsolute = new Set([...keptDocuments].map((item) => key(path.join(docsRoot, item))));
for (const file of documentFiles.filter((file) => isInside(path.join(docsRoot, name.specs), file))) {
  keptAbsolute.add(key(file));
}

const absolutePrefixRules = prefixRules.map(([sourcePrefix, targetPrefix]) => [
  path.join(docsRoot, sourcePrefix),
  path.join(docsRoot, targetPrefix),
]);

function mapReferencedPath(sourcePath) {
  const exact = movedBySource.get(key(sourcePath));
  if (exact) return exact;
  if (keptAbsolute.has(key(sourcePath))) return sourcePath;
  for (const [sourcePrefix, targetPrefix] of absolutePrefixRules) {
    if (key(sourcePath) === key(sourcePrefix) || isInside(sourcePrefix, sourcePath)) {
      return path.join(targetPrefix, path.relative(sourcePrefix, sourcePath));
    }
  }
  return sourcePath;
}

function splitLinkTarget(rawTarget) {
  const wrapped = rawTarget.startsWith('<') && rawTarget.endsWith('>');
  const value = wrapped ? rawTarget.slice(1, -1) : rawTarget;
  const tailIndex = value.search(/[?#]/);
  return {
    wrapped,
    pathname: tailIndex === -1 ? value : value.slice(0, tailIndex),
    tail: tailIndex === -1 ? '' : value.slice(tailIndex),
  };
}

function rewriteTarget(rawTarget, oldSource, newSource) {
  const { wrapped, pathname, tail } = splitLinkTarget(rawTarget);
  if (!pathname || /^(?:[a-z][a-z\d+.-]*:|#|\/|\\|[a-z]:[\\/])/i.test(pathname)) {
    return rawTarget;
  }
  let decoded = pathname;
  try {
    decoded = decodeURIComponent(pathname);
  } catch {
    // Keep the original path text when percent-encoding is malformed.
  }
  const oldTarget = path.resolve(path.dirname(oldSource), decoded);
  const newTarget = mapReferencedPath(oldTarget);
  let rewritten = toPosix(path.relative(path.dirname(newSource), newTarget));
  if (!rewritten) rewritten = './';
  const value = `${rewritten}${tail}`;
  return wrapped ? `<${value}>` : value;
}

const linkFilePaths = [
  path.join(root, 'README.md'),
  path.join(root, 'AGENTS.md'),
  path.join(root, 'platform-backend', 'AI-MIGRATION.md'),
  ...walkFiles(docsRoot),
  ...walkFiles(path.join(root, 'prototype')),
].filter((file, index, all) => all.indexOf(file) === index
  && ['.md', '.html', '.htm'].includes(path.extname(file).toLowerCase()));

const linkFiles = linkFilePaths.map((oldSource) => ({
  oldSource,
  newSource: movedBySource.get(key(oldSource)) || oldSource,
  content: fs.readFileSync(oldSource, 'utf8'),
}));

const markdownLink = /(!?\[[^\]\r\n]*\]\()(<[^>\r\n]+>|[^)\s]+)((?:\s+["'][^"'\r\n]*["'])?\))/g;
const htmlLink = /((?:href|src)\s*=\s*["'])([^"']+)(["'])/gi;

function rewriteLinks(record) {
  let changes = 0;
  const pattern = ['.html', '.htm'].includes(path.extname(record.oldSource).toLowerCase())
    ? htmlLink
    : markdownLink;
  const content = record.content.replace(pattern, (full, prefix, target, suffix) => {
    const rewritten = rewriteTarget(target, record.oldSource, record.newSource);
    if (rewritten !== target) changes += 1;
    return `${prefix}${rewritten}${suffix}`;
  });
  return { content, changes };
}

function removeEmptyDirectories(directory, preserveRoot = true) {
  if (!fs.existsSync(directory)) return;
  for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
    if (entry.isDirectory()) removeEmptyDirectories(path.join(directory, entry.name), false);
  }
  if (!preserveRoot && fs.readdirSync(directory).length === 0) fs.rmdirSync(directory);
}

function quoteCsv(value) {
  return `"${String(value).replaceAll('"', '""')}"`;
}

const preview = {
  mode: apply ? 'apply' : 'dry-run',
  documentFiles: documentFiles.length,
  keptDocumentFiles: documentFiles.length - moves.filter((move) => move.kind === 'document').length,
  movedDocumentFiles: moves.filter((move) => move.kind === 'document').length,
  originalRequirementFiles: moves.filter((move) => move.kind === 'original-requirement').length,
  totalMoves: moves.length,
  targetProjects: Object.fromEntries(Object.entries(targetProject).map(([project, value]) => [project, relative(value)])),
  moves: moves.map((move) => ({
    source: relative(move.source),
    target: relative(move.target),
    kind: move.kind,
    sha256: move.sha256,
  })),
};

if (!apply) {
  process.stdout.write(`${JSON.stringify(preview, null, 2)}\n`);
  process.exit(0);
}

for (const move of moves) {
  fs.mkdirSync(path.dirname(move.target), { recursive: true });
  fs.renameSync(move.source, move.target);
}

let changedLinkFiles = 0;
let changedLinks = 0;
for (const record of linkFiles) {
  const rewritten = rewriteLinks(record);
  if (rewritten.changes === 0) continue;
  fs.writeFileSync(record.newSource, rewritten.content, 'utf8');
  changedLinkFiles += 1;
  changedLinks += rewritten.changes;
}

const mappingPath = path.join(
  docsRoot,
  name.records,
  '2026-09-12-\u9879\u76ee\u6587\u6863\u5f52\u7c7b\u6620\u5c04.csv',
);
const mappingHeader = ['\u65e7\u8def\u5f84', '\u65b0\u8def\u5f84', '\u7c7b\u578b', '\u8fc1\u79fb\u524dSHA-256'];
const mappingLines = [mappingHeader, ...moves.map((move) => [
  relative(move.source),
  relative(move.target),
  move.kind,
  move.sha256,
])].map((row) => row.map(quoteCsv).join(','));
fs.writeFileSync(mappingPath, `\uFEFF${mappingLines.join('\r\n')}\r\n`, 'utf8');

removeEmptyDirectories(docsRoot);
removeEmptyDirectories(wordRoot, false);

process.stdout.write(`${JSON.stringify({
  ...preview,
  mapping: relative(mappingPath),
  changedLinkFiles,
  changedLinks,
}, null, 2)}\n`);
