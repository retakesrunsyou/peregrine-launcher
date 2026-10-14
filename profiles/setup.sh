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
npx --yes wrangler@3 deploy | tee /tmp/peregrine-deploy.txt
url="$(grep -oE 'https://[a-z0-9.-]+\.workers\.dev' /tmp/peregrine-deploy.txt | head -1)"
echo
echo "Done! Your profile service is at: ${url:-(see the address above)}"
echo "Send that address to whoever builds Peregrine, or put it in peregrine/__init__.py as PROFILES_URL."
