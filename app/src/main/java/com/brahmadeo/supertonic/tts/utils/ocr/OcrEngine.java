/*
 * Copyright 2025 Christian Kierdorf
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 */
package com.brahmadeo.supertonic.tts.utils.ocr;

import android.content.Context;
import android.graphics.Bitmap;

/**
 * The OcrEngine interface defines the contract for Optical Character Recognition (OCR) engines that
 * can extract text from images. Implementations of this interface provide specific OCR backend
 * functionality, such as language configuration and text extraction.
 *
 * <p>It also ensures resource management by extending the {@link AutoCloseable} interface.
 */
public interface OcrEngine extends AutoCloseable {

  /**
   * Retrieves the unique identifier representing the OCR engine implementation.
   *
   * @return A string representing the unique identifier of the OCR engine.
   */
  String id();

  /**
   * Checks if the OCR engine is available for use in the given context.
   *
   * @param ctx The context in which availability of the OCR engine is being checked.
   * @return {@code true} if the OCR engine is available in the specified context, {@code false}
   *     otherwise.
   */
  boolean isAvailable(Context ctx);

  /**
   * Sets the language for the OCR engine to use during text recognition.
   *
   * @param langSpec A string representing the language specification in a format compatible with
   *     the OCR engine (e.g., Tesseract language codes like "eng" for English or "deu" for German).
   *     The provided value determines the language model used for text extraction.
   */
  void setLanguage(String langSpec);

  /**
   * Executes the OCR process on the provided bitmap image and extracts recognized words.
   *
   * @param bitmap The input image as a {@link Bitmap} object to process for text recognition. It is
   *     expected to contain the image data from which text needs to be extracted.
   * @return An {@link OcrResult} object containing the extracted text and associated
   *     positional or metadata information.
   * @throws Exception If an error occurs during the OCR process, such as issues with the input
   *     image, OCR engine configuration, or internal processing.
   */
  OcrResult run(Bitmap bitmap) throws Exception;
}
