"""Only one launcher at a time.

The first launcher listens on a local channel (a named pipe on Windows, a socket file
elsewhere) named for this user. A second one opened later finds it, asks it to come to the
front, and quits; so double-clicking the icon again brings the window back instead of
starting another launcher that would fight over the same files."""

import getpass
import hashlib

NAME = "peregrine-launcher"


def channel_name() -> str:
    try:
        user = getpass.getuser()
    except Exception:
        user = "user"
    return NAME + "-" + hashlib.sha1(user.encode("utf-8", "replace")).hexdigest()[:10]


def already_running(timeout_ms: int = 600) -> bool:
    """True if another launcher answered (it has been asked to show itself)."""
    from PySide6.QtNetwork import QLocalSocket
    sock = QLocalSocket()
    sock.connectToServer(channel_name())
    if not sock.waitForConnected(timeout_ms):
        return False
    sock.write(b"show\n")
    sock.flush()
    sock.waitForBytesWritten(timeout_ms)
    sock.disconnectFromServer()
    return True


_active = None


def release() -> None:
    """Stops listening (before the launcher restarts itself in place after an update)."""
    global _active
    if _active is not None:
        _active.close()
        _active = None


class Guard:
    """Listens for later launchers; calls on_show() when one asks (on the window's thread)."""

    def __init__(self, on_show):
        from PySide6.QtNetwork import QLocalServer
        self.on_show = on_show
        self.server = QLocalServer()
        self.server.setSocketOptions(QLocalServer.SocketOption.UserAccessOption)
        name = channel_name()
        if not self.server.listen(name):
            # left over from a launcher that crashed: nobody answered, so clear it and listen
            QLocalServer.removeServer(name)
            self.server.listen(name)
        self.server.newConnection.connect(self._connected)
        global _active
        _active = self

    def _connected(self):
        while self.server.hasPendingConnections():
            sock = self.server.nextPendingConnection()
            sock.readyRead.connect(lambda s=sock: self._read(s))
            sock.disconnected.connect(sock.deleteLater)
            if sock.bytesAvailable():
                self._read(sock)

    def _read(self, sock):
        data = bytes(sock.readAll())
        if b"show" in data:
            try:
                self.on_show()
            except Exception as e:  # never let this take the launcher down
                print(f"[peregrine] couldn't bring the window forward: {e}")

    def close(self):
        self.server.close()
