# Complete Auto-Colab Integration Guide
# How to Wire Everything Into Your Existing App

## Overview

This guide shows you exactly how to connect all the delivered files into your existing Dress-Up Android app. After following these steps, your users will tap ONE button and get free GPU-powered AI try-on automatically.

## Files You Should Have

### From Previous Deliveries:
1. TryOnPipelineV3.kt - Main engine with 3 modes
2. FreeCloudClient.kt - Cloud API client
3. ImageEnhancer.kt - Post-processing pipeline
4. DeviceProfiler.kt - Phone capability detection
5. ModelManager.kt - On-device model management
6. build_v3.gradle.kts - Updated dependencies

### From This Delivery:
7. AutoColabManager.kt - One-tap Colab connection logic
8. AutoColabConnectScreen.kt - Beautiful UI for the connection flow
9. pairing_server.js - Free pairing server (deploy once, use forever)
10. colab_auto_backend.py - Colab notebook (auto-connects to app)
11. PAIRING_SERVER_SETUP.md - How to deploy the pairing server
12. COLAB_SETUP_GUIDE.md - How to set up Colab manually

## Step-by-Step Integration

### STEP 1: Deploy the Pairing Server (One-Time, 2 Minutes)

This is the only setup YOU need to do. Your users never see this.

1. Go to https://dash.deno.com
2. Sign in with GitHub (free)
3. Click "New Project" -> "Playground"
4. Name it: dressup-pairing
5. Delete the default code
6. Copy ALL of pairing_server.js into the editor
7. Click "Save & Deploy"
8. Your URL is: https://dressup-pairing.deno.dev
9. Copy this URL

### STEP 2: Update AutoColabManager.kt

Open AutoColabManager.kt and find this line:

private const val DEFAULT_PAIRING_SERVER = "https://dressup-pairing.deno.dev"

Replace it with your actual URL from Step 1.

### STEP 3: Add Files to Your Project

Copy these files into your Android project:

app/src/main/java/com/example/engine/
  TryOnPipelineV3.kt          (replace TryOnPipeline.kt)
  FreeCloudClient.kt           (new file)
  ImageEnhancer.kt             (new file)
  DeviceProfiler.kt            (new file)
  ModelManager.kt              (new file)

app/src/main/java/com/example/engine/autocolab/
  AutoColabManager.kt          (new package + file)

app/src/main/java/com/example/ui/autocolab/
  AutoColabConnectScreen.kt    (new package + file)

### STEP 4: Update build.gradle.kts

Replace your current app/build.gradle.kts with build_v3.gradle.kts (delivered earlier).

Or manually add these key dependencies:

```kotlin
dependencies {
    // Existing ones you already have:
    implementation(libs.mediapipe.tasks.vision)
    implementation(libs.opencv)
    implementation(libs.okhttp)
    implementation(libs.litertlm)

    // NEW - Add these:
    implementation("com.google.mlkit:face-detection:16.1.7")
    implementation("androidx.work:work-runtime-ktx:2.9.1")
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("androidx.browser:browser:1.8.0")
    implementation("com.airbnb.android:lottie-compose:6.5.0")
}
```

Sync Gradle after adding.

### STEP 5: Update Your MainActivity.kt

Add a button or menu item that opens the AutoColab connection screen.

In your MainActivity.kt or wherever your navigation is set up:

```kotlin
import com.example.ui.autocolab.AutoColabConnectScreen
import com.example.engine.autocolab.AutoColabManager

class MainActivity : ComponentActivity() {

    private lateinit var autoColabManager: AutoColabManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        autoColabManager = AutoColabManager(this)

        setContent {
            // Your existing theme
            DressUpTheme {
                // Your existing navigation setup
                val navController = rememberNavController()

                NavHost(navController = navController, startDestination = "home") {
                    // Your existing screens
                    composable("home") {
                        HomeScreen(
                            // ... your existing params
                            onConnectGpu = {
                                navController.navigate("autocolab")
                            }
                        )
                    }

                    // ADD THIS NEW SCREEN:
                    composable("autocolab") {
                        AutoColabConnectScreen(
                            autoColabManager = autoColabManager,
                            onConnected = {
                                // When connected, go back to home
                                navController.popBackStack()
                                // Optionally show a toast
                                Toast.makeText(
                                    this@MainActivity,
                                    "Free GPU connected!",
                                    Toast.LENGTH_LONG
                                ).show()
                            },
                            onBack = {
                                navController.popBackStack()
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        autoColabManager.cleanup()
    }
}
```

### STEP 6: Update TryOnPipeline Usage

In your existing code where you call TryOnPipeline, update to use V3:

```kotlin
// OLD (your current code):
val pipeline = TryOnPipeline(context)
val session = pipeline.createSession(modelUri, garmentUri, 0)

// NEW (auto-selects best mode):
val pipeline = TryOnPipelineV3(context)

// Option A: Auto mode (recommended)
val result = pipeline.createSessionAuto(modelUri, garmentUri, 0)
result.onSuccess { session ->
    // Use the session
}.onFailure { error ->
    // Show error to user
}

// Option B: Explicit mode
val session = pipeline.createUltraLightSession(modelUri, garmentUri, 0)  // Fastest
// OR
val session = pipeline.createOnDeviceSession(modelUri, garmentUri, 0)     // If model downloaded
// OR
val session = pipeline.createFreeCloudSession(modelUri, garmentUri, 0)    // Uses Colab
```

### STEP 7: Add the "Connect Free GPU" Button to Your UI

In your HomeScreen.kt or main screen, add a button:

```kotlin
@Composable
fun HomeScreen(
    onConnectGpu: () -> Unit,
    // ... your other params
) {
    // Your existing UI
    Column {
        // ... existing content

        // ADD THIS BUTTON:
        Button(
            onClick = onConnectGpu,
            modifier = Modifier.fillMaxWidth().padding(16.dp)
        ) {
            Icon(Icons.Default.Cloud, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Connect Free GPU")
        }

        // ... rest of your UI
    }
}
```

### STEP 8: Test the Flow

1. Build and run your app on your phone
2. Tap "Connect Free GPU"
3. You should see the AutoColabConnectScreen with a "Connect Free GPU" button
4. Tap it
5. The app should generate a pairing code and open Chrome/Colab
6. In Colab, click "Runtime > Run all" (or Ctrl+F9)
7. Wait 2-3 minutes
8. Your app should automatically show "Connected!"

### STEP 9: (Optional) Add a Settings Menu

For advanced users who want to configure backends manually:

```kotlin
// In your settings screen, add:
BackendSettingsScreen(
    pipeline = pipeline,
    onBack = { navController.popBackStack() }
)
```

This uses the BackendSettingsScreen.kt delivered earlier and gives users:
- Device capability info
- Manual Colab URL input
- Manual ngrok URL input
- Hugging Face token input (optional)

## How It All Works Together

```
User taps "Connect Free GPU"
    |
    v
AutoColabConnectScreen (beautiful UI with steps)
    |
    v
PairingSecurity.newToken() -> 64-character cryptographically random token
    |
    v
AutoColabManager opens Colab in browser
    with pairing code embedded in URL
    |
    v
User clicks "Run All" in Colab (ONE button)
    |
    v
colab_auto_backend.py notebook:
    - Reads pairing code from URL
    - Installs dependencies
    - Loads AI model on free T4 GPU
    - Starts Flask server
    - Creates ngrok tunnel
    - POSTS ngrok URL to pairing server
    |
    v
Pairing Server (deno.dev):
    - Stores a one-time token -> validated HTTPS tunnel origin
    |
    v
AutoColabManager (polling every 3 seconds):
    - GET /status with Authorization: Bearer <token>
    - Receives a one-time ready response containing a validated HTTPS endpoint
    - Auto-configures FreeCloudClient
    - Updates state -> UI shows "Connected!"
    |
    v
TryOnPipelineV3.createFreeCloudSession():
    - Sends photos to Colab's free GPU
    - Receives photorealistic AI try-on result
    - ImageEnhancer post-processes the result
    - User sees the final image!
```

## What the User Sees

### Screen 1: Idle
- Big GPU icon
- "Free GPU Power" title
- "Get photorealistic AI try-on results using Google's free GPU in the cloud"
- 4 steps listed
- Big "Connect Free GPU" button
- "100% Free. No credit card needed."

### Screen 2: Generating Code
- Spinner
- "Preparing Connection..."
- "Generating a secure pairing code..."

### Screen 3: Awaiting Colab
- Secure pairing token remains hidden from the UI
- "Waiting for Colab to start..." with spinner
- Elapsed time counter
- Progress bar
- Instructions: "In your browser: 1. Colab should be open now 2. Click Runtime > Run all..."
- "Cancel" button

### Screen 4: Connected
- Big checkmark icon
- "Connected!" in green
- Card showing: GPU: Tesla T4, Status: Ready, Cost: Free
- "You can now use photorealistic AI try-on with free GPU power!"
- "Start Using AI Try-On" button

### Screen 5 (if failed): Failed
- Error icon
- "Connection Failed"
- Error message
- Common fixes list
- "Try Again" button
- "Go Back" button

## Troubleshooting

### "Colab didn't open"
- Make sure Chrome is installed
- The app uses Chrome Custom Tabs - if not available, falls back to regular browser

### "Pairing code not detected in Colab"
- The notebook uses JavaScript to read the URL fragment
- Some browsers block this - try Chrome
- Manual fallback requires the full 64-character token; never share it or expose it in notebook output

### "Registration failed"
- Check that your pairing server URL is correct in AutoColabManager.kt
- Test: open https://your-url.deno.dev/health in a browser
- Should return {"status":"ok"}

### "Polling timed out"
- Colab takes 2-3 minutes on first run (downloading models)
- Make sure you clicked "Run All" in Colab
- Check that Colab Runtime is set to GPU
- Try again - sometimes Colab is slow

### "ngrok tunnel failed"
- Without an ngrok token, tunnels expire after 2 hours
- Get a free token at ngrok.com and paste it in Colab cell 3
- Or use localtunnel as fallback (automatic)

## Cost Summary

| Component | Cost | Who Pays |
|-----------|------|----------|
| Pairing Server (Deno Deploy) | $0 | You (deploy once) |
| Google Colab GPU | $0 | Google (free tier) |
| ngrok tunnel | $0 | ngrok (free tier) |
| AI Model (open source) | $0 | Community |
| Android App | $0 | You develop |
| **TOTAL** | **$0** | **Everyone** |

## Next Steps After Integration

1. Test on a low-end phone (the app should auto-detect and use Ultra-Light mode)
2. Test on a mid-range phone (should offer on-device AI option)
3. Test the Colab flow end-to-end
4. Consider adding:
   - Model download UI (for on-device AI)
   - Result history/caching
   - Batch processing
   - Share to social media

## Need Help?

- Check PAIRING_SERVER_SETUP.md for server deployment issues
- Check COLAB_SETUP_GUIDE.md for Colab-specific issues
- All Kotlin files have detailed comments explaining the code
