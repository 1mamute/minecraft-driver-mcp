import assert from 'node:assert/strict'
import { setTimeout as delay } from 'node:timers/promises'

const [endpoint, folder] = process.argv.slice(2)
if (!endpoint || !folder) {
  throw new Error('Usage: node scripts/check-target.mjs <http://127.0.0.1:port/mcp> <disposable-world-folder-with-cheats>')
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
  const result = await call('mc_send_chat', { text: message })
  assert.ok(!result.isError, JSON.stringify(result.content))
  const marker = `driver_target_check_${++commandId}`
  await value('mc_send_chat', { text: `/say ${marker}` })
  const reply = await value('mc_wait_for', { message: marker, since: messages.latest, timeout_ms: 5000 })
  assert.equal(reply.matched, true, `Commands did not complete; verify cheats are enabled: ${message}`)
}

async function aim(x, y, z, type) {
  const result = await call('mc_look_at', { x, y, z })
  assert.ok(!result.isError, JSON.stringify(result.content))
  await delay(250)
  return until(() => value('mc_get_target'), result => result.target.type === type, `Expected ${type}`)
}

function checkDistance(snapshot) {
  const { location, distance } = snapshot.target
  const { origin } = snapshot
  const expected = Math.hypot(location.x - origin.x, location.y - origin.y, location.z - origin.z)
  assert.ok(Math.abs(distance - expected) < 1e-9, `Distance ${distance}, expected ${expected}`)
}

function checkMiss(snapshot) {
  assert.equal(snapshot.target.type, 'miss')
  assert.equal(snapshot.target.block, undefined)
  assert.equal(snapshot.target.entity, undefined)
  checkDistance(snapshot)
}

await rpc('initialize', {
  protocolVersion: '2025-03-26', capabilities: {}, clientInfo: { name: 'crosshair-target-check', version: '1' },
})
const initialized = await fetch(endpoint, {
  method: 'POST',
  headers: { 'Content-Type': 'application/json', Accept: 'application/json, text/event-stream' },
  body: JSON.stringify({ jsonrpc: '2.0', method: 'notifications/initialized' }),
  signal: AbortSignal.timeout(10_000),
})
assert.equal(initialized.status, 202)
const tools = await rpc('tools/list', {})
const definition = tools.tools.find(tool => tool.name === 'mc_get_target')
assert.ok(definition, 'mc_get_target is advertised')
assert.equal(Object.keys(definition.inputSchema.properties ?? {}).length, 0)
assert.deepEqual(definition.annotations, {
  readOnlyHint: true, destructiveHint: false, idempotentHint: true, openWorldHint: false,
})
assert.equal((await value('mc_get_state')).connected, false, 'Start the client at the title screen')
const outsideWorld = await call('mc_get_target')
assert.equal(outsideWorld.isError, true)
assert.match(outsideWorld.content[0].text, /mc_join_world|mc_join_server/)
console.log('PASS initialize, tool schema/annotations, actionable title-screen error')

const join = await call('mc_join_world', { folder })
assert.ok(!join.isError, JSON.stringify(join.content))
await until(() => value('mc_get_state'), state => state.player !== null && state.screen === null, 'World loaded', 60_000)
await call('mc_close_screen')

try {
  await command('/gamemode creative')
  await command('/execute in minecraft:overworld run tp @s 0.5 205 0.5 -90 0')
  await command('/attribute @s minecraft:player.block_interaction_range base set 4.5')
  await command('/attribute @s minecraft:player.entity_interaction_range base set 3')
  await command('/kill @e[tag=driver_target_check]')
  await command('/setblock 0 199 0 minecraft:stone')
  await command('/fill 1 200 -1 8 203 1 minecraft:air')
  await command('/setblock 3 201 0 minecraft:stone')
  await command('/tp @s 0.5 200 0.5 -90 0')
  const block = await aim(3.5, 201.5, 0.5, 'block')
  assert.deepEqual(block.target.block, { id: 'minecraft:stone', x: 3, y: 201, z: 0, face: 'west' })
  assert.equal(block.target.entity, undefined)
  assert.ok(Math.abs(block.target.location.x - 3) < 1e-6)
  assert.equal(block.blockReach, 5)
  assert.equal(block.entityReach, 5)
  checkDistance(block)
  const before = await value('mc_get_state')
  const inventoryBefore = await value('mc_read_inventory')
  for (let i = 0; i < 3; i++) assert.deepEqual((await value('mc_get_target')).target, block.target)
  assert.deepEqual((await value('mc_get_state')).player, before.player)
  assert.deepEqual(await value('mc_read_inventory'), inventoryBefore)
  console.log('PASS block ID/position/face, distance, creative reach, read-only repeated observations')

  checkMiss(await aim(0.5, 250, 0.5, 'miss'))
  await command('/setblock 3 201 0 minecraft:air')
  await command('/setblock 7 201 0 minecraft:stone')
  checkMiss(await aim(7.5, 201.5, 0.5, 'miss'))
  await command('/gamemode survival')
  const survival = await until(() => value('mc_get_target'), result => result.blockReach === 4.5, 'Survival ranges')
  assert.equal(survival.entityReach, 3)
  await command('/attribute @s minecraft:player.block_interaction_range base set 8')
  const extended = await aim(7.5, 201.5, 0.5, 'block')
  assert.equal(extended.blockReach, 8)
  assert.equal(extended.target.block.x, 7)
  await command('/attribute @s minecraft:player.block_interaction_range base set 4.5')
  console.log('PASS sky/out-of-reach misses, survival ranges, block range attribute changes')

  await command('/setblock 7 201 0 minecraft:air')
  await command('/summon minecraft:pig 4.5 200 0.5 {NoAI:1b,NoGravity:1b,Silent:1b,Tags:["driver_target_check"]}')
  checkMiss(await aim(4.5, 200.5, 0.5, 'miss'))
  await command('/attribute @s minecraft:player.entity_interaction_range base set 6')
  const entity = await aim(4.5, 200.5, 0.5, 'entity')
  assert.equal(entity.entityReach, 6)
  assert.equal(entity.target.entity.type, 'minecraft:pig')
  assert.ok(Number.isInteger(entity.target.entity.id))
  assert.match(entity.target.entity.uuid, /^[0-9a-f]{8}-(?:[0-9a-f]{4}-){3}[0-9a-f]{12}$/)
  assert.equal(entity.target.block, undefined)
  checkDistance(entity)
  await command('/attribute @s minecraft:player.entity_interaction_range base set 3')
  await aim(4.5, 200.5, 0.5, 'miss')
  await command('/kill @e[tag=driver_target_check]')
  console.log('PASS entity ID/UUID/type, distance, distinct entity reach and range attribute changes')

  await command('/setblock 3 201 0 minecraft:stone')
  await command('/setblock 2 200 0 minecraft:stone')
  await command('/setblock 2 201 0 minecraft:water')
  const throughWater = await aim(3.5, 201.5, 0.5, 'block')
  assert.equal(throughWater.target.block.id, 'minecraft:stone')
  assert.equal(throughWater.target.block.x, 3)
  await command('/setblock 3 201 0 minecraft:air')
  checkMiss(await aim(3.5, 201.5, 0.5, 'miss'))
  console.log('PASS fluids skipped, solid behind water selected, fluid-only miss')
} finally {
  await call('mc_disconnect')
}
const afterDisconnect = await call('mc_get_target')
assert.equal(afterDisconnect.isError, true)
assert.match(afterDisconnect.content[0].text, /mc_join_world|mc_join_server/)
console.log('PASS disconnect clears access to any cached target')
