"""RSA signatures (PKCS#1 v1.5 with SHA-256), small and dependency-free.

Used to prove who you are to the Peregrine service with the key Minecraft itself gives your
account for chat signing: the service checks Mojang's signature on that key, then yours on
a one-time challenge. Nothing secret leaves your computer.
"""

import base64
import hashlib

_SHA256_PREFIX = bytes.fromhex("3031300d060960864801650304020105000420")


def _read(der: bytes, i: int):
    """One DER element at i: (tag, content start, content end)."""
    tag = der[i]
    length = der[i + 1]
    i += 2
    if length & 0x80:
        n = length & 0x7F
        length = int.from_bytes(der[i:i + n], "big")
        i += n
    return tag, i, i + length


def _children(der: bytes, start: int, end: int):
    out = []
    i = start
    while i < end:
        tag, s, e = _read(der, i)
        out.append((tag, s, e))
        i = e
    return out


def _rsa_numbers(der: bytes):
    """n and d from a PKCS#8 or PKCS#1 private key."""
    tag, s, e = _read(der, 0)
    parts = _children(der, s, e)
    if len(parts) >= 3 and parts[1][0] == 0x30 and parts[2][0] == 0x04:  # PKCS#8: unwrap
        inner = der[parts[2][1]:parts[2][2]]
        return _rsa_numbers(inner)
    ints = [int.from_bytes(der[a:b], "big") for t, a, b in parts if t == 0x02]
    if len(ints) < 4:
        raise ValueError("not an RSA private key")
    return ints[1], ints[3]  # n, d


def pem_body(pem: str) -> bytes:
    lines = [ln.strip() for ln in pem.strip().splitlines() if ln.strip() and not ln.startswith("-----")]
    return base64.b64decode("".join(lines))


def sign_sha256(private_pem: str, message: bytes) -> bytes:
    n, d = _rsa_numbers(pem_body(private_pem))
    k = (n.bit_length() + 7) // 8
    t = _SHA256_PREFIX + hashlib.sha256(message).digest()
    em = b"\x00\x01" + b"\xff" * (k - len(t) - 3) + b"\x00" + t
    return pow(int.from_bytes(em, "big"), d, n).to_bytes(k, "big")
