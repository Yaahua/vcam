package com.yaahua.vcam;

import android.view.Surface;

public interface SurfacePlayerBackend {
    void setOutputSurface(Surface surface);
    void open(MediaSourceDescriptor source);
    void restart();
    void stop();
    void release();
    boolean isPlaying();
    long getCurrentPositionMs();
    long getDurationMs();
    void setLooping(boolean looping);
    void setVolume(float volume);
    void setListener(Listener listener);

    interface Listener {
        void onReady();
        void onError(String message, Throwable cause);
        void onDisconnected();
        void onReconnected();
        void onCompletion();
    }
}