# Nova — Implementation Plan

> **Source of truth:** `PROMPT.md` §0–§62. This plan turns every mandatory requirement into concrete,
> ordered work packages with acceptance criteria. Nothing in the spec is dropped; where a requirement
> is genuinely blocked (legal/API access), it is **flagged explicitly** in §9 rather than silently omitted.
>
> **Status:** Phase 0 (boilerplate) exists in the repo. Phase 1 is being implemented.
> **Verified:** all external API/library facts below were checked live against vendor docs and package
> registries on the date of writing, not recalled from training data. See §10 for the verification log.

---

## 0. Executive summary

Nova is **two products in one codebase**:

1. A **ChatGPT-style Android chat app** — simple, fast, obvious.
2. An **autonomous agent platform** — natural-language agents that call tools on a server-side schedule.

The single most important architectural rule that makes both work: **the Android client is a thin,
replaceable view over a REST+SSE contract.** Everything intelligent — Gemini, tools, permissions,
schedules, memory, approvals — lives server-side. This satisfies §32 (agents run when the phone is
off), §46 (no secrets in the APK), and §62 (add capabilities without redesigning the app).

### The five decisions that shape everything else

| # | Decision | Rationale |
|---|----------|-----------|
| D1 | **Backend owns Gemini via `@google/genai` + REST `interactions` API** | §2, §46. The Android app talks only to our API. |
| D2 | **Tool execution is a single gated pipeline** (`runToolWithSafety`) | §15, §47. One choke point = one auditable place for permission/approval logic. Fail-closed. |
| D3 | **Agents are data, not code** — an Agent row + schedule + tool grants | §13, §59. Enables the visual editor, the NL builder, and versioning with the same model. |
| D4 | **Everything external sits behind an interface** (`AIProvider`, `Tool`, `BrowserProvider`, `IntegrationAdapter`) | §61. Swappable providers, testable, no vendor lock-in. |
| D5 | **Approval gates are per-tool configuration, checked *before* execution** | §36, §47. Writes/sends block by default; reads do not. |

---

## 1. Verified technical baseline

### 1.1 Gemini API — **the repo's current assumptions are outdated**

The existing `GeminiProvider.ts` uses `@google/generative-ai` `0.21` with `gemini-2.5-flash` and
`generateContentStream`. **Both the SDK and the model names have been superseded.**

| Item | Repo currently assumes | **Verified current** |
|---|---|---|
| SDK (Node) | `@google/generative-ai` 0.21 | **`@google/genai` 2.22.0** |
| Primary model | `gemini-2.5-flash` | **`gemini-3.8-flash`** |
| Secondary | — | `gemini-3.7-flash`, `gemini-3.6-flash`, `gemini-3.5-flash`, `gemini-3.5-flash-lite`, `gemini-3.1-flash-lite` |
| Long-horizon / planning | — | `gemini-3.1-pro-preview`, `gemini-3-pro-preview` |
| Image gen (Nano Banana) | — | `gemini-3.1-flash-image`, `gemini-3-pro-image` |
| Live (voice, §11) | — | `gemini-3.1-flash-live-preview` |
| TTS (§11 output) | — | `gemini-3.1-flash-tts-preview` |
| Transcribe (STT) | — | `gemini-3.5-transcribe`, `gemini-3.5-transcribe-live` |

**The API shape changed materially.** Chat and function calling now go through an **Interactions API**,
not `generateContent`:

```ts
import { GoogleGenAI } from '@google/genai';
const client = new GoogleGenAI({});          // reads GEMINI_API_KEY

// One call handles text, tools, and multimodal input.
const interaction = await client.interactions.create({
  model: 'gemini-3.8-flash',
  input: 'Schedule a meeting with Bob and Alice for 03/14/2025 at 10:00 AM about Q3 planning.',
  tools: [{
    type: 'function',
    name: 'schedule_meeting',
    description: 'Schedules a meeting with specified attendees at a given time and date.',
    parameters: {
      type: 'object',
      properties: {
        attendees: { type: 'array', items: { type: 'string' } },
        date:      { type: 'string', description: 'Date (e.g., "2024-07-29")' },
        time:      { type: 'string', description: 'Time (e.g., "15:00")' },
        topic:     { type: 'string', description: 'The meeting topic.' },
      },
      required: ['attendees', 'date', 'time', 'topic'],
    },
  }],
});

for (const step of interaction.steps) {
  if (step.type === 'function_call') {
    console.log(step.name, step.arguments);   // call_id lives on the step
  }
}
```

Feeding a tool result back is **turn 2 of the same interaction**, not a new stateless call:

```jsonc
POST https://generativelanguage.googleapis.com/v1beta/interactions
{
  "model": "gemini-3.8-flash",
  "previous_interaction_id": "INTERACTION_ID",   // links the turns
  "tools": [ /* same tool defs, resent each turn */ ],
  "input": [
    {
      "type": "function_result",
      "name": "get_weather",
      "call_id": "call_123",                      // MUST match the function_call step
      "result": [{ "type": "text", "text": "{\"response\": \"Very cold. 22 F.\"}" }]
    }
  ]
}
```

Other verified capabilities that the plan exploits:

- **Streaming** uses `stream: true` over **server-sent events** (`event_type` on each event). This maps
  1:1 onto our own SSE endpoint — no translation layer needed.
- **Multimodal function *responses*** (Gemini 3 series): `result` accepts content blocks of `type`
  `text` / `image`, so a tool can return a screenshot directly to the model. This is what makes the
  browser-agent loop in §41 practical.
- **Server-side built-in tools** exist: `{"type": "google_search"}`, `google_maps`, `code_execution`,
  `url_context`, `file_search`, **`computer_use`**. §40's web search should use `google_search` rather
  than a hand-rolled scraper.
- Tools are **combinable** with function calling in one request.

### 1.2 Android stack (verified from Google Maven)

| Library | Verified latest stable | Note |
|---|---|---|
| Kotlin | **2.4.20** | `kotlin-gradle-plugin` |
| KSP | 2.3.12 | must match Kotlin minor |
| AGP | 9.4.0 stable (9.5.0-alpha05 preview) | repo has no pinned AGP; Kotlin 2.x + AGP 9 needs the Compose compiler plugin |
| Compose BOM | **2026.09.00** | use BOM, never per-artifact versions |
| Material 3 | via BOM (1.5.0-alpha28 standalone) | |
| navigation-compose | 2.10.1 | |
| room-runtime | 2.8.5 | |
| lifecycle-viewmodel-compose | 2.12.0-alpha03 → pin stable 2.9.x | |
| core-ktx | 1.19.0 | |
| activity-compose | 1.14.0-alpha02 → pin stable 1.11.x | |
| kotlinx-coroutines | 1.11.0 | |
| kotlinx-serialization-json | 1.12.0-RC → pin 1.9.x stable | |
| OkHttp | **5.5.0** | repo pins 4.12.0 |
| Retrofit | **3.0.0** | repo pins 2.11.0 |
| Coil (images) | 3.6.2 | |
| android security-crypto | 1.1.0 | |

> **Action:** the repo's `android/app/build.gradle.kts` pins `compileSdk = 34` and 2024-era library
> versions. Bump to `compileSdk`/`targetSdk = 36`, Java 17, and the BOM above.

### 1.3 Backend/infra (verified from npm)

`fastify` 5.12.4 · `@prisma/client` 7.10.0 (`prisma` 8.0.0-rc → pin 7.x) · `zod` 4.6.2 ·
`typescript` 7.0.2 · `tsx` 4.23.13 · `@trigger.dev/sdk` 4.5.16 · `inngest` 4.20.0 ·
`@browserbasehq/sdk` 2.20.0 · `@browserbasehq/stagehand` 4.1.0 · `playwright` 1.63.0 · `jose` 6.2.12.

**Cloudflare Browser Run** is GA and materially better than the spec assumed: it ships **Quick Actions**
as plain HTTP endpoints — `/markdown`, `/json` (AI structured extraction), `/screenshot`, `/pdf`,
`/accessibilityTree`, `/scrape`, `/links`, `/crawl` (beta) — plus full CDP for Puppeteer/Playwright and
a **Stagehand beta**. This makes it the cheapest first browser provider: a "summarize this page" tool is
one HTTP call, no browser fleet.

---

## 2. Target architecture

```text
┌─────────────────── Android (Kotlin/Compose) ────────────────────┐
│ Chat │ Agents │ Activity │ Connections │ Settings               │
│   — thin client: views + ViewModels + SSE consumption —         │
│   Room: local cache & offline read.  Keystore: session token.   │
│   ✗ no Gemini key  ✗ no tool logic  ✗ no scheduling             │
└─────────────────────────────┬───────────────────────────────────┘
                              │ HTTPS + SSE, Bearer <session>
┌─────────────────────────────▼───────────────────────────────────┐
│                     API Gateway (Fastify)                        │
│  auth middleware → per-user scoping → zod validation → rate limit│
├──────────────┬──────────────────────────────────────────────────┤
│ Chat Service │              Agent Service                        │
│  history,    │  Agent Builder (NL → config, §12/§59)            │
│  title gen,  │  Runtime: Gemini ⇄ tool loop (§32)                │
│  streaming   │  Tool Registry + runToolWithSafety (§15/§47)      │
├──────────────┴──────────────────────────────────────────────────┤
│  AIProvider ─ GeminiProvider (@google/genai)                     │
│  IntegrationAdapter ─ Native │ n8n │ Composio                    │
│  BrowserProvider ─ Cloudflare │ Browserbase │ Playwright+Stagehand│
│  Scheduler ─ Trigger.dev / Inngest (server-side, §33)            │
└─────────────────────────────┬───────────────────────────────────┘
                              ▼
        PostgreSQL (Prisma, §31)  ·  Secret store (encrypted OAuth)
```

### 2.1 The tool safety pipeline (§15, §47) — the heart of the system

Every tool call, whether from interactive chat or an autonomous agent run, passes through **one**
function. It fails closed.

```ts
async function runToolWithSafety(ctx, toolId, args, callId) {
  const tool = registry.get(toolId);
  if (!tool)                                  return deny('tool_unavailable');      // 1
  if (!ctx.agent?.grants.includes(tool.id))   return deny('agent_lacks_permission');// 2
  const conn = await connections.usable(ctx.userId, tool.auth);
  if (!conn || !coversScopes(conn, tool.scopes))
                                              return deny('needs_connection');      // 3
  if (tool.approval === 'always' || (tool.approval === 'write' && tool.isWrite))
                                              return suspendForApproval(...);       // 4
  const result = await tool.execute(args, conn);                                     // 5
  await auditLog(ctx, tool, args, result);                                           // 6
  return ok(result);
}
```

Steps 1–4 are the enforcement points §47 demands. Order matters: **approval is checked before
execution, and a missing permission short-circuits before any network call.**

---

## 3. Data model

The existing `prisma/schema.prisma` already covers §31's entity list. Required changes:

| Entity | Change | Why |
|---|---|---|
| `User` | add `timezone`, `passwordHash`/`authProvider`, `deletedAt` | §33 schedules need a timezone; §29 auth |
| `Message` | add `interactionId` (Gemini turn-link), `toolCalls Json?`, `status` | §3 needs `previous_interaction_id`; streaming needs a status |
| `Agent` | `tools`/`permissions` → typed relations; add `version`, `nextRunAt` | §13, §37; `nextRunAt` powers §45 "Next run" |
| `Schedule` | add `cron`, `timezone`, `nextRunAt`, unique on `agentId` | §33 recurring/one-time |
| `Execution` | add `approvalId?`, `tokensUsed`, `durationMs` | §34 status incl. `WAITING_FOR_APPROVAL`; §56 usage limits |
| **`Approval`** *(new)* | `id, executionId, toolId, payload Json, status, decidedAt` | §36 has no home in the current schema |
| **`AuditLog`** *(new)* | `id, userId, actor, action, toolId, redactedArgs, result, at` | §46 audit logging |
| **`OAuthState`** *(new)* | `state, userId, provider, codeVerifier, expiresAt` | §38 PKCE flow, replay protection |
| `Usage` | add `model`, `kind` (chat/agent), index `(userId, createdAt)` | §56 limits |
| `Notification` | add `executionId?`, `type`, `readAt` | §43 deep-links into executions |

**Security invariant:** `Connection.encryptedToken` is encrypted with a KMS/env key, never returned by
any API, and never written into `ExecutionStep.metadata` (§35). Add a test that greps API responses for
token-shaped strings.

---

## 4. API contract

Extend `shared/openapi.yaml`. Target surface:

```text
POST   /v1/auth/register | /login | /refresh | /logout
GET    /v1/me

GET    /v1/conversations?q=&archived=&cursor=
POST   /v1/conversations
GET    /v1/conversations/{id}
PATCH  /v1/conversations/{id}          # rename, archive
DELETE /v1/conversations/{id}
GET    /v1/conversations/{id}/messages

POST   /v1/chat/stream                 # SSE: token | tool_call | approval | done | error
POST   /v1/chat/stop                   # §6 stop generation

POST   /v1/files                       # §10 upload → validate → store → extract
GET    /v1/files/{id}

POST   /v1/agents/build                # §12 NL → {config, questions[]}
POST   /v1/agents                      # persist (status=draft)
GET    /v1/agents  |  /v1/agents/{id}
PATCH  /v1/agents/{id}                 # §13 review/edit before activate
POST   /v1/agents/{id}/activate | /pause
POST   /v1/agents/{id}/run             # §33 Run Now

GET    /v1/executions?agentId=&status=&cursor=
GET    /v1/executions/{id}             # §44 detail incl. steps
POST   /v1/executions/{id}/cancel

GET    /v1/approvals?status=pending
POST   /v1/approvals/{id}              # {decision: approve|reject}  §36

GET    /v1/connections
GET    /v1/connections/{provider}/authorize   # → OAuth URL, PKCE
GET    /v1/connections/{provider}/callback
DELETE /v1/connections/{provider}
GET    /v1/tools                       # registry, for the visual editor

GET    /v1/memories  POST  PATCH/DELETE /v1/memories/{id}   # §39
GET    /v1/notifications  POST /v1/notifications/read       # §43
```

**SSE event schema** (one shape for chat *and* agent runs, so the client has one streaming path):

```jsonc
{"type":"token",    "text":"..."}
{"type":"tool_call","toolId":"github.list_issues","summary":"Checking assigned issues"}
{"type":"approval", "approvalId":"...","toolId":"github.create_issue","payload":{...}}
{"type":"step",     "label":"Found 5 issues"}
{"type":"error",    "code":"rate_limited","retryable":true}
{"type":"done",     "messageId":"..."}
```

---

## 5. Phased delivery

Each phase ends at a **demoable milestone** and leaves `main` green. Phases map 1:1 to §50–§56.

### Phase 0 — Foundation hardening ✅ exists, needs work
Boilerplate is present but built on stale assumptions.
- [ ] Replace `@google/generative-ai` → **`@google/genai` 2.22.0**; rewrite `GeminiProvider` against
      `interactions.create` (§1.1).
- [ ] Pin real dependency versions across backend + Android (§1.2, §1.3).
- [ ] Add `docker-compose` Postgres check, `.env.example` completeness, `npm run typecheck`.
- [ ] **Acceptance:** `npm run build` clean; a scripted call to `gemini-3.8-flash` returns text.

### Phase 1 — ChatGPT core (§50) ← *implementing now*
- [ ] **Auth (§29):** register/login → JWT (access + refresh), bcrypt/argon2, `jose` for signing.
      Middleware resolves `userId` from the token — **never** from the request body.
- [ ] **Conversations (§8):** full CRUD + search + archive; auto-title via a cheap Gemini call after
      the first exchange, manually editable.
- [ ] **Streaming chat (§3, §6):** `POST /v1/chat/stream` SSE, backed by Gemini `stream: true`,
      persisting the assistant message on completion; abort support.
- [ ] **Android chat UI (§6, §7):** streaming bubble, markdown + syntax-highlighted code blocks with
      copy, regenerate, stop, retry, edit-and-resend, share.
- [ ] **Input (§7):** expanding text field, attachment picker (image/file), model selector.
- [ ] **States (§4):** light/dark, keyboard-aware, loading/empty/error/offline.
- [ ] **Acceptance:** login → send a message → tokens appear progressively → kill and reopen the app →
      the conversation is intact.

### Phase 2 — Files, multimodal, voice (§51)
- [ ] Upload → validate (type/size/AV) → store → extract → attach to Gemini context (§10).
- [ ] PDF/TXT/DOCX/CSV/image support; Gemini Files API for large binaries, inline for small.
- [ ] Voice **as a separate module** (§11): STT → Gemini → TTS. Build on `gemini-3.5-transcribe` and
      `gemini-3.1-flash-tts-preview`, or the Live API for realtime.

### Phase 3 — Agent framework (§52)
- [ ] `Tool` interface + registry (§15); seed with `github.*` and `web.search` (already stubbed).
- [ ] `runToolWithSafety` (§2.1) with unit tests per gate.
- [ ] Agent Builder: NL → structured config with clarifying questions (§12, §14, §59), validated
      against `shared/agent-config.schema.json`.
- [ ] Agent runtime: the Gemini ⇄ tool loop using `previous_interaction_id` chaining.
- [ ] Run Now, execution records + steps (§34, §35).
- [ ] **Acceptance:** "check my GitHub issues" builds a config, a Run Now executes it, Activity shows
      the step timeline, and every tool call is permission-checked.

### Phase 4 — Integrations (§53)
Level 1 native: **GitHub, Gmail, Google Calendar** (+ Gemini, already native). Then Slack, Notion.
Then X, WhatsApp (Business API), Google Docs/Drive, LeetCode, other dev tools (§27).
- [ ] OAuth with PKCE, encrypted token storage, granular scopes (§37, §38).
- [ ] **GitHub first-class (§17):** implement the listed `github.*` tools with read/write split.
      Write tools are `approval: 'always'`. Connecting GitHub must **not** grant write (§17).
- [ ] `IntegrationAdapter` for n8n/Composio/Pipedream (§25, §26) for everything below Level 1.

### Phase 5 — Automation (§54)
- [ ] Scheduler on Trigger.dev **or** Inngest (§30, §33): Run Now / one-time / recurring + timezone.
- [ ] Server-side workers — independent of the Android app (§32). *Test by running an agent with the
      emulator closed.*
- [ ] Approvals (§36) + FCM push notifications with user-controlled preferences (§43).

### Phase 6 — Browser agents (§55)
- [ ] `BrowserProvider` with three implementations (§23): **Cloudflare Quick Actions** (cheapest —
      start here), Browserbase, Playwright (+ Stagehand for NL tasks, §24).
- [ ] Browser task history; session persistence where appropriate.

### Phase 7 — Advanced agents (§56)
- [ ] Persistent memory, viewable/editable/deletable (§39).
- [ ] Multi-step planning, multi-agent delegation, long-running jobs, usage limits, observability.

---

## 6. Security plan (§46, §47, §61)

| Risk | Control | Verified by |
|---|---|---|
| Gemini key in APK | Backend-only; key never in `buildConfigField` | CI grep of the built APK for `AIza…` |
| Client spoofs `userId` | Middleware derives identity from the session only | Test: forged body `userId` is ignored |
| Over-broad OAuth | Per-provider granular scopes; least privilege (§37) | Scope diff test on connect |
| Unintended writes | `approval: 'always'` on every write/send tool | Test: write tool suspends without approval |
| Secret leakage in logs | Redaction in `ExecutionStep.metadata` + audit log | Grep responses/logs for token shapes |
| Token at rest | Envelope encryption; KMS/env master key | Decrypt only inside the tool runtime |
| Abuse / runaway agents | Per-user rate limits, execution timeouts, token budgets | Load test + budget-exceeded path |
| Prompt-injection via web content | Tool results are untrusted data; writes still gated by approval | Adversarial page fixture test |

**Rule:** a failed permission check must never fall through to execution. All gates return an explicit
deny, and the model receives a structured "permission required" result so it can tell the user
(§47's `[Connect GitHub]` affordance).

---

## 7. Testing strategy

- **Backend:** Vitest. Unit — the four safety gates, schedule→cron expansion, title generation, SSE
  framing. Integration — auth isolation, conversation CRUD, stream completion, approval round-trip.
- **Contract:** validate responses against `shared/openapi.yaml`; a schema drift test fails CI.
- **Android:** Compose UI tests for chat states; ViewModel tests with a fake SSE source; a `Turbine`
  test proving tokens render progressively.
- **E2E (§50 milestone):** login → chat → stream → reopen → history intact, on a real emulator.
- **Adversarial:** forged `userId`, missing permission, unapproved write, oversize upload,
  prompt-injection payload in a fetched page.

---

## 8. Risks and mitigations

| Risk | Impact | Mitigation |
|---|---|---|
| **Gemini API churn** — `interactions` already replaced `generateContent` | Provider rewrite | `AIProvider` boundary (§2); provider tests run in CI against a recorded cassette |
| Gemini 3 availability/quotas by tier | Feature gaps | Model capability probe at startup; graceful per-feature degradation |
| Android version drift | Build breaks | Compose BOM + version catalog (`libs.versions.toml`) |
| Trigger.dev vs Inngest lock-in | Migration cost | Scheduler behind a thin internal interface |
| OAuth review delays (Gmail/Slack) | Phase 4 slip | Start review submissions in Phase 3; keep n8n/Composio as Level 2 fallback |
| Browser automation ToS (§18, §41) | Legal | Per-site allowlist with documented permission; prefer official APIs first |
| Cost blowup from agents | Runaway spend | Token budgets, per-user caps, alerting (§56) |

---

## 9. Explicit flags — requirements with real-world constraints

These are **not** dropped, but they cannot be satisfied by code alone. Flagging per §3 of `PROMPT.md`.

1. **LeetCode (§18)** exposes **no official public API** for the described operations. The spec already
   acknowledges this. Plan: a LeetCode integration only where access rules permit; do **not** bypass
   auth, CAPTCHA, or rate limits. Ship it behind a feature flag and surface "unavailable" honestly.
2. **X/Twitter (§19)** API tiers gate most read/write capabilities. Implement capability detection and
   report unavailable actions gracefully, as §19 requires.
3. **WhatsApp (§20)** — Business/Cloud API only; requires a verified business account and template
   approval. Personal-account automation is explicitly out of scope.
4. **Gmail/Slack/Google scopes** require OAuth app verification (Google CASA for restricted Gmail
   scopes) before public release. This is a **scheduling** risk, not a coding one.
5. **Instagram, TikTok, and other social APIs** are not named in the spec; do not add them
   speculatively.

---

## 10. External verification log

Facts checked live during planning (not from memory):

| Fact | Source |
|---|---|
| Interactions API, `tools:[{type:'function'}]`, `steps[]`, `function_result` + `previous_interaction_id` | [Function calling](https://ai.google.dev/gemini-api/docs/function-calling) |
| Model IDs incl. `gemini-3.8-flash`, live/tts/transcribe variants | [Models](https://ai.google.dev/gemini-api/docs/models) |
| SSE streaming with `stream: true` / `event_type` | [Streaming](https://ai.google.dev/gemini-api/docs/streaming) |
| `@google/genai` **2.22.0**; `@google/generative-ai` now legacy at 0.24.1 | npm registry |
| Kotlin 2.4.20, KSP 2.3.12, Compose BOM 2026.09.00, room 2.8.5, navigation 2.10.1, AGP 9.4.0 | Maven Central / Google Maven |
| OkHttp 5.5.0, Retrofit 3.0.0, Coil 3.6.2 | Maven Central |
| Cloudflare Browser Run Quick Actions + Stagehand beta | [Browser Run docs](https://developers.cloudflare.com/browser-run/) |
| fastify 5.12.4, zod 4.6.2, @prisma/client 7.10.0, Trigger.dev 4.5.16, Inngest 4.20.0, Stagehand 4.1.0, Playwright 1.63.0 | npm registry |
| GitHub REST rate limits (unauthenticated core = 60/hr) — motivates OAuth App tokens | `api.github.com/rate_limit` |

---

## 11. Immediate next actions

1. **Rewrite `GeminiProvider`** against `@google/genai` + the Interactions API (unblocks everything).
2. **Add auth middleware** — the foundation for §29 and per-user isolation.
3. **Ship conversations CRUD + SSE streaming** and prove the §50 milestone end-to-end.
4. **Set up the Android version catalog** and bump to Compose BOM 2026.09.00 / Kotlin 2.4.20.
5. **Write the safety-gate tests** *before* Phase 3 tools exist — the gates are the security boundary.
