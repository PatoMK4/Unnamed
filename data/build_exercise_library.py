#!/usr/bin/env python3
"""Build the curated exercise library for Unnamed.

Takes the public-domain free-exercise-db (yuhonas/free-exercise-db), runs
normalization/correction passes onto Unnamed's canonical schema, and merges the
hand-curated starter set on top (hand entries win on overlap).

Inputs:
  - free-exercise-db dist JSON (path arg 1, default /tmp/fedb.json)
  - hand-curated overrides (data/exercise_curated_overrides.json)
Output:
  - data/exercise_library.json

Run: python3 data/build_exercise_library.py
The result is reviewable in version control; re-run to regenerate.
"""
import json, re, sys, os, collections

HERE = os.path.dirname(os.path.abspath(__file__))
SRC = sys.argv[1] if len(sys.argv) > 1 else "/tmp/fedb.json"
OVERRIDES = os.path.join(HERE, "exercise_curated_overrides.json")
OUT = os.path.join(HERE, "exercise_library.json")

MUSCLE_VOCAB = [
    "chest", "front_delts", "side_delts", "rear_delts", "shoulders",
    "triceps", "biceps", "forearms", "lats", "upper_back", "traps",
    "lower_back", "glutes", "quads", "hamstrings", "adductors", "abductors",
    "calves", "abs", "obliques", "neck",
]
EQUIP_VOCAB = [
    "barbell", "dumbbell", "machine", "cable", "bodyweight", "kettlebell",
    "smith_machine", "ez_bar", "trap_bar", "band", "medicine_ball",
    "exercise_ball", "foam_roller", "other",
]
PATTERN_VOCAB = [
    "horizontal_push", "vertical_push", "horizontal_pull", "vertical_pull",
    "squat", "hinge", "lunge", "carry", "core", "isolation",
    "mobility", "plyometric", "cardio", "other",
]

MUSCLE_MAP = {
    "abdominals": "abs", "quadriceps": "quads", "hamstrings": "hamstrings",
    "adductors": "adductors", "abductors": "abductors", "biceps": "biceps",
    "shoulders": "shoulders", "chest": "chest", "middle back": "upper_back",
    "calves": "calves", "glutes": "glutes", "lower back": "lower_back",
    "lats": "lats", "triceps": "triceps", "traps": "traps",
    "forearms": "forearms", "neck": "neck",
    # accept already-canonical names from overrides:
    **{m: m for m in MUSCLE_VOCAB},
    "spinal_erectors": "lower_back",  # collapse to open-DB granularity
}
EQUIP_MAP = {
    "body only": "bodyweight", "barbell": "barbell", "dumbbell": "dumbbell",
    "machine": "machine", "cable": "cable", "kettlebells": "kettlebell",
    "e-z curl bar": "ez_bar", "bands": "band", "medicine ball": "medicine_ball",
    "exercise ball": "exercise_ball", "foam roll": "foam_roller",
    "other": "other", None: "other",
    **{e: e for e in EQUIP_VOCAB},
}

def slugify(name):
    s = name.lower().strip()
    s = re.sub(r"[^a-z0-9]+", "-", s).strip("-")
    return s

def shoulder_head(name):
    """Upgrade generic 'shoulders' to a specific delt head when inferable."""
    n = name.lower()
    if any(k in n for k in ["rear delt", "reverse fly", "rear lateral",
                            "reverse pec", "face pull", "rear-delt"]):
        return "rear_delts"
    if any(k in n for k in ["lateral raise", "side raise", "side lateral",
                            "lateral fly"]):
        return "side_delts"
    if "front raise" in n:
        return "front_delts"
    if any(k in n for k in ["overhead press", "military press", "shoulder press",
                            "push press", "ohp", "arnold"]):
        return "front_delts"
    return "shoulders"

def map_muscles(lst, name, is_primary):
    out = []
    for m in lst or []:
        canon = MUSCLE_MAP.get(m, m)
        if canon == "shoulders":
            canon = shoulder_head(name) if is_primary else "shoulders"
        if canon in MUSCLE_VOCAB and canon not in out:
            out.append(canon)
    return out

PATTERN_RULES = [
    ("carry",          ["carry", "farmer", "waiter walk", "suitcase", "yoke",
                        "conan", "sled"]),
    ("lunge",          ["lunge", "split squat", "step-up", "step up", "stepup"]),
    ("squat",          ["squat", "leg press", "hack ", "sissy", "wall sit"]),
    ("hinge",          ["deadlift", "rdl", "romanian", "good morning",
                        "hip thrust", "glute bridge", "back extension",
                        "hyperextension", "kettlebell swing", "swing",
                        "stiff leg", "stiff-leg", "pull-through", "pull through"]),
    ("vertical_pull",  ["pulldown", "pull-down", "pull down", "pullup",
                        "pull-up", "pull up", "chin-up", "chin up", "chinup"]),
    ("horizontal_pull",["row", "face pull", "rear delt", "reverse fly",
                        "reverse pec", "pull-apart", "pull apart", "shrug"]),
    ("vertical_push",  ["overhead press", "military press", "shoulder press",
                        "push press", "ohp", "arnold", "handstand", "pike push"]),
    ("horizontal_push",["bench press", "push-up", "push up", "pushup", "dip",
                        "chest press", "floor press",
                        "incline press", "decline press"]),
]

def derive_pattern(name, category, mechanic, force, primary):
    n = name.lower()
    if category == "stretching":
        return "mobility"
    if category == "cardio":
        return "cardio"
    if category == "plyometrics":
        return "plyometric"
    # chest/rear-delt flyes are isolation, not a press
    if any(k in n for k in ["fly", "flye"]):
        return "isolation"
    for pat, kws in PATTERN_RULES:
        if any(k in n for k in kws):
            # rows/shrugs/face pulls correctly land horizontal_pull above
            return pat
    # anything primarily trained at the trunk is core
    if any(m in primary for m in ["abs", "obliques"]):
        return "core"
    # muscle-based fallback for presses/raises
    if "raise" in n and any(m in primary for m in
                            ["side_delts", "front_delts", "rear_delts", "shoulders"]):
        return "isolation"
    if mechanic == "isolation":
        return "isolation"
    if force == "pull":
        return "horizontal_pull"
    if force == "push":
        return "horizontal_push"
    return "other"

ABBREV = [("barbell", "bb"), ("dumbbell", "db"), ("e-z bar", "ez bar"),
          ("e-z curl", "ez curl")]

def make_aliases(name):
    al = set()
    base = name.lower().strip()
    al.add(base)
    # strip grip/variant suffix after " - "
    if " - " in base:
        al.add(base.split(" - ")[0].strip())
    for long, short in ABBREV:
        if long in base:
            al.add(base.replace(long, short))
    al.discard("")
    return al

def is_unilateral(name):
    n = name.lower()
    return any(k in n for k in ["single-arm", "single arm", "one-arm", "one arm",
                                "single-leg", "single leg", "one-leg", "one leg",
                                "single", "one-legged"])

def allows_load(category, equipment, is_bw):
    if category in ("stretching", "cardio"):
        return False
    if equipment in ("barbell", "dumbbell", "machine", "cable", "kettlebell",
                     "ez_bar", "trap_bar", "smith_machine", "medicine_ball",
                     "band", "exercise_ball"):
        return True
    return is_bw  # bodyweight movements can usually be weighted

def main():
    src = json.load(open(SRC))
    overrides = json.load(open(OVERRIDES))["exercises"]

    # index overrides by every alias + name for matching
    ov_by_key = {}
    for o in overrides:
        keys = set(a.lower() for a in o.get("aliases", []))
        keys.add(o["name"].lower())
        for k in keys:
            ov_by_key[k] = o

    built = {}
    matched_ov = set()
    for x in src:
        name = x["name"].strip()
        equipment = EQUIP_MAP.get(x.get("equipment"), "other")
        is_bw = equipment == "bodyweight"
        category = x.get("category")
        primary = map_muscles(x.get("primaryMuscles"), name, True)
        secondary = map_muscles(x.get("secondaryMuscles"), name, False)
        secondary = [m for m in secondary if m not in primary]
        pattern = derive_pattern(name, category, x.get("mechanic"),
                                 x.get("force"), primary)
        rec = {
            "id": slugify(name),
            "name": name,
            "aliases": sorted(make_aliases(name) - {name.lower()}),
            "movement_pattern": pattern,
            "equipment": equipment,
            "primary_muscles": primary,
            "secondary_muscles": secondary,
            "is_bodyweight": is_bw,
            "allows_added_load": allows_load(category, equipment, is_bw),
            "is_unilateral": is_unilateral(name),
            "category": category,
            "mechanic": x.get("mechanic"),
            "force": x.get("force"),
            "level": x.get("level"),
            "instructions": x.get("instructions", []),
            "images": x.get("images", []),
        }
        # merge a hand-curated override if this exercise matches one
        ov = ov_by_key.get(name.lower())
        if not ov:
            for a in rec["aliases"]:
                if a in ov_by_key:
                    ov = ov_by_key[a]
                    break
        if ov:
            matched_ov.add(ov["id"])
            rec["id"] = ov["id"]
            rec["name"] = ov["name"]
            rec["aliases"] = sorted(set(rec["aliases"]) |
                                    set(a.lower() for a in ov.get("aliases", [])))
            rec["movement_pattern"] = ov["movement_pattern"]
            rec["equipment"] = ov["equipment"]
            rec["primary_muscles"] = map_muscles(ov["primary_muscles"], ov["name"], True)
            rec["secondary_muscles"] = [m for m in
                map_muscles(ov["secondary_muscles"], ov["name"], False)
                if m not in rec["primary_muscles"]]
            rec["is_unilateral"] = ov["is_unilateral"]
            rec["allows_added_load"] = ov["allows_added_load"]
        # de-dup ids
        base_id = rec["id"]
        i = 2
        while rec["id"] in built and built[rec["id"]]["name"] != rec["name"]:
            rec["id"] = f"{base_id}-{i}"; i += 1
        rec["aliases"] = [a for a in rec["aliases"] if a != rec["name"].lower()]
        built[rec["id"]] = rec

    # add any hand-curated exercises not present in the source dataset
    for o in overrides:
        if o["id"] in matched_ov:
            continue
        rec = {
            "id": o["id"], "name": o["name"],
            "aliases": sorted(set(a.lower() for a in o.get("aliases", []))),
            "movement_pattern": o["movement_pattern"], "equipment": o["equipment"],
            "primary_muscles": map_muscles(o["primary_muscles"], o["name"], True),
            "secondary_muscles": map_muscles(o["secondary_muscles"], o["name"], False),
            "is_bodyweight": o["is_bodyweight"],
            "allows_added_load": o["allows_added_load"],
            "is_unilateral": o["is_unilateral"],
            "category": "strength", "mechanic": None, "force": None,
            "level": None, "instructions": [], "images": [],
        }
        if rec["id"] not in built:
            built[rec["id"]] = rec

    exercises = sorted(built.values(), key=lambda r: r["name"].lower())

    # an alias must map to exactly one exercise; drop ambiguous ones so the
    # parser never silently picks the wrong exercise.
    alias_count = collections.Counter()
    for r in exercises:
        for a in set(r["aliases"]):
            alias_count[a] += 1
    ambiguous = {a for a, c in alias_count.items() if c > 1}
    for r in exercises:
        r["aliases"] = sorted(a for a in r["aliases"] if a not in ambiguous)
    collisions = {a: alias_count[a] for a in ambiguous}

    out = {
        "schema_version": 2,
        "description": "Curated exercise library for Unnamed. Built from the "
                       "public-domain free-exercise-db, normalized to Unnamed's "
                       "canonical schema and merged with hand-curated overrides. "
                       "Regenerate with data/build_exercise_library.py.",
        "source": {
            "dataset": "yuhonas/free-exercise-db",
            "license": "Unlicense (public domain)",
            "url": "https://github.com/yuhonas/free-exercise-db",
        },
        "muscle_vocabulary": MUSCLE_VOCAB,
        "equipment_vocabulary": EQUIP_VOCAB,
        "movement_patterns": PATTERN_VOCAB,
        "exercises": exercises,
    }
    json.dump(out, open(OUT, "w"), indent=2, ensure_ascii=False)

    # stats
    print(f"total exercises: {len(exercises)}")
    print(f"hand-curated merged: {len(matched_ov)} / {len(overrides)}")
    pats = collections.Counter(r["movement_pattern"] for r in exercises)
    print("by pattern:", dict(pats))
    other = [r["name"] for r in exercises if r["movement_pattern"] == "other"]
    print(f"unclassified ('other'): {len(other)}")
    no_primary = [r["name"] for r in exercises if not r["primary_muscles"]]
    print(f"no primary muscle: {len(no_primary)}")
    print("alias collisions:", dict(collisions) if collisions else "none")

if __name__ == "__main__":
    main()
