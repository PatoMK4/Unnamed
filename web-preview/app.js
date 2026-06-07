// Unnamed — web preview of the Android app.
// Ports the ExerciseLibrary + SetParser + analytics engine to the browser.

let LIBRARY = [];
let BY_PHRASE = new Map(); // lowercased alias/name -> exercise
let BY_ID = new Map();     // id -> exercise

const PAIN_WORDS = ["pain","hurt","tweak","sore","heavy","bothered","pinch","ache","uncomfortable"];
const BODY_PARTS = ["lower back","shoulder","knee","elbow","wrist","hip","back","neck","ankle","bicep","hamstring"];

async function boot() {
  const res = await fetch("exercises.json");
  LIBRARY = await res.json();
  for (const e of LIBRARY) {
    BY_ID.set(e.id, e);
    BY_PHRASE.set(e.name.toLowerCase(), e);
    for (const a of (e.aliases || [])) {
      const k = a.toLowerCase();
      if (!BY_PHRASE.has(k)) BY_PHRASE.set(k, e);
    }
  }
  greet();
  renderHistory();
  renderProgress();
}

// --- exercise resolution (mirrors ExerciseLibrary.resolve) ------------------
function resolve(phrase) {
  const p = phrase.trim().toLowerCase();
  if (!p) return null;
  if (BY_PHRASE.has(p)) return BY_PHRASE.get(p);
  for (const [k, v] of BY_PHRASE) {
    if (p.includes(k) || k.includes(p)) return v;
  }
  return null;
}

// --- parser (mirrors SetParser.parse) ---------------------------------------
function parse(message) {
  const lower = message.toLowerCase();
  let sets = 1, reps = null;

  let m = lower.match(/(\d+)\s*(?:x|sets?\s*of)\s*(\d+)/);
  if (m) { sets = +m[1]; reps = +m[2]; }
  else { m = lower.match(/(\d+)\s*reps?/); if (m) reps = +m[1]; }

  const w = lower.match(/(?:at\s*)?(\d+(?:\.\d+)?)\s*(?:kg|kilo|kilos|kgs)/);
  const weightKg = w ? parseFloat(w[1]) : null;

  const r = lower.match(/rpe\s*(\d+(?:\.\d+)?)/);
  const rpe = r ? parseFloat(r[1]) : null;

  const f = lower.match(/fatigue\s*(\d)/);
  const fatigue = f ? +f[1] : null;

  const noteText = extractNote(message);
  const tags = [];
  if (noteText) {
    const nl = noteText.toLowerCase();
    if (PAIN_WORDS.some(x => nl.includes(x))) tags.push("pain");
    const part = BODY_PARTS.find(x => nl.includes(x));
    if (part) tags.push(part);
  }

  const phrase = isolatePhrase(message);
  const exercise = resolve(phrase);
  return { exercise, exercisePhrase: phrase, sets, reps, weightKg, rpe, fatigue, noteText, tags };
}

function extractNote(message) {
  const afterComma = message.includes(",") ? message.slice(message.indexOf(",") + 1).trim() : "";
  if (afterComma) return afterComma;
  const lower = message.toLowerCase();
  return PAIN_WORDS.some(x => lower.includes(x)) ? message.trim() : null;
}

function isolatePhrase(message) {
  const head = message.includes(",") ? message.slice(0, message.indexOf(",")) : message;
  const numIdx = head.search(/\d/);
  return (numIdx === -1 ? head : head.slice(0, numIdx)).trim();
}

// --- analytics engine (mirrors data/analytics/Analytics.kt) -----------------
function epley(load, reps) { return reps <= 1 ? load : load * (1 + reps / 30); }

function setMetrics(s) {
  const hasLoad = s.weightKg != null;
  const load = hasLoad ? s.weightKg + (s.addedLoadKg || 0) : null;
  const reps = s.reps;
  const volume = (load != null && reps != null) ? load * reps : 0;
  const e1rm = (load != null && load > 0 && reps != null && reps > 0) ? epley(load, reps) : null;
  return { load, volume, e1rm, isWorking: s.setType !== "warmup" };
}

function muscleSets(sets) {
  const t = {};
  for (const s of sets) {
    if (s.setType === "warmup") continue;
    const ex = s.exId ? BY_ID.get(s.exId) : null;
    if (!ex) continue;
    for (const m of (ex.primary_muscles || [])) t[m] = (t[m] || 0) + 1;
    for (const m of (ex.secondary_muscles || [])) t[m] = (t[m] || 0) + 0.5;
  }
  return Object.entries(t).map(([muscle, n]) => ({ muscle, sets: n })).sort((a, b) => b.sets - a.sets);
}

function summarize(session) {
  const groups = new Map();
  for (const s of session.sets) {
    const key = s.exId || s.ex;
    if (!groups.has(key)) groups.set(key, { exId: s.exId, name: s.ex, sets: [] });
    groups.get(key).sets.push(s);
  }
  const list = [...groups.values()].map(g => {
    const m = g.sets.map(setMetrics);
    const e1rms = m.map(x => x.e1rm).filter(x => x != null);
    return {
      name: g.name, exId: g.exId,
      sets: g.sets.map((s, i) => ({ s, m: m[i] })),
      workingSets: m.filter(x => x.isWorking).length,
      totalVolume: m.reduce((a, x) => a + x.volume, 0),
      topE1rm: e1rms.length ? Math.max(...e1rms) : null,
    };
  }).sort((a, b) => b.totalVolume - a.totalVolume);
  return {
    session, groups: list,
    totalVolume: list.reduce((a, g) => a + g.totalVolume, 0),
    totalSets: session.sets.length,
    workingSets: list.reduce((a, g) => a + g.workingSets, 0),
    exerciseCount: list.length,
    muscleSets: muscleSets(session.sets),
    topExerciseName: (list.find(g => g.totalVolume > 0) || list[0] || {}).name,
  };
}

function overview(sessions) {
  const sums = sessions.map(summarize);
  const weekCut = Date.now() - 7 * 864e5;
  const week = sums.filter(s => s.session.date >= weekCut);
  const wm = {};
  for (const s of week) for (const mv of s.muscleSets) wm[mv.muscle] = (wm[mv.muscle] || 0) + mv.sets;
  return {
    totalSessions: sums.filter(s => s.totalSets > 0).length,
    totalVolume: sums.reduce((a, s) => a + s.totalVolume, 0),
    totalWorkingSets: sums.reduce((a, s) => a + s.workingSets, 0),
    weekSessions: week.filter(s => s.totalSets > 0).length,
    weekVolume: week.reduce((a, s) => a + s.totalVolume, 0),
    weekWorkingSets: week.reduce((a, s) => a + s.workingSets, 0),
    weekMuscle: Object.entries(wm).map(([muscle, n]) => ({ muscle, sets: n })).sort((a, b) => b.sets - a.sets),
    records: personalRecords(sessions),
  };
}

function personalRecords(sessions) {
  const byEx = new Map();
  for (const ses of sessions) for (const s of ses.sets) {
    const key = s.exId || s.ex;
    if (!key) continue;
    const m = setMetrics(s);
    const cur = byEx.get(key) || { name: s.ex, bestE1rm: null, heaviest: null };
    if (m.e1rm != null) cur.bestE1rm = Math.max(cur.bestE1rm || 0, m.e1rm);
    if (m.load != null) cur.heaviest = Math.max(cur.heaviest || 0, m.load);
    byEx.set(key, cur);
  }
  return [...byEx.values()]
    .filter(r => r.bestE1rm || r.heaviest)
    .sort((a, b) => (b.bestE1rm || b.heaviest || 0) - (a.bestE1rm || a.heaviest || 0));
}

// --- chat UI ----------------------------------------------------------------
const chat = document.getElementById("chat");

function bubble(text, cls) {
  const d = document.createElement("div");
  d.className = "bubble " + cls;
  d.textContent = text;
  chat.appendChild(d);
  scroll();
}

function greet() {
  bubble('Tell me what you did — type or tap 🎙️. I\'ll show a card to confirm before saving.', "them");
}

function scroll() { chat.scrollTop = chat.scrollHeight; }

function submit() {
  const input = document.getElementById("input");
  const msg = input.value.trim();
  if (!msg) return;
  bubble(msg, "mine");
  input.value = "";
  renderCard(parse(msg));
}

function renderCard(p) {
  const card = document.createElement("div");
  card.className = "card";
  const title = p.exercise ? p.exercise.name : (p.exercisePhrase || "Unknown exercise");
  const meta = p.exercise ? `${cap(p.exercise.movement_pattern)} · ${cap(p.exercise.equipment)}` : "";
  card.innerHTML = `
    <h3>${esc(title)}</h3>
    ${meta ? `<div class="meta">${esc(meta)}</div>` : ""}
    ${p.exercise ? "" : `<div class="warn">⚠︎ Couldn't match this exercise — confirm or rename it.</div>`}
    <div class="fields">
      <div class="field"><label>Sets</label><input data-k="sets" inputmode="numeric" value="${p.sets ?? ""}"></div>
      <div class="field"><label>Reps</label><input data-k="reps" inputmode="numeric" value="${p.reps ?? ""}"></div>
      <div class="field"><label>Weight (kg)</label><input data-k="weightKg" inputmode="decimal" value="${p.weightKg ?? ""}"></div>
      <div class="field"><label>RPE</label><input data-k="rpe" inputmode="decimal" value="${p.rpe ?? ""}"></div>
    </div>
    ${p.noteText ? `<div class="note">📝 ${esc(p.noteText)}</div>` : ""}
    ${p.tags.length ? `<div class="tags">${p.tags.map(t => `<span class="tag">${esc(t)}</span>`).join("")}</div>` : ""}
    <div class="actions">
      <button class="btn text" data-act="discard">Discard</button>
      <button class="btn filled" data-act="confirm">Confirm</button>
    </div>`;
  chat.appendChild(card);
  scroll();

  card.querySelector('[data-act="discard"]').onclick = () => card.remove();
  card.querySelector('[data-act="confirm"]').onclick = () => {
    const get = k => card.querySelector(`[data-k="${k}"]`).value;
    const final = {
      ...p,
      sets: parseInt(get("sets")) || 1,
      reps: parseInt(get("reps")) || null,
      weightKg: parseFloat(get("weightKg")) || null,
      rpe: parseFloat(get("rpe")) || null,
    };
    commit(final);
    card.remove();
    let s = `✓ Logged: ${final.exercise ? final.exercise.name : final.exercisePhrase}`;
    if (final.reps) s += ` ${final.sets}×${final.reps}`;
    if (final.weightKg) s += ` @ ${final.weightKg}kg`;
    if (final.rpe) s += ` RPE ${final.rpe}`;
    bubble(s, "logged");
  };
}

// --- persistence (localStorage; mirrors the offline-first Room store) --------
const KEY = "unnamed.sessions.v2";
function loadSessions() { return JSON.parse(localStorage.getItem(KEY) || "[]"); }
function saveSessions(s) { localStorage.setItem(KEY, JSON.stringify(s)); }

function startOfDay(ms) { const d = new Date(ms); d.setHours(0, 0, 0, 0); return d.getTime(); }

function commit(p) {
  const sessions = loadSessions();
  const today = startOfDay(Date.now());
  let ses = sessions.find(s => startOfDay(s.date) === today);
  if (!ses) { ses = { id: uid(), title: null, date: Date.now(), sets: [] }; sessions.push(ses); }

  const exId = p.exercise ? p.exercise.id : null;
  const exName = p.exercise ? p.exercise.name : p.exercisePhrase;
  const existing = ses.sets.filter(x => (x.exId || x.ex) === (exId || exName)).length;
  for (let i = 0; i < p.sets; i++) {
    ses.sets.push({
      id: uid(), exId, ex: exName, setIndex: existing + i + 1, setType: "working",
      reps: p.reps, weightKg: p.weightKg, rpe: p.rpe, fatigue: p.fatigue,
      note: i === 0 ? p.noteText : null, tags: i === 0 ? p.tags : [],
    });
  }
  saveSessions(sessions);
  renderHistory();
  renderProgress();
}

// --- History ----------------------------------------------------------------
function renderHistory() {
  const h = document.getElementById("history");
  const sessions = loadSessions().sort((a, b) => b.date - a.date);
  if (!sessions.length) { h.innerHTML = emptyHTML("No sessions yet.", "Log a set on the Log tab, or:"); wireSample(h); return; }
  h.innerHTML = sessions.map(ses => {
    const sm = summarize(ses);
    return `<div class="session tap" data-id="${ses.id}">
      <div class="s-title">${esc(ses.title || sm.topExerciseName || "Workout")}</div>
      <div class="when">${new Date(ses.date).toLocaleString()}</div>
      <div class="chips">${chip("Volume", fmtVol(sm.totalVolume))}${chip("Sets", sm.workingSets)}${chip("Exercises", sm.exerciseCount)}</div>
    </div>`;
  }).join("");
  h.querySelectorAll(".tap").forEach(el => el.onclick = () => openDetail(el.dataset.id));
}

function openDetail(id) {
  const ses = loadSessions().find(s => s.id === id);
  if (!ses) return;
  const sm = summarize(ses);
  document.getElementById("detail-title").textContent = ses.title || sm.topExerciseName || "Session";
  document.getElementById("detail-body").innerHTML = `
    <div class="when">${new Date(ses.date).toLocaleString()}</div>
    <div class="chips wrap">${chip("Volume", fmtVol(sm.totalVolume))}${chip("Work sets", sm.workingSets)}${chip("Exercises", sm.exerciseCount)}</div>
    ${sm.muscleSets.length ? section("Muscles worked", bars(sm.muscleSets)) : ""}
    ${sm.groups.map(exerciseSection).join("")}`;
  showScreen("detail");
}

function exerciseSection(g) {
  const head = `<div class="chips">${g.topE1rm ? chip("Top e1RM", fmtKg(g.topE1rm)) : ""}${chip("Volume", fmtVol(g.totalVolume))}${chip("Sets", g.workingSets)}</div>`;
  const rows = g.sets.map(({ s, m }) => {
    const trailing = [];
    if (m.e1rm != null) trailing.push("e1RM " + fmtNum(m.e1rm));
    if (s.rpe != null) trailing.push("RPE " + fmtNum(s.rpe));
    if (s.setType !== "working") trailing.push(s.setType);
    const main = (s.reps != null ? s.reps : "—") + (m.load ? " × " + fmtNum(m.load) + " kg" : " reps");
    return `<div class="set-row2"><span class="si">Set ${s.setIndex}</span><span class="sm">${esc(main)}</span><span class="st">${esc(trailing.join(" · "))}</span></div>`;
  }).join("");
  return section(g.name, head + rows);
}

// --- Progress ---------------------------------------------------------------
function renderProgress() {
  const el = document.getElementById("progress-body");
  const sessions = loadSessions();
  const o = overview(sessions);
  if (!o.totalSessions) { el.innerHTML = emptyHTML("Progress", "Log a few sessions and your stats build up here, or:"); wireSample(el); return; }
  el.innerHTML = `
    <div class="sec-t big">All time</div>
    <div class="tiles">${tile("Sessions", o.totalSessions)}${tile("Volume", fmtVol(o.totalVolume))}${tile("Work sets", o.totalWorkingSets)}</div>
    ${section("This week",
      `<div class="tiles">${tile("Sessions", o.weekSessions)}${tile("Volume", fmtVol(o.weekVolume))}${tile("Sets", o.weekWorkingSets)}</div>` +
      (o.weekMuscle.length ? `<div class="sub">Sets per muscle</div>` + bars(o.weekMuscle) : ""))}
    ${o.records.length ? `<div class="sec-t big">Personal records</div>` +
      o.records.map(r => section(r.name,
        `<div class="pr"><div><div class="pr-l">Best e1RM</div><div class="pr-v">${fmtKg(r.bestE1rm)}</div></div>` +
        `<div><div class="pr-l">Heaviest</div><div class="pr-v">${fmtKg(r.heaviest)}</div></div></div>`)).join("") : ""}`;
}

// --- shared render helpers --------------------------------------------------
function section(title, inner) { return `<div class="sec"><div class="sec-t">${esc(title)}</div>${inner}</div>`; }
function chip(l, v) { return `<div class="chip"><div class="chip-v">${esc(String(v))}</div><div class="chip-l">${esc(String(l).toUpperCase())}</div></div>`; }
function tile(l, v) { return `<div class="tile"><div class="tile-v">${esc(String(v))}</div><div class="tile-l">${esc(l)}</div></div>`; }
function bars(items) {
  const max = Math.max(...items.map(i => i.sets), 0.0001);
  return `<div class="bars">` + items.map(i =>
    `<div class="bar-row"><span class="bar-l">${esc(cap(i.muscle))}</span>` +
    `<div class="bar-track"><div class="bar-fill" style="width:${Math.max(6, i.sets / max * 100)}%"></div></div>` +
    `<span class="bar-v">${fmtNum(i.sets)}</span></div>`).join("") + `</div>`;
}
function emptyHTML(title, hint) {
  return `<div class="empty"><div class="empty-t">${esc(title)}</div><p>${esc(hint)}</p>
    <button class="btn filled" id="sample">Load a sample week</button></div>`;
}
function wireSample(container) {
  const b = container.querySelector("#sample");
  if (b) b.onclick = loadSample;
}

// --- format (mirrors ui/format/Format.kt) -----------------------------------
function fmtNum(v) { return Math.abs(v - Math.round(v)) < 0.05 ? String(Math.round(v)) : v.toFixed(1); }
function fmtVol(kg) { if (kg <= 0) return "—"; if (kg >= 10000) return (kg / 1000).toFixed(1) + "k kg"; return Math.round(kg).toLocaleString("en-US") + " kg"; }
function fmtKg(kg) { return kg == null ? "—" : fmtNum(kg) + " kg"; }

// --- sample data ------------------------------------------------------------
function findEx(kw) { return LIBRARY.find(e => e.name.toLowerCase().includes(kw)); }

function loadSample() {
  const day = n => Date.now() - n * 864e5 + 18 * 36e5; // n days ago, ~18:00
  const set = (ex, idx, reps, kg, rpe, type) => ex && ({
    id: uid(), exId: ex.id, ex: ex.name, setIndex: idx, setType: type || "working",
    reps, weightKg: kg, rpe, fatigue: null, note: null, tags: [],
  });
  const exercise = (ex, ...rows) => rows.map((r, i) => set(ex, i + 1, r[0], r[1], r[2], r[3])).filter(Boolean);

  const bench = findEx("bench press");
  const ohp = findEx("overhead press") || findEx("military press") || findEx("shoulder press");
  const tri = findEx("pushdown") || findEx("triceps");
  const dead = findEx("deadlift");
  const pull = findEx("pulldown") || findEx("pull-up") || findEx("pullup");
  const curl = findEx("biceps curl") || findEx("barbell curl") || findEx("curl");
  const squat = findEx("squat");
  const legpress = findEx("leg press");
  const calf = findEx("calf raise") || findEx("calf");

  const sessions = [
    { id: uid(), title: "Push", date: day(5), sets: [
      ...exercise(bench, [5, 40, null, "warmup"], [8, 80, 7], [8, 82.5, 8], [7, 82.5, 9]),
      ...exercise(ohp, [10, 40, 7], [9, 45, 8], [8, 45, 9]),
      ...exercise(tri, [12, 25, 7], [12, 27.5, 8]),
    ].filter(Boolean) },
    { id: uid(), title: "Pull", date: day(3), sets: [
      ...exercise(dead, [5, 60, null, "warmup"], [5, 120, 7], [5, 130, 8], [5, 130, 9]),
      ...exercise(pull, [10, 60, 7], [9, 65, 8], [8, 65, 9]),
      ...exercise(curl, [12, 20, 7], [10, 22.5, 8]),
    ].filter(Boolean) },
    { id: uid(), title: "Legs", date: day(1), sets: [
      ...exercise(squat, [5, 60, null, "warmup"], [8, 100, 7], [8, 105, 8], [7, 105, 9]),
      ...exercise(legpress, [12, 160, 7], [12, 180, 8], [10, 200, 9]),
      ...exercise(calf, [15, 80, 7], [15, 80, 8], [14, 80, 9]),
    ].filter(Boolean) },
  ];
  // Keep any real sessions the user already created.
  const existing = loadSessions();
  saveSessions([...existing, ...sessions]);
  renderHistory();
  renderProgress();
  showScreen("history");
}

// --- voice (Web Speech API; mirrors on-device SpeechRecognizer) -------------
function setupMic() {
  const SR = window.SpeechRecognition || window.webkitSpeechRecognition;
  const mic = document.getElementById("mic");
  if (!SR) { mic.title = "Voice not supported in this browser"; mic.style.opacity = .4; return; }
  const rec = new SR();
  rec.lang = "en-US"; rec.interimResults = false; rec.maxAlternatives = 1;
  mic.onclick = () => { try { rec.start(); mic.classList.add("listening"); } catch (_) {} };
  rec.onresult = e => { document.getElementById("input").value = e.results[0][0].transcript; };
  rec.onend = () => mic.classList.remove("listening");
  rec.onerror = () => mic.classList.remove("listening");
}

// --- navigation -------------------------------------------------------------
const SCREENS = ["log", "history", "detail", "progress"];
const SUBTITLE = { log: "Log a set…", history: "Your sessions", detail: "Session detail", progress: "Stats & progress" };

function showScreen(name) {
  SCREENS.forEach(t => document.getElementById("screen-" + t).classList.toggle("hidden", t !== name));
  // Detail keeps History highlighted in the bottom nav.
  const navName = name === "detail" ? "history" : name;
  document.querySelectorAll(".nav").forEach(b => b.classList.toggle("active", b.dataset.tab === navName));
  document.getElementById("subtitle").textContent = SUBTITLE[name];
}

function setupNav() {
  document.querySelectorAll(".nav").forEach(btn => {
    btn.onclick = () => {
      const tab = btn.dataset.tab;
      if (tab === "history") renderHistory();
      if (tab === "progress") renderProgress();
      showScreen(tab);
    };
  });
  document.getElementById("back").onclick = () => { renderHistory(); showScreen("history"); };
}

// --- helpers ----------------------------------------------------------------
function esc(s) { return String(s).replace(/[&<>"]/g, c => ({ "&": "&amp;", "<": "&lt;", ">": "&gt;", '"': "&quot;" }[c])); }
function cap(s) { return s ? s.replace(/_/g, " ").replace(/\b\w/g, c => c.toUpperCase()) : ""; }
function uid() { return (crypto.randomUUID ? crypto.randomUUID() : "id-" + Math.random().toString(36).slice(2) + Date.now()); }

document.getElementById("send").onclick = submit;
document.getElementById("input").addEventListener("keydown", e => { if (e.key === "Enter") submit(); });
setupMic();
setupNav();
boot();
