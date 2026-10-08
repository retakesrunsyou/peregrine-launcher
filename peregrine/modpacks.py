"""Install Modrinth modpacks (.mrpack files) as new instances."""

import json
import shutil
import tempfile
import zipfile
from pathlib import Path

from . import instances, modrinth, net

SUPPORTED_LOADERS = {"fabric-loader": "fabric"}
LOADER_NAMES = {"forge": "Forge", "neoforge": "NeoForge", "quilt-loader": "Quilt"}


def _safe_join(root: Path, rel: str) -> Path:
    out = (root / rel).resolve()
    if not out.is_relative_to(root.resolve()):
        raise RuntimeError(f"The modpack tried to write outside its folder: {rel}")
    return out


def latest_version(project_id: str) -> dict:
    versions = net.get_json(f"{modrinth.API}/project/{project_id}/version",
                            params={"loaders": json.dumps(["fabric"])})
    if not versions:
        raise RuntimeError("This modpack has no Fabric version Peregrine can install.")
    releases = [v for v in versions if v.get("version_type") == "release"]
    return (releases or versions)[0]


def install(project: dict, progress=None) -> "instances.Instance":
    """Download a modpack and turn it into a ready-to-play instance."""
    version = latest_version(project["project_id"])
    file = next((f for f in version["files"] if f.get("primary")), version["files"][0])

    work = Path(tempfile.mkdtemp(prefix="peregrine-pack-"))
    inst = None
    try:
        if progress:
            progress(0, 1, f"Downloading {project['title']}")
        pack = work / "pack.mrpack"
        net.fetch(net.Download(file["url"], pack, file.get("hashes", {}).get("sha1")))

        with zipfile.ZipFile(pack) as z:
            index = json.loads(z.read("modrinth.index.json"))
            deps = index.get("dependencies", {})
            loader_key = next((k for k in deps if k != "minecraft"), None)
            if loader_key and loader_key not in SUPPORTED_LOADERS:
                name = LOADER_NAMES.get(loader_key, loader_key)
                raise RuntimeError(f"This modpack needs {name}, which Peregrine doesn't support yet.")

            inst = instances.create(
                project["title"], deps["minecraft"], "fabric" if loader_key else "vanilla",
                description=project.get("description", ""),
                loader_version=deps.get(loader_key))
            game = inst.game_dir

            # Mods and other files listed in the pack.
            downloads = []
            for f in index.get("files", []):
                if f.get("env", {}).get("client") == "unsupported":
                    continue
                downloads.append(net.Download(f["downloads"][0], _safe_join(game, f["path"]),
                                              f.get("hashes", {}).get("sha1"),
                                              size=f.get("fileSize")))
            net.fetch_all(downloads, "Modpack files", progress)

            # Config files and extras bundled inside the pack.
            # (client-overrides last, so they win over overrides, as the format says)
            for prefix in ("overrides/", "client-overrides/"):
                for member in z.namelist():
                    if member.startswith(prefix) and not member.endswith("/"):
                        dest = _safe_join(game, member[len(prefix):])
                        dest.parent.mkdir(parents=True, exist_ok=True)
                        with z.open(member) as src, open(dest, "wb") as out:
                            shutil.copyfileobj(src, out)

        inst.data["modpack"] = {"project_id": project["project_id"],
                                "version_id": version["id"], "version": version["version_number"]}
        icon = modrinth.icon_path(project.get("icon_url"))
        if icon:
            shutil.copyfile(icon, inst.folder / "icon.png")
        inst.save()
        return inst
    except Exception:
        if inst is not None:
            shutil.rmtree(inst.folder, ignore_errors=True)  # don't leave half an instance behind
        raise
    finally:
        shutil.rmtree(work, ignore_errors=True)
