package com.brahmadeo.supertonic.tts.service;

import com.brahmadeo.supertonic.tts.service.IPlaybackListener;

interface IPlaybackService {
    oneway void synthesizeAndPlay(String text, String lang, String stylePath, float speed, int steps, int startIndex);
    oneway void addToQueue(String text, String lang, String stylePath, float speed, int steps, int startIndex);
    oneway void play();
    oneway void pause();
    oneway void stop();
    boolean isServiceActive();
    oneway void setListener(IPlaybackListener listener);
    oneway void removeListener(IPlaybackListener listener);
    oneway void exportAudio(String text, String lang, String stylePath, float speed, int steps, String outputPath);
    int getCurrentIndex();
    oneway void setSleepTimer(int minutes);
    int getSleepTimerSeconds();
}