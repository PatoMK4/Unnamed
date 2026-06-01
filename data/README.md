# Seed data

Canonical reference data loaded into the app's database on first run, then
editable by the user. Kept as plain JSON so it's reviewable in version control.

## `exercise_library.json`

The hand-curated starter exercise set (65 exercises across all 10 movement
patterns). This is the backbone of data quality: the chat parser matches your
natural-language input against these records so that "incline db press" and
"incline dumbbell bench" both resolve to the **same** canonical exercise, which
keeps trends and AI analysis from fragmenting.

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
  "is_unilateral": true                // one limb at a time (per-side logging)?
}
```

Allowed values for `movement_pattern`, `equipment`, and the muscle fields are
enumerated at the top of `exercise_library.json` (`movement_patterns`,
`equipment_vocabulary`, `muscle_vocabulary`). Keep new entries within these
vocabularies so filtering and analysis stay consistent.

### How the parser uses it

1. On-device speech-to-text produces raw text.
2. The parser lowercases the spoken exercise phrase and matches it against
   `name` + `aliases` across all records (fuzzy match for typos/mishearings).
3. The matched `id` is attached to the set; numbers (reps, weight, RPE,
   fatigue) and subjective notes are extracted alongside.
4. If no confident match, the confirmation card asks you to pick/confirm the
   exercise — and you can save the spoken phrase as a new alias so it matches
   next time. This is how the library grows from real usage.

### Extending the set

- Add a new object to the `exercises` array with a unique `id`.
- Give it generous, realistic `aliases` (how people actually *say* it).
- Avoid alias clashes with existing exercises (an alias should resolve to
  exactly one exercise).
- `is_unilateral: true` signals the logger to capture per-side data.
- `allows_added_load: false` is for movements where extra load doesn't apply
  (e.g. ab wheel rollout); bodyweight movements that *can* be loaded (pull-ups,
  dips) use `is_bodyweight: true` + `allows_added_load: true`.

## Subjective scales (referenced by the data model)

These definitions are the agreed scales for the `rpe` and `fatigue` fields on a
set, so logging and AI analysis interpret them the same way.

### RPE — Rate of Perceived Exertion (per set)

How hard the set was, on the standard **RIR-based 1–10 scale** (half-points
allowed). Higher = closer to failure.

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

A simpler **1–5 scale** capturing how drained you felt *on that set*
(distinct from RPE: a set can be high-RPE but low systemic fatigue, or vice
versa). Used by the recovery/injury skills later.

| Fatigue | Meaning |
|---------|---------|
| 1 | Fresh, strong |
| 2 | Slightly worked |
| 3 | Moderately tired |
| 4 | Heavily fatigued |
| 5 | Exhausted / form breaking down |

> Both are optional per set — the logger never blocks on them — but the more
> consistently they're captured, the better the coaching skills perform.
