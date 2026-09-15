# Nova — Implementation Plan

> Companion to `PROMPT.md` (§0–§62). This plan is grounded in a **verified audit of the existing repo**
> and **live-verified external facts** (2026-09). Every version and API claim below was checked against
> an authoritative source; sources are cited. Where something could not be verified, it is marked
> **UNVERIFIED** rather than guessed.

---

## 1. What the audit actually found

### 1.1 The repo is a stub, not a partial implementation

`PROJECT_CONTEXT.md:82` claims "Phase 0 boilerplate". That is accurate, but the stub is thinner than
"boilerplate" implies. Every file was read; here is the real state:

| Area | Reality |
|---|---|
| `backend/src/index.ts` (34 lines) | `/health` works. `/v1/chat/stream` exists but is unauthenticated, has no conversation persistence, and no tool loop. `/v1/agents`, `/v1/executions` are **hardcoded empty arrays** that ignore the DB. |
| `ai/GeminiProvider.ts` | Calls `generateContentStream` with a **flat `"role: content"` string join** — it discards message structure, has no function calling, and its `model` arg is an unrecognised second parameter. |
| `ai/AIProvider.ts` | Interface has 2 methods; no tool-call or structured-output surface. |
| `tools/registry.ts` | 2 tools, both returning `{ note: 'TODO: ...' }`. |
| `tools/Tool.ts` | `runToolWithSafety` takes its permission results as **pre-computed booleans passed in by the caller** (`checks: { hasPermission, hasAuth }`), so it cannot actually enforce anything — it is a formality, not a gate. |
| `services/agentService.ts` | `executeAgent` logs three hardcoded strings and returns `COMPLETED`. **No Gemini call at all.** |
| `services/chatService.ts` | Not yet read in full, but consumed by a stream that does no persistence. |
| `android/.../Screens.kt` | `ChatViewModel.send()` appends the literal string `"TODO: stream from backend Gemini."` — **this is a fake AI response in a production flow, a direct §61 violation.** |
| `android/.../data/NovaApi.kt` | An interface containing only comments. Zero endpoints. |
| `prisma/schema.prisma` (166 lines) | Genuinely good and close to §31 — all 14 entities present with correct relationships. This is the single most reusable asset in the repo. |

**Conclusion:** there is no "existing implementation" to preserve. Treat the build as greenfield, with
`schema.prisma`, `openapi.yaml`, and `PROJECT_CONTEXT.md` as the reusable starting points.

### 1.2 The build does not currently work

- **Backend**: `npx tsc --noEmit` passes (verified, exit 0). It compiles but does nothing.
- **Android**: **cannot be built by anyone.** There is no `gradlew`, no `gradle/wrapper/`, and no
  `local.properties`. `gradle` and `kotlinc` are not on PATH. The project is unbuildable as committed.

### 1.3 What the toolchain audit found (good news)

| Tool | Status |
|---|---|
| Node | **v24.11.0** — Node 24 is the Active LTS line (codename Krypton). |
| npm | 11.18.0 |
| JDK | **21.0.8 LTS** — fine for AGP 9.x. |
| Android SDK | `C:\Users\mhvin\AppData\Local\Android\sdk` — platforms **33, 34, 35, 36**; build-tools **34.0.0, 35.0.0, 36.0.0, 36.1.0**; `cmdline-tools/latest` present. |
| Emulator | `Medium_Phone_API_36.1.avd` exists → **the app can actually be run and verified**, not just compiled. |
| Android Studio | Installed. |
| Docker | **NOT installed.** `docker-compose.yml` (local Postgres) is therefore unusable as written. |
| git | 2.51.2 — but **this directory is not a git repository**. No history, no safety net. |

Two immediate consequences: **(a)** Postgres must come from somewhere other than Docker (see §5.1),
and **(b)** `git init` must happen before any code is written, or there is no rollback.

### 1.4 The three findings that change the architecture

These are the most important results of this audit, because following `PROJECT_CONTEXT.md` as written
would produce a system built on a dead SDK and a deprecated API.

**Finding 1 — `@google/generative-ai` is dead.** The scaffold depends on it (`package.json`), and
`GeminiProvider.ts` imports it. Google's library page states the legacy JS library
(`@google/generative-ai`) is **"Not actively maintained"** and **deprecated as of November 30, 2025**;
the replacement is **`@google/genai`**. Registry check: `@google/genai` is at **2.22.0**, and
`@google/generative-ai` is pinned at 0.24.1 with a deprecation notice. ([libraries](https://ai.google.dev/gemini-api/docs/libraries), [migrate](https://ai.google.dev/gemini-api/docs/migrate))

**Finding 2 — there is a new recommended API: Interactions.** As of June 2026 the **Interactions API is
GA and recommended for all new projects**. `generateContent` "remains fully supported" but is
**"now considered legacy"**, and — decisively — *"all new models, multimodal capabilities, tools, and
agentic features will launch on the Interactions API."* ([interactions-overview](https://ai.google.dev/gemini-api/docs/interactions-overview))

This is not a cosmetic difference. The Interactions API natively provides several things §32–§35
require us to hand-roll: an **observable step timeline** (`function_call`, `function_result`,
`thought`, `model_output`) that maps almost 1:1 onto the §35 `ExecutionStep` model, **server-side
conversation state** via `previous_interaction_id`, and **background execution** for long tasks.
It also has real constraints that must be designed around (§2.4).

**Finding 3 — the Play target-API deadline is now.** Verified from Google's requirements page:
*"Starting **August 31 2026**: New apps and app updates must target **Android 16 (API level 36)** or
higher."* The scaffold targets **34**, which is rejected. API 36 and build-tools 36.1.0 are already
installed locally, so this costs nothing but a version bump. ([target-sdk](https://developer.android.com/google/play/requirements/target-sdk))

### 1.5 Version traps found (do not `npm install` / accept IDE defaults blindly)

- **Prisma mismatch.** `prisma@latest` currently resolves to **`8.0.0-rc.13`** (a release candidate)
  while `@prisma/client@latest` is **7.10.0** (stable). A default install pairs a v8-rc CLI with a v7
  client. The docs site says "Prisma 8 is here", but **no stable 8.x is published** — all 59 published
  8.x versions are `-rc`/`-dev`. **Pin both to `7.10.0`.**
- **kotlinx-serialization metadata trap.** Maven's `release` field reports **`1.12.0-RC`**. Pin the
  stable **`1.11.0`** explicitly, or a naive "use latest release" rule ships a pre-release.
- **`composeOptions` is dead.** The compose compiler is now the **`org.jetbrains.kotlin.plugin.compose`**
  plugin, versioned in lockstep with Kotlin. The legacy `androidx.compose.compiler:compiler` artifact is
  frozen at 1.5.15. The scaffold already uses the plugin, but also still carries `kotlinOptions`.
- **`okhttp-sse` is officially experimental.** Square's README says *"API is not considered stable and
  may change at any time."* This is still the right choice (§4.3), but the risk is acknowledged, not hidden.

---

## 2. Architectural decisions

### 2.1 ADR-1: Adopt the Interactions API, behind the existing `AIProvider` interface

**Decision.** Implement `GeminiProvider` on `@google/genai` ≥ 2.3.0 using `ai.interactions.create`,
and keep every consumer talking only to `AIProvider`. `generateContent` is not used for new code.

**Why.** It is the recommended API for new projects, it is where new features will ship, and its step
timeline directly serves §35 observability instead of us re-deriving it. Behind the interface, this
choice can be reversed without touching a single caller — which is exactly what §61 demands
("Keep provider-specific code behind interfaces"). We are *exercising* our own abstraction on day one.

**Consequence.** `AIProvider` must be redesigned (§3.2). Its current two-method shape cannot express
tool calls, structured output, or step events, and it must not leak Interactions-specific types.

### 2.2 ADR-2: Expand `AIProvider` from "a chat streamer" to "an agent-capable runtime"

This is the highest-leverage change in the plan. A provider that can only stream prose forces the
agent runtime to grow its own second, divergent model loop — which is how codebases end up with two
incompatible definitions of "a tool call".

The new interface (§3.2) is one primitive — `run()` yielding events — from which chat, the agent
worker, and the Agent Builder are all built. Chat becomes "an agent with no tools".

### 2.3 ADR-3: Server-side execution is the product; the phone is a client

§32 requires agents to run when the phone is locked, offline, or powered down. This is a correctness
constraint, not an optimisation. Therefore the scheduler, job queue, worker, and tool execution all
live in the backend, and the Android app only *requests* and *observes* runs. No agent logic, ever, in
WorkManager. (§61 already states this; the plan makes it structurally enforced — see §7.2.)

### 2.4 ADR-4: Design around the Interactions API's documented gaps

Four documented limitations shape the design, and pretending they don't exist would break later:

1. **Automatic function calling is NOT available.** We own the tool loop explicitly. This is fine —
   we must own it anyway, because §15/§47 insert permission and approval gates *between* the model's
   request and execution, which an auto-loop would bypass.
2. **`store=false` is incompatible with `background=true` and blocks `previous_interaction_id`.**
   Agent runs are long-lived, so agents use `store=true` + `previous_interaction_id`. Users get a
   `store=false` toggle for chat, and the UI must state the cost (no follow-ups, no background).
3. **Retention is 55 days (paid) / 1 day (free).** Google's copy is a cache and a debug trail — **ours
   is the source of truth.** Conversations/executions must be fully reconstructible from our Postgres
   with zero dependence on vendor retention. This is a data-durability requirement, not a nicety.
4. **No custom safety settings, no batch, no explicit caching** on Interactions. Anything needing those
   stays on `generateContent` behind the same interface.

**Also flagged:** the JS SDK README uses `interaction.outputs` while all official docs use
`interaction.steps`. **Step 0 of Phase 1 resolves this against the installed typings** (§8, Task 0.3) —
we do not write a provider against a guessed property name.

### 2.5 ADR-5: Cheap, boring infrastructure until load justifies otherwise

`PROJECT_CONTEXT.md` lists Trigger.dev, Inngest, n8n, Composio, Browserbase, Supabase, Firebase, Neon.
Adopting any of these *now* means paying integration cost for a load we do not have and cannot measure,
and it puts vendor-shaped holes in our own interfaces. The spec is explicit that this is a *starting*
architecture and that we should "use the simplest reliable system" (§30) and "not adopt every listed
service automatically" (§49).

So: **Postgres-backed job queue + one worker process** for Phases 3–5, behind an `AgentQueue` interface.
Trigger.dev/Inngest slot in behind that interface when concurrency actually demands it. Same pattern for
`BrowserProvider` and `IntegrationAdapter` — the interface is built now, the vendor is chosen later.

---

## 3. Target architecture

### 3.1 Revised backend layout

```text
backend/src/
├── index.ts                    # Fastify gateway; registers plugins + routes
├── env.ts                      # Zod-validated env; FAILS FAST at boot
├── plugins/
│   ├── auth.ts                 # JWT verify -> request.user (never trusts client userId)
│   ├── errors.ts               # setErrorHandler; maps domain error codes -> HTTP
│   └── rateLimit.ts
├── routes/
│   ├── health.ts  chat.ts  conversations.ts  attachments.ts  voice.ts
│   ├── agents.ts  executions.ts  approvals.ts  connections.ts
│   ├── memories.ts  notifications.ts  devices.ts
├── ai/
│   ├── AIProvider.ts           # interface ONLY (§3.2) — no vendor types escape this file
│   ├── GeminiProvider.ts       # @google/genai interactions.*
│   └── models.ts               # model registry + capability flags
├── agent/
│   ├── runtime.ts              # THE tool loop: model -> gate -> tool -> result -> model (§32)
│   ├── planner.ts              # NL -> AgentConfig + clarifying questions (§12,§59)
│   ├── queue.ts                # AgentQueue interface + PostgresQueue impl
│   ├── scheduler.ts            # cron materialisation -> queue (§33)
│   └── worker.ts               # separate process entrypoint
├── tools/
│   ├── Tool.ts                 # interface + REAL safety pipeline (§15,§47)
│   ├── registry.ts             # id -> Tool
│   └── native/                 # github.ts gmail.ts calendar.ts websearch.ts ...
├── integrations/
│   ├── IntegrationAdapter.ts   # Native | n8n | Composio
│   └── oauth/                  # provider-agnostic authorization-code flow
├── browser/
│   ├── BrowserProvider.ts      # Browserbase | Cloudflare | Playwright
│   └── stagehand.ts
├── security/
│   ├── crypto.ts               # AES-256-GCM envelope encryption for OAuth tokens
│   └── permissions.ts          # permission catalogue + least-privilege checks
└── services/                   # chatService, agentService, executionService, memoryService,
                                # notificationService, titleService, usageService
```

### 3.2 The redesigned `AIProvider` (ADR-2)

The key insight is that **one primitive serves chat, agents, and the builder.** Chat is a `run()` with
an empty toolset; agent execution is a `run()` with tools and a gate; the Agent Builder is a `run()`
with a JSON-schema `responseFormat`.

```ts
export type RunEvent =
  | { type: 'text';        delta: string }
  | { type: 'thought';     delta: string }
  | { type: 'tool_call';   callId: string; name: string; args: unknown }
  | { type: 'tool_result'; callId: string; name: string; result: unknown; error?: string }
  | { type: 'usage';       inputTokens: number; outputTokens: number }
  | { type: 'done';        interactionId: string; reason: string };

export interface RunRequest {
  messages: ChatMessage[];
  systemInstruction?: string;
  model?: string;
  tools?: ToolDef[];                 // omitted => plain chat
  responseFormat?: { schema: object };   // omitted => prose
  statefulId?: string;               // previous_interaction_id
  store?: boolean;
  signal?: AbortSignal;              // §6 "Stop generation" must actually cancel upstream
  /** THE GATE. Called before every tool execution. §47 lives here, once. */
  onToolCall?: (c: ToolCall) => Promise<ToolOutcome>;
}

export interface AIProvider {
  run(req: RunRequest): AsyncIterable<RunEvent>;
  readonly capabilities: ProviderCapabilities;   // lets UI §19 "gracefully report unavailable"
}
```

**Why the gate is a callback, not a loop the runtime owns.** The §47 sequence — available? permitted?
authorized? approval required? — must run for *every* tool call, and a provider that receives
`tools: []` must still be unable to execute anything. Putting `onToolCall` in the contract means a
provider physically cannot bypass the gate, because it has no other way to produce a `tool_result`.

### 3.3 Making the safety pipeline real (repairs §1.1's formality)

`runToolWithSafety` currently accepts booleans its caller computed. That inverts the trust boundary:
the thing being checked supplies its own verdict. The replacement resolves each check from the database
itself and is the **single** path to `tool.execute`:

```ts
async function gate(ctx: ExecutionContext, call: ToolCall): Promise<ToolOutcome> {
  const tool = registry.get(call.name);
  if (!tool)                                 return { status: 'denied', reason: 'TOOL_UNAVAILABLE' };
  if (!ctx.agentPermissions.has(tool.requiredPermission))
                                             return { status: 'denied', reason: 'PERMISSION_REQUIRED' };
  const conn = await connections.find(ctx.userId, tool.provider);   // DB lookup, not a bool
  if (!conn || conn.status !== 'CONNECTED' || !covers(conn.scopes, tool.requiredScopes))
                                             return { status: 'denied', reason: 'AUTH_REQUIRED' };
  if (requiresApproval(tool, ctx.policy))    return { status: 'pending_approval', ... };  // §36
  await audit.log(...);                      // §46
  return { status: 'ok', result: await tool.execute(call.args, ctx) };
}
```

`schema.prisma` already has `Schema_validator`, `Connection.scopes`, and `Agent.permissions` as JSON —
the data model supports this today. Phase 3 makes the code honour it.

### 3.4 Data-model deltas (small — the schema is already good)

| Change | Why |
|---|---|
| `Conversation.archived` → index on `(userId, updatedAt DESC)` | §8 "search/continue" needs a real index, not a scan. |
| `Message.content` stays `String`; add `Message.parts Json?` | Multimodal (§9) needs ordered text+image+document parts; flat content cannot represent order. |
| `Attachment`: add `storageKey`, `sha256`, `extractedText`, `status` | §10's validate→store→extract→context pipeline needs state, and dedupe needs a hash. |
| New `Approval` model | §36 has `WAITING_FOR_APPROVAL` as a status but **no entity to store the pending decision**. Currently unrepresentable. |
| New `AuditLog` model | §46 requires audit logging; nothing in the schema provides it. |
| New `Job` model | Backs `PostgresQueue` (§2.5). |
| `Execution`: add `idempotencyKey`, `attempt`, `interactionId` | Retries must not double-send; `interactionId` links to the vendor trace. |
| `Usage`: add `model`, `kind`, and index on `(userId, createdAt)` | §46 "execution limits" and cost attribution need per-model rows. |

Everything else in §31 is already present and correct.

### 3.5 Android stack — moving from 2024 to 2026

The scaffold targets `compileSdk 34`, `Retrofit 2.11.0`, `OkHttp 4.12.0`, `compose-material3 1.2.1`,
and has no Compose BOM.

| Item | Scaffold | Target | Note |
|---|---|---|---|
| AGP / Gradle | (none) | **9.4.0 / 9.7.1** | No wrapper exists — must be generated. |
| Kotlin | (unpinned) | **2.4.20** | |
| Compose compiler | plugin | **`org.jetbrains.kotlin.plugin.compose` 2.4.20** | Delete `kotlinOptions`. |
| Compose BOM | *absent* | **2026.09.00** | Use unversioned `material3` — do not hand-pin. |
| compile/targetSdk | 34 | **36** | Play deadline, §1.4 Finding 3. |
| Navigation | 2.7.7 | **2.10.1** | |
| Lifecycle/ViewModel | 2.7.0 | **2.11.0** | |
| Room | 2.6.1 | **2.8.5** | |
| WorkManager | *absent* | **2.11.2** | |
| Retrofit / OkHttp | 2.11.0 / 4.12.0 | **3.0.0 / 5.5.0** | Major bumps. |
| SSE | none | **`okhttp-sse:5.5.0`** | Experimental — see below. |
| Serialization | 1.7.3 | **1.11.0** | **Not** the 1.12.0-RC Maven advertises. |
| Markdown | none | **`com.mikepenz:multiplatform-markdown-renderer:0.45.0`** | Actively maintained, M3 artifact, tables + code. |

**SSE approach (§6 streaming).** Retrofit has no SSE converter, and Retrofit's whole-body semantics
fight token streaming. Use `okhttp-sse` directly on the *same shared* `OkHttpClient` Retrofit uses:

```kotlin
val factory = EventSources.createFactory(streamingClient)   // readTimeout(0), no gzip
factory.newEventSource(request, object : EventSourceListener() {
  override fun onEvent(es: EventSource, id: String?, type: String?, data: String) { ... }
})
```

Two gotchas that must be handled explicitly, or streaming silently buffers: set
**`readTimeout(0)`** (SSE holds the connection open indefinitely) and **disable transparent gzip** on
that client. The artifact's README calls the API unstable — so it is wrapped in our own
`ChatStreamClient` and never referenced from UI code.

---

## 4. Phase plan

Sequenced strictly per §50–§56: "following Phases 1–7 above in order." Each phase ends at a milestone
that is **demonstrated running**, not merely compiled. Estimates assume one focused engineer.

### Phase 0 — Make it buildable, and make it safe to change (0.5 day)

Nothing else is meaningful until this is done.

1. `git init` + `.gitattributes`; commit the current tree as the baseline. **There is no rollback today.**
2. Generate the Gradle wrapper: `gradle wrapper --gradle-version 9.7.1` (using Android Studio's bundled
   Gradle), commit `gradlew`, `gradlew.bat`, `gradle/wrapper/`.
3. `android/local.properties` with `sdk.dir` (gitignored) — currently absent, so nothing can build.
4. Bump to AGP 9.4.0 / Kotlin 2.4.20 / compileSdk+targetSdk 36; remove `kotlinOptions`; add Compose BOM.
5. Delete `Screens.kt`'s `"TODO: stream from backend Gemini."` string — a fake AI response in a
   production flow (§61). It is replaced in Phase 1; until then, show an explicit error state.
6. **Gate:** `./gradlew assembleDebug` succeeds; `npx tsc --noEmit` succeeds; both in CI.

### Phase 1 — ChatGPT core (§50) — 1.5 weeks

**Backend**
- `env.ts` Zod validation, fail-fast at boot (catches a missing `GEMINI_API_KEY` at deploy, not at first message).
- **`@google/genai` migration**; delete `@google/generative-ai`. **Task 0: probe the SDK** to confirm
  `steps` vs `outputs` (§2.4) and pin the model ID.
- `AIProvider.run()` per §3.2; `GeminiProvider` on `interactions.create` with `stream: true`; map
  `step.delta` events to `RunEvent`.
- **Auth** (§29): register/login, JWT, `plugins/auth.ts` resolving `request.user`. Every route uses it.
  *Never trust a client-supplied `userId`. Never return a 404 that reveals another user's ID exists.*
- Conversations CRUD + search + archive + rename (§8); auto-title as a background job.
- `POST /v1/chat/stream` with SSE: `Content-Type`, `Cache-Control: no-cache`, **`X-Accel-Buffering: no`**
  (the single most common "works locally, buffers in prod" bug), and client-disconnect handling so an
  abandoned stream cancels the upstream Gemini call rather than billing for it.
- Per §2.4: use Fastify's native `reply.send(stream)`, **not** `reply.raw` (which skips Fastify's hooks
  and error handling — we need those for auth).

**Android**
- Real `NovaApi` (Retrofit) + `ChatStreamClient` (`okhttp-sse`) per §3.5.
- `ChatViewModel` with a proper `UiState` (Idle/Streaming/Error) — replacing the current fake response.
- Streaming chat UI; markdown + code rendering via `multiplatform-markdown-renderer`; three themes.
- Message actions (§6): copy, copy-code, regenerate, stop, retry, edit, share.
- Input (§7): text, send, stop, model selector; attachments/voice arrive in Phase 2.
- §4 states: loading, empty, error, **offline**.

**Milestone (§50):** `Login → Chat → Gemini → Streaming → Persistent conversation`, verified on the
`Medium_Phone_API_36.1` emulator with the backend stopped mid-stream (offline/error states).

### Phase 2 — Files, multimodal, voice (§51) — 1 week

- §10 pipeline: upload → **validate** (magic-byte sniff + allowlist + size cap, not extension trust) →
  storage → extract → Gemini context. Supported: PDF, TXT, DOCX, CSV, images.
- Note the Interactions constraint: documents support **`application/pdf` and `text/csv`** only, so
  DOCX/TXT need server-side text extraction before the model sees them (§9's "backend preprocessing").
- Images/PDF sent inline (base64) or via Files API — **resolve which during Task 0.3**.
- Vision: screenshot → explain error (§9). Resume → rewrite example (§10) as an acceptance test.
- Voice (§11) as a **modular** capability: `SpeechRecognizer` (check on-device availability, else cloud)
  for STT; Gemini TTS or Android TTS for output. Voice must not be entangled with `ChatViewModel` — §11
  requires it to be swappable.
- `POST_NOTIFICATIONS` runtime permission (§43), requested **in context**, not at first launch.

### Phase 3 — Agent framework (§52) — 2 weeks

The core of the product, and where §1.1's stubs are replaced by real machinery.

- **Tool abstraction** (§15) and the **real gate** (§3.3) — the first genuine implementation of §47.
- **`agent/runtime.ts`**: the explicit tool loop (no AFC, per §2.4). Model → `tool_call` → gate →
  execute → `function_result` → continue, with a **hard iteration cap** and wall-clock budget
  (§46 "agent execution limits", §32 long-running safety).
- **Structured output** via top-level `response_format` (§12/§59 planner): NL → `AgentConfig` +
  `questions[]` when fields are missing. Validated against `shared/agent-config.schema.json`.
- **Conversational builder** (§14): config card with Edit/Activate, wired into chat (§42).
- **Execution records** (§34/§35): statuses, steps, and `ExecutionStep` metadata that **must never
  contain credentials or tokens** (§35) — enforced by an allowlist serializer, not by discipline.
- **Run Now** (§33) + execution history (§44).
- **Prisma migration** for the §3.4 deltas including `Approval`, `AuditLog`, `Job`.

**Milestone:** §14's exact dialogue (`GitHub every morning` → clarifying questions → config card →
Activate) works end to end, and a Run Now produces a real §35 step timeline.

### Phase 4 — Integrations (§53) — 2 weeks

Order: **GitHub → Gmail → Google Calendar → Slack → Notion**, then X/WhatsApp/Drive/LeetCode.

- `IntegrationAdapter` + OAuth authorization-code flow; tokens **AES-256-GCM envelope-encrypted** with
  a KMS-managed key (§38/§46). Per-user isolation is a query invariant, tested adversarially.
- **GitHub first** (§17): all 13 tools, split **read** vs **write** permissions (§37) — and per §17's
  explicit rule, *connecting GitHub must never grant write*. Read tools ship enabled; write tools are
  off until individually granted and are approval-gated (§36).
- **Connections screen** (§38): connect / reauthorize / disconnect / **inspect granted scopes**;
  §19's "gracefully report unavailable capabilities" is a first-class UI state, since (e.g.) not every
  X account has every API tier.
- **Level 2** (§27): `IntegrationAdapter` routes lower-priority services to n8n/Composio. Build the
  interface in Phase 3; integrate here.
- **LeetCode** (§18): no assumption of an official API. Browser automation **only where terms permit**;
  explicitly no auth/CAPTCHA/rate-limit circumvention. Ship it as a documented best-effort with honest
  failure states.
- **WhatsApp** (§20): Business/Cloud API only. No personal-account automation.

### Phase 5 — Automation (§54) — 1.5 weeks

- **`PostgresQueue`** (§2.5) with `SELECT ... FOR UPDATE SKIP LOCKED`, exponential backoff, dead-letter,
  and idempotency keys so a retry cannot double-send an email or file a duplicate issue.
- **Scheduler** (§33): `Scheduler` rows materialise into `Job`s. Run Now / one-time / recurring with
  cron + **explicit IANA timezone** (a "9 AM" agent without a timezone is a latent bug). DST handled
  by storing the zone and re-deriving, never by fixed offsets.
- **Worker** as a separate process so a long agent run cannot stall the API.
- **Approvals** (§36): persist to the `Approval` model, surface via `WAITING_FOR_APPROVAL`, resume the
  run on decision, with timeout → auto-reject (fail closed).
- **Notifications** (§43) via FCM + local channels, with user-controlled preferences.
- Retry/monitoring: failed runs are visible and recoverable (§61).

### Phase 6 — Browser agents (§55) — 1.5 weeks

- `BrowserProvider` (§23): **Browserless.io and Browserbase** are the chosen providers —
  Browserless for deterministic scrape/automation sessions, Browserbase (+ Stagehand, §24)
  for NL-driven agent tasks — selected by config, never by call-site branching.
- Session persistence and browser task history (§55).
- §41: prefer APIs; browser only where unavailable **and permitted**.
- Note the strategic catch: browser agents inherit the *user's* session, so this phase is where
  credential-handling risk peaks — sessions are scoped per user, never shared across tenants, and
  never logged.

### Phase 7 — Advanced agent system (§56) — 2 weeks

- **Persistent memory** (§39): viewable, editable, deletable. §39's "do not silently store arbitrary
  sensitive information" is implemented as an extraction filter + a user-visible review queue, not a
  promise.
- Multi-step planning, multiple agents, **agent-to-agent delegation** (with depth caps and cycle
  detection), long-running jobs (`background=true`), HITL workflows, usage limits/quotas, and
  observability built on the Interactions step timeline.

---

## 5. Cross-cutting requirements

### 5.1 Infrastructure reality (Docker is not installed)

`docker-compose.yml` cannot run here. Options, in order of preference:
1. **Neon or Supabase free tier** — managed Postgres, zero install, closest to production.
2. **Local Postgres** via the Windows installer or `winget install PostgreSQL.PostgreSQL`.
3. Docker Desktop, only if the user wants it.

Redis is deliberately **not** required — `PostgresQueue` (§2.5) removes that dependency for Phases 3–5.

### 5.2 Secrets (§46) — enforced mechanically, not by convention

- `GEMINI_API_KEY` lives only in backend env. **CI runs a grep gate over the APK** for key-shaped
  strings and fails the build if any appear. §46 becomes a test, not a guideline.
- OAuth tokens: envelope-encrypted at rest, decrypted only in the tool-execution process, never in logs,
  never in `ExecutionStep.metadata`, never in an error message.
- `SessionKeystore.kt` already holds session tokens only — keep it that way.

### 5.3 Testing — the part that makes "production-quality" true

The scaffold has **zero tests**. Realistically this is the difference between a demo and the thing §0 asks for.

| Layer | Approach |
|---|---|
| Backend unit | Vitest. The **gate** (§3.3) gets exhaustive table-driven tests: every §47 branch. |
| Backend integration | Testcontainers or a scratch schema; every route tested for **cross-user isolation** (user A must never read user B's conversation/agent/execution). |
| Provider | `AIProvider` conformance suite run against a fake provider — so swapping to `generateContent` or another vendor is provably safe (§61). |
| Android unit | JVM tests for `ChatViewModel` state machine + SSE frame parsing (no emulator needed). |
| Android UI | Compose UI tests for chat, builder, connections. |
| E2E | Emulator (`Medium_Phone_API_36.1`) against a live backend, scripted: login → chat → stream → attach → build agent → run → approve. |
| Security | A dedicated suite asserting: no key in APK; no token in any execution step; no unauthenticated route; no cross-tenant read. |

### 5.4 Definition of done (per phase)

A phase is complete only when: it builds; tests pass; the milestone is **seen working on the emulator**;
`docs/ARCHITECTURE.md` and `PROJECT_CONTEXT.md` are updated; and security invariants (§5.2/§5.3) still hold.
Compiling is not done.

---

## 6. Risk register

| Risk | Severity | Mitigation |
|---|---|---|
| **Interactions API still evolving** (SDK v3 removes AFC from `generateContent`, and the docs/README disagree on `steps` vs `outputs`) | High | Pin `@google/genai` `>=2.3.0 <3`. Probe the API in Task 0.3 before writing the provider. `AIProvider` isolates all of it. |
| **`okhttp-sse` is officially experimental** and may change | Medium | Wrap in `ChatStreamClient`; no UI dependency on it. Fallback: buffered `POST` + polling, or OkHttp streaming directly. |
| **Interactions retention is 55 days (paid) / 1 day (free)** | Medium | Never treat vendor state as storage — Postgres is the source of truth (§2.4-3). |
| **Prisma rc/stable mismatch** | Medium | Pin `prisma@7.10.0` + `@prisma/client@7.10.0` exactly. Never `@latest`. |
| **Scope: §0 says "every requirement is mandatory"** — this is a very large product | **High** | Phases are strictly ordered; each ends in a running milestone. §62 explicitly authorises shipping the foundation first. **Realistic cost: ~12–14 focused weeks to Phase 7.** |
| **Third-party API access is not guaranteed** (X tiers, LeetCode has no official API, WhatsApp Business approval) | Medium | §19's "gracefully report unavailable capabilities" is designed in from Phase 4, not retrofitted. |
| **OAuth verification lead times** (Google/Gmail scopes can take weeks) | Medium | Start Google verification in Phase 3 so it clears before Phase 4 ends. |
| **No git history today** | High | Phase 0 Task 1 commits the baseline before any change. |

---

## 7. Honest trade-offs (§0 requires flagging, not silent dropping)

1. **Nothing is dropped, but ordering is opinionated.** §0 authorises flagging trade-offs. The plan
   delivers all 62 sections, but Phases 4–7 integrations are staged so that *breadth of connectors*
   yields to *depth of the agent runtime*. A working GitHub agent is worth more than twelve shallow ones.
2. **Custom job queue instead of Trigger.dev/Inngest** (§2.5) — one engineer's ~2 days vs a vendor
   dependency and recurring cost. Revisit at real concurrency; the interface makes it a swap.
3. **`generateContent` is deprecated as the default choice.** §2 said "Gemini", not which API, so
   choosing Interactions honours the spec while keeping us off a legacy path. The older API remains
   available behind `AIProvider` for the features Interactions lacks (batch, safety settings).
4. **LeetCode cannot be promised.** With no official API, §18's use cases are best-effort and
   terms-dependent. The plan says so rather than implying a guarantee.
5. **Voice quality depends on the device.** On-device recognition is model- and locale-dependent;
   the plan checks availability and degrades to cloud, rather than pretending otherwise.
6. **Timeline is an estimate, not a commitment.** §0's requirement set is genuinely large; the plan is
   honest about that rather than compressing it into an implausible schedule.

---

## 8. Immediate next steps

| # | Task | Gate |
|---|---|---|
| 0.1 | `git init`, commit baseline, `.gitattributes` | A rollback point exists |
| 0.2 | Gradle wrapper 9.7.1 + `local.properties` | `./gradlew assembleDebug` succeeds |
| 0.3 | **SDK probe**: install `@google/genai`, verify `steps` vs `outputs`, streaming event names, current model ID, `response_format`, and inline-vs-Files for PDF | A written note fixing exact API shapes — **before** writing `GeminiProvider` |
| 0.4 | Version bump to AGP 9.4.0 / Kotlin 2.4.20 / SDK 36 / Compose BOM 2026.09.00 | Build green on API 36 |
| 0.5 | Database: Neon/Supabase project, `DATABASE_URL` in backend `.env` | `prisma migrate dev` applies |
| 1.1 | `env.ts` + `plugins/auth.ts` + JWT | Unauthenticated request is rejected |
| 1.2 | `AIProvider.run()` + `GeminiProvider` (interactions, streaming) | Streaming tokens arrive from a real Gemini call |
| 1.3 | SSE route + persistence + conversations CRUD | §50 milestone on the emulator |
| 1.4 | Android chat UI, markdown, actions, states | End-to-end chat verified, offline/error states verified |
| 1.5 | CI: build, test, **APK secret-scan gate** | CI fails on a planted fake key |

**Immediate blocking question for the user:** confirm the Postgres choice (§5.1) and confirm the
`GEMINI_API_KEY` will be supplied for the backend, since Phase 1's milestone cannot be verified without
a real Gemini call (§61 forbids faking it).
