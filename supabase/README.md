# Supabase backend

The sync/backup + auth + (future) AI-proxy layer for Unnamed. The app is
offline-first (Room/SQLite is the source of truth); this is the cloud layer.

## Project

| | |
|---|---|
| Project name | `unnamed` |
| Project ref | `iwzsqxkfbqyddnalsbrb` |
| API URL | `https://iwzsqxkfbqyddnalsbrb.supabase.co` |
| Region | `eu-west-1` |
| Plan | Free ($0/mo) |
| Org | `patomk4's projects` (Vercel-managed) |

**Client config** (safe to embed — the publishable/anon key is public by design;
RLS protects all data):

```
SUPABASE_URL=https://iwzsqxkfbqyddnalsbrb.supabase.co
SUPABASE_PUBLISHABLE_KEY=sb_publishable_5Fdb7Im5ZSICcgjXJxOLog_R2cuO2-C
```

> Not a separate "Untrained" project — that older project (with prior data) was
> left untouched per the decision to start fresh.

## Schema

Defined in `migrations/` and already applied to the project:

- `0001_initial_schema.sql` — all tables, RLS, indexes, `updated_at` triggers.
- `0002_soft_exercise_reference.sql` — drops the hard FK on `exercise_id`
  (offline-first: bundled library is canonical).

Reference tables (shared read-only + per-user custom): `exercises`, `foods`,
`serving_definitions`.
User-owned tables (RLS: `user_id = auth.uid()`): `workout_sessions`,
`set_entries`, `notes`, `body_metrics`, `nutrition_entries`, `goals`,
`injuries`.

Security advisors: **clean** (no warnings).

## Seeding the `exercises` mirror (deferred)

The `exercises` table is intentionally **empty** for now. Because the app ships
the full 883-exercise library as a bundled asset (`data/exercise_library.json`)
and the `exercise_id` FK was relaxed, the Postgres mirror isn't needed until the
server-side AI uses it. Populate it later by either:

- the client upserting its bundled library on first sync, or
- pasting a generated seed into the Supabase SQL editor (generate with
  `data/build_exercise_library.py` output + a `jsonb_to_recordset` insert).

This keeps the mirror authoritative-by-app and avoids duplicating curation.

## AI edge function (scaffold, not deployed)

`functions/ai-coach/index.ts` keeps the Anthropic key server-side. It is
**inert** until activated: with no `ANTHROPIC_API_KEY` secret it returns 501, so
no paid API usage occurs. Per the plan, the key is wired only once the rest of
the app is in place.

```
# later, when starting the AI phase:
supabase functions deploy ai-coach
supabase secrets set ANTHROPIC_API_KEY=sk-ant-...
```

## Auth & sync (email + password, offline-first)

The Android app is offline-first: Room is the source of truth and **an account is
optional**. Signing in (email + password) claims the device's local data and
turns on two-way sync.

- **Push:** local rows with `synced=0` are upserted (stamped with `user_id`),
  then marked synced. **Pull:** rows changed since `lastSync` (RLS scopes them to
  the user) are upserted into Room. Conflict policy: last-write-wins by
  `updated_at`; local unsynced rows are never clobbered.
- Implemented in `app/.../data/auth/AuthManager.kt`,
  `app/.../data/sync/SyncManager.kt`, with DTO mapping in
  `app/.../data/remote/`.

### Required dashboard step (one-time)

For the "me + friends" stage, **disable email confirmation** so sign-up works
without configuring SMTP:

> Authentication → Providers → **Email** → turn **off** "Confirm email" (and
> keep "Enable email provider" on).

Otherwise new accounts can't sign in until they click a confirmation email, and
Supabase's built-in mailer is rate-limited. Revisit (enable confirmation + a real
SMTP/email provider) before any public release.
