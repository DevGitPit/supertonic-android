package com.brahmadeo.supertonic.tts.utils.ocr;

import java.util.List;

/**
 * Simplified result of OCR processing.
 */
public class OcrResult {
    public final String text;
    public final List<RecognizedWord> words;
    public final float meanConfidence;

    public OcrResult(String text, List<RecognizedWord> words, float meanConfidence) {
        this.text = text;
        this.words = words;
        this.meanConfidence = meanConfidence;
    }
}
