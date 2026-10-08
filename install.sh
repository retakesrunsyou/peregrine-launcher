#!/usr/bin/env bash
# Installs or updates Peregrine for the current user: a private Python
# environment, a `peregrine` command, and an app menu entry.
# Run it again any time; your instances, worlds and settings are kept.
set -euo pipefail

SRC="$(cd "$(dirname "$0")" && pwd)"
DATA="${XDG_DATA_HOME:-$HOME/.local/share}"
APP="$DATA/peregrine/app"
BIN="$HOME/.local/bin"
APPS="$DATA/applications"
ICONS="$DATA/icons/hicolor/scalable/apps"

fail() {
  echo
  echo "Install failed: $1"
  exit 1
}
trap 'fail "the step on line $LINENO did not finish. Copy the messages above if you need help."' ERR

version_of() {
  sed -n 's/^VERSION = "\(.*\)"/\1/p' "$1/peregrine/__init__.py" 2>/dev/null || true
}
NEW="$(version_of "$SRC")"
OLD="$(version_of "$APP")"

# ---- check system packages (Ubuntu / Mint / Debian names)
command -v python3 >/dev/null || fail "Python 3 isn't installed. Run: sudo apt install python3"

missing=()
if ! python3 -c "import ensurepip, venv" 2>/dev/null; then
  missing+=("python3-venv")
fi
LDCONFIG="$(command -v ldconfig || echo /sbin/ldconfig)"
if [ -x "$LDCONFIG" ]; then
  libs="$("$LDCONFIG" -p 2>/dev/null || true)"
  if [ -n "$libs" ] && ! grep -q "libxcb-cursor.so.0" <<<"$libs"; then
    missing+=("libxcb-cursor0")
  fi
fi
if [ ${#missing[@]} -gt 0 ]; then
  echo "Peregrine needs these system packages first. Run:"
  echo
  echo "  sudo apt install ${missing[*]}"
  echo
  echo "Then run this installer again."
  exit 1
fi

# ---- copy the app
if [ -n "$OLD" ]; then
  echo "Updating Peregrine $OLD → $NEW"
else
  echo "Installing Peregrine $NEW"
fi
mkdir -p "$APP" "$BIN" "$APPS" "$ICONS"
rm -rf "$APP/peregrine.new"
cp -r "$SRC/peregrine" "$APP/peregrine.new"
rm -rf "$APP/peregrine"
mv "$APP/peregrine.new" "$APP/peregrine"
cp "$SRC/requirements.txt" "$APP/"

# ---- Python environment (rebuilt if an earlier attempt left it broken)
if [ ! -x "$APP/venv/bin/pip" ] || ! "$APP/venv/bin/python" -c "pass" 2>/dev/null; then
  echo "Setting up Python environment…"
  rm -rf "$APP/venv"
  python3 -m venv "$APP/venv"
fi
echo "Installing dependencies (first time takes a minute)…"
"$APP/venv/bin/python" -m pip install --quiet --upgrade pip
"$APP/venv/bin/python" -m pip install --quiet --upgrade -r "$APP/requirements.txt"
"$APP/venv/bin/python" -c "import PySide6.QtWidgets, PySide6.QtSvg, requests" \
  || fail "the Qt libraries didn't load. Try: sudo apt install libxcb-cursor0 libegl1"

# ---- launcher command + menu entry
cat > "$BIN/peregrine" <<EOF
#!/usr/bin/env bash
cd "$APP" && exec "$APP/venv/bin/python" -m peregrine "\$@"
EOF
chmod +x "$BIN/peregrine"

# App icon: the SVG plus PNG sizes, since some docks and taskbars only use PNGs.
cp "$SRC/peregrine.svg" "$ICONS/peregrine.svg"
QT_QPA_PLATFORM=offscreen "$APP/venv/bin/python" - "$DATA/icons/hicolor" "$SRC/peregrine.svg" <<'PY' || true
import sys
from pathlib import Path
from PySide6.QtCore import Qt
from PySide6.QtGui import QGuiApplication, QImage, QPainter
from PySide6.QtSvg import QSvgRenderer
app = QGuiApplication(sys.argv[:1])
root, svg = Path(sys.argv[1]), QSvgRenderer(sys.argv[2])
for size in (16, 24, 32, 48, 64, 128, 256, 512):
    img = QImage(size, size, QImage.Format_ARGB32)
    img.fill(Qt.transparent)
    p = QPainter(img)
    p.setRenderHint(QPainter.Antialiasing)
    svg.render(p)
    p.end()
    out = root / f"{size}x{size}" / "apps"
    out.mkdir(parents=True, exist_ok=True)
    img.save(str(out / "peregrine.png"))
PY

cat > "$APPS/peregrine.desktop" <<EOF
[Desktop Entry]
Type=Application
Name=Peregrine Launcher
GenericName=Minecraft Launcher
Comment=Launch and speed up Minecraft: Java Edition
Exec=$BIN/peregrine
Icon=peregrine
StartupWMClass=peregrine
StartupNotify=true
Categories=Game;
Keywords=minecraft;launcher;mods;fabric;
Terminal=false
EOF
command -v update-desktop-database >/dev/null && update-desktop-database "$APPS" 2>/dev/null || true
command -v gtk-update-icon-cache >/dev/null && gtk-update-icon-cache -q -t "$DATA/icons/hicolor" 2>/dev/null || true

trap - ERR
echo
echo "Done! Open 'Peregrine Launcher' from your app menu, or run: peregrine"
case ":$PATH:" in
  *":$BIN:"*) ;;
  *) echo "(If 'peregrine' says command not found, log out and back in once.)" ;;
esac
