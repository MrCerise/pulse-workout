"""Validate versioned release notes and stage the same text for both publishing jobs."""
import os
from pathlib import Path
import re
import sys

from safe_repo_io import BoundedSourceReader, RepositoryIOError


def prepare_notes(releases, version, destination):
    if not re.fullmatch(r"(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)", version):
        raise ValueError("Release notes version must be MAJOR.MINOR.PATCH.")
    source = releases / version
    reader = BoundedSourceReader(releases)
    github = reader.read_text(source / "github.md").strip()
    if not github or len(github) > 16_000:
        raise ValueError("GitHub release notes must contain 1 to 16000 characters.")
    notes = {}
    for path in sorted((source / "whatsnew").glob("whatsnew-*")):
        if not re.fullmatch(r"whatsnew-[a-z]{2,3}(?:-[A-Za-z0-9]{2,8})*", path.name):
            raise ValueError(f"Invalid localized release note filename: {path.name}")
        value = reader.read_text(path).strip()
        if not value or len(value) > 500:
            raise ValueError(f"{path.name}: Google Play release notes must contain 1 to 500 characters.")
        notes[path.name] = value
    if not {"whatsnew-en-US", "whatsnew-fr-FR"}.issubset(notes):
        raise ValueError("Release notes must include English (en-US) and French (fr-FR).")

    # Validate every locale before producing any upload artifact.
    (destination / "whatsnew").mkdir(parents=True, exist_ok=True)
    (destination / "github.md").write_text(github + "\n", encoding="utf-8")
    for name, value in notes.items():
        (destination / "whatsnew" / name).write_text(value, encoding="utf-8")
    return len(notes)


def main():
    root = Path(__file__).resolve().parents[1]
    try:
        count = prepare_notes(
            root / "distribution/releases", os.environ.get("RELEASE_NOTES_VERSION", ""),
            Path(os.environ["RELEASE_NOTES_OUTPUT"]),
        )
    except (KeyError, ValueError, OSError, RepositoryIOError) as error:
        print(f"Release notes rejected: {error}", file=sys.stderr)
        return 1
    print(f"Validated GitHub changelog and {count} Play release-note translations.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
