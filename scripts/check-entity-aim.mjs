import assert from 'node:assert/strict'
import { setTimeout as delay } from 'node:timers/promises'

const [endpoint, folder] = process.argv.slice(2)
if (!endpoint || !folder) {
  throw new Error('Usage: node scripts/check-entity-aim.mjs <http://127.0.0.1:port/mcp> <disposable-world-folder-with-cheats>')
}

let requestId = 0
let commandId = 0
const fixtureTag = 'driver_entity_aim_check'
const remoteX = 512

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

async function entities() {
  const result = await call('mc_list_entities', { name_contains: 'Driver aim ', radius: 256 })
  assert.ok(!result.isError, JSON.stringify(result.content))
  assert.equal(result.content.length, 2, 'Entity array and metadata remain separate text contents')
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

async function command(text) {
  const messages = await value('mc_read_messages')
  await value('mc_send_chat', { text })
  const marker = `driver_entity_aim_barrier_${++commandId}`
  await value('mc_send_chat', { text: `/say ${marker}` })
  const reply = await value('mc_wait_for', { message: marker, since: messages.latest, timeout_ms: 5000 })
  assert.equal(reply.matched, true, `Commands did not complete; verify cheats are enabled: ${text}`)
}

function near(actual, expected, label, tolerance = 1e-4) {
  assert.ok(Number.isFinite(actual) && Math.abs(actual - expected) < tolerance, `${label}: ${actual}, expected ${expected}`)
}

function checkGeometry(entity, x, z) {
  for (const field of ['id', 'type', 'name', 'x', 'y', 'z', 'distance', 'eyePosition', 'boundingBox']) {
    assert.ok(Object.hasOwn(entity, field), `Entity retains/adds ${field}`)
  }
  assert.ok(Number.isInteger(entity.id))
  assert.equal(entity.type, 'minecraft:zombie')
  near(entity.x, x, 'Feet x')
  near(entity.y, 260, 'Feet y')
  near(entity.z, z, 'Feet z')
  near(entity.distance, 3, 'Feet distance')
  const box = entity.boundingBox
  for (const field of ['minX', 'minY', 'minZ', 'maxX', 'maxY', 'maxZ']) assert.ok(Number.isFinite(box[field]), field)
  assert.ok(box.minX < box.maxX && box.minY < box.maxY && box.minZ < box.maxZ, 'Nonempty bounds')
  near((box.minX + box.maxX) / 2, x, 'Bounds centered at world x')
  near(box.minY, 260, 'Bounds start at world feet y')
  near((box.minZ + box.maxZ) / 2, z, 'Bounds centered at world z')
  near(entity.eyePosition.x, x, 'Eye world x')
  near(entity.eyePosition.z, z, 'Eye world z')
  assert.ok(entity.eyePosition.y > box.minY && entity.eyePosition.y < box.maxY, 'Eye lies inside vertical bounds')
}

function center(entity) {
  const box = entity.boundingBox
  return { x: (box.minX + box.maxX) / 2, y: (box.minY + box.maxY) / 2, z: (box.minZ + box.maxZ) / 2 }
}

function checkDirection(state, origin, point) {
  assert.ok(state.connected && state.player, 'Look returns the existing client state shape')
  const dx = point.x - origin.x
  const dy = point.y - origin.y
  const dz = point.z - origin.z
  const length = Math.hypot(dx, dy, dz)
  const yaw = state.player.yaw * Math.PI / 180
  const pitch = state.player.pitch * Math.PI / 180
  // Compare the resulting gaze vector with the requested point, independently of angle wrapping.
  near(-Math.sin(yaw) * Math.cos(pitch), dx / length, 'Gaze x')
  near(-Math.sin(pitch), dy / length, 'Gaze y')
  near(Math.cos(yaw) * Math.cos(pitch), dz / length, 'Gaze z')
}

async function aim(args, point) {
  const before = await value('mc_get_state')
  const { origin } = await value('mc_get_target')
  const state = await value('mc_look_at', args)
  checkDirection(state, origin, point)
  for (const axis of ['x', 'y', 'z']) near(state.player[axis], before.player[axis], `Aim preserves player ${axis}`)
  return state
}

async function invalid(args, pattern) {
  const result = await call('mc_look_at', args)
  assert.equal(result.isError, true, `Expected rejection: ${JSON.stringify(args)}`)
  assert.match(result.content[0].text, pattern)
}

function summon(label, x, z, baby) {
  const name = JSON.stringify({ text: `Driver aim ${label}` })
  return `/summon minecraft:zombie ${x} 260 ${z} {NoAI:1b,NoGravity:1b,Silent:1b,Invulnerable:1b,IsBaby:${baby ? 1 : 0}b,CustomName:'${name}',Tags:["${fixtureTag}","${fixtureTag}_${label}"]}`
}

await rpc('initialize', {
  protocolVersion: '2025-03-26', capabilities: {}, clientInfo: { name: 'entity-aim-check', version: '1' },
})
const initialized = await fetch(endpoint, {
  method: 'POST',
  headers: { 'Content-Type': 'application/json', Accept: 'application/json, text/event-stream' },
  body: JSON.stringify({ jsonrpc: '2.0', method: 'notifications/initialized' }),
  signal: AbortSignal.timeout(10_000),
})
assert.equal(initialized.status, 202)
const { tools } = await rpc('tools/list', {})
const look = tools.find(tool => tool.name === 'mc_look_at')
assert.ok(look, 'mc_look_at is advertised')
assert.ok(tools.some(tool => tool.name === 'mc_list_entities'))
assert.ok(tools.some(tool => tool.name === 'mc_get_target'))
assert.equal(look.inputSchema.properties.entity_id.type, 'integer')
assert.deepEqual(look.inputSchema.properties.aim_point.enum, ['eye', 'center'])
for (const coordinate of ['x', 'y', 'z']) assert.equal(look.inputSchema.properties[coordinate].type, 'number')
assert.deepEqual(look.annotations, {
  readOnlyHint: false, destructiveHint: false, idempotentHint: true, openWorldHint: false,
})
assert.equal((await value('mc_get_state')).connected, false, 'Start at the title screen')
for (const args of [{ x: 0, y: 260, z: 0 }, { entity_id: 1 }]) {
  await invalid(args, /mc_join_world|mc_join_server/)
}
console.log('PASS initialize, advertised coordinate/entity schemas, annotations, actionable title-screen errors')

const join = await call('mc_join_world', { folder })
assert.ok(!join.isError, JSON.stringify(join.content))
try {
  await until(() => value('mc_get_state'), state => state.player !== null && state.screen === null, 'World loaded', 60_000)
  await value('mc_close_screen')
  await command('/gamemode creative')
  await command('/difficulty normal')
  await command('/time set midnight')
  await command('/execute in minecraft:overworld run tp @s 0.5 260 0.5 0 0')
  await command('/fill -2 259 -2 2 259 2 minecraft:stone')
  await command('/fill -2 260 -2 20 264 5 minecraft:air')
  await command('/tp @s 0.5 260 0.5 0 0')
  await until(() => value('mc_get_state'), state => state.player?.y === 260, 'Player settled')
  await command(`/kill @e[tag=${fixtureTag}]`)
  await command(summon('adult', 3.5, 0.5, false))
  await command(summon('baby', 0.5, 3.5, true))
  const fixtures = await until(entities, items => items.length === 2, 'Both fixtures tracked')
  const adult = fixtures.find(entity => entity.name === 'Driver aim adult')
  const baby = fixtures.find(entity => entity.name === 'Driver aim baby')
  assert.ok(adult && baby, 'Both fixture names are present')
  checkGeometry(adult, 3.5, 0.5)
  checkGeometry(baby, 0.5, 3.5)
  const height = entity => entity.boundingBox.maxY - entity.boundingBox.minY
  assert.ok(height(baby) < height(adult) * 0.75, 'Baby bounds are substantially shorter')
  assert.ok(baby.eyePosition.y < adult.eyePosition.y, 'Eye position follows entity size')
  console.log('PASS retained entity fields, finite world bounds and eye positions for differently sized mobs')

  for (const entity of [adult, baby]) {
    const defaultEye = await aim({ entity_id: entity.id }, entity.eyePosition)
    const explicitEye = await aim({ entity_id: entity.id, aim_point: 'eye' }, entity.eyePosition)
    near(defaultEye.player.pitch, explicitEye.player.pitch, 'Default aim is eye')
    const centered = await aim({ entity_id: entity.id, aim_point: 'center' }, center(entity))
    assert.ok(Math.abs(centered.player.pitch - defaultEye.player.pitch) > 1, 'Center and eye produce distinct pitches')
    await aim({ x: entity.x, y: entity.y, z: entity.z }, entity)
    for (let repeat = 0; repeat < 3; repeat++) await aim({ entity_id: entity.id }, entity.eyePosition)
  }
  await aim({ entity_id: adult.id }, adult.eyePosition)
  const picked = await until(() => value('mc_get_target'), target => target.target.entity?.id === adult.id, 'Visible nearby adult picked')
  assert.equal(picked.target.type, 'entity')
  console.log('PASS default/explicit eye, bounding-box center, repeated aims, legacy coordinates, visible nearby pick')

  await command(`/tp @e[tag=${fixtureTag}_adult] -3.5 261 -2.5`)
  const moved = await until(entities, items => items.some(entity => entity.id === adult.id && entity.x === -3.5 && entity.y === 261), 'Moved entity observed')
  const movedAdult = moved.find(entity => entity.id === adult.id)
  await aim({ entity_id: adult.id }, movedAdult.eyePosition)
  await aim({ entity_id: adult.id, aim_point: 'center' }, center(movedAdult))
  near(movedAdult.boundingBox.minY, 261, 'Moved bounds are current world coordinates')
  console.log('PASS stable entity ID resolves current position after teleport')

  const invalidArguments = [
    [{}, /x|coordinate|entity_id/i],
    [{ x: 1, y: 2 }, /z|coordinate/i],
    [{ x: 1, z: 2 }, /y|coordinate/i],
    [{ y: 1, z: 2 }, /x|coordinate/i],
    [{ entity_id: adult.id, x: 1 }, /coordinate|entity_id|mix|either/i],
    [{ entity_id: adult.id, x: 1, y: 2, z: 3 }, /coordinate|entity_id|mix|either/i],
    [{ aim_point: 'eye' }, /entity_id/i],
    [{ x: 1, y: 2, z: 3, aim_point: 'center' }, /entity_id|aim_point/i],
    [{ entity_id: '1' }, /entity_id/i],
    [{ entity_id: 1.5 }, /entity_id/i],
    [{ entity_id: true }, /entity_id/i],
    [{ entity_id: adult.id, aim_point: 'feet' }, /aim_point/i],
    [{ entity_id: adult.id, aim_point: 1 }, /aim_point/i],
    [{ x: 'not-a-number', y: 2, z: 3 }, /x/i],
    [{ x: 1, y: false, z: 3 }, /y/i],
    [{ x: 1, y: 2, z: [] }, /z/i],
  ]
  for (const [args, pattern] of invalidArguments) await invalid(args, pattern)
  for (const coordinate of ['x', 'y', 'z']) {
    // Numeric strings reach the existing Double parser; overflowing JSON numbers are rejected earlier by the SDK.
    for (const nonfinite of ['NaN', 'Infinity', '-Infinity']) {
      await invalid({ x: 1, y: 260, z: 1, [coordinate]: nonfinite }, /finite|number/i)
    }
  }
  console.log('PASS mixed/incomplete forms, aim-point validation, incorrect types and nonfinite coordinates')

  await command(`/tp @e[tag=${fixtureTag}_adult] 3.5 260 0.5`)
  await until(entities, items => items.some(entity => entity.id === adult.id && entity.x === 3.5 && entity.y === 260), 'Adult returned')
  await command('/fill 2 260 0 2 262 0 minecraft:stone')
  await aim({ entity_id: adult.id }, adult.eyePosition)
  const blocked = await until(() => value('mc_get_target'), target => target.target.type === 'block', 'Wall blocks pick')
  assert.equal(blocked.target.block.x, 2)
  await command('/fill 2 260 0 2 262 0 minecraft:air')
  await command(`/tp @e[tag=${fixtureTag}_adult] 16.5 260 0.5`)
  const distant = await until(entities, items => items.some(entity => entity.id === adult.id && entity.x === 16.5), 'Distant entity still loaded')
  await aim({ entity_id: adult.id }, distant.find(entity => entity.id === adult.id).eyePosition)
  const missed = await until(() => value('mc_get_target'), target => target.target.type === 'miss', 'Entity beyond interaction reach')
  assert.ok(missed.entityReach < 15, 'Fixture lies beyond current entity reach')
  console.log('PASS aiming succeeds through a wall and beyond reach while the actual crosshair pick remains blocked/miss')

  await command(`/kill @e[tag=${fixtureTag}_baby]`)
  await until(entities, items => !items.some(entity => entity.id === baby.id), 'Removed baby no longer tracked')
  await invalid({ entity_id: baby.id }, /mc_list_entities/)
  await command(`/forceload add ${remoteX} 0`)
  await command(`/tp @e[tag=${fixtureTag}_adult] ${remoteX + 0.5} 260 0.5`)
  // Absence from a radius-limited list alone cannot prove unloading; require lookup itself to fail.
  const unloaded = await until(
    () => call('mc_look_at', { entity_id: adult.id }),
    result => result.isError === true,
    'Far-teleported entity unloaded from the client',
    30_000,
  )
  assert.match(unloaded.content[0].text, /mc_list_entities/)
  assert.ok(!(await entities()).some(entity => entity.id === adult.id))
  console.log('PASS removed/unloaded entity IDs fail with an actionable mc_list_entities retry')
} finally {
  try {
    await command(`/kill @e[tag=${fixtureTag}]`)
    await command(`/forceload remove ${remoteX} 0`)
    await command('/fill 2 260 0 2 262 0 minecraft:air')
  } finally {
    await call('mc_disconnect')
  }
}
await invalid({ entity_id: 1 }, /mc_join_world|mc_join_server/)
console.log('PASS cleanup and disconnect clear access to entity aiming')
