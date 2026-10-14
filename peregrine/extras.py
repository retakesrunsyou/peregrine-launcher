"""Peregrine's bundled extras: a few popular, well-made Fabric mods that every Fabric
instance gets by default (food values, what you're looking at, voice chat, shaders...),
plus a shader pack ready to switch on.

They're looked after like the performance mods: the right version for the instance,
checked for updates once a day, never installed twice, and taken out if the player
switches them off (Settings) or turns one off in Content. Shaders start switched off;
the Shaders button in Content turns them on."""

import time

from . import config, modrinth, performance

# (Modrinth slug, name, what it adds)
MODS = [
    ("appleskin", "AppleSkin", "food and saturation values on the hunger bar"),
    ("enchantment-descriptions", "Enchantment Descriptions", "what every enchantment does, on its tooltip"),
    ("not-enough-animations", "Not Enough Animations", "third-person animations for eating, maps, bows and more"),
    ("continuity", "Continuity", "connected textures for glass and more"),
    ("simple-voice-chat", "Simple Voice Chat", "proximity voice chat on servers that have it"),
    ("iris", "Iris Shaders", "shader packs, fast (with Sodium)"),
]
SLUGS = [m[0] for m in MODS]

# The shader pack set up (switched off) the first time: MakeUp Ultra Fast, light and quick;
# or Complementary Reimagined if that can't be had.
SHADERS = ["makeup-ultra-fast-shaders", "complementary-reimagined"]
# Packs an older Peregrine set up that Iris can't load: replaced with the one above.
OLD_SHADERS = ("e-lite",)

# Extras we used to add and no longer do: taken back out of instances we put them in
# (a copy the player added themselves is left alone).
RETIRED = ["jade"]

# Releases listed for a Minecraft version that don't actually start on it (found by the
# play test). They're tried again on other versions as usual.
KNOWN_BAD = {"1.21.2": ["not-enough-animations", "appleskin"]}


def enabled(inst) -> bool:
    return (config.load().get("extras", True) and inst.data.get("extras", True)
            and inst.data.get("loader") == "fabric")


def sync(inst, progress=None, force: bool = False) -> dict:
    """Make the instance's extras match its version (or take them out when off)."""
    mods = inst.game_dir / "mods"
    on = enabled(inst)
    tracked = dict(inst.data.get("extra_files") or {})
    retired = [slug for slug in RETIRED if slug in tracked]
    if retired:
        for slug in retired:
            name = tracked.pop(slug)
            for f in (mods / name, mods / (name + ".disabled")):
                f.unlink(missing_ok=True)
        inst.update(extra_files=tracked)
    if on and mods.is_dir():
        broken = set(performance.unmet(mods, tracked.values()))
        if broken:
            keep = performance.shared_files(inst, "extra")  # e.g. Sodium, also a performance mod
            for key, fn in list(tracked.items()):
                if fn in broken:
                    if fn not in keep:
                        (mods / fn).unlink(missing_ok=True)
                    tracked.pop(key)
            inst.update(extra_files=tracked, extra_checked=0)
            force = True
    mc = inst.data.get("mc_version")
    skip = inst.data.get("extra_skip") or {}
    if isinstance(skip, dict) and any(skip.get(slug) != mc for slug in KNOWN_BAD.get(mc, [])):
        inst.update(extra_skip=dict(skip, **{slug: mc for slug in KNOWN_BAD.get(mc, [])}))
        for slug in KNOWN_BAD.get(mc, []):
            if slug in tracked:
                (mods / tracked.pop(slug)).unlink(missing_ok=True)
        inst.update(extra_files=tracked)
    summary = performance.sync_set(inst, SLUGS, "extra", on, progress, force)
    if on:
        try:
            setup_shader(inst, progress)
        except Exception as e:  # offline: try again next launch
            print(f"[peregrine] shader pack not set up: {e}")
    return summary


def setup_shader(inst, progress=None) -> str:
    """The first time: a shader pack into shaderpacks/, chosen in Iris, but switched off."""
    folder = inst.game_dir / "shaderpacks"
    old = inst.data.get("extra_shader")
    if old and any(tag in old.lower() for tag in OLD_SHADERS):
        (folder / old).unlink(missing_ok=True)
        cfg = inst.game_dir / "config" / "iris.properties"
        if cfg.exists():
            lines = [l for l in cfg.read_text().splitlines() if not l.startswith("shaderPack=")]
            cfg.write_text("\n".join(lines) + "\n")
        inst.update(extra_shader=None)
        old = None
    if old:
        return old
    name = None
    for project in SHADERS:
        try:
            name = modrinth.install_pack(project, inst.data["mc_version"], folder, progress)
            break
        except Exception as e:
            print(f"[peregrine] {project}: {e}")
    if not name:
        return ""
    cfg = inst.game_dir / "config" / "iris.properties"
    cfg.parent.mkdir(parents=True, exist_ok=True)
    lines = cfg.read_text().splitlines() if cfg.exists() else []
    if not any(l.startswith("shaderPack=") for l in lines):
        lines.append(f"shaderPack={name}")
    if not any(l.startswith("enableShaders=") for l in lines):
        lines.append("enableShaders=false")
    cfg.write_text("\n".join(lines) + "\n")
    inst.update(extra_shader=name, extra_shader_at=time.time())
    return name


def set_enabled(inst, on: bool) -> None:
    """Switch the extras for one instance (off takes out what we added)."""
    inst.update(extras=on, extra_checked=0, extra_off=[] if on else inst.data.get("extra_off", []))
    if not on:
        sync(inst)


def after_crash(inst) -> list:
    """After a crash: switch off an extra that caused it (for this Minecraft version).
    Iris needs OpenGL; on a PC where Minecraft 26.3+ falls back to Vulkan it can't run."""
    blamed = performance.after_crash(inst, "extra", MODS)
    tracked = dict(inst.data.get("extra_files") or {})
    if "iris" in tracked:
        try:
            log = inst.log_file.read_text(errors="replace")[-400000:]
        except OSError:
            log = ""
        if "Failed to create backend OpenGL" in log and "No context is current" in log:
            f = inst.game_dir / "mods" / tracked.pop("iris")
            f.unlink(missing_ok=True)
            skip = inst.data.get("extra_skip") or {}
            skip["iris"] = inst.data["mc_version"]
            inst.update(extra_files=tracked, extra_skip=skip)
            blamed.append("Iris Shaders")
    return blamed
