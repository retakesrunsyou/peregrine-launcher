"""Recommended mods: well-made, popular Fabric mods that add what Peregrine Client
doesn't draw itself (3D skin layers, dynamic lights, cape physics...). Each one is
added from Modrinth in one click, with the version that fits the instance."""

from . import modrinth, net

# (Modrinth slug, name, what it does, a word found in its file name, to spot it's installed)
MODS = [
    ("3dskinlayers", "3D Skin Layers", "Your skin's outer layer (hat, jacket, sleeves) in real 3D", "skinlayers"),
    ("lambdynamiclights", "LambDynamicLights", "A torch in your hand lights up the area around you", "dynamiclights"),
    ("wavey-capes", "Wavey Capes", "Capes (Peregrine's too) that flow and swing with physics", "waveycapes"),
]
# AppleSkin, voice chat, Iris and the rest come with every instance: see extras.py.


def installed(inst, marker: str) -> bool:
    folder = inst.game_dir / "mods"
    if not folder.is_dir():
        return False
    return any(marker in p.name.lower().replace("-", "").replace("_", "") for p in folder.iterdir())


def resolve(slug: str, title: str) -> str:
    """A Modrinth project id for a slug, or by searching its name if the slug moved."""
    try:
        return net.get_json(f"{modrinth.API}/project/{slug}")["id"]
    except Exception:
        hits = modrinth.search(title, "mod", None, "fabric", 0, 5)
        for h in hits:
            if h.get("title", "").lower() == title.lower():
                return h["project_id"]
        if hits:
            return hits[0]["project_id"]
        raise RuntimeError(f"Couldn't find {title} on Modrinth.")


def install(inst, slugs: list, progress=None) -> list:
    """Adds the chosen mods (and anything they need). Returns the names that weren't
    available for this Minecraft version."""
    mc = inst.data["mc_version"]
    missing = []
    for i, (slug, title, _, _) in enumerate(m for m in MODS if m[0] in slugs):
        if progress:
            progress(i, len(slugs), f"Adding {title}")
        try:
            added = modrinth.install([resolve(slug, title)], mc, "fabric", inst.game_dir / "mods")
        except Exception:
            added = []
        if added:
            inst.remember_mods(added)
        else:
            missing.append(title)
    if progress:
        progress(len(slugs), len(slugs), "Done")
    return missing


def install_shader(inst, project_id: str, progress=None) -> str:
    """A shader pack into the instance, with Iris to run it, and switched on."""
    mc = inst.data["mc_version"]
    name = modrinth.install_pack(project_id, mc, inst.game_dir / "shaderpacks", progress)
    if not installed(inst, "iris"):
        added = modrinth.install([resolve("iris", "Iris Shaders")], mc, "fabric", inst.game_dir / "mods", progress)
        inst.remember_mods(added)
    cfg = inst.game_dir / "config" / "iris.properties"
    cfg.parent.mkdir(parents=True, exist_ok=True)
    lines = [l for l in (cfg.read_text().splitlines() if cfg.exists() else [])
             if not l.startswith(("shaderPack=", "enableShaders="))]
    cfg.write_text("\n".join(lines + [f"shaderPack={name}", "enableShaders=true"]) + "\n")
    return name


def shaders_on(inst) -> bool:
    cfg = inst.game_dir / "config" / "iris.properties"
    try:
        return "enableShaders=true" in cfg.read_text()
    except OSError:
        return False


def set_shaders(inst, on: bool) -> None:
    cfg = inst.game_dir / "config" / "iris.properties"
    cfg.parent.mkdir(parents=True, exist_ok=True)
    lines = [l for l in (cfg.read_text().splitlines() if cfg.exists() else []) if not l.startswith("enableShaders=")]
    cfg.write_text("\n".join(lines + [f"enableShaders={'true' if on else 'false'}"]) + "\n")
