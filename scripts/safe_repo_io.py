"""Bounded, symlink-resistant I/O for repository validation scripts.

The scripts run on pull-request contents in CI. Source files and generated reports therefore must
not be allowed to redirect reads through symlinks, stream forever, or redirect truncating writes.
"""
from __future__ import annotations

import errno
import os
import secrets
import stat
import tempfile
from pathlib import Path

MAX_SOURCE_FILE_BYTES = 2 * 1024 * 1024
MAX_SOURCE_TREE_BYTES = 32 * 1024 * 1024


class RepositoryIOError(ValueError):
    """An unsafe or oversized repository input/output path."""


def _relative_parts(root: Path, path: Path) -> tuple[str, ...]:
    root = Path(root).resolve(strict=True)
    path = Path(path)
    try:
        relative = path.relative_to(root)
    except ValueError as error:
        raise RepositoryIOError(f"{path}: path is outside the checkout") from error
    parts = relative.parts
    if not parts or any(part in ("", ".", "..") for part in parts):
        raise RepositoryIOError(f"{path}: invalid repository-relative path")
    return parts


def _supports_openat_nofollow() -> bool:
    return (
        os.name == "posix"
        and hasattr(os, "O_NOFOLLOW")
        and hasattr(os, "O_DIRECTORY")
        and os.open in getattr(os, "supports_dir_fd", set())
    )


def _open_directory(root: Path, parts: tuple[str, ...]) -> int:
    """Open a directory beneath root without following any contributed symlink component."""
    flags = os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW | getattr(os, "O_CLOEXEC", 0)
    current = os.open(root, flags)
    try:
        for part in parts:
            following = os.open(part, flags, dir_fd=current)
            os.close(current)
            current = following
        return current
    except BaseException:
        os.close(current)
        raise


def _read_from_fd(fd: int, path: Path, limit: int, limit_message: str) -> bytes:
    info = os.fstat(fd)
    if not stat.S_ISREG(info.st_mode):
        raise RepositoryIOError(f"{path}: expected a regular source file")
    if info.st_size > limit:
        raise RepositoryIOError(limit_message)

    content = bytearray()
    while len(content) <= limit:
        chunk = os.read(fd, min(64 * 1024, limit + 1 - len(content)))
        if not chunk:
            break
        content.extend(chunk)
    if len(content) > limit:
        raise RepositoryIOError(limit_message)
    return bytes(content)


def _read_fallback(root: Path, parts: tuple[str, ...], path: Path, limit: int, message: str) -> bytes:
    """Portable fallback for platforms without openat/O_NOFOLLOW (e.g. Windows)."""
    parent = root
    for part in parts[:-1]:
        parent = parent / part
        try:
            info = parent.lstat()
        except OSError as error:
            raise RepositoryIOError(f"{path}: cannot inspect source path ({error})") from error
        if stat.S_ISLNK(info.st_mode) or not stat.S_ISDIR(info.st_mode):
            raise RepositoryIOError(f"{path}: symlink or non-directory path component is not allowed")

    target = parent / parts[-1]
    try:
        info = target.lstat()
    except OSError as error:
        raise RepositoryIOError(f"{path}: cannot inspect source file ({error})") from error
    if stat.S_ISLNK(info.st_mode) or not stat.S_ISREG(info.st_mode):
        raise RepositoryIOError(f"{path}: expected a non-symlink regular source file")
    if info.st_size > limit:
        raise RepositoryIOError(message)
    try:
        with target.open("rb") as stream:
            if not stat.S_ISREG(os.fstat(stream.fileno()).st_mode):
                raise RepositoryIOError(f"{path}: expected a regular source file")
            content = stream.read(limit + 1)
    except OSError as error:
        raise RepositoryIOError(f"{path}: cannot read source file ({error})") from error
    if len(content) > limit:
        raise RepositoryIOError(message)
    return content


class BoundedSourceReader:
    """Reads only regular, non-symlink files below a root with per-file and aggregate limits."""

    def __init__(
        self,
        repository_root: Path,
        max_file_bytes: int = MAX_SOURCE_FILE_BYTES,
        max_total_bytes: int = MAX_SOURCE_TREE_BYTES,
    ) -> None:
        self.root = Path(repository_root).resolve(strict=True)
        self.max_file_bytes = max_file_bytes
        self.max_total_bytes = max_total_bytes
        self.bytes_read = 0

    def read_bytes(self, path: Path) -> bytes:
        parts = _relative_parts(self.root, path)
        remaining = self.max_total_bytes - self.bytes_read
        if remaining < 0:
            raise RepositoryIOError(f"{path}: aggregate source limit exceeded")
        limit = min(self.max_file_bytes, remaining)
        message = (
            f"{path}: source exceeds the {self.max_file_bytes}-byte per-file limit"
            if limit == self.max_file_bytes
            else f"{path}: aggregate source limit of {self.max_total_bytes} bytes exceeded"
        )

        try:
            if not _supports_openat_nofollow():
                data = _read_fallback(self.root, parts, Path(path), limit, message)
            else:
                parent_fd = _open_directory(self.root, parts[:-1])
                try:
                    flags = (
                        os.O_RDONLY
                        | os.O_NOFOLLOW
                        | getattr(os, "O_CLOEXEC", 0)
                        | getattr(os, "O_NONBLOCK", 0)
                    )
                    fd = os.open(parts[-1], flags, dir_fd=parent_fd)
                    try:
                        data = _read_from_fd(fd, Path(path), limit, message)
                    finally:
                        os.close(fd)
                finally:
                    os.close(parent_fd)
        except RepositoryIOError:
            raise
        except OSError as error:
            reason = "symbolic links are not allowed" if error.errno == errno.ELOOP else str(error)
            raise RepositoryIOError(f"{path}: cannot safely read source file ({reason})") from error

        self.bytes_read += len(data)
        return data

    def read_text(self, path: Path) -> str:
        try:
            return self.read_bytes(path).decode("utf-8")
        except UnicodeDecodeError as error:
            raise RepositoryIOError(f"{path}: source is not valid UTF-8") from error


def _write_fallback(root: Path, parts: tuple[str, ...], path: Path, content: bytes) -> None:
    parent = root
    for part in parts[:-1]:
        parent = parent / part
        try:
            info = parent.lstat()
        except OSError as error:
            raise RepositoryIOError(f"{path}: cannot inspect output directory ({error})") from error
        if stat.S_ISLNK(info.st_mode) or not stat.S_ISDIR(info.st_mode):
            raise RepositoryIOError(f"{path}: symlink or non-directory output component is not allowed")

    temporary = None
    try:
        fd, temporary = tempfile.mkstemp(prefix=f".{parts[-1]}.", suffix=".tmp", dir=parent)
        with os.fdopen(fd, "wb") as stream:
            stream.write(content)
            stream.flush()
            os.fsync(stream.fileno())
        os.chmod(temporary, 0o644)
        os.replace(temporary, parent / parts[-1])
        temporary = None
    except OSError as error:
        raise RepositoryIOError(f"{path}: cannot safely write report ({error})") from error
    finally:
        if temporary is not None:
            try:
                os.unlink(temporary)
            except FileNotFoundError:
                pass


def atomic_write_text(repository_root: Path, relative_path: Path, text: str) -> None:
    """Atomically replace a report entry without following its destination symlink."""
    root = Path(repository_root).resolve(strict=True)
    relative_path = Path(relative_path)
    if relative_path.is_absolute():
        raise RepositoryIOError(f"{relative_path}: report path must be relative to the checkout")
    parts = relative_path.parts
    if not parts or any(part in ("", ".", "..") for part in parts):
        raise RepositoryIOError(f"{relative_path}: invalid report path")
    content = text.encode("utf-8")

    if not _supports_openat_nofollow():
        _write_fallback(root, parts, root.joinpath(*parts), content)
        return

    try:
        parent_fd = _open_directory(root, parts[:-1])
    except OSError as error:
        raise RepositoryIOError(f"{relative_path}: unsafe or missing report directory ({error})") from error
    temporary_name = f".{parts[-1]}.{secrets.token_hex(12)}.tmp"
    try:
        flags = os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW | getattr(os, "O_CLOEXEC", 0)
        fd = os.open(temporary_name, flags, 0o644, dir_fd=parent_fd)
        try:
            os.fchmod(fd, 0o644)
            view = memoryview(content)
            while view:
                written = os.write(fd, view)
                view = view[written:]
            os.fsync(fd)
        finally:
            os.close(fd)
        # replace() operates on the directory entry itself; if it is a symlink, it replaces
        # the link rather than opening or truncating its target.
        os.replace(
            temporary_name,
            parts[-1],
            src_dir_fd=parent_fd,
            dst_dir_fd=parent_fd,
        )
        os.fsync(parent_fd)
    except OSError as error:
        try:
            os.unlink(temporary_name, dir_fd=parent_fd)
        except OSError:
            pass
        raise RepositoryIOError(f"{relative_path}: cannot safely write report ({error})") from error
    finally:
        os.close(parent_fd)
