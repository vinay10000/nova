# Nova Android — UI/UX Overhaul

This document describes the design system, navigation architecture, and the UX fixes applied to the Android app (Compose, Material 3). Everything compiles against the pinned Compose BOM; run `gradlew :app:compileDebugKotlin` to verify.

## Design system (`NovaDesign.kt`)

All screens draw from shared tokens and components instead of hard-coded colors and sizes:

- **Theme modes** — `NovaThemeMode` (System / Light / Dark), persisted via `AccentPreferences`, applied in `MainActivity`. The status bar icon contrast is synced to the active mode. The app is AMOLED-first in dark mode.
- **Accent** — a user-selectable accent color drives `primary`; contrast-checked derivatives (on-primary, container tones) are computed from it rather than hard-coded.
- **Spacing, radius, motion** — `NovaSpace`, `NovaRadius`, `NovaMotion` tokens (durations ~150–350 ms, standard easing).
- **Shape lock** — one stated radius scale, no ad-hoc values anywhere:
  `hair 2` (progress tracks, chart bars, ticks) · `sm 10` (chips, tags, small inputs) ·
  `row 12` (grouped rows, thumbnails) · `md 16` (cards, popups, text areas) ·
  `lg 20` (menus, sheets, composer field) · `bubble 22` (chat bubbles) ·
  `xl 26` (floating tab pill). **Actions are pills** — every button passes
  `CircleShape`, never a radius. Audited mechanically: `RoundedCornerShape(<n>.dp)`
  occurrences in Kotlin = 0.
- **Motion curve** — `NovaMotion.Ease` is the strong ease-out
  `CubicBezierEasing(0.23, 1, 0.32, 1)` used by every entrance, screen transition
  and data-fill (all non-gesture timing stays under 300 ms). `NovaMotion.Pulse`
  (`FastOutSlowInEasing`) is reserved for infinite loading pulses only — it is
  never used for an entrance.
- **Ambient tokens** — `NovaAmbient` owns the background field stops; the bottom
  floor strip under the tab bar reuses the same pair instead of restating hex.
- **Tabular figures** — `NovaTabular` (`fontFeatureSettings = "tnum"`) is merged
  into any proportional-font number that counts or changes, so digits never shift
  their neighbours while streaming.
- **Components** — `NovaPageHeader`, `NovaEyebrow`, `NovaStatusPill`, `NovaCard`, `NovaEmptyState`, `NovaSkeletonCards` (shimmer), `NovaFilterChips` (real `FilterChip`, so TalkBack announces selection state), `NovaIconAction` (48 dp minimum touch target with semantics), `NovaButton` (with loading state), `novaFieldColors()`, `NovaJumpToLatest`, `NovaThinkingIndicator`, `NovaApprovalRow`, `SpecCircleButton`, `SpecAccentButton` (voice → send → stop morph, 48 dp).

## Navigation (`NovaNav.kt`)

- **Bottom navigation bar** with four top-level destinations: Chat, Agents, Activity, Settings. Connections is a child screen (from Settings, drawer, or OAuth return), not a bottom tab. Previously destinations were hidden behind a hamburger drawer, which hurt discoverability and engagement (standard Nielsen Norman finding on hidden navigation).
- Correct `NavHost` flags: `launchSingleTop`, `restoreState`, `saveState`/`popUpTo` so tab switches preserve scroll and state.
- Slide + fade transitions between tabs; the bar hides while the IME is open so the chat composer owns the keyboard inset.
- The drawer remains a chat-context surface: recent conversations, search-free quick list, Connections entry. Duplicate Agents/Activity/Settings rows were removed.

## UX bug fixes

1. **Streaming scroll** — the list follows the stream only while pinned to the bottom (`followStream`); token appends use `scrollToItem` (no per-token `animateScrollToItem` jank). A "jump to latest" pill appears when the user scrolls up during generation.
2. **Truthful timestamps** — messages carry `createdAt` from the send time (optimistic echo and streaming stub share the same clock); history rows show relative times. The old `LocalTime.now()` at composition showed render time and changed on every recomposition.
3. **Touch targets** — all icon actions are ≥ 48 dp (`MinTouchTarget`); back buttons and delete buttons were previously 28–36 dp.
4. **Destructive actions** — deleting a chat requires confirmation (M3 `AlertDialog` with glass `containerColor`) on both the drawer and All Chats; previously instant and irreversible.
5. **Notices vs. steps** — `notice`/`approval` SSE chunks surface as a distinct sticky banner instead of being written into the step label (where they vanished on `done`).
6. **Friendly errors** — raw codes (`stream_failed`, `upload_failed`, HTTP statuses) map to human sentences, including the login screen's `friendlyError` (409/401/400/offline cases).
7. **Contrast** — `onSurfaceVariant.copy(alpha = 0.7f)` on near-black fails WCAG AA for small text; replaced with the unmodified token.
8. **Composer** — IME Send action sends the message (Enter-to-send), sentence capitalization, focus cleared on send, one `SpecAccentButton` that morphs voice → send → stop instead of two flickering buttons.
9. **Executable suggestions** — starter cards run the prompt immediately instead of merely filling the box (two-column grid); hidden while a run is in flight.
10. **Login** — edge-to-edge safe, scrolling form (button no longer hides behind the keyboard), show/hide password, sign-in/create toggle, inline error copy, `NovaButton` with loading state.

## @plugin mention UX (chat composer)

- **Discoverable trigger** — an `@` icon button in the composer inserts `@` and refocuses the field, opening the picker with the keyboard still up. Previously the feature was invisible unless the user already knew to type `@`.
- **Live filtering** — typing after `@` narrows the candidate chips; a bare `@` lists every plugin with its blurb. Unconnected plugins show "Not connected" instead of failing silently later.
- **No-match feedback** — a query that matches nothing explains itself (`No plugin named "…", try @browser, @github, @gmail…`) instead of silently hiding the picker.
- **Visual confirmation** — completed mentions render in mono font with a tinted background both while typing (input transform) and in the sent bubble.

## Feature parity with the backend

`NovaApi.kt` now uses endpoints the backend already exposed:
- `GET /v1/conversations?q=&archived=` — live search in All Chats plus a Current/Archived segmented toggle.
- `PATCH /v1/conversations/{id}` — rename and archive/unarchive (`PatchConversationRequest`).
- Message `createdAt` flows through from the backend so history shows real times.

## Loading & empty states

All list screens (Agents, Activity, All Chats, Connections) show shimmer skeletons during first load and structured `NovaEmptyState` copy when empty — no more bare `LinearProgressIndicator` bars or silent blank areas.

## Generative UI (§45, `GenerativeUi.kt` + `backend/src/ui/UiBlocks.ts`)

Ten validated block types render under assistant replies (streamed as `ui` SSE
chunks, persisted in message `metadata.ui`, restored from history):

| Type | Shape |
|---|---|
| `summary` | prose + mono key/value fact rows |
| `metrics` | big numbers + signed mono deltas (≤3 on one grid) |
| `list` | mono indices, hairline rhythm, right datum |
| `table` | fixed mono header, measured columns, zebra, scrolls under 360dp |
| `progress` | percent bars — track always visible, fill animates width |
| `timeline` | status dots (done/active/todo/error) on a rounded rail |
| `comparison` | two labeled columns on one shared row grid; tonal winner mark |
| `code` | inset mono well, both-axis scroll, language in header meta |
| `chart` | Canvas bar series with measured scale + mono category labels |
| `links` | tappable source rows (http/https only) with domain meta |

Fixes baked in: server-assigned ids (model ids can't collide/drop cards),
nonce per tool-result block, table rows padded/truncated to header width,
blank metric values rejected, copy action attached server-side, content
visible by default (user collapse only), `filter` no longer mis-wired to
expand, link URLs must be http(s).

## A2UI surfaces (§45b, `a2ui/` + `backend/src/ui/A2ui.ts`)

The A2UI path (`androidx.a2ui.compose:compose-runtime|compose-ui` and
`androidx.compose.material3:material3-a2ui`, all `1.0.0-alpha01`, protocol
`v0.9.1`) sits beside §45, not instead of it. §45 is a fixed vocabulary of ten
card shapes — right for a table of numbers. A2UI is an open one: the model
composes whatever the catalog offers and the app renders it as real Compose,
with live data bindings, progressive streaming and actions travelling back.

### How a surface reaches the screen

```
model → present_surface tool  (typed card content, no JSON)
       ↓
A2ui.ts  →  createSurface      (empty surface, catalogId)
           updateComponents    (tree, bindings pointing at nothing yet)
           updateDataModel     (the values those bindings point at)
       ↓
StreamChunk { type: "a2ui", frame }  ×3, over the existing SSE stream
       ↓
NovaA2uiController.submit()  →  A2uiSurface  →  Nova components
```

Three frames, in that order, on purpose. The card frames land before the data
does, so a surface fills in the way a streaming answer does rather than popping
in whole. `Message.surfaces` holds only the surface *ids*; the models are engine
state, so a card that fills in later updates the mounted transcript instead of
re-entering it.

### The catalog

One catalog, one id (`https://nova.app/catalogs/agentic/v1/catalog.json` — an
identifier, never fetched), holding two layers:

- The A2UI **Basic Catalog** (`Text`, `Card`, `Column`, `Button`, `Image`,
  `CheckBox`…), so a model that has never heard of Nova still renders something.
- Nova's own components, all `Nova*`-prefixed so they can never collide with a
  spec name.

Both in one catalog because a component can only reference children in the same
catalog — a Nova card that wants a `Text` child needs the Basic `Text` beside it.

| Component | Shape | Notable |
|---|---|---|
| `NovaTrip` | destination over dates over the flight | flight is mono + tabular; `legStatus` as a status pill |
| `NovaStay` | name, address, check-in/out, photo | Coil for the image; mono times |
| `NovaWeather` | now, high/low, rain, wind, hour strip | strip is drawn at equal weight so 3 hours and 12 read the same |
| `NovaAgenda` | a day's timed entries | time is mono + accent, title is the row's largest type, place is the quietest |
| `NovaChecklist` | tickable list | **two-way**: writes through `bindUpdater` to the surface data model |
| `NovaApproval` | provider, amount, instrument, 4-state flow | `pending → verifying → approved → declined`, each with its own sentence |
| `NovaActions` | suggested next steps as pills | each dispatches an action instead of navigating |

### Readiness and progressive rendering

`isReady` is what lets a component wait for its own data. Each answers on its
single required binding — the trip waits for its destination, the checklist for
its first item — and treats everything else as genuinely optional. That is what
makes a card fill in field by field rather than appearing all at once.

A field that has not arrived renders as a skeleton bar, not an empty string.

### Two-way binding, honestly

`NovaChecklist` is the only surface that writes back. When the agent sends
`done[]` it gets a bound path and the ticks persist; when it does not,
`bindUpdater` returns null and the rows render **read-only** — the label is
dropped from the semantics and the tap does nothing. A control that cannot
persist must not look like one that can.

Tapping anything else (a chip, a confirm button) produces a `SurfaceAction` that
**prefills the composer**. It does not send. The model offered the next step;
the user still decides.

### Supplying the parts the libraries do not

The A2UI libraries ship no image loader and no media stack, on purpose — a
bundled one would collide with whatever the app already uses. So the catalog
takes them as slots:

- **Image** — the Coil instance Nova already had.
- **Video / Audio** — honest placeholders showing the media reference. Nova has
  no Media3 dependency, and a play control that does nothing is worse than none.
  These two objects are the only things that change if Media3 lands.
- **URL opener** — `NovaA2uiContext`, http/https only. A generated surface is
  model output, so a `file://` or `intent://` URL would be the model asking the
  app to open something the user never chose.
- **Message formatter** — `{name}` substitution, and a whole number prints as
  `3`, never `3.0`.

### Boundaries

- The model never emits A2UI JSON. It calls `present_surface` with typed
  content, and `A2ui.ts` builds the frames — so the server owns every id, every
  JSON Pointer, and the `root` of the tree. A hallucinated component name or
  data path is rejected there, not rendered as a hole.
- The action interceptor drops any action from a surface that is no longer live,
  so a torn-down checklist cannot talk to the agent.
- `newChat` resets the controller, so surfaces go with the conversation they
  belong to.
- A turn whose only content is a surface still gets kept (`Message.hasContent`).
- A generative-UI failure is an inline note in the card, not a lost answer: the
  prose around it is still true.

### Build note

`compileSdk = 37` + **`compileSdkMinor = 1`**. The A2UI artifacts are built
against API 37.1 and their AAR metadata rejects a lower minor version.
`targetSdk` stays 36 — a compileSdk minor opts into no new runtime behaviour.

### Tests

- `backend/src/ui/A2ui.test.ts` — 19 tests on frame construction: ordering,
  the `root` requirement, unique ids, every binding being a pointer with a value
  behind it, no binding for an absent field, checklist read-only vs two-way,
  label round-trip, https-only photos, unknown kinds rejected, the payload cap.
- `android/.../test/.../NovaA2uiWireTest.kt` — 15 JVM tests: SSE frame
  deserialization, surface-id extraction from all four envelope keys, the
  `deleteSurface` escaping (quote, backslash, control characters), and the
  action name/label mapping.
- `android/.../androidTest/.../NovaA2uiCatalogTest.kt` — needs a device. Drives
  the real catalog through the real engine: a trip card draws its destination,
  a checklist tick reaches the data model, an unbound checklist does not write,
  an action chip dispatches its named event, the approval card shows its amount
  and state.

## Solid surface system (was: glassmorphism / Haze)

- **Decision** — Haze was removed. On the emulator (and anywhere the blur
  no-ops) translucent glass over live message text renders as sharp text
  bleeding through the composer and tab bar — broken, not premium. The
  reconstruction spec (`assets/ui-reconstruction-spec.md`) calls for solid
  surfaces anyway (composer/drawer/menus = `#202020`), so panels are now
  opaque: dark fill `#202020`, light fill `#F2EEE5`, hairline self-colored
  edge + top-lip gloss kept.
- **Bottom chrome** — `LocalBottomChrome: Dp` CompositionLocal gives screens
  bottom clearance (bar + margin + nav inset). A solid floor strip in
  `NovaNav` covers that band above the NavHost so scroll content never
  bleeds through the floating pill's margins or the navigation inset.
- **Chat list** — LazyColumn `contentPadding` bottom =
  `overlayDp + LocalBottomChrome + NovaSpace.lg`, so the last message rests
  clear above the composer AND the tab bar. The jump-to-latest control is
  parked at `bottomChrome + overlayDp + NovaSpace.md` (above the composer,
  not behind it).
- **Accent coherence** — dark `primaryContainer` follows the chosen accent's
  container tone (`accentMap[accent].darkContainer`) instead of a pinned
  violet, so the user bubble always matches the send button.
- **Attach menu** — M3 `ModalBottomSheet` with surface tiles (Camera/Photos/Files/Plugins); drawer = `ModalDrawerSheet` with `novaGlassFill()`; `DropdownMenu` / `AlertDialog` use `containerColor = novaGlassFill()`.
- **Empty state** — two-column surface starter cards (`NovaCard`, ref 5) replacing `NovaSuggestionChips`; executable-chip behavior kept (`vm.send(prompt)` when `!streaming && !uploading`).
- **User bubble** — `scheme.primaryContainer` + `onPrimaryContainer` (accent-driven).
- **Tab bar** — floating pill, solid fill, icon+label, active = tonal accent (`scheme.primary.copy(alpha=0.12f)`) + SemiBold, `Role.Tab` semantics, no dot indicator.
- **Hard-coded colors** — replaced with scheme/surface tokens; screen `contentPadding` bottom = `28.dp + LocalBottomChrome.current`.
- **ChatGPT→Nova renames** — drawer header "Nova", composer placeholders "Ask Nova"/"Reply to Nova".
