# INTERVALS design system — "Momentum"

As of v1.8.0, INTERVALS ships the **Momentum** system: deep blue-tinted ink neutrals, a single volt
lime accent, and a two-family voice — Space Grotesk for display and numbers, Inter for text. Cards
float on a whisper of shadow behind rounder silhouettes, the phone navigation bar is a detached
pill, and the ink hero panel carries a volt wash rising from its lower edge. The three-bar beat mark
is monochrome; volt (or a contrast-checked wallpaper accent) identifies actions.

Labels and actual workout data take priority over explanatory copy. Page subtitles are reserved for
useful metadata such as a date, not slogans. Workout descriptions and the text-description parser
are no longer part of the interface. Interval notes, error recovery and permission information remain.

This document is normative: it is the reference a review will hold your change against. The measured
numbers it depends on live in the generated [contrast report](CONTRAST.md).

1. [Principles](#1-principles)
2. [Colour](#2-colour)
3. [Typography](#3-typography)
4. [Space, size, radius, elevation, motion](#4-space-size-radius-elevation-motion)
5. [The shell](#5-the-shell)
6. [Components](#6-components)
7. [Accessibility](#7-accessibility)
8. [Adding a screen](#8-adding-a-screen)
9. [Verification](#9-verification)
10. [Branding and theme interaction](#10-branding-and-theme-interaction)

---

## 1. Principles

1. **Neutral chrome, one accent.** Grey does the layout, the accent does the pointing. Volt lime
   appears on: the primary action of a screen, the selected navigation item, the focus ring,
   links, and live data. Its one decorative appearance is the fixed-alpha wash inside the ink hero
   panel — brand colour as light, never as a filled block behind text.
2. **A hairline, then a whisper of shadow.** Surfaces are separated from the canvas by one fill step,
   a 1 dp border and one elevation step of shadow. Cards lift; pressed cards lift further; the
   floating navigation pill lifts most — so the shadows on screen still form a readable depth scale.
3. **Soft rectangles, not pills.** 16 dp controls, 22 dp cards, 30 dp sheets. The one exception is
   granted by *function*, not by size: whatever you tap is a rounded rectangle — a button, a field, a
   stepper, and a filter or option chip too — while a chip that only labels something (a phase tag, a
   category name, a status badge, a progress track) may be fully round, because it carries no action.
4. **Dense, not cramped.** 4 dp base unit, 20 dp page gutter, 16 dp card padding, 56 dp list rows,
   40 dp compact controls and 56 dp action buttons.
5. **One surface, not two.** A section sits directly on the canvas; the controls inside it carry
   their own surfaces where they need one (a field, a drop-down trigger, a button, a stat tile).
   A container around containers — a card wrapping fields, buttons or tiles — is never drawn: a
   list of rows is one table surface with hairlines, and chips and badges may appear *inside* a
   surface only because they are labels, not containers.
6. **Colour always means something.** Four interval phases, six category families, four semantic
   states. Every hue ships with the pre-composited tint it is drawn on, so a tinted chip's contrast
   is a measured number rather than an alpha estimate.
7. **Motion is felt, not watched.** 180 ms feedback, 260 ms selection, 340 ms overlays — and zero
   when the user turns on reduced motion.
8. **One idea, one component.** A category badge, a phase tag, a status pill or a stat tile is one
   component wherever it appears. If two pages can each have their own version of something, they
   will — and then they will drift.
9. **One measure, one gutter.** Every page is capped at the same width and inset by the same
   padding, so a destination is the same page at every window size, and every page's title sits at
   the same height.

---

## 2. Colour

Defined once in `app/src/main/res/values/colors.xml` and mirrored value-for-value in
`ui/theme/Palette.kt`. `scripts/contrast_check.py` exits **2** if the two files disagree (token drift)
and **1** if any measured pairing drops below its WCAG 2.1 threshold. The current run measures
**125 pairs** across the ink, void and paper themes; see `docs/CONTRAST.md` for the per-pair table.

### Ink (dark, the default theme)

| Token | Value | Role |
| --- | --- | --- |
| `pulse_canvas` | `#101216` | app background |
| `pulse_nav` | `#14161B` | sidebar, icon rail, bottom bar, breadcrumb bar |
| `pulse_surface` | `#181B20` | cards, panels, table surfaces |
| `pulse_surface_raised` | `#1E2126` | inputs, chips, secondary buttons, icon tiles |
| `pulse_surface_overlay` | `#23262C` | menus, dialogs, snackbars |
| `pulse_surface_hover` | `#2A2E34` | hover and pressed fill |
| `pulse_surface_sunken` | `#121418` | wells: table headers, inset groups, text fields |
| `pulse_border` | `#2E323A` | hairline separators (decorative) |
| `pulse_border_strong` | `#3B4048` | nested divider, field edge that needs to be visible |
| `pulse_outline` | `#8A9099` | meaningful control boundary (measured at 3:1) |
| `pulse_text_primary` | `#F4F6F8` | titles, body, values |
| `pulse_text_secondary` | `#C3C7CC` | supporting copy, list subtitles |
| `pulse_text_muted` | `#9CA2A9` | labels, captions, timestamps |
| `pulse_text_disabled` | `#6E747C` | disabled only (exempt from 4.5:1) |
| `pulse_track` | `#3A3E45` | slider, ring and chart grooves |

### Paper (light)

Same roles, inverted polarity, and a *paper* canvas (`#F4F6F8`) with white surfaces, so a card edge
is legible without a shadow. The accent splits in two, which is the one asymmetry between the themes:

| Token | Value | Role |
| --- | --- | --- |
| `pulse_light_canvas` | `#F4F6F8` | app background |
| `pulse_light_nav` | `#FFFFFF` | chrome |
| `pulse_light_surface` | `#FFFFFF` | cards |
| `pulse_light_surface_raised` | `#F7F8FA` | chips, secondary buttons |
| `pulse_light_surface_sunken` | `#EFF2F5` | wells, text fields |
| `pulse_light_border` | `#E3E7EB` | hairline separators |
| `pulse_light_outline` | `#6B7280` | control boundary (3:1) |
| `pulse_light_text_primary` | `#131519` | titles, body |
| `pulse_light_text_secondary` | `#4B5157` | supporting copy |
| `pulse_light_text_muted` | `#666C74` | labels, captions |
| `pulse_light_accent` | `#3F6212` | accent as **text**: links, selection, focus |
| `pulse_light_brand` | `#A3E635` | accent as **fill**: primary buttons |

### Void (OLED)

`pulse_black_*` replaces only the surfaces with pure black (`#000000` canvas and chrome, `#0B0C0E`
cards, `#23262A` hairlines) and reuses the Ink text, border and accent values. It is a surface
override, not a fourth palette — which is why the contrast report re-measures it rather than trusting
it.

### The accent rule

`pulse_accent` is the interactive accent and `pulse_brand` the brand fill; in the dark theme they are
the same `#A3E635`, and in the light theme the accent deepens to `#3F6212` so that volt **text**
clears 4.5:1 on paper. Practical consequences:

* a **primary button** is a brand-coloured fill with ink on it (`action_text`), in both themes — that
  pair is measured, not assumed;
* a **link, selected nav label or focus ring** uses `accent`, so it is legible in both themes;
* `accent_tint` is the pre-composited container for a selected row or an active nav item;
* `accent_alt` is a lighter stop used **only as a graphic** (a bar, a rank badge), never as a label.

### Interval phases and semantic states

| Family | Dark | Light | Used for |
| --- | --- | --- | --- |
| `work` | `#4CC38A` | `#12603C` | work intervals, success, "connected" |
| `rest` | `#6CABF8` | `#0A56A8` | rest and transitions |
| `prepare` | `#F5B544` | `#7A5100` | preparation, warnings, "scheduled" |
| `cooldown` | `#B18CFA` | `#5B21B6` | cool-down |
| `destructive` | `#F87171` | `#A8291F` | delete, end session, errors |
| `info` | `#7CB7FF` | `#0B57C2` | informational banners, chips |

Semantic state reuses the phase hues on purpose — one palette, one meaning per hue, no second family
of "status colours" that could drift. Success is the work green, warning is the prepare amber, danger
is the destructive red, and `success_tint` / `warning_tint` / `danger_tint` are the measured
containers that go with them.

### Category hues

Six families, each with its own pre-composited tint, used by the category registry so a HIIT card and
a mobility card are told apart at a glance without the chrome becoming a box of crayons.

| Family | Dark | Light | Registry keys |
| --- | --- | --- | --- |
| `cat_cardio` | `#F0607A` | `#A3123A` | run/walk, cycling, rowing |
| `cat_conditioning` | `#F5B544` | `#7A5100` | HIIT, Tabata, EMOM, AMRAP, circuits |
| `cat_strength` | `#B18CFA` | `#5B21B6` | strength, compound blocks |
| `cat_mobility` | `#5BA8F5` | `#0A5C8A` | stretching, mobility flows |
| `cat_mind` | `#4CC38A` | `#106544` | breathing, focus |
| `cat_tools` | `#98A2B3` | `#3F4753` | stopwatch, repeating cue, customs |

### The ink panel

`pulse_hero_surface` (`#131519` in both themes) is deliberately dark in **both** themes. It is used twice
in the whole app — the Today hero and the session summary — so the one panel that carries the page's
headline never looks like one more card in a stack.

---

## 3. Typography

Two families, two jobs. **Space Grotesk** (Medium, SemiBold, Bold) is the display face: page
titles, hero figures and the countdown — the engineered voice that makes a screen read as Intervals.
**Inter** (Regular, Medium, SemiBold, Bold) is the text face: a tall-x-height grotesque that keeps
12–14 sp labels legible mid-workout. Both ship as static instances subset to Latin, each with its
SIL OFL licence in `res/raw/`.

Numbers are Space Grotesk with **tabular figures** (`fontFeatureSettings = "tnum"`) — the face ships
the feature, so the display voice can drive the countdown without a pixel of reflow as a `1:00`
ticks to `0:59`. `PulseType` holds the numeric styles; `PulseFonts.Mono` remains for the one place a
fixed advance width is genuinely wanted (the cue-script preview).

| Style | Size / weight | Used for |
| --- | --- | --- |
| `displaySmall` | 28 sp SemiBold (Grotesk) | onboarding headline |
| `headlineLarge` | 26 sp SemiBold (Grotesk) | page titles (`PulsePageHeader`) |
| `headlineMedium` | 21 sp SemiBold (Grotesk) | onboarding step headings |
| `headlineSmall` | 18 sp SemiBold (Grotesk) | cards, the breadcrumb leaf, dialog titles |
| `titleLarge` | 17 sp SemiBold (Grotesk) | the sidebar wordmark |
| `titleMedium` | 14 sp SemiBold (Inter) | list-row titles, card headings |
| `titleSmall` | 13 sp SemiBold (Inter) | field labels |
| `bodyLarge` | 15 sp Regular (Inter) | body copy |
| `bodyMedium` | 14 sp Regular (Inter) | body copy, metric-row labels |
| `bodySmall` | 13 sp Regular (Inter) | metadata, subtitles, captions |
| `labelMedium` | 12 sp Medium (Inter) | chips, badges, quiet actions |
| `labelSmall` | 11 sp SemiBold (Inter) | uppercase micro-labels |
| `PulseType.NumericDisplay` | 64 sp Bold tabular (Grotesk) | player countdown |
| `PulseType.NumericLarge` | 32 sp SemiBold tabular (Grotesk) | hero figures |
| `PulseType.NumericMedium` | 17 sp SemiBold tabular (Grotesk) | stat tile values |
| `PulseType.NumericSmall` | 13 sp Medium tabular (Inter) | inline numbers |
| `PulseType.SectionLabel` | 11 sp SemiBold, +0.8 tracking (Inter) | sidebar group labels |
| `PulseType.ButtonLabel` | 15 sp SemiBold (Grotesk) | button and menu-trigger labels |
| `PulseType.NamePlate` | 21 sp SemiBold (Grotesk) | the builder's workout-name plate |

`pulseTypography()` binds Material's role scale to the same table, so a component that reads
`MaterialTheme.typography.<role>` inherits the Momentum rhythm instead of Material's defaults.

---

## 4. Space, size, radius, elevation, motion

| Token | Value | Notes |
| --- | --- | --- |
| `microGap` / `xs` / `s` / `m` / `l` / `xl` / `xxl` / `huge` / `massive` | 2 / 4 / 8 / 12 / 16 / 20 / 24 / 32 / 48 dp | 4 dp base unit |
| `pagePadding` | 20 dp | every scrolling screen shares it |
| `cardPadding` | 16 dp | `PulseCard` interior |
| `cardGap` | 12 dp | between sibling cards |
| `chipGap` | 6 dp | inside a cluster of chips |
| `chipPaddingHorizontal` / `chipPaddingVertical` | 8 / 3 dp | inside every label chip |
| `buttonPaddingHorizontal` / `buttonPaddingVertical` | 20 / 14 dp | inside every action button |
| `controlHeight` / `buttonHeight` / `namePlateHeight` | 40 / 56 / 64 dp | compact controls / action buttons / the builder name plate |
| `iconButtonSize` | 36 dp | inside a 48 dp touch target |
| `listRowHeight` | 56 dp | `PulseListRow` |
| `metricRowHeight` | 44 dp | `MetricRow` |
| `statTileHeight` | 92 dp | a stat tile, so a row of them lines up |
| `topBarHeight` | 56 dp | breadcrumb bar (before the status-bar inset) |
| `navBarHeight` | 68 dp | phone floating navigation pill |
| `railWidth` / `sidebarWidth` | 72 / 244 dp | tablet rail, desktop sidebar |
| radii | 16 / 22 / 30 / full | controls, panels and every tappable chip / cards / sheets / label chips only |
| elevation | 0 / 1 / 4 / 12 dp | flat / interactive / overlay / floating chrome |
| motion | 180 / 260 / 260 / 340 ms | feedback / selection / theme palette / overlay — 0 under reduced motion |

### The responsive layer

A *size* is not a *style*: the colour of a card does not depend on the window, but the measure of a
page and the number of columns a grid may use do. Those numbers live in `PulseLayout`
(`ui/theme/Responsive.kt`), resolved once from the window size class.

| Window | Class | Navigation | `contentMaxWidth` | `statTileMinWidth` | `cardMinWidth` | `tileMinWidth` |
| --- | --- | --- | --- | --- | --- | --- |
| `< 600 dp` | `COMPACT` | floating navigation pill | 760 dp | 124 dp | 196 dp | 140 dp |
| `600–999 dp` | `MEDIUM` | 72 dp icon rail | 760 dp | 124 dp | 196 dp | 140 dp |
| `>= 1000 dp` | `EXPANDED` | 244 dp sidebar | 760 dp | 124 dp | 196 dp | 140 dp |

* **The measure.** Every page's content is capped at `contentMaxWidth` and centred, so a
  destination on a desktop is the *same page* as that destination on a phone — same gutter, same
  measure, same place for the title — rather than the same content stretched across a monitor.
  The page's own bottom bar shares the measure, so its action button sits in the same column as
  the page it belongs to.
* **The gutter.** `pageContentPadding()` is the one padding a scrolling page uses: 20 dp either
  side, 12 dp above, 32 dp below. Every page's title therefore sits at the same height below the
  breadcrumb bar, and every card's left edge lands in the same place.
* **The columns.** `PulseStatGrid`, `PulseCellGrid` and the library's grid ask the width they are
  actually given and fit as many cells as will hold their minimum; a row of three statistics is
  three across on a tablet and two-plus-one on a phone, with the same tiles, the same gap and the
  same height either way.
* **The hero.** The ink panel on the Today page has a floor of 168 dp on a phone, 200 dp on a
  tablet and 216 dp on a desktop, so it keeps its presence on a large screen instead of
  shrinking into a strip. It never shrinks the content — only the air around it.


---

### The watch

The watch app is a second surface, not a second design system: it shares the phone's palette
roles, the same phase colours and the same brand marks, and it has its own token set
(`WearColors`, `WearDimens`) restricted to what a 200 dp round screen actually renders. The
consequences are the same as on the phone — a padding that is named rather than written by hand,
and no raw colour outside the theme — but the numbers are the watch's own, because a 4 dp gap and a
12 dp gap do not mean the same thing on a 200 dp circle.

## 5. The shell

The application frame is one navigation model in three shapes, and the shape follows the window:

| Window width | Navigation | Chrome |
| --- | --- | --- |
| `< 600 dp` (phone) | floating navigation pill, tinted indicator behind the active glyph, hovering above the system bar area on one shadow | breadcrumb bar + brand mark |
| `>= 600 dp` (tablet, portrait) | 72 dp icon rail with the mark at its head | breadcrumb bar |
| `>= 1000 dp` (desktop, tablet landscape) | 244 dp sidebar: wordmark, grouped destinations, an accent beat dot on the active row, appearance switch in the footer | breadcrumb bar |

The ladder is published once, by the theme, as `LocalPulseWindowSize` (`COMPACT` / `MEDIUM` /
`EXPANDED`). The shell draws its navigation from it and the page components draw their columns
from it, so the chrome and the pages can never disagree about how much room there is.

* **Breadcrumb bar** (`PulseBreadcrumbBar`) — one 56 dp row: where you are on the left, the global
  controls (theme, settings) on the right, a hairline under it. It owns the status-bar inset, so no
  screen ever reasons about insets.
* **Back navigation lives in the page**, not in the bar (`PulsePageHeader(onBack = …)`), because a
  page knows whether leaving it needs a confirmation — an unsaved builder does, and the shell does
  not. The header's H1 is **centred** on the page: every destination's headline — Progress,
  Settings, a workout named "5K Race Run" — sits in the same optical place, with the page actions
  floating at the end of the same band.
* **Immersive routes** (`player`, `welcome`) draw no shell at all.
* Destinations that are not in the bar (builders, details, health, history…) are reached from the
  pages that own them; starter templates are not a destination at all — they are a rail on Today.
* The shell carries no theme switch: appearance is configured in Settings → Appearance (and the
  sidebar footer), never from a sun/moon glyph that can flip colours mid-session.

---

## 6. Components

Everything lives in `ui/components/` and reads only from `LocalPulseColors`, `LocalPulseDimens`,
`LocalPulseShapes`, `LocalPulseElevation` and `LocalPulseMotion`.

| File | Provides |
| --- | --- |
| `Foundation.kt` | `PulseScaffold`, `PulseBreadcrumbBar`, `PulsePageHeader`, `PulseCard`, `PulseHeroCard`, `PulsePanel`, `SectionHeader`, `PulseDivider`, `PulseListRow` |
| `Layout.kt` | `PulseIconTile` / `PulseIconTileSize`, `PulseStartTile`, `PulseStat` / `PulseStatGrid`, `PulseCellGrid`, `pageContentPadding` |
| `Controls.kt` | `Primary/Secondary/Ghost/DangerActionButton`, `PulseIconButton`, `PulseTextField`, `OptionRow`, `PulseSelect`, `PulseActionMenu`, `PulseBottomSheet`, `PulseSheetRow`, `PulseChipGroup`, `PulseChoiceChip`, `ToggleRow`, `PulseSliderRow`, `DurationStepper`, `NumberStepper` |
| `Indicators.kt` | `LabelChip`, `PhaseChip`, `NeutralChip`, `CategoryChip`, `StatusBadge`, `StatTile`, `PulseMicroStat`, `MetricRow`, `InfoBanner`, `CategoryPicker` |
| `DataDisplay.kt` | `TimelineBar`, `ProgressRing`, `ActivityChart`, `ProgressBar` |
| `Feedback.kt` | `EmptyState`, `LoadingBox`, `ErrorState`, `PulseDialog`, `ConfirmDialog`, `PulseSnackbar` |
| `PulseLogo.kt` | Shared monochrome vector mark |
| `ThemePicker.kt` | Four theme-preview tiles, animated selection and theme toggle |
| `Navigation.kt` | `PulseNavItem`, `PulseNavGroup`, `PulseFloatingNavBar`, `PulseNavRail`, `PulseAppSidebar`, `PulseRailDivider` |

Rules the components encode:

* **One surface per idea.** A `PulseCard` groups; a `PulsePanel` nests *down* into a well; a table of
  numbers is one card with `MetricRow`s and `PulseDivider`s between them — not five stacked tiles.
  A `StatTile` is a card with a number in it: same surface, same radius, same hairline, same
  shadow. A tile that looked like a card but sat flat on the canvas was two products in one page.
* **One idea, one component.** The category badge at the head of a list row, on a grid card, at
  the head of a workout's page and on a category page is one component at four sizes
  (`PulseIconTile`), not four components that happen to look similar. The same is true of the four
  label chips — a phase, a category, metadata and a status are one `LabelChip` with a different
  colour and a different glyph — and of the two "start a session" tiles on the Today page, which
  are one `PulseStartTile`.
* **The whole row is the tap target.** Rows, cards and chips answer the pointer (hover → `surfaceHover`,
  press → a slightly deeper wash) before the tap lands.
* **A chip you tap is a control.** `PulseChoiceChip` — the library's filter rail and preset rows —
  draws `shapes.control` with a hairline border, exactly like a button; `PhaseChip`, `CategoryChip`,
  `NeutralChip` and `StatusBadge` label something and may draw the full `shapes.chip`. An enumerated
  *preference* is not a rail of chips: `OptionRow` is a sheet picker (`PulseSelect`) whose row shows
  the current value and whose `PulseBottomSheet` gives every label its full width with a check on
  the chosen one, and a cluster of secondary actions becomes one `PulseActionMenu` trigger opening
  the same sheet — never a row of side-by-side buttons, never a web-form drop-down. Options never
  sit inside a track or panel, and an option whose label is ellipsized is not a choice.
* **A dialog is a dialog.** `PulseDialog` is the shell: one radius, one elevation, one fill, one
  type ramp, one button system. `ConfirmDialog` and the builder's rename dialog are the same
  object, and neither of them is the platform's default.
* **A page has one measure and one gutter.** `PulseScaffold` caps the content and centres it;
  `pageContentPadding()` gives every scrolling page the same inset. A page that invents its own
  padding is a page whose heading is a few pixels higher than every other heading.
* **The title never collides with the actions.** `PulsePageHeader` centres the title on the page
  and insets it by the width of the action cluster on *both* sides, so the two never overlap; when
  the cluster is too wide for that — three icon buttons on a phone — the actions take a row of
  their own above the title.
* **States are explicit.** Every interactive component defines hover, pressed, focused, selected and
  disabled; focus is a 2 dp accent ring, disabled is `text_disabled` with no ripple.
* **Empty, loading and error states are components**, not ad-hoc text: `EmptyState`, `LoadingBox`,
  `ErrorState` are used by every screen that can be in one of those three states.

---

## 7. Accessibility

* Every measured pair in `docs/CONTRAST.md` clears WCAG 2.1 AA: 4.5:1 for text, 3:1 for large text and
  non-text UI. `pulse_border` and `pulse_track` are decorative and *not* contrast-constrained;
  `pulse_outline` is the token that marks a real control boundary.
* The **high-contrast** switch replaces phase hues, the outline and body text with strictly brighter
  variants (`PulsePalette.HighContrast*`), and the **dynamic colour** switch is gated behind a 4.5:1
  check against all neutral surfaces and its selection tint. It supplies a matching action fill and
  black/white foreground. High contrast disables wallpaper accents and theme-colour interpolation.
* Touch targets are at least 48 dp (`minTouchTarget`); icon buttons are 36 dp glyphs inside that
  target.
* Motion collapses to 0 ms under the reduced-motion switch; no screen animates when it is on.
* Colour is never the only carrier of meaning: phase chips carry their name, status badges carry a
  word next to the dot, and the activity chart keeps a 2 dp stub for a zero day so an empty day reads
  as a zero rather than as missing data.

---

## 8. Adding a screen

1. Wrap the content in `PulseScaffold { padding -> … }`; never draw your own top bar — the shell has it.
2. Start the content with `PulsePageHeader(title, onBack = …, trailing = …)`; put the
   screen's primary action in `trailing`.
3. Give the scrolling content `pageContentPadding()` — one gutter, one top inset, one bottom inset —
   and `dimens.cardGap` between sections.
4. Reach for `PulseCard` / `PulsePanel` / `PulseListRow` / `MetricRow` / `PulseIconTile` /
   `PulseStatGrid` before writing layout by hand. A row of statistics is `PulseStatGrid`, never a
   hand-written `Row` of weighted tiles: the grid is what makes the row wrap on a phone and sit
   three across on a tablet.
5. Take every colour from `LocalPulseColors`, every size from `LocalPulseDimens`, every radius from
   `LocalPulseShapes`, every duration from `LocalPulseMotion` and every responsive number from
   `LocalPulseLayout` — a raw hex value, a stray 13 dp or a `RoundedCornerShape(24.dp)` is what a
   review will catch.
6. Verify with the gates below; `check_ui.py` and `kotlin_xref_gate.py` catch the mechanical mistakes,
   `contrast_check.py` catches the optical ones.

---

## 9. Verification

| Gate | Command | Enforces |
| --- | --- | --- |
| Contrast & token parity | `python3 scripts/contrast_check.py` | 125 measured pairs ≥ AA, XML ↔ Kotlin token equality |
| Resource integrity | `python3 scripts/check_ui.py` | resource references resolve, vectors parse, en/fr string parity, bracket balance |
| Kotlin syntax | `python3 scripts/kotlin_parse_gate.py` | every `.kt` file parses (tree-sitter) |
| Cross-references | `python3 scripts/kotlin_xref_gate.py` | imports resolve, container/prefs members exist, AppRoot call signatures, `R.string` names |
| Brand integrity | `python3 scripts/check_brand.py` | shared SVG geometry, adaptive safe-zone inset, legacy splash vector |

The Android build itself runs in CI (`.github/workflows/build-release.yml`): the engine, sync,
library, app and wear unit tests plus the gates above on every pull request, with the test modules
running as parallel jobs; signed APKs on a `v*` tag.

## 10. Branding and theme interaction

`docs/brand/pulse.svg` is the normalized source. `ic_pulse.xml` is used by Compose and share images;
launcher, monochrome, notification and splash vectors use the same path. Adaptive launcher layers
place 50 dp of artwork, centred, in the 108 dp viewport — smaller than the 66 dp safe zone so no mask
shape clips the bars. The splash uses a vector drawable item, not a bitmap.

Settings and onboarding show System, Light, Dark and True black in a two-column preview grid. The
System preview is split light/dark. Selection animates its check and outline; pressing scales a tile
to 97%. Theme changes interpolate palette tokens over 260 ms without duplicating screen composition.
Route changes fade over 180–260 ms. All these durations respect Reduced motion; Android’s animator
scale also applies. The shell carries no quick theme toggle: a switch that can flip every colour mid-session lives behind Settings → Appearance, where the previews show exactly what changes.
