# PULSE design system — "Signal"

Signal is the visual layer PULSE ships today: a neutral, dense, enterprise-dashboard interface in the
spirit of the Cloudflare dashboard — charcoal chrome in the dark theme, paper chrome in the light
theme, **one** accent (Cloudflare orange) spent deliberately, hairline structure instead of shadows,
and Inter set in a compact dashboard scale.

It replaced "Prism" (violet → cyan accent gradients, 24 dp cards, pastel surfaces), which had drifted
into the generic AI-dashboard look: gradients as decoration, colour everywhere, and radii large enough
that nothing read as a tool. Signal is the opposite bet — **restraint reads as confidence**.

Nothing in this document is aspirational: every token, size and rule below is implemented in
`ui/theme/`, and `scripts/contrast_check.py` fails the build if the numbers stop being true.

---

## 1. Principles

1. **Neutral chrome, one accent.** Grey does the layout, the accent does the pointing. Orange appears
   on: the brand mark, the primary action of a screen, the selected navigation item, the focus ring,
   links, and live data. It is never a background wash and never a gradient.
2. **A hairline beats a shadow.** Surfaces are separated from the canvas by one fill step plus a
   1 dp border. Shadows are reserved for chrome that genuinely floats above the page — menus,
   dialogs, snackbars — so the few shadows on screen still carry meaning.
3. **Rectangles, not pills.** 6 dp controls, 8 dp cards, 12 dp overlays. The one exception is granted
   by *function*, not by size: whatever you tap is a rectangle — a button, a field, a stepper, and a
   filter or option chip too — while a chip that only labels something (a phase tag, a category name,
   a status badge, a progress track) may be fully round, because it carries no action.
4. **Dense, not cramped.** 4 dp base unit, 16 dp page gutter, 16 dp card padding, 56 dp list rows,
   40 dp controls. A dashboard fits a workout, its metadata and its action on one screen.
5. **Colour always means something.** Four interval phases, six category families, four semantic
   states. Every hue ships with the pre-composited tint it is drawn on, so a tinted chip's contrast
   is a measured number rather than an alpha estimate.
6. **Motion is felt, not watched.** 150 ms feedback, 200 ms selection, 250 ms overlays — and zero
   when the user turns on reduced motion.

---

## 2. Colour

Defined once in `app/src/main/res/values/colors.xml` and mirrored value-for-value in
`ui/theme/Palette.kt`. `scripts/contrast_check.py` exits **2** if the two files disagree (token drift)
and **1** if any measured pairing drops below its WCAG 2.1 threshold. The current run measures
**125 pairs** across the ink, void and paper themes; see `docs/CONTRAST.md` for the per-pair table.

### Ink (dark, the default theme)

| Token | Value | Role |
| --- | --- | --- |
| `pulse_canvas` | `#17181B` | app background |
| `pulse_nav` | `#1C1D21` | sidebar, icon rail, bottom bar, breadcrumb bar |
| `pulse_surface` | `#202226` | cards, panels, table surfaces |
| `pulse_surface_raised` | `#26282D` | inputs, chips, secondary buttons, icon tiles |
| `pulse_surface_overlay` | `#2A2D33` | menus, dialogs, snackbars |
| `pulse_surface_hover` | `#313439` | hover and pressed fill |
| `pulse_surface_sunken` | `#1B1C20` | wells: table headers, inset groups, text fields |
| `pulse_border` | `#34373D` | hairline separators (decorative) |
| `pulse_border_strong` | `#42464D` | nested divider, field edge that needs to be visible |
| `pulse_outline` | `#8A9099` | meaningful control boundary (measured at 3:1) |
| `pulse_text_primary` | `#F5F6F7` | titles, body, values |
| `pulse_text_secondary` | `#C3C7CC` | supporting copy, list subtitles |
| `pulse_text_muted` | `#9CA2A9` | labels, captions, timestamps |
| `pulse_text_disabled` | `#6E747C` | disabled only (exempt from 4.5:1) |
| `pulse_track` | `#3A3E45` | slider, ring and chart grooves |

### Paper (light)

Same roles, inverted polarity, and a *paper* canvas (`#F5F6F7`) with white surfaces, so a card edge
is legible without a shadow. The accent splits in two, which is the one asymmetry between the themes:

| Token | Value | Role |
| --- | --- | --- |
| `pulse_light_canvas` | `#F5F6F7` | app background |
| `pulse_light_nav` | `#FFFFFF` | chrome |
| `pulse_light_surface` | `#FFFFFF` | cards |
| `pulse_light_surface_raised` | `#F7F8F9` | chips, secondary buttons |
| `pulse_light_surface_sunken` | `#F1F2F4` | wells, text fields |
| `pulse_light_border` | `#E4E6E8` | hairline separators |
| `pulse_light_outline` | `#6B7280` | control boundary (3:1) |
| `pulse_light_text_primary` | `#1B1E21` | titles, body |
| `pulse_light_text_secondary` | `#4B5157` | supporting copy |
| `pulse_light_text_muted` | `#666C74` | labels, captions |
| `pulse_light_accent` | `#B23A0B` | accent as **text**: links, selection, focus |
| `pulse_light_brand` | `#F38020` | accent as **fill**: primary buttons, brand mark |

### Void (OLED)

`pulse_black_*` replaces only the surfaces with pure black (`#000000` canvas and chrome, `#0B0C0E`
cards, `#23262A` hairlines) and reuses the Ink text, border and accent values. It is a surface
override, not a fourth palette — which is why the contrast report re-measures it rather than trusting
it.

### The accent rule

`pulse_accent` is the interactive accent and `pulse_brand` the brand fill; in the dark theme they are
the same `#F38020`, and in the light theme the accent deepens to `#B23A0B` so that orange **text**
clears 4.5:1 on paper. Practical consequences:

* a **primary button** is a brand-coloured fill with ink on it (`action_text`), in both themes — that
  pair is measured, not assumed;
* a **link, selected nav label or focus ring** uses `accent`, so it is legible in both themes;
* `accent_tint` is the pre-composited container for a selected row or an active nav item;
* `accent_alt` is a lighter stop used **only as a graphic** (a bar, a rank badge), never as a label.

### Interval phases and semantic states

| Family | Dark | Light | Used for |
| --- | --- | --- | --- |
| `work` | `#4CC38A` | `#14754A` | work intervals, success, "connected" |
| `rest` | `#6CABF8` | `#0B5EA8` | rest and transitions |
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
| `cat_mobility` | `#7DD3FC` | `#0A5C8A` | stretching, mobility flows |
| `cat_mind` | `#4CC38A` | `#106544` | breathing, focus |
| `cat_tools` | `#98A2B3` | `#3F4753` | stopwatch, repeating cue, customs |

### The ink panel

`pulse_hero_surface` (`#1B1C20` / `#1B1E21`) is deliberately dark in **both** themes. It is used twice
in the whole app — the Today hero and the session summary — so the one panel that carries the page's
headline never looks like one more card in a stack.

---

## 3. Typography

One family: **Inter**, in four static weights (Regular, Medium, SemiBold, Bold) subset to Latin, with
its SIL OFL licence shipped in `res/raw/inter_ofl.txt`. It is the face the Cloudflare dashboard is
built on, and it earns its place here because 12–14 sp dashboard text stays legible mid-workout, its
digits exist in a tabular variant, and it is metrically close to the platform faces.

Numbers are Inter with **tabular figures** (`fontFeatureSettings = "tnum"`) rather than a monospace
face: a countdown ticking from `1:00` to `0:59` must not shift a pixel, and the rest of the type
system stays one family. `PulseType` holds the numeric styles; `PulseFonts.Mono` remains for the one
place a fixed advance width is genuinely wanted (the cue-script preview).

| Style | Size / weight | Used for |
| --- | --- | --- |
| `displaySmall` | 32 sp Bold | onboarding headline |
| `headlineLarge` | 22 sp SemiBold | page titles (`PulsePageHeader`) |
| `headlineMedium` | 19 sp SemiBold | onboarding step headings |
| `headlineSmall` | 17 sp SemiBold | cards, the breadcrumb leaf, dialog titles |
| `titleMedium` | 15 sp SemiBold | list-row titles, card headings |
| `titleSmall` | 14 sp SemiBold | section headers, field labels |
| `bodyMedium` | 15 sp Regular | body copy, metric-row labels |
| `bodySmall` | 13 sp Regular | metadata, subtitles, captions |
| `labelMedium` | 12 sp Medium | chips, badges, quiet actions |
| `labelSmall` | 11 sp Medium | uppercase micro-labels |
| `PulseType.NumericDisplay` | 60 sp Bold tabular | player countdown |
| `PulseType.NumericLarge` | 30 sp SemiBold tabular | hero figures |
| `PulseType.NumericMedium` | 17 sp SemiBold tabular | stat tile values |
| `PulseType.NumericSmall` | 13 sp Medium tabular | inline numbers |
| `PulseType.SectionLabel` | 11 sp SemiBold, +0.8 tracking | sidebar group labels |

`pulseTypography()` binds Material's role scale to the same table, so a component that reads
`MaterialTheme.typography.<role>` inherits the Signal rhythm instead of Material's defaults.

---

## 4. Space, size, radius, elevation, motion

| Token | Value | Notes |
| --- | --- | --- |
| `microGap` / `xs` / `s` / `m` / `l` / `xl` / `xxl` / `huge` / `massive` | 2 / 4 / 8 / 12 / 16 / 20 / 24 / 32 / 48 dp | 4 dp base unit |
| `pagePadding` | 16 dp | every scrolling screen shares it |
| `cardPadding` | 16 dp | `PulseCard` interior |
| `cardGap` | 12 dp | between sibling cards |
| `chipGap` | 6 dp | inside a cluster of chips |
| `controlHeight` / `buttonHeight` | 40 dp | buttons, inputs, segmented controls |
| `iconButtonSize` | 36 dp | inside a 48 dp touch target |
| `listRowHeight` | 56 dp | `PulseListRow` |
| `metricRowHeight` | 44 dp | `MetricRow` |
| `topBarHeight` | 56 dp | breadcrumb bar (before the status-bar inset) |
| `navBarHeight` | 64 dp | phone bottom bar |
| `railWidth` / `sidebarWidth` | 72 / 244 dp | tablet rail, desktop sidebar |
| radii | 6 / 8 / 12 / full | controls, panels and every tappable chip / cards / overlays / label chips only |
| elevation | 0 / 1 / 4 / 12 dp | flat / interactive / overlay / floating chrome |
| motion | 150 / 200 / 250 ms | feedback / selection / overlay — 0 under reduced motion |

---

## 5. The shell

The application frame is one navigation model in three shapes, and the shape follows the window:

| Window width | Navigation | Chrome |
| --- | --- | --- |
| `< 600 dp` (phone) | bottom bar, indicator behind the active glyph, hairline above | breadcrumb bar + brand mark |
| `>= 600 dp` (tablet, portrait) | 72 dp icon rail with the mark at its head and the theme toggle in its footer | breadcrumb bar |
| `>= 1000 dp` (desktop, tablet landscape) | 244 dp sidebar: wordmark, grouped destinations, 2 dp accent edge on the active row, appearance switch in the footer | breadcrumb bar |

* **Breadcrumb bar** (`PulseBreadcrumbBar`) — one 56 dp row: where you are on the left, the global
  controls (theme, settings) on the right, a hairline under it. It owns the status-bar inset, so no
  screen ever reasons about insets.
* **Back navigation lives in the page**, not in the bar (`PulsePageHeader(onBack = …)`), because a
  page knows whether leaving it needs a confirmation — an unsaved builder does, and the shell does
  not.
* **Immersive routes** (`player`, `welcome`) draw no shell at all.
* Destinations that are not in the bar (templates, builders, details, health, history…) are reached
  from the pages that own them, which keeps the bar to four items on a phone and seven in the sidebar.

---

## 6. Components

Everything lives in `ui/components/` and reads only from `LocalPulseColors`, `LocalPulseDimens`,
`LocalPulseShapes`, `LocalPulseElevation` and `LocalPulseMotion`.

| File | Provides |
| --- | --- |
| `Foundation.kt` | `PulseScaffold`, `PulseBreadcrumbBar`, `PulsePageHeader`, `PulseCard`, `PulseHeroCard`, `PulsePanel`, `SectionHeader`, `PulseDivider`, `PulseListRow` |
| `Controls.kt` | `Primary/Secondary/Ghost/DangerActionButton`, `PulseIconButton`, `PulseTextField`, `OptionRow`, `PulseSegmented`, `PulseChoiceChip`, `ToggleRow`, `PulseSliderRow`, `DurationStepper`, `NumberStepper`, `PulseDropdown` |
| `Indicators.kt` | `PhaseChip`, `NeutralChip`, `CategoryChip`, `StatusBadge`, `StatTile`, `MetricRow`, `InfoBanner`, `CategoryPicker` |
| `DataDisplay.kt` | `TimelineBar`, `ProgressRing`, `ActivityChart`, `ProgressBar` |
| `Feedback.kt` | `EmptyState`, `LoadingBox`, `ErrorState`, `ConfirmDialog`, `PulseSnackbar` |
| `Navigation.kt` | `PulseNavItem`, `PulseNavGroup`, `PulseFloatingNavBar`, `PulseNavRail`, `PulseAppSidebar`, `PulseRailDivider` |

Rules the components encode:

* **One surface per idea.** A `PulseCard` groups; a `PulsePanel` nests *down* into a well; a table of
  numbers is one card with `MetricRow`s and `PulseDivider`s between them — not five stacked tiles.
* **The whole row is the tap target.** Rows, cards and chips answer the pointer (hover → `surfaceHover`,
  press → a slightly deeper wash) before the tap lands.
* **A chip you tap is a control.** `PulseChoiceChip` — the library's filters and the builder's option
  rows — draws `shapes.control` with a hairline border, exactly like a button; `PhaseChip`,
  `CategoryChip`, `NeutralChip` and `StatusBadge` label something and may draw the full `shapes.chip`.
  `OptionRow` lays its choices out two to a row, because an option whose label is ellipsized is not a
  choice.
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
  check against the themed surface, so a system accent can never make a label unreadable.
* Touch targets are at least 48 dp (`minTouchTarget`); icon buttons are 36 dp glyphs inside that
  target.
* Motion collapses to 0 ms under the reduced-motion switch; no screen animates when it is on.
* Colour is never the only carrier of meaning: phase chips carry their name, status badges carry a
  word next to the dot, and the activity chart keeps a 2 dp stub for a zero day so an empty day reads
  as a zero rather than as missing data.

---

## 8. Adding a screen

1. Wrap the content in `PulseScaffold { padding -> … }`; never draw your own top bar — the shell has it.
2. Start the content with `PulsePageHeader(title, description, onBack = …, trailing = …)`; put the
   screen's primary action in `trailing`.
3. Use `dimens.pagePadding` for the gutter, `dimens.cardGap` between cards and `dimens.huge` of bottom
   padding so the last row clears the phone's navigation bar.
4. Reach for `PulseCard` / `PulsePanel` / `PulseListRow` / `MetricRow` before writing layout by hand.
5. Take every colour from `LocalPulseColors`, every size from `LocalPulseDimens`, every radius from
   `LocalPulseShapes`, every duration from `LocalPulseMotion` — a raw hex value, a stray 13 dp or a
   `RoundedCornerShape(24.dp)` is what a review will catch.
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
| Static UI check | `python3 /tmp/px/uicheck.py .` (dev tool) | token members, named arguments, icon imports, project symbols imported |

The Android build itself runs in CI (`.github/workflows/build-release.yml`): `:engine:test` and
`:app:testDebugUnitTest` on every pull request, signed APKs on a `v*` tag.
