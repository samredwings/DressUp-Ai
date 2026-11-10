/**
 * DressUp one-time notebook pairing service (Deno Deploy).
 *
 * The Android client generates a 256-bit random bearer token, embeds it in the
 * notebook launch fragment, and polls /status using Authorization: Bearer.
 * The notebook registers one validated HTTPS tunnel against that token.
 * A ready result is returned once and then deleted from the in-memory store.
 */

const store = new Map();
const TOKEN_PATTERN = /^[a-f0-9]{64}$/;
const TOKEN_TTL_MS = 10 * 60 * 1000;
const MAX_BODY_BYTES = 8 * 1024;
const MAX_ACTIVE_PAIRINGS = 10_000;
const ALLOWED_TUNNEL_SUFFIXES = [
  "ngrok-free.app",
  "ngrok-free.dev",
  "ngrok.io",
  "ngrok.app",
  "trycloudflare.com",
  "loca.lt",
];

function json(data, status = 200) {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      "content-type": "application/json; charset=utf-8",
      "cache-control": "no-store, max-age=0",
      "pragma": "no-cache",
      "x-content-type-options": "nosniff",
    },
  });
}

function tokenFromRequest(req) {
  const match = /^Bearer ([a-f0-9]{64})$/i.exec(req.headers.get("authorization") || "");
  return match?.[1]?.toLowerCase() || null;
}

function validateTunnelUrl(raw) {
  if (typeof raw !== "string" || raw.length > 512) return null;
  let parsed;
  try {
    parsed = new URL(raw);
  } catch {
    return null;
  }

  if (parsed.protocol !== "https:") return null;
  if (parsed.username || parsed.password || parsed.port) return null;
  if (parsed.pathname !== "/" || parsed.search || parsed.hash) return null;

  const host = parsed.hostname.toLowerCase();
  if (!ALLOWED_TUNNEL_SUFFIXES.some((suffix) => host.endsWith(`.${suffix}`))) return null;
  return parsed.origin;
}

function pruneExpired() {
  const now = Date.now();
  for (const [token, value] of store.entries()) {
    if (now - value.createdAt > TOKEN_TTL_MS) store.delete(token);
  }
}

async function readBoundedText(request, maxBytes) {
  const reader = request.body?.getReader();
  if (!reader) return "";
  const chunks = [];
  let total = 0;
  try {
    while (true) {
      const { value, done } = await reader.read();
      if (done) break;
      total += value.byteLength;
      if (total > maxBytes) {
        await reader.cancel();
        throw new RangeError("Request body too large");
      }
      chunks.push(value);
    }
  } finally {
    reader.releaseLock();
  }
  const merged = new Uint8Array(total);
  let offset = 0;
  for (const chunk of chunks) {
    merged.set(chunk, offset);
    offset += chunk.byteLength;
  }
  return new TextDecoder("utf-8", { fatal: true }).decode(merged);
}

Deno.serve(async (req) => {
  pruneExpired();
  const url = new URL(req.url);

  if (req.method === "GET" && url.pathname === "/health") {
    return json({ status: "ok", service: "DressUp pairing", active_pairings: store.size });
  }

  if (req.method === "GET" && url.pathname === "/status") {
    const token = tokenFromRequest(req);
    if (!token) return json({ status: "error", message: "Unauthorized" }, 401);

    const data = store.get(token);
    if (!data) return json({ status: "waiting" });
    if (Date.now() - data.createdAt > TOKEN_TTL_MS) {
      store.delete(token);
      return json({ status: "expired" });
    }

    // One-time retrieval prevents a captured status token being replayed.
    store.delete(token);
    return json({
      status: "ready",
      endpoint_url: data.endpointUrl,
      gpu: data.gpu,
      timestamp: data.createdAt,
    });
  }

  if (req.method === "POST" && url.pathname === "/register") {
    const declaredLength = Number(req.headers.get("content-length") || 0);
    if (declaredLength > MAX_BODY_BYTES) {
      return json({ status: "error", message: "Request body too large" }, 413);
    }

    let raw;
    try {
      raw = await readBoundedText(req, MAX_BODY_BYTES);
    } catch (error) {
      if (error instanceof RangeError) {
        return json({ status: "error", message: "Request body too large" }, 413);
      }
      return json({ status: "error", message: "Invalid request body" }, 400);
    }

    let body;
    try {
      body = JSON.parse(raw);
    } catch {
      return json({ status: "error", message: "Invalid JSON body" }, 400);
    }

    const token = typeof body?.code === "string" ? body.code.toLowerCase() : "";
    if (!TOKEN_PATTERN.test(token)) {
      return json({ status: "error", message: "Invalid pairing token" }, 400);
    }

    const endpointUrl = validateTunnelUrl(body.endpoint_url);
    if (!endpointUrl) {
      return json({ status: "error", message: "Endpoint must be a supported HTTPS tunnel origin" }, 400);
    }

    if (store.has(token)) {
      return json({ status: "error", message: "Pairing token already used" }, 409);
    }
    if (store.size >= MAX_ACTIVE_PAIRINGS) {
      return json({ status: "error", message: "Pairing service is busy" }, 503);
    }

    const gpu = typeof body.gpu === "string" ? body.gpu.slice(0, 80) : "Unknown";
    store.set(token, {
      endpointUrl,
      gpu: gpu.replace(/[\u0000-\u001f\u007f]/g, ""),
      createdAt: Date.now(),
    });

    return json({ status: "ok", message: "Registered" });
  }

  return json({ status: "error", message: "Not found" }, 404);
});
