package com.yaahua.vcam;

import android.view.Surface;

/** ExoPlayer 后端存根。后续版本需要引入 Media3 ExoPlayer 依赖实现 RTSP 播放。 */
public class ExoPlayerBackend implements SurfacePlayerBackend {
    private Listener listener;
    private Surface outputSurface;

    @Override public void setOutputSurface(Surface surface) { this.outputSurface = surface; }
    @Override public void open(MediaSourceDescriptor source) {
        LogUtil.log("【CS】ExoPlayerBackend: 存根模式，未实现流播放");
        if (listener != null) listener.onError("ExoPlayer not available", null);
    }
    @Override public void restart() {}
    @Override public void stop() {}
    @Override public void release() {}
    @Override public boolean isPlaying() { return false; }
    @Override public long getCurrentPositionMs() { return 0; }
    @Override public long getDurationMs() { return -1; }
    @Override public void setLooping(boolean looping) {}
    @Override public void setVolume(float volume) {}
    @Override public void setListener(Listener listener) { this.listener = listener; }
}