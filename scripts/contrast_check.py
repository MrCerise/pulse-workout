#!/usr/bin/env python3
"""
PULSE contrast checker (v1.4 "Graphite" design system).

Parses the palette out of the app's own resources (`app/src/main/res/values/colors.xml`), verifies
that the Compose mirror in `ui/theme/Palette.kt` declares exactly the same values, and computes
WCAG 2.1 contrast ratios for the pairs the interface actually renders.

It exits non-zero when a text pair falls below 4.5:1 or a meaningful non-text pair (control
boundary, phase marker, ring) below 3:1, and it always writes `docs/CONTRAST.md` so the numbers can
be reviewed without rerunning anything.

Usage (from the repository root):

    python3 scripts/contrast_check.py
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
COLORS_XML = ROOT / "app/src/main/res/values/colors.xml"
PALETTE_KT = ROOT / "app/src/main/java/com/pulse/intervalcoach/ui/theme/Palette.kt"
OUT = ROOT / "docs/CONTRAST.md"

TEXT_MIN = 4.5
LARGE_TEXT_MIN = 3.0  # >= 18 pt, or >= 14 pt bold
NON_TEXT_MIN = 3.0

# Tokens that must exist before anything is measured.
REQUIRED_TOKENS = [
    # Neutral ramp — dark
    "pulse_canvas", "pulse_nav", "pulse_surface", "pulse_surface_raised",
    "pulse_surface_overlay", "pulse_surface_hover", "pulse_border",
    "pulse_text_primary", "pulse_text_secondary", "pulse_text_muted", "pulse_text_disabled",
    # Neutral ramp — light
    "pulse_light_canvas", "pulse_light_nav", "pulse_light_surface", "pulse_light_surface_raised",
    "pulse_light_surface_overlay", "pulse_light_surface_hover", "pulse_light_border",
    "pulse_light_text_primary", "pulse_light_text_secondary", "pulse_light_text_muted",
    "pulse_light_text_disabled",
    # Neutral ramp — OLED
    "pulse_black_canvas", "pulse_black_surface", "pulse_black_surface_raised",
    "pulse_black_surface_overlay", "pulse_black_border",
    # Control boundaries and accent
    "pulse_outline", "pulse_light_outline", "pulse_accent", "pulse_light_accent",
    "pulse_accent_tint", "pulse_light_accent_tint",
    # Phases and their pre-composited tints
    "pulse_work", "pulse_rest", "pulse_prepare", "pulse_cooldown", "pulse_destructive",
    "pulse_work_tint", "pulse_rest_tint", "pulse_prepare_tint", "pulse_cooldown_tint",
    "pulse_destructive_tint", "pulse_neutral_fill",
    "pulse_light_work", "pulse_light_rest", "pulse_light_prepare", "pulse_light_cooldown",
    "pulse_light_destructive",
    "pulse_light_work_tint", "pulse_light_rest_tint", "pulse_light_prepare_tint",
    "pulse_light_cooldown_tint", "pulse_light_destructive_tint", "pulse_light_neutral_fill",
    # Solid action surfaces
    "pulse_action_fill", "pulse_action_text",
    "pulse_light_action_fill", "pulse_light_action_text",
    "pulse_on_accent", "pulse_light_on_accent",
]

# Compose mirrors: Kotlin token name -> colour resource name. The two must agree exactly, so a
# token cannot drift between XML and Compose unnoticed.
KOTLIN_MIRROR = {
    "Canvas": "pulse_canvas",
    "Nav": "pulse_nav",
    "Surface": "pulse_surface",
    "SurfaceRaised": "pulse_surface_raised",
    "SurfaceOverlay": "pulse_surface_overlay",
    "SurfaceHover": "pulse_surface_hover",
    "Border": "pulse_border",
    "TextPrimary": "pulse_text_primary",
    "TextSecondary": "pulse_text_secondary",
    "TextMuted": "pulse_text_muted",
    "TextDisabled": "pulse_text_disabled",
    "LightCanvas": "pulse_light_canvas",
    "LightNav": "pulse_light_nav",
    "LightSurface": "pulse_light_surface",
    "LightSurfaceRaised": "pulse_light_surface_raised",
    "LightSurfaceOverlay": "pulse_light_surface_overlay",
    "LightSurfaceHover": "pulse_light_surface_hover",
    "LightBorder": "pulse_light_border",
    "LightTextPrimary": "pulse_light_text_primary",
    "LightTextSecondary": "pulse_light_text_secondary",
    "LightTextMuted": "pulse_light_text_muted",
    "LightTextDisabled": "pulse_light_text_disabled",
    "BlackCanvas": "pulse_black_canvas",
    "BlackNav": "pulse_black_nav",
    "BlackSurface": "pulse_black_surface",
    "BlackSurfaceRaised": "pulse_black_surface_raised",
    "BlackSurfaceOverlay": "pulse_black_surface_overlay",
    "BlackSurfaceHover": "pulse_black_surface_hover",
    "BlackBorder": "pulse_black_border",
    "Outline": "pulse_outline",
    "LightOutline": "pulse_light_outline",
    "Accent": "pulse_accent",
    "LightAccent": "pulse_light_accent",
    "AccentTint": "pulse_accent_tint",
    "LightAccentTint": "pulse_light_accent_tint",
    "Work": "pulse_work",
    "Rest": "pulse_rest",
    "Prepare": "pulse_prepare",
    "Cooldown": "pulse_cooldown",
    "Destructive": "pulse_destructive",
    "WorkTint": "pulse_work_tint",
    "RestTint": "pulse_rest_tint",
    "PrepareTint": "pulse_prepare_tint",
    "CooldownTint": "pulse_cooldown_tint",
    "DestructiveTint": "pulse_destructive_tint",
    "NeutralFill": "pulse_neutral_fill",
    "LightWork": "pulse_light_work",
    "LightRest": "pulse_light_rest",
    "LightPrepare": "pulse_light_prepare",
    "LightCooldown": "pulse_light_cooldown",
    "LightDestructive": "pulse_light_destructive",
    "LightWorkTint": "pulse_light_work_tint",
    "LightRestTint": "pulse_light_rest_tint",
    "LightPrepareTint": "pulse_light_prepare_tint",
    "LightCooldownTint": "pulse_light_cooldown_tint",
    "LightDestructiveTint": "pulse_light_destructive_tint",
    "LightNeutralFill": "pulse_light_neutral_fill",
    "ActionFill": "pulse_action_fill",
    "ActionText": "pulse_action_text",
    "LightActionFill": "pulse_light_action_fill",
    "LightActionText": "pulse_light_action_text",
    "OnAccent": "pulse_on_accent",
    "LightOnAccent": "pulse_light_on_accent",
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
        for m in re.finditer(
            r"val\s+([A-Za-z][A-Za-z0-9]*)\s*=\s*Color\(0xFF([0-9A-Fa-f]{6})\)", text
        )
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
    ("Primary text on canvas", "pulse_text_primary", "pulse_canvas", TEXT_MIN, "titles and body copy"),
    ("Primary text on card", "pulse_text_primary", "pulse_surface", TEXT_MIN, "workout cards, list rows"),
    ("Primary text on raised", "pulse_text_primary", "pulse_surface_raised", TEXT_MIN, "inputs, stat tiles"),
    ("Primary text on overlay", "pulse_text_primary", "pulse_surface_overlay", TEXT_MIN, "dialogs, menus, tooltips"),
    ("Primary text on nav bar", "pulse_text_primary", "pulse_nav", TEXT_MIN, "the bottom navigation bar"),
    ("Secondary text on canvas", "pulse_text_secondary", "pulse_canvas", TEXT_MIN, "captions and hints"),
    ("Secondary text on card", "pulse_text_secondary", "pulse_surface", TEXT_MIN, "card captions and metadata"),
    ("Secondary text on raised", "pulse_text_secondary", "pulse_surface_raised", TEXT_MIN, "input placeholders"),
    ("Secondary text on overlay", "pulse_text_secondary", "pulse_surface_overlay", TEXT_MIN, "dialog body copy"),
    ("Muted text on canvas", "pulse_text_muted", "pulse_canvas", TEXT_MIN, "section labels, timestamps"),
    ("Muted text on card", "pulse_text_muted", "pulse_surface", TEXT_MIN, "timeline row captions"),
    ("Muted text on raised", "pulse_text_muted", "pulse_surface_raised", TEXT_MIN, "stat tile labels"),
    ("Control outline on canvas", "pulse_outline", "pulse_canvas", NON_TEXT_MIN, "input borders, focus rings"),
    ("Control outline on card", "pulse_outline", "pulse_surface", NON_TEXT_MIN, "card control boundaries"),
    ("Accent on canvas", "pulse_accent", "pulse_canvas", TEXT_MIN, "links and inline actions"),
    ("Accent on card", "pulse_accent", "pulse_surface", TEXT_MIN, "selected rows, activity labels"),
    ("Accent on overlay", "pulse_accent", "pulse_surface_overlay", TEXT_MIN, "menu emphasis"),
    ("Work accent on canvas", "pulse_work", "pulse_canvas", TEXT_MIN, "work phase label, chart bars"),
    ("Work accent on card", "pulse_work", "pulse_surface", TEXT_MIN, "work phase label on a card"),
    ("Rest accent on card", "pulse_rest", "pulse_surface", TEXT_MIN, "rest phase label"),
    ("Prepare accent on card", "pulse_prepare", "pulse_surface", TEXT_MIN, "preparation phase label"),
    ("Cooldown accent on card", "pulse_cooldown", "pulse_surface", TEXT_MIN, "cool-down phase label"),
    ("Destructive on canvas", "pulse_destructive", "pulse_canvas", TEXT_MIN, "destructive labels"),
    ("Destructive on card", "pulse_destructive", "pulse_surface", TEXT_MIN, "delete actions in rows"),
    ("Work marker on canvas", "pulse_work", "pulse_canvas", NON_TEXT_MIN, "timeline segments, progress ring"),
    ("Rest marker on canvas", "pulse_rest", "pulse_canvas", NON_TEXT_MIN, "timeline segments"),
    ("Prepare marker on canvas", "pulse_prepare", "pulse_canvas", NON_TEXT_MIN, "timeline segments"),
    ("Cooldown marker on canvas", "pulse_cooldown", "pulse_canvas", NON_TEXT_MIN, "timeline segments"),
    # Phase chips are a tinted pill: accent text on the pre-composited tint, not on a raw accent fill.
    ("Work chip label", "pulse_work", "pulse_work_tint", TEXT_MIN, "phase chips, interval tiles"),
    ("Rest chip label", "pulse_rest", "pulse_rest_tint", TEXT_MIN, "phase chips"),
    ("Prepare chip label", "pulse_prepare", "pulse_prepare_tint", TEXT_MIN, "phase chips"),
    ("Cooldown chip label", "pulse_cooldown", "pulse_cooldown_tint", TEXT_MIN, "phase chips"),
    ("Destructive chip label", "pulse_destructive", "pulse_destructive_tint", TEXT_MIN, "invalid import, error banners"),
    ("Accent chip label", "pulse_accent", "pulse_accent_tint", TEXT_MIN, "selected filters, info badges"),
    # Primary buttons are solid: light fill with ink label (dark theme) and the reverse in light.
    ("Primary button label", "pulse_action_text", "pulse_action_fill", TEXT_MIN, "the solid primary action"),
    ("Neutral chip label", "pulse_text_primary", "pulse_neutral_fill", TEXT_MIN, "type badges, neutral metadata"),
]

TRUE_BLACK_PAIRS = [
    ("Primary text on OLED canvas", "pulse_text_primary", "pulse_black_canvas", TEXT_MIN, "true black theme"),
    ("Secondary text on OLED canvas", "pulse_text_secondary", "pulse_black_canvas", TEXT_MIN, "true black theme"),
    ("Muted text on OLED canvas", "pulse_text_muted", "pulse_black_canvas", TEXT_MIN, "true black theme"),
    ("Control outline on OLED canvas", "pulse_outline", "pulse_black_canvas", NON_TEXT_MIN, "true black controls"),
    ("Primary text on OLED card", "pulse_text_primary", "pulse_black_surface", TEXT_MIN, "true black cards"),
    ("Secondary text on OLED card", "pulse_text_secondary", "pulse_black_surface", TEXT_MIN, "true black card copy"),
    ("Primary text on OLED overlay", "pulse_text_primary", "pulse_black_surface_overlay", TEXT_MIN, "true black dialogs"),
    ("Secondary text on OLED overlay", "pulse_text_secondary", "pulse_black_surface_overlay", TEXT_MIN, "true black dialogs"),
    ("Work accent on OLED canvas", "pulse_work", "pulse_black_canvas", TEXT_MIN, "true black phase labels"),
    ("Rest accent on OLED canvas", "pulse_rest", "pulse_black_canvas", TEXT_MIN, "true black phase labels"),
    ("Destructive on OLED canvas", "pulse_destructive", "pulse_black_canvas", TEXT_MIN, "true black destructive"),
]

LIGHT_PAIRS = [
    ("Primary text on canvas", "pulse_light_text_primary", "pulse_light_canvas", TEXT_MIN, "titles and body copy"),
    ("Primary text on card", "pulse_light_text_primary", "pulse_light_surface", TEXT_MIN, "cards and list rows"),
    ("Primary text on raised", "pulse_light_text_primary", "pulse_light_surface_raised", TEXT_MIN, "inputs, stat tiles"),
    ("Primary text on overlay", "pulse_light_text_primary", "pulse_light_surface_overlay", TEXT_MIN, "dialogs and menus"),
    ("Primary text on nav bar", "pulse_light_text_primary", "pulse_light_nav", TEXT_MIN, "the bottom navigation bar"),
    ("Secondary text on canvas", "pulse_light_text_secondary", "pulse_light_canvas", TEXT_MIN, "captions and hints"),
    ("Secondary text on card", "pulse_light_text_secondary", "pulse_light_surface", TEXT_MIN, "card captions"),
    ("Secondary text on raised", "pulse_light_text_secondary", "pulse_light_surface_raised", TEXT_MIN, "input placeholders"),
    ("Muted text on canvas", "pulse_light_text_muted", "pulse_light_canvas", TEXT_MIN, "section labels, timestamps"),
    ("Muted text on card", "pulse_light_text_muted", "pulse_light_surface", TEXT_MIN, "timeline row captions"),
    ("Muted text on raised", "pulse_light_text_muted", "pulse_light_surface_raised", TEXT_MIN, "stat tile labels"),
    ("Control outline on canvas", "pulse_light_outline", "pulse_light_canvas", NON_TEXT_MIN, "input borders, focus rings"),
    ("Control outline on card", "pulse_light_outline", "pulse_light_surface", NON_TEXT_MIN, "card control boundaries"),
    ("Accent on canvas", "pulse_light_accent", "pulse_light_canvas", TEXT_MIN, "links and inline actions"),
    ("Accent on card", "pulse_light_accent", "pulse_light_surface", TEXT_MIN, "selected rows, activity labels"),
    ("Work accent on canvas", "pulse_light_work", "pulse_light_canvas", TEXT_MIN, "work phase label"),
    ("Work accent on card", "pulse_light_work", "pulse_light_surface", TEXT_MIN, "work phase label on a card"),
    ("Rest accent on card", "pulse_light_rest", "pulse_light_surface", TEXT_MIN, "rest phase label"),
    ("Prepare accent on card", "pulse_light_prepare", "pulse_light_surface", TEXT_MIN, "preparation phase label"),
    ("Cooldown accent on card", "pulse_light_cooldown", "pulse_light_surface", TEXT_MIN, "cool-down phase label"),
    ("Destructive on canvas", "pulse_light_destructive", "pulse_light_canvas", TEXT_MIN, "destructive labels"),
    ("Destructive on card", "pulse_light_destructive", "pulse_light_surface", TEXT_MIN, "delete actions in rows"),
    ("Work marker on canvas", "pulse_light_work", "pulse_light_canvas", NON_TEXT_MIN, "timeline segments, progress ring"),
    ("Rest marker on canvas", "pulse_light_rest", "pulse_light_canvas", NON_TEXT_MIN, "timeline segments"),
    ("Prepare marker on canvas", "pulse_light_prepare", "pulse_light_canvas", NON_TEXT_MIN, "timeline segments"),
    ("Cooldown marker on canvas", "pulse_light_cooldown", "pulse_light_canvas", NON_TEXT_MIN, "timeline segments"),
    ("Work chip label", "pulse_light_work", "pulse_light_work_tint", TEXT_MIN, "phase chips, interval tiles"),
    ("Rest chip label", "pulse_light_rest", "pulse_light_rest_tint", TEXT_MIN, "phase chips"),
    ("Prepare chip label", "pulse_light_prepare", "pulse_light_prepare_tint", TEXT_MIN, "phase chips"),
    ("Cooldown chip label", "pulse_light_cooldown", "pulse_light_cooldown_tint", TEXT_MIN, "phase chips"),
    ("Destructive chip label", "pulse_light_destructive", "pulse_light_destructive_tint", TEXT_MIN, "error banners"),
    ("Accent chip label", "pulse_light_accent", "pulse_light_accent_tint", TEXT_MIN, "selected filters, info badges"),
    ("Primary button label", "pulse_light_action_text", "pulse_light_action_fill", TEXT_MIN, "the solid primary action"),
    ("Neutral chip label", "pulse_light_text_primary", "pulse_light_neutral_fill", TEXT_MIN, "type badges, neutral metadata"),
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
    missing_file = [p for p in (COLORS_XML, PALETTE_KT) if not p.exists()]
    if missing_file:
        print("missing: " + ", ".join(str(p) for p in missing_file), file=sys.stderr)
        return 2

    colors = parse_colors(COLORS_XML)
    kotlin = parse_kotlin_palette(PALETTE_KT)

    problems: list[str] = []
    for token in REQUIRED_TOKENS:
        if token not in colors:
            problems.append(f"colors.xml is missing {token}")
    for kotlin_name, token in KOTLIN_MIRROR.items():
        if kotlin_name not in kotlin:
            problems.append(f"Palette.kt is missing PulsePalette.{kotlin_name}")
        elif token in colors and kotlin[kotlin_name].upper() != colors[token].upper():
            problems.append(
                f"token drift: PulsePalette.{kotlin_name}={kotlin[kotlin_name]} but "
                f"{token}={colors[token]} in colors.xml"
            )
    if problems:
        print("\n".join(problems), file=sys.stderr)
        return 2

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
        "`app/src/main/java/com/pulse/intervalcoach/ui/theme/Palette.kt`.",
        "",
        "Thresholds are WCAG 2.1 AA: **4.5:1** for body text, **3:1** for large text (>= 18 pt, or",
        ">= 14 pt bold) and for non-text UI such as timeline segments, progress rings and control",
        "boundaries (WCAG 1.4.11).",
        "",
        f"**Result: {'PASS' if not failures else 'FAIL'}** — {total} pairs measured across three themes.",
        "",
        "Two border tokens exist on purpose: `pulse_border` is a decorative hairline between",
        "surfaces and is *not* contrast-constrained, while `pulse_outline` marks a meaningful control",
        "boundary (input, focus ring, toggle) and is measured at 3:1. Phase chips are tinted pills,",
        "so their label contrast is measured against the pre-composited tint value that ships, not",
        "against a hypothetical alpha blend.",
        "",
        "The high-contrast switch in Settings replaces the phase accents and the control outline with",
        "brighter variants (`highContrast` in `PulseColors`); those variants are strictly lighter than",
        "the pairs measured here and text moves to pure white / pure black.",
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
        "- The chrome is monochrome: navigation, buttons, borders and text use the neutral ramp only,",
        "  so colour always means something. Anything chromatic on screen is a phase, a state or the",
        "  single interactive accent.",
        "- Phase colour is never the only signal: every interval also carries a name, a position in the",
        "  session timeline and, in the player, a written status, so the timer stays readable in",
        "  greyscale.",
        "- The primary action is a solid light-on-dark (dark theme) or dark-on-light (light theme)",
        "  surface rather than a brand colour, which is why `pulse_action_fill` / `pulse_action_text`",
        "  are measured as a pair.",
        "- Timer digits are set in the platform monospace face with tabular figures, so a changing",
        "  countdown never reflows the layout.",
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
