## Get Supertonic for Android

Supertonic is a Text-to-Speech (TTS) engine available across multiple platforms. This repository is for the Systemwide TTS implementation on Android specifically, supporting most/all `ARM` ABIs on Play Store and GitHub releases APKs. On F-Droid release currently only `arm64-v8a` is implemented. 

Please note that functionality and bug fixes may vary slightly between the F-Droid and Play Store builds as there can be version difference as they get submitted and approved following different timetables.

### Download Options

* **Stable Release:** Available now on [F-Droid](https://f-droid.org/packages/com.brahmadeo.supertonic.tts/)
* **Stable Release:** Available on [Google Play Store](https://play.google.com/store/apps/details?id=com.brahmadeo.supertonic.tts)

---

> **Note:** This repository currently tracks both versions of the application.

---

## Adding or Improving Language Support (Submit a PR)

We welcome community contributions to support new languages or improve existing ones. The text-processing pipeline works as follows:
1. **Kotlin Text Normalizer**: Expands shorthand notations (numbers, currency, percentages, ranges) into full spoken words (in the target language).
2. **JNI / Rust Chunker**: Receives the normalized text and splits it into optimal sentences and audio chunks (maximum 300 characters, or 120 for CJK languages).

To add or improve support for a language, follow these steps:

### 1. High-Level Text Normalization (Kotlin)

Text normalization prevents the engine from reading symbols literally (e.g. pronouncing "3" as English "three" instead of Hindi "तीन").

- **Path:** [`app/src/main/java/com/brahmadeo/supertonic/tts/utils/TextNormalizer.kt`](app/src/main/java/com/brahmadeo/supertonic/tts/utils/TextNormalizer.kt)
- **Action:** Add a routing check for your language prefix inside the `normalize` function, and implement your language-specific normalization logic:

```kotlin
// Inside TextNormalizer.normalize()
if (lowerLang.startsWith("hi")) {
    return normalizeHindi(processedText)
}
```

```kotlin
// Example: Implementation of normalizeHindi
private fun normalizeHindi(text: String): String {
    // 1. Convert native scripts digits to latin digits (e.g. ०-९ -> 0-9)
    var normalized = convertDevanagariDigits(text)
    
    // 2. Format ranges (e.g. "10-15" -> "10 से 15")
    val rangePattern = Pattern.compile("\\b(\\d+)\\s*[-–—]\\s*(\\d+)\\b")
    // Replace logic ...

    // 3. Format currency (e.g. "₹500" -> "500 रुपये")
    val currencyPattern = Pattern.compile("(?:\\bINR|₹)\\s*(\\d+(?:\\.\\d+)?)\\b")
    // Replace logic ...

    // 4. Convert remaining digits to words using NumberUtils
    val numberPattern = Pattern.compile("\\b(\\d+(?:\\.\\d+)?)\\b")
    // Replace each match using NumberUtils.convertHindi or NumberUtils.convertHindiDouble
    return normalized
}
```

### 2. Number to Word Expansion (Kotlin)

- **Path:** [`app/src/main/java/com/brahmadeo/supertonic/tts/utils/NumberUtils.kt`](app/src/main/java/com/brahmadeo/supertonic/tts/utils/NumberUtils.kt)
- **Action:** Implement utility functions to convert integer and decimal values into spoken words:

```kotlin
// Example signature:
fun convertHindi(n: Long): String
fun convertHindiDouble(d: Double): String
```

### 3. Sentence Splitting and Chunking (Rust)

The JNI layer delegates text chunking to Rust, ensuring the display UI in `PlaybackActivity` matches the underlying audio chunks.

- **Paths:** 
  - [`rust/src/lang/mod.rs`](rust/src/lang/mod.rs) (Language Normalizer Registry)
  - `rust/src/lang/<lang_code>.rs` (Language normalizer implementation)
  - `rust/src/lang/configs/<lang_code>.json` (JSON config for abbreviations)
- **Action:** 
  1. Add your language configuration JSON containing punctuation splits, abbreviations, and rules.
  2. Implement the `LanguageNormalizer` trait for your language:
     ```rust
     pub struct HindiNormalizer;
     impl LanguageNormalizer for HindiNormalizer {
         fn preprocess(&self, text: &str) -> String { ... }
         fn split_sentences(&self, text: &str) -> Vec<String> { ... }
         fn max_chunk_len(&self) -> usize { 300 }
         fn should_wrap_tags(&self) -> bool { false }
     }
     ```
  3. Register your normalizer in the `get_normalizer` factory function in `rust/src/lang/mod.rs`.

### 4. Language Selection UI and Resources (Kotlin)

- **Paths:**
  - [`app/src/main/res/values/strings.xml`](app/src/main/res/values/strings.xml)
  - [`app/src/main/java/com/brahmadeo/supertonic/tts/MainActivity.kt`](app/src/main/java/com/brahmadeo/supertonic/tts/MainActivity.kt)
- **Action:**
  1. Declare the language display string in `strings.xml`:
     ```xml
     <string name="lang_hindi">Hindi</string>
     ```
  2. Add the string ID and language code mapping to the `languages` map in `MainActivity.kt`:
     ```kotlin
     R.string.lang_hindi to "hi"
     ```

### 5. Writing and Running Tests

Always write tests for the normalization expansions and chunking rules to prevent regressions.

- **Kotlin Tests Path:** [`app/src/test/java/com/brahmadeo/supertonic/tts/utils/TextNormalizerTest.kt`](app/src/test/java/com/brahmadeo/supertonic/tts/utils/TextNormalizerTest.kt)
- **Run Rust Tests:**
  ```bash
  cd rust
  cargo test
  ```
- **Run Kotlin Tests:**
  ```bash
  .\gradlew.bat testDebugUnitTest
  ```

---

## Pronunciation Dictionary (Lexicon) Import/Export Format

To easily share or modify pronunciation rules, you can import and export them as a JSON file.

### JSON Schema
The file must be a JSON array containing objects with the following fields:
* **`term`** (or **`word`**): The source text or word pattern you wish to replace. (Required)
* **`replacement`** (or **`pronunciation`** / **`ipa`**): The target text to substitute. (Required)
  * *Note: `replacement` is a plain-text substitution applied before synthesis, not phonetic IPA notation.*
* **`ignoreCase`** (optional, default: `true`): Boolean specifying whether matching is case-insensitive.
* **`isRegex`** (optional, default: `false`): Boolean specifying whether to treat the matching term as a regular expression pattern.

### Example JSON
```json
[
  {
    "term": "LLMs",
    "replacement": "L L Ems",
    "ignoreCase": true,
    "isRegex": false
  },
  {
    "word": "read",
    "pronunciation": "red",
    "ignoreCase": false
  }
]
```

---

### Credits
* [Supertonic](https://github.com/supertone-inc/supertonic) - For creating lightweight, high-performance, and great-sounding TTS models optimized for edge compute.
* [Readium Kotlin Toolkit](https://github.com/readium/kotlin-toolkit) (BSD 3-Clause) - For EPUB and PDF parsing, manifest structures, and book rendering navigation.
* [PdfBox-Android by Tom Roush](https://github.com/TomRoush/PdfBox-Android) (Apache 2.0) - For parsing and extracting text from PDF documents.
* [jsoup](https://jsoup.org/) (MIT) - For cleaning, parsing, and normalising HTML content.
* [ONNX Runtime](https://github.com/microsoft/onnxruntime) (MIT) - For running cross-platform neural model inference on device.
