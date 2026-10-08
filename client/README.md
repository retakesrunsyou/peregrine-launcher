# Peregrine Client

The in-game part of Peregrine: press **Right Shift** in game for a see-through
menu where you search and turn features on and off.

**Main menu:** replaces Minecraft's title screen with Peregrine's (can be switched off).

**HUD** (drag anywhere with Edit HUD layout)

| Feature | What it shows |
|---|---|
| FPS | Frames per second |
| Coordinates | Position and facing direction |
| Compass | A strip of headings across the top |
| Keystrokes | WASD, mouse buttons and jump as you press them |
| CPS | Clicks per second |
| Armor status | Armor and held item with durability left |
| Potion effects | Active effects and time left |
| Potion counter | Potions left in your inventory |
| Food | Hunger plus hidden saturation |
| Biome | The biome you're in |
| Speed | Blocks per second |
| World clock | In-game day and time |
| Clock | Real-world time |
| Ping | Delay to the server |
| Server address | Which server you're on |
| Memory | Game memory in use |
| Resource packs | Active packs, in order |

**Utility:** toggle sprint, toggle sneak, zoom (hold C), fullbright.

**Visuals:** static FOV, steady camera, no menu blur, fewer particles,
chunk borders, hitboxes, clean edges (no vignette), clear weather.

Click **Edit HUD layout** in the menu to drag HUD items anywhere. Settings
save to `config/peregrine-client.json`.

## Supported Minecraft versions

Every release from **1.21 to 26.3**, built separately for each version so every
jar matches its game exactly:

| Adapter folder | Minecraft versions | What changed in that range |
|---|---|---|
| `versions/1.21.1` | 1.21, 1.21.1 | the original |
| `versions/1.21.2` | 1.21.2 – 1.21.5 | zoom uses a different number type |
| `versions/1.21.6` | 1.21.6 – 1.21.8 | new 2D drawing transforms |
| `versions/1.21.9` | 1.21.9 – 1.21.11 | new mouse/keyboard events, new debug options |
| `versions/26.1` | 26.1 – 26.3 | unobfuscated game, renamed drawing system, Java 25 |

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
