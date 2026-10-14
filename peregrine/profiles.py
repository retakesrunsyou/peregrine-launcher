"""Peregrine profiles: share your setup under your Minecraft name, and grab a friend's.

A profile lists the mods, resource packs and shaders you added from Modrinth (by Modrinth
id, so friends download them from Modrinth themselves, matched to their own Minecraft
version) and your in-game mod settings. Nothing is shared until you switch sharing on.

The service lives at PROFILES_URL (a free Cloudflare Worker, see profiles/README.md). To
save your profile the launcher proves who you are with Mojang's own "join" handshake, the
one joining a server uses, so nobody can post as you and your login never leaves Mojang.
"""

import hashlib
import json
import shutil
from pathlib import Path

from . import PROFILES_URL, VERSION, client_mod, modrinth, net, paths

# In-game settings that are about you rather than your setup: never shared.
PRIVATE_MODULES = {"waypoints", "nick_hider", "quick_commands", "anti_afk"}
KINDS = (("mods", "mods"), ("resourcepacks", "resourcepacks"), ("shaders", "shaderpacks"))


class NotSetUp(RuntimeError):
    pass


def available() -> bool:
    return bool(PROFILES_URL)


def _url(path: str) -> str:
    if not PROFILES_URL:
        raise NotSetUp("Profiles aren't switched on in this version of Peregrine yet.")
    return PROFILES_URL.rstrip("/") + path


def _sha1(path: Path) -> str:
    h = hashlib.sha1()
    with open(path, "rb") as f:
        for block in iter(lambda: f.read(1 << 20), b""):
            h.update(block)
    return h.hexdigest()


def _ours(inst) -> set:
    """Files Peregrine adds by itself (performance mods, extras, the in-game mod): every
    instance gets those anyway, so they aren't part of anyone's profile."""
    names = set((inst.data.get("perf_files") or {}).values()) | set((inst.data.get("extra_files") or {}).values())
    return names


def _content(inst, folder: str) -> list:
    d = inst.game_dir / folder
    if not d.is_dir():
        return []
    skip = _ours(inst)
    out = []
    for p in sorted(d.iterdir()):
        if p.is_file() and p.suffix in (".jar", ".zip") and p.name not in skip \
                and not p.name.startswith(client_mod.PREFIX):
            out.append(p)
    return out


def client_settings() -> dict:
    """Your in-game mod settings, without the private parts."""
    try:
        data = json.loads(paths.CLIENT_SETTINGS.read_text())
    except (OSError, ValueError):
        return {}
    data.pop("lastServer", None)  # where you play is yours
    mods = data.get("modules")
    if isinstance(mods, dict):
        data["modules"] = {k: v for k, v in mods.items() if k not in PRIVATE_MODULES}
    return data


def build(inst, include_settings: bool = True) -> dict:
    """What your profile will show, from one instance: only things that are on Modrinth."""
    files = {kind: _content(inst, folder) for kind, folder in KINDS}
    hashes = {_sha1(p): (kind, p) for kind, ps in files.items() for p in ps}
    found = {}
    if hashes:
        r = net.session.post(f"{modrinth.API}/version_files", json={"hashes": list(hashes), "algorithm": "sha1"},
                             timeout=30)
        r.raise_for_status()
        found = r.json()
    projects = sorted({v["project_id"] for v in found.values()})
    info = {}
    if projects:
        for p in net.get_json(f"{modrinth.API}/projects", params={"ids": json.dumps(projects)}):
            info[p["id"]] = p
    out = {"minecraft": inst.data.get("mc_version", ""), "mods": [], "resourcepacks": [], "shaders": [],
           "skipped": 0}
    for h, (kind, path) in hashes.items():
        v = found.get(h)
        if not v:
            out["skipped"] += 1  # not from Modrinth: can't be shared
            continue
        p = info.get(v["project_id"], {})
        out[kind].append({"project": v["project_id"], "slug": p.get("slug", ""), "title": p.get("title", path.stem),
                          "icon": p.get("icon_url") or "", "file": path.name})
    for kind, _ in KINDS:
        out[kind].sort(key=lambda m: m["title"].lower())
    out["client_settings"] = client_settings() if include_settings else None
    return out


def _prove(account: dict) -> str:
    """Mojang's join handshake for a one-time id from the service. Returns that id."""
    if account.get("type") != "msa" or not account.get("mc_token"):
        raise RuntimeError("Sign in with a Microsoft account to share a profile.")
    sid = net.session.post(_url("/v1/challenge"), json={"name": account["name"]}, timeout=30)
    sid.raise_for_status()
    server_id = sid.json()["serverId"]
    r = net.session.post("https://sessionserver.mojang.com/session/minecraft/join", timeout=30, json={
        "accessToken": account["mc_token"], "selectedProfile": account["uuid"].replace("-", ""), "serverId": server_id})
    if r.status_code not in (200, 204):
        raise RuntimeError("Mojang didn't accept the sign-in. Sign in again on the Accounts page.")
    return server_id


def share(account: dict, inst, include_settings: bool = True) -> dict:
    """Turn sharing on (or update what's shared). Returns what was shared."""
    from . import auth, config
    account = auth.refresh(account, config.load().get("client_id", ""))
    auth.add_account(account)
    profile = build(inst, include_settings)
    server_id = _prove(account)
    body = {"name": account["name"], "serverId": server_id, "launcher": VERSION,
            "profile": {k: v for k, v in profile.items() if k != "skipped"}}
    r = net.session.put(_url("/v1/profile"), json=body, timeout=30)
    if r.status_code != 200:
        raise RuntimeError(r.json().get("error", f"Couldn't share ({r.status_code})"))
    return profile


def unshare(account: dict) -> None:
    from . import auth, config
    account = auth.refresh(account, config.load().get("client_id", ""))
    auth.add_account(account)
    server_id = _prove(account)
    r = net.session.delete(_url("/v1/profile"), json={"name": account["name"], "serverId": server_id}, timeout=30)
    if r.status_code != 200:
        raise RuntimeError(r.json().get("error", f"Couldn't stop sharing ({r.status_code})"))


def lookup(name: str) -> dict:
    """A player's shared profile. Raises LookupError if they haven't shared one."""
    name = name.strip()
    r = net.session.get(_url(f"/v1/profile/{name}"), timeout=30)
    if r.status_code == 404:
        raise LookupError(f"{name} hasn't shared a Peregrine profile.")
    r.raise_for_status()
    return r.json()


def take(profile: dict, inst, mods=(), resourcepacks=(), shaders=(), settings: bool = False, progress=None) -> dict:
    """Download the picked things from Modrinth into an instance (matched to its Minecraft
    version), and optionally use their in-game mod settings. Returns a summary."""
    mc, loader = inst.data["mc_version"], inst.data.get("loader", "fabric")
    summary = {"added": [], "unavailable": [], "settings": False}
    if mods:
        got = modrinth.install(list(mods), mc, loader, inst.game_dir / "mods", progress)
        got_ids = {pid for pid, _ in got}
        summary["added"] += [f for _, f in got]
        summary["unavailable"] += [m for m in mods if m not in got_ids]
    for kind, folder, picks in (("resourcepacks", "resourcepacks", resourcepacks), ("shaders", "shaderpacks", shaders)):
        for project in picks:
            try:
                summary["added"].append(modrinth.install_pack(project, mc, inst.game_dir / folder, progress))
            except Exception:
                summary["unavailable"].append(project)
    if settings and profile.get("client_settings"):
        theirs = dict(profile["client_settings"])
        target = paths.CLIENT_SETTINGS
        target.parent.mkdir(parents=True, exist_ok=True)
        mine = {}
        if target.exists():
            shutil.copy2(target, target.with_suffix(".before-profile.json"))  # one step back
            try:
                mine = json.loads(target.read_text())
            except ValueError:
                mine = {}
        # keep your own private bits (waypoints, nick, last server...), take everything else
        if "lastServer" in mine:
            theirs["lastServer"] = mine["lastServer"]
        if isinstance(mine.get("modules"), dict) and isinstance(theirs.get("modules"), dict):
            for k in PRIVATE_MODULES:
                if k in mine["modules"]:
                    theirs["modules"][k] = mine["modules"][k]
        target.write_text(json.dumps(theirs, indent=2))
        summary["settings"] = True
    return summary
