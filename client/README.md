# Peregrine Client

The in-game part of Peregrine: press **Right Shift** in game for a see-through
menu where you search and turn features on and off.

| Feature | What it does |
|---|---|
| FPS | Frames per second |
| Coordinates | Position and facing direction |
| Keystrokes | WASD, mouse buttons and jump as you press them |
| CPS | Clicks per second |
| Armor status | Armor and held item with durability left |
| Potion effects | Active effects and time left |
| Toggle sprint | Always sprint when walking forward |
| Zoom | Hold C to zoom (change it in Controls) |
| Fullbright | See in the dark (some servers don't allow it) |

Click **Edit HUD layout** in the menu to drag HUD items anywhere. Settings
save to `config/peregrine-client.json`.

## How it's built: one core, small adapters

```
core/                     every feature, the menu and settings, written once
  Platform.java           what the core asks of Minecraft
  Draw.java               how the core draws
versions/1.21.1/          the adapter for Minecraft 1.21.1 (about 400 lines)
```

The core never touches Minecraft and stays Java 8 compatible, so it can be
shared by old and new versions alike. Supporting another Minecraft version
means copying a `versions/` folder and fixing the adapter for that version's
names, not rewriting features.

## Build

Needs a Java 21 JDK (`sudo apt install openjdk-21-jdk`).

```bash
./build.sh            # every version
./build.sh 1.21.1     # just one
```

Jars land in `dist/` as `peregrine-client-<version>.jar`. Attach them to the
GitHub release and the launcher adds them to Fabric instances automatically,
along with Fabric API.

## Adding a feature

Make a class in `core/src/main/java/net/peregrine/client/core/modules/`
(extend `Module`, or `HudModule` for something drawn on screen) and add it to
the list in `Peregrine.java`. It appears in the menu, searchable, on every version.
