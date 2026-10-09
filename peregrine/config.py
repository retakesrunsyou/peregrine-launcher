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
    "animations": True,       # the animated night sky behind the launcher
    # Game window
    "window_mode": "screen",  # screen = fill this screen, size = width x height, default = Minecraft's
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
        saved = json.loads(paths.CONFIG_FILE.read_text())
        cfg.update(saved)
        if "window_mode" not in saved:  # settings from before the size menu: keep a chosen size
            cfg["window_mode"] = "size" if saved.get("width") else "screen"
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
    tmp = paths.CONFIG_FILE.with_suffix(".tmp")
    tmp.write_text(json.dumps(cfg, indent=2))
    os.replace(tmp, paths.CONFIG_FILE)


def write_private(path, data) -> None:
    """Write JSON readable only by the current user (used for login tokens)."""
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_name(path.name + f".{os.getpid()}.tmp")
    fd = os.open(tmp, os.O_WRONLY | os.O_CREAT | os.O_TRUNC, 0o600)
    with os.fdopen(fd, "w") as f:
        json.dump(data, f, indent=2)
    os.replace(tmp, path)  # all at once, so a crash can't leave a half-written file
