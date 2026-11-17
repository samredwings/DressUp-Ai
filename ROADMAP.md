# Dress-Up AI Roadmap

**Status:** Audited against GitHub `main` on 2026-11-17  
**Product direction:** Android virtual try-on application with optional cloud notebook backends.

Checkboxes reflect current source evidence. Source implementation is distinguished from deployment verification and end-to-end tests.

## Phase 0 — Build and deployment readiness
- [ ] Restore the standard Gradle 8.13 wrapper JAR or document a verified alternative build path.
- [ ] Verify a clean Android build with JDK 17, Android SDK 36, and the configured Android Gradle Plugin.
- [ ] Deploy the hardened pairing server.
- [ ] Update externally hosted Colab and Kaggle notebooks to the same pairing protocol.
- [ ] Configure the Express API using the documented environment variables; keep protected endpoints fail-closed when the access token is absent.
- [ ] Review authentication, pairing, token storage, transport security, and log redaction before release.

**Audit note:** The wrapper JAR is absent. A local Gradle 8.10.2 attempt failed during Kotlin KAPT plugin classpath resolution; the required Gradle 8.13 wrapper build was not possible. The pairing server and notebooks are external deployment dependencies and were not live-verified. The Express API code returns 503 when `API_ACCESS_TOKEN` is missing/too short and authenticates selected protected routes, but production environment configuration was not inspected.

**Exit criteria:** A clean reproducible Android build and a deployed backend/notebook set using one compatible protocol and secure configuration.

## Phase 1 — Virtual try-on model readiness
- [ ] Define the expected MediaPipe model assets, versions, and licensing.
- [ ] Add a reliable model download/import and activation flow.
- [ ] Verify model integrity and compatibility before activation.
- [ ] Make the lightweight fallback path explicit to the user when models are unavailable.
- [ ] Test segmentation/landmark behavior across varied image sizes, lighting, poses, and clothing.

**Audit note:** A `ModelManager` class contains model URLs, bounded downloads and progress events, and the pipeline has heuristic/lightweight fallbacks. However, the model manager has no application UI call sites, no model files are bundled in assets, and the active pipeline expects asset-path models. The complete download-to-activation UX and integrity verification are therefore not checked off. Fallback processing exists in code, but explicit user-facing model-unavailable guidance was not established.

**Exit criteria:** Users can tell which processing path is active, and model-backed processing works when the required assets are installed.

## Phase 2 — Pairing and backend reliability
- [ ] Test Android-to-notebook pairing, reconnect, timeout, and expired-session behavior.
- [x] Add clear backend health and connection status. Source includes pairing-server `/health`, self-hosted backend health probes, and explicit connection states/screens.
- [ ] Handle notebook sleep/restart and transient network failures gracefully.
- [x] Ensure API authorization is enforced consistently across protected routes. The Express API applies bearer-token middleware to the protected try-on, batch, status and backend routes and fails closed when the server token is not configured. End-to-end route tests remain outstanding.
- [ ] Add operational diagnostics that do not expose credentials or private image data.

**Exit criteria:** Pairing and processing failures are recoverable and explainable without weakening access controls.

## Phase 3 — User workflow and output quality
- [ ] Improve image selection, crop/orientation handling, and input validation.
- [ ] Show processing progress and distinguish upload, queue, inference, and result stages.
- [ ] Provide before/after comparison and predictable result saving/sharing.
- [ ] Handle large images and low-memory devices safely.
- [ ] Add clear privacy and retention information for local and cloud processing.

**Audit note:** The UI exposes processing-stage progress states and the device profiler selects processing tiers. This does not yet establish the full requested upload/queue/inference/result lifecycle, before/after review, orientation handling or low-memory reliability.

**Exit criteria:** The end-to-end try-on flow communicates what is happening and produces a reliably accessible result.

## Phase 4 — Testing and release
- [ ] Add Android tests for pairing state, model availability, processing fallback, and result handling.
- [ ] Add backend tests for authentication, pairing, and failure modes.
- [ ] Verify notebook protocol compatibility against the deployed pairing server.
- [ ] Test on representative Android API levels and device memory profiles.
- [ ] Publish setup, deployment, troubleshooting, and release checklists.

## Audit results — 2026-11-17
- Android test source files were not found in the current repository.
- The Gradle wrapper JAR is absent.
- Local Gradle 8.10.2 build attempt failed at Kotlin KAPT plugin classpath resolution; no successful Android build is claimed.
- Model assets are not present under `app/src/main/assets`.
- Pairing token generation uses 256-bit secure randomness; client and server validate HTTPS tunnel origins against allowlisted tunnel suffixes.
- Pairing and API code contains time-limited pairing state, bounded request handling, token checks and fail-closed API authorization.
- External deployment state, remote notebook synchronization and device-level behavior were not verified.

**Audit conclusion:** Two source-level backend items are checked off. Build, model activation, deployment and end-to-end testing remain open.
