# Tool reference

All built-in tools are named `mc_<verb>_<noun>`. Tools added by other mods are named `<modid>_<verb>_<noun>` (see
[Extension API](extension-api.md)).

Each tool carries the MCP annotations `readOnlyHint`, `destructiveHint`, `idempotentHint` and `openWorldHint`, so a
client can decide which calls need confirmation. Results are JSON text, except `mc_screenshot`, which returns a PNG
image. A failure is an `isError` result whose message says what to do next.

Tools that use the screen, the player or the level run on the game's render thread. They fail with a clear message
when the game is not in the required state, for example when no world is joined.

## Summary

| Tool | Kind | Does |
| --- | --- | --- |
| [`mc_get_state`](#mc_get_state) | read | Open screen, connection, window focus, player position |
| [`mc_list_widgets`](#mc_list_widgets) | read | Widgets of the open screen |
| [`mc_read_screen_text`](#mc_read_screen_text) | read | Text the open screen drew |
| [`mc_read_inventory`](#mc_read_inventory) | read | The player's inventory |
| [`mc_read_container`](#mc_read_container) | read | The open container screen |
| [`mc_read_messages`](#mc_read_messages) | read | Chat and system messages |
| [`mc_read_log`](#mc_read_log) | read | The game's log lines |
| [`mc_wait_for`](#mc_wait_for) | read | Block until a message or screen appears |
| [`mc_list_entities`](#mc_list_entities) | read | Entities near the player |
| [`mc_screenshot`](#mc_screenshot) | read | PNG of the game framebuffer |
| [`mc_list_instances`](#mc_list_instances) | read | Running clients with this mod |
| [`mc_click`](#mc_click) | act | Click a widget or a point |
| [`mc_click_slot`](#mc_click_slot) | act | Click a container slot |
| [`mc_close_screen`](#mc_close_screen) | act | Close the open screen |
| [`mc_type_text`](#mc_type_text) | act | Type into the focused widget |
| [`mc_press_key`](#mc_press_key) | act | Press a key on the open screen |
| [`mc_set_key`](#mc_set_key) | act | Hold or release a movement key |
| [`mc_look_at`](#mc_look_at) | act | Face a world position |
| [`mc_use`](#mc_use) | act | Right click, then use the held item |
| [`mc_send_chat`](#mc_send_chat) | act | Send chat or a command |
| [`mc_join_server`](#mc_join_server) | act | Connect to a multiplayer server |
| [`mc_join_world`](#mc_join_world) | act | Load a singleplayer world |
| [`mc_disconnect`](#mc_disconnect) | act | Leave the world or server |

## Observation tools

These tools only read. They are annotated read-only and idempotent.

### mc_get_state

The open screen's class name (`null` when none), whether the client is connected to a server, `windowFocused`, and the
player's position and rotation (`null` outside a world).

```json
{ "screen": null, "connected": true, "windowFocused": true,
  "player": { "x": 10.5, "y": 64.0, "z": -3.2, "yaw": 90.0, "pitch": 0.0 } }
```

`windowFocused` turns `false` only after the window had focus and lost it; it does not report the operating system's
foreground state at launch.

### mc_list_widgets

The clickable widgets of the open screen, each with `index`, `type`, `label` and bounds. Pass the index or the label to
`mc_click`. Vanilla widgets report a stable type (`Button`, `EditBox`, `Slider`, `StringWidget`, and so on); a widget
from another mod keeps its own class name.

### mc_read_screen_text

The text the open screen drew in its last frame: title, labels, body text and any tooltip showing, with coordinates.
This includes text that has no widget. A tooltip appears only while the real cursor hovers its target. Call it after
the screen has been open for a frame.

### mc_read_inventory

The player's hotbar, main, armor and offhand slots with item id, name, count, durability, enchantments and lore, the
selected hotbar slot and the stack on the cursor.

| Argument | Type | Meaning |
| --- | --- | --- |
| `include_empty` | boolean | List empty slots too. Default `false` |

Slot numbers are inventory indexes. The view is what the server last synced, so it can lag a click by a tick.

### mc_read_container

The open container screen (chest, furnace, brewing stand, crafting table, villager trades, the inventory screen): menu
type, title, every slot with its menu index, group and item, the cursor stack and, for merchants, the trade offers.
Fails when no container screen is open.

| Argument | Type | Meaning |
| --- | --- | --- |
| `include_empty` | boolean | List empty slots too. Default `false` |

Slot numbers are **menu** indexes, the ones `mc_click_slot` takes. In a chest screen the container slots come first,
then the player's main inventory, then the hotbar. Groups include `hotbar`, `main`, `armor`, `offhand`, `input`,
`fuel`, `output`, `bottle`, `ingredient`, `result`, `grid`, `creative`, `trash` and `container` for unknown menus.

### mc_read_messages

Chat and system messages received after a sequence number.

| Argument | Type | Meaning |
| --- | --- | --- |
| `since` | integer | Last seen sequence number. Default `0` (everything buffered) |

The result has the messages and `latest`; pass `latest` as `since` next time to read only new messages. `truncated` is
`true` when messages were dropped from the 200-message buffer before they were read. Action-bar text is not included.

### mc_read_log

The game's own log output captured since the mod started: level, logger, thread, message and the first frames of any
exception. Returns the newest matching lines, oldest first.

| Argument | Type | Meaning |
| --- | --- | --- |
| `since` | integer | Last seen sequence number. Default `0` |
| `min_level` | string | Lowest level: `TRACE`, `DEBUG`, `INFO`, `WARN`, `ERROR` or `FATAL`. Default `INFO` |
| `contains` | string | Text the message, exception or logger must contain, ignoring case |
| `max_lines` | integer | Most lines to return. Default 100, at most 500 |

The buffer holds the last 1000 lines and cuts messages at 2000 characters. Capture is per process. See
[Known issues](known-issues.md#log-capture) for what it does not see.

### mc_wait_for

Block until a chat or system message contains `message`, or the open screen's class name contains `screen`
(`none` waits for no screen), whichever happens first.

| Argument | Type | Meaning |
| --- | --- | --- |
| `message` | string | Text a message must contain, ignoring case |
| `screen` | string | Text the screen class name must contain, or `none` |
| `since` | integer | Only messages with a higher sequence number count. Default: the latest at call time |
| `timeout_ms` | integer | How long to wait. Default 10000, at most 60000 |

Give `message`, `screen` or both. The result reports `matched` and a `reason`, plus `latest` (pass it as `since` later); `matched: false` with reason `timeout` when time
runs out. Keep `timeout_ms` below the MCP client's request timeout.

### mc_list_entities

Entities within 64 blocks of the player, nearest first and at most 100, with id, registry type, name, feet position and
distance. Requires a world.

### mc_screenshot

A PNG of the game framebuffer. It is taken from the game, never from the desktop, so it works while the window is behind
others. Prefer the text tools when they can answer the question.

### mc_list_instances

Every running client with this mod on the machine: name, MCP URL, port, Minecraft version, game directory and whether a
token is required. See [Several clients](multiple-clients.md).

## Action tools

These tools change the game. They are not read-only; the exceptions to the default annotations are noted.

### mc_click

Click a widget of the open screen, or a point. Runs through the screen's click handlers; the real cursor is untouched.

| Argument | Type | Meaning |
| --- | --- | --- |
| `label` | string | Part of the widget label, case-insensitive |
| `index` | integer | Widget index from `mc_list_widgets` |
| `x`, `y` | number | A point in GUI coordinates, used together |
| `button` | integer | `0` left (default) or `1` right |

Give `label`, `index` or `x` and `y`. A click by point is how to reach elements that are not widgets, such as the tabs
of the creative inventory.

### mc_click_slot

Click a slot of the open container screen to move items.

| Argument | Type | Meaning |
| --- | --- | --- |
| `slot` | integer | Menu slot index from `mc_read_container` |
| `action` | string | `pick_up` (default), `quick_move`, `swap`, `throw`, `clone`, `pick_up_all` |
| `button` | integer | `0` left or `1` right, for `pick_up` and `throw` (`throw`: 0 one item, 1 the whole stack) |
| `hotbar` | integer | Hotbar key 0-8, or 40 for the offhand, for `swap` |
| `outside` | boolean | With `pick_up`, click outside the window to drop the cursor stack |

The result is the slot and cursor stack as the client predicts them; the server confirms later, so call
`mc_read_container` to see what it kept. Annotated destructive. A villager trade must be selected first with
`mc_click`; then `quick_move` on the result slot takes the item.

### mc_close_screen

Close the open screen as Escape does: a sub-screen returns to its parent, a container screen tells the server it
closed, and the last screen returns to the game. Returns the state after closing.

### mc_type_text

Type text into the focused widget of the open screen: the server address, a world name, an anvil rename box, a search
box, a sign, a command block.

| Argument | Type | Meaning |
| --- | --- | --- |
| `text` | string | Characters to type, at most 1000 |

Focus a text field first with `mc_click`. Returns how many characters the widget accepted. For chat use
`mc_send_chat`.

### mc_press_key

Press and release one key on the open screen.

| Argument | Type | Meaning |
| --- | --- | --- |
| `name` | string | `enter`, `escape`, `tab`, `backspace`, `delete`, `left`, `right`, `up`, `down`, `home`, `end`, `page_up`, `page_down` |
| `times` | integer | Presses to repeat. Default 1 |

`backspace` with a large `times` empties a text field. With no screen open in a world, `escape` opens the pause
screen. Modifier keys are not offered: widgets read them from the physical keyboard, so a modifier sent with the event
has no effect.

### mc_set_key

Hold or release a movement key.

| Argument | Type | Meaning |
| --- | --- | --- |
| `name` | string | `forward`, `back`, `left`, `right`, `jump`, `sneak` or `inventory` |
| `down` | boolean | `true` to hold (default), `false` to release |

A held key stays down until it is set again. While a screen is open the player stops, and a held key resumes when the
screen closes. `inventory` is one press: it opens the player's inventory (the creative screen in creative mode) and
closes it, or a container screen, on the next press. Idempotent.

### mc_look_at

Turn the player to face the world position `x`, `y`, `z` (all required). Returns the new state. Idempotent.

### mc_use

Right click what is under the crosshair, for example to open a block or interact with an entity. When that does
nothing, the held item is used (main hand, then off hand).

| Argument | Type | Meaning |
| --- | --- | --- |
| `hold_ticks` | integer | Keep the use key down for this many ticks (20 per second), 0 to 200. Default 0, a single press |

A single press only starts eating, drinking, drawing a bow or raising a shield, and the game ends it on the next tick.
Use `hold_ticks` to finish: eating or drinking takes 32 ticks, a bow reaches full draw at 20. Aim first with
`mc_look_at`. The result is the interaction result such as `CONSUME`, `SUCCESS` or `PASS`.

### mc_send_chat

Send a chat message, or a command when the text starts with `/`, as the player. The text is trimmed, collapsed to
single spaces and cut to 256 characters; the result returns what was sent. Replies arrive later; read them with
`mc_read_messages` or `mc_wait_for`. Annotated destructive and open-world, because a command can change a world or
reach a server.

### mc_join_server

Connect to a multiplayer server like Direct Connection.

| Argument | Type | Meaning |
| --- | --- | --- |
| `address` | string | `host` or `host:port`. Default port 25565; IPv6 as `[::1]:25565` |

Returns at once with the connect screen open; login finishes later, so follow with `mc_wait_for` or `mc_get_state`. A
failed connection leaves a disconnect screen that `mc_read_screen_text` can read. Fails if already in a world; call
`mc_disconnect` first.

### mc_join_world

Load a singleplayer world.

| Argument | Type | Meaning |
| --- | --- | --- |
| `folder` | string | The save folder name under `saves/`, not the display name |

Returns at once; the world loads over the next seconds. Saves that need an upgrade or experimental-settings
confirmation open a vanilla confirmation screen. Fails if already in a world.

### mc_disconnect

Leave the current world or server and return to the title screen. A singleplayer world is saved, and the call blocks
while it saves. Does nothing on the title screen. Annotated destructive.
