"""Verify only the two expected phone assets; never follow paths from a checksum manifest."""
import hashlib
import os
from pathlib import Path
import re
import sys


def verify_assets(directory, version):
    if not re.fullmatch(r"(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)", version):
        raise ValueError("Expected a MAJOR.MINOR.PATCH version.")
    manifest = directory / "SHA256SUMS"
    if manifest.is_symlink() or not manifest.is_file() or manifest.stat().st_size > 64 * 1024:
        raise ValueError("Expected a regular checksum manifest of at most 64 KiB.")
    sums = {}
    for line in manifest.read_text(encoding="utf-8").splitlines():
        match = re.fullmatch(r"([0-9a-f]{64}) [ *](.+)", line)
        if not match:
            raise ValueError("Invalid checksum manifest.")
        digest, name = match.groups()
        if name in sums:
            raise ValueError("Duplicate checksum entry.")
        sums[name] = digest
    for suffix in ("release.aab", "mapping.txt"):
        name = f"interval-coach-{version}-phone-{suffix}"
        path = directory / name
        if name not in sums or path.is_symlink() or not path.is_file():
            raise ValueError(f"Missing regular release asset or checksum: {name}")
        with path.open("rb") as stream:
            digest = hashlib.file_digest(stream, "sha256").hexdigest()
        if digest != sums[name]:
            raise ValueError(f"Checksum mismatch: {name}")
    return True


def main():
    try:
        verify_assets(Path("release-assets"), os.environ.get("VERSION_NAME", ""))
    except (ValueError, OSError) as error:
        print(f"Release assets rejected: {error}", file=sys.stderr)
        return 1
    print("Phone bundle and mapping match the published release checksums.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
