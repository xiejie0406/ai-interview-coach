const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..', '..');
const sourcePath = path.join(root, '文档', '项目', '生产排产项目', '架构', '生产排程数据库基线草案.sql');
const migrationPath = path.join(root, 'platform-backend', 'aps', 'aps-infrastructure-mysql', 'src', 'main',
  'resources', 'db', 'migration', 'aps', 'V001__aps_baseline.sql');
const permissionPath = path.join(root, 'platform-backend', 'sql', 'aps-permissions.sql');
const configPath = path.join(root, 'platform-backend', 'ruoyi-admin', 'src', 'main', 'resources', 'application-aps.yml');

const read = file => fs.readFileSync(file, 'utf8').replace(/^\uFEFF/, '').replace(/\r\n/g, '\n');
const stripComments = value => value.replace(/\/\*[\s\S]*?\*\//g, '').replace(/--[^\r\n]*/g, '');
const normalize = value => value.trim().replace(/[ \t]+$/gm, '');

const source = read(sourcePath);
const migration = read(migrationPath);
const permissions = read(permissionPath);
const config = read(configPath);
const marker = '-- ============================================================================';
const sourceBody = source.slice(source.indexOf(marker));
const migrationBody = migration.slice(migration.indexOf(marker));
const executable = stripComments(migration);
const checks = [];
const check = (name, ok, detail) => checks.push({ name, status: ok ? 'Pass' : 'Fail', detail });

check('V001 executable body exactly matches the approved DDL candidate',
  normalize(sourceBody) === normalize(migrationBody), 'comments before the first section may differ');

const tables = [...executable.matchAll(/CREATE\s+TABLE\s+(`?[a-zA-Z0-9_]+`?)/ig)]
  .map(match => match[1].replace(/`/g, '').toLowerCase());
check('V001 creates exactly 29 distinct APS tables', tables.length === 29 && new Set(tables).size === 29,
  { statements: tables.length, distinct: new Set(tables).size });
check('V001 contains no factory table or factory_id column',
  !/\baps_factory\b/i.test(executable) && !/\bfactory_id\b/i.test(executable), null);
const skillTables = tables.filter(table => table.includes('skill'));
check('V001 has exactly one skill table',
  skillTables.length === 1 && skillTables[0] === 'aps_resource_skill', skillTables);
check('V001 keeps 64 foreign keys', (executable.match(/\bFOREIGN\s+KEY\s*\(/ig) || []).length === 64,
  (executable.match(/\bFOREIGN\s+KEY\s*\(/ig) || []).length);
check('V001 has no database creation, destructive DDL, seed, or foreign-key bypass',
  !/\b(CREATE\s+DATABASE|DROP|TRUNCATE|DELETE\s+FROM|INSERT\s+INTO|REPLACE\s+INTO|FOREIGN_KEY_CHECKS)\b/i.test(executable), null);
check('V001 declares immutable forward-only migration policy',
  migration.includes('V001 一经在共享环境执行即不再修改') && migration.includes('V002+'), null);

const permissionValues = [...permissions.matchAll(/'((?:aps):[a-z]+:[a-z]+)'\s+perms|,\s*'(aps:[a-z]+:[a-z]+)'/g)]
  .map(match => match[1] || match[2]);
check('Permission SQL defines 29 unique aps:* permission codes',
  permissionValues.length === 29 && new Set(permissionValues).size === 29,
  { count: permissionValues.length, distinct: new Set(permissionValues).size });
check('Permission SQL is idempotent and grants no role',
  (permissions.match(/where\s+not\s+exists/ig) || []).length >= 1 &&
    !/insert\s+into\s+sys_role_menu/i.test(stripComments(permissions)), null);
check('All APS runtime and migration gates default to false',
  ['APS_ENABLED:false', 'APS_API_ENABLED:false', 'APS_PERSISTENCE_ENABLED:false',
    'APS_FLYWAY_ENABLED:false', 'APS_DATASOURCE_ENABLED:false', 'APS_WORKER_ENABLED:false']
    .every(value => config.includes(value)), null);

const failed = checks.filter(item => item.status === 'Fail');
console.log(JSON.stringify({
  kind: 'aps-v001-migration-static-check',
  summary: { status: failed.length === 0 ? 'Pass' : 'Fail', pass: checks.length - failed.length, fail: failed.length },
  checks
}, null, 2));
process.exitCode = failed.length === 0 ? 0 : 1;
