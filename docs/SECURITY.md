# Security — §46 + §47

Never in APK: GEMINI_API_KEY, DB creds, OAuth secrets, service accounts.
Flow: Android -> authenticated backend -> secrets/external APIs.
Enforce: auth, per-user isolation, encrypted OAuth tokens, rate limit, Zod validation,
permission checks, audit log, execution limits, no secret leakage in ExecutionSteps.
