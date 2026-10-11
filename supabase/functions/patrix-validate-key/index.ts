import "jsr:@supabase/functions-js/edge-runtime.d.ts";
import { createClient } from "npm:@supabase/supabase-js@2";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};
const json = (body: unknown, status = 200) => new Response(JSON.stringify(body), {
  status,
  headers: { ...corsHeaders, "Content-Type": "application/json" },
});
async function sha256(value: string): Promise<string> {
  const bytes = new TextEncoder().encode(value.trim());
  const digest = await crypto.subtle.digest("SHA-256", bytes);
  return [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, "0")).join("");
}
Deno.serve(async (req: Request) => {
  if (req.method === "OPTIONS") return new Response("ok", { headers: corsHeaders });
  if (req.method !== "POST") return json({ ok: false, error: "method_not_allowed" }, 405);
  let payload: { key?: string; device_id?: string; app_version?: string };
  try { payload = await req.json(); } catch { return json({ ok: false, error: "invalid_json" }, 400); }
  const key = typeof payload.key === "string" ? payload.key.trim() : "";
  const deviceId = typeof payload.device_id === "string" ? payload.device_id.trim() : "";
  if (!key || !deviceId || deviceId.length > 128) return json({ ok: false, error: "key_and_device_required" }, 400);
  const admin = createClient(Deno.env.get("SUPABASE_URL")!, Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!, { auth: { persistSession: false, autoRefreshToken: false } });
  const digest = await sha256(key);
  const { data: license, error: lookupError } = await admin.from("patrix_licenses").select("id,status,expires_at,bound_device_id,max_devices,label").eq("key_digest", digest).maybeSingle();
  const reject = async (reason: string, status = 401) => {
    await admin.from("patrix_login_audit").insert({ license_id: license?.id ?? null, device_id: deviceId, outcome: "rejected", reason });
    return json({ ok: false, error: reason }, status);
  };
  if (lookupError || !license) return reject("invalid_key");
  if (license.status !== "active") return reject("key_not_active");
  if (new Date(license.expires_at).getTime() <= Date.now()) return reject("key_expired");
  if (license.bound_device_id && license.bound_device_id !== deviceId) return reject("device_not_authorized", 403);
  if (!license.bound_device_id) {
    const { error } = await admin.from("patrix_licenses").update({ bound_device_id: deviceId, last_seen_at: new Date().toISOString() }).eq("id", license.id).is("bound_device_id", null);
    if (error) return reject("device_binding_failed", 503);
  } else await admin.from("patrix_licenses").update({ last_seen_at: new Date().toISOString() }).eq("id", license.id);
  await admin.from("patrix_login_audit").insert({ license_id: license.id, device_id: deviceId, outcome: "accepted", reason: payload.app_version ?? null });
  return json({ ok: true, app: "Patrix", label: license.label, expires_at: license.expires_at, device_id: deviceId, message: "key_accepted" });
});
