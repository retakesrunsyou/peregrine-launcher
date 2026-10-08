#!/usr/bin/env bash
# Test builds only: when a Minecraft version fails to build, save the Minecraft
# and Fabric API signatures it was compiled against (plus the build log) to the
# "api-dump" branch, so the adapter can be fixed without guessing.
#   .github/api-dump.sh 26.3 [more versions…]
set -uo pipefail
out="$(mktemp -d)"

fabric_cp="$(find ~/.gradle/caches/modules-2/files-2.1 -path '*net.fabricmc*' -name '*.jar' ! -name '*sources*' 2>/dev/null | tr '\n' ':')"

for mc in "$@"; do
  d="$out/$mc"; mkdir -p "$d"
  cp "client/dist/build-$mc.log" "$d/build.log" 2>/dev/null
  jar="$(find ~/.gradle/caches/fabric-loom -path "*/$mc/*" -name 'minecraft-merged*.jar' 2>/dev/null | head -1)"
  [ -n "$jar" ] || jar="$(find ~/.gradle/caches/fabric-loom -path "*$mc*" -name '*.jar' -size +5M 2>/dev/null | head -1)"
  echo "$jar" > "$d/jar.txt"
  [ -n "$jar" ] || continue
  unzip -Z1 "$jar" | grep '\.class$' | sed 's/\.class$//' | sort > "$d/classes.txt"
  grep -E '^(net/minecraft/client/[^/]+|net/minecraft/client/gui/[^/]+|net/minecraft/client/gui/screens/[^/]+|net/minecraft/client/gui/components/(events|debug)/[^/]+|net/minecraft/client/gui/screens/options/OptionsScreen|net/minecraft/client/input/[^/]+|com/mojang/blaze3d/platform/[^/]+|net/minecraft/client/renderer/GameRenderer|net/minecraft/world/level/Level|net/minecraft/client/multiplayer/(ClientLevel|PlayerInfo|ServerData|ClientPacketListener))$' "$d/classes.txt" |
    tr '/' '.' > "$d/dumped.txt"
  javap -p -cp "$jar" $(cat "$d/dumped.txt") > "$d/minecraft-api.txt" 2>&1
  find ~/.gradle/caches/modules-2/files-2.1 -path '*net.fabricmc*' -name '*.jar' ! -name '*sources*' 2>/dev/null |
    xargs -rn1 unzip -Z1 2>/dev/null | grep -E '^net/fabricmc/fabric/api/client/.*\.class$' | sed 's/\.class$//' | sort -u > "$d/fabric-classes.txt"
  grep -E '/(HudElementRegistry|HudElement|VanillaHudElements|KeyMappingHelper|KeyBindingHelper|ClientTickEvents|ClientLifecycleEvents|ScreenEvents)$' "$d/fabric-classes.txt" | tr '/' '.' |
    xargs -r javap -p -cp "$jar:$fabric_cp" > "$d/fabric-api.txt" 2>&1
done

cd "$out"
git init -q -b api-dump
git -c user.name=Peregrine -c user.email=noreply@users.noreply.github.com add -A
git -c user.name=Peregrine -c user.email=noreply@users.noreply.github.com commit -qm "API dump for $*"
git push -qf "https://x-access-token:${GH_TOKEN}@github.com/${GITHUB_REPOSITORY}.git" api-dump
echo "Saved to the api-dump branch."
