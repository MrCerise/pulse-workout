# PULSE design system — "Graphite"

Design reference: **Linear**. One reference, applied consistently: the calm near-black chrome, the
hairline separators instead of shadows, the tight type scale with monospaced numerals, the small
number of radii, and a single accent that only ever means "interactive". No Linear asset, logo,
font or colour value is reproduced — what is borrowed is the *approach*, and every value below is
PULSE's own.

The system is deliberately small. It has one neutral ramp, one accent, five signal colours and eight
spacing steps, and every screen composes from those. If a screen needs something that does not exist
here, the answer is to add a token, not a literal.

---

## 1. Principles

1. **Chrome is monochrome.** Navigation, headers, buttons, borders and text never carry hue.
2. **Colour means something.** The only chromatic elements are interval phases (work, rest,
   prepare, cool-down) and state (destructive). A screen with twenty chips stays calm because
   chips are tinted containers, not saturated blocks.
3. **Elevation is a border, not a shadow.** A surface is separated from the canvas by its fill plus
   a one-pixel border. That renders identically on every density, in every theme, and never blurs
   text underneath it.
4. **Small radii, consistently.** 12 dp surfaces, 8 dp controls, 6 dp chips, 16 dp sheets.
   Nothing is a pill unless it is genuinely round (the transport button).
5. **The primary action inverts the text ramp.** A light block in dark themes, a dark block in
   light themes. This keeps the accent free to mean "interactive/selected" rather than "brand".
6. **Motion is felt, not watched.** 120 ms for feedback, 180 ms for selection and expansion, and
   zero when the user asks for reduced motion.

## 2. Colour

Defined once in `app/src/main/res/values/colors.xml` and mirrored value-for-value in
`ui/theme/Palette.kt`. `scripts/contrast_check.py` exits **2** if the two ever disagree (token
drift) and exits **1** if any measured pairing drops below its WCAG 2.1 AA threshold.

### Neutral ramp (dark — default)

| Token | Value | Role |
| --- | --- | --- |
| `pulse_canvas` | `#08090A` | app background |
| `pulse_nav` | `#0B0C0E` | top bar, bottom bar |
| `pulse_surface` | `#101114` | cards, panels |
| `pulse_surface_raised` | `#16181B` | inputs, stat tiles, steppers |
| `pulse_surface_overlay` | `#1D1F23` | dialogs, menus, snackbars |
| `pulse_surface_hover` | `#222429` | neutral chip fill, hover |
| `pulse_border` | `#24272C` | hairline separators (decorative) |
| `pulse_outline` | `#5F6470` | control boundaries (measured at 3:1) |
| `pulse_text_primary` | `#F7F8F8` | titles, body, values |
| `pulse_text_secondary` | `#9BA1AE` | supporting copy |
| `pulse_text_muted` | `#7A818F` | labels, captions, timestamps |
| `pulse_text_disabled` | `#4B5058` | disabled only (exempt from 4.5:1) |

The light ramp is the same structure with inverted polarity (`pulse_light_*`), and the OLED ramp
replaces only the surfaces with pure black equivalents (`pulse_black_*`).

### Signal colours

| Meaning | Dark | Light | Container |
| --- | --- | --- | --- |
| Accent (selection, links, focus) | `#7C8CFF` | `#3D4FD6` | `pulse_accent_tint` |
| Work / success | `#4CC38A` | `#177245` | `pulse_work_tint` |
| Rest | `#5BC8E8` | `#0B6E85` | `pulse_rest_tint` |
| Prepare / warning | `#E3B341` | `#8A5A00` | `pulse_prepare_tint` |
| Cool-down | `#A78BFA` | `#5B3FD1` | `pulse_cooldown_tint` |
| Destructive / error | `#F2555A` | `#BE2F3C` | `pulse_destructive_tint` |

Tints are **pre-composited values**, not runtime alpha: `docs/CONTRAST.md` therefore measures the
chip label against the exact colour that ships.

## 3. Typography

Two families, both already on the device — no font assets, no download step:

- **Sans** (`FontFamily.Default`) for prose and labels.
- **Mono** (`FontFamily.Monospace`) for every number: countdowns, durations, interval counts, BPM.

Monospaced digits with tabular figures (`tnum`) are why a countdown ticking from `1:00` to `0:59`
does not shift a single pixel.

| Role | Size / line height / weight | Used for |
| --- | --- | --- |
| `displayLarge` | 56 / 60 / Bold | player countdown (via `PulseType.NumericDisplay`) |
| `displaySmall` | 28 / 34 / SemiBold | welcome hero |
| `headlineSmall` | 18 / 24 / SemiBold | screen headlines |
| `titleLarge` | 16 / 22 / SemiBold | top-bar titles, card headings |
| `titleMedium` | 14 / 20 / Medium | list row titles |
| `titleSmall` | 13 / 18 / Medium | tile values, dense headings |
| `bodyLarge` | 14 / 21 / Normal | field text, primary body copy |
| `bodyMedium` | 13 / 19 / Normal | card body copy |
| `bodySmall` | 12 / 17 / Normal | captions, hints |
| `labelLarge` | 13 / 18 / Medium | button labels |
| `labelMedium` | 11 / 16 / Medium, +0.6 sp | chips, metadata |
| `labelSmall` | 11 / 14 / Medium, +0.9 sp | section labels (uppercased by the component) |

Numeric styles live in `PulseType` (`NumericDisplay`, `NumericLarge`, `NumericMedium`,
`NumericSmall`) rather than in the Material role scale, because a timer is data, not prose.

## 4. Spacing, size and radii

Base unit 4 dp. Steps: `xs 4`, `s 8`, `m 12`, `l 16`, `xl 24`, `xxl 32`, `huge 48`.

| Purpose | Token | Value |
| --- | --- | --- |
| Page gutter (every screen) | `pagePadding` | 16 dp |
| Card interior | `cardPadding` | 16 dp |
| Gap between sibling cards | `cardGap` | 12 dp |
| Button / input height | `controlHeight`, `buttonHeight` | 40 dp |
| Icon button | `iconButtonSize` | 36 dp visual, 48 dp touch |
| Top bar | `topBarHeight` | 56 dp |
| List row | `listRowHeight` | 56 dp |
| Minimum touch target | `minTouchTarget` | 48 dp |
| Radii | `cardRadius` 12, `controlRadius` 8, `chipRadius` 6, `sheetRadius` 16, `pillRadius` 999 |
| Micro gap (a caption under its title) | `microGap` | 2 dp |
| Shared content metrics | `barHeightCompact` 8, `barHeightRegular` 48, `chartHeight` 112, `gridCellMin` 160, `carouselCardWidth` 220 | — |
| Player density presets | `playerPanelCompact` 200, `playerPanelStandard` 260, `playerPanelLarge` 320 | — |

A screen never writes a raw `dp` for **spacing or for a shared metric**: gutters, gaps, padding,
control heights and everything in the table above are read from `LocalPulseDimens.current`. Values
that size one screen's own artwork — the home ring's diameter, the welcome illustration, the player's
transport button, the density presets' panel height — stay in that screen as `LocalPulseDimens`
tokens where they are shared (`playerPanelCompact`, `chartHeight`, `gridCellMin`, `carouselCardWidth`)
and as plain literals where they are not, because a name used once adds indirection rather than
information.

Inside a single component the rule is deliberately looser: a dot is 5–6 dp, a glyph inside a badge is
16 dp, a progress stroke is 2 dp. Those are optical adjustments to one control, not spacing between
blocks, so they live in the component that draws them and never leak to a call site — no screen passes
a radius, a stroke width or a dot size to a component.

## 5. Components

Everything lives in `ui/components/`, is built from tokens, and is used by every screen — there is no
screen-local restyling of a button, card or field.

| File | Contents |
| --- | --- |
| `Foundation.kt` | `PulseCard`, `PulsePanel`, `SectionHeader`, `PulseDivider`, `PulseListRow`, `PulseTopBar`, `VerticalSpacer` |
| `Controls.kt` | `PrimaryActionButton`, `SecondaryActionButton`, `GhostActionButton`, `DangerActionButton`, `PulseIconButton` (+ tooltip), `PulseTextField`, `PulseSegmented`, `PulseChoiceChip`, `OptionRow`, `ToggleRow`, `PulseSliderRow`, `DurationStepper`, `NumberStepper`, `PulseDropdown` / `PulseDropdownItem` |
| `Indicators.kt` | `PhaseChip`, `NeutralChip`, `StatusBadge`, `StatTile`, `InfoBanner` |
| `DataDisplay.kt` | `TimelineBar`, `ProgressRing`, `ActivityChart` |
| `Feedback.kt` | `EmptyState`, `LoadingBox`, `ErrorState`, `ConfirmDialog`, `PulseSnackbar` |
| `Navigation.kt` | `PulseBottomBar`, `PulseNavItem` |

Rules encoded in the components themselves, so a screen cannot get them wrong:

- A card is 12 dp, a control 8 dp, a chip 6 dp — there is no API to pass any other radius.
- An icon button carries a content description *and* an optional tooltip.
- A destructive confirmation is only red when it destroys something (`destructive = true`).
- An empty or error state that can be resolved always offers the action that resolves it.
- Every chip, badge and banner draws its colours from a named tone, never from a raw hex.

## 6. Layout

- **Phone:** single column, 16 dp gutters, 12 dp between cards, bottom navigation on the four
  primary destinations (Today, Workouts, Progress, Settings). Pushed screens own their header and
  get a back affordance in the same place every time.
- **Tablet and landscape:** the same components are driven by content width. Adaptive grids are
  used where a list would otherwise stretch (`GridCells.Adaptive`), and wide stat clusters collapse
  to rows that wrap rather than to a separate layout.
- **No layout shift.** Tabular numerals, fixed row heights and `heightIn` controls mean nothing
  moves when a value changes.

## 7. Interaction states

| State | Treatment |
| --- | --- |
| Hover / pressed | M3 ripple plus a `surfaceHover` fill on raised controls |
| Selected | `accentTint` container with accent content and an accent border |
| Focused | 2 dp accent boundary on the focused control (M3 focus indicator) |
| Disabled | `textDisabled` / `surfaceHover` fill, never a dimmed colour |
| Transition | 120 ms feedback, 180 ms selection/expansion, 0 ms when reduced motion is on |

## 8. Accessibility

- Text clears 4.5:1 in both themes; control boundaries clear 3:1. Every pairing is measured in
  [`CONTRAST.md`](CONTRAST.md).
- Colour is never the only signal: a phase also has a name, a dot and a position in the timeline.
- All interactive elements expose at least a 48 dp touch target, even when drawn at 36 dp.
- Icon-only controls always have a content description; tooltips are additive.
- `highContrast` lifts the accents and pushes text to the extremes; `reducedMotion` collapses every
  duration to zero.

## 9. Extending the system

1. Add the token to `colors.xml`.
2. Mirror it in `Palette.kt` with the same name in `PulsePalette`.
3. Add it to `REQUIRED_TOKENS` and `KOTLIN_MIRROR` in `scripts/contrast_check.py` if it is a raw
   colour, and add a measured pair if it carries text or marks a boundary.
4. Run `python3 scripts/contrast_check.py` — it must exit 0 before anything else runs.
5. Surfacing it in a screen: use a component, or a composition local. A hex literal in a screen is
   how a design system stops being one.
