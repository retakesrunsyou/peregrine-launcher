"""Install mods from Modrinth, including their required dependencies."""

import json
from pathlib import Path

from . import net

API = "https://api.modrinth.com/v2"

# One-click FPS boost. Each targets a different bottleneck, and all are
# client-safe and work together.
PERFORMANCE_MODS = [
    "sodium",           # rewrites rendering: the biggest FPS gain
    "lithium",          # faster game logic (mobs, physics, ticking)
    "ferrite-core",     # cuts memory use, so fewer GC stutters
    "entityculling",    # skips drawing mobs/chests you can't see
    "immediatelyfast",  # faster HUD, text and entity rendering
    "modernfix",        # faster loading and lower memory
    "dynamic-fps",      # drops FPS when the game is in the background
]


def _best_version(project: str, game_version: str, loader: str):
    versions = net.get_json(f"{API}/project/{project}/version", params={
        "loaders": json.dumps([loader]),
        "game_versions": json.dumps([game_version]),
    })
    releases = [v for v in versions if v.get("version_type") == "release"]
    return (releases or versions or [None])[0]


def install(projects: list, game_version: str, loader: str, mods_dir: Path,
            progress=None) -> list:
    """Install projects + required deps. Returns [(project_id, filename)]; skips unavailable ones."""
    mods_dir.mkdir(parents=True, exist_ok=True)
    queue = list(projects)
    seen, downloads, installed, skipped = set(), [], [], []

    while queue:
        project = queue.pop(0)
        version = _best_version(project, game_version, loader)
        if version is None:
            skipped.append(project)
            continue
        if version["project_id"] in seen:
            continue
        seen.add(version["project_id"])
        file = next((f for f in version["files"] if f.get("primary")), version["files"][0])
        downloads.append(net.Download(file["url"], mods_dir / file["filename"],
                                      file.get("hashes", {}).get("sha1"),
                                      size=file.get("size")))
        installed.append((version["project_id"], file["filename"]))
        for dep in version.get("dependencies", []):
            if dep.get("dependency_type") == "required" and dep.get("project_id"):
                if dep["project_id"] not in seen:
                    queue.append(dep["project_id"])

    net.fetch_all(downloads, "Mods", progress)
    if skipped and progress:
        progress(1, 1, f"Not available for {game_version}: {', '.join(skipped)}")
    return installed


def search(query: str = "", project_type: str = "mod", game_version: str = None,
           loader: str = "fabric", offset: int = 0, limit: int = 20) -> list:
    """Search Modrinth. Returns hits with title, description, author, downloads, icon_url."""
    facets = [[f"project_type:{project_type}"]]
    if loader:
        facets.append([f"categories:{loader}"])
    if game_version:
        facets.append([f"versions:{game_version}"])
    data = net.get_json(f"{API}/search", params={
        "query": query, "facets": json.dumps(facets), "limit": limit, "offset": offset,
        "index": "relevance" if query else "downloads",
    })
    return data.get("hits", [])


def icon_path(url: str):
    """Download a project icon once and keep it in the cache. Returns a path or None."""
    import hashlib
    from . import paths
    if not url:
        return None
    path = paths.CACHE / "icons" / hashlib.sha1(url.encode()).hexdigest()
    if not path.is_file():
        try:
            net.fetch(net.Download(url, path), retries=1)
        except Exception:
            return None
    return path
