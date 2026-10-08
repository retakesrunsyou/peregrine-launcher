"""Where Peregrine keeps things, following the Linux XDG conventions."""

import os
from pathlib import Path


def _xdg(var: str, fallback: str) -> Path:
    value = os.environ.get(var)
    return Path(value) if value else Path.home() / fallback


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
ACCOUNTS_FILE = CONFIG / "accounts.json"


def ensure() -> None:
    for p in (DATA, CONFIG, LIBRARIES, ASSETS, VERSIONS, RUNTIMES, INSTANCES, NATIVES, CACHE, LOGS):
        p.mkdir(parents=True, exist_ok=True)
