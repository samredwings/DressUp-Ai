# Dress-Up App: Market-Standard AI Upgrade Guide

## Executive Summary

This document provides a complete roadmap to upgrade your current dress-up application from a basic OpenCV/MediaPipe overlay engine to a market-leading, AI-powered virtual try-on application that produces photorealistic results comparable to commercial solutions like Google Shopping, Amazon StyleSnap, and Vue.ai.

---

## Current Architecture Analysis

### What Works Well
- **Multi-person pose detection** using MediaPipe Pose Landmarker
- **Person segmentation** with MediaPipe Selfie Segmentation
- **Perspective warp** for garment fitting using OpenCV
- **Skin tone harmonization** and illumination matching
- **Layer-based compositing** with fabric overlays
- **Cloud fallback** via Hugging Face Spaces
- **Offline LLM** for fit advice (Gemma 3n via LiteRT-LM)

### Current Limitations
1. **2D Overlay Approach**: The garment is simply warped and pasted over the person. No understanding of body depth, occlusion, or fabric physics.
2. **No Generative AI**: Results look like stickers pasted on photos, not real clothing.
3. **Limited Garment Understanding**: No semantic segmentation of garment parts (sleeves, collar, hem).
4. **No Body Shape Adaptation**: Same garment warp regardless of body type.
5. **Basic Lighting**: Simple illumination matching; no environment-aware relighting.
6. **No Texture Synthesis**: Fabric styles are procedural overlays, not realistic material rendering.
7. **Cloud Dependency for Quality**: Best results require external Hugging Face Spaces.

---

## Target Architecture: 3-Tier AI Pipeline

```
Tier 1: On-Device (Fast Preview)
  MediaPipe -> Segmentation + Pose -> Basic Overlay (current, optimized)

Tier 2: Edge AI (High Quality, ~2-5s)
  Diffusion Model (ONNX/TensorFlow Lite) -> Generative Try-On
  + Upscaling + Face Preservation

Tier 3: Cloud AI (Photorealistic, ~5-15s)
  Stable Diffusion XL / FLUX -> Full Generative Pipeline
  + ControlNet (Pose + Segmentation)
  + IP-Adapter (Garment Preservation)
  + Inpainting (Seamless Blending)
```

---

## Phase 1: Core Engine Upgrade (Diffusion-Based Generation)

### 1.1 Replace Warp-Based Fitting with Generative Inpainting

**Current**: `warpToTorso()` uses perspective transform to stretch the garment onto body keypoints.

**Upgrade**: Use a diffusion model to generate the garment directly on the body:

```
Input: Person Photo + Garment Photo + Pose Map + Segmentation Mask
Output: Photorealistic person wearing the garment
```

**Recommended On-Device Model**: 
- **Omnierse (ONNX)** - 200MB, runs at ~3s on Snapdragon 8 Gen 3
- **CatVTON (TensorFlow Lite)** - 150MB, optimized for mobile
- **IDM-VTON Lite** - 180MB, good balance of quality/speed

**Recommended Cloud Model**:
- **OOTDiffusion** (best realism for upper body)
- **Stable Diffusion + ControlNet + IP-Adapter**
- **FLUX.1 + Redux** (state-of-the-art as of 2025)

### 1.2 Add Garment Parsing (Semantic Segmentation)

Before fitting, understand the garment structure:
- Detect: collar, sleeves, hem, buttons, patterns
- Classify: dress, shirt, pants, jacket, etc.
- Extract: fabric texture, color palette, pattern

**Model**: DeepFashion2-trained segmentation model (converted to TFLite)

### 1.3 Body-Aware Warping

Instead of simple perspective transform:
1. Extract body mesh from pose landmarks (33 keypoints -> dense mesh)
2. Use Thin Plate Spline (TPS) transformation for natural draping
3. Apply garment-specific deformation rules (e.g., skirts flare, shirts tuck)

---

## Phase 2: Photorealistic Rendering Pipeline

### 2.1 Advanced Harmonization

**Current**: Simple color mean matching + Gaussian illumination

**Upgrade**:
- **Color Transfer in LAB Space**: More perceptually accurate than RGB
- **Histogram Matching**: Match lighting distribution, not just mean
- **Poisson Blending**: Seamless edge integration
- **Multi-scale Blending**: Laplacian pyramid for natural transitions

### 2.2 Shadow & Occlusion Generation

- Generate contact shadows under garment hems
- Self-shadowing based on garment folds
- Body-part occlusion (garment hides body behind it)
- Ambient occlusion for depth realism

### 2.3 Face & Hand Preservation

Critical for realism: preserve the person's face and hands exactly:
- Extract face region using face mesh detection
- Apply diffusion only to body region
- Seamlessly blend face back with edge-aware interpolation
- Preserve skin texture and facial features

### 2.4 Detail Enhancement

- **Super-resolution**: 2x/4x upscaling of final output (ESRGAN/Real-ESRGAN)
- **Texture Sharpening**: Unsharp mask for fabric details
- **Noise Matching**: Add camera noise to match original photo

---

## Phase 3: Multi-Model Intelligence

### 3.1 Smart Category Detection

**Current**: `guessCategory()` returns UNDEFINED

**Upgrade**: 
- Vision model (MobileNetV3) classifies garment type from photo
- Auto-select appropriate fitting pipeline (dress vs shirt vs pants)
- Detect garment attributes: sleeve length, neckline, fit type

### 3.2 Size & Fit Recommendation

**Current**: Offline LLM gives generic advice

**Upgrade**:
- Extract body measurements from pose landmarks
- Compare with garment size chart data
- Visual fit simulation (tight/loose regions)
- AI-generated fit report: "This dress runs small in the shoulders"

### 3.3 Style Compatibility Scoring

- Analyze color harmony between garment and person's skin tone
- Check occasion appropriateness (casual/formal)
- Suggest complementary accessories

---

## Phase 4: User Experience Upgrades

### 4.1 Real-Time Preview

- Show low-res preview instantly (Tier 1)
- Progressive enhancement: preview -> enhanced -> final
- Loading animation showing AI processing steps

### 4.2 Multiple Result Variations

- Generate 3-4 variations of the same try-on
- Different poses, lighting, or styling
- Let user pick the best result

### 4.3 Background Replacement

- Option to replace background for catalog-style output
- Green screen effect for e-commerce integration
- Scene-aware lighting matching

### 4.4 Social Sharing

- Before/after comparison slider
- One-tap share to Instagram, TikTok
- Watermark with brand logo

---

## Phase 5: Technical Implementation

### 5.1 New Dependencies (build.gradle.kts)

```kotlin
dependencies {
    // Existing
    implementation(libs.mediapipe.tasks.vision)
    implementation(libs.opencv)

    // NEW: ONNX Runtime for on-device diffusion
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.19.0")

    // NEW: TensorFlow Lite GPU acceleration
    implementation("org.tensorflow:tensorflow-lite-gpu-api:2.16.1")
    implementation("org.tensorflow:tensorflow-lite-gpu-delegate-plugin:0.4.4")

    // NEW: Image processing
    implementation("jp.co.cyberagent.android:gpuimage:2.1.0")
    implementation("com.github.chrisbanes:PhotoView:2.3.0")

    // NEW: Face detection for preservation
    implementation("com.google.mlkit:face-detection:16.1.7")

    // NEW: Networking (keep existing OkHttp, add)
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-gson:2.11.0")

    // NEW: Image loading/caching
    implementation("io.coil-kt:coil-compose:2.7.0")

    // NEW: Background processing
    implementation("androidx.work:work-runtime-ktx:2.9.1")
}
```

### 5.2 Model Assets to Add

| Model | Size | Purpose |
|-------|------|---------|
| `catvton_generator.onnx` | 150MB | On-device generative try-on |
| `catvton_refiner.onnx` | 80MB | Detail refinement |
| `garment_parser.tflite` | 25MB | Semantic garment segmentation |
| `body_mesh.tflite` | 15MB | Dense body mesh from pose |
| `esrgan_2x.tflite` | 15MB | 2x super-resolution |
| `face_mesh.tflite` | 5MB | Face landmark preservation |

**Total additional assets**: ~290MB (can be downloaded on-demand)

### 5.3 Server Upgrade (Node.js)

Add cloud diffusion pipeline:
- Queue-based processing with Redis
- Support for multiple backends: Replicate, Fal.ai, RunPod
- Webhook callbacks for async results
- Result caching with CDN

---

## Phase 6: Quality Benchmarks

### Target Metrics

| Metric | Current | Target (Market Standard) |
|--------|---------|--------------------------|
| Preview Speed | ~1s | <500ms |
| High-Quality Speed | N/A (cloud only) | 2-5s on-device |
| Photorealistic Speed | 10-30s cloud | 5-10s cloud |
| Output Resolution | 1024px | 2048px (4K option) |
| FID Score (vs real) | ~80 | <30 |
| User Satisfaction | ~60% | >85% |
| Edge Artifacts | Visible | Invisible |
| Shadow Realism | None | Natural |
| Fabric Draping | Stiff warp | Physics-based |

---

## Phase 7: Monetization & Market Features

### 7.1 E-Commerce Integration
- Direct "Buy Now" links to retailer
- Affiliate commission tracking
- Product catalog API integration

### 7.2 Subscription Tiers
- **Free**: Basic overlay (current Tier 1)
- **Pro ($4.99/mo)**: On-device diffusion (Tier 2)
- **Studio ($9.99/mo)**: Cloud photorealistic + batch processing

### 7.3 Analytics
- Track which garments are tried on most
- A/B test different AI models
- User feedback loop for model improvement

---

## Implementation Priority

### Week 1-2: Foundation
1. Integrate ONNX Runtime
2. Add CatVTON model (on-device)
3. Upgrade CompositeEngine with Poisson blending

### Week 3-4: Quality
1. Add face preservation pipeline
2. Implement shadow generation
3. Add super-resolution post-processing

### Week 5-6: Intelligence
1. Garment category auto-detection
2. Fit recommendation engine
3. Style compatibility scoring

### Week 7-8: Polish
1. Real-time preview with progress steps
2. Multiple result variations
3. Background replacement
4. Social sharing features

---

## File Deliverables in This Package

| File | Description |
|------|-------------|
| `TryOnPipelineV2.kt` | Upgraded pipeline with diffusion support |
| `CompositeEngineV2.kt` | Advanced blending with Poisson + shadows |
| `DiffusionClient.kt` | On-device and cloud diffusion client |
| `ImageEnhancer.kt` | Post-processing: SR, sharpening, noise |
| `build.gradle.kts` | Updated dependencies |
| `server/index.js` | Upgraded Node.js server with diffusion proxy |

---

## Recommended AI Model Providers

### On-Device (Free, Privacy-First)
- **CatVTON** (ONNX) - Best mobile-optimized try-on
- **Omnierse** - Good balance of speed/quality

### Cloud (Pay-Per-Use)
- **Replicate** - Run OOTDiffusion, IDM-VTON
- **Fal.ai** - Fast FLUX.1 inference
- **RunPod** - Cheapest GPU rental for custom models

### Open Source Self-Hosted
- **OOTDiffusion** (Hugging Face) - Best open-source realism
- **IDM-VTON** - Good for full-body
- **Stable Diffusion + ControlNet** - Most flexible

---

## Conclusion

This upgrade transforms your app from a simple overlay tool into a professional-grade virtual try-on platform. The key differentiator is the shift from **warping** to **generating** clothing on the body using diffusion models, which is the standard used by industry leaders.

Start with Phase 1 (diffusion integration) for the biggest visual impact, then progressively add the quality and UX enhancements in subsequent phases.
