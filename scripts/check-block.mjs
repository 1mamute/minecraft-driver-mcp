import assert from 'node:assert/strict'
import { setTimeout as delay } from 'node:timers/promises'

const [endpoint, folder] = process.argv.slice(2)
if (!endpoint || !folder) {
  throw new Error('Usage: node scripts/check-block.mjs <http://127.0.0.1:port/mcp> <disposable-world-folder-with-cheats>')
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
  await value('mc_send_chat', { text: message })
  const marker = `driver_block_check_${++commandId}`
  await value('mc_send_chat', { text: `/say ${marker}` })
  const reply = await value('mc_wait_for', { message: marker, since: messages.latest, timeout_ms: 5000 })
  assert.equal(reply.matched, true, `Commands did not complete; verify cheats are enabled: ${message}`)
}

async function blockAt(x, y, z, id) {
  const snapshot = await until(
    () => value('mc_get_block', { x, y, z }),
    result => result.loaded && result.block?.id === id,
    `Expected ${id} at ${x}, ${y}, ${z}`,
  )
  assert.equal(snapshot.x, x)
  assert.equal(snapshot.y, y)
  assert.equal(snapshot.z, z)
  assert.equal(snapshot.dimension, 'minecraft:overworld')
  assert.equal(snapshot.loaded, true)
  assert.equal(typeof snapshot.block.properties, 'object')
  assert.notEqual(snapshot.block.properties, null)
  for (const property of Object.values(snapshot.block.properties)) assert.equal(typeof property, 'string')
  return snapshot
}

async function invalid(args, expected) {
  const result = await call('mc_get_block', args)
  assert.equal(result.isError, true, `Expected rejection of ${JSON.stringify(args)}`)
  assert.match(result.content[0].text, expected)
}

await rpc('initialize', {
  protocolVersion: '2025-03-26', capabilities: {}, clientInfo: { name: 'block-observation-check', version: '1' },
})
const initialized = await fetch(endpoint, {
  method: 'POST',
  headers: { 'Content-Type': 'application/json', Accept: 'application/json, text/event-stream' },
  body: JSON.stringify({ jsonrpc: '2.0', method: 'notifications/initialized' }),
  signal: AbortSignal.timeout(10_000),
})
assert.equal(initialized.status, 202)
const tools = await rpc('tools/list', {})
const definition = tools.tools.find(tool => tool.name === 'mc_get_block')
assert.ok(definition, 'mc_get_block is advertised')
assert.deepEqual(Object.keys(definition.inputSchema.properties).sort(), ['x', 'y', 'z'])
assert.deepEqual([...definition.inputSchema.required].sort(), ['x', 'y', 'z'])
for (const axis of ['x', 'y', 'z']) assert.equal(definition.inputSchema.properties[axis].type, 'integer')
assert.deepEqual(definition.annotations, {
  readOnlyHint: true, destructiveHint: false, idempotentHint: true, openWorldHint: false,
})
assert.equal((await value('mc_get_state')).connected, false, 'Start the client at the title screen')
await invalid({ x: 0, y: 300, z: 0 }, /mc_join_world|mc_join_server/)
console.log('PASS initialize, integer coordinate schema/annotations, actionable title-screen error')

const join = await call('mc_join_world', { folder })
assert.ok(!join.isError, JSON.stringify(join.content))
try {
  await until(() => value('mc_get_state'), state => state.player !== null && state.screen === null, 'World loaded', 60_000)
  await value('mc_close_screen')
  // Fixtures use the Minecraft 1.21.1 command format and overwrite this area of the disposable world.
  await command('/gamemode creative')
  await command('/execute in minecraft:overworld run tp @s -8.5 301 -8.5 0 0')
  await command('/fill -20 299 -20 5 299 5 minecraft:stone')
  await command('/fill -20 300 -20 5 303 5 minecraft:air')
  await command('/tp @s -8.5 300 -8.5 0 0')
  await command('/setblock -1 300 -1 minecraft:stone')
  await command('/setblock -17 300 -17 minecraft:stone')
  await command('/setblock -2 300 -2 minecraft:oak_stairs[facing=east,half=top,shape=straight,waterlogged=false]')

  const air = await blockAt(0, 300, 0, 'minecraft:air')
  assert.deepEqual(air.block.properties, {})
  assert.equal(air.block.blockEntity, null)
  for (const coordinate of [-1, -17]) {
    const stone = await blockAt(coordinate, 300, coordinate, 'minecraft:stone')
    assert.deepEqual(stone.block.properties, {})
    assert.equal(stone.block.blockEntity, null)
  }
  const stairs = await blockAt(-2, 300, -2, 'minecraft:oak_stairs')
  assert.deepEqual(stairs.block.properties, { facing: 'east', half: 'top', shape: 'straight', waterlogged: 'false' })
  assert.equal(stairs.block.blockEntity, null)
  console.log('PASS loaded air/stone, string-valued block properties, negative coordinates across chunk boundaries')

  const signMarker = 'driver_block_visible_sign'
  await command(`/setblock -3 300 -3 minecraft:oak_sign{front_text:{messages:['{"text":"${signMarker}"}','""','""','""']}}`)
  const sign = await until(
    () => blockAt(-3, 300, -3, 'minecraft:oak_sign'),
    result => result.block.blockEntity?.updateSnbt?.includes(signMarker),
    'Synchronized sign text',
  )
  assert.equal(sign.block.blockEntity.type, 'minecraft:sign')
  assert.equal(sign.block.blockEntity.status, 'available')
  assert.ok(sign.block.blockEntity.updateSnbt.length <= 16384)

  await command('/setblock -4 300 -4 minecraft:chest')
  await command('/item replace block -4 300 -4 container.0 with minecraft:diamond 7')
  const messages = await value('mc_read_messages')
  const serverMarker = 'driver_block_server_inventory_present'
  await command(`/execute if data block -4 300 -4 Items[{id:"minecraft:diamond"}] run say ${serverMarker}`)
  const serverInventory = await value('mc_wait_for', { message: serverMarker, since: messages.latest, timeout_ms: 5000 })
  assert.equal(serverInventory.matched, true, 'The chest fixture must contain a server-side diamond stack')
  const chest = await blockAt(-4, 300, -4, 'minecraft:chest')
  assert.deepEqual(chest.block.blockEntity, {
    type: 'minecraft:chest', status: 'empty_update_tag', updateSnbt: '{}',
  })
  assert.doesNotMatch(chest.block.blockEntity.updateSnbt, /Items|diamond|Count|count/)
  console.log('PASS synchronized sign update SNBT, empty chest update tag without server-only inventory data')

  await command('/setblock -5 300 -5 minecraft:white_banner{patterns:[{pattern:"minecraft:stripe_bottom",color:"red"}]}')
  for (let i = 0; i < 9; i++) {
    await command('/data modify block -5 300 -5 patterns append from block -5 300 -5 patterns[]')
  }
  const oversized = await until(
    () => blockAt(-5, 300, -5, 'minecraft:white_banner'),
    result => result.block.blockEntity?.status === 'too_large',
    'Oversized client update tag omitted',
  )
  assert.equal(oversized.block.blockEntity.type, 'minecraft:banner')
  assert.equal(oversized.block.blockEntity.updateSnbt, null)
  await command('/setblock -5 300 -5 minecraft:air')
  console.log('PASS oversized block-entity update SNBT omitted entirely')

  const far = { x: 10_000_000, y: 300, z: -10_000_000 }
  for (let i = 0; i < 3; i++) {
    assert.deepEqual(await value('mc_get_block', far), { ...far, dimension: 'minecraft:overworld', loaded: false, block: null })
  }
  console.log('PASS repeated unloaded-chunk reads return null rather than fabricated air')

  for (const axis of ['x', 'y', 'z']) {
    const valid = { x: 0, y: 300, z: 0 }
    const missing = { ...valid }
    delete missing[axis]
    await invalid(missing, new RegExp(axis))
    for (const input of [null, 0.5, '0', true, [], {}, 2147483648]) {
      await invalid({ ...valid, [axis]: input }, new RegExp(axis))
    }
  }
  for (const args of [
    { x: -30_000_001, y: 300, z: 0 }, { x: 30_000_000, y: 300, z: 0 },
    { x: 0, y: 300, z: -30_000_001 }, { x: 0, y: 300, z: 30_000_000 },
    { x: 0, y: -65, z: 0 }, { x: 0, y: 320, z: 0 },
  ]) await invalid(args, /bound|between|range|height|world/i)
  console.log('PASS missing/null/non-integer coordinates and world-bound validation')

  await until(() => value('mc_get_state'), state => state.player?.y === 300, 'Player settled on fixture platform')
  const before = await value('mc_get_state')
  const inventoryBefore = await value('mc_read_inventory')
  for (let i = 0; i < 3; i++) {
    assert.deepEqual(await value('mc_get_block', { x: -2, y: 300, z: -2 }), stairs)
    assert.deepEqual(await value('mc_get_block', { x: -3, y: 300, z: -3 }), sign)
    assert.deepEqual(await value('mc_get_block', { x: -4, y: 300, z: -4 }), chest)
  }
  assert.deepEqual((await value('mc_get_state')).player, before.player)
  assert.deepEqual(await value('mc_read_inventory'), inventoryBefore)
  await command('/setblock -1 300 -1 minecraft:gold_block')
  await blockAt(-1, 300, -1, 'minecraft:gold_block')
  console.log('PASS repeated reads preserve player/inventory state and observe subsequent server block updates')
} finally {
  await call('mc_disconnect')
}
await invalid({ x: -1, y: 300, z: -1 }, /mc_join_world|mc_join_server/)
console.log('PASS disconnect clears access to the world')
