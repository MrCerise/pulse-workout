"""Validate release tags without switching away from the workflow event's commit."""

import os
from pathlib import Path
import re
import subprocess
import sys


def validate_source(repository, workflow_sha, source_tag, release_tag):
    def git(*arguments):
        return subprocess.run(
            ["git", *arguments], cwd=repository, capture_output=True, text=True, timeout=20,
        )

    if not re.fullmatch(r"(?:[0-9a-f]{40}|[0-9a-f]{64})", workflow_sha):
        raise ValueError("The workflow must supply its immutable Git commit SHA.")
    head = git("rev-parse", "--verify", "HEAD^{commit}")
    if head.returncode or head.stdout.strip() != workflow_sha:
        raise ValueError("The checkout does not match the workflow event's commit.")
    if git("merge-base", "--is-ancestor", workflow_sha, "refs/remotes/origin/main").returncode:
        raise ValueError("Release source must be a commit already merged into main.")

    for label, tag in (("Requested source", source_tag), ("Published release", release_tag)):
        if not tag:
            continue
        ref = f"refs/tags/{tag}"
        if git("check-ref-format", ref).returncode:
            raise ValueError(f"{label} tag is not a valid Git tag name.")
        exists = git("show-ref", "--verify", "--quiet", ref)
        if exists.returncode == 1:
            if label == "Requested source":
                raise ValueError("The requested source tag does not exist.")
            continue
        if exists.returncode:
            raise ValueError(f"Cannot inspect the {label.lower()} tag.")
        commit = git("rev-parse", "--verify", "--end-of-options", f"{ref}^{{commit}}")
        if commit.returncode or commit.stdout.strip() != workflow_sha:
            raise ValueError(
                f"{label} tag points to another commit. Select that tag under Run workflow > "
                "Use workflow from (or gh workflow run --ref TAG), or use a new release tag."
            )
    return workflow_sha


def main():
    try:
        sha = validate_source(
            Path.cwd(), os.environ.get("WORKFLOW_SHA", ""),
            os.environ.get("SOURCE_TAG", ""), os.environ.get("RELEASE_TAG", ""),
        )
    except (ValueError, OSError, subprocess.TimeoutExpired) as error:
        print(f"Release source rejected: {error}", file=sys.stderr)
        return 1
    print(f"Validated workflow commit: {sha}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
