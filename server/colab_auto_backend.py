# ============================================================
# DRESS-UP APP: GOOGLE COLAB AUTO-CONNECT BACKEND (V2)
# ============================================================
# This notebook auto-connects to your Android app via pairing code.
#
# HOW TO USE:
# 1. In your Android app, tap "Connect Free GPU"
# 2. The app opens THIS notebook in Colab with a pairing code
# 3. Just click "Runtime > Run all" (ONE button!)
# 4. Wait 2-3 minutes - the notebook auto-starts and connects
# 5. Your app shows "Connected!" automatically
#
# NO MANUAL URL COPYING NEEDED!
# ============================================================

# @title 0. Read Pairing Config from URL (Auto-detected)
from IPython.display import Javascript, display
import json
import base64
import re
import hmac

# This JavaScript reads the pairing code from the URL fragment
# The Android app embeds it there when opening Colab
display(Javascript('''
    async function getPairingConfig() {
        const hash = window.location.hash;
        if (hash && hash.includes('config=')) {
            const encoded = hash.split('config=')[1];
            const decoded = atob(encoded.replace(/-/g, '+').replace(/_/g, '/'));
            const config = JSON.parse(decoded);
            google.colab.kernel.invokeFunction('notebook.set_pairing_config', [config], {});
        }
    }
    getPairingConfig();
'''))

# Python callback to receive the config
PAIRING_CONFIG = {"pairing_code": "", "pairing_server": ""}

def set_pairing_config(config):
    global PAIRING_CONFIG
    PAIRING_CONFIG = config
    print("Pairing configuration received.")

# Register the callback
import google.colab.output as colab_output
colab_output.register_callback('notebook.set_pairing_config', set_pairing_config)

# Wait a moment for JS to execute
import time
time.sleep(2)

# If no config from URL, try manual input
if not PAIRING_CONFIG.get("pairing_code"):
    print("No pairing config detected from URL.")
    print("You can enter it manually below, or the app should auto-detect it.")

print("Pairing configuration ready." if PAIRING_CONFIG.get("pairing_code") else "Pairing configuration pending.")

# @title 0b. Manual Pairing Code (Fallback)
# If the auto-detection didn't work, paste the code from your app here:
MANUAL_PAIRING_CODE = "" # @param {type:"string"}
MANUAL_PAIRING_SERVER = "https://dressup-pairing.deno.dev" # @param {type:"string"}

if MANUAL_PAIRING_CODE and not PAIRING_CONFIG.get("pairing_code"):
    PAIRING_CONFIG["pairing_code"] = MANUAL_PAIRING_CODE
    PAIRING_CONFIG["pairing_server"] = MANUAL_PAIRING_SERVER
    print("Using manually supplied pairing token.")

# @title 1. Check GPU
import subprocess
result = subprocess.run(["nvidia-smi"], capture_output=True, text=True)
print(result.stdout if result.returncode == 0 else "No GPU! Go to Runtime > Change runtime type > GPU")

# @title 2. Install Dependencies
!pip install -q torch torchvision torchaudio --index-url https://download.pytorch.org/whl/cu118
!pip install -q diffusers transformers accelerate gradio
!pip install -q opencv-python pillow numpy flask pyngrok
print("All dependencies installed!")

# @title 3. Authenticate ngrok
NGROK_AUTH_TOKEN = "" # @param {type:"string"}

if NGROK_AUTH_TOKEN:
    from pyngrok import ngrok
    ngrok.set_auth_token(NGROK_AUTH_TOKEN)
    print("ngrok authenticated!")
else:
    print("No ngrok token - tunnel will expire in 2 hours. Get free token at ngrok.com")

# @title 4. Load AI Model
import torch
from diffusers import StableDiffusionInpaintPipeline, DPMSolverMultistepScheduler
from PIL import Image
import cv2
import numpy as np
import io
import base64

MODEL_LOADED = False
GPU_NAME = "Unknown"

try:
    if torch.cuda.is_available():
        GPU_NAME = torch.cuda.get_device_name(0)
        print(f"GPU detected: {GPU_NAME}")

    pipe = StableDiffusionInpaintPipeline.from_pretrained(
        "runwayml/stable-diffusion-inpainting",
        torch_dtype=torch.float16 if torch.cuda.is_available() else torch.float32,
        safety_checker=None,
        requires_safety_checker=False,
    ).to("cuda" if torch.cuda.is_available() else "cpu")

    pipe.scheduler = DPMSolverMultistepScheduler.from_config(pipe.scheduler.config)
    MODEL_LOADED = True
    print(f"Model loaded successfully on {GPU_NAME}!")

except Exception as e:
    print(f"Model loading issue: {e}")
    print("Will use fallback processing.")

# @title 5. Try-On Processing Functions
def create_body_mask(person_img):
    img_array = np.array(person_img)
    height, width = img_array.shape[:2]
    mask = np.zeros((height, width), dtype=np.uint8)
    x1 = int(width * 0.15)
    y1 = int(height * 0.12)
    x2 = int(width * 0.85)
    y2 = int(height * 0.80)
    mask[y1:y2, x1:x2] = 255
    hsv = cv2.cvtColor(img_array, cv2.COLOR_RGB2HSV)
    lower_skin = np.array([0, 20, 70], dtype=np.uint8)
    upper_skin = np.array([30, 170, 255], dtype=np.uint8)
    skin_mask = cv2.inRange(hsv, lower_skin, upper_skin)
    face_region = np.zeros_like(mask)
    face_y2 = int(height * 0.35)
    face_region[0:face_y2, :] = 255
    skin_mask = cv2.bitwise_and(skin_mask, face_region)
    body_mask = cv2.bitwise_and(mask, cv2.bitwise_not(skin_mask))
    body_mask = cv2.GaussianBlur(body_mask, (51, 51), 0)
    return Image.fromarray(body_mask)

def overlay_garment_realistic(person_img, garment_img, body_mask):
    person_np = np.array(person_img)
    garment_np = np.array(garment_img.resize(person_img.size))
    mask_np = np.array(body_mask)
    person_bgr = cv2.cvtColor(person_np, cv2.COLOR_RGB2BGR)
    garment_bgr = cv2.cvtColor(garment_np, cv2.COLOR_RGB2BGR)
    center_x = person_np.shape[1] // 2
    center_y = int(person_np.shape[0] * 0.45)
    center = (center_x, center_y)
    try:
        result_bgr = cv2.seamlessClone(garment_bgr, person_bgr, mask_np, center, cv2.MIXED_CLONE)
        result_rgb = cv2.cvtColor(result_bgr, cv2.COLOR_BGR2RGB)
        return Image.fromarray(result_rgb)
    except:
        mask_float = mask_np.astype(np.float32) / 255.0
        mask_3ch = np.stack([mask_float] * 3, axis=-1)
        blended = person_np * (1 - mask_3ch) + garment_np * mask_3ch
        return Image.fromarray(blended.astype(np.uint8))

def process_tryon(person_b64, garment_b64, category="upper_body"):
    person_img = decode_image_data_uri(person_b64)
    garment_img = decode_image_data_uri(garment_b64)
    person_img = person_img.resize((512, 768))
    garment_img = garment_img.resize((512, 768))
    body_mask = create_body_mask(person_img)
    if MODEL_LOADED and torch.cuda.is_available():
        result = pipe(
            prompt=f"person wearing {category} clothing, photorealistic, high quality",
            image=person_img,
            mask_image=body_mask,
            num_inference_steps=25,
            guidance_scale=7.5,
        ).images[0]
    else:
        result = overlay_garment_realistic(person_img, garment_img, body_mask)
    buffered = io.BytesIO()
    result.save(buffered, format="JPEG", quality=88)
    img_b64 = base64.b64encode(buffered.getvalue()).decode()
    return {"status": "success", "image": f"data:image/jpeg;base64,{img_b64}"}

print("Processing functions ready!")

# @title 6. Start Flask Server & Auto-Register
from flask import Flask, request, jsonify
import threading
import requests
import time
from collections import defaultdict, deque
from threading import Lock

app = Flask(__name__)
app.config["MAX_CONTENT_LENGTH"] = 16 * 1024 * 1024
Image.MAX_IMAGE_PIXELS = 20_000_000

PAIRING_TOKEN_RE = re.compile(r"^[a-f0-9]{64}$")
ALLOWED_CATEGORIES = {"upper_body", "lower_body", "dresses"}
MAX_IMAGE_BYTES = 6 * 1024 * 1024
request_times = defaultdict(deque)
request_times_lock = Lock()

def authenticated_request():
    expected = PAIRING_CONFIG.get("pairing_code", "")
    provided = request.headers.get("Authorization", "")
    if not isinstance(expected, str) or not PAIRING_TOKEN_RE.fullmatch(expected):
        return False
    if not provided.startswith("Bearer "):
        return False
    supplied = provided[7:]
    return hmac.compare_digest(expected, supplied)

def tryon_rate_limited():
    now = time.time()
    key = request.remote_addr or "unknown"
    with request_times_lock:
        if len(request_times) > 2048:
            for stale_key in list(request_times.keys()):
                stale_bucket = request_times[stale_key]
                while stale_bucket and now - stale_bucket[0] > 60:
                    stale_bucket.popleft()
                if not stale_bucket:
                    request_times.pop(stale_key, None)
        bucket = request_times[key]
        while bucket and now - bucket[0] > 60:
            bucket.popleft()
        if len(bucket) >= 12:
            return True
        bucket.append(now)
    return False

def decode_image_data_uri(value):
    if not isinstance(value, str) or len(value) > (MAX_IMAGE_BYTES * 4 // 3 + 256):
        raise ValueError("Image payload too large")
    match = re.fullmatch(r"data:image/(?:jpeg|jpg|png|webp);base64,([A-Za-z0-9+/=]+)", value)
    if not match:
        raise ValueError("Unsupported image payload")
    raw = base64.b64decode(match.group(1), validate=True)
    if not raw or len(raw) > MAX_IMAGE_BYTES:
        raise ValueError("Image payload too large")
    with Image.open(io.BytesIO(raw)) as probe:
        if probe.width < 64 or probe.height < 64 or probe.width * probe.height > 20_000_000:
            raise ValueError("Invalid image dimensions")
        probe.verify()
    return Image.open(io.BytesIO(raw)).convert("RGB")

@app.route("/", methods=["GET"])
def home():
    return jsonify({
        "status": "running",
        "service": "DressUp try-on backend"
    })

@app.route("/tryon", methods=["POST"])
def tryon():
    if not authenticated_request():
        return jsonify({"error": "Unauthorized"}), 401
    if tryon_rate_limited():
        return jsonify({"error": "Rate limit exceeded"}), 429
    data = request.get_json(silent=True)
    if not isinstance(data, dict):
        return jsonify({"error": "Invalid JSON request"}), 400
    person_img = data.get("human_img", "")
    garment_img = data.get("garment_img", "")
    category = data.get("category", "upper_body")
    if category not in ALLOWED_CATEGORIES:
        return jsonify({"error": "Invalid category"}), 400
    if not person_img or not garment_img:
        return jsonify({"error": "human_img and garment_img required"}), 400
    try:
        result = process_tryon(person_img, garment_img, category)
        if result.get("status") != "success":
            return jsonify({"error": "Image processing failed"}), 422
        return jsonify(result)
    except Exception:
        app.logger.exception("Try-on request failed")
        return jsonify({"error": "Image processing failed"}), 422

@app.route("/health", methods=["GET"])
def health():
    return jsonify({"status": "ok"})

def start_server():
    app.run(host="0.0.0.0", port=7860, debug=False, use_reloader=False)

server_thread = threading.Thread(target=start_server, daemon=True)
server_thread.start()
time.sleep(3)

try:
    from pyngrok import ngrok as pyngrok_module
    tunnel = pyngrok_module.connect(addr="7860", proto="http")
    public_url = tunnel.public_url
    if not isinstance(public_url, str) or not public_url.startswith("https://"):
        raise RuntimeError("The notebook tunnel did not provide a public HTTPS URL")
    print("=" * 60)
    print("SERVER RUNNING!")
    print("=" * 60)
    print(f"ngrok URL: {public_url}")
    print(f"GPU: {GPU_NAME}")
    print(f"Model: {'Loaded' if MODEL_LOADED else 'Fallback mode'}")
    print("=" * 60)

    pairing_code = PAIRING_CONFIG.get("pairing_code")
    pairing_server = PAIRING_CONFIG.get("pairing_server", "https://dressup-pairing.deno.dev")

    if pairing_code and pairing_server:
        try:
            reg_response = requests.post(
                f"{pairing_server}/register",
                json={"code": pairing_code, "endpoint_url": public_url, "gpu": GPU_NAME},
                timeout=15
            )
            if reg_response.status_code == 200:
                print("Auto-registration complete.")
                print("Your Android app should now show 'Connected!'")
            else:
                print(f"Registration response: {reg_response.status_code}")
        except Exception as e:
            print("Auto-registration failed; check the pairing server URL and notebook logs.")
            print(f"Manual: POST {pairing_server}/register")
    else:
        print("No pairing code configured. Use manual mode.")

except Exception as e:
    print(f"ngrok failed: {e}")
    print("Trying localtunnel...")
    import subprocess
    subprocess.Popen(["npx", "localtunnel", "--port", "7860"])

# @title 7. Keep Alive
print("Server is running. Keep this tab open.")
print("The Android app will auto-detect when connected.")
print("=" * 60)

from IPython.display import clear_output

try:
    while True:
        time.sleep(300)
        clear_output(wait=True)
        print(f"Server running. GPU: {GPU_NAME}. Last ping: {time.strftime('%H:%M:%S')}")
        if 'public_url' in globals():
            print(f"URL: {public_url}")
except KeyboardInterrupt:
    print("Stopped keep-alive.")
