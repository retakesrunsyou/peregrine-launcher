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
const CAPES = ["", "falcon", "violet", "ember", "starry", "pumpkin", "frost", "custom"];
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
  if (body.serverId && !body.nonce)
    return reply({ error: "Update Peregrine to the newest version to share and show your badge." }, 426);
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

// ------------------------------------------------------------------ players' own capes
//
// A player's own cape (a picture, GIF or video made in the Locker) can be shared: the game sends
// a small copy (a still up to 512 px wide, or up to 12 frames up to 256 px wide) as one block of
// PNGs. Before it's kept, every picture goes through a safety check (Cloudflare Workers AI looks
// at it for nudity, sexual content, gore and hate symbols); anything flagged, or anything that
// can't be checked, isn't shared. Players can also report a cape: three different players
// reporting it hides it from everyone until its owner makes a new one.

const MAX_CAPE_BYTES = 2 * 1024 * 1024;
const MAX_CAPE_FRAMES = 12;
const CAPE_UPLOADS_A_DAY = 5;
const CAPE_REPORTS_TO_HIDE = 3;
const CHECK_PROMPT =
  "You check pictures that players put on their Minecraft capes, which everyone (including children) sees. " +
  "Does this picture show nudity, exposed private parts, sexual or pornographic content, sexually suggestive poses, " +
  "gore, or hate symbols? Answer with exactly one word: SAFE or UNSAFE.";

/** The block the game sends: "PCAP", frame count, then each frame's length and its PNG. */
function readCapeBlock(bytes) {
  const v = new DataView(bytes.buffer, bytes.byteOffset, bytes.byteLength);
  if (bytes.length < 12 || String.fromCharCode(...bytes.subarray(0, 4)) !== "PCAP") return null;
  const n = v.getUint32(4);
  if (n < 1 || n > MAX_CAPE_FRAMES) return null;
  const frames = [], ms = [];
  let at = 8;
  for (let i = 0; i < n; i++) {
    if (at + 8 > bytes.length) return null;
    const len = v.getUint32(at), dur = v.getUint32(at + 4);
    at += 8;
    if (len < 33 || at + len > bytes.length) return null;
    const png = bytes.subarray(at, at + len);
    at += len;
    frames.push(png);
    ms.push(Math.max(20, Math.min(10000, dur)));
  }
  return at === bytes.length ? { frames, ms } : null;
}

/** A PNG's size from its header, or null if it isn't a PNG. */
function pngSize(png) {
  const sig = [137, 80, 78, 71, 13, 10, 26, 10];
  for (let i = 0; i < 8; i++) if (png[i] !== sig[i]) return null;
  const v = new DataView(png.buffer, png.byteOffset, png.byteLength);
  return { w: v.getUint32(16), h: v.getUint32(20) };
}

function base64(bytes) {
  let s = "";
  for (let i = 0; i < bytes.length; i += 0x8000) s += String.fromCharCode.apply(null, bytes.subarray(i, i + 0x8000));
  return btoa(s);
}

// Vision models tried in turn: if one isn't available or fails, the next one looks instead.
const CHECK_MODELS = [
  { id: "@cf/meta/llama-4-scout-17b-16e-instruct",
    input: (png) => ({ messages: [{ role: "user", content: [
      { type: "text", text: CHECK_PROMPT },
      { type: "image_url", image_url: { url: "data:image/png;base64," + base64(png) } },
    ] }], max_tokens: 8, temperature: 0 }) },
  { id: "@cf/meta/llama-3.2-11b-vision-instruct", agree: true,
    input: (png) => ({ messages: [{ role: "user", content: CHECK_PROMPT }], image: [...png], max_tokens: 8, temperature: 0 }) },
  { id: "@cf/llava-hf/llava-1.5-7b-hf",
    input: (png) => ({ image: [...png], prompt: CHECK_PROMPT, max_tokens: 8 }) },
];

/** A small plain picture (64 x 32, one colour) for the self-test. */
const TEST_PNG = Uint8Array.from(atob(
  "iVBORw0KGgoAAAANSUhEUgAAAEAAAAAgCAIAAAAt/+nTAAAAN0lEQVR42u3PQQkAAAgEsMtpJiMaxgw+hcEKLNXzWgQEBAQEBAQEBAQEBAQEBAQEBAQEBAQErhb42hDTvL4T4wAAAABJRU5ErkJggg=="),
  (c) => c.charCodeAt(0));

function verdictOf(r) {
  let text = r && (r.response ?? r.description ?? r.result?.response ?? r.result?.description);
  if (text && typeof text !== "string") text = JSON.stringify(text);
  text = String(text || "").toUpperCase();
  if (text.includes("UNSAFE")) return "unsafe";
  if (text.includes("SAFE")) return "safe";
  return null;
}

/** {verdict: "safe" | "unsafe" | null (couldn't check: nothing is shared), model, errors}. */
async function checkPicture(env, png) {
  const errors = [];
  if (!env.AI) return { verdict: null, model: null, errors: ["no AI binding"] };
  for (const m of CHECK_MODELS) {
    for (let attempt = 0; attempt < 2; attempt++) {
      try {
        const verdict = verdictOf(await env.AI.run(m.id, m.input(png)));
        if (verdict) return { verdict, model: m.id, errors };
        errors.push(m.id + ": no clear answer");
        break;
      } catch (e) {
        const msg = String((e && e.message) || e);
        // Meta's vision model asks once for its licence to be accepted
        if (m.agree && attempt === 0 && /agree|licen[cs]e/i.test(msg)) {
          try { await env.AI.run(m.id, { prompt: "agree" }); } catch {}
          continue;
        }
        errors.push(m.id + ": " + msg.slice(0, 200));
        break;
      }
    }
  }
  console.log("cape check failed: " + errors.join(" | "));
  return { verdict: null, model: null, errors };
}

/** Is the safety check working? (a plain test picture; the answer is kept for 10 minutes) */
async function capeSelfTest(env) {
  const cached = await env.PROFILES.get("capeselftest");
  if (cached) return new Response(cached, { headers: { "content-type": "application/json" } });
  const r = await checkPicture(env, TEST_PNG);
  const out = JSON.stringify({ ok: r.verdict === "safe", verdict: r.verdict, model: r.model, errors: r.errors,
                               checked: new Date().toISOString() });
  await env.PROFILES.put("capeselftest", out, { expirationTtl: 600 });
  return new Response(out, { headers: { "content-type": "application/json" } });
}

async function hashHex(bytes) {
  const d = new Uint8Array(await crypto.subtle.digest("SHA-256", bytes));
  return [...d.subarray(0, 12)].map((b) => b.toString(16).padStart(2, "0")).join("");
}

async function capeUpload(request, env) {
  let proof;
  try { proof = JSON.parse(atob(request.headers.get("x-peregrine-proof") || "")); } catch { return reply({ error: "bad proof" }, 400); }
  const uuid = await proven(env, proof);
  if (uuid instanceof Response) return uuid;
  const day = new Date().toISOString().slice(0, 10);
  const countKey = `capeup:${uuid}:${day}`;
  const count = parseInt((await env.PROFILES.get(countKey)) || "0", 10);
  if (count >= CAPE_UPLOADS_A_DAY) return reply({ error: "That's enough new capes for today. Try again tomorrow." }, 429);
  const bytes = new Uint8Array(await request.arrayBuffer());
  if (bytes.length > MAX_CAPE_BYTES) return reply({ error: "cape too big" }, 413);
  const block = readCapeBlock(bytes);
  if (!block) return reply({ error: "bad cape" }, 400);
  for (const png of block.frames) {
    const s = pngSize(png);
    const maxW = block.frames.length > 1 ? 256 : 512;
    if (!s || s.w !== s.h * 2 || s.w < 64 || s.w > maxW) return reply({ error: "bad cape size" }, 400);
  }
  for (const png of block.frames) {
    const { verdict } = await checkPicture(env, png);
    if (verdict !== "safe" && verdict !== "unsafe")   // couldn't check: not counted, try again later
      return reply({ error: "The safety check isn't working right now, so the cape wasn't shared. Try again later." }, 503);
    if (verdict === "unsafe") {
      await env.PROFILES.put(countKey, String(count + 1), { expirationTtl: 2 * 86400 });
      return reply({ error: "This cape can't be shared: the safety check found nudity, sexual content, gore or a hate symbol in it.", flagged: true }, 422);
    }
  }
  await env.PROFILES.put(countKey, String(count + 1), { expirationTtl: 2 * 86400 });
  const hash = await hashHex(bytes);
  await env.PROFILES.put(`capeblob:${hash}`, bytes, { expirationTtl: PRESENCE_DAYS * 86400 });
  await env.PROFILES.put(`cape:${uuid}`, JSON.stringify({ hash, n: block.frames.length, ms: block.ms }),
                         { expirationTtl: PRESENCE_DAYS * 86400 });
  return reply({ ok: true, hash });
}

async function capeReport(request, env) {
  const body = await request.json().catch(() => ({}));
  const reporter = await proven(env, body);
  if (reporter instanceof Response) return reporter;
  const target = String(body.target || "").replace(/-/g, "").toLowerCase();
  if (!UUID.test(target) || target === reporter) return reply({ error: "bad player" }, 400);
  const raw = await env.PROFILES.get(`cape:${target}`);
  if (!raw) return reply({ ok: true, hidden: false });
  const cape = JSON.parse(raw);
  const key = `capereports:${cape.hash}`;
  const who = JSON.parse((await env.PROFILES.get(key)) || "[]");
  if (!who.includes(reporter)) who.push(reporter);
  await env.PROFILES.put(key, JSON.stringify(who.slice(0, 50)), { expirationTtl: PRESENCE_DAYS * 86400 });
  const hidden = who.length >= CAPE_REPORTS_TO_HIDE;
  if (hidden && !cape.hidden) {
    cape.hidden = true;
    await env.PROFILES.put(`cape:${target}`, JSON.stringify(cape), { expirationTtl: PRESENCE_DAYS * 86400 });
  }
  return reply({ ok: true, hidden });
}

// ------------------------------------------------------------------ voice parties
//
// A party is a small voice room (up to 8 players) named by a 6-letter code. Each player joins
// with a WebSocket, proves who they are the same way as for profiles (their Minecraft chat key),
// and then their voice is passed on to everyone else in the room. Nothing is stored or recorded.

const PARTY = /^[A-Z0-9]{6}$/;
const MAX_PARTY = 8;

export class VoiceRoom {
  constructor(state, env) {
    this.state = state;
    this.env = env;
  }

  /** Who sent this hello: their account id, or a Response saying why not. */
  verify(body) {
    return proven(this.env, body);
  }

  members() {
    const out = [];
    for (const ws of this.state.getWebSockets()) {
      const m = ws.deserializeAttachment();
      if (m && m.uuid) out.push(m);
    }
    return out;
  }

  broadcast(msg, except) {
    for (const ws of this.state.getWebSockets()) {
      const m = ws.deserializeAttachment();
      if (ws === except || !m || !m.uuid) continue;
      try { ws.send(msg); } catch {}
    }
  }

  sendMembers() {
    const list = this.members().map((m) => ({ id: m.id, uuid: m.uuid, name: m.name, muted: !!m.muted }));
    this.broadcast(JSON.stringify({ type: "members", members: list }));
  }

  async fetch(request) {
    if (request.headers.get("Upgrade") !== "websocket") return reply({ error: "a WebSocket, please" }, 426);
    if (this.state.getWebSockets().length >= MAX_PARTY + 2) return reply({ error: "This party is full" }, 409);
    const pair = new WebSocketPair();
    this.state.acceptWebSocket(pair[1]);
    pair[1].serializeAttachment({});
    return new Response(null, { status: 101, webSocket: pair[0] });
  }

  async webSocketMessage(ws, msg) {
    const me = ws.deserializeAttachment() || {};
    if (typeof msg !== "string") {
      // voice: [1 byte kind][data...] -> everyone else gets [kind][sender id][data...]
      if (!me.uuid) return;
      const data = new Uint8Array(msg);
      if (data.length < 2 || data.length > 4096) return;
      const out = new Uint8Array(data.length + 1);
      out[0] = data[0];
      out[1] = me.id;
      out.set(data.subarray(1), 2);
      this.broadcast(out.buffer, ws);
      return;
    }
    let body;
    try { body = JSON.parse(msg); } catch { return; }
    if (body.type === "hello" && !me.uuid) {
      const who = await this.verify(body);
      if (who instanceof Response) {
        const why = await who.json().catch(() => ({}));
        ws.send(JSON.stringify({ type: "denied", error: why.error || "not allowed" }));
        ws.close(4001, "not allowed");
        return;
      }
      const others = this.members();
      if (others.length >= MAX_PARTY) {
        ws.send(JSON.stringify({ type: "denied", error: "This party is full (8 players)" }));
        ws.close(4002, "full");
        return;
      }
      for (const ws2 of this.state.getWebSockets()) {  // the same player joining again: drop the old one
        const m = ws2.deserializeAttachment();
        if (ws2 !== ws && m && m.uuid === who) { try { ws2.close(4003, "joined again"); } catch {} }
      }
      const used = new Set(others.filter((m) => m.uuid !== who).map((m) => m.id));
      let id = 1;
      while (used.has(id)) id++;
      const name = NAME.test(body.name || "") ? body.name : "Player";
      ws.serializeAttachment({ id, uuid: who, name, muted: false });
      ws.send(JSON.stringify({ type: "welcome", id }));
      this.sendMembers();
      return;
    }
    if (body.type === "muted" && me.uuid) {
      me.muted = !!body.muted;
      ws.serializeAttachment(me);
      this.sendMembers();
    }
  }

  async webSocketClose(ws) {
    ws.serializeAttachment({});
    this.sendMembers();
  }

  async webSocketError(ws) {
    ws.serializeAttachment({});
    this.sendMembers();
  }
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
      // serverId only so launchers before 1.4.0 get as far as the "please update" message
      return reply({ nonce, serverId: randomHex(20) });
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
      const ownWanted = [];
      ids.forEach((u, i) => {
        if (!found[i]) return;
        try { players[u] = { cape: JSON.parse(found[i]).cape || "" }; } catch { players[u] = { cape: "" }; }
        if (players[u].cape === "custom") ownWanted.push(u);
      });
      const own = await Promise.all(ownWanted.map((u) => env.PROFILES.get(`cape:${u}`)));
      ownWanted.forEach((u, i) => {
        try {
          const c = own[i] ? JSON.parse(own[i]) : null;
          if (c && !c.hidden) players[u].own = { hash: c.hash, n: c.n, ms: c.ms };
          else players[u].cape = "";
        } catch { players[u].cape = ""; }
      });
      return reply({ players });
    }

    // 4b. Players' own capes: share yours (checked first), fetch someone's, report one.
    if (path === "/v1/cape" && request.method === "PUT") return capeUpload(request, env);
    if (path === "/v1/cape" && request.method === "DELETE") {
      const body = await request.json().catch(() => ({}));
      const uuid = await proven(env, body);
      if (uuid instanceof Response) return uuid;
      await env.PROFILES.delete(`cape:${uuid}`);
      return reply({ ok: true, removed: true });
    }
    if (path === "/v1/cape/report" && request.method === "POST") return capeReport(request, env);
    if (path === "/v1/cape/selftest" && request.method === "GET") return capeSelfTest(env);
    const blob = path.match(/^\/v1\/cape\/([0-9a-f]{24})$/);
    if (blob && request.method === "GET") {
      const bytes = await env.PROFILES.get(`capeblob:${blob[1]}`, { type: "arrayBuffer" });
      if (!bytes) return reply({ error: "not found" }, 404);
      return new Response(bytes, { headers: { "content-type": "application/octet-stream",
        "cache-control": "public, max-age=31536000, immutable", "access-control-allow-origin": "*" } });
    }

    // 5. Look someone up by account id (the launcher turns names into ids with Mojang).
    const m = path.match(/^\/v1\/profile\/([0-9a-fA-F-]{32,36})$/);
    if (m && request.method === "GET") {
      const id = m[1].replace(/-/g, "").toLowerCase();
      const p = UUID.test(id) ? await env.PROFILES.get(`profile:${id}`) : null;
      return p ? new Response(p, { headers: { "content-type": "application/json", "access-control-allow-origin": "*" } })
               : reply({ error: "not shared" }, 404);
    }

    // 6. Voice parties: a WebSocket to the party's room.
    const party = path.match(/^\/v1\/voice\/([A-Za-z0-9]{6})$/);
    if (party && request.method === "GET") {
      if (!env.VOICE) return reply({ error: "Voice isn't set up on this service yet" }, 501);
      const code = party[1].toUpperCase();
      return env.VOICE.get(env.VOICE.idFromName(code)).fetch(request);
    }

    if (path === "" || path === "/v1") return reply({ service: "peregrine-profiles", version: 5, voice: !!env.VOICE, capes: !!env.AI, ok: true });
    return reply({ error: "not found" }, 404);
  },
};
