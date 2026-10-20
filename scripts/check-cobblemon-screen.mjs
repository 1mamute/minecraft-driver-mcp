import assert from 'node:assert/strict'
import { mkdir, writeFile } from 'node:fs/promises'
import { setTimeout as delay } from 'node:timers/promises'

const [endpoint, output = 'build/check-screen'] = process.argv.slice(2)
if (!endpoint) throw new Error('Usage: node scripts/check-cobblemon-screen.mjs <endpoint-in-disposable-Cobblemon-1.8.1-battle> [evidence-directory]')
let requestId = 0

async function rpc(method, params) {
  const response = await fetch(endpoint, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Accept: 'application/json, text/event-stream' },
    body: JSON.stringify({ jsonrpc: '2.0', id: ++requestId, method, params }),
    signal: AbortSignal.timeout(30_000),
  })
  assert.equal(response.status, 200)
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

async function capture() {
  for (let attempt = 0; attempt < 50; attempt++) {
    const result = await call('mc_read_screen_text')
    if (!result.isError) return JSON.parse(result.content[0].text)
    await delay(100)
  }
  assert.fail('Battle screen has no fresh rendered text')
}

await rpc('initialize', {
  protocolVersion: '2025-03-26', capabilities: {}, clientInfo: { name: 'cobblemon-screen-check', version: '1' },
})
assert.match((await value('mc_get_state')).screen ?? '', /BattleGUI$/, 'Prepare a disposable English Cobblemon 1.8.1 battle first')
const before = await capture()
assert.ok(before.texts.some(text => text.text === 'Forfeit' && text.bounds))
const privateTile = await call('mc_click', { label: 'Forfeit' })
assert.equal(privateTile.isError, true, 'Private battle tiles are absent from the public widget hierarchy')
const forfeit = await value('mc_click_text', { frame_id: before.frameId, text: 'Forfeit' })
assert.equal(forfeit.handled, true, 'Measured text center reaches the actual private Forfeit tile')
await delay(200)
const confirmation = await value('mc_list_widgets')
const panel = confirmation.widgets.find(widget => widget.type.endsWith('ForfeitConfirmationSelection'))
assert.ok(panel, JSON.stringify(confirmation))
assert.deepEqual([panel.x, panel.y, panel.width, panel.height], [0, 0, 113, 45], 'Reproduces the raw zero-origin rectangle')
assert.equal(panel.inspection.boundsStatus, 'uncertain')
assert.equal(panel.inspection.renderedBounds, null, 'Do not invent a screen coordinate for custom rendering')
const refused = await call('mc_click', { path: panel.inspection.path })
assert.equal(refused.isError, true)
assert.match(refused.content[0].text, /coordinates|mc_screenshot/)
const screen = await capture()
const title = screen.texts.find(text => text.text === 'Forfeit' && text.bounds)
assert.ok(title, 'Confirmation title has measured text bounds')
assert.ok(title.bounds.x > 0 && title.bounds.y > 0, 'Actual confirmation text is centered, despite the raw zero origin')
const screenshot = await call('mc_screenshot')
assert.ok(!screenshot.isError)
const png = screenshot.content.find(content => content.type === 'image')
const metadata = JSON.parse(screenshot.content.find(content => content.type === 'text').text)
assert.ok(png)
await mkdir(output, { recursive: true })
await writeFile(`${output}/cobblemon-confirm.png`, Buffer.from(png.data, 'base64'))
await writeFile(`${output}/cobblemon-confirm.json`, JSON.stringify({ before, forfeit, confirmation, screen, metadata }, null, 2) + '\n')
console.log('PASS measured private Forfeit text click; actual 0,0 confirmation bounds reproduced and rejected as uncertain')
console.log(`Evidence: ${output}/cobblemon-confirm.png and .json`)

// Fixture coordinates from Cobblemon 1.8.1 ForfeitConfirmationSelection, never used by the driver itself.
const panelX = Math.floor(metadata.gui.width / 2) - 56
const panelY = Math.floor(metadata.gui.height / 2) - 22
assert.equal((await value('mc_click', { x: panelX + 74, y: panelY + 27.5 })).handled, true)
await delay(200)
assert.ok(!(await value('mc_list_widgets')).widgets.some(widget => widget.type.endsWith('ForfeitConfirmationSelection')))
const again = await capture()
assert.equal((await value('mc_click_text', { frame_id: again.frameId, text: 'Forfeit' })).handled, true)
await delay(200)
assert.equal((await value('mc_click', { x: panelX + 39, y: panelY + 27.5 })).handled, true)
let finished = false
for (let attempt = 0; attempt < 100; attempt++) {
  if (!/BattleGUI$/.test((await value('mc_get_state')).screen ?? '')) {
    finished = true
    break
  }
  await delay(100)
}
assert.ok(finished, 'Accepted confirmation exits the disposable battle')
console.log('PASS explicit cancel, fresh captured-text reopen and explicit accept exit the battle')
