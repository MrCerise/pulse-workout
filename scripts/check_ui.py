#!/usr/bin/env python3
"""
PULSE UI/resource static checker.

The Gradle build needs the Android SDK and a JDK; neither is always available (CI images,
code-review sandboxes, a laptop before Android Studio has been installed). This script catches the
class of mistake that actually breaks a UI change without needing a toolchain:

  1. every XML resource parses;
  2. every Android VectorDrawable `pathData` is grammatically valid (an invalid path crashes the
     inflation of the icon, not the build);
  3. every `@type/name` reference in an XML resource resolves to a resource that exists;
  4. every `R.string.x` / `R.drawable.x` / `R.color.x` used from Kotlin resolves to a resource;
  5. every English string has a French translation (the two tables must stay in step);
  6. each Kotlin source file is structurally balanced — brackets, braces, parentheses, quotes,
     triple-quoted strings and comments — so an edit that drops a brace is caught here.

Usage (from the repository root):

    python3 scripts/check_ui.py

Exits non-zero on the first category of failure and always prints a summary.
"""

from __future__ import annotations

import re
import sys
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
RES = ROOT / "app/src/main/res"
KOTLIN_ROOTS = [
    ROOT / "app/src/main/java",
    ROOT / "engine/src/main/kotlin",
]

ANDROID_NS = "http://schemas.android.com/apk/res/android"


def qn(tag: str) -> str:
    return f"{{{ANDROID_NS}}}{tag}"


# ---------------------------------------------------------------------------------------------
# Resource inventory
# ---------------------------------------------------------------------------------------------

def collect_resources() -> dict[str, set[str]]:
    """Maps resource type -> set of names, from `res/<type>[-qualifier]/<name>.<ext>` and values."""
    resources: dict[str, set[str]] = {}
    for path in sorted(RES.rglob("*")):
        if not path.is_file():
            continue
        relative = path.relative_to(RES)
        folder = relative.parts[0]
        resource_type = folder.split("-")[0]
        if resource_type == "values":
            try:
                tree = ET.parse(path)
            except ET.ParseError:
                continue
            for child in tree.getroot():
                name = child.get("name")
                if not name:
                    continue
                tag = child.tag.split("}")[-1]
                if tag == "string":
                    resources.setdefault("string", set()).add(name)
                elif tag == "color":
                    resources.setdefault("color", set()).add(name)
                elif tag in ("style", "declare-styleable"):
                    resources.setdefault("style", set()).add(name)
                elif tag in ("dimen", "integer", "bool", "string-array", "array", "plurals"):
                    resources.setdefault(
                        {"string-array": "array", "plurals": "plurals"}.get(tag, tag), set()
                    ).add(name)
                elif tag == "item":
                    declared = child.get("type")
                    if declared:
                        resources.setdefault(declared, set()).add(name)
        else:
            resources.setdefault(resource_type, set()).add(path.stem)
    return resources


def string_tables() -> tuple[set[str], set[str]]:
    def names(path: Path) -> set[str]:
        if not path.exists():
            return set()
        tree = ET.parse(path)
        return {
            child.get("name")
            for child in tree.getroot()
            if child.tag.endswith("string") and child.get("name")
        }

    return names(RES / "values/strings.xml"), names(RES / "values-fr/strings.xml")


# ---------------------------------------------------------------------------------------------
# XML: well-formedness + vector paths
# ---------------------------------------------------------------------------------------------

PATH_COMMAND = set("MmZzLlHhVvCcSsQqTtAa")
PATH_ARGS = {
    "M": 2, "m": 2, "L": 2, "l": 2, "H": 1, "h": 1, "V": 1, "v": 1,
    "C": 6, "c": 6, "S": 4, "s": 4, "Q": 4, "q": 4, "T": 2, "t": 2, "A": 7, "a": 7,
}


def check_path_data(path: Path, raw: str) -> list[str]:
    """Validates an SVG/Android path string: known commands with an arity that matches."""
    tokens = re.findall(r"[MmZzLlHhVvCcSsQqTtAa]|[+-]?(?:\d+\.?\d*|\.\d+)(?:[eE][+-]?\d+)?", raw)
    if not tokens:
        return [f"{path}: empty pathData"]
    problems = []
    index = 0
    while index < len(tokens):
        token = tokens[index]
        if token not in PATH_COMMAND:
            return [f"{path}: pathData must start with a command, found {token!r}"]
        if token in ("Z", "z"):
            index += 1
            continue
        needed = PATH_ARGS[token]
        index += 1
        count = 0
        while index < len(tokens) and tokens[index] not in PATH_COMMAND:
            count += 1
            index += 1
        if count == 0 or count % needed != 0:
            problems.append(
                f"{path}: pathData command {token!r} takes {needed} values per repeat, got {count}"
            )
    return problems


def check_xml(resources: dict[str, set[str]]) -> list[str]:
    problems = []
    for path in sorted(RES.rglob("*.xml")):
        try:
            tree = ET.parse(path)
        except ET.ParseError as error:
            problems.append(f"{path.relative_to(ROOT)}: not well-formed — {error}")
            continue
        root = tree.getroot()
        tag = root.tag.split("}")[-1]
        if tag == "vector":
            for element in root.iter():
                if element.tag.split("}")[-1] != "path":
                    continue
                data = element.get(qn("pathData"))
                if data is None:
                    continue
                problems += check_path_data(path.relative_to(ROOT), data)
        for element in root.iter():
            for attribute, value in element.attrib.items():
                if not value.startswith("@") or value.startswith("@+") or value.startswith("@*"):
                    continue
                body = value[1:]
                if ":" in body:  # @android:color/white — a framework resource, not ours
                    continue
                resource_type, _, name = body.partition("/")
                if not name:
                    continue
                if resource_type == "color" and re.fullmatch(r"[0-9A-Fa-f]{6,8}", name):
                    continue  # inline literal, e.g. @color/#ff0000 style — not a reference
                known = resources.get(resource_type, set())
                if name not in known:
                    problems.append(
                        f"{path.relative_to(ROOT)}: {value} does not resolve "
                        f"(no {resource_type} resource named {name})"
                    )
    return problems


# ---------------------------------------------------------------------------------------------
# Kotlin: resource references + structural balance
# ---------------------------------------------------------------------------------------------

RESOURCE_REFERENCE = re.compile(r"\bR\.(string|drawable|color|dimen|plurals|array|mipmap|layout|xml|raw)\.([A-Za-z0-9_]+)")


def strip_kotlin_noise(source: str) -> str:
    """Removes comments and string contents so bracket counting cannot be fooled by them."""
    out = []
    index = 0
    length = len(source)
    while index < length:
        char = source[index]
        nxt = source[index + 1] if index + 1 < length else ""
        if char == "/" and nxt == "/":
            while index < length and source[index] != "\n":
                index += 1
        elif char == "/" and nxt == "*":
            depth = 1
            index += 2
            while index < length and depth:
                if source[index : index + 2] == "/*":
                    depth += 1
                    index += 2
                elif source[index : index + 2] == "*/":
                    depth -= 1
                    index += 2
                else:
                    index += 1
        elif source[index : index + 3] == '"""':
            index += 3
            while index < length and source[index : index + 3] != '"""':
                index += 1
            index += 3
            out.append('""')
        elif char == '"':
            index += 1
            while index < length and source[index] != '"':
                if source[index] == "\\":
                    index += 1
                index += 1
            index += 1
            out.append('""')
        elif char == "'":
            index += 1
            while index < length and source[index] != "'":
                if source[index] == "\\":
                    index += 1
                index += 1
            index += 1
            out.append("''")
        else:
            out.append(char)
            index += 1
    return "".join(out)


def check_kotlin(resources: dict[str, set[str]]) -> tuple[list[str], list[str]]:
    problems = []
    icons = set()
    for root in KOTLIN_ROOTS:
        for path in sorted(root.rglob("*.kt")):
            source = path.read_text(encoding="utf-8")
            relative = path.relative_to(ROOT)
            for resource_type, name in RESOURCE_REFERENCE.findall(source):
                if name not in resources.get(resource_type, set()):
                    problems.append(f"{relative}: R.{resource_type}.{name} does not exist")
            stripped = strip_kotlin_noise(source)
            for opener, closer in (("(", ")"), ("{", "}"), ("[", "]")):
                opens = stripped.count(opener)
                closes = stripped.count(closer)
                if opens != closes:
                    problems.append(
                        f"{relative}: unbalanced {opener}{closer} — {opens} open, {closes} close"
                    )
            icons.update(re.findall(r"Icons\.(?:AutoMirrored\.)?(?:Filled|Outlined|Rounded|Sharp|TwoTone)\.([A-Za-z0-9_]+)", source))
    return problems, sorted(icons)


def main() -> int:
    if not RES.exists():
        print(f"missing resource directory: {RES}", file=sys.stderr)
        return 2

    resources = collect_resources()
    problems = check_xml(resources)

    kotlin_problems, icons = check_kotlin(resources)
    problems += kotlin_problems

    english, french = string_tables()
    missing_fr = sorted(english - french)
    missing_en = sorted(french - english)
    if missing_fr:
        problems.append("values-fr/strings.xml is missing: " + ", ".join(missing_fr))
    if missing_en:
        problems.append("values/strings.xml is missing (present in French only): " + ", ".join(missing_en))

    total_types = len(resources)
    total_names = sum(len(names) for names in resources.values())
    print(f"resources: {total_names} names across {total_types} types")
    print(f"strings: {len(english)} en / {len(french)} fr")
    print(f"material icons referenced: {len(icons)} distinct")

    if problems:
        print(f"\n{len(problems)} problem(s):", file=sys.stderr)
        for problem in problems:
            print("  " + problem, file=sys.stderr)
        return 1
    print("OK — resources resolve, XML and vector paths parse, Kotlin sources are balanced")
    return 0


if __name__ == "__main__":
    sys.exit(main())
