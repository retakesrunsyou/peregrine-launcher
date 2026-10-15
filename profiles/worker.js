// Peregrine profiles: a tiny free service (Cloudflare Workers + KV) where players can share
// their setup (mods, resource packs and shaders from Modrinth, and their in-game mod
// settings) under their Minecraft account, so friends can look it up in the launcher.
//
// Nobody can post as someone else. Minecraft gives every account a key pair for chat
// signing, and Mojang signs the public half together with the account id. To save something
// the launcher sends that public key with Mojang's signature, plus its own signature on a
// one-time challenge made with the private key. This service checks both signatures itself,
// so it never has to ask Mojang anything, and no login or token ever comes here.
// Profiles are only stored once a player turns sharing on, and removing sharing deletes them.

const MAX_BYTES = 256 * 1024;
const NAME = /^[A-Za-z0-9_]{3,16}$/;
const UUID = /^[0-9a-f]{32}$/;
const CAPES = ["", "falcon", "violet", "ember", "starry", "pumpkin", "frost"];
const PRESENCE_DAYS = 60;

// Mojang's keys for signing players' chat keys ("playerCertificateKeys" from
// https://api.minecraftservices.com/publickeys). If Mojang adds a new one, the service
// fetches the list itself and remembers it for a day.
const MOJANG_KEYS = [
  "MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAylB4B6m5lz7jwrcFz6Fd/fnfUhcvlxsTSn5kIK/2aGG1C3kMy4VjhwlxF6BFUSnfxhNswPjh3ZitkBxEAFY25uzkJFRwHwVA9mdwjashXILtR6OqdLXXFVyUPIURLOSWqGNBtb08EN5fMnG8iFLgEJIBMxs9BvF3s3/FhuHyPKiVTZmXY0WY4ZyYqvoKR+XjaTRPPvBsDa4WI2u1zxXMeHlodT3lnCzVvyOYBLXL6CJgByuOxccJ8hnXfF9yY4F0aeL080Jz/3+EBNG8RO4ByhtBf4Ny8NQ6stWsjfeUIvH7bU/4zCYcYOq4WrInXHqS8qruDmIl7P5XXGcabuzQstPf/h2CRAUpP/PlHXcMlvewjmGU6MfDK+lifScNYwjPxRo4nKTGFZf/0aqHCh/EAsQyLKrOIYRE0lDG3bzBh8ogIMLAugsAfBb6M3mqCqKaTMAf/VAjh5FFJnjS+7bE+bZEV0qwax1CEoPPJL1fIQjOS8zj086gjpGRCtSy9+bTPTfTR/SJ+VUB5G2IeCItkNHpJX2ygojFZ9n5Fnj7R9ZnOM+L8nyIjPu3aePvtcrXlyLhH/hvOfIOjPxOlqW+O5QwSFP4OEcyLAUgDdUgyW36Z5mB285uKW/ighzZsOTevVUG2QwDItObIV6i8RCxFbN2oDHyPaO5j1tTaBNyVt8CAwEAAQ==",
  "MIICIjANBgkqhkiG9w0BAQEFAAOCAg8AMIICCgKCAgEAt4t9NPuu7cktclnaH7eZj0omkLcJHeLz5MKsyJEntHZ0INtuBjSSul3Pp3pBeJN8k3ADdcdBLUN90bcAi7WsQqTx3Ft363q3W7TbM8j2iTEdp/0uVspoRt/DP1tkaWFs/w2WwUv9jbVoBUzfUc4pSTIxRwdjmqjZQfvjwKNDbOx3IhP2H0WXodbISejPi1wBZqNW4m1rnZAXp/EpUguxA8mobCa4vUCBkyFDyXdl69/wUSJHyCPmgcMJ364OlAhIqtwVPShBZObvrK/f0BYk6ShJD3N7TFDatSYsIIdcTKRknaIm91s+EsMrdB9U4Yw+ZJ/pyCB4S3vk8zfDCnb0DWIxYH3/EMzaxl77djmTmMzi/JDITup5z3jfWtRZmrAhU2/+W5IO5hEpo3/bCS9PXIY5xb41Lmp2ZO8dXKtyD66Chchy0W129n8vPl2GIruOdrxsjZAHnneyAb9jm0uaGaphwnEnuecX/qgHY6ZMtayvLLsPst8PO6R1vufMy8WqjK+j7LnC1krL7CPDg0NEhyQTmw5l+NCNjSlvB1juM9V4PARg0bYCOkGXm7ydRCjSSH8CJXZpwnd5cBB5WKAX3KPzutRgMi/LFwNSMZzFuUyXaYOZPpD259yqph1LmGqegEdDriACVU+dVEONFMm8eIuBofe7ljmsAFKW9BINwK0CAwEAAQ=="
];

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

function unbase64(s) {
  const bin = atob(String(s || "").replace(/\s+/g, ""));
  const out = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) out[i] = bin.charCodeAt(i);
  return out;
}

async function rsaVerify(spki, hash, signature, data) {
  try {
    const key = await crypto.subtle.importKey("spki", spki, { name: "RSASSA-PKCS1-v1_5", hash }, false, ["verify"]);
    return await crypto.subtle.verify("RSASSA-PKCS1-v1_5", key, signature, data);
  } catch {
    return false;
  }
}

async function mojangKeys(env, fresh) {
  const keys = [...MOJANG_KEYS];
  let saved = await env.PROFILES.get("mojang-keys");
  if (!saved && fresh) {
    try {
      const r = await fetch("https://api.minecraftservices.com/publickeys", { headers: { "user-agent": "peregrine-profiles/2" } });
      if (r.ok) {
        const list = ((await r.json()).playerCertificateKeys || []).map((k) => k.publicKey).filter(Boolean);
        saved = JSON.stringify(list);
        await env.PROFILES.put("mojang-keys", saved, { expirationTtl: 86400 });
      }
    } catch {}
  }
  if (saved) for (const k of JSON.parse(saved)) if (!keys.includes(k)) keys.push(k);
  return keys;
}

// Did Mojang sign this chat key for this account? (the same check servers do)
async function mojangSigned(env, uuid, cert) {
  const publicKey = unbase64(cert.publicKey);
  const expires = Number(cert.expiresAt);
  if (!Number.isSafeInteger(expires) || expires < Date.now()) return false;
  const data = new Uint8Array(24 + publicKey.length);
  const view = new DataView(data.buffer);
  view.setBigUint64(0, BigInt("0x" + uuid.slice(0, 16)));
  view.setBigUint64(8, BigInt("0x" + uuid.slice(16)));
  view.setBigInt64(16, BigInt(expires));
  data.set(publicKey, 24);
  const signature = unbase64(cert.keySig);
  for (const fresh of [false, true]) {
    for (const k of await mojangKeys(env, fresh)) {
      if (await rsaVerify(unbase64(k), "SHA-1", signature, data)) return true;
    }
  }
  return false;
}

function challengeText(nonce, uuid) {
  return new TextEncoder().encode(`peregrine:${nonce}:${uuid}`);
}

// Who sent this? Returns the account id (no dashes), or a Response saying why not.
async function proven(env, body) {
  const uuid = String(body.uuid || "").replace(/-/g, "").toLowerCase();
  if (!UUID.test(uuid)) return reply({ error: "bad account id" }, 400);
  const nonce = String(body.nonce || "");
  if (!/^[0-9a-f]{40}$/.test(nonce)) return reply({ error: "start again" }, 401);
  const key = `challenge:${nonce}`;
  const issued = await env.PROFILES.get(key);
  if (!issued) return reply({ error: "start again" }, 401);
  await env.PROFILES.delete(key);  // one use only
  const cert = body.cert || {};
  try {
    if (!(await mojangSigned(env, uuid, cert)))
      return reply({ error: "Mojang's signature on your game key didn't check out. Sign in again on the Accounts page." }, 401);
    if (!(await rsaVerify(unbase64(cert.publicKey), "SHA-256", unbase64(body.sig), challengeText(nonce, uuid))))
      return reply({ error: "This request wasn't signed by your account." }, 401);
  } catch {
    return reply({ error: "bad proof" }, 400);
  }
  return uuid;
}

export default {
  async fetch(request, env) {
    const url = new URL(request.url);
    const path = url.pathname.replace(/\/+$/, "");
    if (request.method === "OPTIONS") return reply({});

    // 1. A one-time challenge to sign.
    if (path === "/v1/challenge" && request.method === "POST") {
      const nonce = randomHex(20);
      await env.PROFILES.put(`challenge:${nonce}`, "1", { expirationTtl: 300 });
      return reply({ nonce });
    }

    // 2. Save (or remove) your profile.
    if (path === "/v1/profile" && (request.method === "PUT" || request.method === "DELETE")) {
      const raw = await request.text();
      if (raw.length > MAX_BYTES) return reply({ error: "profile too big" }, 413);
      let body;
      try { body = JSON.parse(raw); } catch { return reply({ error: "bad json" }, 400); }
      const uuid = await proven(env, body);
      if (uuid instanceof Response) return uuid;
      const key = `profile:${uuid}`;
      if (request.method === "DELETE") {
        await env.PROFILES.delete(key);
        return reply({ ok: true, removed: true });
      }
      const profile = body.profile || {};
      const stored = {
        name: NAME.test(body.name || "") ? body.name : "",
        uuid,
        updated: new Date().toISOString(),
        launcher: String(body.launcher || "").slice(0, 20),
        minecraft: String(profile.minecraft || "").slice(0, 20),
        mods: (profile.mods || []).slice(0, 300),
        resourcepacks: (profile.resourcepacks || []).slice(0, 100),
        shaders: (profile.shaders || []).slice(0, 50),
        client_settings: profile.client_settings || null,
      };
      await env.PROFILES.put(key, JSON.stringify(stored));
      return reply({ ok: true, uuid });
    }

    // 3. "I play with Peregrine": other Peregrine players then see the falcon next to your
    //    name and the cape you picked. Only your account id, cape and the day you last played
    //    are kept, for 60 days after you last play.
    if (path === "/v1/presence" && (request.method === "PUT" || request.method === "DELETE")) {
      const body = await request.json().catch(() => ({}));
      const uuid = await proven(env, body);
      if (uuid instanceof Response) return uuid;
      const key = `user:${uuid}`;
      if (request.method === "DELETE") {
        await env.PROFILES.delete(key);
        return reply({ ok: true, removed: true });
      }
      const cape = CAPES.includes(body.cape) ? body.cape : "";
      await env.PROFILES.put(key, JSON.stringify({ cape, seen: new Date().toISOString().slice(0, 10) }),
                             { expirationTtl: PRESENCE_DAYS * 86400 });
      return reply({ ok: true, uuid, cape });
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

    // 5. Look someone up by account id (the launcher turns names into ids with Mojang).
    const m = path.match(/^\/v1\/profile\/([0-9a-fA-F-]{32,36})$/);
    if (m && request.method === "GET") {
      const id = m[1].replace(/-/g, "").toLowerCase();
      const p = UUID.test(id) ? await env.PROFILES.get(`profile:${id}`) : null;
      return p ? new Response(p, { headers: { "content-type": "application/json", "access-control-allow-origin": "*" } })
               : reply({ error: "not shared" }, 404);
    }

    if (path === "" || path === "/v1") return reply({ service: "peregrine-profiles", version: 2, ok: true });
    return reply({ error: "not found" }, 404);
  },
};
