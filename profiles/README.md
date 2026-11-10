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

## Players' own capes

A cape made from your own picture, GIF or video in the Locker is shared too, once it passes a
safety check. The game sends a small copy (a still up to 512 px wide, or up to 12 frames up to
256 px wide); the service shows every picture to Cloudflare Workers AI (free plan, a vision model)
and keeps it only if it finds no nudity, sexual content, gore or hate symbols. Anything flagged, or
anything the check can't answer, isn't shared, and the player is told in their chat (they still
see their cape themselves). Each account can send 5 capes a day. In game, anyone can hide a
player's cape or report it (Cosmetics → "Own capes around you"); three different players
reporting a cape hide it from everyone until its owner makes a new one. Players can also turn
other players' own capes off altogether.

The check makes mistakes sometimes, both ways, so the reports are there too.

## Voice parties

The same service runs voice parties: a small room per party code (a Cloudflare Durable Object,
available on the free plan). Players join with a WebSocket and prove who they are with their
Minecraft key, like profiles; their voice is passed straight on to the others and never stored.
The free plan covers roughly a day of party time per day across everyone (each room-hour uses
about 450 of the 13,000 free GB-seconds); past that, new voice connections wait until the next
day. Cloudflare's $5 a month plan lifts that far beyond what a client like this needs.

## Check it's working

Open the address in a browser: it should say `{"service":"peregrine-profiles","version":5,"voice":true,"capes":true,"ok":true}`. If it
shows an older version, `"voice":false` or `"capes":false`, the service is still the old one: update it
as above (with the new `wrangler.toml` too).

To check the cape safety check, open `/v1/cape/selftest` on the same address: it shows a plain test
picture to the check and should say `"ok":true`. If it says `"ok":false`, the `errors` it lists say
why (the answer is kept for 10 minutes).

## Tests

```bash
node tests/profiles_worker_test.mjs
```
