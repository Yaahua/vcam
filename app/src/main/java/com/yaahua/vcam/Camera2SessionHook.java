package com.yaahua.vcam;

import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.InputConfiguration;
import android.hardware.camera2.params.OutputConfiguration;
import android.hardware.camera2.params.SessionConfiguration;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.view.Surface;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class Camera2SessionHook {

    // ======================== processCamera2Init ========================
    public static void processCamera2Init(final Class hooked_class) {
        XposedHelpers.findAndHookMethod(hooked_class, "onOpened", CameraDevice.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                SharedState.need_recreate = true;
                createVirtualSurface();

                // 释放旧的播放器资源
                if (SharedState.c2_player != null) {
                    SharedState.c2_player.stop();
                    SharedState.c2_player.reset();
                    SharedState.c2_player.release();
                    SharedState.c2_player = null;
                }
                if (SharedState.c2_hw_decode_obj_1 != null) {
                    SharedState.c2_hw_decode_obj_1.stopDecode();
                    SharedState.c2_hw_decode_obj_1 = null;
                }
                if (SharedState.c2_hw_decode_obj != null) {
                    SharedState.c2_hw_decode_obj.stopDecode();
                    SharedState.c2_hw_decode_obj = null;
                }
                if (SharedState.c2_player_1 != null) {
                    SharedState.c2_player_1.stop();
                    SharedState.c2_player_1.reset();
                    SharedState.c2_player_1.release();
                    SharedState.c2_player_1 = null;
                }
                SharedState.c2_preview_Surfcae_1 = null;
                SharedState.c2_reader_Surfcae_1 = null;
                SharedState.c2_reader_Surfcae = null;
                SharedState.c2_preview_Surfcae = null;
                SharedState.is_first_hook_build = true;
                XposedBridge.log("【VCAM】打开相机C2");

                File file = HookGuards.getVideoFile();
                SharedState.need_to_show_toast = HookGuards.shouldShowToast();
                if (!file.exists()) {
                    if (SharedState.toast_content != null && SharedState.need_to_show_toast) {
                        try {
                            Toast.makeText(SharedState.toast_content,
                                    "不存在替换视频\n" + SharedState.toast_content.getPackageName() +
                                    "当前路径：" + SharedState.video_path, Toast.LENGTH_SHORT).show();
                        } catch (Exception ee) {
                            XposedBridge.log("【VCAM】[toast]" + ee.toString());
                        }
                    }
                    return;
                }

                // Hook CameraDevice.createCaptureSession (传统3参数)
                XposedHelpers.findAndHookMethod(param.args[0].getClass(), "createCaptureSession",
                        List.class, CameraCaptureSession.StateCallback.class, Handler.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam paramd) throws Throwable {
                        if (paramd.args[0] != null) {
                            XposedBridge.log("【VCAM】createCaptureSession创捷捕获，原始:" + paramd.args[0].toString() +
                                    "虚拟：" + SharedState.c2_virtual_surface.toString());
                            paramd.args[0] = Arrays.asList(SharedState.c2_virtual_surface);
                            if (paramd.args[1] != null) {
                                processCamera2SessionCallback((CameraCaptureSession.StateCallback) paramd.args[1]);
                            }
                        }
                    }
                });

                // Hook CameraDevice.createCaptureSessionByOutputConfigurations (API 24+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    XposedHelpers.findAndHookMethod(param.args[0].getClass(),
                            "createCaptureSessionByOutputConfigurations", List.class,
                            CameraCaptureSession.StateCallback.class, Handler.class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            super.beforeHookedMethod(param);
                            if (param.args[0] != null) {
                                SharedState.outputConfiguration = new OutputConfiguration(SharedState.c2_virtual_surface);
                                param.args[0] = Arrays.asList(SharedState.outputConfiguration);
                                XposedBridge.log("【VCAM】执行了createCaptureSessionByOutputConfigurations-144777");
                                if (param.args[1] != null) {
                                    processCamera2SessionCallback((CameraCaptureSession.StateCallback) param.args[1]);
                                }
                            }
                        }
                    });
                }

                // Hook CameraDevice.createConstrainedHighSpeedCaptureSession
                XposedHelpers.findAndHookMethod(param.args[0].getClass(),
                        "createConstrainedHighSpeedCaptureSession", List.class,
                        CameraCaptureSession.StateCallback.class, Handler.class, new XC_MethodHook() {
                    @Override
                    protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                        super.beforeHookedMethod(param);
                        if (param.args[0] != null) {
                            param.args[0] = Arrays.asList(SharedState.c2_virtual_surface);
                            XposedBridge.log("【VCAM】执行了 createConstrainedHighSpeedCaptureSession -5484987");
                            if (param.args[1] != null) {
                                processCamera2SessionCallback((CameraCaptureSession.StateCallback) param.args[1]);
                            }
                        }
                    }
                });

                // Hook CameraDevice.createReprocessableCaptureSession (API 23+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    XposedHelpers.findAndHookMethod(param.args[0].getClass(),
                            "createReprocessableCaptureSession", InputConfiguration.class, List.class,
                            CameraCaptureSession.StateCallback.class, Handler.class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            super.beforeHookedMethod(param);
                            if (param.args[1] != null) {
                                param.args[1] = Arrays.asList(SharedState.c2_virtual_surface);
                                XposedBridge.log("【VCAM】执行了 createReprocessableCaptureSession ");
                                if (param.args[2] != null) {
                                    processCamera2SessionCallback((CameraCaptureSession.StateCallback) param.args[2]);
                                }
                            }
                        }
                    });
                }

                // Hook CameraDevice.createReprocessableCaptureSessionByConfigurations (API 24+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    XposedHelpers.findAndHookMethod(param.args[0].getClass(),
                            "createReprocessableCaptureSessionByConfigurations", InputConfiguration.class,
                            List.class, CameraCaptureSession.StateCallback.class, Handler.class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            super.beforeHookedMethod(param);
                            if (param.args[1] != null) {
                                SharedState.outputConfiguration = new OutputConfiguration(SharedState.c2_virtual_surface);
                                param.args[0] = Arrays.asList(SharedState.outputConfiguration);
                                XposedBridge.log("【VCAM】执行了 createReprocessableCaptureSessionByConfigurations");
                                if (param.args[2] != null) {
                                    processCamera2SessionCallback((CameraCaptureSession.StateCallback) param.args[2]);
                                }
                            }
                        }
                    });
                }

                // Hook CameraDevice.createCaptureSession with SessionConfiguration (API 28+)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    XposedHelpers.findAndHookMethod(param.args[0].getClass(), "createCaptureSession",
                            SessionConfiguration.class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                            super.beforeHookedMethod(param);
                            if (param.args[0] != null) {
                                XposedBridge.log("【VCAM】执行了 createCaptureSession -5484987");
                                SharedState.sessionConfiguration = (SessionConfiguration) param.args[0];
                                SharedState.outputConfiguration = new OutputConfiguration(SharedState.c2_virtual_surface);
                                SharedState.fake_sessionConfiguration = new SessionConfiguration(
                                        SharedState.sessionConfiguration.getSessionType(),
                                        Arrays.asList(SharedState.outputConfiguration),
                                        SharedState.sessionConfiguration.getExecutor(),
                                        SharedState.sessionConfiguration.getStateCallback());
                                param.args[0] = SharedState.fake_sessionConfiguration;
                                processCamera2SessionCallback(SharedState.sessionConfiguration.getStateCallback());
                            }
                        }
                    });
                }
            }
        });

        // Hook StateCallback.onError
        XposedHelpers.findAndHookMethod(hooked_class, "onError", CameraDevice.class, int.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                XposedBridge.log("【VCAM】相机错误onerror：" + (int) param.args[1]);
            }
        });

        // Hook StateCallback.onDisconnected
        XposedHelpers.findAndHookMethod(hooked_class, "onDisconnected", CameraDevice.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                XposedBridge.log("【VCAM】相机断开onDisconnected ：");
            }
        });
    }

    // ======================== createVirtualSurface ========================
    private static Surface createVirtualSurface() {
        if (SharedState.need_recreate) {
            if (SharedState.c2_virtual_surfaceTexture != null) {
                SharedState.c2_virtual_surfaceTexture.release();
                SharedState.c2_virtual_surfaceTexture = null;
            }
            if (SharedState.c2_virtual_surface != null) {
                SharedState.c2_virtual_surface.release();
                SharedState.c2_virtual_surface = null;
            }
            SharedState.c2_virtual_surfaceTexture = new SurfaceTexture(15);
            SharedState.c2_virtual_surface = new Surface(SharedState.c2_virtual_surfaceTexture);
            SharedState.need_recreate = false;
        } else {
            if (SharedState.c2_virtual_surface == null) {
                SharedState.need_recreate = true;
                SharedState.c2_virtual_surface = createVirtualSurface();
            }
        }
        XposedBridge.log("【VCAM】【重建垃圾场】" + SharedState.c2_virtual_surface.toString());
        return SharedState.c2_virtual_surface;
    }

    // ======================== processCamera2Play ========================
    public static void processCamera2Play() {
        // Reader Surface 1
        if (SharedState.c2_reader_Surfcae != null) {
            if (SharedState.c2_hw_decode_obj != null) {
                SharedState.c2_hw_decode_obj.stopDecode();
                SharedState.c2_hw_decode_obj = null;
            }
            SharedState.c2_hw_decode_obj = new VideoToFrames();
            try {
                if (SharedState.imageReaderFormat == 256) {
                    SharedState.c2_hw_decode_obj.setSaveFrames("null", OutputImageFormat.JPEG);
                } else {
                    SharedState.c2_hw_decode_obj.setSaveFrames("null", OutputImageFormat.NV21);
                }
                SharedState.c2_hw_decode_obj.set_surfcae(SharedState.c2_reader_Surfcae);
                SharedState.c2_hw_decode_obj.decode(HookGuards.getVideoFile().getAbsolutePath());
            } catch (Throwable throwable) {
                XposedBridge.log("【VCAM】" + throwable);
            }
        }

        // Reader Surface 2
        if (SharedState.c2_reader_Surfcae_1 != null) {
            if (SharedState.c2_hw_decode_obj_1 != null) {
                SharedState.c2_hw_decode_obj_1.stopDecode();
                SharedState.c2_hw_decode_obj_1 = null;
            }
            SharedState.c2_hw_decode_obj_1 = new VideoToFrames();
            try {
                if (SharedState.imageReaderFormat == 256) {
                    SharedState.c2_hw_decode_obj_1.setSaveFrames("null", OutputImageFormat.JPEG);
                } else {
                    SharedState.c2_hw_decode_obj_1.setSaveFrames("null", OutputImageFormat.NV21);
                }
                SharedState.c2_hw_decode_obj_1.set_surfcae(SharedState.c2_reader_Surfcae_1);
                SharedState.c2_hw_decode_obj_1.decode(HookGuards.getVideoFile().getAbsolutePath());
            } catch (Throwable throwable) {
                XposedBridge.log("【VCAM】" + throwable);
            }
        }

        // Preview Surface 1
        if (SharedState.c2_preview_Surfcae != null) {
            if (SharedState.c2_player == null) {
                SharedState.c2_player = new MediaPlayer();
            } else {
                SharedState.c2_player.release();
                SharedState.c2_player = new MediaPlayer();
            }
            SharedState.c2_player.setSurface(SharedState.c2_preview_Surfcae);
            if (!HookGuards.shouldPlaySound()) {
                SharedState.c2_player.setVolume(0, 0);
            }
            SharedState.c2_player.setLooping(true);
            try {
                SharedState.c2_player.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                    public void onPrepared(MediaPlayer mp) {
                        SharedState.c2_player.start();
                    }
                });
                SharedState.c2_player.setDataSource(HookGuards.getVideoFile().getAbsolutePath());
                SharedState.c2_player.prepare();
            } catch (Exception e) {
                XposedBridge.log("【VCAM】[c2player][" + SharedState.c2_preview_Surfcae.toString() + "]" + e);
            }
        }

        // Preview Surface 2
        if (SharedState.c2_preview_Surfcae_1 != null) {
            if (SharedState.c2_player_1 == null) {
                SharedState.c2_player_1 = new MediaPlayer();
            } else {
                SharedState.c2_player_1.release();
                SharedState.c2_player_1 = new MediaPlayer();
            }
            SharedState.c2_player_1.setSurface(SharedState.c2_preview_Surfcae_1);
            if (!HookGuards.shouldPlaySound()) {
                SharedState.c2_player_1.setVolume(0, 0);
            }
            SharedState.c2_player_1.setLooping(true);
            try {
                SharedState.c2_player_1.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                    public void onPrepared(MediaPlayer mp) {
                        SharedState.c2_player_1.start();
                    }
                });
                SharedState.c2_player_1.setDataSource(HookGuards.getVideoFile().getAbsolutePath());
                SharedState.c2_player_1.prepare();
            } catch (Exception e) {
                XposedBridge.log("【VCAM】[c2player1]" + "[ " + SharedState.c2_preview_Surfcae_1.toString() + "]" + e);
            }
        }
        XposedBridge.log("【VCAM】Camera2处理过程完全执行");
    }

    // ======================== processCamera2SessionCallback ========================
    private static void processCamera2SessionCallback(CameraCaptureSession.StateCallback callback_class) {
        if (callback_class == null) return;

        XposedHelpers.findAndHookMethod(callback_class.getClass(), "onConfigureFailed",
                CameraCaptureSession.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                XposedBridge.log("【VCAM】onConfigureFailed ：" + param.args[0].toString());
            }
        });

        XposedHelpers.findAndHookMethod(callback_class.getClass(), "onConfigured",
                CameraCaptureSession.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                XposedBridge.log("【VCAM】onConfigured ：" + param.args[0].toString());
            }
        });

        XposedHelpers.findAndHookMethod(callback_class.getClass(), "onClosed",
                CameraCaptureSession.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                XposedBridge.log("【VCAM】onClosed ：" + param.args[0].toString());
            }
        });
    }

    // ======================== 热切换：视频变更时重新加载播放器 ========================
    public static void reloadVideo() {
        HookGuards.getConfig().forceReload();
        File newFile = HookGuards.getVideoFile();
        String newPath = newFile.getAbsolutePath();
        boolean playSound = HookGuards.shouldPlaySound();

        XposedBridge.log("【VCAM】热切换视频 → " + newPath + " 声音=" + playSound);

        // Reader Surface 1 — 重启解码
        if (SharedState.c2_reader_Surfcae != null && SharedState.c2_hw_decode_obj != null) {
            try {
                SharedState.c2_hw_decode_obj.stopDecode();
                SharedState.c2_hw_decode_obj = new VideoToFrames();
                if (SharedState.imageReaderFormat == 256) {
                    SharedState.c2_hw_decode_obj.setSaveFrames("null", OutputImageFormat.JPEG);
                } else {
                    SharedState.c2_hw_decode_obj.setSaveFrames("null", OutputImageFormat.NV21);
                }
                SharedState.c2_hw_decode_obj.set_surfcae(SharedState.c2_reader_Surfcae);
                SharedState.c2_hw_decode_obj.decode(newPath);
            } catch (Throwable t) {
                XposedBridge.log("【VCAM】热切换 reader1 失败: " + t);
            }
        }

        // Reader Surface 2
        if (SharedState.c2_reader_Surfcae_1 != null && SharedState.c2_hw_decode_obj_1 != null) {
            try {
                SharedState.c2_hw_decode_obj_1.stopDecode();
                SharedState.c2_hw_decode_obj_1 = new VideoToFrames();
                if (SharedState.imageReaderFormat == 256) {
                    SharedState.c2_hw_decode_obj_1.setSaveFrames("null", OutputImageFormat.JPEG);
                } else {
                    SharedState.c2_hw_decode_obj_1.setSaveFrames("null", OutputImageFormat.NV21);
                }
                SharedState.c2_hw_decode_obj_1.set_surfcae(SharedState.c2_reader_Surfcae_1);
                SharedState.c2_hw_decode_obj_1.decode(newPath);
            } catch (Throwable t) {
                XposedBridge.log("【VCAM】热切换 reader2 失败: " + t);
            }
        }

        // Preview Surface 1 — 切换 MediaPlayer
        if (SharedState.c2_preview_Surfcae != null && SharedState.c2_player != null) {
            try {
                SharedState.c2_player.reset();
                SharedState.c2_player.setSurface(SharedState.c2_preview_Surfcae);
                SharedState.c2_player.setVolume(playSound ? 1f : 0f, playSound ? 1f : 0f);
                SharedState.c2_player.setLooping(true);
                SharedState.c2_player.setOnPreparedListener(mp -> SharedState.c2_player.start());
                SharedState.c2_player.setDataSource(newPath);
                SharedState.c2_player.prepare();
            } catch (Exception e) {
                XposedBridge.log("【VCAM】热切换 c2_player 失败: " + e);
            }
        }

        // Preview Surface 2
        if (SharedState.c2_preview_Surfcae_1 != null && SharedState.c2_player_1 != null) {
            try {
                SharedState.c2_player_1.reset();
                SharedState.c2_player_1.setSurface(SharedState.c2_preview_Surfcae_1);
                SharedState.c2_player_1.setVolume(playSound ? 1f : 0f, playSound ? 1f : 0f);
                SharedState.c2_player_1.setLooping(true);
                SharedState.c2_player_1.setOnPreparedListener(mp -> SharedState.c2_player_1.start());
                SharedState.c2_player_1.setDataSource(newPath);
                SharedState.c2_player_1.prepare();
            } catch (Exception e) {
                XposedBridge.log("【VCAM】热切换 c2_player_1 失败: " + e);
            }
        }

        XposedBridge.log("【VCAM】热切换完成");
    }

    // ======================== 声音开关：动态更新音量 ========================
    public static void updateSoundVolume() {
        boolean playSound = HookGuards.shouldPlaySound();
        float vol = playSound ? 1f : 0f;
        XposedBridge.log("【VCAM】更新音量 → " + (playSound ? "开" : "关"));

        if (SharedState.c2_player != null) {
            SharedState.c2_player.setVolume(vol, vol);
        }
        if (SharedState.c2_player_1 != null) {
            SharedState.c2_player_1.setVolume(vol, vol);
        }
    }
}