#!/usr/bin/env bash
# Builds peregrine-launcher.zip for a GitHub release.
# 1. Bump VERSION in peregrine/__init__.py
# 2. Run ./make-release.sh
# 3. On GitHub: Releases → Draft a new release, tag it v<VERSION>,
#    attach peregrine-launcher.zip, publish. Installed launchers pick it up.
set -euo pipefail
cd "$(dirname "$0")"
VERSION="$(sed -n 's/^VERSION = "\(.*\)"/\1/p' peregrine/__init__.py)"
OUT="$(pwd)/../peregrine-launcher.zip"
STAGE="$(mktemp -d)"
mkdir "$STAGE/peregrine"
cp -r peregrine .gitignore install.sh make-release.sh requirements.txt README.md DEVELOPING.md peregrine.svg "$STAGE/peregrine/"
find "$STAGE" -name __pycache__ -prune -exec rm -rf {} +
rm -f "$OUT"
(cd "$STAGE" && zip -qr "$OUT" peregrine)
rm -rf "$STAGE"
echo "Built $OUT for v$VERSION. Tag the GitHub release v$VERSION and attach it."
