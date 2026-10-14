"""Entry point for the packaged app (Windows .exe and Linux AppImage)."""
import sys


def _main():
    from peregrine import paths
    if sys.stdout is None or sys.stderr is None:
        # A windowed .exe has no console: keep what it prints in the logs folder instead.
        try:
            paths.LOGS.mkdir(parents=True, exist_ok=True)
            out = open(paths.LOGS / "launcher-output.log", "w", encoding="utf-8", errors="replace", buffering=1)
        except OSError:
            import os
            out = open(os.devnull, "w")
        sys.stdout = sys.stdout or out
        sys.stderr = sys.stderr or out
        sys.__stdout__ = sys.__stdout__ or out
        sys.__stderr__ = sys.__stderr__ or out
    try:
        from peregrine.ui.window import main
        main()
    except SystemExit:
        raise
    except BaseException:
        import traceback
        text = traceback.format_exc()
        try:
            paths.LOGS.mkdir(parents=True, exist_ok=True)
            with open(paths.LOGS / "launcher.log", "a", encoding="utf-8") as f:
                f.write("\n--- start-up\n" + text)
        except OSError:
            pass
        sys.stderr.write(text)
        sys.exit(1)


if __name__ == "__main__":
    _main()
