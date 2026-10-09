# 🪶 Peregrine Launcher

**Fast to launch. Faster to play.** A Minecraft: Java Edition launcher for Linux.

The peregrine falcon is the fastest animal on Earth, diving at over 300 km/h.
Peregrine gets you into the game just as fast, then keeps it fast while you
play: more FPS, smoother frames and fewer lag spikes, out of the box.

## What it does

**Makes the game faster**
- **Performance mode, on for every instance**: Sodium, Lithium, FerriteCore, Entity Culling,
  ImmediatelyFast, ModernFix, More Culling, BadOptimizations, ScalableLux and Dynamic FPS, picked for the instance's exact
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

**In-game menu (Peregrine Client)**
- Press **Right Shift** in game for a see-through menu: search features and switch them on or off
- FPS, coordinates, keystrokes, CPS, armor status and potion timers, placed anywhere you drag them
- Zoom, toggle sprint and sneak, fullbright, compass, biome, memory, clear weather and 20 more
- A Peregrine main menu in place of Minecraft's title screen
- Works on every Minecraft version from 1.21 to 26.3, added to Fabric instances automatically (turn it off in Settings)

**Makes playing easier**
- Separate **instances**, each with its own version, mods, worlds, color and memory
- **Modpacks**: browse and install Fabric modpacks from Modrinth in one click
- **Add mods** from Modrinth right inside each instance, with required libraries handled for you
- Turn mods, resource packs and shaders on or off
- **Microsoft login** with multiple accounts and your skin's face in the header
- **Customizable look**: Dusk, Midnight or Light theme, and any accent color
- Shows what you're playing on **Discord**
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
