# Offline chat AI

Chat edits ("make it a little longer and navy") can run on a local language model with no
internet. Open the editor → **AI models** icon → **Offline chat AI** tab.

**How it behaves:** the instant built-in rules always run first. Only when they can't map a
request is the local model asked. If the model is missing, slow (60 s limit) or returns junk,
you get the rule parser's answer. Model output is validated (known layer ids only, numbers
clamped) before anything is applied.

## Supported files
| Format | Status |
|---|---|
| `.litertlm` (Google LiteRT-LM) | **Works** — runtime bundled (`litertlm-android`). |
| `.gguf` (llama.cpp) | **Import only** — no llama.cpp runtime is bundled, so it can't be selected yet. |

Small instruct models (~0.5–2 GB) suit phones, e.g. `.litertlm` files from the LiteRT Community
on Hugging Face. Some (Gemma) require accepting their license on Hugging Face first.

## Enabling GGUF
1. Add a llama.cpp Android binding you trust (or build llama.cpp via the NDK yourself).
2. Implement `LocalLlm` for it in `engine/LocalLlm.kt`.
3. Return `true` for `LocalModelFormat.GGUF` in `LocalLlmFactory.isSupported` and construct it in `create`.
Nothing else changes — the store, picker and parser already handle GGUF files.

## Notes
- Models live in app-private **no-backup** storage; they are never included in cloud backups.
- Model files are parsed by native code: import only files from sources you trust.
- First reply after selecting a model is slow (engine load); it is released when you leave the editor.
- This only powers **chat parsing**. Photo try-on stays MediaPipe + OpenCV (or the optional cloud Space).
- `litertlm` uses `latest.release` in `gradle/libs.versions.toml` — pin it to a concrete version after your first successful build.
- Release builds now use R8; keep rules are in `app/proguard-rules.pro`. Test a release build.
