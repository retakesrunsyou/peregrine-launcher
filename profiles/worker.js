// Peregrine profiles: a tiny free service (Cloudflare Workers + KV) where players can share
// their setup (mods, resource packs and shaders from Modrinth, and their in-game mod
// settings) under their Minecraft name, so friends can look it up in the launcher.
//
// Nobody can post as someone else: to save or remove a profile, the launcher first does the
// same "join" handshake with Mojang that joining a server does, and this service asks Mojang
// whether that name really joined (the player's login never comes here). Profiles are only
// stored once a player turns sharing on, and removing sharing deletes them.

const MAX_BYTES = 256 * 1024;
const NAME = /^[A-Za-z0-9_]{3,16}$/;
const UUID = /^[0-9a-f]{32}$/;
const CAPES = ["", "falcon", "violet", "ember", "starry", "pumpkin", "frost"];
const PRESENCE_DAYS = 60;

function reply(body, status = 200) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "content-type": "application/json", "access-control-allow-origin": "*" },
  });
}

function randomHex(bytes) {
  const a = new Uint8Array(bytes);
  crypto.getRandomValues(a);
  return [...a].map((b) => b.toString(16).padStart(2, "0")).join("");
}

// Asks Mojang whether this player just did the join handshake with this server id.
async function verified(name, serverId) {
  const url = `https://sessionserver.mojang.com/session/minecraft/hasJoined?username=${encodeURIComponent(name)}&serverId=${encodeURIComponent(serverId)}`;
  const r = await fetch(url);
  if (r.status !== 200) return null;
  const p = await r.json();
  return p && p.id ? { uuid: p.id, name: p.name } : null;
}

// One use only: the id is forgotten as soon as it's checked.
async function takeChallenge(env, serverId) {
  if (!/^[0-9a-f]{40}$/.test(serverId || "")) return null;
  const key = `challenge:${serverId}`;
  const name = await env.PROFILES.get(key);
  if (name) await env.PROFILES.delete(key);
  return name;
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    const path = url.pathname.replace(/\/+$/, "");
    if (request.method === "OPTIONS") return reply({});

    // 1. The launcher asks for a one-time server id to do Mojang's join handshake with.
    if (path === "/v1/challenge" && request.method === "POST") {
      const { name } = await request.json().catch(() => ({}));
      if (!NAME.test(name || "")) return reply({ error: "bad name" }, 400);
      const serverId = randomHex(20);
      await env.PROFILES.put(`challenge:${serverId}`, name.toLowerCase(), { expirationTtl: 300 });
      return reply({ serverId });
    }

    // 2. Save (or remove) your profile, proven with the handshake.
    if (path === "/v1/profile" && (request.method === "PUT" || request.method === "DELETE")) {
      const raw = await request.text();
      if (raw.length > MAX_BYTES) return reply({ error: "profile too big" }, 413);
      let body;
      try { body = JSON.parse(raw); } catch { return reply({ error: "bad json" }, 400); }
      const name = body.name || "";
      if (!NAME.test(name)) return reply({ error: "bad name" }, 400);
      const asked = await takeChallenge(env, body.serverId);
      if (!asked || asked !== name.toLowerCase()) return reply({ error: "start again" }, 401);
      const who = await verified(name, body.serverId);
      if (!who) return reply({ error: "Mojang didn't confirm this account" }, 401);
      const key = `profile:${who.name.toLowerCase()}`;
      if (request.method === "DELETE") {
        await env.PROFILES.delete(key);
        return reply({ ok: true, removed: true });
      }
      const profile = body.profile || {};
      const stored = {
        name: who.name,
        uuid: who.uuid,
        updated: new Date().toISOString(),
        launcher: String(body.launcher || "").slice(0, 20),
        minecraft: String(profile.minecraft || "").slice(0, 20),
        mods: (profile.mods || []).slice(0, 300),
        resourcepacks: (profile.resourcepacks || []).slice(0, 100),
        shaders: (profile.shaders || []).slice(0, 50),
        client_settings: profile.client_settings || null,
      };
      await env.PROFILES.put(key, JSON.stringify(stored));
      return reply({ ok: true, name: who.name });
    }

    // 3. "I play with Peregrine": other Peregrine players then see the falcon next to your
    //    name and the cape you picked. Proven with the handshake like profiles; only your
    //    account id, cape and the day you last played are kept, for 60 days after you last play.
    if (path === "/v1/presence" && (request.method === "PUT" || request.method === "DELETE")) {
      const body = await request.json().catch(() => ({}));
      const name = body.name || "";
      if (!NAME.test(name)) return reply({ error: "bad name" }, 400);
      const asked = await takeChallenge(env, body.serverId);
      if (!asked || asked !== name.toLowerCase()) return reply({ error: "start again" }, 401);
      const who = await verified(name, body.serverId);
      if (!who) return reply({ error: "Mojang didn't confirm this account" }, 401);
      const key = `user:${who.uuid.toLowerCase()}`;
      if (request.method === "DELETE") {
        await env.PROFILES.delete(key);
        return reply({ ok: true, removed: true });
      }
      const cape = CAPES.includes(body.cape) ? body.cape : "";
      await env.PROFILES.put(key, JSON.stringify({ cape, seen: new Date().toISOString().slice(0, 10) }),
                             { expirationTtl: PRESENCE_DAYS * 86400 });
      return reply({ ok: true, uuid: who.uuid, cape });
    }

    // 4. Which of these players use Peregrine? (the game asks about players it can see)
    if (path === "/v1/players" && request.method === "POST") {
      const body = await request.json().catch(() => ({}));
      const ids = [...new Set((body.uuids || []).map((u) => String(u).replace(/-/g, "").toLowerCase()))]
        .filter((u) => UUID.test(u)).slice(0, 100);
      const found = await Promise.all(ids.map((u) => env.PROFILES.get(`user:${u}`)));
      const players = {};
      ids.forEach((u, i) => {
        if (!found[i]) return;
        try { players[u] = { cape: JSON.parse(found[i]).cape || "" }; } catch { players[u] = { cape: "" }; }
      });
      return reply({ players });
    }

    // 5. Look someone up by Minecraft name.
    const m = path.match(/^\/v1\/profile\/([A-Za-z0-9_]{3,16})$/);
    if (m && request.method === "GET") {
      const p = await env.PROFILES.get(`profile:${m[1].toLowerCase()}`);
      return p ? new Response(p, { headers: { "content-type": "application/json", "access-control-allow-origin": "*" } })
               : reply({ error: "not shared" }, 404);
    }

    if (path === "" || path === "/v1") return reply({ service: "peregrine-profiles", ok: true });
    return reply({ error: "not found" }, 404);
  },
};
