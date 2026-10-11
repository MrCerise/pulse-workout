# PULSE design system — "Prism"

Prism is the third generation of the PULSE visual layer and the first built around **categories as
data**. It replaced "Graphite" (near-black chrome, hairline borders, monochrome chrome, small radii,
no gradients) with a system that is louder where it should be — the hero, the primary action, the
running timer — and quieter everywhere else.

## 1. Principles

1. **The gradient is a privilege.** The accent gradient (violet → cyan) appears in exactly three
   places at scale: the Today hero, the session-summary hero, and the primary action. It is never a
   background texture, never a border, never a chart fill. Because it is rare, it still means
   "look here".
2. **Depth is earned.** A card is separated from the canvas by its fill and a hairline. Only chrome
   that genuinely floats — the navigation bar, sheets, dialogs — casts a shadow. One shadow on a
   screen is a signal; six are noise.
3. **Big panels, small pills.** Cards are 24 dp, nested panels 18 dp, controls 14 dp, chips fully
   round. The two silhouettes are deliberately far apart, so a screen reads as a handful of large
   soft surfaces carrying small round pills.
4. **Colour means something.** Interval phases own four hues, workout categories own six families,
   destructive state owns one. Nothing is chromatic for decoration, and every hue ships with the
   pre-composited container tint it is drawn on.
5. **Motion is felt, not watched.** 160 ms for feedback, 260 ms for selection, 380 ms for a sheet —
   and zero when the user asks for reduced motion.

## 2. Colour

Defined once in `app/src/main/res/values/colors.xml` and mirrored value-for-value in
`ui/theme/Palette.kt`. `scripts/contrast_check.py` exits **2** if the two ever disagree (token drift)
and exits **1** if any measured pairing drops below its WCAG 2.1 AA threshold. The current run
measures **97 pairs** across the ink, void and paper themes.

### Neutral ramp — Ink (dark, default)

| Token | Value | Role |
| --- | --- | --- |
| `pulse_canvas` | `#0A0912` | app background |
| `pulse_nav` | `#120F1C` | navigation bar and rail |
| `pulse_surface` | `#171423` | cards, panels |
| `pulse_surface_raised` | `#1F1B2E` | inputs, stat tiles, chips |
| `pulse_surface_overlay` | `#272137` | dialogs, menus, snackbars |
| `pulse_surface_hover` | `#2E2842` | hover and pressed fill |
| `pulse_border` | `#2B2540` | hairline separators (decorative) |
| `pulse_outline` | `#8B7FB8` | control boundaries (measured at 3:1) |
| `pulse_text_primary` | `#F6F4FF` | titles, body, values |
| `pulse_text_secondary` | `#B9B2D4` | supporting copy |
| `pulse_text_muted` | `#948DB4` | labels, captions, timestamps |
| `pulse_text_disabled` | `#5E5780` | disabled only (exempt from 4.5:1) |

The **Paper** ramp (`pulse_light_*`) is the same structure with inverted polarity and a cool paper
canvas (`#F5F4FA`), and the **Void** ramp (`pulse_black_*`) replaces only the surfaces with pure
black for OLED panels.

### Accent

| Token | Value | Role |
| --- | --- | --- |
| `pulse_accent` | `#A78BFA` | interactive violet: selection, links, focus, live data |
| `pulse_accent_alt` | `#22D3EE` | the gradient's second stop — graphic only, never a label |
| `pulse_accent_tint` | `#241E3C` | pre-composited container for accent text |

`pulse_accent_alt` is measured at 3:1 rather than 4.5:1 on purpose: it is a *fill*, and the only
text drawn on the gradient uses `PulseColors.onAccentGradient` (ink in dark themes, paper in light
ones).

### Signal colours

| Meaning | Ink | Paper | Container |
| --- | --- | --- | --- |
| Work / success | `#4ADE80` | `#147536` | `*_work_tint` |
| Rest | `#38BDF8` | `#0369A1` | `*_rest_tint` |
| Prepare / warning | `#FBBF24` | `#965B06` | `*_prepare_tint` |
| Cool-down | `#C084FC` | `#7E22CE` | `*_cooldown_tint` |
| Destructive / error | `#FB7185` | `#BE123C` | `*_destructive_tint` |

### Category hues

| Family | Ink | Paper | Container | Categories |
| --- | --- | --- | --- | --- |
| Conditioning | `#FBBF24` | `#965B06` | `*_cat_conditioning_tint` | HIIT, Tabata, Circuit, Boxing, EMOM, E2MOM, AMRAP |
| Cardio | `#FB7185` | `#BE123C` | `*_cat_cardio_tint` | Run / Walk |
| Strength | `#A78BFA` | `#6D28D9` | `*_cat_strength_tint` | Strength |
| Mobility | `#38BDF8` | `#0369A1` | `*_cat_mobility_tint` | Stretching |
| Mind | `#4ADE80` | `#147536` | `*_cat_mind_tint` | Breathing, Focus |
| Tools | `#94A3B8` | `#475569` | `*_cat_tools_tint` | Custom sequence, Compound, Stopwatch, Repeating cue |

## 3. Typography

Two families, both already on the device — no font assets, no download step:

- **Sans** (`FontFamily.Default`) for prose and labels.
- **Mono** (`FontFamily.Monospace`) with tabular figures for every number, so a countdown ticking
  from `1:00` to `0:59` does not shift a single pixel.

| Role | Size / line height / weight | Used for |
| --- | --- | --- |
| `displayLarge` | 64 / 68 / Bold | reserved |
| `displayMedium` | 44 / 48 / Bold | the onboarding headline |
| `displaySmall` | 32 / 38 / Bold | reserved |
| `headlineLarge` | 26 / 32 / Bold | the Today hero greeting |
| `headlineMedium` | 22 / 28 / SemiBold | pushed-screen headlines, category screen titles |
| `headlineSmall` | 18 / 24 / SemiBold | top-bar titles |
| `titleLarge` | 17 / 24 / SemiBold | card headings |
| `titleMedium` | 15 / 22 / Medium | list row titles |
| `titleSmall` | 13 / 18 / Medium | tile values, dense headings |
| `bodyLarge` | 15 / 23 / Normal | field text, primary body copy |
| `bodyMedium` | 14 / 21 / Normal | card body copy |
| `bodySmall` | 12 / 18 / Normal | captions, hints |
| `labelLarge` | 15 / 20 / SemiBold | button labels |
| `labelMedium` | 12 / 16 / Medium, +0.6 sp | chips, metadata |
| `labelSmall` | 11 / 14 / Bold, +1.0 sp | section labels (uppercased by the component) |

Numeric styles live in `PulseType` (`NumericDisplay`, `NumericLarge`, `NumericMedium`,
`NumericSmall`, `StepperValue`) rather than in the Material role scale, because a timer is data, not
prose.

## 4. Spacing, size and radii

Base unit 4 dp. Steps: `microGap 2`, `xs 4`, `s 8`, `m 12`, `l 16`, `xl 20`, `xxl 28`, `huge 40`,
`massive 56`.

| Purpose | Token | Value |
| --- | --- | --- |
| Page gutter (every screen) | `pagePadding` | 20 dp |
| Card interior | `cardPadding` | 18 dp |
| Gap between sibling cards | `cardGap` | 14 dp |
| Gap inside a chip cluster | `chipGap` | 8 dp |
| Button height | `buttonHeight` | 52 dp |
| Input / stepper height | `controlHeight` | 48 dp |
| Icon button (visual) | `iconButtonSize` | 40 dp |
| Top bar | `topBarHeight` | 64 dp |
| List row | `listRowHeight` | 64 dp |
| Floating navigation bar | `navBarHeight` | 72 dp |
| Navigation rail width | `railWidth` | 88 dp |
| Minimum touch target | `minTouchTarget` | 48 dp |
| Radii | `cardRadius` 24, `panelRadius` 18, `controlRadius` 14, `chipRadius` 999, `sheetRadius` 28 | — |
| Shared content metrics | `barHeightCompact` 10, `barHeightRegular` 20, `chartHeight` 132, `gridCellMin` 172, `carouselCardWidth` 236 | — |
| Player density presets | `playerPanelCompact` 208, `playerPanelStandard` 264, `playerPanelLarge` 320 | — |
| Today hero height | `heroHeight` | 188 dp |

A screen never writes a raw `dp` for spacing or for a shared metric. Values that size one screen's
own artwork stay with that screen.

## 5. Components

Everything lives in `ui/components/`, is built from tokens, and is used by every screen — there is no
screen-local restyling of a button, card or field.

| File | Contents |
| --- | --- |
| `Foundation.kt` | `PulseScaffold`, `PulseCard`, `PulseGradientCard`, `PulsePanel`, `PulseTopBar`, `SectionHeader`, `PulseDivider`, `PulseListRow`, `VerticalSpacer` |
| `Controls.kt` | `PrimaryActionButton`, `SecondaryActionButton`, `GhostActionButton`, `DangerActionButton`, `PulseIconButton`, `PulseTextField`, `OptionRow`, `PulseSegmented`, `PulseChoiceChip`, `ToggleRow`, `PulseSliderRow`, `DurationStepper`, `NumberStepper`, `PulseDropdown` / `PulseDropdownItem`, `SelectableTile` |
| `Indicators.kt` | `PhaseChip`, `NeutralChip`, `CategoryChip`, `StatusBadge`, `StatTile`, `InfoBanner`, `StatRow`, `CategoryPicker` |
| `DataDisplay.kt` | `TimelineBar`, `ProgressRing`, `ActivityChart`, `GradientProgressBar` |
| `Feedback.kt` | `EmptyState`, `LoadingBox`, `ErrorState`, `ConfirmDialog`, `PulseSnackbar` |
| `Navigation.kt` | `PulseFloatingNavBar`, `PulseNavRail`, `PulseNavItem`, `PulseRailDivider` |

Rules encoded in the components themselves, so a screen cannot get them wrong:

- There is no API to pass a radius: a card is 24 dp, a panel 18, a control 14, a chip round.
- A destructive confirmation is only red when `destructive = true`.
- An empty or error state that can be resolved always offers the action that resolves it.
- Every chip, badge and banner draws its colours from a named tone or hue, never from a raw hex.

## 6. Layout

- **Phone:** single column, 20 dp gutters, 14 dp between cards, and a **floating pill bar** on the
  four primary destinations (Today, Workouts, Progress, Settings).
- **Tablet and landscape (≥ 600 dp):** the same four destinations become a **rail** down the left
  edge with the wordmark at its head. One navigation model, two shapes — both read the same
  `PulseNavItem` list, so there is nothing to keep in step.
- **Every screen is a `PulseScaffold`.** One background, one optional header, one optional bottom
  bar, and content that is handed its padding rather than guessing at it.
- **No layout shift.** Tabular numerals, fixed row heights and `heightIn` controls mean nothing
  moves when a value changes.

## 7. Interaction states

| State | Treatment |
| --- | --- |
| Hover / pressed | M3 ripple plus a `surfaceHover` fill on raised controls |
| Selected | the element's own hue as the container, its hue as the content |
| Focused | 2 dp accent boundary (M3 focus indicator) |
| Disabled | `textDisabled` / `surfaceHover` fill, never a dimmed colour |
| Transition | 160 ms feedback, 260 ms selection, 380 ms sheets, 0 ms when reduced motion is on |

## 8. Categories

`WorkoutType` in the engine is a **semantic key**: it is what is serialised into a plan and stored in
the database, and the only thing it knows about itself is whether it can report a finish time.
Everything a human reads lives in `data/Categories.kt`:

```kotlin
WorkoutCategory(
    type = WorkoutType.HIIT,
    title = "HIIT",
    tagline = "Hard efforts, short recoveries",
    description = "…",
    hue = CategoryHue.CONDITIONING,
    icon = Icons.Filled.LocalFireDepartment,
    defaults = CategoryDefaults(workMillis = 40_000, …),
    capabilities = CategoryCapabilities(supportsLadder = true, …),
    keywords = listOf("interval", "cardio", …),
)
```

The registry (`WorkoutCategories`) is the single source for:

- the library's **category rail**, generated from the registry rather than from the workouts that
  happen to exist, so a category with no workouts yet is still part of the vocabulary;
- the **category picker** in the builder, which shows each category's glyph in its own hue with a
  check on the selected one — and replaced a hard-coded list of enum constants;
- the **category screen** (`Routes.CATEGORY`), which needs nothing but a key to render a hero, the
  default timings and everything you already have in that category;
- the **Today presets** and the "where your time went" breakdown in Progress;
- the icon and hue on every card, chip, row and player header.

Adding a category is a two-file change: add the member to `WorkoutType`, add the record to
`WorkoutCategories`, and every screen, filter, default and icon follows automatically.

## 9. Accessibility

- Text clears 4.5:1 in every theme; control boundaries clear 3:1. Every pairing is measured in
  [`CONTRAST.md`](CONTRAST.md).
- Colour is never the only signal: a phase has a name, a dot and a position in the timeline; a
  category has a name and a glyph.
- All interactive elements expose at least a 48 dp touch target, even when drawn at 40 dp.
- Icon-only controls always have a content description; tooltips are additive.
- `highContrast` lifts the accents and pushes text to the extremes; `reducedMotion` collapses every
  duration to zero.

## 10. Extending the system

1. Add the token to `colors.xml`.
2. Mirror it in `Palette.kt` with the same name in `PulsePalette`.
3. Add it to `REQUIRED_TOKENS` and `KOTLIN_MIRROR` in `scripts/contrast_check.py` if it is a raw
   colour, and add a measured pair if it carries text or marks a boundary.
4. Run `python3 scripts/contrast_check.py` — it must exit 0 before anything else runs.
5. Surfacing it in a screen: use a component, or a composition local. A hex literal in a screen is
   how a design system stops being one.
6. Adding a *category*: `WorkoutType` + `WorkoutCategories`. Nothing else needs to know.
