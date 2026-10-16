#!/usr/bin/env python3
"""
CADENCE contrast checker (v2.0 "Momentum" design system).

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

try:
    from .safe_repo_io import BoundedSourceReader, RepositoryIOError, atomic_write_text
except ImportError:  # Executed as `python3 scripts/contrast_check.py`.
    from safe_repo_io import BoundedSourceReader, RepositoryIOError, atomic_write_text

ROOT = Path(__file__).resolve().parent.parent
SOURCE_READER = BoundedSourceReader(ROOT)
COLORS_XML = ROOT / "app/src/main/res/values/colors.xml"
PALETTE_KT = ROOT / "app/src/main/java/com/pulse/intervalcoach/ui/theme/Palette.kt"
# The Wear APK mirrors the palette by value (an application module cannot share it); the mirror
# must stay byte-identical to the phone's or the watch drifts off the measured theme.
WEAR_PALETTE_KT = ROOT / "wear/src/main/java/com/pulse/intervalcoach/wear/ui/theme/WearPalette.kt"
OUT = ROOT / "docs/CONTRAST.md"

TEXT_MIN = 4.5
LARGE_TEXT_MIN = 3.0  # >= 18 pt, or >= 14 pt bold
NON_TEXT_MIN = 3.0

# Tokens that must exist before anything is measured.
REQUIRED_TOKENS = [
    # Neutral ramp — dark
    "pulse_canvas", "pulse_nav", "pulse_surface", "pulse_surface_raised",
    "pulse_surface_overlay", "pulse_surface_hover", "pulse_surface_sunken",
    "pulse_border", "pulse_border_strong", "pulse_outline", "pulse_track",
    "pulse_text_primary", "pulse_text_secondary", "pulse_text_muted", "pulse_text_disabled",
    # Neutral ramp — light
    "pulse_light_canvas", "pulse_light_nav", "pulse_light_surface", "pulse_light_surface_raised",
    "pulse_light_surface_overlay", "pulse_light_surface_hover", "pulse_light_surface_sunken",
    "pulse_light_border", "pulse_light_border_strong", "pulse_light_track",
    "pulse_light_text_primary", "pulse_light_text_secondary", "pulse_light_text_muted",
    "pulse_light_text_disabled",
    # Neutral ramp — OLED
    "pulse_black_canvas", "pulse_black_nav", "pulse_black_surface", "pulse_black_surface_raised",
    "pulse_black_surface_overlay", "pulse_black_surface_hover", "pulse_black_surface_sunken",
    "pulse_black_border",
    # Control boundaries and accent
    "pulse_outline", "pulse_light_outline",
    "pulse_accent", "pulse_light_accent",
    "pulse_accent_alt", "pulse_light_accent_alt",
    "pulse_accent_tint", "pulse_light_accent_tint",
    # Brand fill, the ink on it, and the ink hero panel
    "pulse_brand", "pulse_on_brand",
    "pulse_light_brand", "pulse_light_on_brand",
    "pulse_hero_surface", "pulse_hero_text", "pulse_hero_muted",
    "pulse_light_hero_surface", "pulse_light_hero_text", "pulse_light_hero_muted",
    # Informational state
    "pulse_info", "pulse_info_tint", "pulse_light_info", "pulse_light_info_tint",
    # Workout category hues and their pre-composited tints
    "pulse_cat_cardio", "pulse_cat_cardio_tint",
    "pulse_cat_conditioning", "pulse_cat_conditioning_tint",
    "pulse_cat_strength", "pulse_cat_strength_tint",
    "pulse_cat_mobility", "pulse_cat_mobility_tint",
    "pulse_cat_mind", "pulse_cat_mind_tint",
    "pulse_cat_tools", "pulse_cat_tools_tint",
    "pulse_light_cat_cardio", "pulse_light_cat_cardio_tint",
    "pulse_light_cat_conditioning", "pulse_light_cat_conditioning_tint",
    "pulse_light_cat_strength", "pulse_light_cat_strength_tint",
    "pulse_light_cat_mobility", "pulse_light_cat_mobility_tint",
    "pulse_light_cat_mind", "pulse_light_cat_mind_tint",
    "pulse_light_cat_tools", "pulse_light_cat_tools_tint",
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
    "SurfaceSunken": "pulse_surface_sunken",
    "Border": "pulse_border",
    "BorderStrong": "pulse_border_strong",
    "TextPrimary": "pulse_text_primary",
    "TextSecondary": "pulse_text_secondary",
    "TextMuted": "pulse_text_muted",
    "TextDisabled": "pulse_text_disabled",
    "Track": "pulse_track",
    "LightCanvas": "pulse_light_canvas",
    "LightNav": "pulse_light_nav",
    "LightSurface": "pulse_light_surface",
    "LightSurfaceRaised": "pulse_light_surface_raised",
    "LightSurfaceOverlay": "pulse_light_surface_overlay",
    "LightSurfaceHover": "pulse_light_surface_hover",
    "LightSurfaceSunken": "pulse_light_surface_sunken",
    "LightBorder": "pulse_light_border",
    "LightBorderStrong": "pulse_light_border_strong",
    "LightTextPrimary": "pulse_light_text_primary",
    "LightTextSecondary": "pulse_light_text_secondary",
    "LightTextMuted": "pulse_light_text_muted",
    "LightTextDisabled": "pulse_light_text_disabled",
    "LightTrack": "pulse_light_track",
    "BlackCanvas": "pulse_black_canvas",
    "BlackNav": "pulse_black_nav",
    "BlackSurface": "pulse_black_surface",
    "BlackSurfaceRaised": "pulse_black_surface_raised",
    "BlackSurfaceOverlay": "pulse_black_surface_overlay",
    "BlackSurfaceHover": "pulse_black_surface_hover",
    "BlackSurfaceSunken": "pulse_black_surface_sunken",
    "BlackBorder": "pulse_black_border",
    "Outline": "pulse_outline",
    "LightOutline": "pulse_light_outline",
    "Accent": "pulse_accent",
    "AccentAlt": "pulse_accent_alt",
    "LightAccent": "pulse_light_accent",
    "LightAccentAlt": "pulse_light_accent_alt",
    "AccentTint": "pulse_accent_tint",
    "LightAccentTint": "pulse_light_accent_tint",
    "Brand": "pulse_brand",
    "OnBrand": "pulse_on_brand",
    "LightBrand": "pulse_light_brand",
    "LightOnBrand": "pulse_light_on_brand",
    "HeroSurface": "pulse_hero_surface",
    "HeroText": "pulse_hero_text",
    "HeroMuted": "pulse_hero_muted",
    "LightHeroSurface": "pulse_light_hero_surface",
    "LightHeroText": "pulse_light_hero_text",
    "LightHeroMuted": "pulse_light_hero_muted",
    "Info": "pulse_info",
    "InfoTint": "pulse_info_tint",
    "LightInfo": "pulse_light_info",
    "LightInfoTint": "pulse_light_info_tint",
    "CatCardio": "pulse_cat_cardio",
    "CatCardioTint": "pulse_cat_cardio_tint",
    "CatConditioning": "pulse_cat_conditioning",
    "CatConditioningTint": "pulse_cat_conditioning_tint",
    "CatStrength": "pulse_cat_strength",
    "CatStrengthTint": "pulse_cat_strength_tint",
    "CatMobility": "pulse_cat_mobility",
    "CatMobilityTint": "pulse_cat_mobility_tint",
    "CatMind": "pulse_cat_mind",
    "CatMindTint": "pulse_cat_mind_tint",
    "CatTools": "pulse_cat_tools",
    "CatToolsTint": "pulse_cat_tools_tint",
    "LightCatCardio": "pulse_light_cat_cardio",
    "LightCatCardioTint": "pulse_light_cat_cardio_tint",
    "LightCatConditioning": "pulse_light_cat_conditioning",
    "LightCatConditioningTint": "pulse_light_cat_conditioning_tint",
    "LightCatStrength": "pulse_light_cat_strength",
    "LightCatStrengthTint": "pulse_light_cat_strength_tint",
    "LightCatMobility": "pulse_light_cat_mobility",
    "LightCatMobilityTint": "pulse_light_cat_mobility_tint",
    "LightCatMind": "pulse_light_cat_mind",
    "LightCatMindTint": "pulse_light_cat_mind_tint",
    "LightCatTools": "pulse_light_cat_tools",
    "LightCatToolsTint": "pulse_light_cat_tools_tint",
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
    text = SOURCE_READER.read_text(path)
    return {
        m.group(1): m.group(2).upper()
        for m in re.finditer(r'<color name="([^"]+)">\s*(#[0-9A-Fa-f]{6,8})\s*</color>', text)
    }


def parse_kotlin_palette(path: Path) -> dict[str, str]:
    text = SOURCE_READER.read_text(path)
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
    ("Primary text on canvas", "pulse_text_primary", "pulse_canvas", TEXT_MIN, "page titles, body copy"),
    ("Primary text on card", "pulse_text_primary", "pulse_surface", TEXT_MIN, "workout cards, list rows"),
    ("Primary text on raised", "pulse_text_primary", "pulse_surface_raised", TEXT_MIN, "inputs, stat tiles"),
    ("Primary text on overlay", "pulse_text_primary", "pulse_surface_overlay", TEXT_MIN, "dialogs, menus, tooltips"),
    ("Primary text on nav", "pulse_text_primary", "pulse_nav", TEXT_MIN, "sidebar and bottom navigation"),
    ("Primary text on sunken", "pulse_text_primary", "pulse_surface_sunken", TEXT_MIN, "table headers, inset wells"),
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
    ("Accent chip label", "pulse_accent", "pulse_accent_tint", TEXT_MIN, "selected filters, active navigation item"),
    ("Informational label", "pulse_info", "pulse_info_tint", TEXT_MIN, "info banners, notes"),
    ("Informational on canvas", "pulse_info", "pulse_canvas", TEXT_MIN, "inline help links"),
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
    # The primary action is a solid brand block with an ink label, in both themes.
    ("Primary button label", "pulse_action_text", "pulse_action_fill", TEXT_MIN, "the solid primary action"),
    ("Neutral chip label", "pulse_text_primary", "pulse_neutral_fill", TEXT_MIN, "type badges, neutral metadata"),
    ("Neutral chip caption", "pulse_text_secondary", "pulse_neutral_fill", TEXT_MIN, "equipment and count pills"),
    # The second accent stop is never a label: it only ever sits behind or beside the accent, so it
    # is measured as a large graphic element rather than as text.
    ("Accent partner on canvas", "pulse_accent_alt", "pulse_canvas", NON_TEXT_MIN, "bars, sweeps, rank badges"),
    ("Accent partner on card", "pulse_accent_alt", "pulse_surface", NON_TEXT_MIN, "graphic fills inside cards"),
    # The ink hero panel stays dark in every theme, so its text is measured as its own pair.
    ("Hero title on ink panel", "pulse_hero_text", "pulse_hero_surface", TEXT_MIN, "the Today and summary hero"),
    ("Hero caption on ink panel", "pulse_hero_muted", "pulse_hero_surface", TEXT_MIN, "hero supporting copy"),
    # Category chips: one hue per workout family, label drawn on the pre-composited tint.
    ("Cardio category label", "pulse_cat_cardio", "pulse_cat_cardio_tint", TEXT_MIN, "category chips and cards"),
    ("Conditioning category label", "pulse_cat_conditioning", "pulse_cat_conditioning_tint", TEXT_MIN, "category chips and cards"),
    ("Strength category label", "pulse_cat_strength", "pulse_cat_strength_tint", TEXT_MIN, "category chips and cards"),
    ("Mobility category label", "pulse_cat_mobility", "pulse_cat_mobility_tint", TEXT_MIN, "category chips and cards"),
    ("Mind category label", "pulse_cat_mind", "pulse_cat_mind_tint", TEXT_MIN, "category chips and cards"),
    ("Tools category label", "pulse_cat_tools", "pulse_cat_tools_tint", TEXT_MIN, "category chips and cards"),
]

# The OLED theme swaps only the surfaces; the ink ramp above is measured against pure black here.
TRUE_BLACK_PAIRS = [
    ("Primary text on canvas", "pulse_text_primary", "pulse_black_canvas", TEXT_MIN, "page titles on OLED"),
    ("Primary text on card", "pulse_text_primary", "pulse_black_surface", TEXT_MIN, "workout cards"),
    ("Primary text on raised", "pulse_text_primary", "pulse_black_surface_raised", TEXT_MIN, "inputs, stat tiles"),
    ("Primary text on overlay", "pulse_text_primary", "pulse_black_surface_overlay", TEXT_MIN, "dialogs and menus"),
    ("Primary text on nav", "pulse_text_primary", "pulse_black_nav", TEXT_MIN, "navigation chrome"),
    ("Secondary text on card", "pulse_text_secondary", "pulse_black_surface", TEXT_MIN, "card captions"),
    ("Secondary text on raised", "pulse_text_secondary", "pulse_black_surface_raised", TEXT_MIN, "placeholders"),
    ("Muted text on canvas", "pulse_text_muted", "pulse_black_canvas", TEXT_MIN, "section labels"),
    ("Muted text on card", "pulse_text_muted", "pulse_black_surface", TEXT_MIN, "timeline captions"),
    ("Muted text on hover", "pulse_text_muted", "pulse_black_surface_hover", TEXT_MIN, "pressed rows"),
    ("Control outline on canvas", "pulse_outline", "pulse_black_canvas", NON_TEXT_MIN, "input borders, focus rings"),
    ("Control outline on card", "pulse_outline", "pulse_black_surface", NON_TEXT_MIN, "card control boundaries"),
    ("Accent on canvas", "pulse_accent", "pulse_black_canvas", TEXT_MIN, "links and inline actions"),
    ("Accent on card", "pulse_accent", "pulse_black_surface", TEXT_MIN, "selected rows"),
    ("Accent chip label", "pulse_accent", "pulse_accent_tint", TEXT_MIN, "selected filters"),
    ("Primary button label", "pulse_action_text", "pulse_action_fill", TEXT_MIN, "the solid primary action"),
    ("Neutral chip label", "pulse_text_primary", "pulse_neutral_fill", TEXT_MIN, "metadata pills"),
    ("Work chip label", "pulse_work", "pulse_work_tint", TEXT_MIN, "phase chips"),
    ("Rest chip label", "pulse_rest", "pulse_rest_tint", TEXT_MIN, "phase chips"),
    ("Prepare chip label", "pulse_prepare", "pulse_prepare_tint", TEXT_MIN, "phase chips"),
    ("Cooldown chip label", "pulse_cooldown", "pulse_cooldown_tint", TEXT_MIN, "phase chips"),
    ("Destructive chip label", "pulse_destructive", "pulse_destructive_tint", TEXT_MIN, "error banners"),
    ("Cardio category label", "pulse_cat_cardio", "pulse_cat_cardio_tint", TEXT_MIN, "category pills"),
    ("Strength category label", "pulse_cat_strength", "pulse_cat_strength_tint", TEXT_MIN, "category pills"),
    ("Hero title on ink panel", "pulse_hero_text", "pulse_hero_surface", TEXT_MIN, "the Today hero"),
]

LIGHT_PAIRS = [
    ("Primary text on canvas", "pulse_light_text_primary", "pulse_light_canvas", TEXT_MIN, "page titles, body copy"),
    ("Primary text on card", "pulse_light_text_primary", "pulse_light_surface", TEXT_MIN, "workout cards, list rows"),
    ("Primary text on raised", "pulse_light_text_primary", "pulse_light_surface_raised", TEXT_MIN, "inputs, stat tiles"),
    ("Primary text on overlay", "pulse_light_text_primary", "pulse_light_surface_overlay", TEXT_MIN, "dialogs and menus"),
    ("Primary text on nav", "pulse_light_text_primary", "pulse_light_nav", TEXT_MIN, "sidebar and bottom navigation"),
    ("Primary text on sunken", "pulse_light_text_primary", "pulse_light_surface_sunken", TEXT_MIN, "table headers, inset wells"),
    ("Secondary text on canvas", "pulse_light_text_secondary", "pulse_light_canvas", TEXT_MIN, "captions and hints"),
    ("Secondary text on card", "pulse_light_text_secondary", "pulse_light_surface", TEXT_MIN, "card captions"),
    ("Secondary text on raised", "pulse_light_text_secondary", "pulse_light_surface_raised", TEXT_MIN, "input placeholders"),
    ("Muted text on canvas", "pulse_light_text_muted", "pulse_light_canvas", TEXT_MIN, "section labels, timestamps"),
    ("Muted text on card", "pulse_light_text_muted", "pulse_light_surface", TEXT_MIN, "timeline row captions"),
    ("Muted text on raised", "pulse_light_text_muted", "pulse_light_surface_raised", TEXT_MIN, "stat tile labels"),
    ("Muted text on sunken", "pulse_light_text_muted", "pulse_light_surface_sunken", TEXT_MIN, "table headers"),
    ("Control outline on canvas", "pulse_light_outline", "pulse_light_canvas", NON_TEXT_MIN, "input borders, focus rings"),
    ("Control outline on card", "pulse_light_outline", "pulse_light_surface", NON_TEXT_MIN, "card control boundaries"),
    ("Accent on canvas", "pulse_light_accent", "pulse_light_canvas", TEXT_MIN, "links and inline actions"),
    ("Accent on card", "pulse_light_accent", "pulse_light_surface", TEXT_MIN, "selected rows, activity labels"),
    ("Accent on raised", "pulse_light_accent", "pulse_light_surface_raised", TEXT_MIN, "selected list rows"),
    ("Accent chip label", "pulse_light_accent", "pulse_light_accent_tint", TEXT_MIN, "selected filters, active navigation item"),
    ("Informational label", "pulse_light_info", "pulse_light_info_tint", TEXT_MIN, "info banners, notes"),
    ("Informational on canvas", "pulse_light_info", "pulse_light_canvas", TEXT_MIN, "inline help links"),
    ("Primary button label", "pulse_light_action_text", "pulse_light_action_fill", TEXT_MIN, "the solid primary action"),
    ("Hero title on ink panel", "pulse_light_hero_text", "pulse_light_hero_surface", TEXT_MIN, "the Today and summary hero"),
    ("Hero caption on ink panel", "pulse_light_hero_muted", "pulse_light_hero_surface", TEXT_MIN, "hero supporting copy"),
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
    ("Neutral chip label", "pulse_light_text_primary", "pulse_light_neutral_fill", TEXT_MIN, "type badges, neutral metadata"),
    ("Neutral chip caption", "pulse_light_text_secondary", "pulse_light_neutral_fill", TEXT_MIN, "equipment and count pills"),
    ("Accent partner on canvas", "pulse_light_accent_alt", "pulse_light_canvas", NON_TEXT_MIN, "bars, sweeps, rank badges"),
    ("Accent partner on card", "pulse_light_accent_alt", "pulse_light_surface", NON_TEXT_MIN, "graphic fills inside cards"),
    ("Cardio category label", "pulse_light_cat_cardio", "pulse_light_cat_cardio_tint", TEXT_MIN, "category chips and cards"),
    ("Conditioning category label", "pulse_light_cat_conditioning", "pulse_light_cat_conditioning_tint", TEXT_MIN, "category chips and cards"),
    ("Strength category label", "pulse_light_cat_strength", "pulse_light_cat_strength_tint", TEXT_MIN, "category chips and cards"),
    ("Mobility category label", "pulse_light_cat_mobility", "pulse_light_cat_mobility_tint", TEXT_MIN, "category chips and cards"),
    ("Mind category label", "pulse_light_cat_mind", "pulse_light_cat_mind_tint", TEXT_MIN, "category chips and cards"),
    ("Tools category label", "pulse_light_cat_tools", "pulse_light_cat_tools_tint", TEXT_MIN, "category chips and cards"),
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
    missing_file = [p for p in (COLORS_XML, PALETTE_KT, WEAR_PALETTE_KT) if not p.exists()]
    if missing_file:
        print("missing: " + ", ".join(str(p) for p in missing_file), file=sys.stderr)
        return 2

    try:
        colors = parse_colors(COLORS_XML)
        kotlin = parse_kotlin_palette(PALETTE_KT)
        wear = parse_kotlin_palette(WEAR_PALETTE_KT)
    except RepositoryIOError as error:
        print(f"unsafe contrast-check input: {error}", file=sys.stderr)
        return 2

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
    # Wear mirror: every constant the watch ships must be byte-identical to the phone's. The
    # watch may ship a subset (no light theme), never a different value.
    for name, value in wear.items():
        if name not in kotlin:
            problems.append(f"WearPalette.{name} has no same-named constant in the phone Palette.kt")
        elif kotlin[name].upper() != value.upper():
            problems.append(
                f"watch theme drift: WearPalette.{name}={value} but PulsePalette.{name}={kotlin[name]}"
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
        "The Wear OS app mirrors this palette by value in",
        "`wear/src/main/java/com/pulse/intervalcoach/wear/ui/theme/WearPalette.kt` (dark, OLED and",
        "high-contrast ramps only — watches have no light theme). This script asserts that every",
        f"mirrored constant is byte-identical to the phone's `PulsePalette` ({len(wear)} of them), so",
        "the ratios above apply to the watch unchanged.",
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
        "- The chrome is neutral: navigation, borders and text use the neutral ramp only, so colour",
        "  always means something. The one chromatic constant is the brand accent, and it appears in",
        "  exactly four places — the brand mark, the primary action, the selected navigation item and",
        "  the focus ring.",
        "- Phase colour is never the only signal: every interval also carries a name, a dot and a",
        "  position in the session timeline, so the player stays readable in greyscale.",
        "- The ink hero panel (`pulse_hero_surface`) is deliberately dark in *both* themes, so its",
        "  text pair is measured on its own rather than against the canvas.",
        "- Timer digits are set in a tabular face, so a changing countdown never reflows the layout.",
        "",
    ]

    try:
        atomic_write_text(ROOT, OUT.relative_to(ROOT), "\n".join(lines))
    except RepositoryIOError as error:
        print(f"could not safely write contrast report: {error}", file=sys.stderr)
        return 2
    print(f"wrote {OUT.relative_to(ROOT)} ({total} pairs)")
    for failure in failures:
        print(failure, file=sys.stderr)
    return 1 if failures else 0


if __name__ == "__main__":
    raise SystemExit(main())
