const crypto = require('node:crypto');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..');
const designRel = '文档/项目/生产排产项目/架构/数据库设计.md';
const sqlRel = '文档/项目/生产排产项目/架构/生产排程数据库基线草案.sql';
const resultRel = '文档/项目/生产排产项目/架构/证据/数据库设计检查结果.json';
const designPath = path.join(root, designRel);
const sqlPath = path.join(root, sqlRel);
const resultPath = path.join(root, resultRel);

const expectedTables = [
  'aps_workshop',
  'aps_work_center',
  'aps_resource',
  'aps_resource_skill',
  'aps_resource_availability',
  'aps_item',
  'aps_operation_spec',
  'aps_operation_phase',
  'aps_resource_requirement',
  'aps_route_version',
  'aps_route_node',
  'aps_route_edge',
  'aps_order',
  'aps_order_line',
  'aps_production_lot',
  'aps_task',
  'aps_task_dependency',
  'aps_material_demand',
  'aps_plan_version',
  'aps_plan_job',
  'aps_plan_job_member',
  'aps_plan_segment',
  'aps_plan_allocation',
  'aps_plan_lock',
  'aps_execution_run',
  'aps_actual_occupancy',
  'aps_production_report',
  'aps_output_lot',
  'aps_quantity_event'
];

const retiredTables = [
  'aps_factory',
  'aps_skill',
  'aps_resource_center',
  'aps_uom',
  'aps_requirement_skill',
  'aps_task_spec',
  'aps_plan_scope',
  'aps_plan_output',
  'aps_plan_supply',
  'aps_daily_baseline',
  'aps_run_member',
  'aps_supply_reservation',
  'aps_material_consumption',
  'aps_quality_disposition',
  'aps_domain_event',
  'aps_inbox',
  'aps_outbox'
];

const readUtf8 = file => fs.readFileSync(file, 'utf8').replace(/^\uFEFF/, '');
const sha256 = value => crypto.createHash('sha256').update(value, 'utf8').digest('hex');
const cleanName = value => value.replace(/`/g, '').trim().toLowerCase();

function stripSqlComments(sql) {
  return sql
    .replace(/\/\*[\s\S]*?\*\//g, '')
    .replace(/--[^\r\n]*/g, '');
}

function findClosingParenthesis(text, openingIndex) {
  let depth = 0;
  let quote = null;
  for (let index = openingIndex; index < text.length; index += 1) {
    const char = text[index];
    const previous = index > 0 ? text[index - 1] : '';
    if (quote) {
      if (char === quote && previous !== '\\') quote = null;
      continue;
    }
    if (char === '\'' || char === '"' || char === '`') {
      quote = char;
      continue;
    }
    if (char === '(') depth += 1;
    if (char === ')') {
      depth -= 1;
      if (depth === 0) return index;
    }
  }
  return -1;
}

function splitTopLevel(body) {
  const parts = [];
  let start = 0;
  let depth = 0;
  let quote = null;
  for (let index = 0; index < body.length; index += 1) {
    const char = body[index];
    const previous = index > 0 ? body[index - 1] : '';
    if (quote) {
      if (char === quote && previous !== '\\') quote = null;
      continue;
    }
    if (char === '\'' || char === '"' || char === '`') {
      quote = char;
      continue;
    }
    if (char === '(') depth += 1;
    if (char === ')') depth -= 1;
    if (char === ',' && depth === 0) {
      parts.push(body.slice(start, index).trim());
      start = index + 1;
    }
  }
  parts.push(body.slice(start).trim());
  return parts.filter(Boolean);
}

function parseColumnList(value) {
  return value
    .split(',')
    .map(part => cleanName(part.replace(/\s+(ASC|DESC)\b/ig, '').replace(/\(\d+\)/g, '')))
    .filter(Boolean);
}

function parseSql(sql) {
  const executable = stripSqlComments(sql);
  const createPattern = /CREATE\s+TABLE\s+(`?[a-zA-Z0-9_]+`?)\s*\(/ig;
  const tables = [];
  let match;
  while ((match = createPattern.exec(executable)) !== null) {
    const name = cleanName(match[1]);
    const openingIndex = match.index + match[0].lastIndexOf('(');
    const closingIndex = findClosingParenthesis(executable, openingIndex);
    if (closingIndex < 0) {
      tables.push({ name, malformed: 'missing closing parenthesis', columns: new Map(), constraints: [], indexes: [], foreignKeys: [] });
      continue;
    }
    const semicolonIndex = executable.indexOf(';', closingIndex);
    const statementEnd = semicolonIndex < 0 ? closingIndex + 1 : semicolonIndex + 1;
    const body = executable.slice(openingIndex + 1, closingIndex);
    const statement = executable.slice(match.index, statementEnd);
    const parts = splitTopLevel(body);
    const columns = new Map();
    const duplicateColumns = [];
    const constraints = [];
    const indexes = [];
    const foreignKeys = [];

    for (const part of parts) {
      const trimmed = part.trim();
      const constraintMatch = trimmed.match(/^CONSTRAINT\s+(`?[a-zA-Z0-9_]+`?)\s+/i);
      if (constraintMatch) constraints.push(cleanName(constraintMatch[1]));

      const primaryMatch = trimmed.match(/^(?:CONSTRAINT\s+`?[a-zA-Z0-9_]+`?\s+)?PRIMARY\s+KEY\s*\(([^)]+)\)/i);
      const uniqueMatch = trimmed.match(/^(?:CONSTRAINT\s+`?[a-zA-Z0-9_]+`?\s+)?UNIQUE(?:\s+KEY)?(?:\s+`?[a-zA-Z0-9_]+`?)?\s*\(([^)]+)\)/i);
      const keyMatch = trimmed.match(/^KEY\s+`?[a-zA-Z0-9_]+`?\s*\(([^)]+)\)/i);
      if (primaryMatch) indexes.push(parseColumnList(primaryMatch[1]));
      if (uniqueMatch) indexes.push(parseColumnList(uniqueMatch[1]));
      if (keyMatch) indexes.push(parseColumnList(keyMatch[1]));

      const fkMatch = trimmed.match(/^CONSTRAINT\s+(`?[a-zA-Z0-9_]+`?)\s+FOREIGN\s+KEY\s*\(([^)]+)\)\s+REFERENCES\s+(`?[a-zA-Z0-9_]+`?)\s*\(([^)]+)\)/i);
      if (fkMatch) {
        foreignKeys.push({
          name: cleanName(fkMatch[1]),
          localColumns: parseColumnList(fkMatch[2]),
          targetTable: cleanName(fkMatch[3]),
          targetColumns: parseColumnList(fkMatch[4])
        });
      }

      if (/^(CONSTRAINT|PRIMARY\s+KEY|UNIQUE|KEY|FOREIGN\s+KEY|CHECK)\b/i.test(trimmed)) continue;
      const columnMatch = trimmed.match(/^(`?[a-zA-Z_][a-zA-Z0-9_]*`?)\s+([\s\S]+)$/);
      if (!columnMatch) continue;
      const columnName = cleanName(columnMatch[1]);
      if (columns.has(columnName)) duplicateColumns.push(columnName);
      columns.set(columnName, columnMatch[2].trim());
    }

    tables.push({ name, body, statement, columns, duplicateColumns, constraints, indexes, foreignKeys });
    createPattern.lastIndex = statementEnd;
  }
  return { executable, tables };
}

function hasLeftPrefix(indexes, columns) {
  return indexes.some(index => columns.every((column, position) => index[position] === column));
}

const design = readUtf8(designPath);
const sql = readUtf8(sqlPath);
const parsed = parseSql(sql);
const tableMap = new Map(parsed.tables.map(table => [table.name, table]));
const checks = [];

function check(name, ok, detail) {
  checks.push({ name, status: ok ? 'Pass' : 'Fail', detail });
}

const designRows = [...design.matchAll(/^\|\s*M(\d{2})\s*\|\s*`([^`]+)`/gm)]
  .map(match => ({ number: Number(match[1]), table: match[2].toLowerCase() }));
const designTables = [...new Set(designRows.map(row => row.table))];
const expectedNumbers = Array.from({ length: 29 }, (_, index) => index + 1);
check(
  'Design declares one unique M01-M29 table list',
  designRows.length === 29 &&
    JSON.stringify(designRows.map(row => row.number)) === JSON.stringify(expectedNumbers) &&
    JSON.stringify(designTables) === JSON.stringify(expectedTables),
  { rows: designRows.length, numbers: designRows.map(row => row.number), tables: designTables }
);

const sqlTables = parsed.tables.map(table => table.name);
check(
  'SQL CREATE TABLE set and order exactly match the 29-table candidate',
  parsed.tables.length === 29 &&
    new Set(sqlTables).size === 29 &&
    JSON.stringify(sqlTables) === JSON.stringify(expectedTables),
  { createStatements: parsed.tables.length, uniqueTables: new Set(sqlTables).size, tables: sqlTables }
);

const retiredPresent = retiredTables.filter(table => tableMap.has(table));
check('No deleted, merged, or deferred table is recreated', retiredPresent.length === 0, retiredPresent);

const factoryColumns = [];
for (const table of parsed.tables) {
  if (table.columns.has('factory_id')) factoryColumns.push(table.name);
}
check('No current table contains factory_id', factoryColumns.length === 0, factoryColumns);

const skillTables = sqlTables.filter(table => table.includes('skill'));
check(
  'Exactly one physical skill table remains',
  skillTables.length === 1 && skillTables[0] === 'aps_resource_skill',
  skillTables
);

const routeEdgeBody = (tableMap.get('aps_route_edge')?.body || '').replace(/\s+/g, ' ');
const taskDependencyBody = (tableMap.get('aps_task_dependency')?.body || '').replace(/\s+/g, ' ');
const sameStartDesignOk =
  design.includes('M12/M17') &&
  design.includes('`SAME_START`') &&
  design.includes('`UNSUPPORTED_SYNC_RULE`');
const sameStartRouteOk = [
  "dependency_type IN ('FINISH', 'QUANTITY', 'SAME_START')",
  "dependency_type = 'SAME_START'",
  'threshold_qty IS NULL',
  'threshold_ratio IS NULL',
  'transfer_batch_qty IS NULL',
  'lag_seconds = 0',
  'consumes_output = 0'
].every(fragment => routeEdgeBody.includes(fragment));
const sameStartTaskOk = [
  "dependency_type IN ('FINISH', 'QUANTITY', 'SAME_START')",
  "dependency_type = 'SAME_START'",
  'threshold_qty IS NULL',
  'threshold_ratio IS NULL',
  'transfer_batch_qty IS NULL',
  'uom_code IS NULL',
  'lag_seconds = 0',
  'consumes_output = 0'
].every(fragment => taskDependencyBody.includes(fragment));
check(
  'SAME_START is preserved in M12/M17 but constrained as a non-executable P0 source rule',
  sameStartDesignOk && sameStartRouteOk && sameStartTaskOk,
  { design: sameStartDesignOk, routeEdge: sameStartRouteOk, taskDependency: sameStartTaskOk }
);

const malformed = parsed.tables.filter(table => table.malformed).map(table => ({ table: table.name, error: table.malformed }));
const duplicateColumns = parsed.tables.flatMap(table => table.duplicateColumns.map(column => `${table.name}.${column}`));
check('Every CREATE TABLE is structurally closed and has unique columns', malformed.length === 0 && duplicateColumns.length === 0, { malformed, duplicateColumns });

const optionErrors = parsed.tables
  .filter(table => !/ENGINE\s*=\s*InnoDB/i.test(table.statement) || !/DEFAULT\s+CHARSET\s*=\s*utf8mb4/i.test(table.statement))
  .map(table => table.name);
check('Every table uses InnoDB and utf8mb4', optionErrors.length === 0, optionErrors);

const missingPrimaryKeys = parsed.tables.filter(table => !table.indexes.some(index => index.length === 1 && index[0] === 'id')).map(table => table.name);
check('Every table has an id primary/index key', missingPrimaryKeys.length === 0, missingPrimaryKeys);

const duplicateConstraintNames = [];
const overlongConstraintNames = [];
const constraintOwners = new Map();
for (const table of parsed.tables) {
  for (const constraint of table.constraints) {
    if (constraint.length > 64) overlongConstraintNames.push(constraint);
    if (constraintOwners.has(constraint)) duplicateConstraintNames.push({ name: constraint, first: constraintOwners.get(constraint), second: table.name });
    else constraintOwners.set(constraint, table.name);
  }
}
check(
  'Named constraints are globally unique and at most 64 characters',
  duplicateConstraintNames.length === 0 && overlongConstraintNames.length === 0,
  { count: constraintOwners.size, duplicates: duplicateConstraintNames, overlong: overlongConstraintNames }
);

const fkErrors = [];
let foreignKeyCount = 0;
for (const table of parsed.tables) {
  for (const foreignKey of table.foreignKeys) {
    foreignKeyCount += 1;
    const target = tableMap.get(foreignKey.targetTable);
    if (!target) {
      fkErrors.push(`${foreignKey.name}: missing target table ${foreignKey.targetTable}`);
      continue;
    }
    if (foreignKey.localColumns.length !== foreignKey.targetColumns.length) {
      fkErrors.push(`${foreignKey.name}: local/target column count differs`);
      continue;
    }
    for (let index = 0; index < foreignKey.localColumns.length; index += 1) {
      const localColumn = foreignKey.localColumns[index];
      const targetColumn = foreignKey.targetColumns[index];
      const localDefinition = table.columns.get(localColumn);
      const targetDefinition = target.columns.get(targetColumn);
      if (!localDefinition) fkErrors.push(`${foreignKey.name}: missing ${table.name}.${localColumn}`);
      if (!targetDefinition) fkErrors.push(`${foreignKey.name}: missing ${target.name}.${targetColumn}`);
      if (localDefinition && targetDefinition) {
        const uuidDefinition = definition => /CHAR\s*\(\s*36\s*\)/i.test(definition) && /CHARACTER\s+SET\s+ascii/i.test(definition) && /COLLATE\s+ascii_bin/i.test(definition);
        const identifierPair = localColumn === 'id' || targetColumn === 'id' || localColumn.endsWith('_id') || targetColumn.endsWith('_id');
        const baseType = definition => (definition.match(/^([A-Z]+(?:\s*\(\s*\d+(?:\s*,\s*\d+)?\s*\))?(?:\s+UNSIGNED)?)/i) || [null, ''])[1].replace(/\s+/g, '').toUpperCase();
        if (identifierPair && (!uuidDefinition(localDefinition) || !uuidDefinition(targetDefinition))) {
          fkErrors.push(`${foreignKey.name}: FK identifier columns must use CHAR(36) ASCII binary`);
        } else if (!identifierPair && baseType(localDefinition) !== baseType(targetDefinition)) {
          fkErrors.push(`${foreignKey.name}: FK column types differ for ${localColumn}/${targetColumn}`);
        }
      }
    }
    if (!hasLeftPrefix(table.indexes, foreignKey.localColumns)) {
      fkErrors.push(`${foreignKey.name}: no child index left-prefix for ${foreignKey.localColumns.join(',')}`);
    }
  }
}
check('All foreign keys target existing UUID columns and have child indexes', fkErrors.length === 0, { foreignKeyCount, errors: fkErrors });

const planVersion = tableMap.get('aps_plan_version');
const currentMarkerOk = Boolean(
  planVersion &&
  planVersion.columns.has('current_published_marker') &&
  /GENERATED\s+ALWAYS/i.test(planVersion.columns.get('current_published_marker')) &&
  hasLeftPrefix(planVersion.indexes, ['current_published_marker'])
);
check('Plan version enforces at most one current published marker', currentMarkerOk, currentMarkerOk ? 'generated nullable marker with unique key' : 'missing generated marker or unique key');

const reportColumns = tableMap.get('aps_production_report')?.columns || new Map();
check('Local production report has no mandatory Inbox dependency', !reportColumns.has('inbox_id'), [...reportColumns.keys()].filter(name => name.includes('inbox')));

const unsafePatterns = [
  { name: 'DROP', pattern: /\bDROP\b/i },
  { name: 'TRUNCATE', pattern: /\bTRUNCATE\b/i },
  { name: 'DELETE FROM', pattern: /\bDELETE\s+FROM\b/i },
  { name: 'INSERT', pattern: /\bINSERT\s+INTO\b/i },
  { name: 'REPLACE', pattern: /\bREPLACE\s+INTO\b/i },
  { name: 'ALTER TABLE', pattern: /\bALTER\s+TABLE\b/i },
  { name: 'FOREIGN_KEY_CHECKS', pattern: /\bFOREIGN_KEY_CHECKS\b/i },
  { name: 'CREATE DATABASE', pattern: /\bCREATE\s+DATABASE\b/i }
];
const unsafeMatches = unsafePatterns.filter(item => item.pattern.test(parsed.executable)).map(item => item.name);
check('DDL contains no destructive statement, seed, database creation, or FK bypass', unsafeMatches.length === 0, unsafeMatches);

const postgresPatterns = [
  /\bUUID\b/i,
  /\bJSONB\b/i,
  /\bTIMESTAMPTZ\b/i,
  /\bBIGSERIAL\b/i,
  /\bDEFERRABLE\b/i,
  /::[a-z_]+/i
];
const postgresMatches = postgresPatterns.filter(pattern => pattern.test(parsed.executable)).map(pattern => pattern.toString());
check('Executable DDL contains no obvious PostgreSQL-only syntax', postgresMatches.length === 0, postgresMatches);

const hygieneErrors = [];
for (const [name, content] of [['design', design], ['sql', sql]]) {
  if (content.includes('\u0000')) hygieneErrors.push(`${name}: NUL byte`);
  if (/^(<{7}|={7}|>{7})/m.test(content)) hygieneErrors.push(`${name}: conflict marker`);
}
check('Design and SQL contain no NUL bytes or conflict markers', hygieneErrors.length === 0, hygieneErrors);
check('Design is explicitly Draft and SQL is Review-only / Not executed', /文档状态：Draft/.test(design) && /Review-only\s*\/\s*Not executed/.test(sql), { designDraft: /文档状态：Draft/.test(design), sqlReviewOnly: /Review-only\s*\/\s*Not executed/.test(sql) });

const fail = checks.filter(item => item.status === 'Fail').length;
const result = {
  generatedAt: new Date().toISOString(),
  kind: 'aps-database-pilot-static-check',
  sources: {
    design: designRel,
    designSha256: sha256(design),
    sql: sqlRel,
    sqlSha256: sha256(sql)
  },
  runtime: process.version,
  summary: {
    status: fail === 0 ? 'Pass' : 'Fail',
    pass: checks.length - fail,
    fail,
    designTables: designTables.length,
    sqlCreateStatements: parsed.tables.length,
    sqlUniqueTables: new Set(sqlTables).size,
    sqlColumns: parsed.tables.reduce((sum, table) => sum + table.columns.size, 0),
    sqlForeignKeys: foreignKeyCount,
    sqlNamedConstraints: constraintOwners.size
  },
  checks,
  notRun: [
    'MySQL parser or empty-database DDL execution',
    'CHECK and foreign-key rejection against live data',
    'Dependency-cycle and cross-table quantity-conservation service rules',
    'Concurrent solve, publish, report, and recovery transactions',
    'Query-plan and performance verification',
    'Backup, restore, migration, or rollback rehearsal',
    'Application integration, external-system integration, or user acceptance'
  ]
};

if (!process.argv.includes('--no-write')) {
  fs.mkdirSync(path.dirname(resultPath), { recursive: true });
  fs.writeFileSync(resultPath, `${JSON.stringify(result, null, 2)}\n`, 'utf8');
}

console.log(JSON.stringify(result, null, 2));
process.exitCode = fail === 0 ? 0 : 1;
