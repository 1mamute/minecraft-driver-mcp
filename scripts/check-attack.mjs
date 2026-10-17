import assert from 'node:assert/strict'
import { setTimeout as delay } from 'node:timers/promises'

const [endpoint, folder] = process.argv.slice(2)
if (!endpoint || !folder) {
  throw new Error('Usage: node scripts/check-attack.mjs <http://127.0.0.1:port/mcp> <disposable-world-folder-with-cheats>')
}

let requestId = 0
let commandId = 0
const tag = 'driver_attack_check'
const outcomes = new Set([
  'pressed', 'hold_complete', 'block_changed', 'target_changed', 'screen_opened',
  'disconnected', 'hands_busy', 'input_released', 'timed_out', 'cancelled',
])

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

async function command(text) {
  const messages = await value('mc_read_messages')
  await value('mc_send_chat', { text })
  const marker = `driver_attack_command_${++commandId}`
  await value('mc_send_chat', { text: `/say ${marker}` })
  const reply = await value('mc_wait_for', { message: marker, since: messages.latest, timeout_ms: 5000 })
  assert.equal(reply.matched, true, `Commands did not complete; verify cheats are enabled: ${text}`)
}

async function serverBlock(x, id, y = 281, z = 0) {
  const messages = await value('mc_read_messages')
  const marker = `driver_attack_block_${++commandId}`
  await command(`/execute if block ${x} ${y} ${z} ${id} run say ${marker}`)
  const reply = await value('mc_wait_for', { message: marker, since: messages.latest, timeout_ms: 5000 })
  assert.equal(reply.matched, true, `Server must retain ${id} at ${x}, ${y}, ${z}`)
  await until(
    () => value('mc_get_block', { x, y, z }),
    result => result.loaded && result.block?.id === id.split('[')[0],
    `Client must observe ${id} at ${x}, ${y}, ${z}`,
  )
}

async function aim(x, y, z, type) {
  await value('mc_look_at', { x, y, z })
  // mc_attack consumes vanilla's cached rendered pick, so let a frame update it.
  await delay(250)
  return until(() => value('mc_get_target'), result => result.target.type === type, `Expected ${type} target`)
}

async function aimBlock() {
  const snapshot = await aim(3.5, 281.5, 0.5, 'block')
  assert.equal(snapshot.target.block.x, 3)
  assert.equal(snapshot.target.block.y, 281)
  assert.equal(snapshot.target.block.z, 0)
  return snapshot
}

async function equip(id) {
  await command('/clear @s')
  if (id) await command(`/item replace entity @s hotbar.0 with ${id}`)
  await value('mc_select_hotbar_slot', { slot: 0 })
  await delay(250)
}

async function fixture(id, tool = 'minecraft:iron_pickaxe') {
  await command('/gamemode creative')
  await command('/tp @s 0.5 280 0.5 -90 0')
  await command('/setblock 3 281 0 minecraft:air')
  await command(`/setblock 3 281 0 ${id}`)
  await command('/setblock 4 281 0 minecraft:stone')
  await equip(tool)
  await command('/gamemode survival')
  await aimBlock()
}

function checkResult(result, requested, type, expectedOutcome) {
  assert.equal(result.requestedHoldTicks, requested)
  assert.ok(Number.isInteger(result.elapsedTicks))
  assert.ok(result.elapsedTicks >= 0 && result.elapsedTicks <= requested, `Elapsed ticks: ${JSON.stringify(result)}`)
  assert.ok(outcomes.has(result.outcome), `Unknown outcome: ${JSON.stringify(result)}`)
  if (expectedOutcome) assert.equal(result.outcome, expectedOutcome, JSON.stringify(result))
  assert.equal(result.serverConfirmed, false)
  assert.equal(result.target.target.type, type)
  assert.equal(typeof result.target.origin.x, 'number')
  assert.equal(typeof result.target.blockReach, 'number')
  assert.equal(typeof result.target.entityReach, 'number')
  if (type === 'block' && result.outcome === 'disconnected') {
    assert.ok(result.blockChanged === null || typeof result.blockChanged === 'boolean')
  } else if (type === 'block') assert.equal(typeof result.blockChanged, 'boolean')
  else assert.equal(result.blockChanged, null)
  return result
}

async function attack(holdTicks, type = 'block', outcome) {
  const args = holdTicks === undefined ? {} : { hold_ticks: holdTicks }
  return checkResult(await value('mc_attack', args), holdTicks ?? 0, type, outcome)
}

async function invalid(holdTicks) {
  const result = await call('mc_attack', { hold_ticks: holdTicks })
  assert.equal(result.isError, true, `Expected rejection of hold_ticks=${JSON.stringify(holdTicks)}`)
  assert.match(result.content[0].text, /hold_ticks/i)
}

async function refused(expected) {
  const result = await call('mc_attack')
  assert.equal(result.isError, true)
  assert.match(result.content[0].text, expected)
}

async function joinWorld() {
  await value('mc_join_world', { folder })
  await until(() => value('mc_get_state'), state => state.player !== null && state.screen === null, 'World loaded', 60_000)
  await value('mc_close_screen')
}

async function retainedAfterRelease(id = 'minecraft:stone') {
  await delay(2000)
  await serverBlock(3, id)
  await serverBlock(4, 'minecraft:stone')
}

async function replacementAfterRelease() {
  // A leaked key would start mining this replacement without another mc_attack.
  await command('/setblock 3 281 0 minecraft:stone')
  await aimBlock()
  await retainedAfterRelease()
}

async function interruptedBy(label, interrupt, expected) {
  await fixture('minecraft:obsidian')
  const pending = attack(200, 'block', expected)
  await delay(300)
  await interrupt()
  const result = await pending
  assert.ok(result.elapsedTicks < 200, JSON.stringify(result))
  assert.equal(result.blockChanged, false)
  await value('mc_close_screen')
  await serverBlock(3, 'minecraft:obsidian')
  await replacementAfterRelease()
  console.log(`PASS ${label} interrupts and releases attack without mining a replacement`)
}

async function health() {
  const messages = await value('mc_read_messages')
  await command(`/data get entity @e[tag=${tag},limit=1] Health`)
  const page = await value('mc_read_messages', { since: messages.latest })
  const reply = page.messages.find(message => /entity data:.*[0-9.]+f/i.test(message.text))
  assert.ok(reply, `Expected English /data Health feedback: ${JSON.stringify(page.messages)}`)
  return Number(reply.text.match(/entity data:\s*([0-9.]+)f/i)[1])
}

await rpc('initialize', {
  protocolVersion: '2025-03-26', capabilities: {}, clientInfo: { name: 'attack-check', version: '1' },
})
const initialized = await fetch(endpoint, {
  method: 'POST',
  headers: { 'Content-Type': 'application/json', Accept: 'application/json, text/event-stream' },
  body: JSON.stringify({ jsonrpc: '2.0', method: 'notifications/initialized' }),
  signal: AbortSignal.timeout(10_000),
})
assert.equal(initialized.status, 202)
const tools = await rpc('tools/list', {})
const definition = tools.tools.find(tool => tool.name === 'mc_attack')
assert.ok(definition, 'mc_attack is advertised')
assert.deepEqual(Object.keys(definition.inputSchema.properties), ['hold_ticks'])
assert.equal(definition.inputSchema.properties.hold_ticks.type, 'integer')
assert.equal(definition.inputSchema.properties.hold_ticks.minimum, 0)
assert.equal(definition.inputSchema.properties.hold_ticks.maximum, 200)
assert.equal(definition.inputSchema.properties.hold_ticks.default, 0)
assert.ok(!(definition.inputSchema.required ?? []).includes('hold_ticks'), 'hold_ticks is optional')
assert.deepEqual(definition.annotations, {
  readOnlyHint: false, destructiveHint: true, idempotentHint: false, openWorldHint: false,
})
assert.equal((await value('mc_get_state')).connected, false, 'Start the client at the title screen')
const outsideWorld = await call('mc_attack')
assert.equal(outsideWorld.isError, true)
assert.match(outsideWorld.content[0].text, /mc_join_world|mc_join_server/)
console.log('PASS initialize, optional integer schema, annotations, actionable title-screen error')

try {
  await joinWorld()
  // Minecraft 1.21.1 fixtures overwrite only this high-altitude area of a disposable cheats world.
  await command('/gamemode creative')
  await command('/execute in minecraft:overworld run tp @s 0.5 285 0.5 -90 0')
  await command('/fill -2 279 -3 7 279 3 minecraft:stone')
  await command('/fill -2 280 -3 7 284 3 minecraft:air')
  await command(`/kill @e[tag=${tag}]`)
  await command('/attribute @s minecraft:player.block_interaction_range base set 4.5')
  await command('/attribute @s minecraft:player.entity_interaction_range base set 3')
  await command('/tp @s 0.5 280 0.5 -90 0')
  for (const input of [-1, 201, 0.5, '1', true, [], {}, 2147483648]) await invalid(input)
  console.log('PASS hold_ticks bounds and wrong-type validation')

  await fixture('minecraft:obsidian')
  await value('mc_set_key', { name: 'inventory', down: true })
  await until(() => value('mc_get_state'), state => state.screen !== null, 'Inventory opened')
  await refused(/screen|mc_close_screen/i)
  await value('mc_close_screen')
  await command('/gamemode spectator')
  await refused(/spectator|game mode/i)
  await command('/gamemode survival')
  await aimBlock()
  await command('/item replace entity @s weapon.offhand with minecraft:shield')
  const preexistingUse = value('mc_use', { hold_ticks: 40 })
  await delay(250)
  await refused(/hands.*busy|using.*item|release/i)
  await preexistingUse
  await serverBlock(3, 'minecraft:obsidian')
  console.log('PASS existing inventory, spectator mode and already-using shield refuse attack')

  await fixture('minecraft:stone')
  await command('/gamemode creative')
  const before = await aimBlock()
  const creative = await attack(undefined)
  assert.deepEqual(creative.target, before, 'Result snapshots the cached target before the attack')
  assert.ok(['pressed', 'block_changed'].includes(creative.outcome))
  assert.equal(creative.blockChanged, true)
  await serverBlock(3, 'minecraft:air')
  await serverBlock(4, 'minecraft:stone')
  await replacementAfterRelease()
  console.log('PASS default immediate creative attack, original snapshot, client observation and release')

  const creativeHold = await attack(200, 'block', 'block_changed')
  assert.equal(creativeHold.elapsedTicks, 0)
  assert.equal(creativeHold.blockChanged, true)
  await serverBlock(3, 'minecraft:air')
  await serverBlock(4, 'minecraft:stone')
  await replacementAfterRelease()
  console.log('PASS positive creative hold ends on the initial break and releases before any replacement')

  await fixture('minecraft:stone')
  const nullDefault = await attack(null, 'block', 'pressed')
  assert.equal(nullDefault.elapsedTicks, 0)
  assert.equal(nullDefault.blockChanged, false)
  await retainedAfterRelease()
  const single = await attack(0, 'block', 'pressed')
  assert.equal(single.blockChanged, false)
  await retainedAfterRelease()
  const short = await attack(1, 'block', 'hold_complete')
  assert.equal(short.blockChanged, false)
  assert.equal(short.elapsedTicks, 1)
  await retainedAfterRelease()
  console.log('PASS null defaults to zero; zero/short survival presses do not keep mining after completion')

  const mined = await attack(100, 'block', 'block_changed')
  assert.equal(mined.blockChanged, true)
  assert.ok(mined.elapsedTicks < 100)
  await serverBlock(3, 'minecraft:air')
  await serverBlock(4, 'minecraft:stone')
  await replacementAfterRelease()
  console.log('PASS survival mining stops at the original block, leaving blocks behind and replacements intact')

  await fixture('minecraft:bedrock')
  const capped = await attack(200, 'block', 'hold_complete')
  assert.equal(capped.elapsedTicks, 200)
  assert.equal(capped.blockChanged, false)
  await serverBlock(3, 'minecraft:bedrock')
  await replacementAfterRelease()
  console.log('PASS unbreakable target completes the maximum 200-tick hold and releases')

  await fixture('minecraft:obsidian')
  const pending = attack(200, 'block', 'hold_complete')
  await delay(300)
  const busy = await call('mc_attack', { hold_ticks: 0 })
  assert.equal(busy.isError, true)
  assert.match(busy.content[0].text, /busy|progress|already|active|wait/i)
  const original = await pending
  assert.equal(original.elapsedTicks, 200, 'Rejected concurrent call must not replace or cancel the active hold')
  await serverBlock(3, 'minecraft:obsidian')
  await replacementAfterRelease()
  console.log('PASS concurrent attack is rejected without replacing the active attack')

  await interruptedBy('look-away', () => aim(0.5, 310, 0.5, 'miss'), 'target_changed')
  await interruptedBy('instantly breakable block at a different position', async () => {
    await command('/setblock 3 280 2 minecraft:stone')
    await command('/setblock 3 281 2 minecraft:torch')
    // Vanilla refreshes its pick during the tick; continuation must inspect that fresh pick.
    const snapshot = await aim(3.5, 281.3, 2.5, 'block')
    assert.equal(snapshot.target.block.id, 'minecraft:torch')
    assert.equal(snapshot.target.block.y, 281)
    assert.equal(snapshot.target.block.z, 2)
  }, 'target_changed')
  await serverBlock(3, 'minecraft:torch', 281, 2)
  console.log('PASS refreshed pick never starts breaking the new instant-break target')
  await interruptedBy('inventory', () => value('mc_set_key', { name: 'inventory', down: true }), 'screen_opened')
  await interruptedBy('pause screen', () => value('mc_press_key', { name: 'escape' }), 'screen_opened')
  await fixture('minecraft:obsidian')
  await command('/item replace entity @s weapon.offhand with minecraft:shield')
  const busyHands = attack(200, 'block', 'hands_busy')
  await delay(300)
  const using = value('mc_use', { hold_ticks: 40 })
  assert.equal((await busyHands).blockChanged, false)
  await using
  await serverBlock(3, 'minecraft:obsidian')
  await replacementAfterRelease()
  console.log('PASS using a shield interrupts attack and no mining resumes after use releases')

  await fixture('minecraft:obsidian')
  const changed = attack(200, 'block', 'block_changed')
  await delay(300)
  await command('/setblock 3 281 0 minecraft:stone')
  assert.equal((await changed).blockChanged, true)
  await retainedAfterRelease()
  console.log('PASS server replacement of original block state ends the hold before mining replacement')

  await fixture('minecraft:oak_log[axis=y]')
  const stateChanged = attack(200, 'block', 'block_changed')
  await delay(300)
  await command('/setblock 3 281 0 minecraft:oak_log[axis=x]')
  assert.equal((await stateChanged).blockChanged, true)
  await serverBlock(3, 'minecraft:oak_log[axis=x]')
  await replacementAfterRelease()
  console.log('PASS original state changes interrupt even when the block ID stays the same')

  await command('/setblock 3 281 0 minecraft:air')
  await command('/setblock 4 281 0 minecraft:air')
  await equip()
  await command(`/summon minecraft:cow 2.5 280 0.5 {NoAI:1b,NoGravity:1b,PersistenceRequired:1b,Silent:1b,Tags:["${tag}"]}`)
  for (const holdTicks of [200, 0]) {
    // Restore the same in-reach fixture after vanilla knockback from the previous hit.
    await command(`/tp @e[tag=${tag},limit=1] 2.5 280 0.5`)
    const cow = await aim(2.5, 281, 0.5, 'entity')
    assert.equal(cow.target.entity.type, 'minecraft:cow')
    const healthBefore = await health()
    const hit = await attack(holdTicks, 'entity', 'pressed')
    assert.equal(hit.elapsedTicks, 0)
    const healthAfter = await health()
    assert.ok(healthAfter < healthBefore && healthAfter > 0, `Expected one nonlethal hit: ${healthBefore} -> ${healthAfter}`)
    await delay(2000)
    assert.equal(await health(), healthAfter, 'Entity hold must not repeat attacks after the one press')
  }
  await command(`/kill @e[tag=${tag}]`)
  const miss = await aim(0.5, 310, 0.5, 'miss')
  for (const holdTicks of [200, 0]) {
    const missed = await attack(holdTicks, 'miss', 'pressed')
    assert.equal(missed.elapsedTicks, 0)
    assert.deepEqual(missed.target, miss)
  }
  console.log('PASS entity receives one server-observed hit and miss remains a single press regardless of hold')

  await fixture('minecraft:bedrock')
  try {
    // Vanilla applies the server's tick target to client ticks while frames and MCP remain responsive.
    await command('/tick rate 1')
    const timedOut = await attack(200, 'block', 'timed_out')
    assert.ok(timedOut.elapsedTicks < 200)
    assert.equal(timedOut.blockChanged, false)
  } finally {
    await command('/tick rate 20')
  }
  await serverBlock(3, 'minecraft:bedrock')
  await replacementAfterRelease()
  console.log('PASS slow client ticks reach the wall timeout and release attack before normal ticks resume')

  await fixture('minecraft:obsidian')
  const disconnecting = attack(200, 'block', 'disconnected')
  await delay(300)
  await value('mc_disconnect')
  assert.ok((await disconnecting).elapsedTicks < 200)
  await joinWorld()
  await serverBlock(3, 'minecraft:obsidian')
  await replacementAfterRelease()
  console.log('PASS disconnect resolves the hold and rejoining does not resume mining')
} finally {
  try {
    const state = await value('mc_get_state')
    if (state.connected) {
      await value('mc_close_screen')
      await command('/tick rate 20')
      await command(`/kill @e[tag=${tag}]`)
    }
  } finally {
    await call('mc_disconnect')
  }
}
console.log('PASS attack runtime checks; tagged entities cleaned up and client disconnected')
