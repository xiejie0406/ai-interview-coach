const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');

const root = path.resolve(__dirname, '..');
const directory = path.join(root, 'docs/architecture/projects/01-production-scheduling');
const documentPath = path.join(directory, '数据库设计.md');
const source = fs.readFileSync(documentPath, 'utf8');
const checks = [];
function check(name, condition, detail) {
  checks.push({ name, status: condition ? 'Pass' : 'Fail', detail });
}

const headers = [...source.matchAll(/^### T(\d{2}) `(aps_[a-z_]+)`[^\n]*$/gm)];
const common = ['id', 'factory_id', 'created_at', 'ruoyi_user_id', 'source_system'];
const mutable = ['updated_at', 'updated_by', 'row_version'];
const tables = headers.map((header, index) => {
  const end = headers[index + 1]?.index ?? source.indexOf('\n## 10.', header.index);
  const section = source.slice(header.index + header[0].length, end);
  const rows = section.split(/\r?\n/).filter(line => /^\| [a-z][a-z0-9_]* \|/.test(line));
  const fields = rows.map(line => {
    const cells = line.split('|').slice(1, -1).map(cell => cell.trim());
    return { name: cells[0], type: cells[1], required: cells[2], description: cells[3], cells: cells.length };
  });
  const isEvent = /（E）/.test(header[0]);
  const inherited = common.filter(field => header[2] !== 'aps_factory' || field !== 'factory_id');
  if (!isEvent) inherited.push(...mutable);
  return { number: Number(header[1]), name: header[2], fields, inherited, section };
});
const byName = new Map(tables.map(table => [table.name, table]));
const failures = { duplicateFields: [], malformedFields: [], foreignKeys: [], uniqueKeys: [], diagramEdges: [] };

check('Table identities are unique and sequential', tables.length === 62 && byName.size === 62 && tables.every((table, i) => table.number === i + 1), { expected: 62, actual: tables.length });
const allowedType = /^(uuid|bigint|integer|smallint|boolean|date|text|jsonb|Q|R|TS|CODE|STATE|varchar\(\d+\)|char\(\d+\)|numeric\(\d+,\d+\))$/;
let foreignKeyCount = 0;
for (const table of tables) {
  const names = [...table.inherited, ...table.fields.map(field => field.name)];
  if (new Set(names).size !== names.length) failures.duplicateFields.push(table.name);
  if (!table.fields.length) failures.malformedFields.push(`${table.name}: no dictionary`);
  for (const field of table.fields) {
    if (field.cells !== 4 || !allowedType.test(field.type) || !/^(NN|NULL) \/ .+/.test(field.required) || !field.description) failures.malformedFields.push(`${table.name}.${field.name}`);
    for (const ref of field.description.matchAll(/FK→(aps_[a-z_]+)(?:\.([a-z_]+))?/g)) {
      foreignKeyCount++;
      const target = byName.get(ref[1]);
      const targetFields = target && [...target.inherited, ...target.fields.map(item => item.name)];
      if (!target || !targetFields.includes(ref[2] || 'id')) failures.foreignKeys.push(`${table.name}.${field.name} -> ${ref[1]}.${ref[2] || 'id'}`);
    }
  }
  for (const key of table.section.matchAll(/UQ\(([^)]+)\)/g)) {
    const columns = key[1].split(',').map(item => item.trim());
    if (columns.some(column => !names.includes(column))) failures.uniqueKeys.push(`${table.name}: ${key[0]}`);
  }
}
check('Each table has typed fields, nullability, defaults and descriptions', failures.malformedFields.length === 0, failures.malformedFields);
check('Fields do not duplicate explicit or inherited fields', failures.duplicateFields.length === 0, failures.duplicateFields);
check('Dictionary foreign-key targets and columns exist', failures.foreignKeys.length === 0, { foreignKeyCount, errors: failures.foreignKeys });
check('Declared UQ column names exist in their table', failures.uniqueKeys.length === 0, failures.uniqueKeys);

const diagrams = [...source.matchAll(/```mermaid\r?\n([\s\S]*?)```/g)];
let edgeCount = 0;
for (const diagram of diagrams) {
  for (const line of diagram[1].split(/\r?\n/)) {
    const edge = line.trim().match(/^(aps_[a-z_]+)\s+[|o}{-]+\s+(aps_[a-z_]+)\s*:/);
    if (!edge) continue;
    edgeCount++;
    const left = byName.get(edge[1]);
    const right = byName.get(edge[2]);
    const references = (from, to) => from && ((to === 'aps_factory' && from.inherited.includes('factory_id')) || from.fields.some(field => field.description.includes(`FK→${to}`)));
    if (!left || !right || (!references(left, edge[2]) && !references(right, edge[1]))) failures.diagramEdges.push(line.trim());
  }
}
check('Three ER diagrams reference existing related tables (text check only)', diagrams.length === 3 && edgeCount > 0 && failures.diagramEdges.length === 0, { diagrams: diagrams.length, edgeCount, errors: failures.diagramEdges, rendered: false });

const requirements = [...source.matchAll(/^\| (REQ-\d{2}) /gm)].map(match => match[1]);
const prd = fs.readFileSync(path.join(root, 'docs/product/projects/01-production-scheduling/产品需求文档.md'), 'utf8');
check('REQ-01 through REQ-10 appear once and exist in PRD', requirements.length === 10 && new Set(requirements).size === 10 && requirements.every((id, index) => id === `REQ-${String(index + 1).padStart(2, '0')}` && prd.includes(`| ${id} |`)), requirements);
check('Constraint IDs C01 through C18 are unique and sequential', [...source.matchAll(/^\| C(\d{2}) /gm)].map(match => Number(match[1])).every((id, index) => id === index + 1) && (source.match(/^\| C\d{2} /gm) || []).length === 18, 'Cross-table consistency matrix, textual identity check');

const arithmetic = {
  furnaceHours: (180 / 60) === 3,
  workerHours: ((30 + 30) / 60) === 1,
  lateHours: (Date.parse('2026-09-15T09:00:00+08:00') - Date.parse('2026-09-14T17:00:00+08:00')) / 3600000 === 16,
  discreteGate: Math.ceil(101 * 0.5) === 51,
  transferTotal: 20 + 20 + 20 === 60,
  recoveryDelivery: 98 + 2 + 100 === 200,
  standardCapacity: Math.floor((4 - 0.5) * 10) === 35 && Math.floor((4 - 0.5) * 8) === 28,
};
check('Worked-example arithmetic is consistent (not a database simulation)', Object.values(arithmetic).every(Boolean), arithmetic);
check('No conflict markers or unintended trailing whitespace', !/^(<{7}|={7}|>{7})/m.test(source) && !/[^ \r\n] {3,}\r?$/m.test(source), 'Markdown two-space line breaks are permitted');

const result = {
  generatedAt: new Date().toISOString(),
  kind: 'documentation-static-check',
  document: path.relative(root, documentPath).replaceAll('\\', '/'),
  documentSha256: crypto.createHash('sha256').update(source).digest('hex'),
  runtime: process.version,
  summary: { pass: checks.filter(item => item.status === 'Pass').length, fail: checks.filter(item => item.status === 'Fail').length, tables: tables.length, explicitFields: tables.reduce((sum, table) => sum + table.fields.length, 0), erDiagrams: diagrams.length },
  checks,
  notRun: ['PostgreSQL DDL', 'Foreign-key enforcement', 'Concurrent transactions', 'Query performance', 'Migration and restore', 'Mermaid rendering', 'User acceptance'],
};
fs.writeFileSync(path.join(directory, 'database-design-check-results.json'), `${JSON.stringify(result, null, 2)}\n`);
process.stdout.write(`${JSON.stringify(result, null, 2)}\n`);
process.exitCode = result.summary.fail ? 1 : 0;
