import "jsr:@supabase/functions-js/edge-runtime.d.ts";

const url = Deno.env.get("SUPABASE_URL")!;
const serviceKey = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
const headers = { apikey: serviceKey, Authorization: `Bearer ${serviceKey}`, "Content-Type": "application/json" };

async function sha256(value: string) {
  const data = new TextEncoder().encode(value);
  const digest = await crypto.subtle.digest("SHA-256", data);
  return Array.from(new Uint8Array(digest)).map(b => b.toString(16).padStart(2, "0")).join("");
}
async function db(path: string, init: RequestInit = {}) {
  return fetch(`${url}/rest/v1/${path}`, { ...init, headers: { ...headers, ...(init.headers || {}) } });
}
function json(body: unknown, status = 200) {
  return new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });
}

Deno.serve(async req => {
  if (req.method !== "POST") return json({ error: "POST required" }, 405);
  let body: any;
  try { body = await req.json(); } catch { return json({ error: "Invalid JSON" }, 400); }

  if (body.action === "register") {
    const codeHash = await sha256(String(body.code || ""));
    const check = await db(`liya_pairing_codes?code_hash=eq.${codeHash}&used_at=is.null&expires_at=gt.${encodeURIComponent(new Date().toISOString())}&select=code_hash`);
    const rows = await check.json();
    if (!Array.isArray(rows) || rows.length !== 1) return json({ error: "Invalid or expired pairing code" }, 403);
    const token = crypto.randomUUID() + crypto.randomUUID();
    const tokenHash = await sha256(token);
    const created = await db("liya_devices", { method: "POST", headers: { Prefer: "return=representation" }, body: JSON.stringify({ device_name: String(body.device_name || "Liya Android"), device_key_hash: tokenHash }) });
    if (!created.ok) return json({ error: "Registration failed" }, 500);
    const devices = await created.json();
    await db(`liya_pairing_codes?code_hash=eq.${codeHash}`, { method: "PATCH", body: JSON.stringify({ used_at: new Date().toISOString() }) });
    return json({ device_id: devices[0].id, device_token: token });
  }

  const authToken = (req.headers.get("Authorization") || "").replace(/^Bearer\s+/i, "");
  const token = authToken || req.headers.get("X-Liya-Token") || String(body.device_token || "");
  if (!token) return json({ error: "Missing device token" }, 401);
  const tokenHash = await sha256(token);
  const found = await db(`liya_devices?device_key_hash=eq.${tokenHash}&enabled=eq.true&select=id`);
  const devices = await found.json();
  if (!Array.isArray(devices) || devices.length !== 1) return json({ error: "Unknown device" }, 403);
  const deviceId = devices[0].id;
  await db(`liya_devices?id=eq.${deviceId}`, { method: "PATCH", body: JSON.stringify({ last_seen_at: new Date().toISOString() }) });

  if (body.action === "poll") {
    const now = new Date().toISOString();
    const fields = "id,instruction,attachment_url,caption,target_package,approved,silent,attempt_count,max_attempts";
    const result = await db(`liya_tasks?device_id=eq.${deviceId}&status=eq.pending&or=(next_attempt_at.is.null,next_attempt_at.lte.${encodeURIComponent(now)})&select=${fields}&order=created_at.asc&limit=1`);
    const tasks = await result.json();
    if (!Array.isArray(tasks) || tasks.length === 0) return json({ task: null });
    const task = tasks[0];
    const attempt = Number(task.attempt_count || 0) + 1;
    const claim = await db(`liya_tasks?id=eq.${task.id}&device_id=eq.${deviceId}&status=eq.pending`, {
      method: "PATCH", headers: { Prefer: "return=representation" },
      body: JSON.stringify({ status: "running", started_at: now, finished_at: null, attempt_count: attempt, next_attempt_at: null, outcome: "running" })
    });
    const claimed = await claim.json();
    if (!claim.ok || !Array.isArray(claimed) || claimed.length !== 1) return json({ task: null });
    return json({ task: { ...task, attempt_count: attempt } });
  }

  if (body.action === "report") {
    const requested = ["completed", "failed", "needs_confirmation"].includes(body.status) ? body.status : "failed";
    const taskResponse = await db(`liya_tasks?id=eq.${Number(body.task_id)}&device_id=eq.${deviceId}&select=id,attempt_count,max_attempts,status`);
    const tasks = await taskResponse.json();
    if (!Array.isArray(tasks) || tasks.length !== 1) return json({ error: "Task not found" }, 404);
    const task = tasks[0];
    const resultText = String(body.result || "").slice(0, 4000);
    const packageName = String(body.package_name || "").slice(0, 300);
    const screen = String(body.screen || "").slice(0, 12000);
    const finishedAt = new Date().toISOString();

    await db("liya_task_attempts", { method: "POST", body: JSON.stringify({
      task_id: task.id, device_id: deviceId, attempt_no: task.attempt_count,
      status: requested, result: resultText, package_name: packageName, screen,
      started_at: null, finished_at: finishedAt
    }) });

    const retry = requested === "failed" && Number(task.attempt_count) < Number(task.max_attempts);
    const nextStatus = retry ? "pending" : requested;
    const patch: Record<string, unknown> = {
      status: nextStatus,
      outcome: retry ? "retrying" : requested === "completed" ? "success" : requested,
      result: resultText,
      last_error: requested === "failed" ? resultText : null,
      last_package: packageName,
      last_screen: screen,
      finished_at: retry ? null : finishedAt,
      next_attempt_at: retry ? new Date(Date.now() + 15_000).toISOString() : null,
    };
    const update = await db(`liya_tasks?id=eq.${task.id}&device_id=eq.${deviceId}`, { method: "PATCH", body: JSON.stringify(patch) });
    return update.ok ? json({ ok: true, status: nextStatus, retry_scheduled: retry }) : json({ error: "Report failed" }, 500);
  }
  return json({ error: "Unknown action" }, 400);
});
