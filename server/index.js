/**
 * MARKET-STANDARD VIRTUAL TRY-ON SERVER (V2)
 *
 * Upgrades from V1:
 * 1. Multi-backend diffusion support (Replicate, Fal.ai, RunPod)
 * 2. Bounded in-memory queue (Redis adapter remains an upgrade)
 * 3. Verified provider webhook
 * 4. Result caching with CDN
 * 5. Progress tracking
 * 6. Batch processing deliberately disabled until an actual queue implementation exists
 * 7. Bearer authentication and request rate limiting
 */

const express = require("express");
const axios = require("axios");
const crypto = require("crypto");

const app = express();

// ============================================================================
// CONFIGURATION
// ============================================================================

const CONFIG = {
  // Diffusion backends (priority order)
  BACKENDS: {
    REPLICATE: {
      enabled: !!process.env.REPLICATE_API_KEY,
      apiKey: process.env.REPLICATE_API_KEY,
      baseUrl: "https://api.replicate.com/v1",
      // Recommended models for virtual try-on
      models: {
        ootdiffusion: process.env.REPLICATE_OOTDIFFUSION_VERSION || "",
        idmVton: process.env.REPLICATE_IDMVTON_VERSION || "",
        catvton: process.env.REPLICATE_CATVTON_VERSION || ""
      }
    },
    FAL_AI: {
      enabled: !!process.env.FAL_KEY,
      apiKey: process.env.FAL_KEY,
      baseUrl: "https://queue.fal.run",
      models: {
        idmVton: "fal-ai/idm-vton",
        ootdiffusion: "fal-ai/ootdiffusion"
      }
    },
    RUNPOD: {
      enabled: !!process.env.RUNPOD_API_KEY,
      apiKey: process.env.RUNPOD_API_KEY,
      endpointId: process.env.RUNPOD_ENDPOINT_ID,
      baseUrl: "https://api.runpod.ai/v2"
    }
  },

  // Processing settings
  MAX_CONCURRENT: Math.max(1, Math.min(20, Number.parseInt(process.env.MAX_CONCURRENT || "3", 10) || 3)),
  RESULT_TTL_SECONDS: Math.max(60, Math.min(86400, Number.parseInt(process.env.RESULT_TTL || "3600", 10) || 3600)),
  REPLICATE_WEBHOOK_SECRET: process.env.REPLICATE_WEBHOOK_SECRET || "",

  // Rate limiting
  RATE_LIMIT: {
    windowMs: 15 * 60 * 1000, // 15 minutes
    maxRequests: 100
  }
};

// ============================================================================
// IN-MEMORY QUEUE (Replace with Redis in production)
// ============================================================================

const jobQueue = new Map(); // jobId -> job data
const resultCache = new Map(); // cacheKey -> { result, timestamp }
const rateBuckets = new Map();
const seenWebhooks = new Map();
let activeSubmissions = 0;

// Retain the exact JSON bytes for Replicate webhook signature verification.
// Compression is disabled to avoid compressed-body resource exhaustion.
function requireApiToken(req, res, next) {
  const expected = process.env.API_ACCESS_TOKEN || "";
  if (Buffer.byteLength(expected) < 32) {
    return res.status(503).json({ error: "Server API authentication is not configured" });
  }
  const match = /^Bearer (.+)$/.exec(req.headers.authorization || "");
  if (!match) return res.status(401).json({ error: "Unauthorized" });
  const supplied = Buffer.from(match[1], "utf8");
  const expectedBytes = Buffer.from(expected, "utf8");
  if (supplied.length !== expectedBytes.length || !crypto.timingSafeEqual(supplied, expectedBytes)) {
    return res.status(401).json({ error: "Unauthorized" });
  }
  next();
}

// Authenticate protected API requests before parsing potentially large JSON bodies.
app.use((req, res, next) => {
  if (req.path === "/tryon" || req.path === "/tryon/batch" ||
      req.path.startsWith("/status/") || req.path === "/backends") {
    return requireApiToken(req, res, next);
  }
  next();
});

app.use(express.json({
  limit: "18mb",
  inflate: false,
  verify: (req, res, buffer) => {
    if (req.originalUrl === "/webhook/replicate") req.rawBody = Buffer.from(buffer);
  }
}));

function rateLimit(req, res, next) {
  const now = Date.now();
  const ip = req.ip || req.socket.remoteAddress || "unknown";
  const bucket = rateBuckets.get(ip) || [];
  const recent = bucket.filter(timestamp => now - timestamp < CONFIG.RATE_LIMIT.windowMs);
  if (recent.length >= CONFIG.RATE_LIMIT.maxRequests) {
    return res.status(429).json({ error: "Rate limit exceeded" });
  }
  recent.push(now);
  rateBuckets.set(ip, recent);
  next();
}

function isBoundedImageDataUri(value) {
  if (typeof value !== "string" || value.length > 8 * 1024 * 1024) return false;
  const match = /^data:image\/(?:jpeg|png|webp);base64,([A-Za-z0-9+/]+={0,2})$/.exec(value);
  if (!match) return false;
  const encoded = match[1];
  const decodedLength = Math.floor(encoded.length * 3 / 4);
  return decodedLength > 0 && decodedLength <= 6 * 1024 * 1024;
}

function validateTryOnBody(body) {
  if (!body || typeof body !== "object" || Array.isArray(body)) return "Invalid JSON body";
  if (!isBoundedImageDataUri(body.human_img) || !isBoundedImageDataUri(body.garment_img)) {
    return "human_img and garment_img must be bounded JPEG, PNG or WebP data URIs";
  }
  if (!new Set(["upper_body", "lower_body", "dresses", "upper", "lower"]).has(body.category || "upper_body")) {
    return "Invalid category";
  }
  if (body.num_samples !== undefined && (!Number.isInteger(body.num_samples) || body.num_samples < 1 || body.num_samples > 4)) {
    return "num_samples must be an integer between 1 and 4";
  }
  if (body.garment_description !== undefined && (typeof body.garment_description !== "string" || body.garment_description.length > 500)) {
    return "garment_description is too long";
  }
  if (body.backend !== undefined && !new Set(["auto", "replicate", "falai", "runpod"]).has(body.backend)) {
    return "Invalid backend";
  }
  return null;
}

function generateJobId() {
  return crypto.randomUUID();
}

function getCacheKey(data) {
  const hash = crypto.createHash("sha256");
  hash.update(JSON.stringify(data));
  return hash.digest("hex");
}

// ============================================================================
// DIFFUSION BACKEND HANDLERS
// ============================================================================

/**
 * Replicate handler - Best for open-source models (OOTDiffusion, IDM-VTON)
 */
async function processWithReplicate(data, jobId) {
  const backend = CONFIG.BACKENDS.REPLICATE;
  const modelKey = data.category === "dresses" ? "ootdiffusion" : "idmVton";
  const modelVersion = backend.models[modelKey];
  if (!modelVersion || !/^[a-f0-9]{64}$/i.test(modelVersion)) {
    throw new Error(`A valid Replicate version hash is required for ${modelKey}`);
  }
  const publicUrl = process.env.PUBLIC_URL;
  let webhookUrl;
  try {
    const parsedPublicUrl = new URL(publicUrl);
    if (parsedPublicUrl.protocol !== "https:" || parsedPublicUrl.username || parsedPublicUrl.password) {
      throw new Error("Invalid public URL");
    }
    webhookUrl = new URL("/webhook/replicate", parsedPublicUrl.origin).toString();
  } catch {
    throw new Error("PUBLIC_URL must be set to this server's HTTPS origin for Replicate webhooks");
  }

  // Create prediction
  const response = await axios.post(
    `${backend.baseUrl}/predictions`,
    {
      version: modelVersion,
      input: {
        human_img: data.human_img,
        garment_img: data.garment_img,
        category: data.category || "upper_body",
        garment_description: data.garment_description || "",
        num_samples: data.num_samples || 1,
        seed: data.seed || Math.floor(Math.random() * 1000000)
      },
      webhook: webhookUrl,
      webhook_events_filter: ["completed"]
    },
    {
      headers: {
        Authorization: `Token ${backend.apiKey}`,
        "Content-Type": "application/json"
      }
    }
  );

  const prediction = response.data;

  // Store job reference
  jobQueue.set(jobId, {
    id: jobId,
    status: "processing",
    backend: "replicate",
    predictionId: prediction.id,
    createdAt: Date.now(),
    result: null,
    error: null
  });

  return prediction.id;
}

/**
 * Fal.ai handler - Fastest inference, great for production
 */
async function processWithFalAi(data, jobId) {
  const backend = CONFIG.BACKENDS.FAL_AI;
  const modelPath = backend.models.idmVton;

  const response = await axios.post(
    `${backend.baseUrl}/${modelPath}`,
    {
      image_url: data.human_img,
      garment_image_url: data.garment_img,
      category: data.category || "upper_body",
      num_samples: data.num_samples || 1,
      guidance_scale: data.guidance_scale || 2.0,
      timesteps: data.timesteps || 50
    },
    {
      headers: {
        Authorization: `Key ${backend.apiKey}`,
        "Content-Type": "application/json"
      }
    }
  );

  const { request_id } = response.data;

  jobQueue.set(jobId, {
    id: jobId,
    status: "processing",
    backend: "falai",
    requestId: request_id,
    createdAt: Date.now(),
    result: null,
    error: null
  });

  // Start polling for result
  pollFalAiResult(jobId, request_id);

  return request_id;
}

/**
 * RunPod handler - Cheapest GPU rental for custom models
 */
async function processWithRunPod(data, jobId) {
  const backend = CONFIG.BACKENDS.RUNPOD;

  const response = await axios.post(
    `${backend.baseUrl}/${backend.endpointId}/run`,
    {
      input: {
        human_img: data.human_img,
        garment_img: data.garment_img,
        category: data.category || "upper_body",
        num_samples: data.num_samples || 1
      }
    },
    {
      headers: {
        Authorization: `Bearer ${backend.apiKey}`,
        "Content-Type": "application/json"
      }
    }
  );

  const { id } = response.data;

  jobQueue.set(jobId, {
    id: jobId,
    status: "processing",
    backend: "runpod",
    jobId: id,
    createdAt: Date.now(),
    result: null,
    error: null
  });

  pollRunPodResult(jobId, id);

  return id;
}

// ============================================================================
// POLLING HELPERS
// ============================================================================

async function pollFalAiResult(jobId, requestId) {
  const backend = CONFIG.BACKENDS.FAL_AI;
  const maxAttempts = 60;
  let attempts = 0;

  const interval = setInterval(async () => {
    try {
      const response = await axios.get(
        `${backend.baseUrl}/fal-ai/idm-vton/requests/${requestId}`,
        { headers: { Authorization: `Key ${backend.apiKey}` } }
      );

      const data = response.data;

      if (data.images && data.images.length > 0) {
        clearInterval(interval);
        const job = jobQueue.get(jobId);
        if (job) {
          job.status = "completed";
          job.result = {
            images: data.images,
            seed: data.seed,
            timesteps: data.timesteps
          };
          job.completedAt = Date.now();
          jobQueue.set(jobId, job);
        }
      }

      attempts++;
      if (attempts >= maxAttempts) {
        clearInterval(interval);
        const job = jobQueue.get(jobId);
        if (job && job.status === "processing") {
          job.status = "failed";
          job.error = "Polling timeout";
          jobQueue.set(jobId, job);
        }
      }
    } catch (error) {
      console.error(`Fal.ai poll error for ${jobId}:`, error.message);
    }
  }, 2000);
}

async function pollRunPodResult(jobId, runpodJobId) {
  const backend = CONFIG.BACKENDS.RUNPOD;
  const maxAttempts = 60;
  let attempts = 0;

  const interval = setInterval(async () => {
    try {
      const response = await axios.get(
        `${backend.baseUrl}/${backend.endpointId}/status/${runpodJobId}`,
        { headers: { Authorization: `Bearer ${backend.apiKey}` } }
      );

      const data = response.data;

      if (data.status === "COMPLETED") {
        clearInterval(interval);
        const job = jobQueue.get(jobId);
        if (job) {
          job.status = "completed";
          job.result = data.output;
          job.completedAt = Date.now();
          jobQueue.set(jobId, job);
        }
      } else if (data.status === "FAILED") {
        clearInterval(interval);
        const job = jobQueue.get(jobId);
        if (job) {
          job.status = "failed";
          job.error = data.error || "RunPod job failed";
          jobQueue.set(jobId, job);
        }
      }

      attempts++;
      if (attempts >= maxAttempts) {
        clearInterval(interval);
        const job = jobQueue.get(jobId);
        if (job && job.status === "processing") {
          job.status = "failed";
          job.error = "Polling timeout";
          jobQueue.set(jobId, job);
        }
      }
    } catch (error) {
      console.error(`RunPod poll error for ${jobId}:`, error.message);
    }
  }, 3000);
}

// ============================================================================
// ROUTES
// ============================================================================

/**
 * Health check
 */
app.get("/health", (req, res) => {
  const backends = {
    replicate: CONFIG.BACKENDS.REPLICATE.enabled,
    falai: CONFIG.BACKENDS.FAL_AI.enabled,
    runpod: CONFIG.BACKENDS.RUNPOD.enabled
  };

  const activeBackends = Object.entries(backends)
    .filter(([, v]) => v)
    .map(([k]) => k);

  res.json({
    status: "ok",
    version: "2.1-secure",
    activeBackends,
    queueSize: jobQueue.size,
    cacheSize: resultCache.size
  });
});

/**
 * Submit a try-on job
 * POST /tryon
 */
app.post("/tryon", requireApiToken, rateLimit, async (req, res) => {
  let submissionReserved = false;
  try {
    const {
      human_img,
      garment_img,
      category = "upper_body",
      backend: preferredBackend = "auto",
      num_samples = 1,
      garment_description = "",
      guidance_scale = 2.0,
      seed
    } = req.body || {};

    const validationError = validateTryOnBody(req.body);
    if (validationError) return res.status(400).json({ error: validationError });

    const activeJobs = [...jobQueue.values()].filter(job => job.status === "processing").length;
    if (activeJobs + activeSubmissions >= CONFIG.MAX_CONCURRENT) {
      return res.status(429).json({ error: "Server is at its concurrent job limit" });
    }
    if (jobQueue.size >= 1000) {
      return res.status(503).json({ error: "Job store is full; retry later" });
    }
    activeSubmissions += 1;
    submissionReserved = true;

    if (typeof guidance_scale !== "number" || !Number.isFinite(guidance_scale) || guidance_scale < 0 || guidance_scale > 20) {
      return res.status(400).json({ error: "guidance_scale must be between 0 and 20" });
    }

    // Check cache
    const cacheKey = getCacheKey(req.body);
    const cached = resultCache.get(cacheKey);
    if (cached && Date.now() - cached.timestamp < CONFIG.RESULT_TTL_SECONDS * 1000) {
      return res.json({
        jobId: "cached",
        status: "completed",
        cached: true,
        result: cached.result
      });
    }

    const jobId = generateJobId();

    // Select backend
    let backendName = preferredBackend;
    if (backendName === "auto") {
      if (CONFIG.BACKENDS.FAL_AI.enabled) backendName = "falai";
      else if (CONFIG.BACKENDS.REPLICATE.enabled) backendName = "replicate";
      else if (CONFIG.BACKENDS.RUNPOD.enabled) backendName = "runpod";
      else {
        return res.status(503).json({ error: "No diffusion backends configured" });
      }
    }

    // Submit to selected backend
    let externalId;
    switch (backendName) {
      case "replicate":
        if (!CONFIG.BACKENDS.REPLICATE.enabled) {
          return res.status(503).json({ error: "Replicate not configured" });
        }
        externalId = await processWithReplicate(req.body, jobId);
        break;
      case "falai":
        if (!CONFIG.BACKENDS.FAL_AI.enabled) {
          return res.status(503).json({ error: "Fal.ai not configured" });
        }
        externalId = await processWithFalAi(req.body, jobId);
        break;
      case "runpod":
        if (!CONFIG.BACKENDS.RUNPOD.enabled) {
          return res.status(503).json({ error: "RunPod not configured" });
        }
        externalId = await processWithRunPod(req.body, jobId);
        break;
      default:
        return res.status(400).json({ error: `Unknown backend: ${backendName}` });
    }

    const submittedJob = jobQueue.get(jobId);
    if (submittedJob) {
      submittedJob.cacheKey = cacheKey;
      jobQueue.set(jobId, submittedJob);
    }

    res.status(202).json({
      jobId,
      status: "processing",
      backend: backendName,
      externalId,
      checkStatusUrl: `/status/${jobId}`
    });

  } catch (error) {
    console.error("Try-on submission failed:", error.message);
    res.status(500).json({
      error: "Try-on submission failed"
    });
  } finally {
    if (submissionReserved) activeSubmissions = Math.max(0, activeSubmissions - 1);
  }
});

/**
 * Check job status
 * GET /status/:jobId
 */
app.get("/status/:jobId", requireApiToken, rateLimit, (req, res) => {
  const { jobId } = req.params;
  const job = jobQueue.get(jobId);

  if (!job) {
    return res.status(404).json({ error: "Job not found" });
  }

  const response = {
    jobId: job.id,
    status: job.status,
    backend: job.backend,
    createdAt: job.createdAt,
    completedAt: job.completedAt || null,
    elapsedMs: job.completedAt ? job.completedAt - job.createdAt : Date.now() - job.createdAt
  };

  if (job.status === "completed") {
    response.result = job.result;
    // Cache the result
    const cacheKey = job.cacheKey;
    if (cacheKey) {
      if (!resultCache.has(cacheKey) && resultCache.size >= 500) {
        const oldestKey = resultCache.keys().next().value;
        if (oldestKey !== undefined) resultCache.delete(oldestKey);
      }
      resultCache.set(cacheKey, {
        result: job.result,
        timestamp: Date.now()
      });
    }
  } else if (job.status === "failed") {
    response.error = job.error;
  }

  res.json(response);
});

/**
 * Replicate webhook
 * POST /webhook/replicate
 */
app.post("/webhook/replicate", (req, res) => {
  const secret = CONFIG.REPLICATE_WEBHOOK_SECRET;
  const webhookId = req.headers["webhook-id"];
  const webhookTimestamp = req.headers["webhook-timestamp"];
  const signatures = req.headers["webhook-signature"];
  const timestamp = Number(webhookTimestamp);
  const rawBody = req.rawBody;

  if (!secret.startsWith("whsec_") || !webhookId || !webhookTimestamp || !signatures || !Buffer.isBuffer(rawBody)) {
    return res.status(503).send("Webhook verification is not configured");
  }
  if (!Number.isFinite(timestamp) || Math.abs(Date.now() / 1000 - timestamp) > 300) {
    return res.status(401).send("Stale webhook");
  }

  const signingKey = Buffer.from(secret.slice("whsec_".length), "base64");
  if (signingKey.length === 0) return res.status(503).send("Webhook verification is not configured");
  const signedPayload = Buffer.concat([
    Buffer.from(`${webhookId}.${webhookTimestamp}.`, "utf8"),
    rawBody
  ]);
  const expectedSignature = crypto.createHmac("sha256", signingKey).update(signedPayload).digest("base64");
  const signatureMatched = String(signatures).split(" ").some((part) => {
    const [version, candidate] = part.split(",", 2);
    if (version !== "v1" || !candidate) return false;
    const candidateBytes = Buffer.from(candidate, "utf8");
    const expectedBytes = Buffer.from(expectedSignature, "utf8");
    return candidateBytes.length === expectedBytes.length && crypto.timingSafeEqual(candidateBytes, expectedBytes);
  });
  if (!signatureMatched) return res.status(401).send("Unauthorized");

  // Reject replayed event identifiers for the signature tolerance window.
  if (seenWebhooks.has(webhookId)) return res.sendStatus(200);
  seenWebhooks.set(webhookId, Date.now());
  let data;
  try {
    data = JSON.parse(rawBody.toString("utf8"));
  } catch {
    return res.status(400).send("Invalid webhook payload");
  }

  // Find job by prediction ID
  for (const [jobId, job] of jobQueue.entries()) {
    if (job.predictionId === data.id) {
      if (data.status === "succeeded") {
        job.status = "completed";
        job.result = { images: data.output };
        job.completedAt = Date.now();

      } else if (data.status === "failed") {
        job.status = "failed";
        job.error = data.error?.detail || "Replicate prediction failed";
      }
      jobQueue.set(jobId, job);
      break;
    }
  }

  res.sendStatus(200);
});

/**
 * Batch try-on
 * POST /tryon/batch
 */
app.post("/tryon/batch", requireApiToken, rateLimit, async (req, res) => {
  try {
    const { items } = req.body || {};

    if (!Array.isArray(items) || items.length === 0) {
      return res.status(400).json({ error: "items must be a non-empty array" });
    }

    if (items.length > 10) {
      return res.status(400).json({ error: "Maximum 10 items per batch" });
    }

    return res.status(501).json({ error: "Batch processing is not implemented in this build" });

  } catch (error) {
    res.status(500).json({ error: error.message });
  }
});

/**
 * List available backends and models
 * GET /backends
 */
app.get("/backends", requireApiToken, rateLimit, (req, res) => {
  const backends = {};

  if (CONFIG.BACKENDS.REPLICATE.enabled) {
    backends.replicate = {
      models: Object.keys(CONFIG.BACKENDS.REPLICATE.models),
      recommended: "ootdiffusion"
    };
  }

  if (CONFIG.BACKENDS.FAL_AI.enabled) {
    backends.falai = {
      models: Object.keys(CONFIG.BACKENDS.FAL_AI.models),
      recommended: "idmVton"
    };
  }

  if (CONFIG.BACKENDS.RUNPOD.enabled) {
    backends.runpod = {
      models: ["custom"],
      recommended: "custom"
    };
  }

  res.json({ backends });
});

// ============================================================================
// CLEANUP
// ============================================================================

// Clean old results from cache every 10 minutes
setInterval(() => {
  const now = Date.now();
  for (const [key, value] of resultCache.entries()) {
    if (now - value.timestamp > CONFIG.RESULT_TTL_SECONDS * 1000) {
      resultCache.delete(key);
    }
  }

  for (const [ip, timestamps] of rateBuckets.entries()) {
    const recent = timestamps.filter(timestamp => now - timestamp < CONFIG.RATE_LIMIT.windowMs);
    if (recent.length) rateBuckets.set(ip, recent);
    else rateBuckets.delete(ip);
  }

  for (const [webhookId, timestamp] of seenWebhooks.entries()) {
    if (now - timestamp > 10 * 60 * 1000) seenWebhooks.delete(webhookId);
  }

  // Clean old completed jobs
  for (const [jobId, job] of jobQueue.entries()) {
    if (job.status !== "processing" && now - (job.completedAt || job.createdAt) > 24 * 60 * 60 * 1000) {
      jobQueue.delete(jobId);
    }
  }
}, 10 * 60 * 1000);

// ============================================================================
// START SERVER
// ============================================================================

const PORT = process.env.PORT || 3000;
app.listen(PORT, () => {
  console.log(`=== Virtual Try-On Server v2.0 (Market Standard) ===`);
  console.log(`Port: ${PORT}`);
  console.log(`Backends:`);
  console.log(`  Replicate: ${CONFIG.BACKENDS.REPLICATE.enabled ? "ON" : "OFF"}`);
  console.log(`  Fal.ai:    ${CONFIG.BACKENDS.FAL_AI.enabled ? "ON" : "OFF"}`);
  console.log(`  RunPod:    ${CONFIG.BACKENDS.RUNPOD.enabled ? "ON" : "OFF"}`);
  console.log(`====================================================`);
});

module.exports = app;
