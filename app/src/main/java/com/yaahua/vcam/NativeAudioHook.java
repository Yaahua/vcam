package com.yaahua.vcam;

import android.media.AudioRecord;
import android.media.AudioFormat;
import android.media.AudioAttributes;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

/**
 * Native 层音频 Hook — 尝试加载 .so 兜底;
 * 若 .so 存在则通过 Native 方法覆写 AudioRecord 的底层行为，
 * 否则回退到 Java 层 Hook。
 */
public class NativeAudioHook {
    private static boolean nativeAvailable = false;

    public static void init() {
        try {
            System.loadLibrary("native_audio_hook");
            nativeAvailable = true;
            XposedBridge.log("【VCAM】NativeAudioHook .so 加载成功，Native 层音频拦截已激活");
        } catch (Throwable t) {
            LogUtil.log("【CS】Native audio .so 未找到，回退到 Java 层 Hook: " + t);
        }
    }

    /** 返回 Native 层是否可用 */
    public static boolean isNativeAvailable() {
        return nativeAvailable;
    }

    /**
     * 通过 JNI 设置要注入的音频数据路径。
     * 对应 native 函数: setAudioSourcePath(JNIEnv*, jclass, jstring)
     */
    public static void setAudioSource(String audioPath) {
        if (nativeAvailable) {
            nativeSetAudioSourcePath(audioPath);
        }
    }

    /** JNI: 启用/禁用静音 */
    public static void setMute(boolean mute) {
        if (nativeAvailable) {
            nativeSetMute(mute);
        }
    }

    /**
     * JNI: 实时更新语音输入缓冲区内容（video_sync 模式专用）。
     * 由 Java 层的回调调用，将解码后的 PCM 数据推入 Native 层。
     */
    public static void pushPcmData(byte[] pcm, int offset, int length) {
        if (nativeAvailable) {
            nativePushPcm(pcm, offset, length);
        }
    }

    // ---- Native 方法声明 ----
    private static native void nativeSetAudioSourcePath(String path);
    private static native void nativeSetMute(boolean mute);
    private static native void nativePushPcm(byte[] pcm, int offset, int length);
}