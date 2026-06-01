# Unnamed

A personalized, AI-assisted training & nutrition tracker for Android. Core idea:
**logging is a conversation, not a form** — you speak/type what you did, the app
parses it into structured data, you confirm an editable card, and it's saved.

See [`docs/PRODUCT_SPEC.md`](docs/PRODUCT_SPEC.md) for the full product &
architecture spec.

## Project layout

```
app/                      Android app (Kotlin + Jetpack Compose)
  src/main/assets/        bundled exercise_library.json (offline-first)
  src/main/java/com/unnamed/app/
    data/exercise/        canonical exercise library loader + models
    data/local/           Room database (source of truth, offline-first)
    data/remote/          Supabase client (sync layer — wired, not yet syncing)
    data/repository/      offline-first data access
    logging/              SetParser — local stub for the AI parser
    ui/                   Compose screens (Log chat, History, Coach), theme, nav
data/                     curated exercise library + build pipeline
supabase/                 backend: SQL migrations, RLS, AI edge-function scaffold
docs/                     product & architecture spec
```

## Build

Requires **Android Studio** (it supplies the Android SDK). The repo includes the
Gradle wrapper (Gradle 8.14.3, AGP 8.9.1, Kotlin 2.1.0).

1. Open the project root in Android Studio and let it sync (downloads the
   Android SDK + dependencies).
2. Run on an emulator or device (minSdk 26 / Android 8.0+).

> The first Gradle sync needs internet to fetch the Android Gradle Plugin and
> libraries. The app itself works offline once installed (local Room DB).

## Status (v0.1 scaffold)

Implemented as a runnable skeleton:
- Chat-style logging UI with **confirm-before-commit** cards.
- On-device **voice input** (Android `SpeechRecognizer`) → editable text.
- Offline **Room** persistence of sessions / sets / notes.
- Bundled **883-exercise** library with alias matching.
- A local heuristic `SetParser` standing in for the future Claude-backed parser.

Not yet wired (by design): Supabase sync, the AI coach (the
`supabase/functions/ai-coach` edge function stays inert until the Anthropic key
is activated), nutrition, and analytics. See the spec's phased roadmap.
