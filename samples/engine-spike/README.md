# Engine Spike Sample

Minimal Compose app that exercises `MediaPipeLlmEngine` on a real device.
**Not a production sample** — its purpose is to verify the inference layer
works end-to-end on Pixel 8 and Pixel 7a and to gather performance numbers
for `docs/adr/0003-mediapipe-llm-inference-spike-results.md`.

## What it does

- Locates a `.task` model file in the app's external files dir.
- Loads it into a `MediaPipeLlmEngine` (cold-start timed).
- Lets you type a prompt and stream a response.
- Reports TTFT, total tokens, decode rate.

No network, no analytics, no permissions. The whole point of Velat is that
none of this needs to leave the device.

## One-time setup

### 1. Get the model file

Download Gemma 2 2B IT INT4 (~1.36 GB) from Google AI Edge:

```
https://huggingface.co/litert-community/Gemma2-2B-IT/resolve/main/Gemma2-2B-IT_multi-prefill-seq_q4_ekv4096.task
```

You'll need a HuggingFace account and to accept Google's license terms first.
Save the file locally as `gemma-2-2b-it-int4.task`.

### 2. Connect your device

Plug in a Pixel 8 or Pixel 7a via USB.

- Enable developer mode: Settings → About phone → tap Build number 7 times.
- Enable USB debugging: Settings → System → Developer options → USB debugging.
- Authorize this computer when prompted.

Verify the device is recognized:

```bash
adb devices
```

Expected output: a line like `XXXXXXXX  device` (not `unauthorized`).

### 3. Build and install the app

From the repo root:

```bash
./gradlew :samples:engine-spike:installDebug
```

This compiles and pushes the APK to the connected device. Takes ~1 minute.

### 4. Push the model file to the device

The app expects the model at a specific location in its scoped external
storage. After step 3 the directory exists; push the model into it:

```bash
adb push gemma-2-2b-it-int4.task \
  /sdcard/Android/data/com.velat.sample.enginespike/files/models/
```

This copy takes 1–3 minutes depending on USB cable quality.

### 5. Launch the app

Either tap the **Velat Engine Spike** icon on the device or from the host:

```bash
adb shell am start -n com.velat.sample.enginespike/.MainActivity
```

The first screen shows model status. If the file was pushed correctly,
you'll see "Found at: …" and a "Load model into engine" button. Tap it.
Cold load takes 5–10 seconds on Pixel 8, 8–15 seconds on Pixel 7a.

After load you can type a prompt and hit Generate. Streaming response
appears with running TTFT and tok/s counters.

## What to measure (for ADR 0003)

Per device, run these and record the numbers:

| Measurement | How |
|---|---|
| Cold load time | App reports it as "Cold-load time" after Load button completes |
| TTFT (short prompt) | Generate "What is the capital of France?" — first counter |
| TTFT (long prompt) | Generate a 200-word prompt — first counter |
| Decode rate (sustained) | Average tok/s shown after Generate completes |
| Peak RAM | Use `adb shell dumpsys meminfo com.velat.sample.enginespike` during generation |
| Warm vs cold | Restart app; first generate is warm if the model is still on disk cache |

Copy results into `docs/adr/0003-mediapipe-llm-inference-spike-results.md`.

## Troubleshooting

**App says "Model file not found" with a path.**
The push in step 4 didn't reach the right directory. Confirm with:

```bash
adb shell ls /sdcard/Android/data/com.velat.sample.enginespike/files/models/
```

You should see `gemma-2-2b-it-int4.task`. If the directory doesn't exist,
launch the app once first (step 5 creates it on first launch).

**App crashes on model load with "Out of memory".**
The Pixel 7a is at the edge of what 4096-token context allows for Gemma 2 2B
INT4. Drop the context size in `EngineSpikeViewModel.loadModel()` by passing
`contextSize = 2048` to `MediaPipeLlmEngine.fromFile`.

**Generation produces garbage / empty text.**
Could be tokenizer mismatch or wrong model variant. Verify you downloaded
the IT (instruction-tuned) variant and not a base model.

**ADB push is extremely slow.**
USB 2.0 cable. Use a USB 3.0 / USB-C cable for ~10x faster transfer.
