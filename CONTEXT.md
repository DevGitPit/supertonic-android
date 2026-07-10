# Context

## Current Task
Implemented sibilance reduction modes (De-Esser, High-Shelf, Low-Pass) in the TTS engine and UI.

## Key Decisions
* **Dynamic De-Esser**: Implemented sample-by-sample DSP bandpass compressor in Rust for the 6kHz band.
* **JNI & UI Integration**: Added a selection dropdown in MainScreen and persisted settings via SharedPreferences.
* **Termux Target Lock**: Configured local.properties to build only for `arm64` to match Termux's compiler.

## Next Steps
* Test the de-esser on device speaker and headphones.
* Adjust de-esser threshold or gain reduction ratio if needed.
* Verify the system-wide TTS engine sibilance levels.
