# Peregrine profiles service

A tiny free service that lets Peregrine players share their setup under their Minecraft name
and look up each other's. It runs on Cloudflare Workers' free plan (100,000 requests a day),
so once it's set up there's nothing to pay or look after.

## What it stores

Only what a player chooses to share, and only after they press **Share** in the launcher:

- the mods, resource packs and shaders they added **from Modrinth** (by Modrinth id; their own files stay private)
- optionally their in-game mod settings (HUD layout, modules), **without** waypoints, nickname, quick commands or servers

**Stop sharing** deletes it. Nobody can post as someone else: the launcher does Mojang's own
"join" handshake (the one joining a server uses), and the service asks Mojang whether that
name really did it. Players' logins never reach this service.

## Set it up (once, about 5 minutes)

You need [Node.js](https://nodejs.org) installed. Then:

```bash
bash profiles/setup.sh
```

1. A browser opens to sign in to Cloudflare. Make a free account if you don't have one (no card needed).
2. The script makes the storage and puts the service online.
3. It prints the service's address, like `https://peregrine-profiles.<you>.workers.dev`.

Put that address in `peregrine/__init__.py` as `PROFILES_URL = "https://..."` and publish a
release. The **Profiles** page in the launcher switches on for everyone who updates.

## Check it's working

Open the address in a browser: it should say `{"service":"peregrine-profiles","ok":true}`.

## Tests

```bash
node tests/profiles_worker_test.mjs
```
