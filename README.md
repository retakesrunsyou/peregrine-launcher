# 🪶 Peregrine Launcher

**Fast to launch. Faster to play.** A Minecraft: Java Edition launcher for Linux.

The peregrine falcon is the fastest animal on Earth, diving at over 300 km/h.
Peregrine gets you into the game just as fast, then keeps it fast while you
play: more FPS, smoother frames and fewer lag spikes, out of the box.

## What it does

**Makes the game faster**
- **Performance mode, on for every instance**: Sodium, Lithium, FerriteCore, Entity Culling,
  ImmediatelyFast, ModernFix, More Culling, BadOptimizations, ScalableLux, Clumps and Dynamic FPS, picked for the instance's exact
  Minecraft version, checked for updates daily, and swapped automatically when you change version
- **Fast game settings** the first time you play: Fast graphics, no clouds or entity shadows,
  smooth lighting and biome blend off, V-Sync off, render distance 10 (your later changes are kept)
- **Stable FPS** in game: when FPS dips on a busy server, particles, entity distance and then
  render distance ease off a step at a time, and come back once it's smooth again
- If a performance mod ever crashes the game, it's switched off for that instance automatically
- Tuned **Java settings**: memory and garbage collection to cut lag spikes (optional low-pause
  mode), a JIT compiler allowed to optimise Minecraft's biggest methods, huge memory pages, and a
  shader cache that survives between launches
- **Graphics driver boost** for AMD, Intel and NVIDIA, and dedicated-GPU mode for laptops
- Uses **Feral GameMode** automatically if you have it

**Measured** (the automated play test: same world, same spot, render distance 8, Minecraft's
own settings vs performance mode, on a test machine with no graphics card, so the game is
drawn in software; real graphics cards usually gain more from Sodium):

| Minecraft | Vanilla | Performance mode | |
|---|---|---|---|
| 26.3 | 12-13 FPS | 24-28 FPS | about 2x |
| 26.1 | 11 FPS | 18-19 FPS | about 1.7x |
| 1.21.11 | 17-18 FPS | 28-31 FPS | about 1.7x |
| 1.21.1 | 37-38 FPS (lows 16) | 42-44 FPS (lows 28) | lows +70% |

The play test can repeat this on any version, and with each mod left out in turn
(Actions → Play test → "benchmark each performance mod").

**In-game menu (Peregrine Client)**
- Press **Right Shift** in game for the Peregrine menu: every mod as a tile with its icon and switch, a filter
  list, search, a Settings tab (accent color and more) and a Keybinds tab to set every key in one place
- **A keybind for every feature**: set it on the feature's options page, then press it in game to switch it on or off
- FPS, coordinates, keystrokes, CPS, armor status and potion timers, placed anywhere you drag them
- Zoom, toggle sprint and sneak, fullbright, compass, biome, memory, clear weather and 40 more
- **Particles**: Normal, Semi, Strong or Hide for each kind (potions, splash potions and XP bottles, lava, explosions, hits, ...)
- **Hitboxes** without F3+B: pick which things show them (XP orbs and items off by default), their color, and whether the look line shows
- **Hit color** for mobs and for crit sparks, **no hurt camera shake**, **low fire**, **1.7 animations**
- **Small totem**: a smaller totem in your hand, and a smaller one flying at you when it pops
- **Fog**: switch it off, clear water and clear lava, and pick its color
- **Block outline**: the color, opacity and (on 1.21.11+) thickness of the outline on the block you look at
- **Ore outline**: one glowing outline around each ore vein you can see, in the ore's color (never through walls), with a switch per ore
  (never through walls)
- **Swing speed** (only on your screen), **static sky** (clouds, sun, moon and stars stay still),
  **chunk animation** (new chunks fade in, 1.21.11+), **sound filters** (explosions, rain, footsteps, mobs and more)
- **Anti-AFK**: after you've been idle, jumps, swings and looks around now and then (check the server's rules)
- **Keep packs out of menus** (on by default): resource packs you add change blocks, items, mobs and the HUD while
  you play, but not the title screen, buttons, menu backgrounds, fonts or splash texts
- **Edit HUD** like Lunar: drag items (they snap to the screen and to each other), resize from the corner or with the
  scroll wheel without them jumping at the screen edge, right-click for colors and **RGB text** (a rainbow that flows
  letter by letter, at the speed you pick). Esc or Enter saves; right-click empty space for Reset all
- One-line items (FPS, CPS, Ping...) can read `FPS 120`, `120`, `FPS: 120` or `[FPS: 120]`; Coordinates can be stacked or on one line, with each part switchable
- **Your own name tag** in third person, shown as `(Peregrine logo) | YourName`
- Resize the server's **scoreboard** and the **chat** (size only)
- **Inventory tweaks**: hold Shift and drag over items to move them all; scroll over an item to move one at a time
- Your client settings, and Minecraft's own (FOV, controls, sound, chat...), follow you to every instance and version
- A Peregrine main menu in place of Minecraft's title screen
- Works on every Minecraft version from 1.21 to 26.3, added to Fabric instances automatically (turn it off in Settings)

**Makes playing easier**
- Separate **instances**, each with its own version, mods, worlds, color and memory
- **Modpacks**: browse and install Fabric modpacks from Modrinth in one click
- **Add mods** from Modrinth right inside each instance, with required libraries handled for you
- **Mods** and **Resource packs** pages: browse Modrinth by category (optimization, utility, PvP, ...) and add to any instance
- Change an instance's version and your mods come along; new instances can bring over settings, packs and mods from another
- Turn mods, resource packs and shaders on or off
- **Microsoft login** with multiple accounts and your skin's face in the header
- **Customizable look**: Dusk, Midnight or Light theme, and any accent color
- Shows what you're playing on **Discord**
- **Fit my screen** fills your screen but leaves the taskbar usable
- Opens instantly, works offline for anything already downloaded, and updates itself

You need your own copy of Minecraft: Java Edition. Sign in with the Microsoft
account that owns it.

## Install

1. Download `peregrine-launcher.zip` from the
   [latest release](https://github.com/retakesrunsyou/peregrine-launcher/releases/latest).
2. Unzip it, open a terminal in the folder, and run:

   ```bash
   bash install.sh
   ```

3. Open **Peregrine Launcher** from your app menu.

On Ubuntu, Mint or Debian the installer may ask you to install a couple of
packages first. It prints the exact command to run.

## Updating

Peregrine tells you when a new version is out. Click **Update now** and it
installs and restarts. Your instances, worlds, accounts and settings are kept.

## Troubleshooting

| Problem | Fix |
|---|---|
| Installer asks for `python3-venv` or `libxcb-cursor0` | Run the `sudo apt install …` line it prints, then run `bash install.sh` again |
| `Permission denied` | Run `bash install.sh` instead of `./install.sh` |
| `peregrine: command not found` | Log out and back in once, or open it from the app menu |
| Something else | [Open an issue](https://github.com/retakesrunsyou/peregrine-launcher/issues) with the last lines the installer printed |

## Where your stuff lives

| What | Where |
|---|---|
| Instances, worlds, game files, Java | `~/.local/share/peregrine/` |
| Settings and accounts | `~/.config/peregrine/` |

To uninstall, delete those two folders plus `~/.local/bin/peregrine` and
`~/.local/share/applications/peregrine.desktop`.

---

Not an official Minecraft product. Not approved by or associated with Mojang or Microsoft.
