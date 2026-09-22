# ChatGPT Android App — UI Reconstruction Spec
**Source:** `screenrecording.mp4` (384×850 portrait, 3 min 2 s, 60 fps, dark mode)
**Purpose:** Pixel-faithful reference for an AI agent to recreate every screen shown in the video.

**How to read this spec:**
- All pixel values are measured on the 384×850 recording canvas. The source phone was ~1080×2340 (×2.8125 scale); at 384 px wide, values are roughly equivalent to Android `dp`.
- `#HEX` colors were sampled programmatically from the frames (see Global Palette).
- Every screen has a ground-truth reference image in `frames/` named to match.
- The **status bar** (clock 17:12–17:15, screen-recorder pill: red dot + elapsed timer `00:01 → 03:00`, wifi/signal/battery 53–63 %) is Android system UI, NOT part of the app — but reproduce it if you want the video-accurate look.
- The small **floating gray dot** (~16 px, `#8B8B8B`) visible on many frames (e.g. 09, 12, 17, 22) is the screen-recorder's touch indicator — NOT part of the app.
- A thin vertical gray line on the **left screen edge** in chat screens is the Android edge-back gesture affordance.

---

## GLOBAL DESIGN SYSTEM

### Color Palette (measured)

| Token | Hex | Usage |
|---|---|---|
| `bg/chat` | `#000000` | Chat screen background (pure black) |
| `bg/surface` | `#202020` | Drawer bg, input pill, popup menus, context menu, search bars |
| `bg/row` | `#404040` | Settings row cards, plugin list rows, hamburger circle button |
| `bg/card` | `#000000` + 1 px `#2A2A2A` border | "Explore more news" card (black fill, subtle outline) |
| `bg/keyboard` | `#1C1C28` | GBoard dark background |
| `key/keyboard` | `#343844` | Keyboard keys |
| `accent/purple` | `#A270F0` | Send/voice/stop buttons, Chat button, selected accent dot, streaming dot |
| `bubble/user` | `#3A1D66` | User message bubbles (dark violet) |
| `btn/primary` | `#F0F0F0` (text `#000000`) | "Get India headlines" CTA pill |
| `btn/scroll` | `#303030` | Circular scroll-to-bottom button, white ↓ arrow |
| `text/primary` | `#FFFFFF` | Headings, message text, row labels |
| `text/secondary` | `#B3B3B3` | Body text, subtitles, helper text |
| `text/tertiary` | `#8E8E93` | Timestamps, bylines, placeholders |
| `link/blue` | `#8F9EC6` | "Upgrade plan" text (light blue) |
| `danger/red` | `#C24B59` | "Delete", "Log out" (recording renders slightly muted) |
| `dot/gray` | `#8B8B8B` | Radio unselected ring, drag dots |
| `tag/pill` | `#2E2E2E` | Source chips (e.g. "Reuters" chip) |

### Typography
- Family: OpenAI **Söhne** (fallback: Inter / Roboto). No serif anywhere.
- H1 (response titles, "Latest news — September 19, 2026"): 24 px **bold**, white.
- H2 (section, "Read the original reports", "Explore more news"): 20 px **bold**, white.
- Card title: 17 px **semibold** (600).
- Body: 16 px regular, line-height ≈ 26 px.
- Small/byline: 13–14 px, `#8E8E93`.
- Section labels ("My ChatGPT", "Account", "Characteristics"): 15 px, `#B3B3B3`.
- Row label: 16–17 px white; row subtitle 14 px `#B3B3B3`.

### Recurring Components
- **Nav circle button:** 44 px circle, `#404040` (on black) or slightly lighter than bg (on surfaces), white glyph. Used for: hamburger, compose (pencil-in-square), ⋮ dots, back arrow, temporary-chat (dashed circle), search.
- **Composer pill:** full-width minus 16 px margins, height ≈ 64 px, radius 32 px (full pill), `#202020`. Left: `+` icon (24 px). Center: placeholder text 16 px `#8E8E93` (states: "Ask ChatGPT" → "Reply to ChatGPT" → "@"). Right: mic icon (24 px white) + accent button (36 px circle `#A270F0`; icon varies: waveform bars = voice idle, ↑ = send ready, ■ = stop while streaming).
- **Action icon row** (below every assistant message): copy, thumbs-up, thumbs-down, speaker, share, ⋮ — 24 px glyphs, `#B3B3B3`, spacing ≈ 40 px, left-aligned.
- **Grouped settings list:** each row is its own 56–64 px card `#404040`, radius 12 px, 6 px gap between rows, icon 24 px left, label 16 px white, optional subtitle 14 px `#B3B3B3`, optional right chevron ›.
- **GBoard dark:** toolbar row (grid, sticker, GIF, gear, translate, palette, mic), QWERTY with number super-hints, `?123` / `,` / emoji / space / `.` / enter; enter key varies (arrow-return on chat, magnifier on search).

---

## SCREEN INVENTORY (chronological)

| # | Time (video) | Screen | Trigger / Navigation |
|---|---|---|---|
| 01 | 0:00–0:02 | Android app drawer | Video start |
| 02 | 0:02–0:07 | ChatGPT new chat — empty | Tap ChatGPT icon |
| 03 | 0:10–0:13 | Attachment popup menu | Tap `+` in composer |
| 04 | 0:13–0:15 | "Add files" bottom sheet | Tap "Files" |
| 05 | 0:16–0:19 | `@` Plugins tools picker | Dismiss sheet, type `@` |
| 06 | 0:19–0:22 | "Sketch" installed-plugins list | Scroll inside @ panel |
| 07 | 0:22–0:25 | File-type mention list (PDF…) | Continue scroll |
| 08 | 0:31–0:40 | Greeting exchange | Send "Hi" |
| 09 | 0:46 | Message sent + streaming dots | Send "Search the web for latest news" |
| 10 | 0:49–0:58 | News response + headline cards | Streaming completes |
| 11 | 1:01–1:13 | Explore more news + category radios | Scroll down in response |
| 12 | 1:10–1:16 | Sources / YouTube links | Scroll further |
| 13 | 1:19–1:25 | India search + "Searching…" status | Send India prompt |
| 14 | 1:25–1:31 | "India: Major news headlines" + image cards | Streaming completes |
| 15 | 1:31 | "Read the original reports" list | Scroll down |
| 16 | 1:34 | Screen-recorder overlay (system UI) | — |
| 17 | 1:36 | Message context menu | Long-press assistant message |
| 18 | 1:42–1:48 | Side drawer | Tap hamburger |
| 19 | 1:54 | Search chats, files, projects | Tap drawer search icon |
| 20 | 2:00 | Settings — Profile | JS avatar → settings |
| 21 | 2:03–2:06 | Settings — General list / Account | Scroll settings |
| 22 | 2:09 | Settings — Plugins page | Tap "Plugins" |
| 23 | 2:12 | Plugins — Added list | Loaded page |
| 24 | 2:21–2:27 | Plugins marketplace | Tap "Browse plugins" |
| 25 | 2:24 | Marketplace — Productivity section | Scroll |
| 26 | 2:33 | Personalization | Back → Personalization |
| 27 | 2:45 | Theme dropdown (System/Light/Dark) | Expand Appearance |
| 28 | 2:48 | Accent color dropdown | Expand "Accent color" |
| 29 | 2:57 | Settings bottom + Log out | Scroll settings |
| 30 | 3:00 | Final: drawer + recorder overlay | Video end |

---

# DETAILED SCREEN SPECS

## S01 — Android App Drawer `frames/01-android-app-drawer.jpg` (0:00–0:02)
Blurred colorful wallpaper background; dark scrim.
- **Top:** clock `17:12` left; recorder pill (red dot + `00:02` in black rounded pill) center; wifi/signal/battery right.
- **Tabs:** centered pill container `#3A3A3A` w/ 2 segments: **Personal** (active: white pill, black text) | Work (inactive: gray text).
- **App grid:** 4 columns × n rows, icon 56 px circle + 12 px white label beneath, row spacing ≈ 84 px. Row-by-row (L→R):
  1. Amazon, Among Us, Apple TV, Arrows (top row partially cut by tabs)
  2. Arrow Puzzle, Asphalt Xtreme, Atomic Chat, Authenticator
  3. Authenticator, bigbasket, BHIM, Bistro
  4. Blinkit, BookMyShow, Brave, Brawl Stars
  5. Calculator, Calendar, Camera, Canara ai1
  6. **ChatGPT** (black circle, white OpenAI knot logo — the launch icon), Chrome, Claude, Clock
  7. Compass, Contacts, Contacts, CRED
- **Right edge:** A–Z index scrollbar (tiny white letters A→Z).
- **Bottom:** search bar — 48 px pill `#2A2A2A`, magnifier icon + "Search" placeholder, above white gesture bar.

## S02 — ChatGPT New Chat (empty) `frames/02-new-chat-empty.jpg` (0:02–0:07)
Background pure `#000000`.
- **Top-left:** 44 px circle `#404040`, hamburger ≡ glyph. **Top-right:** 44 px circle, dashed-circle "temporary chat" glyph.
- **Left edge:** thin vertical gray line (back-gesture affordance), ~200 px tall from top.
- **Composer** (bottom, above keyboard): pill `#202020`, radius 32, h ≈ 64: `+` icon left; "Ask ChatGPT" placeholder `#8E8E93`; mic icon; **voice button** 36 px circle `#A270F0` with 3 white waveform bars.
- **Keyboard (GBoard dark):** bg `#1C1C28`; toolbar: grid ▦, sticker, GIF, gear ⚙, translate, palette 🎨, mic; QWERTY keys `#343844`, white letters, number super-hints on top row; bottom row: `?123`, `,`, 😊, space, `.`, return ↵; leftmost bottom: ˅ chevron collapse key.
- A gray dot (recorder cursor) floats just above composer right side.

## S03 — Attachment Popup `frames/03-attachment-menu.jpg` (0:10–0:13)
Composer pill visible behind on right (mic + purple voice button). Popup:
- **Panel:** rounded-rect radius ≈ 24, `#202020`, soft shadow, anchored bottom-left above composer, width ≈ 280 px, left margin 16.
- **Items** (row h ≈ 56, icon 32 px circle `#404040` + 16 px white label, left padding 16):
  1. 📷 Camera
  2. 🖼 Photos
  3. 📎 Files
  4. **@ Plugins** (at-glyph in circle)
  5. 🧠 Think harder (brain glyph)
- Keyboard visible below composer.

## S04 — "Add files" Bottom Sheet `frames/04-add-files-sheet.jpg` (0:13–0:15)
Full screen dims to black; sheet rises from bottom, bg `#000000`.
- **Header:** 44 px circle ✕ (left) · "Add files" 17 px bold white centered · 44 px circle ⋮ (right).
- **Row:** ⬆ upload icon + "Upload files" 16 px white, h ≈ 56.
- **Divider** 1 px `#2A2A2A`.
- **"Recent"** 15 px `#B3B3B3`.
- **Grid:** two rounded-rect thumbnails 165×165, radius 16, fill `#1C1C1C` (empty placeholders).
- White gesture bar at bottom.

## S05 — `@` Plugins Tools Picker `frames/05-plugins-picker.jpg` (0:16–0:19)
Composer now contains text `@` (white, cursor after). Send button active: 36 px circle `#A270F0`, **↑** arrow.
- **Panel above composer:** radius 24, `#202020`, header "**Plugins**" 15 px `#B3B3B3`, then rows (h ≈ 56, leading glyph 24 px):
  1. **Create image** — gradient (blue→green) image icon; row highlighted `#2E2E2E` rounded 12
  2. 💡 Thinking
  3. ⚙ Deep research
  4. ☑ Create task (checklist glyph)
  5. 📖 Study (open-book glyph)
- Keyboard visible.

## S06 — "Sketch" Installed-Plugins Panel `frames/06-sketch-plugin-list.jpg` (0:19–0:22)
Same panel geometry as S05; header row: pen nib icon + "**Sketch**" 17 px white. Items (24 px leading icons/emojis):
1. 🤗 Hugging Face
2. Lovable (gradient pink-purple heart)
3. Floot (white "F" glyph)
4. GitHub (octocat silhouette)
5. Template Creator (2×2 grid of small squares)
Composer: `@` typed; mic; purple **↑** send. Gray recorder dot near panel's left edge.

## S07 — File-Type Mention List `frames/07-filetype-mention-list.jpg` (0:22–0:25)
Panel scrolled (top item "Presentations" cut off). Rows (h ≈ 56, app-style icons 28 px):
1. PDF (red document icon "PDF")
2. Documents (blue doc)
3. Spreadsheets (green grid)
4. ⚙ OpenAI Platform (gray gear)
5. Visualize (pink scatter-plot glyph)
Composer `@` + cursor; purple **↑** send button; keyboard.

## S08 — Greeting Exchange `frames/08-greeting-exchange.jpg` (0:31–0:40)
- **Top bar change:** left hamburger circle; right side TWO 44 px circles: ✏ compose (pencil-square) + ⋮ dots.
- **User message:** right-aligned pill bubble `#3A1D66`, text "Hi" 16 px white, padding ≈ 12×8, radius 22. Position: top-right below nav.
- **Assistant message:** left-aligned white text "Hi! How can I help you today?" 16 px.
- **Action icon row** under it (copy, 👍, 👎, 🔊, share-nodes, ⋮), 24 px `#B3B3B3`.
- **Composer:** "Reply to ChatGPT" placeholder (text cursor visible) + mic + `#A270F0` waveform button.
- Keyboard rising from bottom (QWERTY uppercase transition visible).

## S09 — Message Sent + Streaming `frames/09-message-sent-streaming.jpg` (0:46)
- History: "Hi" bubble right; greeting text + icon row above (scrolled up).
- **User bubble (new):** right-aligned `#3A1D66` pill, radius 22, text "Search the web for latest news" 16 px white.
- **Left streaming dot:** 12 px circle `#A270F0` at left margin (assistant thinking indicator).
- Recorder dot (gray) at right mid-screen.
- **Composer:** accent button now shows **stop ■** glyph (white square in `#A270F0` circle).

## S10 — News Response + Headline Cards `frames/10-11-12` (0:49–0:58)
Streaming text first appears as "Latest news — September 19, 2026" then completes.
- **H1:** "Latest news — September 19, 2026" 24 px bold white.
- **Body:** "What kind of news are you interested in? I can cover major headlines, India, world affairs, technology, AI, business, or sports." 16 px `#B3B3B3`.
- **"Top headlines"** 20 px bold.
- **News card anatomy** (vertical list, spacing 24):
  - Left thumbnail 100×100 radius 12 (or full-bleed image for wide cards)
  - Right column: **category pill** (11 px bold, colored text on tinted bg — "AI Business" blue `#3B82F6`, "Andhra Pradesh" green `#22C55E`, "World" red `#EF4444`), **title** 17 px bold white (2–3 lines), **body** 15 px `#B3B3B3` (2–5 lines), **source chip** (dark `#2E2E2E` pill, favicon 14 px + name 12 px, e.g. "Reuters", "Sky News"), **byline** "Reuters · September 19" 13 px `#8E8E93`.
- **Card contents shown:**
  1. Claude logo thumbnail (white bg) — "AI Business" — "Anthropic weighs a new AI model ahead of a potential IPO" — "Reuters reports that Anthropic is considering a model launch amid competition with OpenAI and questions about growth and profitability." — Reuters chip — "Reuters · September 19".
  2. Dark thumb — "Andhra Pradesh" — "Andhra Pradesh approves ₹22,178 crore in projects" — "The state approved 28 industrial projects, with projected employment of 19,739 people. Focus areas include semiconductors,…" — "Associated Press · September 19" — AP News chip.
  3. Trading-floor thumb — "Indian IT shares rally amid global AI debate" — "Indian IT stocks rose on September 15 after calls for caution in AI development. The Nifty IT index had experienced a substantial decline earlier in 2026." — Reuters chip — "Reuters · September 15".
  4. Fighter-jet thumb — "World" — "Poland conducts military aviation operations amid Russian strikes" — "Poland reported precautionary operations in response to attacks on Ukraine. Its military later said the operations had concluded without a reported airspace violation." — chip "as Russia repels massive…" — "Sky News · September 19".

## S11 — Explore More News + Category Radios `frames/13-explore-more-news.jpg` (1:01–1:08)
- **H2:** "Explore more news" 20 px bold white.
- **Card:** full-width, black bg, 1 px `#2A2A2A` border, radius 16, padding 16:
  - "**Choose a category**" 17 px bold white.
  - **Radio list** (row h ≈ 36): India ⦿ (selected: 20 px ring, white outer + filled white center dot), then World, AI & Technology, Business & Markets, Science, Sports, Entertainment (unselected: 20 px ring `#8E8E93`, labels 16 px white).
  - **CTA:** full-width pill h ≈ 44, `#F0F0F0`, black 15 px semibold label — **"Get India headlines"** (becomes "Get Science headlines" when Science selected).
- **Disclaimer** 15 px `#8E8E93`: "These are selected reports from September 19, 2026, not a comprehensive live news feed. Details may change as stories develop."
- **Action icon row** (same as S08).
- **Sources row:** 3 overlapping 20 px favicon circles (Sky News red, YouTube red play, white "W") + "Sources" 13 px white.
- **Scroll-to-bottom button:** 44 px circle `#303030` white ↓ centered above composer.

## S12 — Sources / YouTube Links `frames/14-sources-youtube.jpg` (1:10–1:16)
- Radios: **Science** now selected (dot filled `#B3B3B3`), India unselected; labels for non-selected items render dimmer `#B3B3B3`.
- **"Sources"** 15 px `#B3B3B3` section label, then **link rows** (divider 1 px `#2A2A2A` between):
  1. favicon "sky" (white text) — title 16 px semibold white "Ukraine war latest: Poland launches jets - as Russia repels massive …" — desc 14 px `#8E8E93` "Russian attacks on Ukraine overnight have prompted Poland to launch precautinary …" — "Yesterday" 13 px.
  2. YouTube icon (red rounded play) + "youtube" — "Malayalam News Live | HD 24x7 Live Streaming | 24 News - YouTube" — "Live Streaming for Malayalam Live News, Updates, Breaking News, Political News a…" — "February 18, 2024".
  3. Another YouTube row begins (cut off).

## S13 — India Search + "Searching…" Status `frames/15-searching-status.jpg` (1:19–1:25)
- **User bubble:** multi-line right pill `#3A1D66` radius 24, max-width ≈ 75 %, text: "Search the latest news as of September 19, 2026, about India. Give me the major headlines with sources."
- **Search status row** (left-aligned): 🌐 globe icon 20 px `#B3B3B3` + 15 px `#B3B3B3` text "Searching India latest news September 19 2026 major headlines India" (wraps to 2 lines).
- Scroll-to-bottom circle button; composer w/ stop ■ button.

## S14 — "India: Major News Headlines" + Image Cards `frames/16-17` (1:25–1:31)
- **H1:** "India: Major news headlines" 24 px bold.
- **Byline:** "Saturday, September 19, 2026 · India" 13 px `#8E8E93` (location part may render lighter).
- **Intro:** "Here are the major India-related developments reported today, covering international relations, domestic politics, weather, and cricket." 16 px `#B3B3B3`.
- **Image news cards** (same anatomy as S10 but real photos):
  1. Photo (protesters, Indian flag on mountain) 100×100 r12 — pill "Regional affairs" `#2E2E2E` gray — "**Ladakh groups call off week-long protest march**" — "Political groups in Ladakh have replaced a proposed week-long march with a symbolic one-day march planned for September 23, citing weather and changing political circumstances." — "The Times of India · September 19" — chip: TOI logo + "The Times of India".
  2. Photo (storm clouds over city) — pill "Weather" blue — "**Heavy rain and thunderstorms forecast across several states**" — "Weather reports cite India Meteorological Department warnings for heavy rainfall, thunderstorms and strong…" (continues below fold).

## S15 — "Read the Original Reports" `frames/18-read-original-reports.jpg` (1:31)
- **H2:** "Read the original reports" 20 px bold.
- **Card** (black bg, 1 px `#2A2A2A` border, r16) with 3 rows divided by 1 px lines. Row: leading logo 32 px + title 16 px bold white + subtitle 14 px `#B3B3B3` + right **"Read ↗"** pill (outlined 1 px `#3A3A3A`, radius 16, h 32, 13 px white):
  1. Reuters (orange dotted circle) — "US sanctions, Russian oil and India's energy security"
  2. The Indian Express (red slanted bars) — "September 19 daily news briefing"
  3. The Times of India (red circle "TOI") — "Latest India news and regional updates"
- **Note:** 15 px `#8E8E93`: "Note: This is a selection of reports available from the search, not a complete national news roundup. Some stories concern developments from earlier in the week."
- **Closing:** 16 px white, bold segment "India's latest AI and technology news": "I can also provide a separate roundup of **India's latest AI and technology news**, including startups, government initiatives and major company announcements."
- Action icon row; Sources row: 3 favicons + "Sources".

## S16 — Screen-Recorder Overlay `frames/19-voice-recording-overlay.jpg` (1:34)
Android system UI (not the app) — dark `#1C1C1C` rounded panel top-left, w ≈ 350:
- Row 1: 🔴 red dot + "Recording…" 15 px white · timer "01:34" right 15 px.
- Row 2 (36 px circles): speaker (white bg, black glyph), mic-crossed (gray), ⏸ pause (gray), ⏹ stop (red `#E5484D`, white square).
- Status bar here shows network speed "1.10 MB/s" + battery 63 %.

## S17 — Message Context Menu `frames/20-message-context-menu.jpg` (1:36)
Long-press on assistant message ("Greeting exchange" chat) opens popup anchored top-right:
- **Panel:** radius 20, `#202020`, shadow; width ≈ 250; padding 8.
- **Header:** "Greeting exchange" 15 px `#8E8E93` (chat title).
- **Items** (h ≈ 48, icon 20 px + label 16 px white): Share · Pin · Add to project ›(chevron) · Uploaded files · Find in chat · Add to home · Archive · **Delete** (red `#C24B59`, trash icon).

## S18 — Side Drawer `frames/21-22` (1:42–1:51)
Drawer slides from left, width ≈ 310/384 (81 %), bg `#202020`, right edge shadow over black chat. Left-edge drag line persists.
- **Header:** "ChatGPT" 24 px bold white + 44 px circle 🔍 search right.
- **Menu items** (h ≈ 52, icon 24 px `#B3B3B3`, label 16 px white): 🖼 Images · 📚 Library · 📁 Projects · 🕐 Scheduled · @ Plugins.
- **Divider** 1 px `#3A3A3A`.
- **Chat history** (14 px–16 px white rows, h ≈ 42, no icons, ellipsis-free single lines): Greeting exchange · Vercel MCP Configuration · Anime List Without Fanservice · Sliding Window Maximum Sum · Explain Changes · Assess Website Legitimacy · Fix Ollama Syntax Error · OpenAI Platform Search · Zeta Mittag Leffler Discovery · Build Bee Anatomy Website · Build sales dashboard · DNS troubleshooting steps · Create and delete file · ECNR Proof Documents · Type D Memory (last rows fade under bottom bar).
- **Bottom bar (floating over drawer):** **"Chat"** pill (h 52, radius 26, `#A270F0`, white ✏ + "Chat" 16 px) left; **"JS" avatar** 44 px circle `#3A3A3A` white initials center-right; purple waveform mic button right (partially on chat layer).

## S19 — Search Chats `frames/23-search-chats.jpg` (1:54)
- Full-screen black page (replaces drawer).
- **Centered empty state** (~35 % from top): 56 px rounded-square `#2A2A2A` with white 🔍; below: "Search chats, files, and projects" 16 px white.
- **Bottom search bar:** pill `#202020` h 52 w/ 🔍 + "Search" placeholder `#8E8E93` + separate 44 px circle ✕ right.
- Keyboard: bottom-right key = **magnifier** (search action).

## S20 — Settings: Profile `frames/24-settings-profile.jpg` (2:00)
Page bg `#000000`. 
- **Header:** 44 px circle ← back left; "John Smith" 17 px `#B3B3B3` centered.
- **Section label:** "My ChatGPT" 15 px `#B3B3B3`.
- **Group 1** (rows: 56 px cards `#404040` r12, 6 px gaps): 😊 Personalization · 📖 Memory · ⠿ Plugins (2×2 grid icon).
- **Section label:** "Account".
- **Group 2:** 💼 Workspace / subtitle "Personal" · ✦ **Upgrade plan** (blue `#8F9EC6` text, sparkle icon) · ⊞ Subscription / "Go" (+ gray drag-dot right) · 📊 Usage and limits · ⛑ Trusted contact · 👤♥ Parental controls · ✉ Email / "pikachuok5@gmail.com".

## S21 — Settings: General + Appearance `frames/25-settings-appearance-general.jpg` (2:03)
- **"Appearance ∨"** collapsed card `#404040` (sun icon) with nested row: 🖌 "Accent color" + purple dot + "Purple" + ∨ chevron.
- **Group:** ⚙ General · 🔔 Notifications · ⁄⁄ Voice (waveform icon) · 🛡 Safety (shield-check) · 🛡 Security and login (shield-gear) · 🖥 Remote control · 🗄 Storage · 🛢 Data controls · 📢 Ads controls · 🐞 Report bug.

## S22 — Settings: Account Detail `frames/26-settings-account.jpg` (2:06)
- "Account" label + full group: Workspace/"Personal" · ✦ Upgrade plan (blue) · Subscription/"Go" · Usage and limits · Trusted contact · Parental controls · ✉ Email / "pikachuok5@gmail.com" · 📞 Phone number / "+917416514679".
- Below: "Appearance ∨" card + "Accent color — Purple" row.

## S23 — Settings: Plugins Page `frames/27-settings-plugins.jpg` (2:09)
- **Header:** ← back · "**Plugins**" 17 px bold centered.
- **"Preferences"** label; card: "**Permissions** / Allow low-risk" + › chevron.
- **Loading spinner** (white arc, centered).
- **"Browse plugins"** button: full-width pill h 52 `#404040`, ⠿ grid icon + 16 px white label.

## S24 — Plugins: Added List `frames/28-plugins-added-list.jpg` (2:12)
- Header ← + "Plugins" centered. Label "Added" (partially visible top).
- Rows (h 56, cards `#404040` r12, 6 px gaps; leading app icon 32 px rounded-square; 16 px white labels):
  1. Default templates (multi-color squares)
  2. Documents (blue doc)
  3. Floot (dark "F")
  4. GitHub (octocat)
  5. Hugging Face (🤗 yellow emoji tile)
  6. Lovable (gradient heart)
  7. PDF (red "PDF" tile)
  8. Plugin Management (green + grid)
  9. Presentations (orange tile)
  10. Spreadsheets (green tile)
  11. Template Creator (2×2 squares)

## S25 — Plugins Marketplace `frames/29-30` (2:21–2:27)
- **Header:** ← · "**Plugins ∨**" (dropdown chevron after title) · ⚙ gear circle right.
- **Search bar:** pill `#202020` h 44, 🔍 "Search plugins" placeholder.
- **"Installed"** label + horizontal icon row (7 tiles, 40 px): Floot, Documents, GitHub, Hugging Face, Lovable, Default templates, PDF.
- **"Popular"** label; rows (icon 40 px, title 16 px white, subtitle 14 px `#B3B3B3`, right affordance):
  1. Gmail — "Read and manage Gmail" — 🔒 lock
  2. GitHub — "Triage PRs, issues, CI, and publish…" — ⋯
  3. Google Drive — "Drive, Docs, Sheets or Slides" — 🔒
  4. Google Calendar — "Manage Google Calendar events" — 🔒
  5. Notion — "Notion docs and workflows" — ＋
  6. Slack — "Read and manage Slack" — ＋
  7. cluster row: "See Outlook Email, Granola, and more" 15 px white
- **After scroll (S30):** Figma — "Create designs, ship to code" — ＋ · Slack (repeat) · "See Shopify, Wix, and more" w/ Shopify+Wix mini-icons.
- **"Productivity"** section: Granola (green spiral) — "Add your meeting context" — ＋ · Fireflies (pink F) — "Search meeting transcripts" — ＋ · Outlook Calendar — "Manage Outlook schedules" — ＋ · Plaud (white circle "A") — "Retrieve insights from Plaud" — ＋ · Otter.ai (black, white waveform) — "Search meetings from Otter.ai" — ＋ · Atlassian Rovo (Legacy) (blue peaks) — "Manage Jira and Confluence" — ＋.

## S26 — Personalization `frames/31-personalization.jpg` (2:33)
- **Header:** ← · "**Personalization**" 17 px bold centered · 44 px circle ✓ (checkmark, top-right).
- **Card 1:** "Base style and tone" 17 px white + "Default" 14 px `#B3B3B3` + ∨ chevron. Helper text below (15 px `#B3B3B3`, outside card): "This is the main voice and tone ChatGPT uses in your conversations. This doesn't impact ChatGPT's capabilities."
- **"Characteristics"** label.
- **Card 2:** "Fewer emoji" / "Don't use as many emoji".
- **Card 3:** "Add characteristics" (single-line, no subtitle). Helper: "Choose some additional customizations on top of your base style and tone."
- **"Custom instructions"** label.
- **Card 4 (text area, `#404040`, r16, padding 16, 15 px white, multi-line):**
  "DO NOT USE BUZZ WORDS,EMOJIS...... should not tell like this too "No hype, no buzzwords"....
  for DSA, LEETCODE QUESTIONS You have to always code in java, unless i specifically mention a language, ALSO"
- **Bottom:** "Advanced ∨" 15 px `#B3B3B3`.

## S27 — Theme Dropdown `frames/32-theme-dropdown.jpg` (2:45)
- "Appearance" card now **expanded** (chevron ^), revealing "Accent color — Purple" row beneath.
- **Dropdown popup** (dark `#1C1C1C`, radius 16, shadow, anchored right of the Accent row, overlapping list): items 15 px white, h 44: **System (Default)** · **Light** · **Dark**.

## S28 — Accent Color Dropdown `frames/33-accent-color-dropdown.jpg` (2:48)
- "Accent color" row expanded (chevron ^). **Popup** (same style, taller, w ≈ 210, right-anchored): rows h 44 with 16 px colored dot + label:
  🔵 Blue · ⚪ White · 🟢 Green · 🟡 Yellow · 🩷 Pink · 🟠 Orange · 🟣 **Purple ✓** (white checkmark right; currently active).

## S29 — Settings Bottom + Log Out `frames/34-settings-logout.jpg` (2:57)
- Rows continue: (General partially) · 🔔 Notifications · Voice · Safety · Security and login · Remote control · Storage · Data controls · Ads controls · Report bug · ℹ️ **About**.
- **"Log out"** card: full-width h 56 `#404040` r12, red `#C24B59` logout icon + red 16 px label.

## S30 — Final Frame `frames/35-final-drawer-recording.jpg` (3:00)
Composite state: side drawer (S18) open with recorder overlay (S16) showing timer 03:00; status bar "5.00 KB/s … 63 %". Drawer shows Library, Projects, Scheduled, Plugins + history + purple Chat pill + JS avatar.

---

## APPENDIX A — States & Micro-interactions Observed
1. **Composer button morphs:** waveform (voice idle) → ↑ (send ready when text present) → ■ (stop while streaming).
2. **Streaming:** assistant text renders progressively; left-edge 12 px purple dot pulses while waiting; images/cards pop in when loaded.
3. **Composer placeholder evolves:** "Ask ChatGPT" (new chat) → "Reply to ChatGPT" (after first response) → "@" (mention mode).
4. **Radio selection:** tapping a category moves the white dot; CTA label updates ("Get India headlines" → "Get Science headlines"); unselected rows dim.
5. **Menus** (attachment/plugins/dropdowns) appear anchored to their trigger with fade+scale; dismissed by tapping outside or ✕.
6. **Scroll-to-bottom** circular button floats centered above composer whenever chat is scrolled up.
7. **Drawer** overlays chat (no push); scrim dim on the right 19 %.
8. **Keyboard** (GBoard dark) slides with toolbar; return key = ↵ in chat, 🔍 in search.

## APPENDIX B — Ground-Truth Frame Index
All in `frames/` (same names as per-screen refs): 01-android-app-drawer, 02-new-chat-empty, 03-attachment-menu, 04-add-files-sheet, 05-plugins-picker, 06-sketch-plugin-list, 07-filetype-mention-list, 08-greeting-exchange, 09-message-sent-streaming, 10-news-response-start, 11-news-card-anthropic, 12-news-card-poland, 13-explore-more-news, 14-sources-youtube, 15-searching-status, 16-india-headlines-start, 17-india-news-cards, 18-read-original-reports, 19-voice-recording-overlay, 20-message-context-menu, 21-side-drawer, 22-drawer-scrolled, 23-search-chats, 24-settings-profile, 25-settings-appearance-general, 26-settings-account, 27-settings-plugins, 28-plugins-added-list, 29-plugins-marketplace, 30-marketplace-productivity, 31-personalization, 32-theme-dropdown, 33-accent-color-dropdown, 34-settings-logout, 35-final-drawer-recording.
