package com.yaahua.vcam;

import android.media.MediaPlayer;
import android.view.Surface;

/**
 * 本地文件播放后端，封装 {@link android.media.MediaPlayer}。
 * 简化自 CamSwap AndroidMediaPlayerBackend，移除 Provider PFD 逻辑。
 */
public final class AndroidMediaPlayerBackend implements SurfacePlayerBackend {
    private MediaPlayer player;
    private Surface outputSurface;
    private Listener listener;
    private boolean looping = true;
    private float volume = 0f;
    private MediaSourceDescriptor currentSource;

    public AndroidMediaPlayerBackend() {
        player = new MediaPlayer();
    }

    @Override
    public void setOutputSurface(Surface surface) {
        this.outputSurface = surface;
        if (player != null) player.setSurface(surface);
    }

    @Override
    public void open(MediaSourceDescriptor source) {
        this.currentSource = source;
        try {
            player.reset();
            player.setLooping(looping);
            player.setVolume(volume, volume);
            player.setDataSource(source.localPath);
            if (outputSurface != null) player.setSurface(outputSurface);
            player.setOnPreparedListener(mp -> {
                mp.start();
                if (listener != null) listener.onReady();
            });
            player.setOnCompletionListener(mp -> {
                if (listener != null) listener.onCompletion();
            });
            player.setOnErrorListener((mp, what, extra) -> {
                if (listener != null) listener.onError("MediaPlayer error " + what, null);
                return true;
            });
            player.prepare();
            player.start();
        } catch (Exception e) {
            LogUtil.log("【CS】AndroidMediaPlayerBackend.open 异常: " + e);
            if (listener != null) listener.onError("open failed", e);
        }
    }

    @Override
    public void restart() {
        if (player == null || currentSource == null) return;
        try {
            if (player.isPlaying()) player.stop();
            player.reset();
            open(currentSource);
        } catch (Exception ignored) {}
    }

    @Override public void stop() { if (player != null) try { player.stop(); } catch (Exception ignored) {} }

    @Override
    public void release() {
        if (player != null) {
            try { player.stop(); } catch (Exception ignored) {}
            player.release();
            player = null;
        }
    }

    @Override public boolean isPlaying() { return player != null && tryIsPlaying(); }
    private boolean tryIsPlaying() { try { return player.isPlaying(); } catch (Exception e) { return false; } }

    @Override public long getCurrentPositionMs() { try { return player != null ? player.getCurrentPosition() : 0; } catch (Exception e) { return 0; } }
    @Override public long getDurationMs() { try { return player != null ? player.getDuration() : 0; } catch (Exception e) { return 0; } }

    @Override public void setLooping(boolean looping) { this.looping = looping; if (player != null) player.setLooping(looping); }
    @Override public void setVolume(float volume) { this.volume = volume; if (player != null) player.setVolume(volume, volume); }
    @Override public void setListener(Listener listener) { this.listener = listener; }

    public MediaPlayer getRawPlayer() { return player; }
}