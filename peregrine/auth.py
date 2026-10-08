"""Microsoft account login (device code flow) and account storage.

The chain is: Microsoft -> Xbox Live -> XSTS -> Minecraft. You need your own
Azure app "client ID", and Mojang must approve it for the Minecraft API.
"""

import hashlib
import json
import time
import uuid as uuidlib

from . import config, net, paths

MS_DEVICE = "https://login.microsoftonline.com/consumers/oauth2/v2.0/devicecode"
MS_TOKEN = "https://login.microsoftonline.com/consumers/oauth2/v2.0/token"
XBL_AUTH = "https://user.auth.xboxlive.com/user/authenticate"
XSTS_AUTH = "https://xsts.auth.xboxlive.com/xsts/authorize"
MC_LOGIN = "https://api.minecraftservices.com/authentication/login_with_xbox"
MC_PROFILE = "https://api.minecraftservices.com/minecraft/profile"
SCOPE = "XboxLive.signin offline_access"

XSTS_ERRORS = {
    2148916233: "This Microsoft account has no Xbox profile yet. Sign in once at xbox.com, then try again.",
    2148916235: "Xbox Live isn't available in this account's country.",
    2148916236: "This account needs adult verification on xbox.com.",
    2148916237: "This account needs adult verification on xbox.com.",
    2148916238: "This is a child account. An adult needs to add it to a Microsoft family first.",
}


class LoginError(Exception):
    pass


# ----------------------------------------------------------- device code

def start_device_login(client_id: str) -> dict:
    if not client_id:
        raise LoginError("No client ID set. Add your Azure app's client ID in Settings.")
    r = net.session.post(MS_DEVICE, data={"client_id": client_id, "scope": SCOPE}, timeout=30)
    if r.status_code != 200:
        raise LoginError(f"Microsoft rejected the login request: {r.text}")
    return r.json()  # user_code, verification_uri, device_code, interval, expires_in


def wait_for_device_login(client_id: str, flow: dict, cancelled=lambda: False) -> dict:
    interval = flow.get("interval", 5)
    deadline = time.time() + flow.get("expires_in", 900)
    while time.time() < deadline:
        if cancelled():
            raise LoginError("Login cancelled.")
        time.sleep(interval)
        r = net.session.post(MS_TOKEN, timeout=30, data={
            "grant_type": "urn:ietf:params:oauth:grant-type:device_code",
            "client_id": client_id,
            "device_code": flow["device_code"],
        })
        data = r.json()
        if r.status_code == 200:
            return data
        err = data.get("error")
        if err == "authorization_pending":
            continue
        if err == "slow_down":
            interval += 5
            continue
        raise LoginError(data.get("error_description", err or "Login failed."))
    raise LoginError("The login code expired. Try again.")


# --------------------------------------------------- Xbox -> Minecraft

def _post_json(url, body, headers=None):
    h = {"Content-Type": "application/json", "Accept": "application/json"}
    h.update(headers or {})
    return net.session.post(url, data=json.dumps(body), headers=h, timeout=30)


def minecraft_login(ms_access_token: str) -> dict:
    r = _post_json(XBL_AUTH, {
        "Properties": {"AuthMethod": "RPS", "SiteName": "user.auth.xboxlive.com",
                       "RpsTicket": f"d={ms_access_token}"},
        "RelyingParty": "http://auth.xboxlive.com", "TokenType": "JWT"})
    if r.status_code != 200:
        raise LoginError(f"Xbox Live sign-in failed ({r.status_code}).")
    xbl = r.json()

    r = _post_json(XSTS_AUTH, {
        "Properties": {"SandboxId": "RETAIL", "UserTokens": [xbl["Token"]]},
        "RelyingParty": "rp://api.minecraftservices.com/", "TokenType": "JWT"})
    if r.status_code != 200:
        code = r.json().get("XErr") if r.content else None
        raise LoginError(XSTS_ERRORS.get(code, f"Xbox authorization failed ({code or r.status_code})."))
    xsts = r.json()
    uhs = xsts["DisplayClaims"]["xui"][0]["uhs"]

    r = _post_json(MC_LOGIN, {"identityToken": f"XBL3.0 x={uhs};{xsts['Token']}"})
    if r.status_code == 403:
        raise LoginError("Mojang hasn't approved this client ID for Minecraft logins yet. "
                         "Apply via Mojang's form for third-party launchers.")
    if r.status_code != 200:
        raise LoginError(f"Minecraft sign-in failed ({r.status_code}).")
    mc = r.json()

    r = net.session.get(MC_PROFILE, headers={"Authorization": f"Bearer {mc['access_token']}"}, timeout=30)
    if r.status_code == 404:
        raise LoginError("This account doesn't own Minecraft: Java Edition.")
    if r.status_code != 200:
        raise LoginError(f"Couldn't load the Minecraft profile ({r.status_code}).")
    profile = r.json()

    return {
        "type": "msa",
        "name": profile["name"],
        "uuid": profile["id"],
        "xuid": xbl.get("DisplayClaims", {}).get("xui", [{}])[0].get("uhs", ""),
        "mc_token": mc["access_token"],
        "mc_expires": time.time() + mc.get("expires_in", 86400) - 300,
    }


def complete_login(ms_tokens: dict) -> dict:
    account = minecraft_login(ms_tokens["access_token"])
    account["ms_refresh"] = ms_tokens.get("refresh_token", "")
    return account


def refresh(account: dict, client_id: str) -> dict:
    """Return a fresh account (new tokens) if the old one expired."""
    if account.get("type") != "msa" or time.time() < account.get("mc_expires", 0):
        return account
    if not account.get("ms_refresh"):
        raise LoginError("Session expired. Please sign in again.")
    r = net.session.post(MS_TOKEN, timeout=30, data={
        "grant_type": "refresh_token", "client_id": client_id,
        "refresh_token": account["ms_refresh"], "scope": SCOPE})
    if r.status_code != 200:
        raise LoginError("Session expired. Please sign in again.")
    return complete_login(r.json())


# --------------------------------------------------------------- offline

def offline_account(name: str) -> dict:
    """Developer-only account for testing singleplayer. Can't join online servers."""
    digest = bytearray(hashlib.md5(f"OfflinePlayer:{name}".encode()).digest())
    digest[6] = (digest[6] & 0x0F) | 0x30  # UUID version 3
    digest[8] = (digest[8] & 0x3F) | 0x80
    return {"type": "offline", "name": name, "uuid": uuidlib.UUID(bytes=bytes(digest)).hex}


# --------------------------------------------------------------- storage

def load_accounts() -> dict:
    try:
        return json.loads(paths.ACCOUNTS_FILE.read_text())
    except (FileNotFoundError, json.JSONDecodeError):
        return {"active": None, "accounts": []}


def save_accounts(data: dict) -> None:
    config.write_private(paths.ACCOUNTS_FILE, data)


def add_account(account: dict) -> None:
    data = load_accounts()
    data["accounts"] = [a for a in data["accounts"] if a["uuid"] != account["uuid"]] + [account]
    data["active"] = account["uuid"]
    save_accounts(data)


def remove_account(uuid: str) -> None:
    data = load_accounts()
    data["accounts"] = [a for a in data["accounts"] if a["uuid"] != uuid]
    if data["active"] == uuid:
        data["active"] = data["accounts"][0]["uuid"] if data["accounts"] else None
    save_accounts(data)


def active_account():
    data = load_accounts()
    return next((a for a in data["accounts"] if a["uuid"] == data["active"]), None)
