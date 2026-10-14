"""Friends: your Xbox friends (the friends list Minecraft uses for Realms and
Bedrock), who's online, and adding or removing people by gamertag.

Minecraft: Java Edition has no friends list of its own; the Microsoft account you
play with does, so that's the one shown here.
"""

import json
import time

from . import auth, config, net

XSTS_AUTH = "https://xsts.auth.xboxlive.com/xsts/authorize"
PEOPLE = "https://peoplehub.xboxlive.com/users/me/people"
SOCIAL = "https://social.xboxlive.com/users/me/people"


class FriendsError(Exception):
    pass


def _token(account: dict) -> dict:
    """{'header', 'xuid', 'gamertag'} for Xbox Live calls, cached until it expires."""
    cached = account.get("xbl_social") or {}
    if cached.get("header") and time.time() < cached.get("expires", 0):
        return cached
    if account.get("type") != "msa":
        raise FriendsError("Friends need a Microsoft account. Sign in under Accounts.")
    client_id = config.load().get("client_id", "")
    try:
        user = auth.xbox_user_token(auth.fresh_ms_token(account, client_id))
    except auth.LoginError as e:
        raise FriendsError(str(e))
    r = net.session.post(XSTS_AUTH, timeout=30, headers={"Content-Type": "application/json",
                                                         "Accept": "application/json"},
                         data=json.dumps({"Properties": {"SandboxId": "RETAIL", "UserTokens": [user["Token"]]},
                                          "RelyingParty": "http://xboxlive.com", "TokenType": "JWT"}))
    if r.status_code != 200:
        code = r.json().get("XErr") if r.content else None
        raise FriendsError(auth.XSTS_ERRORS.get(code, f"Xbox Live said no ({code or r.status_code})."))
    xsts = r.json()
    claims = xsts["DisplayClaims"]["xui"][0]
    token = {"header": f"XBL3.0 x={claims['uhs']};{xsts['Token']}",
             "xuid": claims.get("xid", ""), "gamertag": claims.get("gtg", ""),
             "expires": time.time() + 12 * 3600}
    account["xbl_social"] = token
    auth.update_account(account)
    return token


def _headers(account: dict, contract: str) -> dict:
    return {"Authorization": _token(account)["header"], "x-xbl-contract-version": contract,
            "Accept-Language": "en-US", "Accept": "application/json"}


def _person(p: dict) -> dict:
    state = (p.get("presenceState") or "Offline")
    detail = p.get("presenceDetails") or []
    playing = next((d.get("PresenceText") or d.get("presenceText") for d in detail
                    if d.get("IsPrimary") or d.get("isPrimary")), "") or p.get("presenceText") or ""
    return {
        "xuid": p.get("xuid", ""),
        "gamertag": p.get("modernGamertag") or p.get("gamertag") or p.get("displayName") or "?",
        "picture": p.get("displayPicRaw") or "",
        "online": state.lower() == "online",
        "status": playing if state.lower() == "online" else "Offline",
        "minecraft": "minecraft" in playing.lower(),
        "friend": bool(p.get("isFollowingCaller")) and bool(p.get("isFollowedByCaller")),
        "following": bool(p.get("isFollowedByCaller")),
    }


def friends(account: dict) -> list:
    """Everyone you've added, online first (people playing Minecraft at the top)."""
    r = net.session.get(f"{PEOPLE}/social/decoration/detail,preferredColor,presenceDetail",
                        headers=_headers(account, "3"), timeout=20)
    if r.status_code == 401:
        account.pop("xbl_social", None)
        raise FriendsError("Xbox Live signed you out. Try again.")
    if r.status_code != 200:
        raise FriendsError(f"Couldn't load your friends ({r.status_code}).")
    people = [_person(p) for p in r.json().get("people", [])]
    return sorted(people, key=lambda p: (not p["online"], not p["minecraft"], p["gamertag"].lower()))


def search(account: dict, gamertag: str) -> list:
    r = net.session.get(f"{PEOPLE}/search/decoration/detail,preferredColor",
                        params={"q": gamertag, "maxItems": 10},
                        headers=_headers(account, "3"), timeout=20)
    if r.status_code != 200:
        raise FriendsError(f"Search failed ({r.status_code}).")
    return [_person(p) for p in r.json().get("people", [])]


def add(account: dict, xuid: str) -> None:
    r = net.session.put(f"{SOCIAL}/xuid({xuid})", headers=_headers(account, "2"), timeout=20)
    if r.status_code not in (200, 204):
        raise FriendsError(f"Couldn't add them ({r.status_code}).")


def remove(account: dict, xuid: str) -> None:
    r = net.session.delete(f"{SOCIAL}/xuid({xuid})", headers=_headers(account, "2"), timeout=20)
    if r.status_code not in (200, 204):
        raise FriendsError(f"Couldn't remove them ({r.status_code}).")
