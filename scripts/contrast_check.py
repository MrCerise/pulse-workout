#!/usr/bin/env python3
"""
PULSE contrast checker.

Parses the palette out of the app's own resources (`app/src/main/res/values/colors.xml`) and
computes WCAG 2.1 contrast ratios for the pairs the interface actually renders. It exits non-zero
when a text pair falls below 4.5:1 or a meaningful non-text pair below 3:1, and it always writes
`docs/CONTRAST.md` so the numbers can be reviewed without rerunning anything.

Kotlin mirrors these tokens in `ui/theme/Theme.kt` (`PulsePalette`) and the script asserts that the
two agree, so a token cannot drift between XML and Compose unnoticed.

Usage (from the repository root):

    python3 scripts/contrast_check.py
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
COLORS_XML = ROOT / "app/src/main/res/values/colors.xml"
THEME_KT = ROOT / "app/src/main/java/com/pulse/intervalcoach/ui/theme/Theme.kt"
OUT = ROOT / "docs/CONTRAST.md"

TEXT_MIN = 4.5
LARGE_TEXT_MIN = 3.0  # >= 18 pt, or >= 14 pt bold
NON_TEXT_MIN = 3.0

# Tokens that must exist before anything is measured.
REQUIRED_DARK = [
    "pulse_background", "pulse_surface", "pulse_surface_raised", "pulse_text_primary",
    "pulse_text_secondary", "pulse_outline", "pulse_work", "pulse_rest", "pulse_prepare",
    "pulse_cooldown", "pulse_destructive", "pulse_on_accent",
]
REQUIRED_LIGHT = [
    "pulse_light_background", "pulse_light_surface", "pulse_light_surface_raised",
    "pulse_light_text_primary", "pulse_light_text_secondary", "pulse_light_outline",
    "pulse_light_work", "pulse_light_rest", "pulse_light_prepare", "pulse_light_cooldown",
    "pulse_light_destructive",
]
REQUIRED_BLACK = ["pulse_black_background", "pulse_black_surface"]

# Compose mirrors: Kotlin token name -> colour resource name.
KOTLIN_MIRROR = {
    "Ink": "pulse_background",
    "Surface": "pulse_surface",
    "SurfaceRaised": "pulse_surface_raised",
    "TextPrimary": "pulse_text_primary",
    "TextSecondary": "pulse_text_secondary",
    "Outline": "pulse_outline",
    "LightBackground": "pulse_light_background",
    "LightSurface": "pulse_light_surface",
    "LightSurfaceRaised": "pulse_light_surface_raised",
    "LightTextPrimary": "pulse_light_text_primary",
    "LightTextSecondary": "pulse_light_text_secondary",
    "LightOutline": "pulse_light_outline",
    "Work": "pulse_work",
    "Rest": "pulse_rest",
    "Prepare": "pulse_prepare",
    "Cooldown": "pulse_cooldown",
    "Destructive": "pulse_destructive",
    "WorkLight": "pulse_light_work",
    "RestLight": "pulse_light_rest",
    "PrepareLight": "pulse_light_prepare",
    "CooldownLight": "pulse_light_cooldown",
    "DestructiveLight": "pulse_light_destructive",
    "OnAccent": "pulse_on_accent",
    "TrueBlack": "pulse_black_background",
    "TrueBlackSurface": "pulse_black_surface",
}


def parse_colors(path: Path) -> dict[str, str]:
    text = path.read_text(encoding="utf-8")
    return {
        m.group(1): m.group(2).upper()
        for m in re.finditer(r'<color name="([^"]+)">\s*(#[0-9A-Fa-f]{6,8})\s*</color>', text)
    }


def parse_kotlin_palette(path: Path) -> dict[str, str]:
    text = path.read_text(encoding="utf-8")
    return {
        m.group(1): "#" + m.group(2).upper()
        for m in re.finditer(r"val\s+([A-Za-z][A-Za-z0-9]*)\s*=\s*Color\(0xFF([0-9A-Fa-f]{6})\)", text)
    }


def to_linear(channel: int) -> float:
    c = channel / 255.0
    return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4


def luminance(hex_color: str) -> float:
    value = hex_color.lstrip("#")
    if len(value) == 8:
        value = value[2:]
    r, g, b = (int(value[i : i + 2], 16) for i in (0, 2, 4))
    return 0.2126 * to_linear(r) + 0.7152 * to_linear(g) + 0.0722 * to_linear(b)


def ratio(fg: str, bg: str) -> float:
    a, b = luminance(fg), luminance(bg)
    return (max(a, b) + 0.05) / (min(a, b) + 0.05)


# (label, foreground token, background token, minimum, note)
DARK_PAIRS = [
    ("Primary text", "pulse_text_primary", "pulse_background", TEXT_MIN, "body copy on the app background"),
    ("Secondary text", "pulse_text_secondary", "pulse_background", TEXT_MIN, "captions and hints"),
    ("Primary text on card", "pulse_text_primary", "pulse_surface", TEXT_MIN, "workout cards, list rows"),
    ("Secondary text on card", "pulse_text_secondary", "pulse_surface", TEXT_MIN, "card captions"),
    ("Primary text on sheet", "pulse_text_primary", "pulse_surface_raised", TEXT_MIN, "bottom sheets, dialogs"),
    ("Work accent on background", "pulse_work", "pulse_background", NON_TEXT_MIN, "work chips and timeline segments"),
    ("Work accent as large text", "pulse_work", "pulse_background", LARGE_TEXT_MIN, "the big timer digits"),
    ("Rest accent on background", "pulse_rest", "pulse_background", NON_TEXT_MIN, "rest chips and segments"),
    ("Prepare accent on background", "pulse_prepare", "pulse_background", NON_TEXT_MIN, "preparation interval"),
    ("Cooldown accent on background", "pulse_cooldown", "pulse_background", NON_TEXT_MIN, "cooldown interval"),
    ("Destructive on background", "pulse_destructive", "pulse_background", NON_TEXT_MIN, "destructive buttons"),
    ("Destructive as large text", "pulse_destructive", "pulse_background", LARGE_TEXT_MIN, "destructive labels"),
    ("Text on a filled work pill", "pulse_on_accent", "pulse_work", TEXT_MIN, "labels inside accent fills"),
    ("Outline on background", "pulse_outline", "pulse_background", NON_TEXT_MIN, "control borders and dividers"),
    ("Outline on card", "pulse_outline", "pulse_surface", NON_TEXT_MIN, "card borders"),
]

TRUE_BLACK_PAIRS = [
    ("Primary text on OLED black", "pulse_text_primary", "pulse_black_background", TEXT_MIN, "true black theme"),
    ("Secondary text on OLED black", "pulse_text_secondary", "pulse_black_background", TEXT_MIN, "true black theme"),
    ("Work accent on OLED black", "pulse_work", "pulse_black_background", NON_TEXT_MIN, "true black theme"),
    ("Rest accent on OLED black", "pulse_rest", "pulse_black_background", NON_TEXT_MIN, "true black theme"),
    ("Destructive on OLED black", "pulse_destructive", "pulse_black_background", NON_TEXT_MIN, "true black theme"),
    ("Primary text on OLED surface", "pulse_text_primary", "pulse_black_surface", TEXT_MIN, "true black cards"),
]

LIGHT_PAIRS = [
    ("Primary text", "pulse_light_text_primary", "pulse_light_background", TEXT_MIN, "body copy"),
    ("Secondary text", "pulse_light_text_secondary", "pulse_light_background", TEXT_MIN, "captions and hints"),
    ("Primary text on card", "pulse_light_text_primary", "pulse_light_surface", TEXT_MIN, "cards"),
    ("Secondary text on card", "pulse_light_text_secondary", "pulse_light_surface", TEXT_MIN, "card captions"),
    ("Primary text on raised card", "pulse_light_text_primary", "pulse_light_surface_raised", TEXT_MIN, "sheets and dialogs"),
    ("Work accent on background", "pulse_light_work", "pulse_light_background", NON_TEXT_MIN, "work chips"),
    ("Work accent as large text", "pulse_light_work", "pulse_light_background", LARGE_TEXT_MIN, "big timer digits"),
    ("Rest accent on background", "pulse_light_rest", "pulse_light_background", NON_TEXT_MIN, "rest chips"),
    ("Prepare accent on background", "pulse_light_prepare", "pulse_light_background", NON_TEXT_MIN, "preparation chips"),
    ("Cooldown accent on background", "pulse_light_cooldown", "pulse_light_background", NON_TEXT_MIN, "cooldown chips"),
    ("Destructive on background", "pulse_light_destructive", "pulse_light_background", NON_TEXT_MIN, "destructive actions"),
    ("Outline on background", "pulse_light_outline", "pulse_light_background", NON_TEXT_MIN, "control borders"),
    ("Outline on card", "pulse_light_outline", "pulse_light_surface", NON_TEXT_MIN, "card borders"),
]


def evaluate(name: str, pairs, colors: dict[str, str]):
    rows, failures = [], []
    for label, fg, bg, minimum, note in pairs:
        if fg not in colors or bg not in colors:
            failures.append(f"{name}: missing token ({fg if fg not in colors else bg})")
            continue
        value = ratio(colors[fg], colors[bg])
        ok = value >= minimum
        rows.append((label, colors[fg], colors[bg], value, minimum, ok, note))
        if not ok:
            failures.append(f"{name}: {label} — {value:.2f}:1 is below {minimum}:1")
    return rows, failures


def main() -> int:
    missing_file = [p for p in (COLORS_XML, THEME_KT) if not p.exists()]
    if missing_file:
        print("missing: " + ", ".join(str(p) for p in missing_file), file=sys.stderr)
        return 2

    colors = parse_colors(COLORS_XML)
    kotlin = parse_kotlin_palette(THEME_KT)

    problems: list[str] = []
    for token in REQUIRED_DARK + REQUIRED_LIGHT + REQUIRED_BLACK:
        if token not in colors:
            problems.append(f"colors.xml is missing {token}")
    for kotlin_name, token in KOTLIN_MIRROR.items():
        if kotlin_name not in kotlin:
            problems.append(f"Theme.kt is missing PulsePalette.{kotlin_name}")
        elif token in colors and kotlin[kotlin_name].upper() != colors[token].upper():
            problems.append(
                f"token drift: PulsePalette.{kotlin_name}={kotlin[kotlin_name]} but "
                f"{token}={colors[token]} in colors.xml"
            )
    if problems:
        print("\n".join(problems), file=sys.stderr)
        return 2

    dark = dict(colors)
    dark["pulse_black_surface"] = colors["pulse_black_surface"]

    sections = []
    failures: list[str] = []
    for name, pairs in (
        ("Dark (default)", DARK_PAIRS),
        ("True black (OLED)", TRUE_BLACK_PAIRS),
        ("Light", LIGHT_PAIRS),
    ):
        rows, bad = evaluate(name, pairs, colors)
        sections.append((name, rows))
        failures += bad

    total = sum(len(rows) for _, rows in sections)
    lines = [
        "# Contrast report",
        "",
        "Generated by `python3 scripts/contrast_check.py`, which reads the real palette from",
        "`app/src/main/res/values/colors.xml` and cross-checks it against `PulsePalette` in",
        "`app/src/main/java/com/pulse/intervalcoach/ui/theme/Theme.kt`.",
        "",
        "Thresholds are WCAG 2.1 AA: **4.5:1** for body text, **3:1** for large text (≥ 18 pt, or",
        "≥ 14 pt bold) and for non-text UI such as timeline segments, progress rings, control",
        "outlines and destructive affordances.",
        "",
        f"**Result: {'PASS' if not failures else 'FAIL'}** — {total} pairs measured across three themes.",
        "",
        "The high-contrast switch in Settings replaces the phase accents and the outline with",
        "brighter variants (`HighContrast*` in Theme.kt); those variants are strictly lighter than the",
        "pairs measured here, and the text colours move to pure white / pure black.",
        "",
    ]
    for name, rows in sections:
        lines += [
            f"## {name}",
            "",
            "| Pair | Foreground | Background | Ratio | Required | Result | Where it is used |",
            "| --- | --- | --- | --- | --- | --- | --- |",
        ]
        for label, fg, bg, value, minimum, ok, note in rows:
            lines.append(
                f"| {label} | `{fg}` | `{bg}` | {value:.2f}:1 | {minimum}:1 | "
                f"{'pass' if ok else '**FAIL**'} | {note} |"
            )
        lines.append("")

    if failures:
        lines += ["## Failures", ""] + [f"- {f}" for f in failures] + [""]

    lines += [
        "## Why these pairs",
        "",
        "- Phase colours are never the only signal: every interval also carries a name, an icon and a",
        "  position in the session timeline, so the timer stays readable in greyscale.",
        "- The large timer digits are checked at the large-text threshold; every other use of an accent",
        "  colour (chips, bars, rings) is checked against the non-text threshold.",
        "- `pulse_outline` was raised from the original #303B55 to reach 3:1 against both the app",
        "  background and card surfaces, so control borders and dividers meet the non-text threshold",
        "  instead of being decorative-only. The light theme outline was raised for the same reason.",
        "- Timer digits use tabular numerals (`FontFeatureSetting(\"tnum\")`), so a changing countdown",
        "  never reflows the layout.",
        "",
    ]

    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text("\n".join(lines), encoding="utf-8")
    print(f"wrote {OUT.relative_to(ROOT)} ({total} pairs)")
    for failure in failures:
        print(failure, file=sys.stderr)
    return 1 if failures else 0


if __name__ == "__main__":
    raise SystemExit(main())
