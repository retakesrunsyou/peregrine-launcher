"""Instances: separate game folders, each with its own version, loader and mods."""

import json
import re
import shutil
import subprocess
from pathlib import Path

from . import auth, client_mod, config, fabric, game, modrinth, paths

# Colors new instances cycle through (each card gets its own).
INSTANCE_COLORS = ["#e8a33d", "#5aa9e6", "#7bc47f", "#c38be0", "#e07a7a", "#d9c25a"]

CONTENT_FOLDERS = {
    "mods": (".jar",),
    "resourcepacks": (".zip",),
    "shaderpacks": (".zip",),
}


# Balanced video settings: keeps the game looking good, removes the costly extras.
VIDEO_PRESET = {
    "enableVsync": "false",       # don't cap FPS to the monitor
    "maxFps": "260",              # 260 = unlimited
    "renderClouds": '"fast"',
    "entityShadows": "false",
    "biomeBlendRadius": "1",
    "particles": "1",             # decreased
    "simulationDistance": "8",
    "mipmapLevels": "2",
}


def _write_json(path: Path, data) -> None:
    """Write via a temp file so a crash can't leave a half-written file."""
    tmp = path.with_suffix(".tmp")
    tmp.write_text(json.dumps(data, indent=2))
    tmp.replace(path)


def _slug(name: str) -> str:
    s = re.sub(r"[^a-zA-Z0-9_-]+", "-", name.strip()).strip("-").lower() or "instance"
    candidate, n = s, 2
    while (paths.INSTANCES / candidate).exists():
        candidate, n = f"{s}-{n}", n + 1
    return candidate


class Instance:
    def __init__(self, folder: Path):
        self.folder = folder
        self.data = json.loads((folder / "instance.json").read_text())

    @property
    def name(self) -> str:
        return self.data["name"]

    @property
    def game_dir(self) -> Path:
        return self.folder / "minecraft"

    @property
    def color(self) -> str:
        return self.data.get("color") or INSTANCE_COLORS[0]

    def subtitle(self) -> str:
        return f"{self.data['loader'].capitalize()} {self.data['mc_version']}"

    def save(self) -> None:
        _write_json(self.folder / "instance.json", self.data)

    @property
    def icon(self):
        p = self.folder / "icon.png"
        return p if p.is_file() else None

    @property
    def log_file(self) -> Path:
        return self.folder / "latest.log"

    def optimize_video(self) -> list:
        """Write performance-friendly video settings to options.txt. Returns what changed."""
        opts = self.game_dir / "options.txt"
        lines = opts.read_text(errors="replace").splitlines() if opts.exists() else []
        current = dict(l.split(":", 1) for l in lines if ":" in l)
        changed = []
        for key, value in VIDEO_PRESET.items():
            if key == "simulationDistance" and current.get(key, "12").isdigit() \
                    and int(current.get(key, "12")) <= int(value):
                continue  # already low enough
            if current.get(key) != value:
                current[key] = value
                changed.append(key)
        self.game_dir.mkdir(parents=True, exist_ok=True)
        opts.write_text("\n".join(f"{k}:{v}" for k, v in current.items()) + "\n")
        return changed

    def delete(self) -> None:
        shutil.rmtree(self.folder)

    # ---- content (mods, resource packs, shaders)

    def content(self, kind: str) -> list:
        """[(path, enabled)] for a content folder, sorted by name."""
        folder = self.game_dir / kind
        exts = CONTENT_FOLDERS[kind]
        if not folder.is_dir():
            return []
        items = []
        for f in folder.iterdir():
            name = f.name[:-len(".disabled")] if f.name.endswith(".disabled") else f.name
            if f.is_file() and name.lower().endswith(exts):
                items.append((f, not f.name.endswith(".disabled")))
            elif f.is_dir() and kind != "mods":
                items.append((f, True))  # unzipped packs
        return sorted(items, key=lambda i: i[0].name.lower())

    @staticmethod
    def set_enabled(path: Path, enabled: bool) -> Path:
        if enabled and path.name.endswith(".disabled"):
            new = path.with_name(path.name[:-len(".disabled")])
        elif not enabled and not path.name.endswith(".disabled"):
            new = path.with_name(path.name + ".disabled")
        else:
            return path
        path.rename(new)
        return new

    def install_performance_mods(self, progress=None) -> list:
        if self.data["loader"] != "fabric":
            raise ValueError("FPS mods need a Fabric instance.")
        mods = modrinth.install(modrinth.PERFORMANCE_MODS, self.data["mc_version"],
                                "fabric", self.game_dir / "mods", progress)
        self.data["performance_mods"] = True
        self.data["mods_installed"] = [f for _, f in mods]
        self.save()
        return mods

    # ---- install + launch

    def profile(self) -> dict:
        vanilla = game.version_json(self.data["mc_version"])
        if self.data["loader"] == "fabric":
            if not self.data.get("loader_version"):
                self.data["loader_version"] = fabric.latest_loader(self.data["mc_version"])
                self.save()
            return game.merge_profiles(vanilla, fabric.profile(self.data["mc_version"],
                                                               self.data["loader_version"]))
        return vanilla

    def prepare(self, progress=None):
        cfg = config.load()
        if self.data.get("memory_mb"):
            cfg["max_memory_mb"] = self.data["memory_mb"]
        prof = self.profile()
        info = game.install(prof, progress)
        if self.data.get("performance_mods") and not self.data.get("mods_installed"):
            self.install_performance_mods(progress)
        try:
            client_mod.sync(self, cfg.get("ingame_menu", True), progress)
        except Exception as e:  # never let the extra mod stop the game from starting
            print(f"[peregrine] in-game menu not updated: {e}")
        java = game.find_java(prof, cfg.get("java_path", ""), progress)
        return cfg, prof, info, java

    def launch(self, account: dict, progress=None) -> subprocess.Popen:
        cfg, prof, info, java = self.prepare(progress)
        account = auth.refresh(account, cfg.get("client_id", ""))
        if account.get("type") == "msa":
            auth.add_account(account)  # store refreshed tokens
        self.game_dir.mkdir(parents=True, exist_ok=True)
        cmd = game.build_command(prof, info, java, account, self.game_dir, cfg)
        if progress:
            progress(1, 1, "Starting Minecraft")
        return subprocess.Popen(cmd, cwd=self.game_dir, env=game.launch_env(cfg),
                                stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                text=True, errors="replace", start_new_session=True)


def create(name: str, mc_version: str, loader: str = "vanilla",
           performance_mods: bool = False, description: str = "",
           loader_version: str = None) -> Instance:
    if performance_mods and loader != "fabric":
        raise ValueError("FPS mods need Fabric.")
    folder = paths.INSTANCES / _slug(name)
    (folder / "minecraft").mkdir(parents=True)
    count = sum(1 for _ in paths.INSTANCES.iterdir())
    data = {"name": name, "description": description, "mc_version": mc_version,
            "loader": loader, "loader_version": loader_version, "performance_mods": performance_mods,
            "color": INSTANCE_COLORS[(count - 1) % len(INSTANCE_COLORS)], "memory_mb": 0}
    _write_json(folder / "instance.json", data)
    return Instance(folder)


def all_instances() -> list:
    paths.ensure()
    out = []
    for folder in sorted(paths.INSTANCES.iterdir()):
        if (folder / "instance.json").is_file():
            try:
                out.append(Instance(folder))
            except (json.JSONDecodeError, KeyError):
                pass
    return out
