// 历史归档：46 表数据库设计静态检查器。
const crypto = require('node:crypto');
const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..', '..', '..', '..', '..', '..');
const architectureDirectory = path.resolve(__dirname, '..');
const designPath = path.join(architectureDirectory, '数据库设计-46表草案.md');
const sqlPath = path.join(architectureDirectory, '生产排程数据库基线草案-46表.sql');
const resultPath = path.join(__dirname, '数据库设计检查结果-46表.json');
const shouldWriteResult = !process.argv.slice(2).includes('--no-write');

const designSource = fs.readFileSync(designPath, 'utf8').replace(/^\uFEFF/, '');
const sqlSource = fs.readFileSync(sqlPath, 'utf8').replace(/^\uFEFF/, '');
const checks = [];

function addCheck(name, condition, detail) {
  checks.push({
    name,
    status: condition ? 'Pass' : 'Fail',
    detail,
  });
}

function sha256(source) {
  return crypto.createHash('sha256').update(source).digest('hex');
}

function relativePath(filePath) {
  return path.relative(root, filePath).replaceAll('\\', '/');
}

function sorted(values) {
  return [...values].sort((left, right) => left.localeCompare(right, 'en'));
}

function sameSet(left, right) {
  return left.size === right.size && [...left].every((value) => right.has(value));
}

function lineNumberAt(source, index) {
  return source.slice(0, index).split('\n').length;
}

function compactExcerpt(source, index, length = 100) {
  const lineStart = source.lastIndexOf('\n', index - 1) + 1;
  const nextLine = source.indexOf('\n', index);
  const lineEnd = nextLine === -1 ? source.length : nextLine;
  return source.slice(lineStart, lineEnd).trim().slice(0, length);
}

/**
 * Replaces comments and quoted strings with spaces while preserving newlines and
 * source indexes. Backtick identifiers deliberately remain visible to parsers.
 */
function maskSqlCommentsAndStrings(source) {
  const characters = [...source];
  let index = 0;
  let state = 'code';

  const mask = (position) => {
    if (characters[position] !== '\n' && characters[position] !== '\r') {
      characters[position] = ' ';
    }
  };

  while (index < characters.length) {
    const current = characters[index];
    const next = characters[index + 1];

    if (state === 'code') {
      const startsDashComment = current === '-' && next === '-'
        && (index === 0 || /\s/.test(characters[index - 1]))
        && (characters[index + 2] === undefined || /\s/.test(characters[index + 2]));
      if (startsDashComment) {
        mask(index);
        mask(index + 1);
        index += 2;
        state = 'line-comment';
        continue;
      }
      if (current === '#') {
        mask(index);
        index += 1;
        state = 'line-comment';
        continue;
      }
      if (current === '/' && next === '*') {
        mask(index);
        mask(index + 1);
        index += 2;
        state = 'block-comment';
        continue;
      }
      if (current === "'") {
        mask(index);
        index += 1;
        state = 'single-quote';
        continue;
      }
      if (current === '"') {
        mask(index);
        index += 1;
        state = 'double-quote';
        continue;
      }
      index += 1;
      continue;
    }

    if (state === 'line-comment') {
      mask(index);
      if (current === '\n') state = 'code';
      index += 1;
      continue;
    }

    if (state === 'block-comment') {
      mask(index);
      if (current === '*' && next === '/') {
        mask(index + 1);
        index += 2;
        state = 'code';
      } else {
        index += 1;
      }
      continue;
    }

    const quote = state === 'single-quote' ? "'" : '"';
    mask(index);
    if (current === '\\' && next !== undefined) {
      mask(index + 1);
      index += 2;
      continue;
    }
    if (current === quote && next === quote) {
      mask(index + 1);
      index += 2;
      continue;
    }
    if (current === quote) state = 'code';
    index += 1;
  }

  return characters.join('');
}

function findMatchingParenthesis(source, openingIndex) {
  let depth = 0;
  for (let index = openingIndex; index < source.length; index += 1) {
    if (source[index] === '(') depth += 1;
    if (source[index] === ')') {
      depth -= 1;
      if (depth === 0) return index;
    }
  }
  return -1;
}

function splitTopLevelSegments(original, masked) {
  const segments = [];
  let depth = 0;
  let start = 0;
  for (let index = 0; index < masked.length; index += 1) {
    if (masked[index] === '(') depth += 1;
    if (masked[index] === ')') depth -= 1;
    if (masked[index] === ',' && depth === 0) {
      segments.push({
        original: original.slice(start, index),
        masked: masked.slice(start, index),
      });
      start = index + 1;
    }
  }
  segments.push({ original: original.slice(start), masked: masked.slice(start) });
  return segments;
}

function parseIdentifierList(source) {
  return source
    .split(',')
    .map((value) => value.trim().match(/^`?([a-z][a-z0-9_]*)`?/i)?.[1]?.toLowerCase())
    .filter(Boolean);
}

function normalizeSqlType(definition) {
  const match = definition.trim().match(/^([a-z]+)\s*(?:\(\s*([0-9]+)\s*(?:,\s*([0-9]+)\s*)?\))?/i);
  if (!match) return null;
  let base = match[1].toLowerCase();
  const first = match[2];
  const second = match[3];
  if (base === 'int') base = 'integer';
  if (base === 'bool') base = 'boolean';
  if (base === 'numeric') base = 'decimal';
  if (base === 'tinyint' && first === '1') return 'boolean';
  if (['char', 'varchar'].includes(base) && first) return `${base}(${first})`;
  if (['decimal', 'datetime'].includes(base) && first) {
    return second ? `${base}(${first},${second})` : `${base}(${first})`;
  }
  return base;
}

function normalizeDesignType(type) {
  const aliases = {
    ID: 'char(36)',
    Q: 'decimal(18,6)',
    R: 'decimal(18,9)',
    TS: 'datetime(3)',
    CODE: 'varchar(64)',
    STATE: 'varchar(32)',
  };
  const trimmed = type.trim();
  if (aliases[trimmed]) return aliases[trimmed];
  const lower = trimmed.toLowerCase().replace(/\s+/g, '');
  if (lower === 'int') return 'integer';
  if (lower === 'bool') return 'boolean';
  if (lower === 'numeric') return 'decimal';
  return lower;
}

function parseDesign(source) {
  const headerPattern = /^###\s+T(\d{2})\s+`(aps_[a-z0-9_]+)`[^\n]*[（(]([MVE])[）)]\s*$/gm;
  const headers = [...source.matchAll(headerPattern)];
  return headers.map((header, index) => {
    const nextTableIndex = headers[index + 1]?.index ?? source.length;
    const nextHeadingOffset = source.slice(header.index + header[0].length, nextTableIndex)
      .search(/^##\s+/m);
    const sectionEnd = nextHeadingOffset === -1
      ? nextTableIndex
      : header.index + header[0].length + nextHeadingOffset;
    const section = source.slice(header.index + header[0].length, sectionEnd);
    const fields = [];

    for (const line of section.split(/\r?\n/)) {
      if (!/^\|\s*[a-z][a-z0-9_]*\s*\|/.test(line)) continue;
      const cells = line.split('|').slice(1, -1).map((cell) => cell.trim());
      if (cells.length !== 4) continue;
      const [name, type, required, description] = cells;
      fields.push({
        name,
        type,
        normalizedType: normalizeDesignType(type),
        nullable: required.startsWith('NULL'),
        required,
        description,
      });
    }

    return {
      number: Number(header[1]),
      name: header[2],
      kind: header[3],
      line: lineNumberAt(source, header.index),
      explicitFields: fields,
      section,
    };
  });
}

const inheritedBaseFields = [
  { name: 'id', type: 'ID', normalizedType: 'char(36)', nullable: false },
  { name: 'created_at', type: 'TS', normalizedType: 'datetime(3)', nullable: false },
  { name: 'ruoyi_user_id', type: 'bigint', normalizedType: 'bigint', nullable: true },
  { name: 'source_system', type: 'CODE', normalizedType: 'varchar(64)', nullable: false },
];
const inheritedFactoryField = {
  name: 'factory_id',
  type: 'ID',
  normalizedType: 'char(36)',
  nullable: false,
};
const inheritedMutableFields = [
  { name: 'updated_at', type: 'TS', normalizedType: 'datetime(3)', nullable: false },
  { name: 'updated_by', type: 'bigint', normalizedType: 'bigint', nullable: true },
  { name: 'row_version', type: 'bigint', normalizedType: 'bigint', nullable: false },
];

function expectedDesignFields(table) {
  const inherited = [...inheritedBaseFields];
  if (table.name !== 'aps_factory') inherited.push(inheritedFactoryField);
  if (table.kind !== 'E') inherited.push(...inheritedMutableFields);
  return [...inherited, ...table.explicitFields];
}

function parseForeignKeys(source, maskedSource, tableName, lineOffset = 0) {
  const foreignKeys = [];
  const pattern = /FOREIGN\s+KEY\s*\(([^)]+)\)\s+REFERENCES\s+(?:(?:`?[a-z][a-z0-9_]*`?)\s*\.\s*)?`?(aps_[a-z0-9_]+)`?\s*\(([^)]+)\)/gim;
  for (const match of maskedSource.matchAll(pattern)) {
    foreignKeys.push({
      table: tableName,
      localColumns: parseIdentifierList(match[1]),
      targetTable: match[2].toLowerCase(),
      targetColumns: parseIdentifierList(match[3]),
      line: lineNumberAt(source, lineOffset + match.index),
    });
  }
  return foreignKeys;
}

function withoutLeadingConstraint(segment) {
  return segment.replace(/^\s*CONSTRAINT\s+`?[a-z][a-z0-9_$]*`?\s+/i, '').trim();
}

function parseIndexes(maskedBody, columns) {
  const indexes = [];
  for (const segment of splitTopLevelSegments(maskedBody, maskedBody)) {
    const definition = withoutLeadingConstraint(segment.masked);
    let match = definition.match(/^PRIMARY\s+KEY\s*(?:USING\s+[a-z]+\s*)?\(([^)]+)\)/i);
    if (match) {
      indexes.push({ name: 'PRIMARY', kind: 'PRIMARY', unique: true, columns: parseIdentifierList(match[1]) });
      continue;
    }

    match = definition.match(/^UNIQUE(?:\s+(?:KEY|INDEX))?\s*(?:`?([a-z][a-z0-9_$]*)`?\s*)?(?:USING\s+[a-z]+\s*)?\(([^)]+)\)/i);
    if (match) {
      indexes.push({ name: match[1] || null, kind: 'UNIQUE', unique: true, columns: parseIdentifierList(match[2]) });
      continue;
    }

    match = definition.match(/^(?:KEY|INDEX)\s*(?:`?([a-z][a-z0-9_$]*)`?\s*)?(?:USING\s+[a-z]+\s*)?\(([^)]+)\)/i);
    if (match) {
      indexes.push({ name: match[1] || null, kind: 'INDEX', unique: false, columns: parseIdentifierList(match[2]) });
    }
  }

  // MySQL also permits column-level PRIMARY KEY / UNIQUE declarations.
  for (const column of columns) {
    if (/\bPRIMARY\s+KEY\b/i.test(column.definition)) {
      indexes.push({ name: 'PRIMARY', kind: 'PRIMARY', unique: true, columns: [column.name] });
    } else if (/\bUNIQUE\b/i.test(column.definition)) {
      indexes.push({ name: null, kind: 'UNIQUE', unique: true, columns: [column.name] });
    }
  }
  return indexes;
}

function parseChecks(source, maskedBody, tableName, lineOffset = 0) {
  const checksInTable = [];
  for (const match of maskedBody.matchAll(/\bCHECK\s*\(/gim)) {
    const openingIndex = match.index + match[0].lastIndexOf('(');
    const closingIndex = findMatchingParenthesis(maskedBody, openingIndex);
    checksInTable.push({
      table: tableName,
      line: lineNumberAt(source, lineOffset + match.index),
      malformed: closingIndex === -1,
      expression: closingIndex === -1
        ? maskedBody.slice(openingIndex + 1)
        : maskedBody.slice(openingIndex + 1, closingIndex),
    });
  }
  return checksInTable;
}

function parseSql(source) {
  const masked = maskSqlCommentsAndStrings(source);
  const tables = [];
  const malformedCreates = [];
  const createPattern = /\bCREATE\s+TABLE\s+(?:IF\s+NOT\s+EXISTS\s+)?(?:(?:`?[a-z][a-z0-9_]*`?)\s*\.\s*)?`?(aps_[a-z0-9_]+)`?\s*\(/gim;

  for (const match of masked.matchAll(createPattern)) {
    const openingIndex = match.index + match[0].lastIndexOf('(');
    const closingIndex = findMatchingParenthesis(masked, openingIndex);
    if (closingIndex === -1) {
      malformedCreates.push({ table: match[1], line: lineNumberAt(source, match.index) });
      continue;
    }

    const name = match[1].toLowerCase();
    const bodyStart = openingIndex + 1;
    const originalBody = source.slice(bodyStart, closingIndex);
    const maskedBody = masked.slice(bodyStart, closingIndex);
    const columns = [];
    const constraintStarters = new Set([
      'primary',
      'unique',
      'key',
      'index',
      'fulltext',
      'spatial',
      'constraint',
      'foreign',
      'check',
    ]);

    for (const segment of splitTopLevelSegments(originalBody, maskedBody)) {
      const columnMatch = segment.masked.match(/^\s*`?([a-z][a-z0-9_]*)`?\s+([\s\S]+)$/i);
      if (!columnMatch || constraintStarters.has(columnMatch[1].toLowerCase())) continue;
      const nameInSql = columnMatch[1].toLowerCase();
      const definitionOffset = segment.masked.indexOf(columnMatch[2]);
      const originalDefinition = segment.original.slice(definitionOffset).trim();
      columns.push({
        name: nameInSql,
        normalizedType: normalizeSqlType(columnMatch[2]),
        nullable: !/\bNOT\s+NULL\b/i.test(columnMatch[2]),
        definition: originalDefinition.replace(/\s+/g, ' '),
      });
    }

    const statementEnd = masked.indexOf(';', closingIndex);
    const optionsEnd = statementEnd === -1 ? source.length : statementEnd;
    const options = source.slice(closingIndex + 1, optionsEnd);
    const indexes = parseIndexes(maskedBody, columns);
    tables.push({
      name,
      line: lineNumberAt(source, match.index),
      columns,
      options,
      indexes,
      checks: parseChecks(source, maskedBody, name, bodyStart),
      foreignKeys: parseForeignKeys(source, maskedBody, name, bodyStart),
    });
  }

  const alterForeignKeys = [];
  const alterPattern = /\bALTER\s+TABLE\s+(?:(?:`?[a-z][a-z0-9_]*`?)\s*\.\s*)?`?(aps_[a-z0-9_]+)`?([\s\S]*?);/gim;
  for (const match of masked.matchAll(alterPattern)) {
    alterForeignKeys.push(...parseForeignKeys(source, match[2], match[1].toLowerCase(), match.index + match[0].indexOf(match[2])));
  }

  const namedConstraints = [...masked.matchAll(/\bCONSTRAINT\s+`?([a-z][a-z0-9_$]*)`?/gim)]
    .map((match) => ({
      name: match[1],
      normalizedName: match[1].toLowerCase(),
      line: lineNumberAt(source, match.index),
    }));

  return {
    masked,
    tables,
    malformedCreates,
    alterForeignKeys,
    namedConstraints,
  };
}

function collectRegexFindings(source, masked, definitions) {
  const findings = [];
  for (const definition of definitions) {
    const pattern = new RegExp(definition.pattern.source, definition.pattern.flags.includes('g')
      ? definition.pattern.flags
      : `${definition.pattern.flags}g`);
    for (const match of masked.matchAll(pattern)) {
      findings.push({
        rule: definition.name,
        line: lineNumberAt(source, match.index),
        excerpt: compactExcerpt(source, match.index),
      });
    }
  }
  return findings.sort((left, right) => left.line - right.line || left.rule.localeCompare(right.rule));
}

const designTables = parseDesign(designSource);
const designByName = new Map(designTables.map((table) => [table.name, table]));
const sql = parseSql(sqlSource);
const sqlByName = new Map(sql.tables.map((table) => [table.name, table]));
const designNames = new Set(designTables.map((table) => table.name));
const sqlNames = new Set(sql.tables.map((table) => table.name));

const designIdentityProblems = [];
if (designTables.length !== 46) {
  designIdentityProblems.push(`expected 46 table sections, found ${designTables.length}`);
}
for (let index = 0; index < designTables.length; index += 1) {
  const expectedNumber = index + 1;
  if (designTables[index].number !== expectedNumber) {
    designIdentityProblems.push(
      `position ${expectedNumber} is T${String(designTables[index].number).padStart(2, '0')} ${designTables[index].name}`,
    );
  }
}
const duplicateDesignNames = sorted(
  designTables
    .map((table) => table.name)
    .filter((name, index, all) => all.indexOf(name) !== index),
);
if (duplicateDesignNames.length) designIdentityProblems.push(`duplicate names: ${duplicateDesignNames.join(', ')}`);
addCheck('Design table identities are exactly T01-T46 and unique', designIdentityProblems.length === 0, {
  expected: 46,
  actual: designTables.length,
  errors: designIdentityProblems,
});

const malformedDesignFields = [];
const duplicateDesignFields = [];
for (const table of designTables) {
  if (table.explicitFields.length === 0) malformedDesignFields.push(`${table.name}: no field rows`);
  const expected = expectedDesignFields(table);
  const names = expected.map((field) => field.name);
  for (const field of table.explicitFields) {
    if (!field.normalizedType || !/^(NN|NULL)\s*\//.test(field.required) || !field.description) {
      malformedDesignFields.push(`${table.name}.${field.name}`);
    }
  }
  const duplicates = names.filter((name, index) => names.indexOf(name) !== index);
  if (duplicates.length) duplicateDesignFields.push(`${table.name}: ${sorted(new Set(duplicates)).join(', ')}`);
}
addCheck('Design field dictionaries and inherited common fields are well formed', malformedDesignFields.length === 0 && duplicateDesignFields.length === 0, {
  malformed: malformedDesignFields,
  duplicate: duplicateDesignFields,
});

const duplicateSqlNames = sorted(
  sql.tables
    .map((table) => table.name)
    .filter((name, index, all) => all.indexOf(name) !== index),
);
const missingSqlTables = sorted([...designNames].filter((name) => !sqlNames.has(name)));
const extraSqlTables = sorted([...sqlNames].filter((name) => !designNames.has(name)));
addCheck(
  'SQL CREATE TABLE set exactly matches the 46 design tables',
  sql.tables.length === 46
    && sqlNames.size === 46
    && sameSet(designNames, sqlNames)
    && duplicateSqlNames.length === 0
    && sql.malformedCreates.length === 0,
  {
    designCount: designTables.length,
    createStatementCount: sql.tables.length,
    uniqueSqlTableCount: sqlNames.size,
    missingInSql: missingSqlTables,
    extraInSql: extraSqlTables,
    duplicateCreateTables: duplicateSqlNames,
    malformedCreateStatements: sql.malformedCreates,
  },
);

const fieldSetMismatches = [];
const duplicateSqlFields = [];
for (const designTable of designTables) {
  const sqlTable = sqlByName.get(designTable.name);
  if (!sqlTable) continue;
  const expectedNames = new Set(expectedDesignFields(designTable).map((field) => field.name));
  const actualNames = new Set(sqlTable.columns.map((column) => column.name));
  const duplicates = sqlTable.columns
    .map((column) => column.name)
    .filter((name, index, all) => all.indexOf(name) !== index);
  if (duplicates.length) duplicateSqlFields.push(`${designTable.name}: ${sorted(new Set(duplicates)).join(', ')}`);
  const missing = sorted([...expectedNames].filter((name) => !actualNames.has(name)));
  const extra = sorted([...actualNames].filter((name) => !expectedNames.has(name)));
  if (missing.length || extra.length) {
    fieldSetMismatches.push({ table: designTable.name, missingInSql: missing, extraInSql: extra });
  }
}
addCheck(
  'Each SQL table field set equals explicit plus inherited design fields',
  fieldSetMismatches.length === 0 && duplicateSqlFields.length === 0 && missingSqlTables.length === 0,
  { mismatches: fieldSetMismatches, duplicateSqlFields },
);

const columnDefinitionMismatches = [];
const idStorageMismatches = [];
for (const designTable of designTables) {
  const sqlTable = sqlByName.get(designTable.name);
  if (!sqlTable) continue;
  const sqlColumns = new Map(sqlTable.columns.map((column) => [column.name, column]));
  for (const expected of expectedDesignFields(designTable)) {
    const actual = sqlColumns.get(expected.name);
    if (!actual) continue;
    if (actual.normalizedType !== expected.normalizedType || actual.nullable !== expected.nullable) {
      columnDefinitionMismatches.push({
        column: `${designTable.name}.${expected.name}`,
        expectedType: expected.normalizedType,
        actualType: actual.normalizedType,
        expectedNullable: expected.nullable,
        actualNullable: actual.nullable,
      });
    }
    if (expected.normalizedType === 'char(36)'
      && (!/\bCHARACTER\s+SET\s+ascii\b/i.test(actual.definition)
        || !/\bCOLLATE\s+ascii_bin\b/i.test(actual.definition))) {
      idStorageMismatches.push(`${designTable.name}.${expected.name}`);
    }
  }
}
addCheck(
  'SQL column types and nullability match the design aliases and dictionaries',
  columnDefinitionMismatches.length === 0,
  columnDefinitionMismatches,
);
addCheck(
  'All ID columns use CHAR(36) with ASCII binary collation',
  idStorageMismatches.length === 0,
  idStorageMismatches,
);

const mysqlOptionMismatches = sql.tables
  .filter((table) => !/\bENGINE\s*=\s*InnoDB\b/i.test(table.options)
    || !/\b(?:DEFAULT\s+)?CHARACTER\s+SET\s*=\s*utf8mb4\b/i.test(table.options))
  .map((table) => table.name);
addCheck(
  'Every CREATE TABLE targets InnoDB with utf8mb4',
  mysqlOptionMismatches.length === 0 && sql.tables.length === 46,
  mysqlOptionMismatches,
);

const checkExpressionKeywords = new Set([
  'all', 'and', 'any', 'as', 'asc', 'between', 'binary', 'both', 'by',
  'case', 'collate', 'current_date', 'current_time', 'current_timestamp', 'current_user',
  'date', 'datetime', 'day', 'decimal', 'desc', 'distinct', 'div', 'else', 'end',
  'escape', 'exists', 'false', 'from', 'hour', 'in', 'integer', 'interval', 'is',
  'leading', 'like', 'localtime', 'localtimestamp', 'microsecond', 'minute', 'mod',
  'month', 'not', 'null', 'of', 'or', 'quarter', 'regexp', 'rlike', 'second',
  'signed', 'some', 'then', 'time', 'timestamp', 'trailing', 'true', 'unknown',
  'unsigned', 'using', 'week', 'when', 'year', 'xor', 'zone',
]);
const invalidCheckReferences = [];
for (const table of sql.tables) {
  const columnNames = new Set(table.columns.map((column) => column.name));
  for (const checkConstraint of table.checks) {
    const unknownIdentifiers = new Set();
    if (checkConstraint.malformed) {
      invalidCheckReferences.push({
        table: table.name,
        line: checkConstraint.line,
        problem: 'unclosed CHECK expression',
        unknownIdentifiers: [],
      });
      continue;
    }
    for (const token of checkConstraint.expression.matchAll(/`?([a-z][a-z0-9_$]*)`?/gim)) {
      const identifier = token[1].toLowerCase();
      if (columnNames.has(identifier) || checkExpressionKeywords.has(identifier)) continue;
      const afterToken = checkConstraint.expression.slice(token.index + token[0].length);
      if (/^\s*\(/.test(afterToken)) continue; // Function or type invocation.
      unknownIdentifiers.add(identifier);
    }
    if (unknownIdentifiers.size) {
      invalidCheckReferences.push({
        table: table.name,
        line: checkConstraint.line,
        problem: 'identifier is neither a local column nor a recognized SQL keyword/function',
        unknownIdentifiers: sorted(unknownIdentifiers),
      });
    }
  }
}
addCheck(
  'Every CREATE TABLE CHECK expression only references columns from its own table',
  invalidCheckReferences.length === 0,
  {
    checkExpressionCount: sql.tables.reduce((sum, table) => sum + table.checks.length, 0),
    invalid: invalidCheckReferences,
    note: 'This is a conservative lexical check; server-side expression validation is still not run.',
  },
);

const constraintNameGroups = new Map();
for (const constraint of sql.namedConstraints) {
  const existing = constraintNameGroups.get(constraint.normalizedName) || [];
  existing.push({ name: constraint.name, line: constraint.line });
  constraintNameGroups.set(constraint.normalizedName, existing);
}
const duplicateConstraintNames = [...constraintNameGroups.entries()]
  .filter(([, occurrences]) => occurrences.length > 1)
  .map(([name, occurrences]) => ({ name, occurrences }));
const overlongConstraintNames = sql.namedConstraints
  .filter((constraint) => [...constraint.name].length > 64)
  .map((constraint) => ({
    name: constraint.name,
    length: [...constraint.name].length,
    line: constraint.line,
  }));
addCheck(
  'Named constraints are globally unique (case-insensitive) and at most 64 characters',
  duplicateConstraintNames.length === 0 && overlongConstraintNames.length === 0,
  {
    namedConstraintCount: sql.namedConstraints.length,
    duplicates: duplicateConstraintNames,
    overlong: overlongConstraintNames,
  },
);

const designForeignKeys = [];
const invalidDesignForeignKeys = [];
for (const table of designTables) {
  if (table.name !== 'aps_factory') {
    designForeignKeys.push({
      table: table.name,
      localColumn: 'factory_id',
      targetTable: 'aps_factory',
      targetColumn: 'id',
      inherited: true,
    });
  }
  for (const field of table.explicitFields) {
    for (const match of field.description.matchAll(/FK→(aps_[a-z0-9_]+)(?:\.([a-z][a-z0-9_]*))?/g)) {
      const expectation = {
        table: table.name,
        localColumn: field.name,
        targetTable: match[1],
        targetColumn: match[2] || 'id',
        inherited: false,
      };
      designForeignKeys.push(expectation);
      const target = designByName.get(expectation.targetTable);
      const targetFields = target && new Set(expectedDesignFields(target).map((item) => item.name));
      if (!target || !targetFields.has(expectation.targetColumn)) invalidDesignForeignKeys.push(expectation);
    }
  }
}
addCheck(
  'Every design foreign-key target table and column exists',
  invalidDesignForeignKeys.length === 0,
  { expectedForeignKeys: designForeignKeys.length, invalid: invalidDesignForeignKeys },
);

const allSqlForeignKeys = [
  ...sql.tables.flatMap((table) => table.foreignKeys),
  ...sql.alterForeignKeys,
];
const invalidSqlForeignKeys = [];
for (const foreignKey of allSqlForeignKeys) {
  const local = sqlByName.get(foreignKey.table);
  const target = sqlByName.get(foreignKey.targetTable);
  const localFields = local && new Set(local.columns.map((column) => column.name));
  const targetFields = target && new Set(target.columns.map((column) => column.name));
  const missingLocalColumns = foreignKey.localColumns.filter((column) => !localFields?.has(column));
  const missingTargetColumns = foreignKey.targetColumns.filter((column) => !targetFields?.has(column));
  if (!local || !target || foreignKey.localColumns.length !== foreignKey.targetColumns.length
    || missingLocalColumns.length || missingTargetColumns.length) {
    invalidSqlForeignKeys.push({
      ...foreignKey,
      missingLocalTable: !local,
      missingTargetTable: !target,
      missingLocalColumns,
      missingTargetColumns,
    });
  }
}
addCheck(
  'Every SQL foreign key has existing local and target tables and columns',
  invalidSqlForeignKeys.length === 0,
  { foreignKeyCount: allSqlForeignKeys.length, invalid: invalidSqlForeignKeys },
);

function sameOrderedColumns(left, right) {
  return left.length === right.length && left.every((column, index) => column === right[index]);
}

function startsWithColumns(indexColumns, foreignKeyColumns) {
  return indexColumns.length >= foreignKeyColumns.length
    && foreignKeyColumns.every((column, index) => indexColumns[index] === column);
}

const compositeForeignKeyTargetMismatches = [];
for (const foreignKey of allSqlForeignKeys.filter((item) => item.targetColumns.length > 1)) {
  const targetTable = sqlByName.get(foreignKey.targetTable);
  const matchingTargetKey = targetTable?.indexes.find((index) => index.unique
    && sameOrderedColumns(index.columns, foreignKey.targetColumns));
  if (!matchingTargetKey) {
    compositeForeignKeyTargetMismatches.push({
      table: foreignKey.table,
      localColumns: foreignKey.localColumns,
      targetTable: foreignKey.targetTable,
      targetColumns: foreignKey.targetColumns,
      line: foreignKey.line,
      availablePrimaryOrUniqueKeys: targetTable
        ? targetTable.indexes
          .filter((index) => index.unique)
          .map((index) => ({ name: index.name, columns: index.columns }))
        : [],
    });
  }
}
addCheck(
  'Every composite SQL foreign key targets an exact PRIMARY or UNIQUE key',
  compositeForeignKeyTargetMismatches.length === 0,
  {
    compositeForeignKeyCount: allSqlForeignKeys.filter((item) => item.targetColumns.length > 1).length,
    mismatches: compositeForeignKeyTargetMismatches,
  },
);

const foreignKeyPrefixIndexMismatches = [];
for (const foreignKey of allSqlForeignKeys) {
  const localTable = sqlByName.get(foreignKey.table);
  const matchingIndex = localTable?.indexes.find((index) => startsWithColumns(index.columns, foreignKey.localColumns));
  if (!matchingIndex) {
    foreignKeyPrefixIndexMismatches.push({
      table: foreignKey.table,
      localColumns: foreignKey.localColumns,
      targetTable: foreignKey.targetTable,
      targetColumns: foreignKey.targetColumns,
      line: foreignKey.line,
      availableIndexes: localTable
        ? localTable.indexes.map((index) => ({ name: index.name, columns: index.columns }))
        : [],
    });
  }
}
addCheck(
  'Every SQL foreign key has an explicit child-side index with the FK columns as its left prefix',
  foreignKeyPrefixIndexMismatches.length === 0,
  { mismatches: foreignKeyPrefixIndexMismatches },
);

const missingDesignForeignKeysInSql = designForeignKeys.filter((expected) => !allSqlForeignKeys.some((actual) => {
  if (actual.table !== expected.table || actual.targetTable !== expected.targetTable) return false;
  const position = actual.localColumns.indexOf(expected.localColumn);
  return position !== -1 && actual.targetColumns[position] === expected.targetColumn;
}));
addCheck(
  'Every design foreign key is implemented by CREATE TABLE or ALTER TABLE SQL',
  missingDesignForeignKeysInSql.length === 0 && missingSqlTables.length === 0,
  { expectedForeignKeys: designForeignKeys.length, missing: missingDesignForeignKeysInSql },
);

// These are the physical tables present in the archived 62-table design but
// intentionally absent from the approved 46-table design. The current design
// adds aps_resource_availability and aps_domain_event, hence 18 retired names
// account for a net reduction of 16 tables.
const retired62TableNames = new Set([
  'aps_audit_log',
  'aps_batch_load_rule',
  'aps_calendar_version',
  'aps_calendar_window',
  'aps_delivery_allocation',
  'aps_execution_event',
  'aps_operation',
  'aps_order_forecast',
  'aps_plan_input',
  'aps_plan_issue',
  'aps_rate_member',
  'aps_rate_profile',
  'aps_recovery_link',
  'aps_resource_calendar',
  'aps_resource_load_limit',
  'aps_sync_group',
  'aps_sync_member',
  'aps_worker',
]);
const sqlIdentifiers = new Set(
  [...sql.masked.matchAll(/\baps_[a-z0-9_]+\b/gi)].map((match) => match[0].toLowerCase()),
);
const retiredNamesInSql = sorted([...retired62TableNames].filter((name) => sqlIdentifiers.has(name)));
addCheck(
  'No retired physical table name from the 62-table draft remains in SQL',
  retiredNamesInSql.length === 0,
  retiredNamesInSql,
);

const postgresqlFindings = collectRegexFindings(sqlSource, sql.masked, [
  { name: 'PostgreSQL UUID type', pattern: /\buuid\b/gi },
  { name: 'PostgreSQL JSONB type', pattern: /\bjsonb\b/gi },
  { name: 'PostgreSQL time type', pattern: /\b(?:timestamptz|timetz)\b/gi },
  { name: 'PostgreSQL serial type', pattern: /\b(?:smallserial|serial|bigserial)\b/gi },
  { name: 'PostgreSQL BYTEA type', pattern: /\bbytea\b/gi },
  { name: 'PostgreSQL extension', pattern: /\bCREATE\s+EXTENSION\b/gi },
  { name: 'PostgreSQL search_path', pattern: /\bSET\s+search_path\b/gi },
  { name: 'PostgreSQL conflict clause', pattern: /\bON\s+CONFLICT\b/gi },
  { name: 'PostgreSQL RETURNING clause', pattern: /\bRETURNING\b/gi },
  { name: 'PostgreSQL cast operator', pattern: /::\s*[a-z][a-z0-9_]*/gi },
  { name: 'PostgreSQL positional parameter', pattern: /\$[1-9][0-9]*/g },
  { name: 'PostgreSQL dollar quote', pattern: /\$[a-z0-9_]*\$/gi },
  { name: 'PostgreSQL deferrable constraint', pattern: /\b(?:DEFERRABLE|INITIALLY\s+(?:DEFERRED|IMMEDIATE))\b/gi },
  { name: 'PostgreSQL exclusion constraint', pattern: /\bEXCLUDE\s+(?:USING|\()/gi },
  { name: 'PostgreSQL GiST index', pattern: /\bUSING\s+GIST\b/gi },
  { name: 'PostgreSQL identity syntax', pattern: /\bGENERATED\s+(?:ALWAYS|BY\s+DEFAULT)\s+AS\s+IDENTITY\b/gi },
  { name: 'PostgreSQL enum type', pattern: /\bCREATE\s+TYPE\b[\s\S]{0,200}?\bAS\s+ENUM\b/gi },
  { name: 'PostgreSQL ILIKE operator', pattern: /\bILIKE\b/gi },
  { name: 'PostgreSQL COMMENT ON', pattern: /\bCOMMENT\s+ON\b/gi },
  { name: 'PostgreSQL partial-index predicate', pattern: /\bCREATE\s+(?:UNIQUE\s+)?INDEX\b[\s\S]{0,500}?\bWHERE\b/gi },
  { name: 'PostgreSQL NULLS ordering', pattern: /\bNULLS\s+(?:FIRST|LAST)\b/gi },
  { name: 'PostgreSQL aggregate FILTER', pattern: /\bFILTER\s*\(\s*WHERE\b/gi },
  { name: 'PostgreSQL DISTINCT ON', pattern: /\bDISTINCT\s+ON\s*\(/gi },
  { name: 'PostgreSQL sequence function', pattern: /\b(?:nextval|setval|currval)\s*\(/gi },
  { name: 'PostgreSQL UUID generator', pattern: /\b(?:gen_random_uuid|uuid_generate_v4)\s*\(/gi },
]);
addCheck(
  'No PostgreSQL-only type or syntax remains in executable SQL',
  postgresqlFindings.length === 0,
  postgresqlFindings,
);

const destructiveFindings = collectRegexFindings(sqlSource, sql.masked, [
  { name: 'DROP statement', pattern: /\bDROP\s+(?:DATABASE|SCHEMA|TABLE|VIEW|INDEX|TRIGGER|PROCEDURE|FUNCTION|EVENT)\b/gi },
  { name: 'TRUNCATE statement', pattern: /\bTRUNCATE(?:\s+TABLE)?\b/gi },
  { name: 'DELETE statement', pattern: /\bDELETE\s+FROM\b/gi },
  { name: 'RENAME TABLE statement', pattern: /\bRENAME\s+TABLE\b/gi },
  { name: 'CREATE OR REPLACE statement', pattern: /\bCREATE\s+OR\s+REPLACE\b/gi },
  { name: 'Disabled foreign-key checks', pattern: /\bSET\s+(?:@@(?:GLOBAL\.)?)?FOREIGN_KEY_CHECKS\s*=\s*0\b/gi },
]);
const destructiveAlterFindings = [];
for (const match of sql.masked.matchAll(/\bALTER\s+TABLE\b[\s\S]*?;/gim)) {
  if (!/\b(?:DROP|TRUNCATE|RENAME|MODIFY|CHANGE)\b/i.test(match[0])) continue;
  destructiveAlterFindings.push({
    rule: 'Destructive ALTER TABLE clause',
    line: lineNumberAt(sqlSource, match.index),
    excerpt: compactExcerpt(sqlSource, match.index),
  });
}
const allDestructiveFindings = [...destructiveFindings, ...destructiveAlterFindings]
  .sort((left, right) => left.line - right.line || left.rule.localeCompare(right.rule));
addCheck('No destructive SQL or foreign-key bypass is present', allDestructiveFindings.length === 0, allDestructiveFindings);

const dataMutationFindings = collectRegexFindings(sqlSource, sql.masked, [
  { name: 'INSERT seed/data statement', pattern: /\bINSERT\s+INTO\b/gi },
  { name: 'UPDATE data statement', pattern: /\bUPDATE\s+(?:`?[a-z][a-z0-9_]*`?\.)?`?[a-z][a-z0-9_]*`?\s+SET\b/gi },
  { name: 'REPLACE data statement', pattern: /\bREPLACE\s+INTO\b/gi },
  { name: 'LOAD DATA statement', pattern: /\bLOAD\s+DATA\b/gi },
]);
addCheck('Baseline SQL contains no seed or data-mutation statements', dataMutationFindings.length === 0, dataMutationFindings);

const sourceHygieneProblems = [];
for (const [label, source] of [['design', designSource], ['sql', sqlSource]]) {
  if (/^(?:<{7}|={7}|>{7})/m.test(source)) sourceHygieneProblems.push(`${label}: conflict marker`);
  if (source.includes('\u0000')) sourceHygieneProblems.push(`${label}: NUL byte`);
}
addCheck('Sources contain no conflict markers or NUL bytes', sourceHygieneProblems.length === 0, sourceHygieneProblems);

const failedChecks = checks.filter((check) => check.status === 'Fail');
const result = {
  generatedAt: new Date().toISOString(),
  kind: 'aps-database-design-static-check',
  sources: {
    design: relativePath(designPath),
    designSha256: sha256(designSource),
    sql: relativePath(sqlPath),
    sqlSha256: sha256(sqlSource),
  },
  runtime: process.version,
  summary: {
    status: failedChecks.length === 0 ? 'Pass' : 'Fail',
    pass: checks.length - failedChecks.length,
    fail: failedChecks.length,
    designTables: designTables.length,
    sqlCreateStatements: sql.tables.length,
    sqlUniqueTables: sqlNames.size,
    expectedFields: designTables.reduce((sum, table) => sum + expectedDesignFields(table).length, 0),
    sqlFields: sql.tables.reduce((sum, table) => sum + table.columns.length, 0),
    designForeignKeys: designForeignKeys.length,
    sqlForeignKeys: allSqlForeignKeys.length,
    sqlCheckExpressions: sql.tables.reduce((sum, table) => sum + table.checks.length, 0),
    sqlNamedConstraints: sql.namedConstraints.length,
  },
  checks,
  notRun: [
    'MySQL parser or empty-database DDL execution',
    'Foreign-key enforcement against live data',
    'Concurrent transaction and lock-order verification',
    'Query-plan and performance verification',
    'Backup, restore, migration, or rollback rehearsal',
    'Application integration or user acceptance',
  ],
};

if (shouldWriteResult) {
  fs.writeFileSync(resultPath, `${JSON.stringify(result, null, 2)}\n`, 'utf8');
}
process.stdout.write(`${JSON.stringify(result, null, 2)}\n`);
process.exitCode = failedChecks.length === 0 ? 0 : 1;
