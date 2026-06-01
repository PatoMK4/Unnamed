// Unnamed — web preview of the Android logging flow.
// Ports the app's ExerciseLibrary + SetParser logic to the browser.

let LIBRARY = [];
let BY_PHRASE = new Map(); // lowercased alias/name -> exercise

const PAIN_WORDS = ["pain","hurt","tweak","sore","heavy","bothered","pinch","ache","uncomfortable"];
const BODY_PARTS = ["lower back","shoulder","knee","elbow","wrist","hip","back","neck","ankle","bicep","hamstring"];

async function boot() {
  const res = await fetch("exercises.json");
  LIBRARY = await res.json();
  for (const e of LIBRARY) {
    BY_PHRASE.set(e.name.toLowerCase(), e);
    for (const a of (e.aliases || [])) {
      const k = a.toLowerCase();
      if (!BY_PHRASE.has(k)) BY_PHRASE.set(k, e);
    }
  }
  greet();
  renderHistory();
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
  const parsed = parse(msg);
  renderCard(parsed);
}

function renderCard(p) {
  const card = document.createElement("div");
  card.className = "card";
  const title = p.exercise ? p.exercise.name : (p.exercisePhrase || "Unknown exercise");
  const meta = p.exercise
    ? `${cap(p.exercise.movement_pattern)} · ${cap(p.exercise.equipment)}`
    : "";
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

// --- persistence (localStorage; mirrors the offline-first idea) -------------
const KEY = "unnamed.session.v1";
function loadSession() { return JSON.parse(localStorage.getItem(KEY) || "null"); }
function saveSession(s) { localStorage.setItem(KEY, JSON.stringify(s)); }

function commit(p) {
  let s = loadSession();
  if (!s) s = { date: Date.now(), sets: [] };
  for (let i = 0; i < p.sets; i++) {
    s.sets.push({
      ex: p.exercise ? p.exercise.name : p.exercisePhrase,
      reps: p.reps, weightKg: p.weightKg, rpe: p.rpe, fatigue: p.fatigue,
      note: i === 0 ? p.noteText : null, tags: i === 0 ? p.tags : [],
    });
  }
  saveSession(s);
  renderHistory();
}

function renderHistory() {
  const h = document.getElementById("history");
  const s = loadSession();
  if (!s || !s.sets.length) {
    h.innerHTML = `<div class="empty">No sets yet.<br>Log something on the Log tab and it'll appear here.</div>`;
    return;
  }
  const rows = s.sets.map(x => {
    let d = "";
    if (x.reps) d += `${x.reps} reps`;
    if (x.weightKg) d += ` · ${x.weightKg}kg`;
    if (x.rpe) d += ` · RPE ${x.rpe}`;
    return `<div class="set-row"><span class="ex">${esc(x.ex)}</span><span>${esc(d || "—")}</span></div>`;
  }).join("");
  h.innerHTML = `<div class="session">
      <div class="when">${new Date(s.date).toLocaleString()} · ${s.sets.length} sets</div>
      ${rows}
    </div>`;
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

// --- tabs -------------------------------------------------------------------
function setupTabs() {
  document.querySelectorAll(".nav").forEach(btn => {
    btn.onclick = () => {
      document.querySelectorAll(".nav").forEach(b => b.classList.remove("active"));
      btn.classList.add("active");
      const tab = btn.dataset.tab;
      ["log","history","coach"].forEach(t =>
        document.getElementById("screen-" + t).classList.toggle("hidden", t !== tab));
      document.getElementById("subtitle").textContent =
        tab === "log" ? "Log a set…" : tab === "history" ? "Your sets" : "AI Coach";
    };
  });
}

// --- helpers ----------------------------------------------------------------
function esc(s) { return String(s).replace(/[&<>"]/g, c => ({ "&":"&amp;","<":"&lt;",">":"&gt;",'"':"&quot;" }[c])); }
function cap(s) { return s ? s.replace(/_/g, " ").replace(/\b\w/g, c => c.toUpperCase()) : ""; }

document.getElementById("send").onclick = submit;
document.getElementById("input").addEventListener("keydown", e => { if (e.key === "Enter") submit(); });
setupMic();
setupTabs();
boot();
