# PROMPT: Android ChatGPT + Agentic AI Platform

You are acting as the lead engineer/architect for a production-quality **native Android AI application**. Read this entire document before writing any code. Every requirement below is mandatory unless explicitly marked optional/"evaluate." Do not skip, simplify away, or silently substitute any requirement — if a trade-off is genuinely necessary, flag it explicitly instead of dropping it silently.

---

## 0. Mission

Build a native Android application that combines the core ChatGPT experience with an advanced agentic automation platform.

- It should feel like a ChatGPT-style mobile assistant for normal conversations.
- It should also let advanced users create AI agents that use tools, access connected services, browse the web, perform multi-step tasks, and run automatically on schedules.
- The product is **Android-first**, but the backend and core AI/tool architecture must be designed so a web or iOS client can be added later without a rewrite.

---

## 1. Core Product

The application has two tightly integrated experiences.

### 1.1 ChatGPT Experience

A user can open the app and immediately start chatting with an AI. Examples of normal use:

- Explain binary search.
- Help me debug this Java code.
- Summarize this PDF.
- Look at this screenshot and explain the error.
- Help me plan my week.

The normal chat experience must **not** require the user to understand agents, tools, APIs, workflows, or automation.

### 1.2 Agent Experience

Advanced users can create autonomous agents from natural language, e.g.:

> Every weekday at 9 AM, check my Gmail for important unread emails, summarize them, and send me a digest.

The system must turn this into a structured agent configuration, ask for missing requirements, identify required tools and permissions, let the user review the configuration, and then activate the agent.

---

## 2. AI Model Provider

- Use **Google Gemini models** — **not** the OpenAI API.
- The backend integrates with the Gemini API for: normal chat, streaming responses, multimodal interactions, function/tool calling, agent planning, and structured agent configuration.
- Keep the AI provider behind an abstraction so models can be swapped without rewriting the app:

```text
AIProvider
    |
    +-- GeminiProvider
```

- The backend owns the Gemini API credentials. **The Android APK must never contain the production Gemini API key.**
- Reference docs:
  - https://ai.google.dev/gemini-api
  - https://ai.google.dev/gemini-api/docs/function-calling

---

## 3. Gemini Architecture

**Normal chat:**

```text
Android App
     |
     | HTTPS / Streaming
     v
Your Backend
     |
     v
Gemini API
     |
     v
Streaming Response
     |
     v
Android App
```

**Agent execution:**

```text
User Request
     |
     v
Gemini
     |
     | Tool / Function Call
     v
Permission Layer
     |
     v
Tool Execution
     |
     v
Tool Result
     |
     v
Gemini
     |
     v
Final Response
```

Gemini function calling is the primary mechanism for connecting the model to external APIs and application tools.

---

## 4. Android Technology Stack

Build the client as a **native Android application**. Do **not** build the main application as a WebView.

Required/recommended stack:

- Kotlin
- Jetpack Compose
- Material 3
- Navigation Compose
- ViewModel
- Kotlin Coroutines
- StateFlow / Flow
- Retrofit / OkHttp
- Kotlin Serialization
- Room where local persistence/cache is useful
- Android Keystore for device-side sensitive data
- WorkManager only for appropriate local Android tasks

The UI must be designed specifically for Android phones and support:

- Light mode
- Dark mode
- Different screen sizes
- Keyboard-aware chat
- Accessibility
- Loading states
- Empty states
- Error states
- Offline states

---

## 5. Application Navigation

Primary navigation:

```text
Chat
Agents
Activity
Connections
Settings
```

- Chat is the primary experience.
- Provide a clear **New Chat** action.
- The app must not feel like a business dashboard — Agents should feel like an extension of the chat experience.

---

## 6. Chat Screen

Build a polished ChatGPT-style chat interface supporting:

- User messages
- Assistant messages
- Streaming responses (must stream progressively)
- Markdown: headings, lists, tables where practical, code blocks, syntax highlighting, inline code, links
- Copy message
- Copy code
- Regenerate response
- Stop generation
- Retry
- Edit user message
- Continue conversation
- Share response

---

## 7. Chat Input

Support:

- Text input
- Attachments (images, files)
- Voice input
- Send
- Stop generation
- Model selection

The input should expand naturally for longer messages. Keep the primary interaction simple and familiar.

---

## 8. Conversations

Persist conversations on the backend.

**Conversation entity:**

```text
Conversation
 ├── id
 ├── userId
 ├── title
 ├── createdAt
 ├── updatedAt
 └── messages[]
```

**Message entity:**

```text
Message
 ├── id
 ├── conversationId
 ├── role
 ├── content
 ├── model
 ├── createdAt
 ├── attachments
 └── metadata
```

Users must be able to: create, rename, delete, search, continue (old conversations), and archive conversations.

Generate conversation titles automatically, while allowing manual editing.

---

## 9. Multimodal AI

Use Gemini multimodal capabilities where supported: text, images, PDFs/documents where supported, audio/voice workflows where appropriate.

Example flow:

```text
User uploads screenshot
        |
        v
Gemini
        |
        v
Explain the error
```

Use backend preprocessing when a file cannot be sent directly to the selected model.

---

## 10. Files

Allow users to attach: PDF, TXT, DOCX, CSV, Images, and other supported document formats.

**Backend flow:**

```text
Upload
  |
  v
Validate
  |
  v
Secure storage
  |
  v
Extract/process content
  |
  v
Gemini context
  |
  v
Answer
```

Example:

```text
resume.pdf
      |
      v
User: Rewrite my summary
      |
      v
Gemini
      |
      v
Rewritten summary
```

---

## 11. Voice

Add voice as a modular capability, independent from the main chat implementation.

**Input:**

```text
Speech
   ↓
Speech-to-text
   ↓
Gemini
```

**Output:**

```text
Gemini
   ↓
Text
   ↓
Text-to-speech
   ↓
Audio
```

---

## 12. Agent Builder

Create an **Agent Builder**. Users describe what they want in natural language, e.g.:

> Create an agent that checks my GitHub repositories every morning and tells me about new issues and pull requests.

The AI must determine: goal, required tools, required connections, required permissions, schedule, expected output, and missing requirements. If information is missing, ask clarifying questions.

---

## 13. Agent Configuration

**Agent entity:**

```text
Agent
 ├── id
 ├── name
 ├── description
 ├── goal
 ├── instructions
 ├── tools
 ├── permissions
 ├── schedule
 ├── status
 ├── createdAt
 └── updatedAt
```

Example:

```json
{
  "name": "GitHub Daily Digest",
  "goal": "Monitor my GitHub activity",
  "instructions": "Check assigned issues, pull requests and relevant repository activity.",
  "tools": [
    "github.list_issues",
    "github.list_pull_requests"
  ],
  "schedule": {
    "type": "recurring",
    "frequency": "daily",
    "time": "09:00"
  }
}
```

The user must be able to review and edit the configuration before activation.

---

## 14. Conversational Agent Creation

Agent creation itself happens through chat:

```text
User:
I want an agent that checks GitHub every morning.

AI:
What should it monitor?

User:
My assigned issues and pull requests.

AI:
What time should it run?

User:
9 AM.

AI:
Here is your agent configuration...
```

Then show a summary card:

```text
GitHub Daily Monitor

Goal:
Monitor assigned GitHub issues and pull requests.

Schedule:
Every day at 9:00 AM.

Tools:
GitHub

[Edit]
[Activate]
```

---

## 15. Tool System

Create a generic tool abstraction:

```text
Tool
 ├── id
 ├── name
 ├── description
 ├── inputSchema
 ├── permissions
 ├── authentication
 └── execute()
```

The Gemini model receives available tools in structured form. When Gemini requests a tool:

1. Validate the tool.
2. Check agent permissions.
3. Check user authorization.
4. Check whether approval is required.
5. Execute the tool.
6. Return the result to Gemini.
7. Continue the execution.

---

## 16. Tool Categories

**Communication:** Gmail, Slack, WhatsApp (where supported through approved APIs), Email, Notifications.

**Developer:** GitHub, GitLab, Bitbucket, Jira, Linear, CI/CD systems.

**Productivity:** Google Calendar, Google Docs, Google Drive, Notion, Task systems.

**Social / Content:** X / Twitter, LinkedIn, other services where official APIs or permitted integrations exist.

**Web:** Search, Fetch, Browser automation, Page interaction.

**Data:** Databases, Spreadsheets, APIs, internal application data.

---

## 17. GitHub Integration

Support GitHub as a first-class integration. Potential capabilities:

```text
github.search_repositories
github.get_repository
github.list_issues
github.get_issue
github.create_issue
github.update_issue
github.list_pull_requests
github.get_pull_request
github.create_pull_request
github.comment_on_issue
github.comment_on_pull_request
github.get_notifications
github.list_commits
```

Separate read and write permissions:

```text
GitHub Read
    |
    +-- repositories
    +-- issues
    +-- pull requests

GitHub Write
    |
    +-- create issues
    +-- comment
    +-- create/update pull requests
```

Never grant write permissions merely because GitHub is connected.

---

## 18. LeetCode Integration

Provide a LeetCode capability where technically and contractually permitted. Potential use cases: track solved problems, track recent submissions, analyze progress, generate study plans, identify weak topics, summarize activity, maintain DSA progress.

Example:

> Every Sunday analyze my LeetCode activity and create a study plan for next week.

Do not assume LeetCode exposes an official API for every desired operation. Where an official API or permitted integration is unavailable, browser automation may be used **only** where the site's access rules and terms permit it. Do not design the system around bypassing authentication, CAPTCHA, rate limits, or access controls.

---

## 19. X / Twitter Integration

Treat Twitter as **X** in the technical architecture. Potential capabilities depend on available official API access: read permitted posts, search permitted content, publish posts, manage permitted account actions, monitor selected topics, generate drafts.

Separate:

```text
X Read
X Write
```

Do not assume every account has access to every X API capability. The integration should gracefully report unavailable capabilities.

---

## 20. WhatsApp Integration

Implement WhatsApp through an officially supported WhatsApp Business/API solution or appropriate provider.

```text
Agent
  ↓
WhatsApp Business/API
  ↓
Message
```

Examples:

> Send my daily report to WhatsApp.

> Create a WhatsApp customer-support assistant using our knowledge base.

Do **not** automate a personal WhatsApp account through unofficial methods. Treat WhatsApp as an external channel/integration with its own authorization and messaging rules.

---

## 21. Browser Automation

For websites without a suitable API, provide a browser automation capability. The goal is to avoid building custom browser infrastructure and automation scripts from scratch for every website.

### Browserbase + Stagehand

Browserbase provides managed cloud browsers and infrastructure for browser agents; Stagehand provides higher-level AI browser interaction. Useful for: browser sessions, navigation, clicking, form interaction, data extraction, multi-step web tasks, AI-driven browser agents. Browserbase also provides examples of browser agents using Gemini models.

References:
- https://www.browserbase.com/
- https://www.browserbase.com/templates/browser-agent-demo
- https://www.browserbase.com/templates/gemini-3-flash

Prefer Browserbase when managed browser infrastructure is more useful than maintaining our own browser fleet.

---

## 22. Cloudflare Browser Run

Evaluate **Cloudflare Browser Run** as another managed browser option. It provides browser automation infrastructure and supports Puppeteer, Playwright, CDP, and Stagehand. Useful for: headless browser sessions, screenshots, PDFs, scraping, dynamic websites, browser agents.

Reference: https://developers.cloudflare.com/browser-run/

Use it as an alternative to Browserbase depending on cost, deployment model, regional requirements, and the rest of the backend infrastructure.

---

## 23. Playwright

Use Playwright when deterministic browser automation is preferable — good for stable workflows, testing, explicit browser automation, known selectors, repeatable tasks.

Do not tightly couple the entire agent system to Playwright. Create a browser abstraction:

```text
BrowserProvider
      |
      +-- Browserbase
      +-- Cloudflare Browser Run
      +-- Playwright
```

---

## 24. Stagehand

Use Stagehand where natural-language browser interaction is useful, e.g.:

```text
Open GitHub.
Find my newest assigned issue.
Open it.
Extract the title and description.
```

The goal is to reduce the amount of fragile custom selector code that needs to be maintained. Use deterministic Playwright automation when a workflow is stable and known; use AI browser automation when the task is variable or natural-language driven.

---

## 25. Workflow / Integration Platforms

Do not implement every integration manually. Evaluate: n8n, Composio, Pipedream, Zapier, Make.

The integration layer must remain replaceable:

```text
Agent
  |
  v
Integration Adapter
  |
  +---- Native API
  +---- n8n
  +---- Composio
  +---- Pipedream
  +---- Other provider
```

---

## 26. n8n

n8n can be used as a workflow and integration layer; it supports AI-agent workflows and many application integrations.

References:
- https://n8n.io/
- https://n8n.io/integrations/ai-agent-tool/

Potential architecture:

```text
Your Agent
     |
     v
n8n
     |
     +---- Gmail
     +---- Slack
     +---- GitHub
     +---- Google
     +---- WhatsApp provider
     +---- Other services
```

n8n is particularly useful for integrations and workflows that would otherwise require custom connector code. Consider self-hosting if greater infrastructure control is required.

---

## 27. Integration Strategy

Do not build hundreds of integrations yourself. Use three levels:

**Level 1 — Native.** Build important integrations directly. Recommended initial native integrations: GitHub, Gemini, Gmail, Google Calendar.

**Level 2 — Integration Platform.** Use n8n, Composio, Pipedream, or similar services for lower-priority integrations.

**Level 3 — Browser Automation.** Use Browserbase, Stagehand, Cloudflare Browser Run, or Playwright when an appropriate API/integration is unavailable and browser automation is permitted.

This keeps development manageable.

---

## 28. Backend-as-a-Service

Evaluate managed backend infrastructure instead of implementing every infrastructure component from scratch.

**Supabase** — PostgreSQL, Authentication, Storage, Realtime, Backend functions.

**Firebase** — Authentication, push notifications, analytics, crash reporting, Android ecosystem integration, Firestore.

**Neon** — managed PostgreSQL.

The final choice should be based on cost, control, expected scale, and the rest of the backend architecture.

---

## 29. Authentication

Possible authentication approaches: Supabase Auth, Firebase Authentication, Clerk, or custom backend authentication.

- The Android application must use secure authenticated sessions.
- Every backend request must be associated with the authenticated user.
- Never trust a user ID supplied by the client without validating the authenticated session.

---

## 30. Background Jobs and Agent Execution

Evaluate managed job/workflow infrastructure instead of implementing a complete distributed worker system immediately. Possible options: Trigger.dev, Inngest, Temporal, cloud queues/workers.

For the first version, use the simplest reliable system that supports: delayed jobs, recurring schedules, retries, long-running tasks, execution status, failure handling.

---

## 31. Database

Use a relational database.

**Core entities:**

```text
User
Conversation
Message
Attachment
Agent
Tool
AgentTool
Connection
Schedule
Execution
ExecutionStep
Memory
Usage
Notification
```

**Relationships:**

```text
User
 ├── Conversations
 ├── Agents
 ├── Connections
 ├── Memories
 └── Usage

Conversation
 └── Messages

Agent
 ├── Tools
 ├── Schedule
 └── Executions

Execution
 └── ExecutionSteps
```

---

## 32. Agent Execution Engine

Agent execution must be independent of the Android application. Agents must continue to execute when the user's phone is locked, offline, powered down, or the app is closed.

```text
Schedule
   |
   v
Job Queue
   |
   v
Agent Worker
   |
   v
Gemini
   |
   +---- Tool
   |
   +---- Tool
   |
   +---- Tool
   |
   v
Result
```

---

## 33. Scheduler

Support:

- **Run Now** — immediate execution.
- **One-Time** — e.g. "Tomorrow at 10:00 AM."
- **Recurring** — e.g. "Every day at 9:00 AM," "Every weekday at 8:00 AM," "Every Monday at 10:00 AM," "Every Sunday."

The scheduler must be server-side. Do not depend on Android background execution for server-side scheduled agents.

---

## 34. Execution Model

Every agent run must produce an execution record:

```text
Execution
 ├── id
 ├── agentId
 ├── userId
 ├── trigger
 ├── status
 ├── startedAt
 ├── completedAt
 ├── output
 ├── error
 └── steps[]
```

**Statuses:**

```text
QUEUED
RUNNING
WAITING_FOR_APPROVAL
COMPLETED
FAILED
CANCELLED
```

---

## 35. Execution Steps

Track individual actions, e.g.:

```text
Agent started
     ↓
Connected to GitHub
     ↓
Checked assigned issues
     ↓
Found 5 issues
     ↓
Checked pull requests
     ↓
Found 2 pull requests
     ↓
Generated summary
     ↓
Completed
```

Store useful metadata **without** exposing credentials, OAuth tokens, or other secrets.

---

## 36. Human Approval

Agents must support approval checkpoints. Examples:

```text
Agent wants to create a GitHub issue.

Title:
Bug: login fails on Android

Description:
...

[Reject]
[Approve]
```

```text
Agent wants to send a WhatsApp message.

Recipient:
...

Message:
...

[Cancel]
[Approve]
```

Approval requirements must be configurable by tool/action.

---

## 37. Permission System

Use granular permissions, e.g.:

```text
GitHub
 ├── Read repositories
 ├── Read issues
 ├── Read pull requests
 ├── Create issues
 ├── Comment
 └── Create pull requests
```

Do not simply give an agent "GitHub access" — give it the smallest useful set of permissions.

---

## 38. OAuth / Connections

Create a Connections screen, e.g.:

```text
GitHub
Connected

Gmail
Connected

Slack
Not connected

Notion
Connected

X
Not connected

WhatsApp
Not connected
```

Users must be able to: connect, reauthorize, disconnect, and inspect granted permissions. Tokens must be stored securely on the backend.

---

## 39. Agent Memory

Support:

- **Conversation Context** — messages relevant to the current conversation.
- **Persistent Memory** — information deliberately stored for future interactions, e.g. "User prefers Java for programming examples."

Memory must be viewable, editable, and deletable. Do not silently store arbitrary sensitive information.

---

## 40. Web Search

Add a web-search capability, kept separate from browser automation:

```text
Search
   ↓
Find information
   ↓
Gemini
   ↓
Answer
```

Browser automation is for interacting with websites. Search is for retrieving information.

---

## 41. Browser Agent

Allow an agent to perform multi-step browser tasks, e.g.:

> Check my GitHub notifications and summarize anything requiring my attention.

Possible execution:

```text
Open GitHub
     ↓
Authenticate
     ↓
Read notifications
     ↓
Identify relevant items
     ↓
Extract information
     ↓
Summarize
```

Prefer a direct API where an appropriate API exists. Use browser automation when an API is unavailable and the automation is permitted.

---

## 42. Chat + Agent Integration

Chat and agents must feel like one product, e.g.:

```text
User:
I keep forgetting to check GitHub every morning.

AI:
I can create an agent that checks it automatically.

[Create Agent]
```

The button should open Agent Builder with the relevant information pre-filled.

---

## 43. Notifications

Send Android notifications for important agent events, e.g.:

```text
GitHub Daily Digest completed.

3 issues need your attention.
2 pull requests were updated.
```

```text
GitHub Agent needs approval.

The agent wants to create an issue.
```

Users must control notification preferences.

---

## 44. Activity Screen

Show recent agent executions, e.g.:

```text
Today

GitHub Daily Digest
Completed
9:00 AM

Email Summary
Completed
9:05 AM

Website Monitor
Failed
10:12 AM
```

Opening an execution should show the full execution details.

---

## 45. Agent Details Screen

Example:

```text
GitHub Daily Digest

Status:
Active

Goal:
Monitor GitHub activity.

Schedule:
Every weekday at 9:00 AM.

Tools:
GitHub Read

Last run:
Today 9:00 AM

Next run:
Tomorrow 9:00 AM

[Run Now]
[Pause]
[Edit]
```

---

## 46. Security

Never put these in the Android APK:

- Gemini API keys
- Database credentials
- OAuth client secrets
- Service account credentials
- Internal API keys

```text
Android
   ↓
Authenticated Backend
   ↓
Secrets / External APIs
```

Implement: authentication, authorization, per-user data isolation, secure OAuth, encrypted credentials, rate limiting, input validation, tool permission checks, audit logging, agent execution limits.

---

## 47. Agent Safety

Before a tool is executed:

```text
Gemini requests tool
       ↓
Is tool available?
       ↓
Does agent have permission?
       ↓
Does user have authorization?
       ↓
Does action require approval?
       ↓
Execute
```

If permission is missing:

```text
GitHub permission required.

[Connect GitHub]
```

If approval is required:

```text
Approval required.

[Approve]
[Reject]
```

---

## 48. Backend Architecture

Use a modular backend:

```text
Android App
      |
      v
API Gateway
      |
      +------------------+
      |                  |
      v                  v
Chat Service        Agent Service
      |                  |
      v                  v
Gemini Gateway      Agent Runtime
                         |
              +----------+----------+
              |          |          |
              v          v          v
           Tools     Scheduler   Approvals
              |
       +------+------+------+------+
       |      |      |      |      |
       v      v      v      v      v
    GitHub  Gmail   Slack  Notion  Web
```

---

## 49. Suggested Practical Stack

A practical first production architecture:

```text
ANDROID
Kotlin
Jetpack Compose
Material 3
Retrofit
Coroutines
ViewModel

             |
             | HTTPS / SSE
             v

BACKEND
TypeScript or Kotlin
REST API
SSE
Authentication
Agent Runtime
Tool Runtime

             |
      +------+------+
      |             |
      v             v

POSTGRESQL       GEMINI API
                 Gemini Models

      |
      +-------------------------------+
      |               |               |
      v               v               v
  Scheduler       Integrations    Browser
      |               |               |
      v               v               v
 Trigger.dev /    n8n / APIs      Browserbase /
 Inngest          OAuth           Stagehand
```

This is a starting architecture — do not adopt every listed service automatically.

---

## 50. MVP — Phase 1: ChatGPT Core

Do not build every feature simultaneously. Phase 1 implements:

- Android application
- Authentication
- Chat screen
- Gemini integration
- Streaming
- Conversations
- Markdown
- Code rendering
- New chat
- Conversation history

Milestone:

```text
Login
   ↓
Chat
   ↓
Gemini
   ↓
Streaming response
   ↓
Persistent conversation
```

---

## 51. Phase 2 — Files and Multimodal

Add: image uploads, PDF/document uploads, file processing, multimodal Gemini interactions, voice input.

---

## 52. Phase 3 — Agent Framework

Add: Agent Builder, agent configuration, tool abstraction, Gemini function calling, tool permission system, execution engine, Run Now, execution history.

---

## 53. Phase 4 — Integrations

Start with: GitHub, Gmail, Google Calendar, Slack, Notion.

Then add: X / Twitter, WhatsApp (through supported APIs/providers), Google Docs, Google Drive, LeetCode (where permitted), additional developer tools.

---

## 54. Phase 5 — Automation

Add: recurring schedules, background workers, notifications, approval checkpoints, retry handling, execution monitoring.

---

## 55. Phase 6 — Browser Agents

Add: Browserbase, Stagehand, Cloudflare Browser Run as an alternative, Playwright for deterministic workflows, browser session persistence where appropriate, browser task history.

---

## 56. Phase 7 — Advanced Agent System

Add: persistent memory, multi-step planning, multiple agents, agent-to-agent delegation, advanced tool permissions, long-running jobs, human-in-the-loop workflows, usage limits, advanced execution observability.

---

## 57. Example Agent Use Cases

**GitHub:**
> Every morning check my GitHub notifications and tell me which issues or pull requests need my attention.

**LeetCode:**
> Every Sunday analyze my LeetCode activity and create a study plan for the next week.

**Gmail:**
> Every weekday summarize important emails from the previous day.

**Slack:**
> At 6 PM summarize the important conversations I missed today.

**X / Twitter:**
> Every morning find relevant posts about Android development and give me a summary.

Capabilities depend on the available API access.

**WhatsApp:**
> Send my daily report to my WhatsApp.

Use an approved WhatsApp Business/API integration.

**Browser:**
> Check the permitted web application, inspect my dashboard, and summarize anything that changed.

Use browser automation only where access and automation are permitted.

---

## 58. Example Multi-Tool Agent

The finished system must support agents combining multiple tools, e.g.:

> Every Monday morning, check my GitHub activity, Google Calendar, Gmail, and Slack and create a weekly work briefing.

Execution:

```text
Agent
 |
 +---- GitHub
 |       |
 |       +---- Issues
 |       +---- Pull Requests
 |
 +---- Gmail
 |       |
 |       +---- Important emails
 |
 +---- Slack
 |       |
 |       +---- Relevant messages
 |
 +---- Calendar
         |
         +---- Upcoming meetings

              ↓

            Gemini

              ↓

       Weekly briefing

              ↓

         Android notification
```

---

## 59. Agent Configuration UX

Do not force users to manually construct complicated workflows. Preferred interaction:

```text
Natural language
       ↓
AI understands goal
       ↓
AI identifies tools
       ↓
AI asks missing questions
       ↓
AI generates configuration
       ↓
User reviews
       ↓
User approves
       ↓
Agent activated
```

The visual configuration editor should still exist for advanced users.

---

## 60. Product Design Principle

The application's core mental model:

> Tell the AI what you want done.

**Normal request:**

```text
Tell AI
   ↓
AI answers
```

**Action:**

```text
Tell AI
   ↓
AI uses tools
   ↓
Action happens
```

**Automation:**

```text
Tell AI
   ↓
AI creates agent
   ↓
User approves
   ↓
Agent runs automatically
```

This makes the agent system feel like an extension of ChatGPT.

---

## 61. Development Rules

The implementation must:

- Use real backend APIs.
- Use Gemini instead of the OpenAI API.
- Never hard-code API keys.
- Never use fake AI responses in production flows.
- Keep integrations modular.
- Keep tool permissions explicit.
- Keep agent execution server-side.
- Keep scheduled execution independent of the Android app.
- Prefer official APIs when available.
- Prefer managed infrastructure where it meaningfully reduces implementation complexity.
- Use browser automation only when appropriate and permitted.
- Avoid building browser infrastructure from scratch when a managed option is suitable.
- Avoid implementing hundreds of integrations manually when an integration platform can safely provide them.
- Keep provider-specific code behind interfaces.
- Make failures observable and recoverable.
- Keep the Android client independent from the internal implementation of tools and integrations.

---

## 62. Final Product

The final application is a **native Android ChatGPT-style AI assistant combined with an autonomous agent platform**.

At the surface:

```text
ChatGPT-like Android app
```

Underneath:

```text
Gemini
+
Tool Calling
+
GitHub
+
LeetCode
+
X / Twitter
+
WhatsApp
+
Gmail
+
Slack
+
Notion
+
Google services
+
Web Search
+
Browser Automation
+
Agent Runtime
+
Scheduling
+
Memory
+
Human Approval
```

A user should be able to start with a simple conversation and gradually turn the AI into an agent capable of interacting with the services they authorize.

The architecture must make it possible to add new integrations, Gemini models, tools, browser capabilities, and automation types without redesigning the Android application.

**The main objective is not to reproduce every ChatGPT feature immediately.** Build a strong ChatGPT-style Android foundation first, then progressively add the agent runtime and integrations on top of it, following Phases 1–7 above in order.
