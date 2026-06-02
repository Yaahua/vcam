package com.yaahua.vcam;

import de.robv.android.xposed.XposedBridge;

/**
 * 日志工具 - 当前阶段委托给 XposedBridge.log。
 * 后续可替换为独立日志系统。
 */
public class LogUtil {
    private static final String TAG = "【VCAM】";

    public static void log(String msg) {
        if (msg != null) {
            XposedBridge.log(TAG + msg);
        }
    }

    public static void log(String prefix, String msg) {
        log(prefix + msg);
    }
}