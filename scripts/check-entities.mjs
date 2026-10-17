import assert from 'node:assert/strict'
import { setTimeout as delay } from 'node:timers/promises'

const [endpoint, folder] = process.argv.slice(2)
if (!endpoint || !folder) {
  throw new Error('Usage: node scripts/check-entities.mjs <http://127.0.0.1:port/mcp> <disposable-world-folder-with-cheats>')
}

let requestId = 0
let commandId = 0

async function rpc(method, params) {
  const response = await fetch(endpoint, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Accept: 'application/json, text/event-stream' },
    body: JSON.stringify({ jsonrpc: '2.0', id: ++requestId, method, params }),
    signal: AbortSignal.timeout(70_000),
  })
  assert.equal(response.status, 200, `${method}: HTTP ${response.status}`)
  const body = await response.text()
  const message = response.headers.get('content-type')?.includes('text/event-stream')
    ? JSON.parse(body.split('\n').find(line => line.startsWith('data: ')).slice(6))
    : JSON.parse(body)
  assert.equal(message.error, undefined, JSON.stringify(message.error))
  return message.result
}

async function call(name, args = {}) {
  return rpc('tools/call', { name, arguments: args })
}

async function value(name, args = {}) {
  const result = await call(name, args)
  assert.ok(!result.isError, `${name}: ${JSON.stringify(result.content)}`)
  return JSON.parse(result.content[0].text)
}

async function entities(args = {}) {
  const result = await call('mc_list_entities', args)
  assert.ok(!result.isError, `mc_list_entities: ${JSON.stringify(result.content)}`)
  assert.equal(result.content.length, 2, 'Entity tool returns the entity array and metadata')
  return {
    items: JSON.parse(result.content[0].text),
    meta: JSON.parse(result.content[1].text),
  }
}

async function until(read, matches, label, timeout = 10_000) {
  const deadline = Date.now() + timeout
  let actual
  do {
    actual = await read()
    if (matches(actual)) return actual
    await delay(100)
  } while (Date.now() < deadline)
  assert.fail(`${label}: ${JSON.stringify(actual)}`)
}

async function command(message) {
  const messages = await value('mc_read_messages')
  await value('mc_send_chat', { text: message })
  const marker = `driver_entity_check_${++commandId}`
  await value('mc_send_chat', { text: `/say ${marker}` })
  const reply = await value('mc_wait_for', { message: marker, since: messages.latest, timeout_ms: 5000 })
  assert.equal(reply.matched, true, `Commands did not complete; verify cheats are enabled: ${message}`)
}

async function invalid(args, expected) {
  const result = await call('mc_list_entities', args)
  assert.equal(result.isError, true, `Expected rejection of ${JSON.stringify(args)}`)
  assert.match(result.content[0].text, expected)
}

function summon(name, x, z, tag = 'driver_entity_fixture') {
  const customName = JSON.stringify({ text: name })
  return `/summon minecraft:armor_stand ${x} 260 ${z} {NoGravity:1b,Marker:1b,CustomName:'${customName}',Tags:["${tag}"]}`
}

await rpc('initialize', {
  protocolVersion: '2025-03-26', capabilities: {}, clientInfo: { name: 'entity-observation-check', version: '1' },
})
const initialized = await fetch(endpoint, {
  method: 'POST',
  headers: { 'Content-Type': 'application/json', Accept: 'application/json, text/event-stream' },
  body: JSON.stringify({ jsonrpc: '2.0', method: 'notifications/initialized' }),
  signal: AbortSignal.timeout(10_000),
})
assert.equal(initialized.status, 202)
const tools = await rpc('tools/list', {})
const definition = tools.tools.find(tool => tool.name === 'mc_list_entities')
assert.ok(definition, 'mc_list_entities is advertised')
assert.deepEqual(Object.keys(definition.inputSchema.properties).sort(), ['limit', 'name_contains', 'radius', 'type', 'type_prefix'])
assert.deepEqual(definition.inputSchema.required ?? [], [])
assert.equal(definition.inputSchema.properties.radius.type, 'number')
assert.equal(definition.inputSchema.properties.limit.type, 'integer')
for (const name of ['type', 'type_prefix', 'name_contains']) assert.equal(definition.inputSchema.properties[name].type, 'string')
assert.deepEqual(definition.annotations, {
  readOnlyHint: true, destructiveHint: false, idempotentHint: true, openWorldHint: false,
})
assert.equal((await value('mc_get_state')).connected, false, 'Start the client at the title screen')
const titleError = await call('mc_list_entities')
assert.equal(titleError.isError, true)
assert.match(titleError.content[0].text, /mc_join_world|mc_join_server/)
console.log('PASS initialize, optional filter schema/annotations, actionable title-screen error')

const join = await call('mc_join_world', { folder })
assert.ok(!join.isError, JSON.stringify(join.content))
try {
  await until(() => value('mc_get_state'), state => state.player !== null && state.screen === null, 'World loaded', 60_000)
  await value('mc_close_screen')
  await command('/gamemode creative')
  await command('/execute in minecraft:overworld run tp @s 0.5 260 0.5 0 0')
  await command('/fill -2 259 -2 2 259 2 minecraft:stone')
  await command('/tp @s 0.5 260 0.5 0 0')
  await until(() => value('mc_get_state'), state => state.player?.y === 260, 'Player settled at fixture height')
  await command('/kill @e[tag=driver_entity_fixture]')
  await command('/kill @e[tag=driver_entity_bulk_fixture]')
  await command(summon('Alpha [Target]!', 2.5, 0.5))
  await command(summon('alpha target!', 3.5, 0.5))
  await command(summon('Nearby decoy', 0.5, 0.5))
  await command(summon('Boundary target', 4.5, 0.5))
  await command(summon('Far named target', 80.5, 0.5))

  const noArguments = await entities()
  assert.deepEqual(noArguments.meta, {
    total: noArguments.items.length, truncated: false, radius: 64, limit: 100,
  })
  const defaults = await until(
    () => entities({ name_contains: 'Alpha [Target]!' }),
    result => result.items.length === 1,
    'Named fixture tracked by client',
  )
  assert.equal(defaults.items[0].type, 'minecraft:armor_stand')
  assert.equal(defaults.items[0].name, 'Alpha [Target]!')
  assert.deepEqual(Object.keys(defaults.items[0]).sort(), ['distance', 'id', 'name', 'type', 'x', 'y', 'z'])
  assert.ok(Number.isInteger(defaults.items[0].id))
  assert.equal(defaults.items[0].x, 2.5)
  assert.equal(defaults.items[0].y, 260)
  assert.equal(defaults.items[0].z, 0.5)
  assert.equal(defaults.items[0].distance, 2)
  assert.deepEqual(defaults.meta, { total: 1, truncated: false, radius: 64, limit: 100 })
  console.log('PASS default radius and limit, displayed name, type and feet-position distance fields')

  const exact = await entities({ type: 'minecraft:armor_stand', name_contains: '[Target]!' })
  assert.equal(exact.items.length, 1, 'Name filter treats punctuation literally and case-insensitively')
  assert.equal(exact.items[0].name, 'Alpha [Target]!')
  const prefix = await entities({ type_prefix: 'minecraft:armor_', name_contains: 'alpha' })
  assert.deepEqual(prefix.items.map(item => item.name), ['Alpha [Target]!', 'alpha target!'])
  assert.deepEqual((await entities({ type: 'Minecraft:armor_stand' })).items, [])
  assert.deepEqual((await entities({ type: 'minecraft:armor_' })).items, [])
  assert.deepEqual((await entities({ type_prefix: 'Minecraft:armor_' })).items, [])
  assert.deepEqual((await entities({ type: 'minecraft:zombie', type_prefix: 'minecraft:armor_' })).items, [])
  const combined = await entities({
    type: 'minecraft:armor_stand', type_prefix: 'minecraft:armor_', name_contains: 'TARGET', radius: 4, limit: 1,
  })
  assert.equal(combined.items.length, 1)
  assert.equal(combined.items[0].name, 'Alpha [Target]!')
  assert.equal(combined.meta.total, 2, 'Filters are combined before applying limit')
  assert.equal(combined.meta.truncated, true)
  const unknown = await entities({ type: 'minecraft:driver_unknown_entity' })
  assert.deepEqual(unknown.items, [])
  assert.equal(unknown.meta.total, 0)
  console.log('PASS exact and prefix registry filters, combined filters, literal case-insensitive names, unknown type')

  const radius = await entities({ radius: 4, name_contains: 'target' })
  assert.deepEqual(radius.items.map(item => item.name), ['Alpha [Target]!', 'alpha target!'])
  assert.ok(radius.items[0].distance < radius.items[1].distance, 'Results are nearest first')
  assert.ok(radius.items.every(item => item.distance < 4), 'Radius boundary is exclusive')
  const wide = await until(
    () => entities({ radius: 256, limit: 1000, name_contains: 'Far named target' }),
    result => result.items.length === 1,
    'Far fixture tracked by client',
  )
  assert.equal(wide.items.length, 1, 'Maximum radius and limit are accepted')
  assert.equal(wide.meta.radius, 256)
  assert.equal(wide.meta.limit, 1000)
  console.log('PASS exclusive radius, nearest-first ordering, and maximum accepted bounds')

  const invalidArgs = [
    [{ radius: 0 }, /radius/i], [{ radius: 256.1 }, /radius/i], [{ radius: '64' }, /radius/i],
    [{ limit: 0 }, /limit/i], [{ limit: 1001 }, /limit/i],
    [{ limit: 1.5 }, /limit/i], [{ limit: '10' }, /limit/i], [{ type: 1 }, /type/i],
    [{ type_prefix: true }, /type_prefix/i], [{ name_contains: [] }, /name_contains/i],
  ]
  for (const [args, expected] of invalidArgs) await invalid(args, expected)
  const nullDefaults = await entities({ radius: null, limit: null, type: null })
  assert.deepEqual(nullDefaults.meta, { total: nullDefaults.items.length, truncated: false, radius: 64, limit: 100 })
  console.log('PASS radius, limit and filter type validation rejects invalid values')

  const bulkCommands = Array.from({ length: 101 }, (_, index) => {
    const x = 20 + (index % 11)
    const z = 20 + Math.floor(index / 11)
    return summon('Bulk cap fixture', `${x}.5`, `${z}.5`, 'driver_entity_bulk_fixture')
  })
  for (const summonCommand of bulkCommands) await value('mc_send_chat', { text: summonCommand })
  await command('/say driver_entity_bulk_barrier')
  const capped = await until(
    () => entities({ name_contains: 'Bulk cap fixture' }),
    result => result.meta.total >= 101,
    'All bulk fixtures tracked',
  )
  assert.equal(capped.items.length, 100)
  assert.equal(capped.meta.total, 101)
  assert.equal(capped.meta.truncated, true)
  console.log('PASS default 100 item cap reports total matching entities and truncation')

  const before = await value('mc_get_state')
  const inventoryBefore = await value('mc_read_inventory')
  for (let i = 0; i < 3; i++) {
    const repeated = await entities({ type: 'minecraft:armor_stand', name_contains: 'Alpha [Target]!' })
    assert.deepEqual(repeated.items, exact.items)
    assert.deepEqual(repeated.meta, exact.meta)
  }
  assert.deepEqual((await value('mc_get_state')).player, before.player)
  assert.deepEqual(await value('mc_read_inventory'), inventoryBefore)
  console.log('PASS repeated reads preserve player and inventory state')
} finally {
  await command('/kill @e[tag=driver_entity_fixture]')
  await command('/kill @e[tag=driver_entity_bulk_fixture]')
  await call('mc_disconnect')
}
const disconnected = await call('mc_list_entities')
assert.equal(disconnected.isError, true)
assert.match(disconnected.content[0].text, /mc_join_world|mc_join_server/)
console.log('PASS fixtures cleaned up, disconnect clears access to the world')
