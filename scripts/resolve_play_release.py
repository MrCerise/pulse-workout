"""Validate release-train inputs before building or exposing signing credentials."""

import os
from pathlib import Path
import re
import sys


def resolve_release(environ, build_file):
    channel = environ["RELEASE_CHANNEL"]
    if channel not in {"internal", "closed", "beta", "production"}:
        raise ValueError("Channel must be internal, closed, beta or production.")

    track = environ.get("RELEASE_PLAY_TRACK", "").strip()
    if channel == "closed":
        if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9._-]{0,99}", track) or track in {"internal", "beta", "production"}:
            raise ValueError("Closed testing requires its Play track ID, for example alpha or closed-testing.")
    elif track and track != channel:
        raise ValueError("A custom Play track is only supported with the closed channel.")
    else:
        track = channel

    name = environ.get("RELEASE_VERSION_NAME", "").strip()
    tag = environ.get("RELEASE_TAG", "").strip()
    tag_match = None
    if tag:
        tag_match = re.fullmatch(
            r"v((?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*)\.(?:0|[1-9][0-9]*))"
            r"(?:-([0-9A-Za-z-]+(?:\.[0-9A-Za-z-]+)*))?", tag,
        )
        if not tag_match:
            raise ValueError("Release tag must look like v1.8.4 or v1.8.4-rc.1.")
        if name and name != tag_match[1]:
            raise ValueError("Version name must match the selected release tag.")
        name = tag_match[1]
    if not name:
        match = re.search(r'versionName\s*=.*?\?:\s*"([^"]+)"', build_file)
        if not match:
            raise ValueError("Could not read the default versionName from app/build.gradle.kts.")
        name = match[1]
    if not re.fullmatch(r"(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)", name):
        raise ValueError("Version name must be MAJOR.MINOR.PATCH, without a v or suffix.")

    # Manual trains keep explicit codes. Tag builds keep the existing numeric tag
    # scheme, but reserve an increasing slot for *every* run, including stable tags.
    # Never wrap with modulo: silently reusing a slot would collide on Play.
    code = environ.get("RELEASE_VERSION_CODE", "").strip()
    if not code and tag:
        major, minor, patch = map(int, name.split("."))
        run = environ.get("GITHUB_RUN_NUMBER", "")
        if minor > 99 or patch > 99 or not re.fullmatch(r"[1-9][0-9]{0,2}", run):
            raise ValueError("This tag needs an explicit version_code; automatic slots require minor/patch <= 99 and run number 1–999.")
        code = str(major * 10_000_000 + minor * 100_000 + patch * 1_000 + int(run))
    if not re.fullmatch(r"[1-9][0-9]{0,9}", code) or int(code) > 2_100_000_000:
        raise ValueError("Version code must be an integer from 1 to 2100000000, higher than previous Play uploads.")

    status = environ["RELEASE_STATUS"]
    if status not in {"draft", "completed"}:
        raise ValueError("Release status must be draft or completed.")
    percent = environ.get("RELEASE_ROLLOUT_PERCENT", "100").strip()
    if not re.fullmatch(r"[1-9][0-9]?|100", percent):
        raise ValueError("Rollout percent must be an integer from 1 to 100.")
    fraction = ""
    if int(percent) < 100:
        if channel != "production" or status != "completed":
            raise ValueError("A staged rollout requires production and completed status.")
        status = "inProgress"
        fraction = f"{int(percent) / 100:.2f}"

    full_name = tag[1:] if tag else (name if channel == "production" else f"{name}-{channel}.{code}")
    prerelease = bool(tag_match[2]) if tag_match else channel != "production"
    return {
        "name": name, "code": code, "full_name": full_name, "status": status,
        "fraction": fraction, "track": track, "release_tag": tag or f"v{full_name}",
        "prerelease": str(prerelease).lower(),
    }


def main():
    try:
        outputs = resolve_release(os.environ, Path("app/build.gradle.kts").read_text(encoding="utf-8"))
    except (KeyError, ValueError) as error:
        print(f"::error::{error}", file=sys.stderr)
        return 1
    with open(os.environ["GITHUB_OUTPUT"], "a", encoding="utf-8") as output:
        for key, value in outputs.items():
            output.write(f"{key}={value}\n")
    print(f"Release: {outputs['full_name']} (versionCode {outputs['code']}), {outputs['status']}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
