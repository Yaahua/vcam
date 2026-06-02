package com.yaahua.vcam;

import android.media.MediaPlayer;

/**
 * 播放器管理器存根。当前阶段直接使用 MediaPlayer + VideoToFrames。
 * 后续阶段可接入 GLVideoRenderer / SurfaceRelay 等扩展。
 */
public class MediaPlayerManager {
    public MediaPlayer mplayer1;
    public MediaPlayer mMediaPlayer;
    public MediaPlayer c2_player;
    public MediaPlayer c2_player_1;

    public void releaseCamera1Resources() {
        stopAndRelease(mplayer1); mplayer1 = null;
        stopAndRelease(mMediaPlayer); mMediaPlayer = null;
    }

    public void releaseCamera2Resources() {
        stopAndRelease(c2_player); c2_player = null;
        stopAndRelease(c2_player_1); c2_player_1 = null;
    }

    public void releaseAllRenderers() {}
    public void updateRotation(int degrees) {}
    public void restartAll() {}

    private void stopAndRelease(MediaPlayer p) {
        if (p != null) {
            try { p.stop(); } catch (Exception ignored) {}
            p.release();
        }
    }
}