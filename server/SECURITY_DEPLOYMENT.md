# Express API secure deployment

`server/index.js` is a separate API backend; it is not the Deno notebook-pairing service. Protected routes fail closed until a bearer token of at least 32 bytes is configured.

## Required environment

Generate a unique API token outside source control, for example:

```sh
openssl rand -hex 32
```

Set:

- `API_ACCESS_TOKEN` — required; send it as `Authorization: Bearer <token>` from trusted clients.
- `PUBLIC_URL` — this Express service's public HTTPS origin; required when using Replicate webhooks.
- `REPLICATE_API_KEY` — optional provider credential.
- `REPLICATE_WEBHOOK_SECRET` — optional Replicate `whsec_...` signing secret; webhook processing fails closed unless correctly configured.
- `REPLICATE_IDMVTON_VERSION` / `REPLICATE_OOTDIFFUSION_VERSION` — exact 64-character provider version hashes; placeholder versions have intentionally been removed.
- `FAL_KEY` — optional Fal.ai provider credential.
- `RUNPOD_API_KEY` and `RUNPOD_ENDPOINT_ID` — optional RunPod configuration.

Never commit real values or include them in logs, screenshots, or support bundles.

## Security behavior

- API-token verification occurs before JSON body parsing and uses constant-time comparison.
- JSON requests are size limited and compressed request bodies are rejected.
- Try-on input must be bounded JPEG/PNG/WebP base64 data; categories, sample counts and descriptions are validated.
- API routes are rate limited and concurrent work is capped.
- User-supplied webhook destinations are not accepted, avoiding callback SSRF.
- Replicate webhook signatures use the provider's `webhook-id`, `webhook-timestamp`, and `webhook-signature` headers, a five-minute timestamp window and replay detection.
- `POST /tryon/batch` returns `501` until actual batch queueing exists; it no longer claims to create placeholder jobs.

## Remaining production work

The job queue and result cache are process-local `Map` objects. They are not durable or shared across multiple replicas. Add Redis or another durable queue/cache before horizontal scaling. Add provider-specific integration tests and model version configuration before enabling production traffic.
