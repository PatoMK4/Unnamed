# Seed data

Canonical reference data loaded into the app's database on first run, then
editable by the user. Kept as JSON so it's reviewable in version control.

## Files

| File | What it is |
|------|------------|
| `exercise_library.json` | **Generated** curated library (883 exercises). Do not hand-edit. |
| `exercise_curated_overrides.json` | Hand-written entries that win on overlap. Edit this. |
| `build_exercise_library.py` | Build script that produces `exercise_library.json`. |

## How the library is built

The library is **derived, not hand-typed**: we take the public-domain
[`free-exercise-db`](https://github.com/yuhonas/free-exercise-db) (~873
exercises, Unlicense) and run correction/normalization passes onto Unnamed's
canonical schema, then merge the hand-curated overrides on top.

```
free-exercise-db (raw, messy) ─┐
                               ├─▶ build_exercise_library.py ─▶ exercise_library.json
exercise_curated_overrides ────┘   (normalize + merge + validate)
```

Correction passes the script performs:
1. **Equipment** → canonical vocab (`body only`→`bodyweight`, `e-z curl bar`→
   `ez_bar`, `kettlebells`→`kettlebell`, …).
2. **Muscles** → canonical vocab (`abdominals`→`abs`, `quadriceps`→`quads`,
   `middle back`→`upper_back`, …), and the generic `shoulders` is **upgraded**
   to `front_/side_/rear_delts` by name heuristics where confidently inferable
   (lateral raise→side, reverse fly/face pull→rear, press/OHP→front). Where the
   head is genuinely ambiguous (most compound presses), it stays `shoulders`.
3. **Movement pattern** (absent from the source) is **derived** from name +
   force + muscles.
4. **Aliases** are generated (strip "- Medium Grip" suffixes, add bb/db
   abbreviations); any alias that would map to >1 exercise is dropped so the
   parser never silently mis-resolves.
5. **Merge**: when a source exercise matches a curated override (by name or
   alias), the override's aliases/muscles/pattern win, and we keep the source's
   `instructions` + `images`.

Re-run any time: `python3 data/build_exercise_library.py`

### Record format

```json
{
  "id": "incline-dumbbell-press",      // stable slug, never changes
  "name": "Incline Dumbbell Press",    // canonical display name
  "aliases": ["incline db press", "incline dumbbell bench", "inc db press"],
  "movement_pattern": "horizontal_push",
  "equipment": "dumbbell",
  "primary_muscles": ["chest"],
  "secondary_muscles": ["front_delts", "triceps"],
  "is_bodyweight": false,              // load comes from added weight only?
  "allows_added_load": true,           // can weight be added (belt/plate/db)?
  "is_unilateral": true,               // one limb at a time (per-side logging)?
  "category": "strength",              // strength|stretching|plyometrics|cardio|...
  "mechanic": "compound",              // compound|isolation|null
  "force": "push",                     // push|pull|static|null
  "level": "beginner",                 // beginner|intermediate|expert|null
  "instructions": ["...", "..."],      // step-by-step, shown in the app
  "images": ["Incline_DB_Press/0.jpg"] // relative paths in free-exercise-db
}
```

Allowed values for `movement_pattern`, `equipment`, and the muscle fields are
enumerated at the top of `exercise_library.json` (`movement_patterns`,
`equipment_vocabulary`, `muscle_vocabulary`). Keep entries within these
vocabularies so filtering and analysis stay consistent.

> **Note on `shoulders`:** `shoulders` = unspecified deltoid (used when the head
> can't be inferred); `front_delts`/`side_delts`/`rear_delts` are used when the
> specific head is known. Mixed granularity is intentional and honest.

### How the parser uses it

1. On-device speech-to-text produces raw text.
2. The parser matches the spoken exercise phrase against `name` + `aliases`
   across all records (fuzzy match for typos/mishearings).
3. The matched `id` is attached to the set; numbers (reps, weight, RPE,
   fatigue) and subjective notes are extracted alongside.
4. If no confident match, the confirmation card asks you to pick/confirm — and
   you can save the spoken phrase as a new alias (added to the overrides) so it
   matches next time. This is how the library grows from real usage.

### Extending / fixing the set

- Add or correct entries in `exercise_curated_overrides.json` (matched to the
  source by `name`/`aliases`), then re-run the build.
- Give new entries generous, realistic `aliases` (how people actually *say* it).
- `is_unilateral: true` signals the logger to capture per-side data.
- `allows_added_load: false` is for movements where extra load doesn't apply
  (stretches, ab wheel rollout). Bodyweight movements that *can* be loaded
  (pull-ups, dips) use `is_bodyweight: true` + `allows_added_load: true`.

## Subjective scales (referenced by the data model)

Agreed scales for the `rpe` and `fatigue` fields on a set, so logging and AI
analysis interpret them the same way.

### RPE — Rate of Perceived Exertion (per set)

Standard **RIR-based 1–10 scale** (half-points allowed). Higher = closer to
failure.

| RPE | Meaning |
|-----|---------|
| 10  | Maximal — no reps left in reserve (RIR 0) |
| 9.5 | Maybe a tiny bit more weight, no more reps |
| 9   | 1 rep left in reserve (RIR 1) |
| 8   | 2 reps in reserve (RIR 2) |
| 7   | 3 reps in reserve (RIR 3) |
| 5–6 | Warm-up / submaximal, many reps left |
| 1–4 | Very easy / light effort |

### Fatigue (per set)

A **1–5 scale** for how drained you felt *on that set* (distinct from RPE: a set
can be high-RPE but low systemic fatigue, or vice versa). Used by the
recovery/injury skills later.

| Fatigue | Meaning |
|---------|---------|
| 1 | Fresh, strong |
| 2 | Slightly worked |
| 3 | Moderately tired |
| 4 | Heavily fatigued |
| 5 | Exhausted / form breaking down |

> Both are optional per set — the logger never blocks on them — but the more
> consistently they're captured, the better the coaching skills perform.

## Licensing

Exercise content (names, instructions, images) derives from
[`free-exercise-db`](https://github.com/yuhonas/free-exercise-db), released
under the **Unlicense** (public domain). See `NOTICE` at the repo root.
