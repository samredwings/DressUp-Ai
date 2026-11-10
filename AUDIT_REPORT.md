# DressUp AI — security audit, build fixes and upgrade scope

**Review date:** 10 November 2026  
**Input:** `DressUp-AI-App (1).zip`  
**Result:** Source-level remediation applied. A signed/release APK was **not** built or validated in this environment.

## Executive summary

The supplied source had several build integration defects and security weaknesses across the Android client, notebook pairing service, Colab backend, and standalone Express API. The changes in this package address the concrete issues listed below. The remaining external notebooks and missing MediaPipe model assets mean the product is not yet ready for a production release.

## High-priority issues found and changes made

| Priority | Finding in supplied source | Change in this build |
|---|---|---|
| P0 — Build | Kotlin 2.0.21 Compose compiler plugin was combined with the legacy Compose compiler extension setting. | Standardized on the Kotlin 2.2.21 Compose compiler Gradle plugin; removed the legacy extension setting. |
| P0 — Build | Invalid repository group-regex escaping in `settings.gradle.kts`. | Corrected Kotlin string escaping. |
| P0 — Build | Manifest referenced `.DressUpApplication` and `.MainActivity`, but source declares `com.example` package classes. | Manifest now references the fully qualified application and activity classes. |
| P0 — Build/runtime | `CacheCleanupWorker` used Hilt assisted injection without `@HiltWorker` and without a Hilt-aware WorkManager factory. | Added `@HiltWorker`, `HiltWorkerFactory` application configuration, WorkManager Hilt dependencies, and disabled the default WorkManager initializer. |
| P0 — Packaging | ZIP contained `gradlew` and wrapper properties but no `gradle-wrapper.jar`. | Updated wrapper distribution to Gradle 8.13 and documented the missing wrapper JAR. The JAR was not fabricated or copied from another project. |
| P1 — Pairing auth | Notebook pairing relied on a short numeric code in status query strings, wildcard CORS, and permissive endpoint validation. | Replaced it with a 256-bit random bearer token, Authorization header polling, one-time retrieval, ten-minute expiry, bounded registration storage/body, HTTPS-only allowlisted tunnel origins, and no wildcard CORS. |
| P1 — Colab endpoint | Flask `/tryon` had no authentication, permissive image handling, broad CORS, and exposed pairing information in its health/status path. | Removed Flask-CORS, require a constant-time-checked bearer token, bound request/image size and pixels, validate data URIs and categories, rate-limit requests, and return generic errors. |
| P1 — Express API | Protected API routes lacked authentication; weak default secret configuration, unbounded input, caller-controlled webhook destinations and placeholder batch behavior were present. | API routes now fail closed until a 32-byte `API_ACCESS_TOKEN` is set; body/image/category limits and rate limiting are enforced; caller-supplied webhook URLs are no longer accepted; unimplemented batch returns HTTP 501. |
| P1 — Replicate webhook | Signature header/body handling was incorrect and replay protection was missing. | Retained raw JSON bytes, verify `webhook-id`, `webhook-timestamp`, and `webhook-signature` using HMAC-SHA256, enforce a five-minute timestamp window, constant-time compare signatures, and reject replays. |
| P1 — Local file safety | Cleanup trusted a persisted model-cache path and could delete outside the app model directory. | Cleanup canonicalizes each path and deletes only files within `filesDir/models`. |
| P1 — Download safety | Model downloads wrote directly to the final file and had no strict upper bound. | Downloads are size-capped, staged to `.part`, use scoped stream/response closing, and are renamed only after successful completion. |
| P1 — Data retention | Room was configured to destructively drop data on a schema migration. | Removed destructive fallback. Add explicit Room migrations before changing the DB schema/version. |
| P1 — Crash/lifecycle | Try-on flow created/closed an independent GPU manager and did not route connected Colab/Kaggle backends correctly. | Shared the injected free-cloud/GPU manager and route connected Colab/Kaggle connections through their authenticated client paths. |
| P1 — Navigation | `wardrobe` and `history` navigation targets were referenced without destinations. | Added destinations so these routes no longer point to missing navigation entries. |
| P1 — Notebook hand-off | Colab registered `str(pyngrok.connect(...))`, not the tunnel's public URL. | Registers the tunnel object's `public_url` HTTPS value. |

## Dependency/build upgrade scope applied

### Android

- Android Gradle Plugin: **8.11.1**
- Gradle distribution: **8.13**
- Kotlin / Compose compiler plugin: **2.2.21**
- Compose BOM: **2026.09.00**
- `compileSdk` / `targetSdk`: **36**
- Navigation Compose: **2.10.1**
- Room: **2.8.5**
- WorkManager: **2.12.0**
- Dagger/Hilt: **2.57.2**
- AndroidX Hilt Work: **1.2.0**

The selected AGP/Gradle pair is aligned with Google's published compatibility table, and Kotlin 2.2.20–2.2.21 officially supports Gradle through 8.14 and AGP through 8.11.1. Compose's official setup documentation currently identifies BOM `2026.09.00` as the latest stable BOM. See the official references linked in the accompanying review response.

### Node backend

- Express: **5.3.0**
- Axios: **1.21.1**
- `body-parser` override: **2.4.0**
- `follow-redirects` override: **1.16.2**
- Node engine requirement: **20+**

The `body-parser` override addresses CVE-2026-111181 / GHSA-27r3-jxmh-p828 (patched in 2.4.0). Axios 1.21.1 raises the `follow-redirects` minimum to 1.16.2 to address CVE-2026-116689 / GHSA-mrwp-f3q8-3g9p. A lockfile could not be generated because npm registry DNS/network access failed in this environment; run `npm install` in a network-enabled environment and commit `package-lock.json` before deployment.

## Important remaining upgrade and release scope

1. **External Colab and Kaggle notebooks are not included in this ZIP.** `AutoColabManager` and `AutoKaggleManager` still launch externally hosted notebooks. The remote Colab notebook must be replaced with this package's `server/colab_auto_backend.py` implementation; the remote Kaggle notebook must be updated to the same bearer-token registration and `/tryon` protocol. Otherwise the app and remote notebook will speak different protocols and auto-connect will fail. The remote notebooks were not modified or audited.
2. **Missing MediaPipe model assets:** `app/src/main/assets` contains no model files, while the local pipeline requests `pose_landmarker_lite.task` and `selfie_segmentation.tflite` as packaged assets. `ModelManager` downloads models to `filesDir/models`, but the pipeline does not load those downloaded files. Local ML pose/segmentation therefore cannot be considered operational; the fallback remains basic image masking/positioning. Implement the model-file loading path and verify model licenses/checksums before marketing this as AI try-on.
3. **Pin model checksums:** model downloads are size-limited but do not yet verify SHA-256 checksums. Pin an expected digest for every model and reject mismatches before promoting a `.part` file.
4. **Persist the Express job queue:** `jobQueue`, `resultCache`, rate-limit buckets, and webhook replay state are process-local Maps. They are not durable and do not work correctly across multiple replicas. Use Redis/a durable queue and shared result storage before production scaling.
5. **Replicate/Fal/RunPod integration:** configure provider credentials and exact provider model/version identifiers in deployment secrets; add contract/integration tests with actual provider responses. Replicate model versions intentionally no longer use placeholder values.
6. **Remaining Android dependency refresh:** review Core KTX, Lifecycle, Activity Compose, DataStore, Browser, Coil, OkHttp, and MediaPipe versions in a separate tested upgrade. OkHttp remains on 4.12.0 and several AndroidX libraries are older than the Compose/Room/Navigation baseline. Upgrade these in a branch with full compile, device, and API 26–36 regression testing rather than changing them blindly in this security patch.
7. **Test coverage:** add automated tests for pairing expiry/replay, malformed data URI handling, bearer token rejection, image pixel/size limits, cache path traversal, webhook signature validation, Room migrations, and backend error handling.
8. **Secrets and deployment:** create unique production secrets outside source control (`API_ACCESS_TOKEN`, provider API keys, Replicate webhook secret); do not reuse pairing tokens as a long-lived API secret. Deploy Deno pairing and Express API separately.

## Verification performed

| Check | Result |
|---|---|
| `node --check server/index.js` | Passed |
| `node --check server/pairing_server.js` | Passed |
| Colab Python syntax parse (excluding IPython `!pip` shell-magics) | Passed |
| Parse Gradle version catalog as TOML | Passed |
| Parse Android XML resources | Passed — 11 files |
| Kotlin source parsing with locally installed `kotlinc` | No Kotlin syntax/parser diagnostics found; full semantic compilation was unavailable because Android/Compose/Hilt dependencies were not present. |
| Android Gradle build | **Not run:** wrapper JAR missing, Gradle executable unavailable, Android SDK not configured, and Gradle distribution cannot be fetched in this environment. |
| `npm install` / `npm audit` | **Not run to completion:** registry access failed with `EAI_AGAIN`; no lockfile or installed dependency tree was supplied. |

## Release status

**Do not treat this ZIP as a verified release APK.** It is a source-level hardening and build-repair package. Before release, restore the official Gradle wrapper JAR (or generate it with a trusted Gradle 8.13 installation), run a clean Android build and device tests, update the remote notebooks, add the missing MediaPipe model-loading path and checksums, generate the npm lockfile, and run dependency scanners in a network-enabled CI environment.
