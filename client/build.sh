#!/usr/bin/env bash
# Builds Peregrine Client for every Minecraft version listed in the adapters'
# targets.txt files, one jar per version: dist/peregrine-client-<version>.jar
#
#   ./build.sh            every version
#   ./build.sh 1.21.4     just one version
#
# Needs a JDK: Java 21 builds 1.21.x; Java 25 builds everything (26.x needs it).
# The first build of each version downloads Minecraft and Fabric (a few minutes).
set -euo pipefail
cd "$(dirname "$0")"

if ! command -v javac >/dev/null; then
  echo "A Java JDK is needed to build. On Mint/Ubuntu run:"
  echo "  sudo apt install openjdk-21-jdk     (or openjdk-25-jdk for 26.x too)"
  exit 1
fi
# (grep for the "javac NN" line: some systems print other notices first)
java_major="$(javac -version 2>&1 | grep -oE 'javac [0-9]+' | grep -oE '[0-9]+' | head -1)"
java_major="${java_major:-0}"

only="${1:-}"
mkdir -p dist
built=() skipped=() failed=()

for adapter in versions/*/; do
  adapter="${adapter%/}"
  [ -f "$adapter/targets.txt" ] || continue
  while read -r mc api; do
    case "$mc" in ''|'#'*) continue ;; esac
    [ -n "$only" ] && [ "$mc" != "$only" ] && continue

    needs=21
    case "$mc" in 2[6-9].*|[3-9][0-9].*) needs=25 ;; esac
    if [ "$java_major" -lt "$needs" ]; then
      skipped+=("$mc (needs Java $needs)")
      continue
    fi

    echo "Building for Minecraft $mc…"
    log="dist/build-$mc.log"
    if ./gradlew -p "$adapter" clean build --quiet \
         -Pminecraft_version="$mc" -Pfabric_api_version="$api" > "$log" 2>&1; then
      jar="$(ls "$adapter/build/libs/"*.jar | grep -v -- '-sources' | head -1)"
      cp "$jar" "dist/peregrine-client-$mc.jar"
      built+=("$mc")
      rm -f "$log"
    else
      failed+=("$mc")
      cat "$log"
      # On GitHub, also post the errors as annotations so they're easy to find.
      if [ -n "${GITHUB_ACTIONS:-}" ]; then
        { grep -E "error:|e: |What went wrong|Could not|Exception" "$log" || true; } | head -40 |
          while IFS= read -r line; do echo "::error title=Minecraft $mc::${line//$'\r'/}"; done
      fi
    fi
  done < "$adapter/targets.txt"
done

echo
[ ${#built[@]} -gt 0 ] && echo "Built:   ${built[*]}"
[ ${#skipped[@]} -gt 0 ] && echo "Skipped: ${skipped[*]}"
if [ ${#failed[@]} -gt 0 ]; then
  echo "FAILED:  ${failed[*]}  (scroll up for the error)"
  exit 1
fi
echo "Jars are in dist/. The launcher installs the one matching each Fabric instance."
