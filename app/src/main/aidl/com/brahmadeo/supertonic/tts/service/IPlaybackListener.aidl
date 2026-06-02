package com.brahmadeo.supertonic.tts.service;

interface IPlaybackListener {
    oneway void onStateChanged(boolean isPlaying, boolean hasContent, boolean isSynthesizing);
    oneway void onProgress(int current, int total);
    oneway void onPlaybackStopped();
    oneway void onExportComplete(boolean success, String path);
    oneway void onChapterChanged(String newText, String chapterHref, int pageIndex);
    oneway void onTransitioningChanged(boolean isTransitioning);
    oneway void onSleepTimerUpdated(int secondsRemaining);
}