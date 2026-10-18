# Peregrine profiles service

A tiny free service that lets Peregrine players share their setup under their Minecraft name
and look up each other's. It runs on Cloudflare Workers' free plan (100,000 requests a day),
so once it's set up there's nothing to pay or look after.

## What it stores

Only what a player chooses to share, and only after they press **Share** in the launcher:

- the mods, resource packs and shaders they added **from Modrinth** (by Modrinth id; their own files stay private)
- optionally their in-game mod settings (HUD layout, modules), **without** waypoints, nickname, quick commands or servers

**Stop sharing** deletes it.

It also powers **Peregrine badges and capes**: when you press Play, the launcher tells the
service that your account plays with Peregrine and which cape you picked in game (only your
account id, the cape and the day; forgotten 60 days after you last play). In game, Peregrine
asks about the players you can see, so everyone using Peregrine sees the falcon by each
other's names and each other's capes. Switch it off in Settings → "Show my Peregrine badge
to others". Nobody can post as someone else: Minecraft gives every account a chat-signing
key that Mojang signs for that account, and the launcher signs a one-time challenge with it.
The service checks both signatures itself (it never has to ask Mojang, which blocks
Cloudflare), and players' logins never reach it.

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

## Updating the service

After `profiles/worker.js` or `profiles/wrangler.toml` changes, put the new version online (your
storage and profiles stay):

```bash
cd profiles
npx --yes wrangler@3 deploy
```

## Voice parties

The same service runs voice parties: a small room per party code (a Cloudflare Durable Object,
available on the free plan). Players join with a WebSocket and prove who they are with their
Minecraft key, like profiles; their voice is passed straight on to the others and never stored.
The free plan covers roughly a day of party time per day across everyone (each room-hour uses
about 450 of the 13,000 free GB-seconds); past that, new voice connections wait until the next
day. Cloudflare's $5 a month plan lifts that far beyond what a client like this needs.

## Check it's working

Open the address in a browser: it should say `{"service":"peregrine-profiles","version":3,"voice":true,"ok":true}`. If it shows an
older version or `"voice":false`, the service is still the old one: update it as above (with the new
`wrangler.toml` too).

## Tests

```bash
node tests/profiles_worker_test.mjs
```
