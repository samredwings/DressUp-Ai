# Dress-Up AI App

Android virtual try-on project with optional cloud notebook backends.

## Build prerequisites

- Android Studio with Android Gradle Plugin 8.11.1 support
- JDK 17 or newer (project bytecode target: Java 17)
- Android SDK Platform 36 and Build Tools installed
- Gradle 8.13 (the wrapper properties are configured for 8.13)

Open the project root in Android Studio, allow Gradle sync, then use **Build > Make Project** or **Build > Build APK(s)**.

> Packaging note: this source archive does not contain `gradle/wrapper/gradle-wrapper.jar`. Therefore `./gradlew assembleDebug` cannot run until the standard Gradle 8.13 wrapper JAR is restored (or the project is opened with Android Studio / a locally installed Gradle 8.13). The wrapper script and properties alone are not a complete Gradle wrapper.

## Project structure

- `app/` — Android application
- `server/pairing_server.js` — Deno pairing service
- `server/colab_auto_backend.py` — Colab notebook source (contains IPython `!pip` cells)
- `server/index.js` — separate authenticated Express API reference backend
- `gradle/` — version catalog and wrapper properties

## Important release notes

- Deploy the hardened pairing server and update the externally hosted Colab and Kaggle notebooks to the same protocol. Those remote notebook files are not included in this archive.
- Configure the Express API environment variables described in `server/SECURITY_DEPLOYMENT.md` before exposing it. Protected endpoints fail closed without `API_ACCESS_TOKEN`.
- The MediaPipe model files are not bundled in `app/src/main/assets`; the app currently falls back to its lightweight processing path when those models are unavailable. Download/model activation UX remains an upgrade item.
