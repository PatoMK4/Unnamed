// ai-coach — Supabase Edge Function (SCAFFOLD, not yet deployed)
//
// Purpose: keep the Anthropic API key server-side. The Android app calls this
// function with the user's auth token; the function verifies the user, gathers
// their data (RLS-scoped), calls Claude, and returns the reply. The API key
// NEVER ships in the app.
//
// STATUS: inert by design. Until ANTHROPIC_API_KEY is set as a function secret,
// it returns 501 so nothing incurs paid API usage. Per the plan, we wire the
// key only once the rest of the app is in place.
//
// Deploy later with:  supabase functions deploy ai-coach
// Set the key later:  supabase secrets set ANTHROPIC_API_KEY=sk-ant-...
//
// Recommended models (see CLAUDE knowledge): Haiku for cheap log parsing,
// Sonnet for coaching reasoning.

import { createClient } from "jsr:@supabase/supabase-js@2";

const MODEL_PARSE = "claude-haiku-4-5-20251001"; // cheap: natural language -> structured log
const MODEL_COACH = "claude-sonnet-4-6";         // reasoning: analyst / coaching

Deno.serve(async (req: Request) => {
  if (req.method !== "POST") {
    return json({ error: "POST only" }, 405);
  }

  const apiKey = Deno.env.get("ANTHROPIC_API_KEY");
  if (!apiKey) {
    // Intentional: no key configured yet -> do not call any paid API.
    return json(
      { error: "AI not activated yet. Set ANTHROPIC_API_KEY to enable." },
      501,
    );
  }

  // Verify the caller via their Supabase JWT (RLS-scoped data access).
  const authHeader = req.headers.get("Authorization") ?? "";
  const supabase = createClient(
    Deno.env.get("SUPABASE_URL")!,
    Deno.env.get("SUPABASE_ANON_KEY")!,
    { global: { headers: { Authorization: authHeader } } },
  );
  const { data: { user }, error } = await supabase.auth.getUser();
  if (error || !user) return json({ error: "Unauthorized" }, 401);

  // TODO (when AI phase begins):
  //  1. Parse the request: { mode: "parse" | "coach", message, context? }
  //  2. For "parse": prompt MODEL_PARSE to map the message onto structured
  //     sets + notes (the app confirms before committing).
  //  3. For "coach": gather the user's RLS-scoped data (sessions, sets, notes,
  //     nutrition, injuries) and prompt MODEL_COACH. Enable prompt caching.
  //  4. Return the structured result.
  return json({ error: "Not implemented yet (scaffold)." }, 501);
});

function json(body: unknown, status = 200): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json" },
  });
}
