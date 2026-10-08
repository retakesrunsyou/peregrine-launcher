#!/usr/bin/env bash
# Builds Peregrine Client for every Minecraft version in versions/ (or just one:
#   ./build.sh 1.21.1). Needs a Java 21 JDK. The first build downloads Gradle,
# Minecraft and Fabric, which takes a few minutes; later builds are quick.
set -euo pipefail
cd "$(dirname "$0")"

if ! command -v javac >/dev/null; then
  echo "A Java 21 JDK is needed to build. On Mint/Ubuntu run:"
  echo "  sudo apt install openjdk-21-jdk"
  exit 1
fi

mkdir -p dist
versions=("${@:-}")
if [ -z "${versions[0]}" ]; then
  versions=()
  for d in versions/*/; do versions+=("$(basename "$d")"); done
fi

for v in "${versions[@]}"; do
  echo "Building Peregrine Client for Minecraft $v…"
  ./gradlew -p "versions/$v" build --quiet
  jar="$(ls "versions/$v/build/libs/"*.jar | grep -v -- '-sources' | head -1)"
  cp "$jar" "dist/peregrine-client-$v.jar"
  echo "  → dist/peregrine-client-$v.jar"
done

echo
echo "Done. Attach the jars in dist/ to your GitHub release; the launcher installs them automatically."
