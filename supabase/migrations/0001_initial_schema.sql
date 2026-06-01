-- Unnamed — initial schema
-- Local-first design: the app's Room DB is the source of truth; this Postgres
-- schema is the sync/backup layer. Every user-owned row carries user_id and is
-- protected by Row-Level Security so each user only ever sees their own data.
-- Reference tables (exercises, foods) are shared read-only, with optional
-- per-user custom rows.
--
-- Canonical units (see docs/PRODUCT_SPEC.md §4):
--   lifting load -> kilograms (weight_kg, added_load_kg)
--   food mass    -> grams      (resolved_grams, *_per_100g)

-- updated_at maintenance ----------------------------------------------------
create or replace function public.set_updated_at()
returns trigger
language plpgsql
set search_path = ''            -- pinned for security (no mutable search_path)
as $$
begin
  new.updated_at = now();
  return new;
end;
$$;

-- ===========================================================================
-- REFERENCE DATA (shared, read-only; optional per-user custom rows)
-- ===========================================================================

-- Canonical exercise library (seeded from data/exercise_library.json).
create table public.exercises (
  id                 text primary key,           -- stable slug
  name               text not null,
  aliases            text[] not null default '{}',
  movement_pattern   text,
  equipment          text,
  primary_muscles    text[] not null default '{}',
  secondary_muscles  text[] not null default '{}',
  is_bodyweight      boolean not null default false,
  allows_added_load  boolean not null default true,
  is_unilateral      boolean not null default false,
  category           text,
  mechanic           text,
  force              text,
  level              text,
  instructions       text[] not null default '{}',
  images             text[] not null default '{}',
  is_custom          boolean not null default false,
  created_by         uuid references auth.users(id) on delete cascade,
  created_at         timestamptz not null default now()
);
create index exercises_name_idx on public.exercises using gin (to_tsvector('english', name));
create index exercises_aliases_idx on public.exercises using gin (aliases);

alter table public.exercises enable row level security;
create policy "exercises readable (shared or own custom)" on public.exercises
  for select using (is_custom = false or created_by = auth.uid());
create policy "insert own custom exercise" on public.exercises
  for insert with check (is_custom = true and created_by = auth.uid());
create policy "update own custom exercise" on public.exercises
  for update using (created_by = auth.uid()) with check (created_by = auth.uid());
create policy "delete own custom exercise" on public.exercises
  for delete using (created_by = auth.uid());

-- Food catalog (Open Food Facts / USDA / user custom). Macros per 100 of the
-- food's canonical unit (g for solids, ml for liquids).
create table public.foods (
  id                uuid primary key default gen_random_uuid(),
  source            text not null default 'custom',  -- off | usda | custom
  external_id       text,                            -- barcode or FDC id
  name              text not null,
  brand             text,
  canonical_unit    text not null default 'g',       -- g | ml
  density_g_per_ml  numeric,                          -- for ml<->g conversion
  kcal_per_100      numeric,
  protein_per_100   numeric,
  carbs_per_100     numeric,
  fat_per_100       numeric,
  fiber_per_100     numeric,
  sugar_per_100     numeric,
  is_custom         boolean not null default true,
  created_by        uuid references auth.users(id) on delete cascade,
  created_at        timestamptz not null default now()
);
create index foods_name_idx on public.foods using gin (to_tsvector('english', name));
create index foods_external_idx on public.foods (source, external_id);

alter table public.foods enable row level security;
create policy "foods readable (shared or own custom)" on public.foods
  for select using (is_custom = false or created_by = auth.uid());
create policy "insert own custom food" on public.foods
  for insert with check (created_by = auth.uid());
create policy "update own custom food" on public.foods
  for update using (created_by = auth.uid()) with check (created_by = auth.uid());
create policy "delete own custom food" on public.foods
  for delete using (created_by = auth.uid());

-- Named servings for a food ("1 medium banana = 118 g").
create table public.serving_definitions (
  id       uuid primary key default gen_random_uuid(),
  food_id  uuid not null references public.foods(id) on delete cascade,
  label    text not null,
  grams    numeric not null
);
alter table public.serving_definitions enable row level security;
-- readable if the parent food is readable; writable if you own the parent food
create policy "servings readable" on public.serving_definitions
  for select using (exists (
    select 1 from public.foods f where f.id = food_id
      and (f.is_custom = false or f.created_by = auth.uid())));
create policy "servings writable for own food" on public.serving_definitions
  for all using (exists (
    select 1 from public.foods f where f.id = food_id and f.created_by = auth.uid()))
  with check (exists (
    select 1 from public.foods f where f.id = food_id and f.created_by = auth.uid()));

-- ===========================================================================
-- USER-OWNED DATA (RLS: user_id = auth.uid())
-- ===========================================================================

create table public.workout_sessions (
  id               uuid primary key default gen_random_uuid(),
  user_id          uuid not null default auth.uid() references auth.users(id) on delete cascade,
  title            text,
  session_date     date not null default current_date,
  started_at       timestamptz,
  ended_at         timestamptz,
  overall_fatigue  smallint check (overall_fatigue between 1 and 5),
  notes            text,
  created_at       timestamptz not null default now(),
  updated_at       timestamptz not null default now()
);
create index workout_sessions_user_date_idx on public.workout_sessions (user_id, session_date desc);
create trigger workout_sessions_updated before update on public.workout_sessions
  for each row execute function public.set_updated_at();

create table public.set_entries (
  id             uuid primary key default gen_random_uuid(),
  user_id        uuid not null default auth.uid() references auth.users(id) on delete cascade,
  session_id     uuid not null references public.workout_sessions(id) on delete cascade,
  exercise_id    text references public.exercises(id),
  set_index      smallint not null default 1,
  set_type       text not null default 'working',  -- working|warmup|drop|amrap|backoff
  reps           smallint,
  weight_kg      numeric,
  rpe            numeric check (rpe >= 1 and rpe <= 10),
  fatigue        smallint check (fatigue between 1 and 5),
  is_bodyweight  boolean not null default false,
  added_load_kg  numeric,
  side           text,                              -- left|right|both (unilateral)
  created_at     timestamptz not null default now(),
  updated_at     timestamptz not null default now()
);
create index set_entries_session_idx on public.set_entries (session_id);
create index set_entries_user_exercise_idx on public.set_entries (user_id, exercise_id);
create trigger set_entries_updated before update on public.set_entries
  for each row execute function public.set_updated_at();

-- Subjective notes, optionally linked to a set/exercise/session. Feeds the
-- injury-prevention skill later.
create table public.notes (
  id            uuid primary key default gen_random_uuid(),
  user_id       uuid not null default auth.uid() references auth.users(id) on delete cascade,
  session_id    uuid references public.workout_sessions(id) on delete cascade,
  set_entry_id  uuid references public.set_entries(id) on delete cascade,
  exercise_id   text references public.exercises(id),
  body          text not null,
  tags          text[] not null default '{}',      -- e.g. {pain, shoulder}
  severity      smallint check (severity between 1 and 5),
  created_at    timestamptz not null default now()
);
create index notes_user_idx on public.notes (user_id, created_at desc);

create table public.body_metrics (
  id          uuid primary key default gen_random_uuid(),
  user_id     uuid not null default auth.uid() references auth.users(id) on delete cascade,
  metric_date date not null default current_date,
  weight_kg   numeric,
  bodyfat_pct numeric,
  note        text,
  created_at  timestamptz not null default now(),
  unique (user_id, metric_date)
);

create table public.nutrition_entries (
  id              uuid primary key default gen_random_uuid(),
  user_id         uuid not null default auth.uid() references auth.users(id) on delete cascade,
  consumed_on     date not null default current_date,
  meal            text,                             -- breakfast|lunch|dinner|snack
  food_id         uuid references public.foods(id),
  quantity        numeric,
  quantity_unit   text,                             -- g|ml|serving
  resolved_grams  numeric,                          -- canonical mass after conversion
  kcal            numeric,                          -- snapshot at log time
  protein         numeric,
  carbs           numeric,
  fat             numeric,
  note            text,
  created_at      timestamptz not null default now()
);
create index nutrition_entries_user_date_idx on public.nutrition_entries (user_id, consumed_on desc);

create table public.goals (
  id           uuid primary key default gen_random_uuid(),
  user_id      uuid not null default auth.uid() references auth.users(id) on delete cascade,
  goal_type    text,                                -- strength|hypertrophy|fat_loss|...
  description  text,
  details      jsonb not null default '{}',
  is_active    boolean not null default true,
  created_at   timestamptz not null default now(),
  updated_at   timestamptz not null default now()
);
create trigger goals_updated before update on public.goals
  for each row execute function public.set_updated_at();

create table public.injuries (
  id           uuid primary key default gen_random_uuid(),
  user_id      uuid not null default auth.uid() references auth.users(id) on delete cascade,
  area         text not null,                       -- e.g. shoulder, lower_back
  description  text,
  status       text not null default 'active',      -- active|recovering|resolved
  since_date   date,
  created_at   timestamptz not null default now(),
  updated_at   timestamptz not null default now()
);
create trigger injuries_updated before update on public.injuries
  for each row execute function public.set_updated_at();

-- Uniform per-user RLS for the owned tables above.
do $$
declare t text;
begin
  foreach t in array array[
    'workout_sessions','set_entries','notes','body_metrics',
    'nutrition_entries','goals','injuries'
  ] loop
    execute format('alter table public.%I enable row level security;', t);
    execute format($f$create policy "select own" on public.%I
      for select using (user_id = auth.uid());$f$, t);
    execute format($f$create policy "insert own" on public.%I
      for insert with check (user_id = auth.uid());$f$, t);
    execute format($f$create policy "update own" on public.%I
      for update using (user_id = auth.uid()) with check (user_id = auth.uid());$f$, t);
    execute format($f$create policy "delete own" on public.%I
      for delete using (user_id = auth.uid());$f$, t);
  end loop;
end $$;
