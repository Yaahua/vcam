package com.yaahua.vcam;

import android.Manifest;
import android.app.Application;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Environment;
import android.widget.Toast;

import java.io.File;
import java.io.FileOutputStream;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

import com.yaahua.vcam.BuildConfig;

public class HookMain implements IXposedHookLoadPackage {

    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpparam) throws Exception {

        // ========== callApplicationOnCreate：初始化 Context、权限检查、目录迁移 ==========
        XposedHelpers.findAndHookMethod("android.app.Instrumentation", lpparam.classLoader,
                "callApplicationOnCreate", Application.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) throws Throwable {
                super.afterHookedMethod(param);
                if (param.args[0] instanceof Application) {
                    try {
                        SharedState.toast_content = ((Application) param.args[0]).getApplicationContext();
                    } catch (Exception ee) {
                        XposedBridge.log("【VCAM】" + ee.toString());
                    }

                    File force_private = new File(Environment.getExternalStorageDirectory().getAbsolutePath() +
                            "/DCIM/Camera1/private_dir.jpg");
                    if (SharedState.toast_content != null) {
                        int auth_statue = 0;
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                            try {
                                auth_statue += (SharedState.toast_content.checkSelfPermission(
                                        Manifest.permission.READ_EXTERNAL_STORAGE) + 1);
                            } catch (Exception ee) {
                                XposedBridge.log("【VCAM】[permission-check]" + ee.toString());
                            }
                            try {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                                    auth_statue += (SharedState.toast_content.checkSelfPermission(
                                            Manifest.permission.MANAGE_EXTERNAL_STORAGE) + 1);
                                }
                            } catch (Exception ee) {
                                XposedBridge.log("【VCAM】[permission-check]" + ee.toString());
                            }
                        } else {
                            if (SharedState.toast_content.checkCallingPermission(
                                    Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                                auth_statue = 2;
                            }
                        }

                        if (auth_statue < 1 || force_private.exists()) {
                            File shown_file = new File(
                                    SharedState.toast_content.getExternalFilesDir(null).getAbsolutePath() +
                                    "/Camera1/");
                            if ((!shown_file.isDirectory()) && shown_file.exists()) {
                                shown_file.delete();
                            }
                            if (!shown_file.exists()) {
                                shown_file.mkdir();
                            }
                            shown_file = new File(
                                    SharedState.toast_content.getExternalFilesDir(null).getAbsolutePath() +
                                    "/Camera1/has_shown");
                            File toast_force_file = new File(
                                    Environment.getExternalStorageDirectory().getPath() +
                                    "/DCIM/Camera1/force_show.jpg");
                            if ((!lpparam.packageName.equals(BuildConfig.APPLICATION_ID)) &&
                                    ((!shown_file.exists()) || toast_force_file.exists())) {
                                try {
                                    Toast.makeText(SharedState.toast_content,
                                            lpparam.packageName +
                                            "未授予读取本地目录权限，请检查权限\nCamera1目前重定向为 " +
                                            SharedState.toast_content.getExternalFilesDir(null).getAbsolutePath() +
                                            "/Camera1/", Toast.LENGTH_SHORT).show();
                                    FileOutputStream fos = new FileOutputStream(
                                            SharedState.toast_content.getExternalFilesDir(null).getAbsolutePath() +
                                            "/Camera1/has_shown");
                                    String info = "shown";
                                    fos.write(info.getBytes());
                                    fos.flush();
                                    fos.close();
                                } catch (Exception e) {
                                    XposedBridge.log("【VCAM】[switch-dir]" + e.toString());
                                }
                            }
                            SharedState.video_path = SharedState.toast_content.getExternalFilesDir(null)
                                    .getAbsolutePath() + "/Camera1/";
                        } else {
                            SharedState.video_path = Environment.getExternalStorageDirectory().getPath() +
                                    "/DCIM/Camera1/";
                        }
                    } else {
                        SharedState.video_path = Environment.getExternalStorageDirectory().getPath() +
                                "/DCIM/Camera1/";
                        File uni_DCIM_path = new File(Environment.getExternalStorageDirectory().getPath() +
                                "/DCIM/Camera1/");
                        if (uni_DCIM_path.canWrite()) {
                            File uni_Camera1_path = new File(SharedState.video_path);
                            if (!uni_Camera1_path.exists()) {
                                uni_Camera1_path.mkdir();
                            }
                        }
                    }
                }
            }
        });

        // ========== 委托 Camera1 Handler ==========
        Camera1Handler.init(lpparam);

        // ========== 委托 Camera2 Handler ==========
        Camera2Handler.init(lpparam);

        // ========== 委托 Microphone Handler ==========
        MicrophoneHandler.init(lpparam);
    }
}