package com.yaahua.vcam;

import android.util.Log;

/**
 * 日志工具 - Xposed 环境用 XposedBridge.log，普通环境回退到 android.util.Log。
 */
public class LogUtil {
    private static final String TAG = "【VCAM】";

    private static volatile boolean xposedAvailable = false;
    private static volatile boolean xposedChecked = false;

    private static boolean isXposedAvailable() {
        if (!xposedChecked) {
            try {
                Class.forName("de.robv.android.xposed.XposedBridge");
                xposedAvailable = true;
            } catch (ClassNotFoundException e) {
                xposedAvailable = false;
            }
            xposedChecked = true;
        }
        return xposedAvailable;
    }

    public static void log(String msg) {
        if (msg != null) {
            if (isXposedAvailable()) {
                try {
                    de.robv.android.xposed.XposedBridge.log(TAG + msg);
                    return;
                } catch (Throwable ignored) {}
            }
            Log.d(TAG, msg);
        }
    }

    public static void log(String prefix, String msg) {
        log(prefix + msg);
    }
}