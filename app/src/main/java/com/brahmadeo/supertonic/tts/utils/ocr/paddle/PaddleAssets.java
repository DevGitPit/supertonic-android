package com.brahmadeo.supertonic.tts.utils.ocr.paddle;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Simplified PaddleAssets for supertonic-android.
 * Handles extraction of models from assets to cache directory.
 */
final class PaddleAssets {
    private static final String TAG = "PaddleAssets";
    private static final String ASSET_DIR = "ocr/v5";
    private static final String CACHE_SUBDIR = "ocr/v5";
    private static final String DET_NAME = "det.ort";

    private PaddleAssets() {}

    static File getDetModelFile(Context context) {
        return new File(getCacheDir(context), DET_NAME);
    }

    static File getRecModelFile(Context context, String modelKey) {
        String base = PaddleLanguageRouter.assetBaseName(modelKey);
        return new File(getCacheDir(context), base + ".ort");
    }

    static File getRecDictFile(Context context, String modelKey) {
        String base = PaddleLanguageRouter.assetBaseName(modelKey);
        return new File(getCacheDir(context), base + "_dict.txt");
    }

    static void ensureDetExtracted(Context context) throws IOException {
        extractAssetIfNeeded(context, DET_NAME);
    }

    static void ensureRecExtracted(Context context, String modelKey) throws IOException {
        String base = PaddleLanguageRouter.assetBaseName(modelKey);
        if (base == null) throw new IOException("Unknown model key: " + modelKey);
        extractAssetIfNeeded(context, base + ".ort");
        extractAssetIfNeeded(context, base + "_dict.txt");
    }

    private static void extractAssetIfNeeded(Context context, String name) throws IOException {
        File target = new File(getCacheDir(context), name);
        if (target.exists() && target.length() > 0) {
            return;
        }

        File parent = target.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }

        Log.i(TAG, "Extracting asset: " + name + " to " + target.getAbsolutePath());
        try (InputStream in = context.getAssets().open(ASSET_DIR + "/" + name);
             OutputStream out = new FileOutputStream(target)) {
            byte[] buf = new byte[16 * 1024];
            int n;
            while ((n = in.read(buf)) > 0) {
                out.write(buf, 0, n);
            }
        }
    }

    private static File getCacheDir(Context context) {
        File dir = new File(context.getCacheDir(), CACHE_SUBDIR);
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    static boolean areModelsPresent(Context context, String modelKey) {
        String base = PaddleLanguageRouter.assetBaseName(modelKey);
        if (base == null) return false;
        try {
            String[] assets = context.getAssets().list(ASSET_DIR);
            if (assets == null) return false;
            boolean detFound = false;
            boolean recFound = false;
            boolean dictFound = false;
            for (String asset : assets) {
                if (asset.equals(DET_NAME)) detFound = true;
                if (asset.equals(base + ".ort")) recFound = true;
                if (asset.equals(base + "_dict.txt")) dictFound = true;
            }
            return detFound && recFound && dictFound;
        } catch (IOException e) {
            return false;
        }
    }
}
