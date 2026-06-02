package com.yaahua.vcam;

/** 原生音频 Hook 存根。后续版本加载 .so 实现音频替换。 */
public class NativeAudioHook {
    private static boolean initialized = false;

    public static void init() {
        if (initialized) return;
        try {
            // 加载原生库（如果存在）
            // System.loadLibrary("native_audio_hook");
            initialized = true;
            LogUtil.log("【CS】NativeAudioHook 初始化（存根模式）");
        } catch (Throwable t) {
            LogUtil.log("【CS】Native audio hooks init failed: " + t);
        }
    }

    public static void setAudioSource(String audioPath) {}
    public static void setMute(boolean mute) {}
}