# Developing Peregrine

## Run from source

```bash
python3 -m venv venv && venv/bin/pip install -r requirements.txt
venv/bin/python -m peregrine
```

Start with `PEREGRINE_DEV=1` to get an "offline test account" button on the
Accounts page. It's for testing singleplayer only and can't join online servers.

## Microsoft login

The launcher's Azure client ID lives in `CLIENT_ID` in `peregrine/__init__.py`.
It's built into releases so players only ever sign in. A client ID isn't a
secret, so it's fine in public code.

To set up a new one: register an app in the Azure portal (personal Microsoft
accounts only, **Allow public client flows** on), then get it approved by
Mojang for the Minecraft API. Until approval, sign-in stops at the last step
with a message saying so.

## Discord status

Create an application at discord.com/developers/applications, upload the
falcon icon under **Rich Presence → Art Assets** named `peregrine`, and put the
Application ID in `DISCORD_APP_ID` in `peregrine/__init__.py`. Not a secret.

## In-game mod (Peregrine Client)

Lives in `client/`; see `client/README.md`. Build with `client/build.sh`
(Java 21 JDK for 1.21.x, Java 25 for 26.x). Releases build and attach the jars
automatically; the launcher installs the one matching each Fabric instance's
Minecraft version.

## Testing

**Actions → Play test → Run workflow** checks everything before a release (about
30 minutes; leave the box empty for every Minecraft version):

- builds the in-game mod for every version, installs it through the launcher's
  own code and plays each version on a virtual screen: main menu, its buttons,
  Right Shift, search, toggles, the HUD editor and dragging, zoom, clicks.
  Each version is played without and with the one-click FPS mods.
- opens the launcher window in every theme, makes an instance with the New
  instance dialog and presses Play.
- installs the release zip with `install.sh` and runs a self-update over it.

Results, screenshots and logs land on the `test-results` branch (`README.txt`
there lists failures per version). Quick checks without Minecraft:
`python3 tests/test_launcher.py`.

## Releasing a new version

1. Run the Play test (above) and make sure it passes.
2. Make sure the code on GitHub is up to date.
3. Set `VERSION` in `peregrine/__init__.py` to the new version (e.g. `0.5.0`) and commit.
4. On GitHub: **Releases → Draft a new release**, tag it with exactly that version
   (`0.5.0`), add a title and notes, and **Publish release**.

GitHub Actions (`.github/workflows/release.yml`) then builds
`peregrine-launcher.zip` and `peregrine-client-<mc>.jar` and attaches them to
the release, usually within 5 minutes. If the tag and `VERSION` don't match, the
build stops and says so.

Installed launchers show **Update now** on their next start, and the in-game
mod updates itself on the next Play.

## Code tour

| File | Job |
|---|---|
| `game.py` | Version manifest, libraries, assets, Java, building the launch command |
| `auth.py` | Microsoft → Xbox → Minecraft login chain |
| `fabric.py` / `modrinth.py` | Fabric loader and mod downloads |
| `instances.py` | Creating, launching and managing content of instances |
| `avatars.py` | Fetching the player's skin for the account picture |
| `updater.py` | Checking GitHub Releases and installing updates |
| `modpacks.py` | Installing Modrinth `.mrpack` modpacks as instances |
| `client_mod.py` | Adding the in-game mod to Fabric instances |
| `discord.py` | Discord Rich Presence |
| `ui/theme.py` | Themes, accent colors, stylesheet, icons |
| `ui/pages.py` | Home, Content, Settings and Accounts pages |
| `ui/browse.py` | Modpacks page and the Add mods window |
| `ui/widgets.py` | Toggle switch and segmented picker |
| `ui/dialogs.py` | Sign-in, new instance and instance settings pop-ups |
| `ui/window.py` | The main window that ties it together |

## Logs

- Launcher errors: `~/.local/share/peregrine/logs/launcher.log`
- Last game run: `latest.log` in each instance's folder
