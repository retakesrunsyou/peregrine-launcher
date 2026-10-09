"""Instances: separate game folders, each with its own version, loader and mods."""

import json
import re
import shutil
import subprocess
from pathlib import Path

from . import auth, client_mod, config, fabric, game, modrinth, paths, performance

# Colors new instances cycle through (each card gets its own).
INSTANCE_COLORS = ["#e8a33d", "#5aa9e6", "#7bc47f", "#c38be0", "#e07a7a", "#d9c25a"]

CONTENT_FOLDERS = {
    "mods": (".jar",),
    "resourcepacks": (".zip",),
    "shaderpacks": (".zip",),
}


# Mod ids of the performance mods, for instances that got them before
# performance mode tracked its files.
_ID_TO_SLUG = {"sodium": "sodium", "lithium": "lithium", "ferritecore": "ferrite-core",
               "entityculling": "entityculling", "immediatelyfast": "immediatelyfast",
               "modernfix": "modernfix", "dynamic_fps": "dynamic-fps", "moreculling": "moreculling"}


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

    @property
    def performance(self) -> bool:
        """Performance mode: speed-up mods and fast settings. On unless switched off."""
        return self.data.get("performance", True)

    def save(self) -> None:
        _write_json(self.folder / "instance.json", self.data)

    def update(self, **changes) -> None:
        """Save just these keys, on top of what's on disk now. Used by background
        tasks, so they don't undo edits made in the settings dialog meanwhile."""
        try:
            current = json.loads((self.folder / "instance.json").read_text())
        except (OSError, ValueError):
            current = dict(self.data)
        current.update(changes)
        self.data.update(changes)
        _write_json(self.folder / "instance.json", current)

    @property
    def icon(self):
        p = self.folder / "icon.png"
        return p if p.is_file() else None

    @property
    def log_file(self) -> Path:
        return self.folder / "latest.log"

    def optimize_video(self) -> list:
        """Write the fast game settings to options.txt now. Returns what changed."""
        changed = performance.apply_fast_settings(self.game_dir)
        self.update(perf_settings=performance.PRESET_VERSION)
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
                items.append((f, not f.name.endswith(".disabled")))  # unzipped packs
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
        """Turn performance mode on and fetch its mods now. Returns [(slug, filename)]."""
        if self.data["loader"] != "fabric":
            raise ValueError("Performance mods need a Fabric instance.")
        self.update(performance=True, perf_off=[])
        performance.sync(self, progress, force=True)
        return list((self.data.get("perf_files") or {}).items())

    def set_performance(self, on: bool) -> None:
        """Switch performance mode. Off removes the mods it added (not the player's own)
        and, for an instance Peregrine moved to Fabric for speed, goes back to Vanilla."""
        self.update(performance=on)
        if not on:
            performance.sync(self)
            if self.data.get("auto_fabric"):
                self.update(loader="vanilla", loader_version=None, auto_fabric=False)
        else:
            self.update(perf_checked=0, perf_off=[])

    def _migrate_performance(self) -> None:
        """Instances from before performance mode: adopt the FPS mods they already have."""
        old = self.data.get("mods_installed")
        if old is None or "perf_files" in self.data:
            return
        tracked = {}
        mods = self.game_dir / "mods"
        for name in old:
            if (mods / name).exists() or (mods / (name + ".disabled")).exists():
                mid = performance.mod_id(mods / name if (mods / name).exists() else mods / (name + ".disabled"))
                slug = _ID_TO_SLUG.get(mid or "")
                if mid == "fabric-api":
                    continue
                tracked[slug or f"dep:{mid or name}"] = name
        self.update(perf_files=tracked, perf_mc=self.data["mc_version"], perf_checked=0)

    def _prepare_performance(self, progress) -> None:
        """Performance mode, before every launch: Fabric if the instance can have it,
        the right mods for this exact version, and fast settings the first time."""
        cfg = config.load()
        if not (self.performance and cfg.get("performance_first", True)):
            if self.data["loader"] == "fabric" and self.data.get("perf_files"):
                performance.sync(self)  # switched off: take our mods back out
            return
        if self.data["loader"] == "vanilla":
            try:
                if self.data["mc_version"] in fabric.supported_game_versions():
                    # Fabric is what makes the speed-up mods possible. Worlds are unaffected,
                    # and switching performance mode off goes back to Vanilla.
                    self.update(loader="fabric", loader_version=None, auto_fabric=True)
            except Exception as e:
                print(f"[peregrine] couldn't check Fabric support: {e}")
        if self.data.get("perf_settings", 0) < performance.PRESET_VERSION:
            try:
                performance.apply_fast_settings(self.game_dir)
                self.update(perf_settings=performance.PRESET_VERSION)
            except OSError as e:
                print(f"[peregrine] fast settings not written: {e}")
        if self.data["loader"] == "fabric":
            self._migrate_performance()
            try:
                performance.sync(self, progress)
            except Exception as e:  # never let this stop the game from starting
                print(f"[peregrine] performance mods not updated: {e}")

    # ---- install + launch

    def profile(self) -> dict:
        vanilla = game.version_json(self.data["mc_version"])
        if self.data["loader"] == "fabric":
            if not self.data.get("loader_version"):
                self.update(loader_version=fabric.latest_loader(self.data["mc_version"]))
            return game.merge_profiles(vanilla, fabric.profile(self.data["mc_version"],
                                                               self.data["loader_version"]))
        return vanilla

    def prepare(self, progress=None):
        cfg = config.load()
        if self.data.get("memory_mb"):
            cfg["max_memory_mb"] = self.data["memory_mb"]
        self._prepare_performance(progress)
        prof = self.profile()
        info = game.install(prof, progress)
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
    """performance_mods: performance mode (speed-up mods and fast settings). It needs
    Fabric, so it's off for an instance the player explicitly made Vanilla."""
    if performance_mods and loader != "fabric":
        raise ValueError("Performance mode needs Fabric.")
    folder = paths.INSTANCES / _slug(name)
    (folder / "minecraft").mkdir(parents=True)
    count = sum(1 for _ in paths.INSTANCES.iterdir())
    data = {"name": name, "description": description, "mc_version": mc_version,
            "loader": loader, "loader_version": loader_version, "performance": performance_mods,
            "color": INSTANCE_COLORS[(count - 1) % len(INSTANCE_COLORS)], "memory_mb": 0}
    _write_json(folder / "instance.json", data)
    return Instance(folder)


def all_instances() -> list:
    paths.ensure()
    out = []
    for folder in sorted(paths.INSTANCES.iterdir()):
        if (folder / "instance.json").is_file():
            try:
                inst = Instance(folder)
                if all(k in inst.data for k in ("name", "mc_version", "loader")):
                    out.append(inst)  # skip damaged ones instead of breaking the home page
            except (OSError, ValueError, KeyError, AttributeError, TypeError):
                pass
    return out
