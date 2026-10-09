# Peregrine Client

The in-game part of Peregrine: press **Right Shift** in game for a see-through
menu where you search and turn features on and off.

**Main menu:** replaces Minecraft's title screen with Peregrine's (can be switched off).

Out of the box only **FPS**, **Coordinates** and **Armor status** are on, at
half size. Everything else is one click away.

**Settings for each feature:** click the **gear** on a row (or right-click the
row) for its options: zoom level, smooth zoom, fullbright strength, 24-hour
clock, speed units, which coordinate lines to show, and more. HUD items also
get **Customize look & position**, which opens the editor on that item.

**HUD editor** (Edit HUD in the menu): drag items anywhere, drag the corner or
scroll to resize, right-click for colors, background, opacity and shadow.

**HUD**

| Feature | What it shows |
|---|---|
| FPS | Frames per second |
| Coordinates | Position, facing and dimension, in every dimension and on any server or Realm; optional Nether/Overworld match |
| Nether coordinates | The matching Nether or Overworld spot |
| Chunk position | Which chunk you're in, and where inside it |
| Rotation | Exact yaw and pitch |
| Compass | A strip of headings across the top |
| Keystrokes | WASD, mouse buttons and jump as you press them |
| CPS | Clicks per second |
| Reach display | How far away you were on your last hit |
| Combo counter | Hits in a row without getting hit |
| Target info | Name, health and distance of what you're aiming at |
| Block info | The block you're looking at |
| Block counter | How many of the held item you have (for bridging) |
| Armor status | Armor and held item with durability left |
| Low durability alert | Warns before armor or tools break |
| Health | Exact health |
| Potion effects | Active effects and time left |
| Potion, totem and arrow counters | What's left in your inventory |
| Food | Hunger plus hidden saturation |
| Experience | Level and progress |
| Light level | Block light where you stand (mobs spawn at 0) |
| Biome | The biome you're in |
| Speed | Blocks per second or km/h |
| World clock | In-game day and time |
| Clock | Real-world time |
| Stopwatch | Start, stop and reset from its settings |
| Session time | How long you've played this session |
| Ping, server address, players online | Your connection and the server |
| Memory | Game memory in use |
| Resource packs | Active packs, in order |

**Anti base leak:** Minecraft picks each block's texture rotation (bedrock,
deepslate, tuff, stone, grass, sand...) and each plant's position from its
coordinates, so a screenshot or stream can be traced back to where you are.
Anti base leak draws every block with the same rotation, centres plants, and
shows bedrock as diamond blocks. Only your screen changes.

**Utility:** toggle sprint, toggle sneak, zoom (hold C), freelook (hold Left
Alt to look around in third person), fullbright.

**Visuals:** custom crosshair (shape, size, gap, thickness, color, outline),
hit color, item physics (dropped items lie flat), static FOV, steady camera,
no menu blur, fewer particles, chunk borders, hitboxes, clean edges (no
vignette), clear weather.

Settings save to `config/peregrine-client.json`.

## Supported Minecraft versions

Every release from **1.21 to 26.3**, built separately for each version so every
jar matches its game exactly:

| Adapter folder | Minecraft versions | What changed in that range |
|---|---|---|
| `versions/1.21.1` | 1.21, 1.21.1 | the original |
| `versions/1.21.2` | 1.21.2 – 1.21.5 | zoom uses a different number type |
| `versions/1.21.6` | 1.21.6 – 1.21.8 | new 2D drawing transforms |
| `versions/1.21.9` | 1.21.9 – 1.21.11 | new mouse/keyboard events, new debug options |
| `versions/26.1` | 26.1 – 26.1.2 | unobfuscated game, renamed drawing system, Java 25 |
| `versions/26.2` | 26.2 – 26.3 | screens moved to `Minecraft.gui`, SDL input (26.3) |

Each adapter's `targets.txt` lists its versions. To add a new Minecraft
version, add a line there (or copy the newest adapter if the game changed).

## How it's built: one core, small adapters

```
core/                     every feature, both menus and settings, written once
  Platform.java           what the core asks of Minecraft
  Draw.java               how the core draws
  Compat.java             helpers that avoid classes Mojang keeps renaming
versions/<range>/         a small adapter per range of Minecraft versions
```

The core never touches Minecraft and stays Java 8 compatible. Hooks into the
game are marked optional, so if Mojang renames something in a future version,
that one feature switches off instead of crashing the game.

## Build

```bash
./build.sh            # every version
./build.sh 1.21.4     # just one
```

Java 21 builds 1.21.x; Java 25 builds everything including 26.x
(`sudo apt install openjdk-25-jdk`). Jars land in `dist/` as
`peregrine-client-<version>.jar`.

You don't usually need to run this: publishing a GitHub release builds every
version automatically and attaches the jars, and the launcher installs the
right one into each Fabric instance along with Fabric API.

## Adding a feature

Make a class in `core/src/main/java/net/peregrine/client/core/modules/`
(extend `Module`, or `HudModule` for something drawn on screen) and add it to
the list in `Peregrine.java`. It appears in the menu, searchable, on every version.
