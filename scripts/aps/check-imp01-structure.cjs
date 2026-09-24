const fs = require('node:fs');
const path = require('node:path');

const root = path.resolve(__dirname, '..', '..');
const checks = [];

function record(name, ok, detail) {
  checks.push({ name, status: ok ? 'Pass' : 'Fail', detail });
}

function absolute(relativePath) {
  return path.join(root, ...relativePath.split('/'));
}

function exists(relativePath) {
  return fs.existsSync(absolute(relativePath));
}

function read(relativePath) {
  return fs.readFileSync(absolute(relativePath), 'utf8').replace(/^\uFEFF/, '');
}

function requireFiles(name, relativePaths) {
  const missing = relativePaths.filter(relativePath => !exists(relativePath));
  record(name, missing.length === 0, { expected: relativePaths.length, missing });
}

const apsModules = [
  'aps-domain',
  'aps-application',
  'aps-infrastructure-mysql',
  'aps-solver-contract',
  'aps-constraint-validator',
  'aps-solver-ortools',
  'aps-api',
  'aps-worker'
];

requireFiles(
  'Eight APS Maven module descriptors exist',
  [
    'platform-backend/aps/pom.xml',
    ...apsModules.map(module => `platform-backend/aps/${module}/pom.xml`)
  ]
);

if (exists('platform-backend/aps/pom.xml')) {
  const aggregator = read('platform-backend/aps/pom.xml');
  const declaredModules = [...aggregator.matchAll(/<module>([^<]+)<\/module>/g)].map(match => match[1].trim());
  record(
    'APS aggregator declares the approved module set and order',
    JSON.stringify(declaredModules) === JSON.stringify(apsModules),
    { expected: apsModules, actual: declaredModules }
  );
  record(
    'OR-Tools is pinned to an exact 9.15 patch',
    /<ortools\.version>9\.15\.\d+<\/ortools\.version>/.test(aggregator),
    (aggregator.match(/<ortools\.version>([^<]+)<\/ortools\.version>/) || [])[1] || 'missing'
  );
}

if (exists('platform-backend/pom.xml')) {
  const rootPom = read('platform-backend/pom.xml');
  record(
    'Platform reactor includes the APS aggregator',
    /<module>aps<\/module>/.test(rootPom),
    '<module>aps</module>'
  );
}

const boundaryPomChecks = [
  ['platform-backend/aps/aps-domain/pom.xml', 'aps-domain-boundary'],
  ['platform-backend/aps/aps-application/pom.xml', 'aps-application-boundary'],
  ['platform-backend/aps/aps-solver-contract/pom.xml', 'aps-solver-contract-boundary'],
  ['platform-backend/aps/aps-constraint-validator/pom.xml', 'aps-constraint-validator-boundary']
];
const missingBoundaryRules = boundaryPomChecks
  .filter(([relativePath, executionId]) => !exists(relativePath) || !read(relativePath).includes(`<id>${executionId}</id>`))
  .map(([relativePath, executionId]) => ({ relativePath, executionId }));
record(
  'Inner Java modules have explicit dependency-boundary guards',
  missingBoundaryRules.length === 0,
  missingBoundaryRules
);

requireFiles('APS runtime configuration is explicitly importable', [
  'platform-backend/ruoyi-admin/src/main/resources/application.yml',
  'platform-backend/ruoyi-admin/src/main/resources/application-aps.yml'
]);

if (
  exists('platform-backend/ruoyi-admin/src/main/resources/application.yml') &&
  exists('platform-backend/ruoyi-admin/src/main/resources/application-aps.yml')
) {
  const application = read('platform-backend/ruoyi-admin/src/main/resources/application.yml');
  const apsApplication = read('platform-backend/ruoyi-admin/src/main/resources/application-aps.yml');
  record(
    'RuoYi explicitly imports application-aps.yml',
    /optional:classpath:application-aps\.yml/.test(application),
    'spring.config.import'
  );
  record(
    'APS API and persistence defaults are disabled',
    /enabled:\s*\$\{APS_ENABLED:false\}/.test(apsApplication) &&
      /enabled:\s*\$\{APS_API_ENABLED:false\}/.test(apsApplication) &&
      /enabled:\s*\$\{APS_PERSISTENCE_ENABLED:false\}/.test(apsApplication) &&
      /enabled:\s*\$\{APS_DATASOURCE_ENABLED:false\}/.test(apsApplication),
    'aps.enabled=false, aps.api.enabled=false, aps.datasource.enabled=false'
  );
  record(
    'APS Worker has three independently disabled runtime gates',
    /enabled:\s*\$\{APS_WORKER_ENABLED:false\}/.test(apsApplication) &&
      /polling-enabled:\s*\$\{APS_WORKER_POLLING_ENABLED:false\}/.test(apsApplication) &&
      exists('platform-backend/aps/aps-worker/src/main/java/com/ruoyi/aps/worker/ApsWorkerRuntimeConfiguration.java') &&
      (read('platform-backend/aps/aps-worker/src/main/java/com/ruoyi/aps/worker/ApsWorkerRuntimeConfiguration.java')
        .match(/@ConditionalOnProperty/g) || []).length === 3,
    'aps.enabled + aps.worker.enabled + aps.worker.polling-enabled'
  );
}

const contractFiles = [
  'contracts/aps/README.md',
  'contracts/aps/openapi/aps-api-v1.yaml',
  'contracts/aps/schemas/problem-v1.schema.json',
  'contracts/aps/schemas/solver-input-v1.schema.json',
  'contracts/aps/schemas/solver-result-v1.schema.json',
  'contracts/aps/schemas/validation-result-v1.schema.json'
];
requireFiles('APS contract baseline files exist', contractFiles);
requireFiles('APS unified contract-test toolchain exists', [
  'contracts/aps/requirements-contracts.txt',
  'contracts/aps/contract-test-manifest.json',
  'contracts/aps/tools/verify_contracts.py',
  'contracts/aps/tools/verify-jcs.mjs'
]);

if (exists('contracts/aps/contract-test-manifest.json')) {
  try {
    const manifest = JSON.parse(read('contracts/aps/contract-test-manifest.json'));
    const listedCases = (manifest.cases || []).map(testCase => testCase.instance).sort();
    const listedHashCases = manifest.hashCases || [];
    const listedJcsVectors = manifest.jcsVectors || [];
    record(
      'Contract manifest covers the complete positive/negative and hash baseline',
      listedCases.length === 16 &&
        listedHashCases.length === 5 &&
        listedJcsVectors.length === 4,
      {
        schemaCases: listedCases.length,
        hashCases: listedHashCases.length,
        jcsVectors: listedJcsVectors.length
      }
    );
  } catch (error) {
    record('Contract test manifest parses as JSON', false, error.message);
  }
}

const goldenDirectory = absolute('contracts/aps/examples/golden');
const goldenFiles = fs.existsSync(goldenDirectory)
  ? fs.readdirSync(goldenDirectory).filter(name => name.endsWith('.json'))
  : [];
record(
  'Contract golden directory contains JSON examples',
  goldenFiles.length >= 4,
  { count: goldenFiles.length, files: goldenFiles }
);

for (const relativePath of contractFiles.filter(file => file.endsWith('.json'))) {
  if (!exists(relativePath)) continue;
  try {
    const document = JSON.parse(read(relativePath));
    record(
      `${relativePath} is JSON Schema 2020-12 with an explicit identifier`,
      document.$schema === 'https://json-schema.org/draft/2020-12/schema' && typeof document.$id === 'string',
      { $schema: document.$schema, $id: document.$id }
    );
  } catch (error) {
    record(`${relativePath} parses as JSON`, false, error.message);
  }
}

requireFiles('APS frontend TypeScript and test configuration exists', [
  'admin-web/tsconfig.aps.json',
  'admin-web/vitest.aps.config.js',
  'admin-web/playwright.aps.config.js',
  'admin-web/src/components/aps/许可证说明.md',
  'admin-web/src/components/aps/gantt/adapter.ts',
  'admin-web/src/components/aps/timeline/adapter.ts',
  'admin-web/tests/fixtures/aps/synthetic-plan.ts',
  'admin-web/tests/unit/aps/gantt-adapter.spec.ts',
  'admin-web/tests/unit/aps/timeline-adapter.spec.ts',
  'admin-web/tests/e2e/aps/adapter-render.spec.ts',
  'admin-web/tests/e2e/aps/fixtures/poc.ts'
]);

if (
  exists('admin-web/playwright.aps.config.js') &&
  exists('admin-web/tests/e2e/aps/adapter-render.spec.ts') &&
  exists('admin-web/tests/e2e/aps/fixtures/poc.ts')
) {
  const playwrightConfig = read('admin-web/playwright.aps.config.js');
  const browserTest = read('admin-web/tests/e2e/aps/adapter-render.spec.ts');
  const browserFixture = read('admin-web/tests/e2e/aps/fixtures/poc.ts');
  record(
    'Playwright artifacts stay under the task output boundary',
    /\.\.\/output\/playwright\/aps-imp01\//.test(playwrightConfig),
    '../output/playwright/aps-imp01/'
  );
  record(
    'Browser PoC exercises 500 resources, 5,000 segments, disposal, and recreation',
    /resourceCount:\s*500/.test(browserFixture) &&
      /segmentCount:\s*5_000/.test(browserFixture) &&
      /recreate\(\)/.test(browserFixture) &&
      /\.recreate\(\)/.test(browserTest) &&
      /consoleErrors/.test(browserTest) &&
      /pageErrors/.test(browserTest),
    '500/5,000 + recreate + clean console/page errors'
  );
}

if (
  exists('admin-web/src/components/aps/许可证说明.md') &&
  exists('admin-web/src/components/aps/gantt/adapter.ts') &&
  exists('admin-web/tests/unit/aps/gantt-adapter.spec.ts')
) {
  const licenseNotice = read('admin-web/src/components/aps/许可证说明.md');
  const ganttAdapter = read('admin-web/src/components/aps/gantt/adapter.ts');
  const ganttTest = read('admin-web/tests/unit/aps/gantt-adapter.spec.ts');
  record(
    'Frontend retains license evidence and rejects non-MIT DHTMLX builds',
    /dhtmlx-gantt@10\.0\.3/.test(licenseNotice) &&
      /vis-timeline@8\.5\.4/.test(licenseNotice) &&
      /assertDhtmlxCommunityLicense\(instance\.license\)/.test(ganttAdapter) &&
      /commercial/.test(ganttTest),
    'pinned versions + retained MIT text + runtime rejection test'
  );
  record(
    'DHTMLX drag intent restores the confirmed model',
    /onAfterTaskDrag/.test(ganttAdapter) &&
      /runtime\.clearAll\(\)/.test(ganttAdapter) &&
      /runtime\.parse\(confirmedData\)/.test(ganttAdapter) &&
      /toHaveBeenCalledTimes\(2\)/.test(ganttTest),
    'emit intent, then clear and parse the current confirmed model'
  );
}

if (exists('admin-web/package.json')) {
  const packageJson = JSON.parse(read('admin-web/package.json'));
  const scripts = packageJson.scripts || {};
  const dependencies = packageJson.dependencies || {};
  const devDependencies = packageJson.devDependencies || {};
  const requiredScripts = ['typecheck:aps', 'test:unit:aps', 'test:e2e:aps', 'test:aps'];
  record(
    'APS frontend scripts are wired',
    requiredScripts.every(name => typeof scripts[name] === 'string' && scripts[name].length > 0),
    Object.fromEntries(requiredScripts.map(name => [name, scripts[name] || null]))
  );
  record(
    'Gantt and timeline dependencies are pinned exactly',
    dependencies['dhtmlx-gantt'] === '10.0.3' &&
      dependencies['vis-timeline'] === '8.5.4',
    {
      'dhtmlx-gantt': dependencies['dhtmlx-gantt'] || null,
      'vis-timeline': dependencies['vis-timeline'] || null
    }
  );
  const requiredDevDependencies = {
    typescript: '5.9.3',
    'vue-tsc': '3.0.8',
    vitest: '4.1.11',
    '@playwright/test': '1.55.1'
  };
  record(
    'APS TypeScript and browser-test dependencies match the verified lock baseline',
    Object.entries(requiredDevDependencies).every(([name, version]) => devDependencies[name] === version),
    Object.fromEntries(Object.keys(requiredDevDependencies).map(name => [name, devDependencies[name] || null]))
  );
}

const featureRoot = '文档/项目/生产排产项目/功能/FEAT-APS-001-生产排程核心闭环';
requireFiles('Feature control and single implementation task source exist', [
  `${featureRoot}/README.md`,
  `${featureRoot}/任务清单.md`,
  `${featureRoot}/验证记录.md`,
  `${featureRoot}/验收记录.md`
]);

requireFiles('IMP-10 target verification assets exist', [
  'scripts/aps/verify-windows-runtime.ps1'
]);

if (exists(`${featureRoot}/README.md`) && exists(`${featureRoot}/任务清单.md`)) {
  const control = read(`${featureRoot}/README.md`);
  const tasks = read(`${featureRoot}/任务清单.md`);
  const detailedMilestones = [...tasks.matchAll(/^##\s+\d+\.\s+IMP-(\d{2})\b/gm)].map(match => match[1]);
  record(
    'Feature is at Stage 7 with IMP-01 through IMP-09 done and IMP-10 in progress',
    /Stage 7[^\r\n]*InProgress/.test(control) && /当前实施里程碑：IMP-10（技术实施中）/.test(control) &&
      /Stage 7[^\r\n]*InProgress/.test(tasks) && /IMP-01～IMP-09 Done/.test(tasks) && /IMP-10 InProgress/.test(tasks),
    'Stage 7, IMP-01 through IMP-09 Done, IMP-10 InProgress'
  );
  record(
    'The single task source contains exactly IMP-01 through IMP-10',
    JSON.stringify(detailedMilestones) === JSON.stringify(['01', '02', '03', '04', '05', '06', '07', '08', '09', '10']),
    detailedMilestones
  );
  record(
    'Approved single-site and deferred reliability boundaries are retained',
    /不恢复\s*`aps_factory`/.test(tasks) &&
      /单 Worker/.test(tasks) &&
      /外部[^\r\n]*可靠[^\r\n]*(?:P1|延期)/.test(tasks) &&
      /SAME_START/.test(tasks) &&
      /UNSUPPORTED_SYNC_RULE/.test(tasks),
    'no factory, single worker, external dispatch P1, SAME_START blocked'
  );
}

if (exists(`${featureRoot}/验收记录.md`) && exists('scripts/aps/verify-windows-runtime.ps1')) {
  const uat = read(`${featureRoot}/验收记录.md`);
  const windowsVerification = read('scripts/aps/verify-windows-runtime.ps1');
  const p0EngineAcceptanceIds = [
    '01', '02', '03', '04', '05', '06', '07', '08',
    '10', '11', '12', '13', '14', '15', '16', '17', '18', '19', '20', '21', '23'
  ];
  const productAcceptanceIds = Array.from({ length: 24 }, (_, index) => String(index + 1).padStart(2, '0'));
  record(
    'IMP-10 UAT package covers P0 product and engine acceptance without agent sign-off',
    productAcceptanceIds.every(id => new RegExp(`(^|[^A-Z-])AC-${id}(?!\\d)`, 'm').test(uat)) &&
      p0EngineAcceptanceIds.every(id => uat.includes(`APS-AC-${id}`)) &&
      /Prepared \/ NotExecuted/.test(uat) &&
      /不能代替用户或业务负责人的验收决定/.test(uat) &&
      /Accepted/.test(uat) && /Conditionally Accepted/.test(uat) && /Rejected/.test(uat),
    'AC-01 through AC-24, 21 P0 APS-AC items, Prepared/NotExecuted, business sign-off only'
  );
  record(
    'Windows target script fails closed around platform, runtime, JNI, artifacts, and startup',
    /\$IsWindows/.test(windowsVerification) &&
      /version \"17\\\./.test(windowsVerification) &&
      /OrToolsNativeSmokeTest/.test(windowsVerification) &&
      /backend-package/.test(windowsVerification) &&
      /frontend-package/.test(windowsVerification) &&
      /worker-disabled-startup/.test(windowsVerification) &&
      /api-isolated-mysql/.test(windowsVerification) &&
      /api-startup/.test(windowsVerification) &&
      /v3\/api-docs/.test(windowsVerification) &&
      /failures -gt 0/.test(windowsVerification),
    'Windows-only + Java 17 baseline + JNI + backend/frontend artifacts + isolated Worker/API startup + non-zero on failure'
  );
}

const passed = checks.filter(check => check.status === 'Pass').length;
const failed = checks.filter(check => check.status === 'Fail').length;

for (const check of checks) {
  console.log(`${check.status === 'Pass' ? '[PASS]' : '[FAIL]'} ${check.name}`);
  if (check.status === 'Fail') console.log(`       ${JSON.stringify(check.detail)}`);
}
console.log(`\nAPS foundation structure gate: ${passed}/${checks.length} passed, ${failed} failed.`);

if (failed > 0) process.exitCode = 1;
