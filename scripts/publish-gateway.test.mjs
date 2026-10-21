import assert from 'node:assert/strict'
import { execFileSync } from 'node:child_process'
import { createHash } from 'node:crypto'
import { mkdirSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from 'node:fs'
import { tmpdir } from 'node:os'
import { join } from 'node:path'
import { after, test } from 'node:test'
import { publishGateway } from './publish-gateway.mjs'

const directory = mkdtempSync(join(tmpdir(), 'publish-gateway-'))
after(() => rmSync(directory, { recursive: true, force: true }))
mkdirSync(join(directory, 'package'))
writeFileSync(join(directory, 'package/package.json'), JSON.stringify({ name: 'minecraft-driver-mcp', version: '0.5.0' }))
const archive = join(directory, 'gateway.tgz')
execFileSync('tar', ['-czf', archive, '-C', directory, 'package'])
const integrity = `sha512-${createHash('sha512').update(readFileSync(archive)).digest('base64')}`
const published = (value = integrity) => Response.json({ dist: { integrity: value } })
const noPublish = () => assert.fail('Must not publish an archive')

test('publishes the release archive only when the version is absent and verifies the result', async () => {
  let uploaded = false
  await publishGateway(archive, 'v0.5.0', {
    fetch: async (url) => {
      assert.equal(url, 'https://registry.npmjs.org/minecraft-driver-mcp/0.5.0')
      return uploaded ? published() : new Response(null, { status: 404 })
    },
    publish: (path) => {
      assert.equal(path, archive)
      uploaded = true
    },
  })
  assert.equal(uploaded, true)
})

test('a retry skips an identical published archive', async () => {
  await publishGateway(archive, 'v0.5.0', { fetch: async () => published(), publish: noPublish })
})

test('refuses an existing version with different archive bytes', async () => {
  await assert.rejects(
    publishGateway(archive, 'v0.5.0', { fetch: async () => published('sha512-other'), publish: noPublish }),
    /differs from the release archive/,
  )
})

test('refuses an archive whose version does not match the tag before contacting npm', async () => {
  await assert.rejects(
    publishGateway(archive, 'v0.6.0', { fetch: noPublish, publish: noPublish }),
    /release tag version/,
  )
})

test('does not interpret registry errors as an unpublished version', async () => {
  for (const status of [401, 403, 429, 500]) {
    await assert.rejects(
      publishGateway(archive, 'v0.5.0', { fetch: async () => new Response(null, { status }), publish: noPublish }),
      new RegExp(`HTTP ${status}`),
    )
  }
})

test('does not publish when registry metadata lacks archive integrity', async () => {
  await assert.rejects(
    publishGateway(archive, 'v0.5.0', { fetch: async () => Response.json({ dist: {} }), publish: noPublish }),
    /no archive integrity/,
  )
})

test('verifies the registry archive after an upload', async () => {
  let uploaded = false
  await assert.rejects(publishGateway(archive, 'v0.5.0', {
    fetch: async () => uploaded ? published('sha512-other') : new Response(null, { status: 404 }),
    publish: () => { uploaded = true },
  }), /differs from the release archive/)
})

test('a successful upload with delayed visibility can be retried safely', async () => {
  await assert.rejects(publishGateway(archive, 'v0.5.0', {
    fetch: async () => new Response(null, { status: 404 }),
    publish: () => {},
  }), /rerun the failed publishing job/)
  await publishGateway(archive, 'v0.5.0', { fetch: async () => published(), publish: noPublish })
})
