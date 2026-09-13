# Nova — Android ChatGPT + Agentic AI Platform

Main repo for Nova. Android-first, backend-owned Gemini keys, web/iOS-ready API.

> Spec source: `../PROMPT.md` (§0-§62). Phases 1-7 built in order. Phase 1 (ChatGPT Core) first.

## Mission (§0)

- ChatGPT-style mobile assistant for normal conversations (§1.1)
- Natural-language autonomous agents with tools, connections, schedules (§1.2)
- Android-first, backend + AI/tool architecture reusable for web/iOS later

## Repo layout

```text
Nova/
├── android/            # Native Kotlin + Compose + Material3 (§4)
├── backend/            # TS REST + SSE, Chat Service + Agent Service (§48-§49)
├── shared/             # OpenAPI + agent JSON schema (cross-client contract)
├── docs/               # Architecture, phases, security
└── docker-compose.yml  # Local Postgres
```

## Architecture (§3, §48)

Normal chat: `Android -> Backend -> Gemini API -> SSE stream -> Android`
Agent run: `Schedule -> JobQueue -> AgentWorker -> Gemini -> PermissionLayer -> Tool -> Gemini -> Result`

Provider abstractions (never hardcode vendor):
- `AIProvider -> GeminiProvider` (§2)
- `BrowserProvider -> Browserbase | Cloudflare | Playwright` (§23)
- `IntegrationAdapter -> Native | n8n | Composio | Pipedream` (§25)

## Security (§46)

Backend owns `GEMINI_API_KEY`, DB creds, OAuth secrets. APK never contains them.
Auth on every request, per-user isolation, encrypted OAuth tokens, rate limits,
tool permission checks, audit log, execution limits.

## MVP order (§50-§56)

1. Phase 1: auth, chat, streaming, conversations, markdown — `Login>Chat>Gemini>SSE>Persist`
2. Phase 2: files + multimodal + voice — `attach>upload>sniff>Neon>extract>Gemini`, voice in/out — live-probed green
3. Phase 3: agent framework (builder, tools, function-calling, Run Now)
4. Phase 4: GitHub, Gmail, Calendar, Slack, Notion, then X/WhatsApp/Drive/LeetCode
5. Phase 5: schedules, notifications, approvals, retries
6. Phase 6: Browserbase/Stagehand/Cloudflare/Playwright
7. Phase 7: memory, multi-agent, delegation, HITL, limits, observability

## Quickstart

```bash
# Backend (needs Postgres + GEMINI_API_KEY server-side only)
cd backend
cp .env.example .env
npm install
npx prisma migrate dev
npm run dev

# Android (local.properties: api.baseUrl=http://10.0.2.2:3000, NO keys)
# Open android/ in Android Studio, run on emulator
```

See `docs/ARCHITECTURE.md`, `docs/PHASES.md`, `docs/SECURITY.md`.
