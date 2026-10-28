"""Peregrine profiles: share your setup under your Minecraft name, and grab a friend's.

A profile lists the mods, resource packs and shaders you added from Modrinth (by Modrinth
id, so friends download them from Modrinth themselves, matched to their own Minecraft
version) and your in-game mod settings. Nothing is shared until you switch sharing on.

The service lives at PROFILES_URL (a free Cloudflare Worker, see profiles/README.md). To
save your profile the launcher proves who you are with the chat-signing key Minecraft gives
every account: Mojang signs that key for your account, and the launcher signs a one-time
challenge with it. Nobody can post as you, and your login never leaves your computer.
"""

import base64
import hashlib
import json
import re
import shutil
from datetime import datetime, timezone
from pathlib import Path

from . import PROFILES_URL, VERSION, client_mod, modrinth, net, paths, rsasign

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


CERTIFICATES = "https://api.minecraftservices.com/player/certificates"


def _millis(stamp: str) -> int:
    """Mojang's ISO time (sometimes with micro- or nanoseconds) as epoch milliseconds, cut the
    way Minecraft cuts it."""
    m = re.match(r"(\d{4}-\d\d-\d\dT\d\d:\d\d:\d\d)(?:\.(\d+))?(Z|[+-]\d\d:?\d\d)?$", stamp.strip())
    if not m:
        raise ValueError(f"odd time from Mojang: {stamp}")
    zone = m.group(3) or "Z"
    zone = "+00:00" if zone == "Z" else zone
    when = datetime.fromisoformat(m.group(1) + zone)
    ms = int((m.group(2) or "0")[:3].ljust(3, "0"))
    return int(when.astimezone(timezone.utc).timestamp()) * 1000 + ms


def _certificate(account: dict) -> dict:
    """The chat-signing key pair Minecraft gets for this account, with Mojang's signature."""
    head = {"Authorization": f"Bearer {account['mc_token']}"}
    r = net.session.post(CERTIFICATES, headers=head, timeout=30)
    if r.status_code in (400, 415):
        r = net.session.post(CERTIFICATES, headers=head, json={}, timeout=30)
    if r.status_code == 401:
        raise RuntimeError("Your sign-in ran out. Sign in again on the Accounts page.")
    if r.status_code != 200:
        raise RuntimeError(f"Mojang didn't hand out your game key ({r.status_code}). Try again in a minute.")
    return r.json()


def _prove(account: dict) -> dict:
    """Proof that this request comes from this account, for the service to check."""
    if account.get("type") != "msa" or not account.get("mc_token"):
        raise RuntimeError("Sign in with a Microsoft account to use this.")
    cert = _certificate(account)
    pair = cert.get("keyPair") or {}
    sig_v2 = cert.get("publicKeySignatureV2") or ""
    if not pair.get("privateKey") or not pair.get("publicKey") or not sig_v2:
        raise RuntimeError("Mojang's answer was missing your game key. Try again in a minute.")
    uuid = account["uuid"].replace("-", "").lower()
    c = net.session.post(_url("/v1/challenge"), json={}, timeout=30)
    if c.status_code != 200:
        raise RuntimeError(f"The Peregrine service didn't answer ({c.status_code}).")
    nonce = c.json()["nonce"]
    sig = rsasign.sign_sha256(pair["privateKey"], f"peregrine:{nonce}:{uuid}".encode())
    return {"uuid": uuid, "nonce": nonce, "sig": base64.b64encode(sig).decode(),
            "cert": {"publicKey": base64.b64encode(rsasign.pem_body(pair["publicKey"])).decode(),
                     "expiresAt": _millis(cert["expiresAt"]), "keySig": sig_v2}}


def _error(r, fallback: str) -> str:
    try:
        return r.json().get("error") or fallback
    except ValueError:
        return fallback


def _fresh(account: dict) -> dict:
    from . import auth, config
    account = auth.refresh(account, config.load().get("client_id", ""))
    auth.add_account(account)
    return account


def share(account: dict, inst, include_settings: bool = True) -> dict:
    """Turn sharing on (or update what's shared). Returns what was shared."""
    account = _fresh(account)
    profile = build(inst, include_settings)
    body = dict(_prove(account), name=account["name"], launcher=VERSION,
                profile={k: v for k, v in profile.items() if k != "skipped"})
    r = net.session.put(_url("/v1/profile"), json=body, timeout=30)
    if r.status_code != 200:
        raise RuntimeError(_error(r, f"Couldn't share ({r.status_code})"))
    return profile


def unshare(account: dict) -> None:
    account = _fresh(account)
    r = net.session.delete(_url("/v1/profile"), json=_prove(account), timeout=30)
    if r.status_code != 200:
        raise RuntimeError(_error(r, f"Couldn't stop sharing ({r.status_code})"))


# ---------------------------------------------------------------------- badges and capes

# the in-game Cosmetics list; your own cape (the last) is never shared, so others see no cape
CAPE_FILES = ("", "falcon", "violet", "ember", "starry", "pumpkin", "frost", "")
RESEND_AFTER = 20 * 3600


def chosen_cape() -> str:
    """The cape picked in the in-game Cosmetics settings ("" for none or Cosmetics off)."""
    try:
        data = json.loads(paths.CLIENT_SETTINGS.read_text())
        m = (data.get("modules") or {}).get("cosmetics") or {}
        if m.get("enabled") is False:
            return ""
        i = int((m.get("settings") or {}).get("cape", 1))
        return CAPE_FILES[i] if 0 <= i < len(CAPE_FILES) else ""
    except (OSError, ValueError, TypeError):
        return CAPE_FILES[1]  # the default: Falcon


def announce(account: dict, force: bool = False) -> str:
    """Tells the service this account plays with Peregrine (and its cape), so other Peregrine
    players see the falcon by its name. Only now and then: when the cape changed, or about
    once a day. Returns what happened, for the log."""
    from . import config
    cfg = config.load()
    if not available() or not cfg.get("show_badge", True):
        return "off"
    if account.get("type") != "msa":
        return "not a Microsoft account"
    cape = chosen_cape()
    sent = dict(cfg.get("badge_sent") or {})
    last = sent.get(account["uuid"]) or {}
    import time
    if not force and last.get("cape") == cape and time.time() - last.get("at", 0) < RESEND_AFTER:
        return "up to date"
    account = _fresh(account)
    r = net.session.put(_url("/v1/presence"), json=dict(_prove(account), cape=cape), timeout=30)
    if r.status_code != 200:
        raise RuntimeError(_error(r, f"badge not saved ({r.status_code})"))
    cfg = config.load()
    sent = dict(cfg.get("badge_sent") or {})
    sent[account["uuid"]] = {"cape": cape, "at": time.time()}
    config.save(dict(cfg, badge_sent=sent))
    return f"badge on, cape {cape or 'none'}"


def hide(account: dict) -> None:
    """Switch the badge off: the service forgets this account."""
    from . import config
    if not available() or account.get("type") != "msa":
        return
    account = _fresh(account)
    r = net.session.delete(_url("/v1/presence"), json=_prove(account), timeout=30)
    if r.status_code != 200:
        raise RuntimeError(_error(r, f"couldn't switch the badge off ({r.status_code})"))
    cfg = config.load()
    sent = dict(cfg.get("badge_sent") or {})
    sent.pop(account["uuid"], None)
    config.save(dict(cfg, badge_sent=sent))


def player_id(name: str) -> tuple:
    """(account id, name as Mojang spells it) for a Minecraft name, or for an account id."""
    name = name.strip()
    if re.fullmatch(r"[0-9a-fA-F]{32}|[0-9a-fA-F-]{36}", name):
        return name.replace("-", "").lower(), name
    if not re.fullmatch(r"[A-Za-z0-9_]{1,16}", name):
        raise LookupError(f"{name} isn't a Minecraft name.")
    for url in (f"https://api.mojang.com/users/profiles/minecraft/{name}",
                f"https://api.minecraftservices.com/minecraft/profile/lookup/name/{name}"):
        try:
            r = net.session.get(url, timeout=20)
        except Exception:
            continue
        if r.status_code == 200:
            p = r.json()
            return p["id"].replace("-", "").lower(), p.get("name", name)
        if r.status_code in (204, 404):
            raise LookupError(f"There's no Minecraft player called {name}.")
    raise RuntimeError("Couldn't reach Mojang to look that name up. Try again in a minute.")


def lookup(name: str) -> dict:
    """A player's shared profile. Raises LookupError if they haven't shared one."""
    uuid, spelled = player_id(name)
    r = net.session.get(_url(f"/v1/profile/{uuid}"), timeout=30)
    if r.status_code == 404:
        raise LookupError(f"{spelled} hasn't shared a Peregrine profile.")
    r.raise_for_status()
    p = r.json()
    if spelled and not re.fullmatch(r"[0-9a-f]{32}", spelled):
        p["name"] = spelled  # the name Mojang has now (they may have renamed since sharing)
    return p


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
