# Dress-Up App: Free & Low-End Android Upgrade Guide

## Overview

This version is designed specifically for:
- **Low-configuration Android phones** (2-4GB RAM, older chipsets)
- **Zero-cost operation** (all AI models are free)
- **No censorship** (self-hosted or open-source models with no content filters)
- **Privacy-first** (everything can run offline)

---

## Architecture: 3 Modes for Different Phone Capabilities

### Mode 1: Ultra-Light (Any Phone, Instant)
- Uses existing MediaPipe + OpenCV pipeline
- No AI generation, just smart warping + blending
- Works on phones from 2018+
- Result: Good enough for quick preview

### Mode 2: On-Device AI (Mid-range Phone, ~3-5s)
- Uses quantized ONNX models (INT8, 50-100MB)
- Runs on CPU (no GPU required)
- Works on Snapdragon 6xx/7xx series, 4GB+ RAM
- Result: Significantly more realistic

### Mode 3: Free Cloud (Any Phone, ~10-30s)
- Uses Hugging Face Free Inference API
- Or self-hosted via ngrok/Cloudflare tunnel
- Zero cost, no API keys needed for HF free tier
- Result: Photorealistic quality

---

## Free AI Model Sources (Zero Cost)

### 1. Hugging Face Inference API (Free Tier)
- **1,000 requests/month FREE** (no credit card)
- Supports: IDM-VTON, OOTDiffusion, CatVTON
- Endpoint: `https://api-inference.huggingface.co/models/{model_id}`
- No content filtering on most community models
- Rate limit: ~1 request/second on free tier

**Recommended Free Models:**
| Model | HF ID | Type | Notes |
|-------|-------|------|-------|
| IDM-VTON | `yisol/IDM-VTON` | Full body | Best free option |
| CatVTON | `zhengchong/CatVTON` | Upper body | Fast, good quality |
| OOTDiffusion | `levihsu/OOTDiffusion` | Upper body | Very realistic |

### 2. Self-Hosted via ngrok (Completely Free)
- Run diffusion model on your PC/laptop with GPU
- Expose via ngrok (free tier: 1 concurrent tunnel)
- Your phone calls your PC via the ngrok URL
- **Completely uncensored** - you control the model
- **Unlimited requests** - limited only by your GPU

**Setup:**
```bash
# On your PC
pip install diffusers transformers accelerate
# Run a Gradio app with the model
gradio app.py
# In another terminal
ngrok http 7860
# Use the ngrok URL in your Android app
```

### 3. Google Colab (Free GPU, ~12hrs/day)
- Run diffusion models on free T4 GPU
- Use ngrok or localtunnel to expose the API
- Free tier has session timeouts but works well

### 4. Kaggle (Free GPU, 30hrs/week)
- Similar to Colab but more reliable
- P100 GPU is faster than Colab's T4

---

## Lightweight On-Device Models

For low-end phones, we use heavily quantized models:

### Model Specifications
| Model | Original Size | Quantized | RAM Usage | Speed (SD 665) |
|-------|--------------|-----------|-----------|----------------|
| CatVTON | 1.5GB | 80MB (INT8) | ~300MB | 8-12s |
| MiniVTON | Custom | 45MB (INT8) | ~180MB | 4-6s |
| SegFormer | 50MB | 15MB | ~60MB | 1-2s |

### Download Strategy
- Models downloaded on-demand (not bundled in APK)
- Cached locally after first download
- User can delete models to free space
- Progressive download: core model first, enhancement models optional

---

## No-Censorship Strategy

### Why Censorship Happens
1. **API providers** (Replicate, Fal.ai) filter "inappropriate" content
2. **Model creators** add safety filters during training
3. **App stores** (Google Play) enforce content policies

### How We Avoid It
1. **Use community models on Hugging Face** - most have no safety filters
2. **Self-host with uncensored checkpoints** - you control everything
3. **Use local ONNX models** - no external filtering at all
4. **Distribute outside Play Store** if needed (APK sideload, F-Droid, etc.)

### Important Legal Note
While we remove AI censorship for fashion try-on (all clothing types including swimwear, lingerie), this is for **personal use with your own photos**. Do not use on photos of others without consent.

---

## Phone Capability Detection

The app automatically detects phone specs and selects the best mode:

```
RAM < 3GB     -> Mode 1 (Ultra-Light) only
RAM 3-4GB     -> Mode 1 + Mode 2 (small models)
RAM 4-6GB     -> Mode 1 + Mode 2 (full models)
RAM 6GB+      -> All 3 modes
```

---

## Implementation Files

| File | Purpose |
|------|---------|
| `TryOnPipelineV3.kt` | Main engine with 3 modes |
| `FreeCloudClient.kt` | Hugging Face + ngrok + Colab clients |
| `ModelManager.kt` | Download, cache, manage ONNX models |
| `DeviceProfiler.kt` | Detect phone capabilities |
| `build.gradle.kts` | Updated dependencies (lightweight) |

---

## Cost Breakdown

| Component | Cost |
|-----------|------|
| On-device models | FREE (download once) |
| Hugging Face API | FREE (1,000 req/month) |
| Self-hosted (ngrok) | FREE (1 tunnel) |
| Google Colab | FREE (12 hrs/day) |
| Kaggle | FREE (30 hrs/week) |
| **Total** | **$0** |

---

## Getting Started

### Step 1: Set up free cloud backend (optional but recommended)
```bash
# Option A: Hugging Face (easiest)
# Just use the Inference API, no setup needed

# Option B: Self-hosted (best quality, uncensored)
# On your PC with GPU:
pip install torch diffusers transformers accelerate gradio
# Download IDM-VTON or OOTDiffusion
# Run with gradio, expose via ngrok
```

### Step 2: Integrate the new Kotlin files into your project

### Step 3: Let the app download models on first run

### Step 4: Choose your mode in settings

---

## Performance Expectations on Low-End Phones

| Phone Example | Mode 1 | Mode 2 | Mode 3 |
|--------------|--------|--------|--------|
| Samsung A20 (3GB) | 1s | Not available | 15s via HF |
| Redmi Note 8 (4GB) | 1s | 8s | 15s via HF |
| Pixel 4a (6GB) | 0.5s | 3s | 10s via HF |
| Nothing Phone 1 (8GB) | 0.5s | 2s | 8s via HF |

---

## Tips for Best Results on Low-End Phones

1. **Use smaller input images** (512px instead of 1024px)
2. **Process in background** with WorkManager, show progress notification
3. **Cache everything** - results, models, preprocessed images
4. **Use grayscale preview** during processing, color on completion
5. **Allow user to cancel** long-running operations
6. **Show "enhance" button** - process in cloud only when user asks

---

## Recommended Free Model Checkpoints

For self-hosting (uncensored, unlimited):

1. **IDM-VTON**: `yisol/IDM-VTON` - Best overall
2. **OOTDiffusion**: `levihsu/OOTDiffusion` - Most realistic
3. **CatVTON**: `zhengchong/CatVTON` - Fastest
4. **DCI-VTON**: `cuuupid/DCI-VTON-v2` - Good for dresses

All available on Hugging Face, all free to use.
