"""Installing and uninstalling the Windows app: files, shortcuts and the Apps & features entry.

Used by Peregrine's own installer (packaging/setup_app.py) and by the launcher's
"--uninstall" (what Windows' Apps & features runs). Everything goes in the user's own
folders, so no administrator rights are needed:

- the program:  %LOCALAPPDATA%\\Programs\\Peregrine
- shortcuts:    Start menu, optionally the desktop and Windows start-up
- Apps & features: HKCU\\...\\Uninstall\\Peregrine

Games, worlds and settings live elsewhere (%LOCALAPPDATA%\\Peregrine, %APPDATA%\\Peregrine)
and are only removed when the player asks for that while uninstalling.
"""

import os
import shutil
import subprocess
import sys
import time
import zipfile
from pathlib import Path

APP_NAME = "Peregrine Launcher"
EXE = "Peregrine.exe"
AUMID = "Peregrine.Launcher"
UNINSTALL_KEY = r"Software\Microsoft\Windows\CurrentVersion\Uninstall\Peregrine"
# What the earlier (Inno Setup) installer registered: replaced on the first install of this one
OLD_INNO_KEY = r"Software\Microsoft\Windows\CurrentVersion\Uninstall\{6E3A2C41-9B8F-4C56-A7D2-5F1B0E9C8A31}_is1"
CREATE_NO_WINDOW = 0x08000000
DETACHED = 0x00000008 | 0x00000200  # DETACHED_PROCESS | CREATE_NEW_PROCESS_GROUP


def _env(name, fallback):
    v = os.environ.get(name)
    return Path(v) if v else Path.home() / fallback


def default_dir() -> Path:
    return _env("LOCALAPPDATA", "AppData/Local") / "Programs" / "Peregrine"


def programs_menu() -> Path:
    return _env("APPDATA", "AppData/Roaming") / "Microsoft" / "Windows" / "Start Menu" / "Programs"


def desktop() -> Path:
    try:
        import ctypes
        from ctypes import wintypes
        buf = ctypes.create_unicode_buffer(wintypes.MAX_PATH)
        if ctypes.windll.shell32.SHGetFolderPathW(None, 0x0010, None, 0, buf) == 0:  # CSIDL_DESKTOPDIRECTORY
            return Path(buf.value)
    except Exception:
        pass
    return Path.home() / "Desktop"


def startup_folder() -> Path:
    return programs_menu() / "Startup"


def shortcut_paths() -> dict:
    return {"menu": programs_menu() / f"{APP_NAME}.lnk",
            "desktop": desktop() / f"{APP_NAME}.lnk",
            "startup": startup_folder() / f"{APP_NAME}.lnk"}


def user_data_dirs() -> list:
    return [_env("LOCALAPPDATA", "AppData/Local") / "Peregrine", _env("APPDATA", "AppData/Roaming") / "Peregrine"]


# ---------------------------------------------------------------------- what's installed

def installed() -> dict:
    """{"dir", "version"} of the installed app (this installer's or the old one's), or {}."""
    try:
        import winreg
    except ImportError:
        return {}
    for key in (UNINSTALL_KEY, OLD_INNO_KEY):
        try:
            with winreg.OpenKey(winreg.HKEY_CURRENT_USER, key) as k:
                loc = winreg.QueryValueEx(k, "InstallLocation")[0]
                ver = winreg.QueryValueEx(k, "DisplayVersion")[0]
                if loc and (Path(loc) / EXE).is_file():
                    return {"dir": Path(loc.rstrip("\\/")), "version": ver}
        except OSError:
            continue
    d = default_dir()
    return {"dir": d, "version": ""} if (d / EXE).is_file() else {}


# ---------------------------------------------------------------------- closing a running copy

def _exe_free(path: Path) -> bool:
    """True when nothing is running from this exe (Windows won't open a running exe for writing)."""
    if not path.exists():
        return True
    try:
        with open(path, "r+b"):
            return True
    except OSError:
        return False


def close_running(app_dir: Path, wait: float = 20.0, log=print) -> None:
    """Wait for a running Peregrine (from this folder) to close, then make it close."""
    exe = app_dir / EXE
    end = time.time() + wait
    while time.time() < end:
        if _exe_free(exe):
            return
        time.sleep(0.4)
    log("Peregrine is still open: closing it")
    if sys.platform == "win32":
        _powershell(f"Get-Process | Where-Object {{ $_.Path -eq {_ps_quote(exe)} }} | Stop-Process -Force")
        for _ in range(25):
            if _exe_free(exe):
                return
            time.sleep(0.2)


# ---------------------------------------------------------------------- installing

def install(payload: Path, app_dir: Path, version: str, desktop_icon=True, startup=False, progress=None, log=print):
    """Puts the app in app_dir from payload (a zip of the built app folder).
    progress(done_bytes, total_bytes, text) is called as files are written."""
    progress = progress or (lambda *a: None)
    app_dir = Path(app_dir)
    progress(0, 1, "Getting ready")
    close_running(app_dir, log=log)
    app_dir.mkdir(parents=True, exist_ok=True)
    # Move the old program files out of the way first (games and settings live elsewhere).
    old = app_dir / "_internal"
    trash = None
    if old.exists():
        trash = app_dir.parent / f".peregrine-old-{int(time.time())}"
        try:
            os.replace(old, trash)
        except OSError:
            shutil.rmtree(old, ignore_errors=True)
            trash = None
    with zipfile.ZipFile(payload) as z:
        members = [m for m in z.infolist() if not m.is_dir()]
        total = sum(m.file_size for m in members) or 1
        done = 0
        root = app_dir.resolve()
        for i, m in enumerate(members):
            target = (app_dir / m.filename).resolve()
            if not str(target).startswith(str(root)):
                raise RuntimeError("The installer file looks damaged. Download it again.")
            target.parent.mkdir(parents=True, exist_ok=True)
            tmp = target.with_name(target.name + ".part")
            with z.open(m) as src, open(tmp, "wb") as dst:
                while True:
                    block = src.read(1 << 20)
                    if not block:
                        break
                    dst.write(block)
                    done += len(block)
                    progress(done, total, f"Copying files  {i + 1:,} / {len(members):,}")
            for attempt in range(10):
                try:
                    os.replace(tmp, target)
                    break
                except OSError:
                    if attempt == 9:
                        raise
                    time.sleep(0.3)
    if trash is not None:
        shutil.rmtree(trash, ignore_errors=True)
    progress(total, total, "Adding shortcuts")
    _remove_old_installer(app_dir)
    exe = app_dir / EXE
    links = shortcut_paths()
    make_shortcut(links["menu"], exe, log=log)
    if desktop_icon:
        make_shortcut(links["desktop"], exe, log=log)
    else:
        _unlink(links["desktop"])
    if startup:
        make_shortcut(links["startup"], exe, "--tray", log=log)
    else:
        _unlink(links["startup"])
    register(app_dir, version)
    progress(total, total, "Done")


def _unlink(p: Path):
    try:
        p.unlink()
    except OSError:
        pass


def _remove_old_installer(app_dir: Path):
    """Tidy up after the earlier Inno Setup installer: its uninstaller, entry and menu folder."""
    for name in ("unins000.exe", "unins000.dat", "unins000.msg"):
        _unlink(app_dir / name)
    shutil.rmtree(programs_menu() / "Peregrine", ignore_errors=True)
    try:
        import winreg
        winreg.DeleteKey(winreg.HKEY_CURRENT_USER, OLD_INNO_KEY)
    except (ImportError, OSError):
        pass


def _dir_size_kb(d: Path) -> int:
    total = 0
    for p in d.rglob("*"):
        try:
            if p.is_file():
                total += p.stat().st_size
        except OSError:
            pass
    return total // 1024


def register(app_dir: Path, version: str):
    """The entry in Settings → Apps (and Control Panel's Programs and Features)."""
    import winreg
    exe = app_dir / EXE
    with winreg.CreateKey(winreg.HKEY_CURRENT_USER, UNINSTALL_KEY) as k:
        for name, value in (("DisplayName", APP_NAME), ("DisplayVersion", version), ("Publisher", "Peregrine"),
                            ("DisplayIcon", f"{exe},0"), ("InstallLocation", str(app_dir)),
                            ("UninstallString", f'"{exe}" --uninstall'),
                            ("QuietUninstallString", f'"{exe}" --uninstall --quiet'),
                            ("URLInfoAbout", "https://github.com/retakesrunsyou/peregrine-launcher")):
            winreg.SetValueEx(k, name, 0, winreg.REG_SZ, value)
        for name, value in (("NoModify", 1), ("NoRepair", 1), ("EstimatedSize", _dir_size_kb(app_dir))):
            winreg.SetValueEx(k, name, 0, winreg.REG_DWORD, value)


# ---------------------------------------------------------------------- shortcuts

# A .lnk with the app's taskbar id, so a pinned shortcut and the running launcher share
# one taskbar button. Made by a little C# snippet run through PowerShell (no extra files).
_LNK_CS = r'''
using System; using System.Runtime.InteropServices; using System.Runtime.InteropServices.ComTypes;
public static class PeregrineLnk {
  [ComImport, Guid("000214F9-0000-0000-C000-000000000046"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
  interface IShellLinkW {
    void GetPath([Out, MarshalAs(UnmanagedType.LPWStr)] System.Text.StringBuilder f, int c, IntPtr d, int fl);
    void GetIDList(out IntPtr p); void SetIDList(IntPtr p);
    void GetDescription([Out, MarshalAs(UnmanagedType.LPWStr)] System.Text.StringBuilder n, int c);
    void SetDescription([MarshalAs(UnmanagedType.LPWStr)] string n);
    void GetWorkingDirectory([Out, MarshalAs(UnmanagedType.LPWStr)] System.Text.StringBuilder d, int c);
    void SetWorkingDirectory([MarshalAs(UnmanagedType.LPWStr)] string d);
    void GetArguments([Out, MarshalAs(UnmanagedType.LPWStr)] System.Text.StringBuilder a, int c);
    void SetArguments([MarshalAs(UnmanagedType.LPWStr)] string a);
    void GetHotkey(out short h); void SetHotkey(short h);
    void GetShowCmd(out int s); void SetShowCmd(int s);
    void GetIconLocation([Out, MarshalAs(UnmanagedType.LPWStr)] System.Text.StringBuilder p, int c, out int i);
    void SetIconLocation([MarshalAs(UnmanagedType.LPWStr)] string p, int i);
    void SetRelativePath([MarshalAs(UnmanagedType.LPWStr)] string p, int r);
    void Resolve(IntPtr h, int f);
    void SetPath([MarshalAs(UnmanagedType.LPWStr)] string f);
  }
  [ComImport, Guid("886D8EEB-8CF2-4446-8D02-CDBA1DBDCF99"), InterfaceType(ComInterfaceType.InterfaceIsIUnknown)]
  interface IPropertyStore {
    void GetCount(out uint c); void GetAt(uint i, out PropertyKey k);
    void GetValue(ref PropertyKey k, IntPtr v); void SetValue(ref PropertyKey k, PropVariant v); void Commit();
  }
  [StructLayout(LayoutKind.Sequential, Pack = 4)]
  public struct PropertyKey { public Guid fmtid; public uint pid; }
  [StructLayout(LayoutKind.Explicit)]
  public class PropVariant { [FieldOffset(0)] public ushort vt; [FieldOffset(8)] public IntPtr p; [FieldOffset(16)] public IntPtr pad; }
  [ComImport, Guid("00021401-0000-0000-C000-000000000046")] class CShellLink {}
  public static void Make(string lnk, string target, string args, string dir, string icon, string aumid) {
    var link = (IShellLinkW)new CShellLink();
    link.SetPath(target); link.SetArguments(args); link.SetWorkingDirectory(dir);
    link.SetIconLocation(icon, 0); link.SetDescription("Peregrine Launcher");
    var store = (IPropertyStore)link;
    var key = new PropertyKey { fmtid = new Guid("9F4C2855-9F79-4B39-A8D0-E1D42DE1D5F3"), pid = 5 };
    var pv = new PropVariant { vt = 31, p = Marshal.StringToCoTaskMemUni(aumid) };
    store.SetValue(ref key, pv); store.Commit();
    Marshal.FreeCoTaskMem(pv.p);
    ((IPersistFile)link).Save(lnk, true);
  }
}
'''


def _ps_quote(s) -> str:
    return "'" + str(s).replace("'", "''") + "'"


def _powershell(script: str):
    """Runs a PowerShell script (passed encoded, so no quoting can go wrong)."""
    import base64
    enc = base64.b64encode(script.encode("utf-16-le")).decode()
    return subprocess.run(["powershell", "-NoProfile", "-NonInteractive", "-ExecutionPolicy", "Bypass",
                           "-EncodedCommand", enc], capture_output=True, text=True,
                          creationflags=CREATE_NO_WINDOW if sys.platform == "win32" else 0)


def make_shortcut(lnk: Path, exe: Path, args: str = "", log=print) -> None:
    lnk.parent.mkdir(parents=True, exist_ok=True)
    q = _ps_quote
    script = (f"$ErrorActionPreference='Stop'; Add-Type -TypeDefinition {q(_LNK_CS)}; "
              f"[PeregrineLnk]::Make({q(lnk)}, {q(exe)}, {q(args)}, {q(exe.parent)}, {q(exe)}, {q(AUMID)})")
    r = _powershell(script)
    if r.returncode == 0 and lnk.exists():
        return
    log(f"shortcut with taskbar id failed, making a plain one: {(r.stderr or r.stdout).strip()[-300:]}")
    # plain shortcut as a fallback (still works, just without the taskbar id)
    fallback = (f"$s=(New-Object -ComObject WScript.Shell).CreateShortcut({q(lnk)}); $s.TargetPath={q(exe)}; "
                f"$s.Arguments={q(args)}; $s.WorkingDirectory={q(exe.parent)}; $s.IconLocation={q(str(exe) + ',0')}; "
                f"$s.Save()")
    _powershell(fallback)


# ---------------------------------------------------------------------- starting the app

def clean_env() -> dict:
    """The environment for starting the installed app from the installer: without the
    installer's own unpacking settings, which would confuse the app."""
    env = dict(os.environ)
    here = getattr(sys, "_MEIPASS", None)
    for k in list(env):
        if k.startswith(("_PYI", "_MEI")) or (here and k.startswith("QT_") and here in env[k]):
            env.pop(k, None)
    if here and "PATH" in env:
        env["PATH"] = os.pathsep.join(p for p in env["PATH"].split(os.pathsep) if here not in p)
    env["PYINSTALLER_RESET_ENVIRONMENT"] = "1"
    return env


def launch(app_dir: Path, args=()) -> None:
    exe = Path(app_dir) / EXE
    subprocess.Popen([str(exe), *args], cwd=str(app_dir), env=clean_env(), close_fds=True,
                     creationflags=DETACHED if sys.platform == "win32" else 0)


# ---------------------------------------------------------------------- uninstalling

def uninstall(app_dir: Path, remove_data: bool = False, log=print) -> None:
    """Removes shortcuts, the Apps & features entry and (after this program exits) the app
    folder. remove_data also deletes instances, worlds and settings."""
    exe = Path(app_dir) / EXE
    if sys.platform == "win32":  # other copies of the launcher still open
        _powershell(f"Get-Process | Where-Object {{ $_.Path -eq {_ps_quote(exe)} -and $_.Id -ne {os.getpid()} }}"
                    " | Stop-Process -Force")
    for p in shortcut_paths().values():
        _unlink(p)
    _remove_old_installer(app_dir)
    try:
        import winreg
        winreg.DeleteKey(winreg.HKEY_CURRENT_USER, UNINSTALL_KEY)
    except (ImportError, OSError):
        pass
    targets = [Path(app_dir)] + (user_data_dirs() if remove_data else [])
    # A running exe can't delete its own folder: a short-lived helper does it once we've quit.
    import tempfile
    lines = ["@echo off", "ping 127.0.0.1 -n 3 > nul"]
    for t in targets:
        lines.append(f'for /l %%i in (1,1,15) do if exist "{t}" (rmdir /s /q "{t}" 2>nul & ping 127.0.0.1 -n 2 > nul)')
    lines.append('del "%~f0"')
    fd, bat = tempfile.mkstemp(prefix="peregrine-uninstall-", suffix=".bat")
    with os.fdopen(fd, "w") as f:
        f.write("\r\n".join(lines) + "\r\n")
    subprocess.Popen(["cmd", "/c", bat], cwd=tempfile.gettempdir(), close_fds=True,
                     creationflags=CREATE_NO_WINDOW | 0x00000200)
    log("removal scheduled")
