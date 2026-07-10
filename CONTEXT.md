# Context

## Current Task
Implemented sibilance reduction modes (De-Esser, High-Shelf, Low-Pass) in the TTS engine and UI.

## Key Decisions
* **Dynamic De-Esser (Standard)**: 6kHz center, 0.015 threshold, 30.0 sensitivity, max -12dB attenuation.
* **Dynamic De-Esser (Aggressive)**: 5.8kHz center, 0.008 threshold, 150.0 sensitivity, max -30dB attenuation (reduces sibilant peaks by 2-3 magnitudes).
* **High-Shelf / Low-Pass**: Restored conservative Option B settings (High-Shelf: -3.5dB at 5500Hz; Low-Pass: 8000Hz) to prevent muffled/lispy fricatives.
* **Noise Gate**: Applied -46dB threshold (0.005) with 3ms attack, 30ms hold, and 80ms release to silence background vocoder hiss across all modes.
* **JNI & UI Integration**: Added a selection dropdown in MainScreen and persisted settings via SharedPreferences.
* **Termux Target Lock**: Configured local.properties to build only for `arm64` to match Termux's compiler.

## Next Steps
* Test the de-esser on device speaker and headphones.
* Adjust de-esser threshold or gain reduction ratio if needed.
* Verify the system-wide TTS engine sibilance levels.
