import assert from 'node:assert/strict'
import { setTimeout as delay } from 'node:timers/promises'

const [endpoint] = process.argv.slice(2)
if (!endpoint) throw new Error('Usage: node scripts/check-screen.mjs <http://127.0.0.1:port/mcp>')
let requestId = 0

async function rpc(method, params) {
  const response = await fetch(endpoint, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', Accept: 'application/json, text/event-stream' },
    body: JSON.stringify({ jsonrpc: '2.0', id: ++requestId, method, params }),
    signal: AbortSignal.timeout(30_000),
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

async function invalid(name, args, pattern) {
  const result = await call(name, args)
  assert.equal(result.isError, true, `${name} should reject ${JSON.stringify(args)}`)
  assert.match(result.content[0].text, pattern)
}

async function screenText() {
  for (let attempt = 0; attempt < 50; attempt++) {
    const result = await call('mc_read_screen_text')
    if (!result.isError) return JSON.parse(result.content[0].text)
    await delay(100)
  }
  assert.fail('Screen did not render a fresh text frame')
}

await rpc('initialize', {
  protocolVersion: '2025-03-26', capabilities: {}, clientInfo: { name: 'screen-check', version: '1' },
})
const { tools } = await rpc('tools/list', {})
const clickText = tools.find(tool => tool.name === 'mc_click_text')
assert.ok(clickText)
assert.deepEqual(clickText.inputSchema.required, ['frame_id', 'text'])
assert.deepEqual(clickText.annotations, {
  readOnlyHint: false, destructiveHint: false, idempotentHint: false, openWorldHint: false,
})
assert.equal(tools.find(tool => tool.name === 'mc_click').inputSchema.properties.path.type, 'string')
assert.equal((await value('mc_get_state')).connected, false, 'Start at the English title screen')

let title = await screenText()
assert.match(title.screen, /TitleScreen$/)
assert.ok(Number.isInteger(title.frameId))
const options = title.texts.find(text => /^Options/.test(text.text))
assert.ok(options?.bounds, 'Options has measured GUI bounds')
for (const bounds of title.texts.map(text => text.bounds).filter(Boolean)) {
  for (const key of ['x', 'y', 'width', 'height']) assert.ok(Number.isFinite(bounds[key]), key)
  assert.ok(bounds.width > 0 && bounds.height > 0)
}
await invalid('mc_click_text', { text: options.text }, /frame_id/)
await invalid('mc_click_text', { frame_id: title.frameId, text: '__driver_missing_text__' }, /match|mc_read_screen_text/i)
await invalid('mc_click_text', { frame_id: title.frameId, text: options.text, text_index: -1 }, /text_index/)
await invalid('mc_click_text', { frame_id: title.frameId, text: options.text, button: 2 }, /button/)
await invalid('mc_click_text', { frame_id: 'wrong', text: options.text }, /frame_id/)
const clicked = await value('mc_click_text', { frame_id: title.frameId, text: options.text })
assert.equal(clicked.x, options.bounds.x + options.bounds.width / 2)
assert.equal(clicked.y, options.bounds.y + options.bounds.height / 2)
assert.equal(clicked.handled, true)
assert.match(clicked.warning, /do not prove a clickable area/)
await invalid('mc_click_text', { frame_id: title.frameId, text: options.text }, /mc_read_screen_text|rendered/i)
console.log('PASS handshake, schemas, measured text, exact selection, argument errors and stale-screen rejection')

try {
  assert.match((await screenText()).screen, /OptionsScreen$/)
  const optionsWidgets = await value('mc_list_widgets')
  const inactive = optionsWidgets.widgets.find(widget => widget.active === false)
  assert.ok(inactive)
  assert.equal(inactive.inspection.boundsStatus, 'uncertain')
  await invalid('mc_click', { index: inactive.index }, /inactive|hidden/i)
  assert.equal((await value('mc_click', { label: 'Controls...' })).handled, true)
  const current = await screenText()
  assert.match(current.screen, /ControlsScreen$/)
  const { widgets } = await value('mc_list_widgets')
  const paths = widgets.map(widget => widget.inspection.path)
  assert.equal(new Set(paths).size, paths.length, 'Every supported child has a unique path')
  const roots = widgets.filter(widget => !widget.inspection.path.includes('/'))
  for (let index = 0; index < roots.length; index++) {
    assert.equal(roots[index].index, index, 'Root indexes retain their legacy ordering')
    assert.equal(roots[index].inspection.path, String(index))
  }
  const nested = widgets.find(widget => widget.inspection.path.includes('/') &&
    widget.inspection.boundsStatus === 'screen' && /Key Binds/.test(widget.label ?? ''))
  assert.ok(nested, `Controls exposes a routable nested button: ${JSON.stringify(widgets)}`)
  assert.equal((await value('mc_click', { path: nested.inspection.path })).handled, true)
  const keys = await screenText()
  assert.match(keys.screen, /KeyBindsScreen$/, 'Nested path reaches its actual screen handler')
  const resets = keys.texts.map((text, index) => ({ ...text, index })).filter(text => text.text === 'Reset' && text.bounds)
  assert.ok(resets.length > 1, 'Visible repeated labels provide an ambiguity fixture')
  await invalid('mc_click_text', { frame_id: keys.frameId, text: 'Reset' }, /ambiguous|text_index/i)
  const reset = await value('mc_click_text', { frame_id: keys.frameId, text: 'Reset', text_index: resets[0].index })
  assert.equal(reset.x, resets[0].bounds.x + resets[0].bounds.width / 2)
  await invalid('mc_click_text', { frame_id: keys.frameId, text: 'Reset', text_index: resets[0].index }, /mc_read_screen_text/i)
  await value('mc_close_screen')
  await screenText()
  console.log('PASS nested paths, preserved root indexes, uncertain-widget refusal, duplicate disambiguation and single-use capture')
} finally {
  for (let attempt = 0; attempt < 4; attempt++) {
    const state = await value('mc_get_state')
    if (/TitleScreen$/.test(state.screen ?? '')) break
    await value('mc_close_screen')
    await delay(100)
  }
}

await invalid('mc_click_text', { frame_id: title.frameId, text: options.text }, /mc_read_screen_text|rendered/i)
title = await screenText()
const currentOptions = title.texts.find(text => /^Options/.test(text.text))
await delay(5100)
await invalid('mc_click_text', { frame_id: title.frameId, text: currentOptions.text }, /stale|mc_read_screen_text/i)
title = await screenText()
const newer = await screenText()
await invalid('mc_click_text', { frame_id: title.frameId, text: currentOptions.text }, /stale|mc_read_screen_text/i)
assert.notEqual(title.frameId, newer.frameId, 'Each read issues a distinct selector')
console.log('PASS return to previous screen instance, five-second expiry and replacement by a newer capture')

const screenshot = await call('mc_screenshot')
assert.ok(!screenshot.isError, JSON.stringify(screenshot.content))
assert.equal(screenshot.content[0].type, 'image')
console.log('PASS framebuffer screenshot; client remains at the title screen')
