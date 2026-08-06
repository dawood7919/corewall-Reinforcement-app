# CoreWall — Module Redesign Study

**Scope:** AI Chat · File Manager · Plan Canvas · Notes · Manpower
**Status:** research and architecture proposal. No implementation until approved.
**Date:** August 2026 · against commit `7c4203f` (v8.1, build 33)

---

## 0. The finding that reframes the brief

I measured before proposing. Two numbers matter more than everything else in this document:

| Measurement | Value |
|---|---|
| Third-party dependencies in the whole app | **15** |
| Of those, libraries doing UI/media/network work | **0** |

The app hand-rolls: HTTP (`HttpURLConnection`), image loading and thumbnail decoding, Markdown parsing and rendering, PDF rendering, PDF annotation, charts (raw `Canvas`), pan/zoom gestures, and list virtualisation strategy.

Module sizes:

| Module | Lines | What it hand-rolls |
|---|---:|---|
| AI Chat | 6,460 | HTTP, SSE, Markdown parse + render, JSON repair |
| Plan Canvas | 2,130 | pan/zoom, hit-testing, rendering, CAD parsing |
| Manpower | 1,649 | donut/line/bar charts on raw Canvas |
| File Manager | 1,556 | thumbnails, bitmap decode, file tree |
| Notes | 1,330 | Markdown block parser, inline styling, editor |

**So the brief's premise needs adjusting.** You asked for a UX redesign and said "don't reinvent solved problems." The audit says the *reinvention already happened* — that is the actual defect. The highest-leverage work here is not new screens; it is deleting roughly 4,000 lines of hand-rolled infrastructure and replacing it with maintained libraries. The visual redesign then rides on top almost for free, because these libraries are already themeable through the design system built in v8.0.

That reordering is the single most important recommendation in this document.

---

## 1. UX Audit

### 1.1 A caution about the reference apps

You named Linear, Notion, Arc, Raycast, Superlist, Figma Mobile. I want to be direct about something before designing against them, because getting this wrong would make the app worse while looking better.

**Every one of those is a desk product for an unhurried, two-handed, indoor, connected user.** Notion's interaction model assumes you are exploring. Linear assumes a keyboard. Raycast assumes a command palette is faster than a button — true when you type 90 wpm on a laptop.

CoreWall's user is on a slab. Likely conditions:

- Direct sunlight (why the contrast work in v8.0 mattered — the "approved" colour was at 2.22:1)
- Gloves, or wet hands
- One hand, because the other holds a drawing, a tape, or a rail
- Intermittent or absent signal
- Time pressure — a pour is a scheduled, expensive, irreversible event

Where the reference apps and the site conflict, **the site wins**. Concretely, this means I will propose:

- ✅ Adopt from them: typographic restraint, meaningful motion, information density with hierarchy, keyboard/command affordances *as an accelerator, never as the only path*
- ❌ Reject: hover-dependent affordances, thin-stroke iconography, low-contrast "elegant" greys, gesture-only actions with no visible equivalent, multi-step creation flows

The premium feel should come from **confidence and speed**, not from delicacy. Bluebeam and Procore feel professional because they are dense and instantaneous, not because they are pretty.

### 1.2 Per-module audit

**AI Chat** — the largest module and the weakest architecture. `AiHttpClient` uses `HttpURLConnection` and reads the whole response body before parsing. That single fact makes streaming impossible: the "thinking indicator" you asked for cannot be honest, because the app has no idea whether the model has produced one token or nine hundred. The Markdown renderer is a hand-written block parser — it will keep failing on nested lists, tables inside blockquotes, and fenced code inside list items, and each fix is bespoke. There is no conversation persistence: no history, no search, no pinning, no folders — the chat is ephemeral state in a ViewModel.

**File Manager** — thumbnails decode bitmaps on the main-thread-adjacent path with a hand-rolled `rememberThumb`, no memory cache, no disk cache, no request cancellation on scroll. On a folder of 200 site photos this is the most likely place the app drops frames or OOMs. No tags, no favourites, no recents, no multi-select, no version history — those are absent features, not weak ones.

**Plan Canvas** — `detectTransformGestures` with a manual scale/offset, then every element re-projected and re-drawn every frame with no viewport culling and no level-of-detail. With 63 plan elements it is fine; it will not survive a real DWG import. No layers, no measurement tools, no undo, no selection handles, no mini-map.

**Notes** — a Markdown block parser plus a custom editor. The `NoteEntity` has `elementId` and `level`, so notes already link to elements and floors — that skeleton is right and worth keeping. Everything above it (editor, renderer, toolbar) is reimplementation.

**Manpower** — charts drawn by hand on `Canvas`: no axis labels beyond the minimum, no tooltips, no accessible values, no animation, no empty/error states. The data layer (`AttendanceFileEntity` + `DailyAttendanceEntity`) is sound. There is no cost data anywhere in the schema, so "cost tracking" is a new subsystem, not a screen.

---

## 2. Architecture Review

### 2.1 Current shape

```
MainActivity → AppShell (router, v8.0) → Screens
                     ↓
              MainViewModel  ← one class, all state for every screen
                     ↓
              Repository → Room (v11, 15 entities, 11 migrations)
                     → FilesManager (java.io)
                     → AiEngine → AiHttpClient (HttpURLConnection)
```

**What is right and must be preserved:**

- The domain layer is pure Kotlin with no Android imports (`ScheduleLogic`, `PourReadiness`, `FloorComparison`, `SteelCalculator`, `FloorSummary`). This is genuinely good and unusual. Every deterministic answer the app gives is testable without a device.
- The floor-isolation invariant. Everything is scoped to the active level; `KnowledgeScope.PROJECT` is the one deliberate exception.
- The v8.0 navigation: typed `Dest`, one back stack, one pop rule.
- The principle: **the app computes, the AI explains.** No model output is ever load-bearing for a pour decision.

**What blocks the requested features:**

| Blocker | Blocks |
|---|---|
| `HttpURLConnection`, no streaming | Streaming responses, thinking indicator, token counting |
| `MainViewModel` is one god object (~1,300 lines) | Independent module state, testability, memory scoping |
| No pagination | Large file lists, long conversations, attendance history |
| No image loader | Thumbnails, grids, fast scrolling |
| No cost/crew/trade schema | Cost tracking, forecasting, crew assignment |
| No FTS index | Conversation search, note search, file search |

### 2.2 Proposed shape

Two structural changes, both incremental — no rewrite, no big-bang.

**(a) Split `MainViewModel` per module.** Keep it as the shell/app-level VM; add `ChatViewModel`, `FilesViewModel`, `CanvasViewModel`, `NotesViewModel`, `ManpowerViewModel`. Each owns its own state and is scoped to its destination, so a 400-message conversation is not held in memory while you are counting rebar.

**(b) Add a repository per module** behind the existing single `Repository`, so Room access is not one 1,000-line class.

I am explicitly **not** recommending Hilt. The app uses a manual service locator in `CoreWallApp`; it works, it is comprehensible, and adding Hilt would be a multi-day refactor that improves nothing the user can perceive. If the team grows past ~4 engineers, revisit.

---

## 3. User Journey Analysis

Five journeys drive every design decision below. Each is timed against the current app.

**J1 — "Can I pour tomorrow?"** (daily, high stakes)
Today: 3 taps → verdict. This is already good; v8.0 fixed it. **Do not touch.**

**J2 — "What changed on floor 12?"** (weekly, high stakes)
Today: Checks → Gaps → read diff. Works. Missing: the ability to ask that question in chat and get the same *computed* answer with citations.

**J3 — "Find the BBS for wall W12"** (many times daily)
Today: Data → Files → scroll a flat list → open PDF → pinch to find the table. **This is the worst journey in the app.** No search, no tags, no OCR, no in-PDF text search. Target: ≤2 taps + typing, ≤3 seconds.

**J4 — "Log what I saw at the wall"** (many times daily, gloves on, one hand)
Today: Data → Notes → new → type. Voice is absent. Camera is a separate screen. Target: one tap from anywhere → voice or photo → auto-linked to the element you last touched.

**J5 — "How many men on 14 this week, and is that normal?"** (weekly)
Today: Manpower → Reports → read static numbers. No trend, no comparison, no anomaly signal.

**The journeys tell you the priority order** — and it is not the order in the brief. J3 and J4 are the highest-frequency, highest-friction journeys. **File Manager and Notes capture should come before AI Chat polish.**

---

## 4. Information Architecture

No change to the five bottom tabs — v8.0 fixed that and churning it again would be its own defect. The changes are *within* tabs.

```
Today · Plan · Checks · Data · Assistant     ← unchanged, stable
                         │        │
                         │        └── Chat (threads, folders, pins, search)
                         │            Knowledge (floor / project scope)
                         │            Documents
                         │
                         └── Files · Tasks · Notes · Photos   ← unchanged tabs
                             but each gains: search, tags, multi-select
```

**One new cross-cutting surface: a command sheet.** Long-press the FAB or pull down on any list → search everything (files, notes, marks, tasks, conversations) with typed filters. This is the Raycast idea, adopted *correctly*: an accelerator layered over navigation that still fully works without it.

**One new relation: the link graph.** `NoteEntity` already carries `elementId` + `level`. Generalise to a `LinkEntity(fromType, fromId, toType, toId)` so a note, a photo, a file, an inspection and a pour can reference each other. This is what makes "every note linked to drawings, floors, elements, inspections, pours and manpower" real rather than decorative — and it is ~1 table plus 1 migration.

---

## 5. Screen-by-screen proposals

### 5.1 AI Chat

**Structure:** thread list (pinned → folders → recent) → thread. Not a single ephemeral scroll.

- **Streaming.** Replace `HttpURLConnection` with OkHttp + SSE. Tokens arrive and render as they land. The thinking indicator becomes honest: it reflects an actual open stream, not a spinner.
- **Markdown.** Delete the hand-written parser. Adopt `multiplatform-markdown-renderer` (§6.1) — tables, nested lists, fenced code all handled and maintained.
- **Context transparency.** A slim bar showing tokens used / model / context remaining. Estimation client-side is approximate; **label it as an estimate**, do not present a guess as a measurement.
- **Grounding.** When the assistant states a number the app computed (gap count, approved %, steel weight), render it as a chip that links to the screen that computed it. This is the single most valuable chat feature for this product, and no consumer chat app has it because none of them has a deterministic engine underneath.
- **Voice input:** Android `SpeechRecognizer`, offline-capable on modern devices. Cheap, high value for J4.

**Motion:** message enters with a 200 ms fade + 8 dp rise. Streaming text does *not* animate per token — that is nausea, not polish.

### 5.2 File Manager

- **Coil 3** for every thumbnail (§6.2): memory + disk cache, automatic cancellation on scroll, no more custom decode path.
- **Grid / list toggle**, persisted per folder.
- **Search** over filename + tags + OCR text, backed by **Room FTS4**.
- **OCR** via ML Kit Text Recognition v2 (§6.5), on-device, run once on import into a `DocTextEntity`. This makes J3 work: "W12" finds the BBS page that contains it.
- **Tags + favourites + recents**: three small tables.
- **Multi-select** with a contextual action bar; **swipe** for the single most common action only (share), because swipe is invisible and must never be the only route.
- **PDF:** replace the custom viewer with **androidx.pdf** (§6.3) — official, with progressive rendering, only-visible-page bitmaps, and text selection. It also brings annotation via androidx.ink.

**Honest limit — DWG.** There is no viable open-source DWG reader for Android. DWG is a closed, versioned format; the real options are the Open Design Alliance SDK (commercial, ~$$$/yr) or server-side conversion to SVG/PDF. **Recommendation: convert to PDF upstream and drop DWG from scope.** I would rather say this now than build a half-parser that fails on files from a newer AutoCAD.

### 5.3 Plan Canvas

This is the module where I would build *more* rather than adopt, because the domain is specific.

- **Keep** the custom renderer — the plan is 63 rectangles with domain meaning, not a generic image. But add: **viewport culling** (skip off-screen elements), **level-of-detail** (no labels below a zoom threshold), and hoist projection out of the draw loop.
- **Gestures:** adopt `Modifier.zoomable` from **telephoto** (§6.4) rather than hand-rolling — it already handles fling, edge resistance, and bounds correctly.
- **Layers panel:** categories, statuses, annotations, measurements — each toggleable. This is the single most requested feature in Bluebeam-class tools.
- **Measurement + snapping:** snap to element edges/centres; store measurements as entities so they persist and export.
- **Undo/redo:** a command stack in the domain layer, testable without a device.
- **Mini-map** at high zoom; **floating tool palette**, one-handed reachable (bottom third, thumb side).

**Performance target:** honest version — 60 FPS sustained on a mid-range device with the real 63-element plan and 200 annotations. 120 FPS is a marketing number; hitting it depends on the panel and I will not promise it before measuring.

### 5.4 Notes

- **Editor:** adopt **compose-rich-editor** (§6.6). It is WYSIWYG, handles Markdown *and* HTML, and ships rich-text-aware undo/redo — which is the part that is genuinely hard and that the current editor does not do.
- **Templates** for the recurring site documents: inspection note, NCR, daily log, pour record.
- **Linking:** `@` mentions resolving to elements, floors, files, inspections — powered by the `LinkEntity` from §4. A note about W12 shows up when you open W12.
- **Voice notes:** record + store + transcribe on-device.
- **AI actions** (summarise / rewrite / translate / extract actions) as an explicit menu — never automatic, never silent.
- **Version history:** append-only `NoteRevisionEntity` on save. Cheap, and matters in a QA context where a note is evidence.

**Honest limit — handwriting.** androidx.ink is **alpha** as of July 2026. Freehand drawing is achievable; *handwriting recognition* is not, without ML Kit Digital Ink (a separate model download). Recommendation: ship freehand annotation, defer recognition.

### 5.5 Manpower

- **Charts:** delete the hand-drawn Canvas charts, adopt **Vico** (§6.7) — 2.4.3 stable, Compose-first, animated, accessible, themeable through the existing palette.
- **Timeline + calendar** view of attendance per floor.
- **Trade distribution**, **productivity** (men per completed element per day — derivable from data that already exists).
- **Heatmap** floor × week.
- **Anomaly flags** — "14 is running 40 % below its 4-week average" is a computed statement, and should be computed, not asked of a model.

**Honest limits.** Cost tracking needs rates per trade — a new schema and a data-entry surface; it is a feature, not a chart. "Forecasting" on a few weeks of single-project attendance would produce a confident-looking line with no predictive value — **I recommend against it** until there is a season of data, and even then presented as a range, never a number.

---

## 6. Libraries — verified, with reasoning

Every entry below was checked against its repository or release feed during this study. Status is as of August 2026.

### 6.1 Markdown — `mikepenz/multiplatform-markdown-renderer`
**Latest:** 0.38.0-b01 · Apache-2.0 · actively maintained
**Replaces:** ~600 lines of hand-written block parser + renderer
**Why:** async parsing since 0.33, Material 3 theming path, optional syntax highlighting. Nested-list and table handling that the custom parser will never fully reach.
https://github.com/mikepenz/multiplatform-markdown-renderer

### 6.2 Images — `coil-kt/coil` 3.x
**Latest:** 3.5.0 · Apache-2.0 · actively maintained
**Replaces:** custom `rememberThumb` decode path
**Why:** memory + disk cache, automatic cancellation on scroll, coroutine-native. This is the direct fix for file-grid scroll performance.
https://github.com/coil-kt/coil

### 6.3 PDF — `androidx.pdf` (Jetpack)
**Status:** official AndroidX library
**Replaces:** custom PDF viewer *and* the custom annotation layer (`PdfAnnotationEntity`)
**Why:** two-pass progressive rendering, releases off-screen page bitmaps (the memory fix for large drawings), text selection, form fields, and annotation tooling built on androidx.ink.
https://developer.android.com/jetpack/androidx/releases/pdf

### 6.4 Zoom/pan — `saket/telephoto`
**Why:** `Modifier.zoomable` is designed to be shared across any composable including canvas and text — not just images. Correct fling, bounds and edge behaviour out of the box.
Alternative if a smaller surface is preferred: `usuiat/Zoomable`.
https://github.com/saket/telephoto · https://github.com/usuiat/Zoomable

### 6.5 OCR — ML Kit Text Recognition v2
**Why:** fully on-device — no network call, no cloud cost, no privacy exposure. Consistent with the app's local-only data posture. This is what makes file search actually useful.
https://developers.google.com/ml-kit/vision/text-recognition/v2/android

### 6.6 Rich text — `MohamedRejeb/compose-rich-editor`
**Latest:** 1.0.0-rc14 · actively maintained (April 2026 activity)
**Replaces:** custom note editor
**Why:** WYSIWYG, HTML + Markdown, **rich-text-aware undo/redo**. Caveat: still RC — pin the version and test the Arabic/RTL path before committing, as RTL is this app's primary direction.
https://github.com/MohamedRejeb/compose-rich-editor

### 6.7 Charts — `patrykandpatrick/vico`
**Latest:** 2.4.3 stable · releases through June 2026
**Replaces:** hand-drawn Canvas charts
**Why:** Compose-first, extensible, themeable from the existing palette so chart colours stay CVD-validated.
https://github.com/patrykandpatrick/vico

### 6.8 Blur/glass — `chrisbanes/haze`
**Latest:** 1.7.x · built on Compose 1.7 GraphicsLayer APIs
**Why:** hardware-accelerated. **Use sparingly** — blur costs GPU time, and in sunlight a frosted bar reduces contrast, which is the opposite of what this app needs. Recommended only for the modal scrim and the command sheet.
https://github.com/chrisbanes/haze

### 6.9 Networking — OkHttp + `EventSource` (SSE)
**Why:** streaming is impossible without it. Also brings connection pooling, retries, and timeouts that the current client hand-implements.

### Deliberately **not** recommended

| Rejected | Reason |
|---|---|
| Hilt/Dagger | Manual service locator works; refactor cost buys the user nothing |
| Navigation-Compose | v8.0's typed stack already solves this; swapping is churn |
| MotionLayout | XML-era API; Compose animation APIs supersede it. You listed it — I'd skip it |
| Any DWG parser | No viable OSS option; converting upstream is the honest answer |
| Retrofit | Overkill for one endpoint shape; OkHttp alone is enough |

---

## 7. Animation plan

You asked for micro-interactions everywhere. I want to push back on "everywhere" precisely.

The design system's rule — inherited from the installed UI/UX skill and already enforced in v8.0 — is **150–300 ms, and motion must explain a state change**. Animation that does not explain anything is latency you added on purpose. On a phone held in one hand under time pressure, that is a real cost.

**Adopt:**

| Motion | Where | Duration |
|---|---|---|
| Shared element | file grid → preview, thread list → thread, element → sheet | 280 ms |
| Spring physics | sheet dismissal, canvas fling, pull-to-refresh | spring, not duration |
| Skeleton/shimmer | file grid and thread list *only* while genuinely loading | — |
| Staggered enter | list first paint, 20 ms offset, capped at 6 items | 200 ms |
| FAB transform | FAB → capture sheet | 280 ms |
| Elastic overscroll | canvas and long lists | spring |

**Reject:** per-token text animation, decorative parallax, animated empty states, page-turn effects, anything that fires more than once for the same state.

**Shared element caveat:** `SharedTransitionLayout` is still experimental as of 2026 and is sensitive to unstable keys. It is worth adopting — with stable IDs, and behind a flag so it can be switched off if a Compose update breaks it.

---

## 8–11. Design system, components, navigation

**Design system (§9):** the v8.0 foundation stands — one spacing scale, one type scale, one contrast-verified palette. Additions needed: elevation/blur tokens for the new overlay surfaces, a chart theme mapping Vico onto the CVD-validated series, and a skeleton-shimmer token.

**Component library (§10):** additions — `CwThreadItem`, `CwMessageBubble`, `CwStreamingText`, `CwFileTile`, `CwTagChip`, `CwSelectionBar`, `CwSearchField`, `CwCommandSheet`, `CwTimeline`, `CwHeatmap`, `CwSkeleton`, `CwLayerPanel`, `CwToolPalette`, `CwMiniMap`.

**Navigation (§11):** unchanged five tabs. Adds: the command sheet as a cross-cutting overlay, and thread/folder depth inside Assistant. `NavGraph.entryPoints` continues to make orphaned destinations detectable.

**Accessibility (§13):** contrast is already gated. Outstanding work — content descriptions on the canvas (every element needs a spoken identity), chart values exposed as text (Vico supports this), a documented minimum touch target on the canvas tool palette, and RTL verification of every adopted library. `compose-rich-editor`'s RTL behaviour is the single biggest integration risk in this plan and must be prototyped first.

**Performance (§12):** Coil fixes grid scrolling; androidx.pdf fixes large-document memory; viewport culling fixes canvas; Room FTS fixes search; Paging 3 fixes long lists; per-module ViewModels fix memory retention. Instrument with Macrobenchmark and JankStats — **measure before and after each phase**, no claims without numbers.

---

## 14. Roadmap

Ordered by the journey analysis (§3), not by the order in the brief.

| Phase | Work | Why here |
|---|---|---|
| **0** | Add deps; prototype `compose-rich-editor` RTL + Vico theming | Both are integration risks. Find out in 2 days, not 2 weeks |
| **1** | Coil + androidx.pdf + FTS + OCR → **File Manager** | Fixes J3, the worst journey. Highest ratio of value to risk |
| **2** | OkHttp/SSE + Markdown lib + thread persistence → **AI Chat** | Unblocks streaming, which nothing else can |
| **3** | `LinkEntity` + rich editor + voice + templates → **Notes** | Fixes J4; depends on Phase 1's file layer |
| **4** | Layers, measurement, undo, culling → **Plan Canvas** | Largest new build; benefits from a settled component library |
| **5** | Vico + timeline + heatmap + anomalies → **Manpower** | Mostly presentation once charts are library-backed |
| **6** | Shared elements, springs, skeletons across all | Motion last, over settled layouts — animating a layout you are about to change is wasted work |

**Sizing, honestly.** Phases 1–2 are where most of the perceived improvement lives. Phase 4 is the largest single body of new code in the plan. Phases 5–6 are comparatively cheap. Cost tracking, forecasting, DWG and handwriting recognition are **out of scope** as argued above; each is a subsystem, and I would rather name them now than let them sit in a roadmap implying they are nearly free.

---

## What I need from you before coding

1. **The reference-app tension (§1.1)** — do you agree the site conditions outrank the desk aesthetic where they conflict?
2. **Phase order (§14)** — I've put File Manager first, not AI Chat. Does that match what actually slows your day?
3. **The four exclusions** — DWG, cost tracking, forecasting, handwriting recognition. Confirm they're out, or tell me which one is worth its cost and I'll scope it properly.
4. **RTL prototype** — Phase 0 exists because `compose-rich-editor` in Arabic is unproven. If it fails, Notes keeps a custom editor and the plan changes.
