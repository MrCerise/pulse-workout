"""Validate release-train inputs before building or exposing signing credentials."""

import os
from pathlib import Path
import re
import sys


def resolve_release(environ, build_file):
    channel = environ["RELEASE_CHANNEL"]
    if channel not in {"internal", "beta", "production"}:
        raise ValueError("Channel must be internal, beta or production.")

    name = environ.get("RELEASE_VERSION_NAME", "").strip()
    if not name:
        match = re.search(r'versionName\s*=.*?\?:\s*"([^"]+)"', build_file)
        if not match:
            raise ValueError("Could not read the default versionName from app/build.gradle.kts.")
        name = match[1]
    if not re.fullmatch(r"(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)", name):
        raise ValueError("Version name must be MAJOR.MINOR.PATCH, without a v or suffix.")

    # Explicit codes work across workflows, tracks and reruns; workflow-local run numbers do not.
    code = environ.get("RELEASE_VERSION_CODE", "").strip()
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

    full_name = name if channel == "production" else f"{name}-{channel}.{code}"
    return {"name": name, "code": code, "full_name": full_name, "status": status, "fraction": fraction}


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
