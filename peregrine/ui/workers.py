"""Run slow work (downloads, logins, the game itself) off the UI thread."""

import subprocess

from PySide6.QtCore import QThread, Signal

_running = set()  # keep threads alive until they finish


class Task(QThread):
    """Run fn(*args) in the background. Pass progress=True to get progress updates."""
    done = Signal(object)
    failed = Signal(str)
    progress = Signal(int, int, str)
    log = Signal(str)

    def __init__(self, fn, *args, with_progress=False):
        super().__init__()
        self.fn, self.args, self.with_progress = fn, args, with_progress
        _running.add(self)
        self.finished.connect(lambda: _running.discard(self))

    def run(self):
        try:
            kwargs = {"progress": lambda d, t, m: self.progress.emit(d, t, m)} if self.with_progress else {}
            result = self.fn(*self.args, **kwargs)
            if isinstance(result, subprocess.Popen):  # the game: stream its output
                for line in result.stdout:
                    self.log.emit(line.rstrip())
                result = result.wait()
            self.done.emit(result)
        except Exception as e:  # shown to the user
            self.failed.emit(str(e))


def run(fn, *args, done=None, failed=None):
    """Shortcut: start a background task with callbacks."""
    t = Task(fn, *args)
    if done:
        t.done.connect(done)
    if failed:
        t.failed.connect(failed)
    t.start()
    return t
