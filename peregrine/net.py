"""HTTP helpers: JSON fetching and verified, parallel file downloads."""

import hashlib
import os
from concurrent.futures import ThreadPoolExecutor, as_completed
from dataclasses import dataclass
from pathlib import Path
from typing import Callable, Optional

import requests

from . import USER_AGENT

session = requests.Session()
session.headers["User-Agent"] = USER_AGENT

Progress = Callable[[int, int, str], None]  # (done, total, message)


def get_json(url: str, **kwargs):
    r = session.get(url, timeout=30, **kwargs)
    r.raise_for_status()
    return r.json()


def get_json_cached(url: str, cache: Path, max_age: float, **kwargs):
    """Fetch JSON, reusing a saved copy for max_age seconds.

    If the network fails, an older saved copy is used instead, so the launcher
    keeps working offline."""
    import json
    import time
    if cache.is_file() and time.time() - cache.stat().st_mtime < max_age:
        try:
            return json.loads(cache.read_text())
        except ValueError:
            pass
    try:
        data = get_json(url, **kwargs)
    except Exception:
        if cache.is_file():
            return json.loads(cache.read_text())
        raise
    cache.parent.mkdir(parents=True, exist_ok=True)
    tmp = cache.with_suffix(".tmp")
    tmp.write_text(json.dumps(data))
    os.replace(tmp, cache)
    return data


def sha1_of(path: Path) -> str:
    h = hashlib.sha1()
    with open(path, "rb") as f:
        for chunk in iter(lambda: f.read(1 << 16), b""):
            h.update(chunk)
    return h.hexdigest()


@dataclass
class Download:
    url: str
    path: Path
    sha1: Optional[str] = None
    executable: bool = False
    size: Optional[int] = None

    def is_done(self) -> bool:
        if not self.path.is_file():
            return False
        # Files are hash-checked when downloaded, so a matching size is enough
        # here. This keeps launches fast instead of re-hashing thousands of files.
        if self.size is not None:
            return self.path.stat().st_size == self.size
        return self.sha1 is None or sha1_of(self.path) == self.sha1


def fetch(d: Download, retries: int = 3) -> None:
    d.path.parent.mkdir(parents=True, exist_ok=True)
    tmp = d.path.with_name(d.path.name + ".part")
    last_err = None
    for _ in range(retries):
        try:
            with session.get(d.url, stream=True, timeout=60) as r:
                r.raise_for_status()
                with open(tmp, "wb") as f:
                    for chunk in r.iter_content(1 << 16):
                        f.write(chunk)
            if d.sha1 and sha1_of(tmp) != d.sha1:
                raise IOError(f"checksum mismatch for {d.url}")
            os.replace(tmp, d.path)
            if d.executable:
                d.path.chmod(0o755)
            return
        except Exception as e:  # retry on any network/IO hiccup
            last_err = e
    tmp.unlink(missing_ok=True)
    raise RuntimeError(f"Failed to download {d.url}: {last_err}")


def fetch_all(downloads: list, label: str, progress: Optional[Progress] = None,
              workers: int = 16) -> None:
    # Drop duplicates and files that are already present and valid.
    unique = {}
    for d in downloads:
        unique.setdefault(d.path, d)
    todo = [d for d in unique.values() if not d.is_done()]
    total = len(todo)
    if progress:
        progress(0, total, label)
    if not todo:
        return
    done = 0
    with ThreadPoolExecutor(workers) as pool:
        futures = [pool.submit(fetch, d) for d in todo]
        for fut in as_completed(futures):
            fut.result()
            done += 1
            if progress:
                progress(done, total, label)
