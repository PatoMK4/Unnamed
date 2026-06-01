-- Offline-first: the bundled app exercise library is canonical; the Postgres
-- exercises table is only an optional server-side mirror. Drop the hard FK so
-- sync never fails if the mirror is incomplete (exercise_id stays a slug).
alter table public.set_entries drop constraint set_entries_exercise_id_fkey;
alter table public.notes drop constraint notes_exercise_id_fkey;
