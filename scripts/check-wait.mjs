import assert from 'node:assert/strict'
import { setTimeout as delay } from 'node:timers/promises'

const [endpoint, folder] = process.argv.slice(2)
if (!endpoint || !folder) {
  throw new Error('Usage: node scripts/check-wait.mjs <http://127.0.0.1:port/mcp> <disposable-world-folder-with-cheats>')
}

let requestId = 0
let commandId = 0
const tag = `driver_wait_${Date.now()}`
const item = 'minecraft:diamond'
const type = 'minecraft:armor_stand'

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

async function wait(args) {
  const result = await value('mc_wait_for', { timeout_ms: 5000, ...args })
  assert.equal(typeof result.latest, 'number')
  assert.ok(Array.isArray(result.observations), JSON.stringify(result))
  return result
}

function matched(result, reason) {
  assert.equal(result.matched, true, JSON.stringify(result))
  assert.equal(result.reason, reason, JSON.stringify(result))
  return result
}

function timedOut(result) {
  assert.equal(result.matched, false, JSON.stringify(result))
  assert.equal(result.reason, 'timeout')
  assert.ok(typeof result.nextStep === 'string' && result.nextStep.trim().length > 0, 'Timeout gives a next step')
  return result
}

function observation(result, condition, expectedMatched, expectedValue) {
  const actual = result.observations.find(entry => entry.condition === condition)
  assert.ok(actual, `${condition}: ${JSON.stringify(result)}`)
  assert.equal(actual.matched, expectedMatched, JSON.stringify(result))
  assert.equal(actual.value, expectedValue, JSON.stringify(result))
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

async function command(text) {
  const messages = await value('mc_read_messages')
  await value('mc_send_chat', { text })
  const marker = `${tag}_command_${++commandId}`
  await value('mc_send_chat', { text: `/say ${marker}` })
  matched(await wait({ message: marker, since: messages.latest }), 'message')
}

async function transition(args, change, reason) {
  // Start a wait while its predicate is false, then mutate the server independently.
  const pending = wait(args)
  await delay(250)
  await change()
  return matched(await pending, reason)
}

await rpc('initialize', {
  protocolVersion: '2025-03-26', capabilities: {}, clientInfo: { name: 'condition-wait-check', version: '1' },
})
const initialized = await fetch(endpoint, {
  method: 'POST',
  headers: { 'Content-Type': 'application/json', Accept: 'application/json, text/event-stream' },
  body: JSON.stringify({ jsonrpc: '2.0', method: 'notifications/initialized' }),
  signal: AbortSignal.timeout(10_000),
})
assert.equal(initialized.status, 202)
const tools = await rpc('tools/list', {})
const definition = tools.tools.find(tool => tool.name === 'mc_wait_for')
assert.ok(definition, 'mc_wait_for is advertised')
const properties = definition.inputSchema.properties
assert.deepEqual(Object.keys(properties).sort(), ['entity', 'health', 'inventory', 'message', 'mode', 'screen', 'since', 'timeout_ms'])
assert.deepEqual(definition.inputSchema.required ?? [], [])
for (const key of ['message', 'screen', 'mode']) assert.equal(properties[key].type, 'string')
for (const key of ['since', 'timeout_ms']) assert.equal(properties[key].type, 'integer')
assert.deepEqual(properties.mode.enum, ['any', 'all'])
assert.equal(properties.mode.default, 'any')
assert.equal(properties.timeout_ms.default, 10_000)
for (const key of ['inventory', 'entity', 'health']) assert.equal(properties[key].type, 'object')
assert.deepEqual(properties.inventory.required, ['item'])
assert.deepEqual(properties.entity.required, ['type'])
assert.equal(properties.inventory.properties.item.type, 'string')
assert.equal(properties.entity.properties.type.type, 'string')
for (const key of ['inventory', 'entity']) {
  for (const bound of ['min_count', 'max_count']) assert.equal(properties[key].properties[bound].type, 'integer')
  assert.equal(properties[key].properties.min_count.default, 1)
}
assert.equal(properties.entity.properties.radius.type, 'number')
assert.equal(properties.entity.properties.radius.default, 64)
assert.equal(properties.entity.properties.radius.exclusiveMinimum, 0)
assert.equal(properties.entity.properties.radius.maximum, 256)
for (const key of ['min', 'max']) assert.equal(properties.health.properties[key].type, 'number')
assert.deepEqual(definition.annotations, {
  readOnlyHint: true, destructiveHint: false, idempotentHint: true, openWorldHint: false,
})
assert.equal((await value('mc_get_state')).connected, false, 'Start at the title screen')
matched(await value('mc_wait_for', { screen: 'titlescreen' }), 'screen')
console.log('PASS initialize, advertised nested schemas and annotations, title-screen precondition')

const invalid = [
  {}, { mode: 'all' }, { message: 1 }, { screen: [] }, { message: 'x', mode: 'both' },
  { inventory: {} }, { inventory: { item: 1 } }, { inventory: { item, min_count: -1 } },
  { inventory: { item, min_count: 1.5 } }, { inventory: { item, max_count: '2' } },
  { inventory: { item, min_count: 2, max_count: 1 } }, { inventory: [] },
  { entity: {} }, { entity: { type, radius: 0 } }, { entity: { type, radius: 257 } },
  { entity: { type, radius: '64' } }, { entity: { type, max_count: -1 } },
  { entity: { type, min_count: 2, max_count: 1 } },
  { health: {} }, { health: { min: -1 } }, { health: { max: '20' } },
  { health: { min: 20, max: 10 } }, { health: false },
  { inventory: { item: 'Minecraft:diamond' } }, { entity: { type: 'Minecraft:armor_stand' } },
]
for (const args of invalid) {
  const result = await call('mc_wait_for', args)
  assert.equal(result.isError, true, `Expected rejection: ${JSON.stringify(args)}`)
  assert.ok(result.content[0].text.trim().length > 0)
}
const noWorld = timedOut(await wait({
  mode: 'all', inventory: { item, min_count: 0, max_count: 0 },
  entity: { type, min_count: 0, max_count: 0 }, health: { min: 0 }, timeout_ms: 150,
}))
for (const condition of ['inventory', 'entity', 'health']) observation(noWorld, condition, false, null)
assert.equal(noWorld.state.connected, false)
console.log('PASS invalid inputs and no-world predicates remain unmet even when zero would match')

await value('mc_join_world', { folder })
try {
  await until(() => value('mc_get_state'), state => state.player !== null && state.screen === null, 'World loaded', 60_000)
  await command('/gamemode creative')
  await command('/execute in minecraft:overworld run tp @s 16000.5 240 16000.5 0 0')
  await command('/fill 15998 239 15998 16003 239 16003 minecraft:stone')
  await command('/tp @s 16000.5 240 16000.5 0 0')
  await command('/clear @s')
  observation(matched(await wait({ inventory: { item, min_count: 0, max_count: 0 } }), 'inventory'), 'inventory', true, 0)
  const added = await transition({ inventory: { item, min_count: 3, max_count: 3 } }, () => command(`/give @s ${item} 3`), 'inventory')
  observation(added, 'inventory', true, 3)
  observation(matched(await wait({ inventory: { item } }), 'inventory'), 'inventory', true, 3)
  observation(timedOut(await wait({ inventory: { item, max_count: 2 }, timeout_ms: 150 })), 'inventory', false, 3)
  timedOut(await wait({ inventory: { item: 'minecraft:diam', min_count: 1 }, timeout_ms: 150 }))
  observation(await transition({ inventory: { item, min_count: 0, max_count: 0 } }, () => command('/clear @s'), 'inventory'), 'inventory', true, 0)
  console.log('PASS inventory defaults, exact IDs, inclusive counts, active wait for give and absence')

  const entity = { type, radius: 4 }
  observation(matched(await wait({ entity: { ...entity, min_count: 0, max_count: 0 } }), 'entity'), 'entity', true, 0)
  const summoned = await transition({ entity }, () => command(
    `/summon ${type} 16002.5 240 16000.5 {NoGravity:1b,Marker:1b,Tags:["${tag}"]}`,
  ), 'entity')
  observation(summoned, 'entity', true, 1)
  observation(matched(await wait({ entity: { ...entity, min_count: 1, max_count: 1 } }), 'entity'), 'entity', true, 1)
  observation(matched(await wait({ entity: { ...entity, radius: 2, min_count: 0, max_count: 0 } }), 'entity'), 'entity', true, 0)
  matched(await wait({ entity: { ...entity, radius: 2.01 } }), 'entity')
  timedOut(await wait({ entity: { type: 'minecraft:armor_' }, timeout_ms: 150 }))
  observation(await transition(
    { entity: { ...entity, min_count: 0, max_count: 0 } }, () => command(`/kill @e[tag=${tag}]`), 'entity',
  ), 'entity', true, 0)
  console.log('PASS entity exact IDs, inclusive counts, exclusive feet radius, summon and removal transitions')

  await command('/gamerule naturalRegeneration false')
  await command('/effect clear @s')
  await command('/gamemode survival')
  await command('/effect give @s minecraft:instant_health 1 5')
  observation(matched(await wait({ health: { min: 20, max: 20 } }), 'health'), 'health', true, 20)
  await command('/effect give @s minecraft:absorption 60 1')
  observation(matched(await wait({ health: { min: 20, max: 20 } }), 'health'), 'health', true, 20)
  await command('/effect clear @s')
  observation(await transition({ health: { min: 16, max: 16 } }, () => command('/damage @s 4 minecraft:generic'), 'health'), 'health', true, 16)
  observation(await transition({ health: { min: 20 } }, () => command('/effect give @s minecraft:instant_health 1 5'), 'health'), 'health', true, 20)
  console.log('PASS inclusive health points, absorption excluded, damage and healing during active waits')

  // A buffered message remains eligible when the other conditions change later.
  const messages = await value('mc_read_messages')
  const marker = `${tag}_all`
  await command(`/say ${marker}`)
  const combined = await transition({
    mode: 'all', message: marker.toUpperCase(), since: messages.latest, screen: 'none',
    inventory: { item }, health: { min: 20, max: 20 },
  }, () => command(`/give @s ${item}`), 'all')
  assert.ok(combined.message.text.includes(marker))
  assert.ok(combined.observations.every(entry => entry.matched))
  observation(combined, 'inventory', true, 1)
  observation(combined, 'health', true, 20)
  matched(await wait({ message: marker, since: messages.latest, screen: 'none', inventory: { item } }), 'message')
  matched(await wait({ screen: 'none', inventory: { item }, health: { min: 20 } }), 'screen')
  matched(await wait({ inventory: { item }, health: { min: 20 } }), 'inventory')
  matched(await wait({ entity: { type, min_count: 0, max_count: 0 }, health: { min: 20 } }), 'entity')
  matched(await wait({ mode: 'all', health: { min: 20 } }), 'health')
  matched(await wait({ mode: 'any', inventory: { item }, health: { max: 1 } }), 'inventory')
  timedOut(await wait({ mode: 'all', inventory: { item }, health: { max: 1 }, timeout_ms: 150 }))
  matched(await wait({ health: { min: 20 }, timeout_ms: 60_000 }), 'health')
  console.log('PASS all combined conditions, buffered case-insensitive message, default/explicit any and reason priority')

  // Inventory and screen are true at different times, but never in the same poll.
  const pending = wait({ mode: 'all', inventory: { item }, screen: 'InventoryScreen', timeout_ms: 5000 })
  await delay(250)
  await command('/clear @s')
  matched(await wait({ inventory: { item, min_count: 0, max_count: 0 } }), 'inventory')
  await value('mc_set_key', { name: 'inventory' })
  matched(await wait({ screen: 'InventoryScreen' }), 'screen')
  timedOut(await pending)
  await value('mc_close_screen')
  console.log('PASS all does not latch inventory success across later screen changes')

  await command(`/kill @e[tag=${tag}]`)
  const disconnectedWait = wait({
    mode: 'all', inventory: { item }, entity: { type }, health: { max: 1 }, timeout_ms: 10_000,
  })
  await delay(250)
  await value('mc_disconnect')
  const disconnected = timedOut(await disconnectedWait)
  for (const condition of ['inventory', 'entity', 'health']) observation(disconnected, condition, false, null)
  assert.equal(disconnected.state.connected, false)
  console.log('PASS disconnect during active wait reports timeout with unavailable observations')
} finally {
  if ((await value('mc_get_state')).connected) {
    try {
      await value('mc_close_screen')
      await command(`/kill @e[tag=${tag}]`)
      await command('/effect clear @s')
      await command('/gamemode creative')
    } finally {
      await call('mc_disconnect')
    }
  }
}
console.log('PASS wait runtime check; cancellation and exact polling deadlines require unit tests')
