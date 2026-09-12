# Architecture — §48 practical stack

`Android (Kotlin/Compose) --HTTPS/SSE--> Backend (TS Fastify) --Postgres + Gemini--`
Scheduler (Trigger.dev/Inngest) + Integrations (n8n/OAuth) + Browser (Browserbase/Stagehand).

Server-side agent runs survive phone locked/offline/closed (§32). Scheduler is server-side (§33).
Tool safety gate before every execute (§47). Granular permissions, least privilege (§37).
