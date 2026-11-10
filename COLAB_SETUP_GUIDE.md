# Google Colab Free GPU Setup Guide for Dress-Up App

## Quick Start (3 Steps)

### Step 1: Open Colab Notebook
1. Go to https://colab.research.google.com
2. Click **File > Upload notebook**
3. Upload the `colab_tryon_backend.py` file (or copy-paste the code into a new notebook)

### Step 2: Enable GPU
1. Click **Runtime > Change runtime type**
2. Under "Hardware accelerator", select **T4 GPU**
3. Click **Save**

### Step 3: Run All Cells
1. Click **Runtime > Run all** (or press Ctrl+F9)
2. Wait for all cells to execute (about 2-3 minutes first time)
3. Look for the **ngrok URL** in the output, it looks like:
   ```
   Public URL: https://abc123-def.ngrok-free.app
   ```
4. **Copy this URL** and paste it in your Android app:
   - Open Dress-Up app
   - Go to **Settings > Self-Hosted Backend**
   - Paste the URL: `https://abc123-def.ngrok-free.app`
   - Tap **Save**

That is it! Your app is now connected to free GPU power.

---

## How It Works

```
Your Phone (Dress-Up App)
    |
    | HTTP request with photos
    v
ngrok tunnel (free)
    |
    v
Google Colab (free T4 GPU)
    |
    v
AI Diffusion Model (IDM-VTON)
    |
    v
Photorealistic result back to your phone
```

**Your photos never go to any company.** They travel:
- Phone -> ngrok -> Your own Colab notebook -> Back to phone
- No third-party AI service sees your images
- Completely private and uncensored

---

## Important Tips

### Keep Colab Running
- Colab disconnects after **~90 minutes of inactivity**
- To prevent this, keep the Colab browser tab open
- Or run the "Keep Alive" cell (cell 8) which pings every 5 minutes

### Free GPU Limits
- **~12 hours per day** of GPU time
- If you hit the limit, wait until the next day or:
  - Use Kaggle (30 hours/week, see separate guide)
  - Use your own PC with ngrok (no limits)

### ngrok Token (Recommended)
- Free signup at https://dashboard.ngrok.com/signup
- Get your authtoken from https://dashboard.ngrok.com/get-started/your-authtoken
- Paste it in **Cell 3** of the notebook where it says:
  ```python
  NGROK_AUTH_TOKEN = ""  # Paste token here
  ```
- Without a token, ngrok tunnels expire after **2 hours**
- With a free token, tunnels last **longer and are more stable**

### If ngrok Fails
The notebook automatically tries **localtunnel** as a backup:
```bash
npx localtunnel --port 7860
```
Look for the localtunnel URL in the output.

---

## Troubleshooting

### "No GPU detected"
- Go to **Runtime > Change runtime type > GPU**
- Restart runtime: **Runtime > Restart runtime**

### "ngrok authentication failed"
- Sign up free at ngrok.com
- Copy your authtoken and paste it in Cell 3
- Re-run the notebook

### "Model loading failed"
- First run downloads ~5GB of model files
- This takes 5-10 minutes on first run
- Subsequent runs are much faster (models are cached)

### "Connection refused" in Android app
- Make sure the Colab notebook is still running
- Check that the ngrok URL has not expired (get a new one by re-running)
- Verify your phone and Colab both have internet

### Colab keeps disconnecting
- Keep the Colab browser tab visible (not minimized)
- Run the keep-alive cell
- Use a browser extension to prevent tab sleep
- Or upgrade to Colab Pro ($10/month) for longer sessions

---

## Alternative: No ngrok Token Needed

If you do not want to sign up for ngrok, you can use **localtunnel** instead:

1. In the notebook, leave `NGROK_AUTH_TOKEN` empty
2. The notebook will automatically use localtunnel
3. The URL will look like: `https://bright-ideas-lie-123-45-67-89.loca.lt`
4. Paste this URL in your app settings

Note: localtunnel sometimes shows a warning page first. This is normal.

---

## Saving the Notebook to Your Drive

1. Click **File > Save a copy in Drive**
2. This saves the notebook to your Google Drive
3. Next time, open it from Drive and just click **Run all**
4. Much faster than re-uploading

---

## Comparison: Colab vs Other Free Options

| Feature | Colab Free | Kaggle | Your PC + ngrok |
|---------|-----------|--------|-----------------|
| GPU | T4 (free) | P100 (free) | Your GPU |
| Daily limit | ~12 hours | 30 hours/week | Unlimited |
| Setup | Easy | Medium | Medium |
| Stability | Okay | Better | Best |
| Speed | Good | Better | Depends on GPU |
| Cost | $0 | $0 | $0 |

**Recommendation:** Start with Colab (easiest). If you need more stability, set up your own PC.
