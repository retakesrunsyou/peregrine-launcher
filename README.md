# 🪶 Peregrine Launcher

**Fast to launch. Faster to play.** A Minecraft: Java Edition launcher for Windows and Linux.

The peregrine falcon is the fastest animal on Earth, diving at over 300 km/h.
Peregrine gets you into the game just as fast, then keeps it fast while you
play: more FPS, smoother frames and fewer lag spikes, out of the box.

## What it does

**The launcher**
- **Recent**: the server you played last, big, with its live player count, MOTD and ping, and a Play
  button that starts Minecraft straight into it. Your other recent servers sit underneath
- **Friends**: your Xbox friends (the list Minecraft uses for Realms and Bedrock), who's online, and
  adding people by gamertag. The top bar shows your head and how many friends are online
- **Home** for your instances, **Mods & packs** for mods, resource packs, shaders and modpacks, and a side panel
  that tucks away with the button at the bottom
- **Peregrine extras on every Fabric instance**: AppleSkin, Enchantment Descriptions, Not Enough Animations,
  Continuity, Simple Voice Chat and Iris, kept up to date like the performance mods, plus the MakeUp Ultra Fast shader pack
  ready to switch on (shaders start off). Turn the whole set off in Settings, or one mod off in Content
- **Recommended mods** in one click: 3D Skin Layers, LambDynamicLights and Wavey Capes
- **Locker**: try on skins and capes on your turning player, then wear them. Keep a collection of skins (from files, or
  copied from any player by name) and put one on your Minecraft account with classic or slim arms, switch your official
  Minecraft capes, pick your Peregrine cape, and choose the game's main menu background (featured ones or your own picture)
- **Profiles**: share your setup under your Minecraft name (only when you press Share), and look up a friend's to grab their
  mods, resource packs and shaders from Modrinth and, if you like, their in-game mod settings. See [profiles/README.md](profiles/README.md)
- **Folders** on every instance: Schematics, Screenshots, Worlds, Resource packs, Shaders, Mods and Mod settings, one click away
- **Shaders**: add a shader pack and Iris comes along to run it; switch shaders on or off per instance
- **Seasonal touches**: bats and a harvest moon in October, snow in December

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
  shader cache that survives between launches. On Java 25 (Minecraft 26.1+) **smaller Java objects**
  (compact object headers) for less memory use and fewer cleanup pauses; on Windows the game gets
  **CPU priority** over background apps. Launches skip re-checking files that haven't changed
- **Graphics driver boost** for AMD, Intel and NVIDIA, and dedicated-GPU mode for laptops
- **Flight boost** in creative and spectator, and an **Inventory HUD** that shows your whole inventory anywhere on screen
- Uses **Feral GameMode** automatically if you have it

**Measured** (same world, same spot, render distance 8, Minecraft's
own settings vs performance mode, on a test machine with no graphics card, so the game is
drawn in software; real graphics cards usually gain more from Sodium):

| Minecraft | Vanilla | Performance mode | |
|---|---|---|---|
| 26.3 | 12-13 FPS | 24-28 FPS | about 2x |
| 26.1 | 11 FPS | 18-19 FPS | about 1.7x |
| 1.21.11 | 17-18 FPS | 28-31 FPS | about 1.7x |
| 1.21.1 | 37-38 FPS (lows 16) | 42-44 FPS (lows 28) | lows +70% |

**In-game menu (Peregrine Client)**
- **Main menu**: the falcon glowing in the middle, three big glass tiles (Singleplayer, Multiplayer, Modules) and
  round buttons for the rest, your player turning beside it in your cape, and a background you choose: Minecraft's
  panorama, Dusk, Aurora, Night sky or your own picture. The **Locker** button slides in your capes, skins and backgrounds:
  try a skin on and wear it, without leaving the game. **Adjust** your own picture like the HUD editor: drag it,
  resize it from the corners or with the scroll wheel, snap it to the middle or the edges, show the whole
  picture, darken it, and switch its slow drift on or off
- Press **Right Shift** in game for the Peregrine menu, a frosted glass window with a sidebar that drops down:
  - **Modules** (HUD, Utility, Visuals, Performance): every built-in mod as a card with its icon and switch, and search
  - **Mods**, **Shaders** and **Resource packs**, each with **Installed** (switch on or off, remove, open the folder)
    and **Browse** (search Modrinth and install, matched to your Minecraft version, with the mods they need)
  - **Locker** (capes and skins, on your turning player) and **Friends** (who's online and what they're playing)
  - **Schematics**: put .litematic, .schem (WorldEdit) or .nbt (structure block) files in the instance's
    schematics folder, place one at your feet, then move it, turn it, mirror it and build one layer at a
    time. Outlines show what's missing (blue), the wrong block (red) and blocks turned the wrong way (orange),
    with how much is built and the blocks you still need next to what's in your inventory. A small HUD item
    shows your progress and which block goes where you're looking
  - **Voice chat**: start a party and share its 6-letter code, or join a friend's; talk on any server or
    world (up to 8 players). Your mic turns on when you speak, or use push to talk (V); mute with a key,
    and a HUD item shows who's talking
  - **Keybinds** to set every key in one place, and **Settings** (accent color and more)
- **A keybind for every feature**: set it on the feature's options page, then press it in game to switch it on or off
- FPS, coordinates, keystrokes, CPS, armor status and potion timers, placed anywhere you drag them
- Zoom, toggle sprint and sneak, fullbright, compass, biome, memory, clear weather and 40 more
- **Particles**: Normal, Semi, Strong or Hide for each kind (potions, splash potions and XP bottles, lava, explosions, hits, ...)
- **Hitboxes** without F3+B: pick which things show them (XP orbs and items off by default), their color, and whether the look line shows
- **Hit color** for mobs and for crit sparks, **no hurt camera shake**, **low fire**, **1.7 animations**
- **Small totem**: a smaller totem in your hand, and a smaller one flying at you when it pops
- **Fog** (off, or in your color), **clear water** and **clear lava**, each its own feature
- **Block outline**: the color, opacity and (on 1.21.11+) thickness of the outline on the block you look at
- **Ore outline**: one glowing outline around each ore vein you can see, in the ore's color (never through walls), with a switch per ore
  (never through walls)
- **Swing speed** (only on your screen), **static sky** (clouds, sun, moon and stars stay still),
  **chunk animation** (new chunks fade in, 1.21.11+), **sound filters** (explosions, rain, footsteps, mobs and more)
- **Anti-AFK**: after you've been idle, jumps, swings and looks around now and then (check the server's rules)
- **Waypoints** per server and dimension, shown over the world with their distance (a death point is added for you),
  and a **TNT countdown** over lit TNT
- **Nick hider** (your name shows as something else on your screen), **time changer**, **glint color**,
  **shiny potions**, **stretched resolution** (4:3, 5:4, 16:10 or 3:2, with a strength slider and an option to stretch the HUD too), **hand position** (move, turn, resize each hand) and **hide plants**
- **Quick commands**: up to four keys that send a command like `/home`
- **Titles**, **action bar** and **boss bars**: resize, move or hide each one; **compact tab list** with ping as numbers;
  **shulker preview**; **Better F3**; **MC Tiers** next to names; **smooth hotbar**; **borderless fullscreen**
- **Peregrine capes and badges**: pick your cape (Falcon, Violet, Ember, Starry night, Pumpkin, Frost) under
  Cosmetics. Everyone playing with Peregrine sees the falcon by each other's names and each other's capes,
  automatically (switch it off in Settings). Special badges (dev, staff, supporter, early) come from
  [`cosmetics.json`](cosmetics.json); get a player's ID with **Copy ID** on the launcher's Accounts page
- **Item switch**: a smoother, quicker or snappier hand when you change items
- **Smooth inventory** (on by default): inventories and chests grow in and shrink away, the slot highlight glides, and items
  you swap with the number keys fly to their new slot. Optional **see-through inventory**: dark glass with grey slots instead
  of the texture (inventory, chests, barrels, shulker boxes, dispensers and hoppers)
- **Motion blur** (off by default): a soft trail when you turn, with a strength slider (every version except 1.21.2 to 1.21.5)
- **Block place animation** (on by default): blocks you place grow into place from the middle, on every version
- **Knives** (26.1, beta): your sword becomes a knife held in your own arm, shooter style: karambit, butterfly knife,
  bayonet, combat knife, nomad, skeleton knife or push dagger, in 12 finishes or your own colors. A flourish when you pull
  it out, slashes that switch sides, uppercuts, an overhead chop every third hit, stabs and lunges on crits, four inspects
  (F) and little fidgets when you stand still. Move, turn and resize the knife in your hand, or hide your arm
- **Viewmodel effects** (26.1, beta): your hand and what it holds see-through like glass, as a hologram or glowing, with
  afterimages when you swing (everything you hold, swords only, or swords and axes). First person only
- **Rainbow block outline**, a little thicker, on by default
- **Music activity**: the song you're playing on Spotify, YouTube or any music app, with its cover, artist and time
  left (Linux and Windows; Spotify and Music on macOS)
- **Armor status**: top to bottom or left to right, with durability as a number, a percentage, a bar, a bar and number, or hidden
- More HUD items: **cooldowns**, an **item counter** for the items you pick and a **profile** with your head
- **Keep packs out of menus** (on by default): resource packs you add change blocks, items, mobs and the HUD while
  you play, but not the title screen, buttons, menu backgrounds, fonts or splash texts
- **Edit HUD** like Lunar: drag items (they snap to the screen and to each other), resize from the corner or with the
  scroll wheel without them jumping at the screen edge, right-click for colors and **RGB text** (a rainbow that flows
  letter by letter, at the speed you pick). Esc or Enter saves; right-click empty space for Reset all
- One-line items (FPS, CPS, Ping...) can read `FPS 120`, `120`, `FPS: 120` or `[FPS: 120]`; Coordinates can be stacked or on one line, with each part switchable
- **Your own name tag** in third person, shown as `(Peregrine logo) | YourName`
- **Server**: the server's icon, then its name (or address)
- Every HUD item can have a **text outline**, a **border**, **rounded corners**, and on FPS, ping and memory
  **color-coded values**; keystrokes fade smoothly and can show sneak
- Resize the server's **scoreboard** and the **chat** (size only)
- **Inventory tweaks**: hold Shift and drag over items to move them all; scroll over an item to move one at a time
- Your client settings, and Minecraft's own (FOV, controls, sound, chat...), follow you to every instance and version
- A Peregrine main menu in place of Minecraft's title screen: your name and head, buttons with icons, Realms,
  Language and Accessibility, and a **Jump back in** card for the last server you played
- A Peregrine pause menu: a glass card with your head, the server and your ping, a big Resume, tiles for Modules,
  Options, the Locker and the rest of Minecraft's menu, and Leave
- A **Settings** tab in the in-game menu (Right Shift): the menu's background (solid, see-through or none), the main and pause
  menus, and quick switches for stretched resolution, the block place animation, the rainbow outline and capes
- **Zoom** up to 50x: hold the key, then scroll to zoom further in or out; the mouse slows down to match
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
- Shows what you're playing on **Discord**: `Playing Java 1.21.11` and where (the menus, singleplayer, or the server's name), with the Peregrine logo and a link
- **System tray**: when you press Play the launcher tucks into the tray and comes back when the game closes. Click the tray icon to
  open it, or right-click for Play (your last instance) and **Quit**. Closing the window keeps it in the tray (change both in Settings)
- **Fit my screen** fills your screen but leaves the taskbar usable
- Opens instantly, works offline for anything already downloaded, and updates itself. When a new Peregrine is
  out, a card slides in on the main menu and in game so you know to restart the launcher

You need your own copy of Minecraft: Java Edition. Sign in with the Microsoft
account that owns it.

## Install

**Windows**

1. Download `Peregrine-Setup.exe` from the
   [latest release](https://github.com/retakesrunsyou/peregrine-launcher/releases/latest) and run it.
   If Windows says it protected your PC, click **More info**, then **Run anyway** (the app isn't signed yet).
2. Open **Peregrine Launcher** from the desktop shortcut or the Start menu. No admin rights needed.

**Linux** (any distro)

1. Download `Peregrine-x86_64.AppImage` from the
   [latest release](https://github.com/retakesrunsyou/peregrine-launcher/releases/latest).
2. Make it runnable (right-click > Properties > Permissions > *Allow executing*, or `chmod +x Peregrine-x86_64.AppImage`) and open it.
   The first start adds Peregrine to your app menu and puts a shortcut on your desktop.

Or with the installer script: download `peregrine-launcher.zip`, unzip it, open a terminal in the folder and run
`bash install.sh`. On Ubuntu, Mint or Debian it may ask you to install a couple of packages first and prints the exact command.

## Updating

Peregrine tells you when a new version is out. Click **Update now** and it
installs and restarts. Your instances, worlds, accounts and settings are kept.

## Troubleshooting

| Problem | Fix |
|---|---|
| Installer asks for `python3-venv` or `libxcb-cursor0` | Run the `sudo apt install …` line it prints, then run `bash install.sh` again |
| `Permission denied` | Run `bash install.sh` instead of `./install.sh` |
| AppImage won't open | Install `libfuse2` (`sudo apt install libfuse2`), or run it with `--appimage-extract-and-run` |
| `peregrine: command not found` | Log out and back in once, or open it from the app menu |
| Something else | [Open an issue](https://github.com/retakesrunsyou/peregrine-launcher/issues) with the last lines the installer printed |

## Where your stuff lives

| What | Linux | Windows |
|---|---|---|
| Instances, worlds, game files, Java | `~/.local/share/peregrine/` | `%LOCALAPPDATA%\Peregrine\` |
| Settings and accounts | `~/.config/peregrine/` | `%APPDATA%\Peregrine\` |

To uninstall on Windows use **Apps > Installed apps > Peregrine Launcher**. On Linux delete the AppImage (or
`~/.local/bin/peregrine`), `~/.local/share/applications/peregrine.desktop` and the two folders above.

---

Not an official Minecraft product. Not approved by or associated with Mojang or Microsoft.

## Credits for the custom skies

- Star and Milky Way maps: NASA/Goddard Space Flight Center Scientific Visualization Studio, *Deep Star Maps 2020*. Gaia DR2: ESA/Gaia/DPAC. Also Hipparcos-2, Tycho-2 and the Yale Bright Star Catalog.
- The Moon: NASA's Scientific Visualization Studio, *CGI Moon Kit* (Ernie Wright, Noah Petro), from the Lunar Reconnaissance Orbiter's camera (LROC) and laser altimeter (LOLA).
- The black hole is Peregrine's own ray-traced simulation (`tools/blackhole_raytrace.py`); the aurora, meteors and clouds are drawn by `tools/make_sky_art.py`.
