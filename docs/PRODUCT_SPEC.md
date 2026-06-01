# Unnamed — Product & Architecture Spec (v0.1, draft)

> A personalized, AI-assisted training & nutrition tracker for Android.
> Core bet: **logging is a conversation, not a form.** You talk/type to the app
> like a chat; it parses your message into clean structured data, shows you a
> card to confirm, then saves. The same clean data later powers an AI coach.

This is a living document. It captures decisions made so far so we build on a
shared foundation. Nothing here is locked; flag anything you disagree with.

---

## 1. Vision

Track every set (reps, weight, RPE, fatigue), subjective notes ("shoulder felt
heavy"), and full nutrition — then layer an AI that (a) answers questions about
your training and (b) eventually adapts your programming to your goals, state,
and injuries via modular "skills."

## 2. Audience & rollout

- **v1:** me + a few friends. No public scale concerns yet.
- **Later (aspirational):** Play Store release *if* it proves great.
- Implication: build clean and offline-first now, but defer multi-tenant auth,
  privacy/compliance, and per-user AI cost engineering until a release is real.
  Design so those can be added without a rewrite.

## 3. Guiding principles

1. **Data quality first.** The AI is only as good as the structured data under
   it. Normalize aggressively (canonical exercises, canonical units).
2. **Confirm before commit.** Parsed input is always shown as an editable card
   the user approves. The DB only ever holds confirmed data.
3. **Offline-first.** Logging must work with no signal (gyms are dead zones).
   The local database is the source of truth.
4. **LLM where it's strong, rules where stakes are high.** Use the LLM for
   language→data parsing and explanation. Use deterministic logic for anything
   prescriptive or safety-related (progression, injury contraindications).
5. **Scope discipline.** Ship the chat-logging loop first. Everything else
   bolts onto that data spine.

## 4. The two unit systems (must never get tangled)

| Domain | Canonical unit | Display | Notes |
|--------|---------------|---------|-------|
| **Lifting load** | kilograms (kg) | kg (lb optional later) | Stored as kg always. |
| **Food mass** | grams (g) | user-friendly servings | Liquids in ml; density used for ml↔g. |

- Food databases report per-100g, per-serving, per-ml, etc. A **normalization
  layer** converts everything to canonical units on import. All math happens in
  canonical units; the UI converts only for display.
- Each food carries serving definitions ("1 medium banana = 118 g") and, where
  needed, a **density** (g/ml) for volume↔mass conversion.

## 5. The chat-logging loop (the heart of v1)

```
You speak/type ─▶ On-device speech-to-text (free, offline) ─▶ raw text
      ─▶ Parser (LLM) maps text onto canonical exercises + extracts
         { sets, reps, weight, RPE, fatigue } and tags subjective notes
      ─▶ Editable confirmation card shown to you
      ─▶ You confirm/fix ─▶ saved to local DB (source of truth)
```

### Hard problems this design must handle
- **Conversation context.** "Another set, same weight, 7 reps" requires knowing
  the *current* exercise and last set. The chat maintains live session state.
- **Exercise normalization.** Free speech ("incline DB press") snaps onto one
  canonical exercise so trends/analysis don't fragment. Backed by a curated
  **exercise library**.
- **Two content types per message.** Performance numbers → structured tables.
  Subjective notes → tagged, linked to the specific set/exercise, fed later to
  the injury-prevention skill.
- **Edge cases.** Supersets, drop sets, AMRAP, partials, warm-up vs working
  sets, bodyweight + added load.
- **Fallbacks.** Typed input + quick-tap steppers + a rest timer, for when
  voice is impractical (loud gym, privacy).

## 6. AI capabilities — phased by risk

| Phase | Capability | Risk | LLM role |
|-------|-----------|------|----------|
| A | Parse chat → structured logs (with confirm) | Low | High — core strength |
| B | Analyst Q&A ("how's my bench trending?", "am I recovering?") | Low | Retrieval + summary over your data |
| C | Guided adjustments within rule-based guardrails | Medium | Explain/adjust *within* deterministic schemes |
| D | Skills: nutrition / training / injury-prevention modules | Medium–High | Reason over shared data, bounded by domain rules |

> **Injury prevention especially must be rules + curated knowledge, not a free
> prompt.** A confident wrong answer is a real liability.

## 7. "Skills" architecture (your idea, formalized)

A shared **data core** (logs, bodyweight, nutrition, injuries, goals) plus
pluggable **domain modules** that all read the same data:

- **Training skill** — progression schemes, volume landmarks, deload triggers.
- **Nutrition skill** — macro targets vs intake, trends.
- **Injury-prevention skill** — contraindications, load management, flags from
  subjective notes.
- ...extensible: add new skills without touching the core.

Each skill = curated domain rules/knowledge **+** LLM for explanation and
within-bounds adjustment. Skills are scaffolded in v1 but filled in over time.

## 8. Data model (first sketch — to refine before coding)

- **Exercise** (canonical): id, name, aliases[], primary muscle, equipment,
  movement pattern, is_bodyweight, allows_added_load.
- **WorkoutSession**: id, date, notes, perceived overall fatigue.
- **SetEntry**: id, session_id, exercise_id, set_index, type
  (working/warmup/drop/amrap), reps, weight_kg, rpe, fatigue, is_bodyweight,
  added_load_kg.
- **Note**: id, linked_to (set/exercise/session), text, tags[] (e.g. `pain`,
  `shoulder`), severity?.
- **Food**: id, name, source (OpenFoodFacts/USDA), barcode?, canonical_unit
  (g/ml), density_g_per_ml?, macros_per_100 (kcal, protein, carbs, fat, ...).
- **ServingDefinition**: id, food_id, label, grams.
- **NutritionEntry**: id, date, meal, food_id, quantity, quantity_unit,
  resolved_grams, computed macros.
- **BodyMetric**: id, date, weight_kg, ...
- **Goal / InjuryProfile**: drives the coaching skills.

## 9. Nutrition (own phase — heaviest component)

- **Food DB source:** Open Food Facts (free, open, global, barcode scanning) as
  base; optionally backfill whole-food macros from USDA FoodData Central.
- **Avoid paid APIs** (Nutritionix/FatSecret) until there's a clear reason
  (licensing + cost).
- **Barcode scanning** is the realistic way to make food logging fast.
- Macro tracking depends entirely on the unit-normalization layer (§4).

## 10. Tech stack (proposed)

- **Android native:** Kotlin + Jetpack Compose (standard, well-supported).
- **Local DB:** Room (SQLite), offline-first, source of truth.
- **Speech:** Android on-device `SpeechRecognizer` (free, fast, offline).
- **AI:** Anthropic API — cheap model (Haiku) for parsing, stronger model
  (Sonnet) for coaching reasoning.
- **API key safety:** fine to prototype, but **before any public release** the
  key must live behind a thin backend/proxy, never on-device. A managed backend
  (e.g. Supabase for auth + sync + a proxy edge function) is a candidate when we
  get there.

## 11. Open questions / next decisions

- [ ] Exercise library: hand-curate a starter set, or import an open dataset?
- [ ] RPE scale: 1–10 with half-points? Define fatigue scale precisely.
- [ ] How much conversation memory does the logging chat keep per session?
- [ ] Confirmation UX: one card per exercise, or per message (could be multiple
      exercises)?
- [ ] Backend timing: stay fully local for v1, or stand up sync early so
      friends can use it?

## 12. Phased roadmap

1. **Phase A — Logging loop:** exercise library + chat input + STT + LLM parser
   + confirmation card + local DB + a session/history view. *Validate it feels
   magic and you use it daily.*
2. **Phase B — Analyst:** trends (volume, estimated 1RM, RPE/fatigue over time)
   + AI Q&A over your data.
3. **Phase C — Nutrition:** food DB + barcode + unit normalization + macro
   tracking.
4. **Phase D — Coaching skills:** training, then injury-prevention, then deeper
   nutrition guidance, within guardrails.
5. **Phase E (if releasing):** auth, sync, key-proxy backend, privacy.
