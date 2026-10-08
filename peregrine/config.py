"""Settings saved in ~/.config/peregrine/config.json."""

import json
import os

from . import CLIENT_ID, paths

DEFAULTS = {
    # Override for the built-in client ID (developers only). Empty = use the built-in one.
    "client_id": "",
    "max_memory_mb": 4096,
    "min_memory_mb": 1024,
    "use_gamemode": True,
    "java_path": "",          # empty = let Peregrine pick (Mojang runtime, then system java)
    "extra_jvm_args": "",
    "show_snapshots": False,
    # Appearance
    "theme": "dusk",          # dusk, midnight, light
    "accent": "#e8a33d",
    # Game window
    "width": 0,               # 0 = Minecraft's default
    "height": 0,
    "fullscreen": False,
    # Launcher behavior
    "on_launch": "keep",      # keep, minimize, hide
    "open_console": False,
    "check_updates": True,
    # Performance
    "gc": "auto",             # auto (tuned G1) or zgc
    "driver_boost": True,     # threaded OpenGL for Mesa / NVIDIA
    "dedicated_gpu": False,   # laptops with two GPUs
    "fast_start": True,       # Java class snapshot for quicker game startup
    # Extras
    "discord": True,          # show what you're playing on Discord
    "ingame_menu": True,      # install Peregrine Client (Right Shift menu) in Fabric instances
}


def load() -> dict:
    cfg = dict(DEFAULTS)
    try:
        cfg.update(json.loads(paths.CONFIG_FILE.read_text()))
    except (FileNotFoundError, json.JSONDecodeError):
        pass
    if not cfg["client_id"]:
        cfg["client_id"] = CLIENT_ID  # the one built into this release
    return cfg


def save(cfg: dict) -> None:
    paths.ensure()
    cfg = dict(cfg)
    if cfg.get("client_id") == CLIENT_ID:
        cfg["client_id"] = ""  # don't pin the built-in ID; a future release may change it
    paths.CONFIG_FILE.write_text(json.dumps(cfg, indent=2))


def write_private(path, data) -> None:
    """Write JSON readable only by the current user (used for login tokens)."""
    path.parent.mkdir(parents=True, exist_ok=True)
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    with os.fdopen(fd, "w") as f:
        json.dump(data, f, indent=2)
