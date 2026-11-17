# Dress-Up AI Roadmap

**Status:** Initial roadmap generated 2026-11-17  
**Product direction:** Android virtual try-on application with optional cloud notebook backends.

This roadmap is based on the current repository README and the prior app-review/security discussion. It separates known release blockers from proposed product improvements.

## Phase 0 — Build and deployment readiness
- [ ] Restore the standard Gradle 8.13 wrapper JAR or document a verified alternative build path.
- [ ] Verify a clean Android build with JDK 17, Android SDK 36, and the configured Android Gradle Plugin.
- [ ] Deploy the hardened pairing server.
- [ ] Update externally hosted Colab and Kaggle notebooks to the same pairing protocol.
- [ ] Configure the Express API using the documented environment variables; keep protected endpoints fail-closed when the access token is absent.
- [ ] Review authentication, pairing, token storage, transport security, and log redaction before release.

**Exit criteria:** A clean reproducible Android build and a deployed backend/notebook set using one compatible protocol and secure configuration.

## Phase 1 — Virtual try-on model readiness
- [ ] Define the expected MediaPipe model assets, versions, and licensing.
- [ ] Add a reliable model download/import and activation flow.
- [ ] Verify model integrity and compatibility before activation.
- [ ] Make the lightweight fallback path explicit to the user when models are unavailable.
- [ ] Test segmentation/landmark behavior across varied image sizes, lighting, poses, and clothing.

**Exit criteria:** Users can tell which processing path is active, and model-backed processing works when the required assets are installed.

## Phase 2 — Pairing and backend reliability
- [ ] Test Android-to-notebook pairing, reconnect, timeout, and expired-session behavior.
- [ ] Add clear backend health and connection status.
- [ ] Handle notebook sleep/restart and transient network failures gracefully.
- [ ] Ensure API authorization is enforced consistently across protected routes.
- [ ] Add operational diagnostics that do not expose credentials or private image data.

**Exit criteria:** Pairing and processing failures are recoverable and explainable without weakening access controls.

## Phase 3 — User workflow and output quality
- [ ] Improve image selection, crop/orientation handling, and input validation.
- [ ] Show processing progress and distinguish upload, queue, inference, and result stages.
- [ ] Provide before/after comparison and predictable result saving/sharing.
- [ ] Handle large images and low-memory devices safely.
- [ ] Add clear privacy and retention information for local and cloud processing.

**Exit criteria:** The end-to-end try-on flow communicates what is happening and produces a reliably accessible result.

## Phase 4 — Testing and release
- [ ] Add Android tests for pairing state, model availability, processing fallback, and result handling.
- [ ] Add backend tests for authentication, pairing, and failure modes.
- [ ] Verify notebook protocol compatibility against the deployed pairing server.
- [ ] Test on representative Android API levels and device memory profiles.
- [ ] Publish setup, deployment, troubleshooting, and release checklists.

## Release gates
- Reproducible Android build.
- Pairing server and remote notebooks use the same protocol.
- Protected API routes fail closed without valid authorization.
- Model availability and fallback behavior are visible and tested.
- No credentials or sensitive image data leak into logs.

## Scope notes
The missing wrapper JAR, external notebook synchronization, Express API configuration, and missing MediaPipe model assets are explicitly documented in the current README. Other items are proposed follow-up work.
