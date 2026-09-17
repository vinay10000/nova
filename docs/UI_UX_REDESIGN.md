# Nova UI / UX redesign

## Status

Draft implementation of the shared design system and adaptive navigation. Not a completed screen-by-screen redesign. No emulator, screenshot review, build, lint, or test execution was available in the editing environment. Do not merge until the checks below pass.

## Design rationale

Nova already has an identifiable bronze / warm-paper / AMOLED visual language. This change preserves that identity instead of introducing a generic gradient-heavy theme. It separates editorial display typography from functional text, increases surface differentiation, and makes the main product destinations visible without opening the chat drawer.

| Area | Implemented change |
| --- | --- |
| Compact navigation | Labelled Chat, Agents, Activity, and Connections destinations below 600dp |
| Expanded navigation | A 112dp navigation rail at 600dp and above |
| Keyboard focus | Hide compact navigation while the IME is visible |
| Secondary destinations | Settings and conversation history remain focused screens without primary navigation |
| Back stack | Primary destination selection returns to the chat root before switching, with single-top and saved-state navigation |
| Typography | Serif display and headlines; sans-serif titles, labels, and body text; explicit sizes and line heights |
| Color | Explicit Material semantic pairs in both themes and all five accent choices |
| Contrast | Stronger control outlines and muted text; separate decorative edge colors |
| Surfaces | More opaque glass panels and more distinct dark raised surfaces |
| Components | A consistent six-to-28dp corner scale |
| Regression coverage | Eight instrumentation tests for theme readability, navigation selection, adaptive layout, keyboard visibility, secondary screens, and back-stack behavior |

## Compatibility and behavior impact

No backend, API, database, signing, model, or permission changes. No migration is required. The existing NovaTheme, NovaNav, GlassPanel, palette, font, and gutter interfaces are retained. The session-wide ChatViewModel remains shared across destinations.

The intentional behavior change is visible top-level navigation. Selecting a primary destination clears secondary entries above Chat while saving supported navigation state. Ordinary remember state is not guaranteed to survive destination changes; verify agent draft behavior manually. The direct Settings-to-Connections visit retains its existing Back-to-Settings behavior until another primary destination is selected.

Theme changes affect all screens using MaterialTheme and NovaPalette, but hard-coded screen styling remains unchanged. The new navigation consumes vertical space on compact screens when the keyboard is closed. Existing screen-level back controls and drawer links are retained for compatibility.

## Verification required

Use the repository's Gradle wrapper and dependency versions. Install the compile SDK required by android/app/build.gradle.kts (currently API 37), a compatible JDK (21 recommended for the build tooling; app bytecode remains Java 17), and an Android emulator. SDK/dependency availability has not been verified here.

The existing debug signing configuration expects android/debug.keystore. If absent, create a local development key; do not commit it:

```bash
cd android
if [ ! -f debug.keystore ]; then
  keytool -genkeypair -keystore debug.keystore -storepass android \
    -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 \
    -validity 365 -dname 'CN=Android Debug,O=Android,C=US'
fi
bash ./gradlew :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug :app:testDebugUnitTest --stacktrace
# With an emulator booted or a development device attached:
bash ./gradlew :app:connectedDebugAndroidTest --stacktrace
```

The first two tests characterize existing theme readability and the Navigation Compose chat-root/back contract. They do not constitute an end-to-end characterization of the authenticated app. Remaining tests exercise the new presentation seam without a backend or user credentials. Tests were committed before production changes but were not executed against either revision.

The attempted .github/workflows/android-ui-checks.yml write failed. A branch comparison confirmed that no workflow was added. CI automation is therefore NOT included in this PR.

## Manual acceptance matrix

| Scenario | Acceptance criterion | Status |
| --- | --- | --- |
| Compact phone, 320–599dp | Navigation labels remain readable; content and composer are reachable | Pending |
| Tablet / foldable, 600dp+ | Rail appears; no bottom bar or content overlap | Pending |
| Font scale 1.0, 1.3, 2.0 | Labels, headings, and controls remain usable without clipping | Pending |
| Light and dark, each accent | No unexpected default Material colors; readable content and controls | Pending |
| TalkBack | Navigation label and selected state are announced without duplicate icon announcements | Pending |
| Keyboard opening / closing | Compact bar hides and returns; composer remains above keyboard | Pending |
| Chat streaming during navigation | Returning to Chat keeps the existing conversation and response | Pending |
| Agents and Activity | Existing run, approval, loading, error, and back actions still work | Pending |
| Connections via Settings | Back returns to Settings; OAuth/token flows are unchanged | Pending |
| History | Opening a thread returns to Chat with the requested conversation | Pending |
| Rotation / process recreation | Review state restoration and unsaved agent drafts | Pending |
| Authentication | Login and logout remain functional and unauthenticated users see no navigation | Pending |

## Remaining redesign work

The large Screens.kt response could not be fully inspected through the available connector, so its screen implementations were not edited. A subsequent screen-level pass should inspect the complete file and real device captures before redesigning chat empty states, composer interactions, agent creation, activity timelines, connection onboarding, and settings grouping. Do not describe this PR as completing those flows.

## Rollback

Revert this PR as a unit. It has no server or storage migration. Removing the test dependency additions also removes the newly introduced instrumentation setup.
