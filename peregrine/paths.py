"""Where Peregrine keeps things: the XDG folders on Linux, AppData on Windows."""

import os
import sys
from pathlib import Path

WINDOWS = sys.platform == "win32"


def _xdg(var: str, fallback: str) -> Path:
    value = os.environ.get(var)
    return Path(value) if value else Path.home() / fallback


if WINDOWS:
    # Games and downloads in Local (not synced between PCs), settings in Roaming.
    DATA = _xdg("LOCALAPPDATA", "AppData/Local") / "Peregrine"
    CONFIG = _xdg("APPDATA", "AppData/Roaming") / "Peregrine"
else:
    DATA = _xdg("XDG_DATA_HOME", ".local/share") / "peregrine"
    CONFIG = _xdg("XDG_CONFIG_HOME", ".config") / "peregrine"

LIBRARIES = DATA / "libraries"
ASSETS = DATA / "assets"
VERSIONS = DATA / "versions"
RUNTIMES = DATA / "runtimes"
INSTANCES = DATA / "instances"
NATIVES = DATA / "natives"
CACHE = DATA / "cache"
LOGS = DATA / "logs"

CONFIG_FILE = CONFIG / "config.json"
# Peregrine Client's settings (HUD layout, features, options), shared by every instance
# so they follow the player to any Minecraft version.
CLIENT_SETTINGS = DATA / "client" / "peregrine-client.json"
ACCOUNTS_FILE = CONFIG / "accounts.json"


def ensure() -> None:
    for p in (DATA, CONFIG, LIBRARIES, ASSETS, VERSIONS, RUNTIMES, INSTANCES, NATIVES, CACHE, LOGS):
        p.mkdir(parents=True, exist_ok=True)
