#!/usr/bin/env bash
# One-time setup of the free profile service on Cloudflare (no card needed).
# Needs Node.js (for npx). Run:  bash profiles/setup.sh
set -euo pipefail
cd "$(dirname "$0")"
echo "1/3  Sign in to Cloudflare (a browser window opens; make a free account if you need one)"
npx --yes wrangler@3 login
if grep -q PUT_YOUR_KV_ID_HERE wrangler.toml; then
  echo "2/3  Making the storage for profiles"
  out="$(npx --yes wrangler@3 kv namespace create PROFILES)"
  echo "$out"
  id="$(echo "$out" | grep -oE '[0-9a-f]{32}' | head -1)"
  [ -n "$id" ] || { echo "Couldn't read the storage id above. Paste it into profiles/wrangler.toml and run this again."; exit 1; }
  sed -i.bak "s/PUT_YOUR_KV_ID_HERE/$id/" wrangler.toml && rm -f wrangler.toml.bak
fi
echo "3/3  Putting the service online"
echo "     (the first time, Cloudflare asks you to pick a workers.dev name: type any name you like)"
npx --yes wrangler@3 deploy
echo
echo "Done! Your service's address is the https://...workers.dev line just above."
echo "Put it in peregrine/__init__.py as PROFILES_URL (or send it to whoever builds Peregrine)."
echo "After changing profiles/worker.js later, update the service with:  cd profiles && npx --yes wrangler@3 deploy"
