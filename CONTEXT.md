# Context

## Current Task
Implemented sibilance reduction modes (De-Esser, High-Shelf, Low-Pass) in the TTS engine and UI.

## Key Decisions
* **Dynamic De-Esser (Standard)**: Split-band de-esser (4.5kHz crossover, 6kHz bandpass detector, 0.015 threshold, 30.0 sensitivity, max -12dB attenuation on high-band).
* **Dynamic De-Esser (Aggressive)**: Split-band de-esser (4.5kHz crossover, 5.8kHz bandpass detector, 0.008 threshold, 150.0 sensitivity, max -30dB attenuation on high-band to squash peaks by 2-3 magnitudes).
* **High-Shelf / Low-Pass**: Restored conservative Option B settings (High-Shelf: -3.5dB at 5500Hz; Low-Pass: 8000Hz) to prevent muffled/lispy fricatives.
* **Noise Gate**: Applied -46dB threshold (0.005) with 3ms attack, 30ms hold, and 80ms release to silence background vocoder hiss across all modes.
* **JNI & UI Integration**: Added a selection dropdown in MainScreen and persisted settings via SharedPreferences.
* **Termux Target Lock**: Configured local.properties to build only for `arm64` to match Termux's compiler.
* **PR Stability & I18n Fixes**: Clamped filter cutoffs to `sample_rate * 0.49` to prevent biquad blow-ups near Nyquist limit. Guarded against uninitialized `sample_rate <= 0.0`. Localized sibilance dropdown strings in strings.xml.

## Next Steps
* Test the de-esser on device speaker and headphones.
* Adjust de-esser threshold or gain reduction ratio if needed.
* Verify the system-wide TTS engine sibilance levels.
