import assert from 'node:assert/strict';
import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import process from 'node:process';
import { fileURLToPath } from 'node:url';

function assertUnicodeScalarString(value, label) {
  for (let index = 0; index < value.length; index += 1) {
    const code = value.charCodeAt(index);
    if (code >= 0xd800 && code <= 0xdbff) {
      const next = value.charCodeAt(index + 1);
      assert.ok(next >= 0xdc00 && next <= 0xdfff, `${label}: unpaired high surrogate`);
      index += 1;
    } else {
      assert.ok(!(code >= 0xdc00 && code <= 0xdfff), `${label}: unpaired low surrogate`);
    }
  }
}

function canonicalize(value, label = '$') {
  if (value === null || typeof value === 'boolean') {
    return JSON.stringify(value);
  }
  if (typeof value === 'number') {
    assert.ok(Number.isFinite(value), `${label}: non-finite number`);
    return JSON.stringify(value);
  }
  if (typeof value === 'string') {
    assertUnicodeScalarString(value, label);
    return JSON.stringify(value);
  }
  if (Array.isArray(value)) {
    return `[${value.map((entry, index) => canonicalize(entry, `${label}[${index}]`)).join(',')}]`;
  }
  assert.equal(typeof value, 'object', `${label}: unsupported JSON value`);
  const keys = Object.keys(value);
  for (const key of keys) {
    assertUnicodeScalarString(key, `${label} key`);
  }
  keys.sort();
  return `{${keys.map((key) => `${JSON.stringify(key)}:${canonicalize(value[key], `${label}.${key}`)}`).join(',')}}`;
}

function sha256(text) {
  return crypto.createHash('sha256').update(text, 'utf8').digest('hex');
}

const here = path.dirname(fileURLToPath(import.meta.url));
const apsRoot = path.resolve(here, '..');
const manifestPath = process.argv[2] ? path.resolve(process.argv[2]) : path.join(apsRoot, 'contract-test-manifest.json');
const manifest = JSON.parse(fs.readFileSync(manifestPath, 'utf8'));

assert.equal(process.versions.node, manifest.toolchain.node, `Node must be ${manifest.toolchain.node}`);

for (const vector of manifest.jcsVectors) {
  const actualCanonical = canonicalize(vector.input);
  assert.equal(actualCanonical, vector.canonical, `${vector.name}: canonical bytes differ`);
  assert.equal(sha256(actualCanonical), vector.sha256, `${vector.name}: SHA-256 differs`);
  process.stdout.write(`JCS vector ${vector.name}: PASS\n`);
}

for (const hashCase of manifest.hashCases) {
  const instancePath = path.join(apsRoot, hashCase.instance);
  const instance = JSON.parse(fs.readFileSync(instancePath, 'utf8'));
  const expected = instance[hashCase.hashField];
  let content;
  if (hashCase.mode === 'OMIT_TOP_LEVEL_FIELD') {
    content = { ...instance };
    delete content[hashCase.hashField];
  } else if (hashCase.mode === 'SELECT_PROPERTY') {
    content = instance[hashCase.property];
  } else {
    throw new Error(`${hashCase.instance}: unknown hash mode ${hashCase.mode}`);
  }
  assert.match(expected, /^[0-9a-f]{64}$/, `${hashCase.instance}: invalid expected hash`);
  assert.equal(sha256(canonicalize(content)), expected, `${hashCase.instance}: content hash differs`);
  process.stdout.write(`JCS hash ${hashCase.instance}: PASS\n`);
}

process.stdout.write('APS JCS/hash verification: PASS\n');
