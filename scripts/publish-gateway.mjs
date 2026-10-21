import { execFileSync } from 'node:child_process'
import { createHash } from 'node:crypto'
import { readFileSync } from 'node:fs'
import { resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const registry = 'https://registry.npmjs.org'

function publishToNpm(archive) {
  if (!process.env.npm_execpath) {
    throw new Error('Run this script with npm run publish:gateway -- <archive.tgz>.')
  }
  execFileSync(process.execPath, [
    process.env.npm_execpath, 'publish', archive,
    '--access', 'public', '--provenance', '--ignore-scripts', '--registry', registry,
  ], { stdio: 'inherit' })
}

export async function publishGateway(archive, tag, { fetch = globalThis.fetch, publish = publishToNpm } = {}) {
  const metadata = JSON.parse(execFileSync('tar', ['-xOf', archive, 'package/package.json'], { encoding: 'utf8' }))
  if (metadata.name !== 'minecraft-driver-mcp' || tag !== `v${metadata.version}`) {
    throw new Error('The gateway archive must be minecraft-driver-mcp at the release tag version.')
  }
  const integrity = `sha512-${createHash('sha512').update(readFileSync(archive)).digest('base64')}`
  const spec = `${metadata.name}@${metadata.version}`
  const url = `${registry}/${metadata.name}/${metadata.version}`

  async function publishedIntegrity() {
    const response = await fetch(url, { signal: AbortSignal.timeout(30000), cache: 'no-store' })
    if (response.status === 404) return null
    if (!response.ok) throw new Error(`npm lookup for ${spec} failed: HTTP ${response.status}.`)
    const metadata = await response.json()
    if (typeof metadata.dist?.integrity !== 'string') {
      throw new Error(`npm returned no archive integrity for ${spec}.`)
    }
    return metadata.dist.integrity
  }

  function verify(published) {
    if (published !== integrity) {
      throw new Error(`${spec} on npm differs from the release archive; publish a new version instead.`)
    }
  }

  const existing = await publishedIntegrity()
  if (existing !== null) {
    verify(existing)
    console.log(`${spec} already contains the release archive; skipping publication.`)
    return
  }
  await publish(archive)
  const published = await publishedIntegrity()
  if (published === null) {
    throw new Error(`npm accepted ${spec}, but it is not visible yet; rerun the failed publishing job.`)
  }
  verify(published)
  console.log(`Verified ${spec} on npm against the release archive.`)
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try {
    if (!process.argv[2] || !process.env.GITHUB_REF_NAME) {
      throw new Error('Supply a gateway archive and GITHUB_REF_NAME=v<version>.')
    }
    await publishGateway(resolve(process.argv[2]), process.env.GITHUB_REF_NAME)
  } catch (error) {
    console.error(error.message)
    process.exitCode = 1
  }
}
