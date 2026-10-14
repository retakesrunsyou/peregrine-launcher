"""Peregrine-Setup.exe: the Windows installer.

Built by the "Build apps" workflow as one file, with the launcher (dist/Peregrine, zipped)
inside it as payload.zip. Understands the switches the old installer did, so updates from
older launchers keep working:

    /SILENT        show progress only, then start Peregrine (with /RESTARTAPPLICATIONS)
    /VERYSILENT    no window at all
    /TASKS=desktopicon,startup    which shortcuts to make
    /DIR=folder    where to install
    /SHOTS=folder  save a picture of every step and quit (build checks)
"""
import sys
from pathlib import Path


def _main():
    if sys.stdout is None or sys.stderr is None:
        import os
        out = open(os.devnull, "w")
        sys.stdout = sys.stdout or out
        sys.stderr = sys.stderr or out
    from peregrine import VERSION
    from peregrine.ui import setup_ui
    args = sys.argv[1:]
    shots = next((a.split("=", 1)[1] for a in args if a.upper().startswith("/SHOTS=")), None)
    if shots:
        setup_ui.screenshots(Path(shots), VERSION)
        return 0
    payload = Path(getattr(sys, "_MEIPASS", Path(__file__).parent)) / "payload.zip"
    if not payload.is_file():
        print("payload.zip is missing from the installer", file=sys.stderr)
        return 1
    return setup_ui.run_installer(VERSION, payload, args)


if __name__ == "__main__":
    sys.exit(_main())
