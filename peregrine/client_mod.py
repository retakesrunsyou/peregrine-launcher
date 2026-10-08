"""Adds Peregrine Client (the Right Shift in-game menu) to Fabric instances.

The mod is built per Minecraft version and attached to the GitHub release as
peregrine-client-<version>.jar. If there's no build for an instance's version
yet, nothing happens and the game launches as normal.
"""

from pathlib import Path

from . import UPDATE_REPO, modrinth, net, paths

PREFIX = "peregrine-client-"


def _release_assets() -> dict:
    if not UPDATE_REPO:
        return {}
    release = net.get_json_cached(f"https://api.github.com/repos/{UPDATE_REPO}/releases/latest",
                                  paths.CACHE / "latest_release.json", 3600,
                                  headers={"Accept": "application/vnd.github+json"})
    return {a["name"]: a for a in release.get("assets", [])}


def _installed(mods: Path) -> list:
    return [f for f in mods.glob(PREFIX + "*.jar*")] if mods.is_dir() else []


def _has_fabric_api(mods: Path) -> bool:
    return any(f.name.lower().startswith(("fabric-api-", "fabric-api_")) for f in mods.glob("*.jar"))


def sync(inst, enabled: bool, progress=None) -> str:
    """Make the instance's mods folder match the setting. Returns what happened."""
    if inst.data["loader"] != "fabric":
        return "not fabric"
    mods = inst.game_dir / "mods"
    if not enabled:
        for f in _installed(mods):
            f.unlink()
        return "removed"

    asset = _release_assets().get(f"{PREFIX}{inst.data['mc_version']}.jar")
    if not asset:
        return "no build for this version"
    dest = mods / asset["name"]
    if dest.with_name(dest.name + ".disabled").exists():
        return "turned off in Content"  # the player switched it off for this instance
    for f in _installed(mods):
        if f != dest:
            f.unlink()  # a copy for another version
    downloads = [net.Download(asset["browser_download_url"], dest, size=asset.get("size"))]
    net.fetch_all(downloads, "Peregrine Client", progress)
    if not _has_fabric_api(mods):
        modrinth.install(["fabric-api"], inst.data["mc_version"], "fabric", mods, progress)
    return "installed"
