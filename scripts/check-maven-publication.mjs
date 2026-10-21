import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { readFileSync, readdirSync } from 'node:fs';
import { join } from 'node:path';
import { execFileSync } from 'node:child_process';

const properties = Object.fromEntries(
  readFileSync('gradle.properties', 'utf8').split(/\r?\n/)
    .filter(line => line && !line.startsWith('#')).map(line => line.split('=')),
);
const group = properties.maven_group;
const artifact = properties.archives_base_name;
for (const minecraft of readdirSync('versions')) {
  const version = `${properties.mod_version}+${minecraft}`;
  const base = `${artifact}-${version}`;
  const directory = join('build/maven-verification', ...group.split('.'), artifact, version);
  const pom = readFileSync(join(directory, `${base}.pom`), 'utf8');
  for (const value of [group, artifact, version, 'MIT License', '<developers>', '<scm>',
    'kotlin-sdk-server-jvm', 'ktor-server-cio-jvm', 'kotlin-stdlib']) {
    assert(pom.includes(value), `POM must contain ${value}`);
  }
  const metadata = JSON.parse(readFileSync(join(directory, `${base}.module`), 'utf8'));
  assert.equal(metadata.component.group, group);
  assert.equal(metadata.component.version, version);
  for (const variant of metadata.variants) {
    for (const file of variant.files) {
      assert(!file.name.includes('-dev'), 'Consumers must receive the remapped mod');
      const bytes = readFileSync(join(directory, file.url));
      assert.equal(createHash('sha256').update(bytes).digest('hex'), file.sha256);
    }
  }
  for (const suffix of ['', '-sources', '-javadoc']) {
    const file = join(directory, `${base}${suffix}.jar`);
    const entries = execFileSync('jar', ['tf', file], { encoding: 'utf8' });
    const expected = suffix === '-sources' ? 'DriverExtension.kt'
      : suffix === '-javadoc' ? 'index.html' : 'fabric.mod.json';
    assert(entries.includes(expected), `${file} must contain ${expected}`);
    if (!suffix) {
      assert(entries.includes('META-INF/jars/'), 'Standalone mod must retain bundled libraries');
      assert(entries.includes('minecraft-driver-mcp/catalogue.json'), 'Tool catalogue must be packaged');
    }
  }
  console.log(`Verified ${group}:${artifact}:${version}: POM, variants, hashes, mod, sources and Java Javadoc.`);
}
