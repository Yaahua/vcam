package com.yaahua.vcam;

import android.hardware.Camera;
import android.os.Environment;
import android.widget.Toast;

import java.io.File;
import java.util.Arrays;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class MicrophoneHandler {

    public static void init(final XC_LoadPackage.LoadPackageParam lpparam) {
        XposedHelpers.findAndHookMethod("android.media.MediaRecorder", lpparam.classLoader,
                "setCamera", Camera.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                super.beforeHookedMethod(param);
                SharedState.need_to_show_toast = HookGuards.shouldShowToast();
                XposedBridge.log("【VCAM】[record]" + lpparam.packageName);
                if (SharedState.toast_content != null && SharedState.need_to_show_toast) {
                    try {
                        Toast.makeText(SharedState.toast_content,
                                "应用：" + lpparam.appInfo.name + "(" + lpparam.packageName + ")" +
                                "触发了录像，但目前无法拦截", Toast.LENGTH_SHORT).show();
                    } catch (Exception ee) {
                        XposedBridge.log("【VCAM】[toast]" + Arrays.toString(ee.getStackTrace()));
                    }
                }
            }
        });
    }
}