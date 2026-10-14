"""Fabric mod loader support via Fabric's meta API."""

from functools import lru_cache

from . import net, paths

META = "https://meta.fabricmc.net/v2"


@lru_cache(maxsize=1)
def supported_game_versions() -> frozenset:
    data = net.get_json_cached(f"{META}/versions/game", paths.CACHE / "fabric_games.json", 6 * 3600)
    return frozenset(v["version"] for v in data)


def latest_loader(game_version: str) -> str:
    loaders = net.get_json(f"{META}/versions/loader/{game_version}")
    if not loaders:
        raise RuntimeError(f"Fabric doesn't support Minecraft {game_version}.")
    stable = [l for l in loaders if l["loader"].get("stable")]
    return (stable or loaders)[0]["loader"]["version"]


def profile(game_version: str, loader_version: str) -> dict:
    # A given loader + game version profile never changes, so keep it forever.
    return net.get_json_cached(f"{META}/versions/loader/{game_version}/{loader_version}/profile/json",
                               paths.CACHE / "fabric" / f"{game_version}-{loader_version}.json",
                               float("inf"))
