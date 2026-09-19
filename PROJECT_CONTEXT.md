# Nova — Project Context (for AI agents)

> Main repo: `Nova/`. Spec: `../PROMPT.md` (§0-§62). Read PROMPT.md + this file before coding.
> Mission: ChatGPT-style Android assistant + natural-language autonomous agents. Android-first, backend owns Gemini keys, API reusable for web/iOS.

## 1. Tech stack

- Android: Kotlin, Jetpack Compose, Material3, Navigation Compose, ViewModel, Coroutines + StateFlow, Retrofit/OkHttp (SSE), Kotlin Serialization, Room (cache), Keystore (session only), WorkManager (local only)
- Backend: TypeScript Node20, Fastify, SSE (`/v1/chat/stream`), Prisma + Postgres, Zod, `@google/generative-ai` server-side only
- Infra (planned): Trigger.dev/Inngest (scheduler), n8n/Composio (integrations), Browserbase/Cloudflare/Playwright + Stagehand (browser)
- Contracts: `shared/openapi.yaml` (REST+SSE), `shared/agent-config.schema.json`

## 2. Folder structure

```text
Nova/
├── README.md                    # mission, arch diagrams, quickstart
├── PROJECT_CONTEXT.md           # this file — AI entry point
├── .gitignore
├── docker-compose.yml           # local Postgres (nova/nova)
├── shared/
│   ├── openapi.yaml             # /health, /v1/chat/stream, conversations, agents, run, executions, approvals, connections
│   ├── agent-config.schema.json # Agent JSON contract (§13)
│   └── example-agent.json       # GitHub Daily Digest example
├── backend/
│   ├── package.json             # fastify, @google/generative-ai, prisma, zod, tsx
│   ├── tsconfig.json
│   ├── .env.example             # DATABASE_URL, GEMINI_API_KEY, AUTH_JWT_SECRET — server only, never APK
│   ├── prisma/schema.prisma     # §31: User, Conversation, Message, Attachment, Agent, Tool, AgentTool, Connection, Schedule, Execution, ExecutionStep, Memory, Usage, Notification
│   └── src/
│       ├── index.ts             # Fastify gateway: /health, POST /v1/chat/stream (SSE), stubs for agents/run/executions
│       ├── ai/AIProvider.ts     # interface: streamChat(), generateAgentConfig()
│       ├── ai/GeminiProvider.ts # Gemini impl, function-calling bridge, JSON agent-config gen
│       ├── tools/Tool.ts        # interface Tool + runToolWithSafety() pipeline (§15,§47)
│       ├── tools/registry.ts    # native Level-1 tools: github.list_issues, web.search
│       ├── browser/BrowserProvider.ts  # Browserbase | Cloudflare | Playwright (§23)
│       ├── integrations/IntegrationAdapter.ts # Native | n8n (§25)
│       └── services/
│           ├── chatService.ts   # persist msgs, stream, auto-title (§8)
│           └── agentService.ts  # executeAgent(): Schedule->Queue->Worker->Gemini->Tools (§32,§35)
├── android/
│   ├── settings.gradle.kts / build.gradle.kts / gradle.properties
│   └── app/build.gradle.kts     # compose+buildConfig, debug API_BASE_URL=http://10.0.2.2:3000, NO keys
│   └── app/src/main/
│       ├── AndroidManifest.xml  # INTERNET, RECORD_AUDIO (voice §11), POST_NOTIFICATIONS (§43)
│       └── kotlin/com/nova/app/
│           ├── MainActivity.kt  # Material3 + light/dark entry
│           ├── NovaNav.kt       # §5 tabs: chat|agents|activity|connections|settings, Chat start + New Chat
│           ├── Screens.kt       # ChatScreen (+ChatViewModel streaming/stop/newChat), AgentsScreen (builder §12-14), ActivityScreen (§44), ConnectionsScreen (§38), SettingsScreen (§39,§43)
│           ├── data/NovaApi.kt  # Retrofit interface stubs — real backend only, no fake AI (§61)
│           └── security/SessionKeystore.kt # AES/GCM session tokens only, never Gemini keys (§46)
└── docs/
    ├── ARCHITECTURE.md          # chat vs agent flows, provider abstractions
    └── SECURITY.md              # APK bans, auth, isolation, audit
```

## 3. Key flows (§3,§32)

- Chat: `Android -> POST /v1/chat/stream -> chatService -> GeminiProvider.streamChat (Interactions API, stream:true) -> SSE chunks -> Android append`. `previous_interaction_id` chaining ready for tool loops.
- Agent: `Schedule -> JobQueue -> agentService.executeAgent -> Gemini function-call loop -> runToolWithSafety (validate→permission→auth→approval?→execute) -> Execution + steps -> notification`
- Agent creation (§59): `NL -> AI detects goal/tools/permissions/schedule -> asks missing -> generates JSON (schema) -> user Edit/Activate`

## 4. Rules (§46,§47,§61)

1. Backend owns `GEMINI_API_KEY`, DB creds, OAuth secrets. Grep APK for keys = fail.
2. Every backend route checks auth user, per-user isolation. Never trust client userId.
3. Least-privilege tools: `github.read` ≠ `github.write`. Approval gate for writes/sends.
4. Provider code behind interfaces: `AIProvider`, `Tool`, `BrowserProvider`, `IntegrationAdapter`.
5. No fake AI in prod flows. No personal-WhatsApp hacks (Business API only §20). Browser only where permitted (§18,§41).
6. Scheduler + execution server-side; phone locked/offline must still run (§32-§33).
7. ExecutionSteps store metadata, never tokens/secrets (§35).

## 5. How to extend

- New tool: add `Tool` in `backend/src/tools/registry.ts`, add row to `Tool` table, expose via `AIProvider` ToolDef, check permission in `runToolWithSafety`.
- New integration (Level 2/3 §27): implement `IntegrationAdapter` or `BrowserProvider`, wire in `agentService`.
- New Android screen: add route in `NovaNav.kt`, UI in `Screens.kt`, DTO in `data/`, call `shared/openapi.yaml` endpoint.
- New model: change `model` param in `GeminiProvider`, keep `AIProvider` signature stable.

## 6. MVP status (Phases §50-§56)

- [x] Phase 0 boilerplate (this tree)
- [x] Phase 1 ChatGPT Core: auth (scrypt+JWT), SSE streaming via Gemini Interactions, conversations CRUD/search/archive, auto-title, markdown+code, model selector, edit/share/regenerate/stop — live probe green (a0e9ec9)
- [x] Phase 2 files+multimodal+voice: /v1/files (magic-byte sniff, 20MB cap, Neon bytea), DOCX/PDF/CSV/TXT/images, inline parts + extracted text to Gemini Interactions, voice module (SpeechRecognizer + Android TTS, swappable), in-context mic/notification permissions — live probe green (vision/PDF/DOCX/CSV all answered by real Gemini)
- [x] Phase 3 agent framework: DB-backed safety gate (permission+connection resolved server-side, fail-closed), tool registry (web.search live via Exa, github.* honest deny pre-OAuth), explicit Gemini tool loop (arguments_delta buffering, chained follow-up with fresh-text fallback, 10-step/55s bounds, flash-lite quota fallback), Run Now + Execution/steps/Approval persistence, NL builder (registry-pinned tool ids, questions on gaps), Android Agents/Activity screens + approvals UI — live probe green on Vercel+Neon (COMPLETED web run with real summary, denial explained for unconnected GitHub)
- [x] Scope-cut hardening (2026-09): single MODELS registry (ai/models.ts); Android notice/approval chunk handling; 9 chat plugins (github/gmail/leetcode/calendar/drive/docs/sheets/vercel/supabase, chat read-only + agent-side approval writes); Drive OAuth + Vercel/Supabase token connections with Android connect/disconnect UI; agent browser via Browserless free tier (SSRF-guarded browserPolicy + task history store); E2B cloud workspace tools; AMOLED true-black + liquid-glass composer/drawer; TTS accepts AI_API_KEY; agent-builder null-safety — backend typecheck + 7 test suites green, :app:assembleDebug green
- [x] F6 agentic depth (2026-09-19): Browserless scrape/screenshot/pdf tools + browser_task research pipeline (fetch pages → optional E2B transform script, approval-gated); workspace_list_files; E2B sandbox auto-cleanup per agent run (register/unregister/cleanup, finally-block in agentService); background=true Run Now (QUEUED, 10-min budget, DB-polled cancel, CANCELLED persisted honestly; true async needs a long-lived host — Vercel lambda freezes post-response) + Android "In background" button with execution polling — backend typecheck + 9 test suites green
- [x] Gen-UI fix (2026-09-19): chat-side present_ui tool (typed params → uiBlockSchema validation → real `ui` SSE chunk + persisted metadata); UI hint in every chat system message (never print UI JSON in prose); tool-loop no longer double-feeds OpenAI-style transcripts alongside function_result chaining; browser_scrape added to presentable tools + browser plugin

## 2026-09-18 UI/UX overhaul

- Android app redesigned: docs/UI.md has the full list. Design system in NovaDesign.kt (theme modes, tokens, shared components), bottom navigation in NovaNav.kt, streaming-scroll fix, truthful timestamps, 48dp touch targets, delete confirms, server-backed chat search/rename/archive, WCAG contrast fixes, login rewrite. All compiles clean (:app:compileDebugKotlin).
