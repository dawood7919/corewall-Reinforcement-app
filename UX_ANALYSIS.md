# CoreWall QA/QC — UX Analysis & Redesign Strategy

**Phase 1 deliverable: analysis only.**
No mockups, no wireframes, no layouts, no code. Every claim below is a measurement
taken from the current source tree, not an impression.

- **Codebase measured:** 97 Kotlin files, 23,472 lines, of which 15,464 (66%) are UI.
- **Standards applied:** `ui-ux-pro-max` (priority table, `references/pro-rules.md`,
  `--domain ux` navigation rules, `--stack jetpack-compose` rules) and
  `mobile-android-design` (Material 3 / Compose best practices).
- **Precedence rule (from the directive):** where the current design conflicts with the
  skills, the skills win. Where the two skills conflict with each other, the stricter
  requirement wins.

---

## 0. Method

The directive mandates a fixed order: analyze the app → understand every screen →
every feature → UX problems → navigation problems → visual inconsistencies →
scalability issues → strategy. This document follows that order exactly.

Two rules were enforced while writing it:

1. **Nothing asserted without measurement.** Where a number appears, it came from a
   grep/count over the source. Where a judgement appears, it is labelled as judgement.
2. **No design work.** Section 9 states *decisions and criteria*, not values or layouts.
   Concrete scales, palettes, and screen structures are Phase 2 output.

---

## 1. What the product actually is

Stripped of its current UI, CoreWall is:

> A **single-operator field instrument** for one QA/QC engineer, standing on an active
> concrete floor, deciding whether that floor's reinforcement is correct and whether it
> can be poured.

Four properties follow from that, and they should drive every design decision:

| Property | Consequence |
|---|---|
| **One floor at a time is the entire mental model** | The active level is not a filter. It is the app's primary key. 48 levels exist (B02→ROOF); the user is on exactly one. |
| **The app computes; the AI interprets** | Numbers are deterministic (`ScheduleLogic`, `SteelCalculator`, `PourReadiness`, `FloorComparison`). The AI never invents a figure. The UI must make that distinction visible, because trust in the number is the product. |
| **Used one-handed, in daylight, on a dusty site, in gloves** | Touch targets, contrast, and glanceability are not polish. They are function. |
| **Arabic RTL, dense engineering data** | Layout mirroring and information density are baseline requirements, not options. |

The skill's design-system query for this product profile
(`"field engineering QA inspection tool data-dense professional" --density 8 --motion 4`)
returned **Style = "Data-Dense Dashboard"** — minimal padding, grid layout,
space-efficient, maximum data visibility, WCAG AA, excellent performance.
That is the correct target and it is *not* what the app currently is.

> Note on tool output: the same query also returned Pattern = "Newsletter / Content
> First". That is an auto-match artifact and is irrelevant to this product. Per the
> skill's own rule — "never present a 0-result search as if it returned data" — it is
> flagged and discarded rather than propagated.

---

## 2. Every screen (complete inventory)

31 screen-level composables exist. They fall into five structurally different classes,
which is itself the first finding.

### 2.1 Bottom-tab screens — Section COREWALL (5)

| Screen | Lines | Role |
|---|---|---|
| `MissionControlScreen` | 802 | Day dashboard: greeting, building journey, floor summary, metrics grid, mission checklist, smart alerts, health rings |
| `HomeScreen` | 463 | The plan canvas — the actual working surface. Lens selector, legend, command bar |
| `FilesScreen` | 836 | Data room: folders, grid/list, quick actions, upload/camera, per-file menu |
| `TasksScreen` | 353 | Floor-scoped tasks with edit dialog |
| `SettingsScreen` | 157 | Section settings |

### 2.2 Bottom-tab screens — Section MANPOWER (4)

| Screen | Role |
|---|---|
| `AttendanceScreen` | Daily attendance entry |
| `ManpowerReportsScreen` | Reports with charts |
| `ManpowerStatisticsScreen` | Aggregations, top-5, periods |
| `SettingsScreen` | **The same composable as COREWALL's settings tab** |

### 2.3 Overlay screens — `AppScreen` enum (13 declared)

`NOTIFICATIONS`, `SETTINGS`, `SYNC`, `ABOUT`, `FLOOR_NOTES`, `SITE_PHOTOS`,
`POUR_READINESS`, `AI_ANALYSIS`, `AI_SETTINGS`, `AI_CHAT`, `AI_KNOWLEDGE`,
`AI_PROJECT_KNOWLEDGE`, `AI_REPORTS`.

Rendered through a hand-written `when` in `MainActivity` inside `AppScreenScaffold`,
each with a hardcoded title string and a hardcoded back lambda.

### 2.4 Full-screen takeovers (5)

`PdfViewerScreen`, `CadViewerScreen`, `ImageViewerScreen`, `NoteEditorScreen`,
`AttendanceFileDetailScreen`. Each is driven by its own nullable `StateFlow` in the
ViewModel and each suppresses the copilot overlay.

### 2.5 Sheets and floating layers

`UnifiedSheet` / `ElementSheet` (582 lines) on element selection, plus
`AiCopilotOverlay` floating above every non-takeover screen.

### 2.6 **Unreachable screens — measured, not suspected**

`ui/home/AnalysisScreen.kt` is referenced by **nothing**. It is the only caller of
`AttentionScreen`, `ToolsScreen`, and `CountingReportScreen`. `AppScreen.AI_ANALYSIS`
is rendered in `MainActivity`'s `when`, but **no `openAppScreen(AppScreen.AI_ANALYSIS)`
call site exists anywhere in the codebase.**

| Dead screen | Lines | What the user has lost access to |
|---|---|---|
| `AnalysisScreen` | 58 | The container itself |
| `AttentionScreen` | 132 | **The gap detector** — schedule rows that leave a level uncovered |
| `ToolsScreen` | 238 | Engineering tools |
| `CountingReportScreen` | 163 | **The vertical-bar counting report** |
| `AiAnalysisScreen` | 173 | Floor AI analysis |
| **Total** | **764** | 5 of 31 screens (16%) are unreachable |

Two of these — gap detection and the counting report — are core to the product thesis.
They are built, they work, and no navigation path reaches them. This is not a styling
problem; it is an information-architecture failure that the current navigation model
made invisible.

---

## 3. Every feature (complete inventory)

| Domain | Feature | Status |
|---|---|---|
| **Plan** | Interactive plan canvas, 63 elements, pan/zoom, element selection | Reachable |
| | Three lenses: Reinforcement / Counting / Data over one canvas | Reachable |
| | Category colouring (wall / coupling beam / internal beam / other) | Reachable |
| **Schedule** | Wall ranges **end-exclusive** (`from ≤ level < to`), 31 marks / 118 rows | Engine |
| | Beam ranges **end-inclusive** (`from ≤ level ≤ to`), 49 marks / 80 rows | Engine |
| | Gap detection (level inside span, no covering row) | **Engine works, UI unreachable** |
| | Reinforcement diff vs. adjacent floor (`AttentionDiff`, `FloorComparison`) | Engine |
| **Inspection** | 5 statuses: NONE / WIR_SUBMITTED / APPROVED / CAST / REJECTED | Reachable |
| | Per-element per-level inspection state | Reachable |
| **Pour** | `PourReadiness` gate: blockers / warnings / info, with method note | Reachable (from Mission Control only) |
| **Counting** | Vertical bar counting, per-level `BarCountEntity` | Entry reachable, **report unreachable** |
| **Steel** | `SteelCalculator` weight/quantity | Engine |
| **Data** | Files, folders, upload, camera, PDF/CAD/image viewers | Reachable |
| | Markdown notes engine + rich editor | Reachable |
| | Floor-scoped tasks | Reachable |
| | Site photos with comment + date | Reachable |
| **Manpower** | Attendance, reports, statistics, PDF/CSV export | Reachable |
| **AI** | Chat with file/image attachment | Reachable |
| | Agent: 27 tools, reason→act→observe, 4 rounds, risk-gated | Reachable |
| | Always-on copilot overlay with local suggestions | Global |
| | Floor knowledge (isolated) vs. project knowledge (shared) | Reachable |
| | Document generation (daily report / inspection / material request) | Reachable |
| | 5 providers, user-supplied key, zero calls without a key | Reachable |
| **System** | Room v11, 16 entities, 13 migrations; backup/sync; notifications | — |

**Finding:** the engine layer is materially stronger than the surface layer. Several of
the app's most defensible capabilities — gap detection, cross-floor diff, steel
calculation — have either no UI or a UI the user cannot reach. The redesign's largest
single win is not new visual language; it is **exposing what already works**.

---

## 4. Measured audit against the skill standards

### 4.1 Accessibility — Priority 1 (CRITICAL)

Contrast ratios computed from the actual theme tokens in `ui/theme/Theme.kt`:

| Token pair | Ratio | Required 4.5:1 |
|---|---|---|
| `srt.text3` #8C92A0 on white | **3.12** | ✗ FAIL |
| `srt.text3` on `surface2` #F2F3F6 | **2.81** | ✗ FAIL |
| `srt.text3` on background #F7F8FA | **2.93** | ✗ FAIL |
| `srt.green` #34C759 on white | **2.22** | ✗ FAIL |
| `srt.orange` #FF9500 on white | **2.20** | ✗ FAIL |
| `srt.red` #FF3B30 on white | 3.55 | ✗ (large text only) |
| White on `srt.green` | **2.22** | ✗ FAIL |
| White on `srt.orange` | **2.20** | ✗ FAIL |
| `grayDot` #AEB2BC on white | 2.12 | ✗ FAIL |
| `srt.blue` #3A6EF0 on white | 4.50 | ✓ (exactly at threshold) |
| `srt.text3` dark #7E8592 on #16181F | 4.78 | ✓ |
| `srt.text3` site #8FB4D4 on #0E2A48 | 6.68 | ✓ |

`srt.text3` is the app's standard secondary-text colour and it fails on **every light
surface in the app**. The status colours — the colours that communicate *approved*
versus *rejected*, the highest-stakes semantic in the product — fail both as text on
white and as backgrounds under white text. On a sunlit site this is not a theoretical
WCAG note; it is unreadable.

Other Priority-1 measurements:

- **110** `contentDescription = null` versus **55** labelled — **67% of icons are
  invisible to TalkBack.** Many are the only affordance in their row.
- Dark theme passes where light fails, so the failure is specific to the light theme —
  which is the one used in daylight.

### 4.2 Touch & interaction — Priority 2 (CRITICAL)

- **20** raw `.clickable` modifiers with no enforced minimum size, against 43
  `IconButton` (which enforces 48dp). Every raw `.clickable` is a candidate violation of
  the 44×44 minimum. Only two files (`AiChatScreen`, `AiCopilotOverlay`) use an explicit
  44dp `TapTarget` wrapper — a good pattern that exists but was never generalised.

### 4.3 Animation — Priority 7

Skill requirement: 150–300ms, motion must convey meaning.

Measured `tween()` durations: **140, 180, 200, 240, 260, 320, 600, 650, 700, 800,
900 (×6), 1400 ms**. More than half the animation budget sits outside the permitted
band, with **900ms appearing six times**. Only 12 of 60 UI files animate at all, so the
app is simultaneously under-animated (no motion where motion would explain a state
change) and over-animated (long decorative durations where none was needed).

### 4.4 Typography & colour — Priority 6

- 11 Material type styles are used — reasonable.
- But **17 hardcoded `fontSize = N.sp`** sites bypass the scale (11, 12, 13, 16, 18, 21,
  24, 32, 34, 40, 48 sp), and **223 `fontWeight =` overrides** re-specify weight the
  scale should already carry. The type scale exists and is routinely overridden.
- **190** `Color(0x…)` literals inside `ui/`, **49 of them outside `ui/theme/`**,
  spanning **117 distinct hex values**.
- **87** raw `Color.White` / `Color.Black` literals — theme-blind by construction; these
  do not respond to dark mode at all.

### 4.5 Spacing, sizing, shape — `pro-rules.md` (4/8dp rhythm, token-driven sizing)

- **63 distinct `.dp` values**, of which **33 are off-grid** (not multiples of 4):
  1, 2, 3, 5, 6, 7, 9, 10, 11, 13, 14, 15, 17, 18, 19, 21, 22, 26, 27, 30, 31, 34, 38,
  42, 46, 50, 51, 58, 90, 130, 150, 230, 999.
- **41 distinct `.size()` values**, of which **23 fall in the icon range (≤32dp)**:
  4, 5, 6, 7, 8, 9, 10, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 24, 26, 27, 30, 32.
  The rule is explicit — "avoid mixing arbitrary values like 20pt/24pt/28pt randomly."
  The app mixes 23.
- **19 distinct corner radii**: 2, 4, 5, 6, 7, 8, 9, 10, 11, 12, 14, 16, 18, 20, 22, 24,
  26, 28, and 999 (pill).

Three sibling cards on the same screen can legally carry 17dp padding, an 18dp icon and
a 14dp radius while their neighbour carries 16/17/12. The eye reads this as
sloppiness even when it cannot name the cause.

### 4.6 Colour architecture — six parallel systems

`MaterialTheme.colorScheme`, `SrtColors`, `VizColors`, `CategoryColors`,
`AppGradients`, `StatusColors`. Six token namespaces, no defined precedence, overlapping
responsibilities. `srt.red`, `viz.critical`, `StatusColors(REJECTED)` and
`colorScheme.error` all mean "something is wrong" and are four different values chosen
per call site.

### 4.7 Jetpack Compose stack rules

| Rule (severity) | Measured | Verdict |
|---|---|---|
| Use `key` in Lazy lists (**High**) | 15 of 28 `items(` calls | ✗ 46% missing |
| Stable params / `@Immutable` (**High**) | **0** annotations in 97 files | ✗ none |
| `rememberSaveable` for config change (**High**) | 4 vs. **92** `remember { mutableStateOf }` | ✗ ~96% of local UI state is lost on rotation |
| Avoid nested scroll containers (**High**) | 17 `verticalScroll` sites | ⚠ requires per-site review |
| Typed routes via sealed class, not String (Medium) | `AppScreen` enum + `when` in Activity | ✗ |
| Navigation as events, not in UI (**High**) | Navigation called directly from composables | ✗ |
| Material3 tokens, not hardcoded values (**High**) | 117 distinct raw hex, 87 White/Black | ✗ |
| Stateless by default / hoist state (**High**) | Every screen takes `vm: MainViewModel` (1,252 lines) | ✗ |
| Precompute in VM, not UI (**High**) | Partially — some formatting done in composables | ⚠ |

The `MainViewModel` finding deserves emphasis: **every screen depends on the entire
ViewModel.** No screen declares what it actually needs, none can be previewed, none can
be tested in isolation, and any change to the ViewModel's surface is a change to all 31
screens' contract.

---

## 5. UX problems

**U1 — 16% of the product is unreachable.** Gap detection and the counting report are
built and orphaned (§2.6). The user cannot find features that exist.

**U2 — The pour decision is buried.** `PourReadiness` is the app's sharpest output — a
verdict on whether concrete can be poured — and it is reachable from exactly one card
inside an 802-line dashboard. It should be a destination, not a detail.

**U3 — Two different screens are both called "الإعدادات".** `SettingsScreen`
(bottom tab, both sections) and `AppSettingsScreen` (drawer → `AppScreen.SETTINGS`) are
distinct composables with the same label. The user cannot know which one holds the
setting they want.

**U4 — Mission Control is a wall, not a hierarchy.** 802 lines producing greeting +
journey + summary + metrics grid + mission list + alerts + health rings, with no stated
priority among them. The skill's Data-Dense Dashboard style permits density; it does not
permit *undifferentiated* density. Nothing on the screen says "this is the thing that
matters right now."

**U5 — Secondary text is unreadable in the light theme.** §4.1. This affects every
subtitle, every metadata line, every helper string.

**U6 — Status colour is the only carrier of status meaning.** Priority-10 rule:
"never rely on colour alone." Approved/rejected/cast are distinguished by hue, and those
hues fail contrast. A colour-blind engineer, or the same engineer in direct sun, loses
the distinction entirely.

**U7 — Rotation destroys work in progress.** 92 unsaved `remember` states versus 4
saved. Scroll positions, expanded rows, filter selections, half-typed input outside chat
— all lost.

**U8 — Two thirds of icons are unlabelled.** §4.1.

**U9 — Motion carries no meaning.** Long decorative durations in a few places; nothing at
all in the 48 files where a state transition actually needs explaining.

**U10 — The active level is a header, not a frame.** The whole product is
floor-scoped, but the floor reads as a label on top rather than as the container
everything sits inside. Floor isolation was a real defect earlier in this project; the
UI never made the boundary visible enough for the user to notice it had been breached.

**U11 — Two knowledge scopes look identical.** "ذاكرة الدور" (isolated to this floor) and
"معرفة" (shared across all floors) sit adjacent in the drawer, styled identically. The
distinction between them is the single most consequential concept in the AI feature and
it is carried entirely by a subtitle.

---

## 6. Navigation problems

The app runs **six concurrent navigation mechanisms** with no shared model:

1. Drawer with 2 workspace sections (Corewall / Manpower)
2. Bottom bar whose tab *set* changes with the section
3. 3 lenses (REINF / COUNT / DATA) that change the meaning of the plan canvas
4. A 13-entry `AppScreen` overlay enum rendered by a `when` in the Activity
5. 5 nullable-flow full-screen takeovers
6. A floating copilot overlay above all of it

**N1 — Back behaviour is a 9-branch hand-written cascade.** `BackHandler` in
`MainActivity` enumerates drawer → image → note → CAD → PDF → attendance → ABOUT →
appScreen → element → naming → `popTab()`. Skill rule `back-behavior` (High): back must
be predictable. Eleven conditions in a fixed priority order, with one special case
(`ABOUT` goes to `SETTINGS`, not back), is not predictable — it is memorised.

**N2 — No navigation state, no deep links, no restoration.** Skill rule `deep-linking`:
state should reflect the current view. Nothing here is addressable. The AI agent has a
`navigate` tool that can only reach `AppScreen` values, so the agent cannot send the user
to a lens, a tab, or an element.

**N3 — The drawer mixes four incompatible item kinds** under two headings. "الأدوات
الرئيسية" contains workspace switches (Corewall, Manpower), a lens switch (Data), and a
leaf screen (Site Photos). "التقارير" silently switches the entire section to Manpower
and selects tab 1. Items that do fundamentally different things look the same.

**N4 — Settings has three entry points and two destinations.** §U3.

**N5 — The last bottom tab of both sections renders the same composable.** Section
identity dissolves at the last tab.

**N6 — Depth is invisible.** Skill rule: breadcrumbs at 3+ levels. Reachable depth today
is section → tab → element sheet → full-screen viewer, with an overlay screen possible on
top. Nothing indicates where the user is in that stack.

**N7 — The copilot overlay occupies the bottom-start corner** — inside the thumb arc, in
an RTL layout, above a bottom navigation bar. It is suppressed for takeovers but not for
sheets. Skill rule: fixed navigation must not obscure content.

**N8 — Enum + `when` instead of typed routes.** Stack rule (Medium) violated; every new
screen requires edits in three places (enum, title `when`, content `when`), which is
exactly the mechanism by which `AI_ANALYSIS` ended up rendered-but-unreachable.

---

## 7. Visual inconsistencies

| # | Inconsistency | Measured |
|---|---|---|
| V1 | Spacing has no grid | 63 `.dp` values, 33 off-grid |
| V2 | Icon sizes are ad hoc | 23 distinct sizes ≤32dp |
| V3 | Corner radii are ad hoc | 19 distinct radii |
| V4 | Six parallel colour systems with no precedence | §4.6 |
| V5 | Raw hex bypasses tokens | 117 distinct hex, 49 sites outside `theme/` |
| V6 | Theme-blind literals | 87 `Color.White`/`Color.Black` |
| V7 | Type scale routinely overridden | 17 raw `fontSize`, 223 `fontWeight` |
| V8 | Motion durations inconsistent and out of band | 140→1400ms, six at 900ms |
| V9 | Card language differs per screen | `SrtGroupedList` rows, `MetricCard`, `FileGridCard`, `FileListRow`, `SuggestionRow`, `HeadlineCard` — six unrelated container idioms |
| V10 | Two "settings" visual languages | §U3 |
| V11 | Elevation/border used interchangeably for the same rank | Surfaces mix `shadowElevation` (8/16dp) and `BorderStroke(1.dp)` with no rule |

None of these is individually visible. Together they are the reason the app reads as
"assembled" rather than "designed" — which is precisely the complaint that prompted this
redesign.

---

## 8. Scalability issues

**S1 — Screen registration is manual and unchecked.** Adding a screen means editing the
enum, the title `when`, and the content `when`. Nothing verifies a screen has an entry
point. This already produced five orphans (§2.6) and will produce more.

**S2 — `MainViewModel` is a 1,252-line single point of coupling.** Every screen takes the
whole object. It cannot be split without touching all 31 screens; it cannot be previewed;
it cannot be tested per-screen. Growth pressure is unbounded — every new feature adds to
the same class.

**S3 — Zero stability annotations.** 0 `@Immutable`/`@Stable` in 97 files. Recomposition
cost grows with data volume, and the data volume is real (48 levels × 63 elements × 198
schedule rows). The Data-Dense Dashboard target increases the number of composables on
screen, which multiplies this.

**S4 — 46% of Lazy lists lack `key`.** Any list that reorders (files, tasks, suggestions,
knowledge documents) re-composes and loses item state. All of these lists are user-growable.

**S5 — Design tokens live in call sites, not in the theme.** 190 colour literals, 63
spacing values, 19 radii. A theme change today is a 190-site edit. This is the structural
reason no visual refresh has ever been fully applied — the last one left three of the
six colour systems behind.

**S6 — Two sections share one bottom bar, and a third is already implied.** Adding a
workspace means another `Section` enum value, another `tabsFor` branch, another `when`
arm in the Activity's routing, and another collision on the shared settings tab.

**S7 — RTL correctness is per-call-site.** Some code uses `Icons.AutoMirrored` correctly;
`start`/`end` padding is used in some places and `horizontal` in others. There is no
enforced convention, so RTL correctness degrades as files are added.

**S8 — The AI agent's reach is capped by the navigation model.** The agent can navigate
only to `AppScreen` values (13 of ~35 destinations). As the agent gains capability, the
navigation model becomes the binding constraint on what it can do for the user.

---

## 9. Redesign strategy

Ten decisions. Each states *what will be true*, the standard it satisfies, and the
measured problem it closes. **No values, layouts, or code — those are Phase 2.**

### D1 — The floor is the frame, not the header
The active level becomes the persistent container the user is *inside*, not a label on
top. Every data surface is unambiguously attributed to a floor, and the shared-knowledge
scope is visually a *different kind of place*, not a differently-worded row.
→ Closes U10, U11. Serves the product model (§1).

### D2 — One navigation model, typed and single-source
Collapse six mechanisms into one declarative destination graph with typed routes.
Every destination is declared once, carries its own title and back behaviour, and is
**unreachable-by-construction if it has no entry point.** Back becomes a stack pop, not a
cascade. Navigation is emitted as events from the ViewModel, not called from composables.
→ Closes N1, N2, N8, S1, S8. Satisfies stack rules `typed-routes`,
`navigation-as-events` (High), UX rules `back-behavior`, `deep-linking`.

### D3 — Rebuild information architecture around the decision, not the data
The destination hierarchy is derived from what the engineer must decide, in order:
*What is the state of this floor? → What is wrong with it? → Can I pour it?*
Everything currently orphaned is re-homed against that spine rather than re-added to a
menu. The pour verdict becomes a first-class destination.
→ Closes U1, U2, N3, N5, S6.

### D4 — Bottom navigation ≤5 items, stable across the app
The tab set stops changing identity underneath the user. Workspace switching stops
being a hidden side effect of drawer items. Duplicate destinations are eliminated —
one settings destination, one entry point.
→ Closes U3, N3, N4, N5. Satisfies `bottom-nav-limit`, `nav-hierarchy`.

### D5 — Single token system; call sites may not invent values
One colour namespace with defined precedence replaces six. One spacing scale on a 4/8
rhythm replaces 63 values. One icon-size set replaces 23. One radius set replaces 19.
One motion-duration set inside 150–300ms replaces the 140–1400ms spread.
**Enforcement rule: no raw hex, no raw `.dp`, no raw `fontSize` outside the theme
package.** The rule is what makes the system survive; the values are secondary.
→ Closes V1–V8, V11, S5. Satisfies `pro-rules.md` (4/8 rhythm, icon-size tokens,
token-driven theming), stack rule `material3-tokens` (High).

### D6 — Accessibility is a gate, not a pass
Every token pair is contrast-verified against its *actual* surfaces in both themes before
it enters the system. Status is carried by **shape + label + colour**, never colour alone.
Every interactive element has a content description. Every touch target meets 44×44 —
generalising the `TapTarget` pattern that already exists in two files.
→ Closes U5, U6, U8, and the raw-`.clickable` exposure. Satisfies Priorities 1, 2, 10.

### D7 — Density with hierarchy
Adopt the Data-Dense Dashboard target, but every dense surface must declare a primary
element. Density is earned by hierarchy; without it, it is just clutter. This is the
specific fix for Mission Control.
→ Closes U4. Satisfies the resolved style at `--density 8`.

### D8 — Screens become stateless and typed
Each screen takes a small, `@Immutable` state object and callbacks — not `MainViewModel`.
`MainViewModel` decomposes along the same seams. All derived values are precomputed in the
ViewModel. Every list gets a stable `key`. Anything the user can change locally is
`rememberSaveable`.
→ Closes U7, S2, S3, S4. Satisfies stack rules `stateless-by-default`,
`stable-parameters`, `lazy-key`, `remember-saveable`, `precompute-in-vm` (all High).

### D9 — Motion explains state changes only
Motion is spent on transitions that would otherwise be unexplained — a status changing, a
blocker clearing, a floor switching. Everything inside 150–300ms. Decorative loops are
removed.
→ Closes U9, V8. Satisfies Priority 7 at `--motion 4`.

### D10 — RTL and site conditions are constraints, not adaptations
Directional properties only (`start`/`end`, `AutoMirrored`), verified per surface. Contrast
targets are set for sunlight, not for a desk monitor — which means exceeding 4.5:1 on the
tokens that carry decisions, not merely meeting it.
→ Closes S7, reinforces D6. Serves the product model (§1).

### Sequencing principle
D5, D6, and D8 are foundational: they are the reason previous refreshes did not hold.
D2 and D3 are structural and depend on D8's decomposition. D1, D4, D7, D9, D10 are
expressive and land on top. **No visual surface should be designed before the token
system and the destination graph are decided**, or the redesign becomes the seventh
colour system.

---

## 10. Exit criteria for Phase 1 → Phase 2

Phase 2 (design) may begin when these are settled — all are decisions, none are drawings:

1. The destination graph: every destination named, its parent, its entry point.
2. The token namespaces and the precedence rule between them.
3. The state contract per screen (what each screen actually needs from the ViewModel).
4. The status-encoding scheme (which non-colour channel carries which status).
5. The density policy per surface class (what "primary element" means on each).

The measured baseline in this document is the regression test. After the redesign, these
numbers must move:

| Metric | Now | Target |
|---|---|---|
| Unreachable screens | 5 / 31 | 0 |
| Distinct `.dp` values | 63 (33 off-grid) | one scale, 0 off-grid |
| Distinct icon sizes ≤32dp | 23 | ≤4 |
| Distinct corner radii | 19 | ≤4 |
| Colour systems | 6 | 1 |
| Raw hex in `ui/` | 190 sites / 117 values | 0 outside `theme/` |
| `Color.White`/`Black` literals | 87 | 0 |
| Token pairs failing 4.5:1 | 9 measured | 0 |
| Unlabelled icons | 110 / 165 (67%) | 0 |
| `items(` without `key` | 13 / 28 (46%) | 0 |
| `@Immutable`/`@Stable` | 0 | every state class |
| `rememberSaveable` share | 4 / 96 (4%) | all user-visible local state |
| Screens depending on whole `MainViewModel` | 31 / 31 | 0 |
| Animation durations outside 150–300ms | 7 of 12 distinct | 0 |
| `BackHandler` branches | 9 | 1 (stack pop) |
