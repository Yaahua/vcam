package com.yaahua.vcam;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Environment;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.widget.Toast;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class Camera1Handler {

    public static void init(final XC_LoadPackage.LoadPackageParam lpparam) {
        hookSetPreviewTexture(lpparam);
        hookStartPreview(lpparam);
        hookSetPreviewDisplay(lpparam);
        hookSetPreviewCallbackWithBuffer(lpparam);
        hookAddCallbackBuffer(lpparam);
        hookSetPreviewCallback(lpparam);
        hookSetOneShotPreviewCallback(lpparam);
        hookTakePicture(lpparam);
    }

    // ======================== setPreviewTexture ========================
    private static void hookSetPreviewTexture(final XC_LoadPackage.LoadPackageParam lpparam) {
        XposedHelpers.findAndHookMethod("android.hardware.Camera", lpparam.classLoader, "setPreviewTexture",
                SurfaceTexture.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                File file = HookGuards.getVideoFile();
                if (file.exists()) {
                    if (HookGuards.isDisabled()) return;
                    if (SharedState.is_hooked) {
                        SharedState.is_hooked = false;
                        return;
                    }
                    if (param.args[0] == null) return;
                    if (param.args[0].equals(SharedState.c1_fake_texture)) return;

                    if (SharedState.origin_preview_camera != null &&
                            SharedState.origin_preview_camera.equals(param.thisObject)) {
                        param.args[0] = SharedState.fake_SurfaceTexture;
                        XposedBridge.log("【VCAM】发现重复" + SharedState.origin_preview_camera.toString());
                        return;
                    } else {
                        XposedBridge.log("【VCAM】创建预览");
                    }

                    SharedState.origin_preview_camera = (Camera) param.thisObject;
                    SharedState.mSurfacetexture = (SurfaceTexture) param.args[0];
                    if (SharedState.fake_SurfaceTexture == null) {
                        SharedState.fake_SurfaceTexture = new SurfaceTexture(10);
                    } else {
                        SharedState.fake_SurfaceTexture.release();
                        SharedState.fake_SurfaceTexture = new SurfaceTexture(10);
                    }
                    param.args[0] = SharedState.fake_SurfaceTexture;
                } else {
                    SharedState.need_to_show_toast = HookGuards.shouldShowToast();
                    if (SharedState.toast_content != null && SharedState.need_to_show_toast) {
                        try {
                            Toast.makeText(SharedState.toast_content,
                                    "不存在替换视频\n" + lpparam.packageName + "当前路径：" + SharedState.video_path,
                                    Toast.LENGTH_SHORT).show();
                        } catch (Exception ee) {
                            XposedBridge.log("【VCAM】[toast]" + ee.toString());
                        }
                    }
                }
            }
        });
    }

    // ======================== startPreview ========================
    private static void hookStartPreview(final XC_LoadPackage.LoadPackageParam lpparam) {
        XposedHelpers.findAndHookMethod("android.hardware.Camera", lpparam.classLoader, "startPreview",
                new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                File file = HookGuards.getVideoFile();
                SharedState.need_to_show_toast = HookGuards.shouldShowToast();
                if (!file.exists()) {
                    if (SharedState.toast_content != null && SharedState.need_to_show_toast) {
                        try {
                            Toast.makeText(SharedState.toast_content,
                                    "不存在替换视频\n" + lpparam.packageName + "当前路径：" + SharedState.video_path,
                                    Toast.LENGTH_SHORT).show();
                        } catch (Exception ee) {
                            XposedBridge.log("【VCAM】[toast]" + ee.toString());
                        }
                    }
                    return;
                }
                if (HookGuards.isDisabled()) return;

                SharedState.is_someone_playing = false;
                XposedBridge.log("【VCAM】开始预览");
                SharedState.start_preview_camera = (Camera) param.thisObject;

                // SurfaceHolder 播放器
                if (SharedState.ori_holder != null) {
                    if (SharedState.mplayer1 == null) {
                        SharedState.mplayer1 = new MediaPlayer();
                    } else {
                        SharedState.mplayer1.release();
                        SharedState.mplayer1 = null;
                        SharedState.mplayer1 = new MediaPlayer();
                    }
                    if (!SharedState.ori_holder.getSurface().isValid() || SharedState.ori_holder == null) {
                        return;
                    }
                    SharedState.mplayer1.setSurface(SharedState.ori_holder.getSurface());
                    if (!(HookGuards.shouldPlaySound() && (!SharedState.is_someone_playing))) {
                        SharedState.mplayer1.setVolume(0, 0);
                        SharedState.is_someone_playing = false;
                    } else {
                        SharedState.is_someone_playing = true;
                    }
                    SharedState.mplayer1.setLooping(true);
                    SharedState.mplayer1.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                        @Override
                        public void onPrepared(MediaPlayer mp) {
                            SharedState.mplayer1.start();
                        }
                    });
                    try {
                        SharedState.mplayer1.setDataSource(HookGuards.getVideoFile().getAbsolutePath());
                        SharedState.mplayer1.prepare();
                    } catch (IOException e) {
                        XposedBridge.log("【VCAM】" + e.toString());
                    }
                }

                // SurfaceTexture 播放器
                if (SharedState.mSurfacetexture != null) {
                    if (SharedState.mSurface == null) {
                        SharedState.mSurface = new Surface(SharedState.mSurfacetexture);
                    } else {
                        SharedState.mSurface.release();
                        SharedState.mSurface = new Surface(SharedState.mSurfacetexture);
                    }
                    if (SharedState.mMediaPlayer == null) {
                        SharedState.mMediaPlayer = new MediaPlayer();
                    } else {
                        SharedState.mMediaPlayer.release();
                        SharedState.mMediaPlayer = new MediaPlayer();
                    }
                    SharedState.mMediaPlayer.setSurface(SharedState.mSurface);
                    if (!(HookGuards.shouldPlaySound() && (!SharedState.is_someone_playing))) {
                        SharedState.mMediaPlayer.setVolume(0, 0);
                        SharedState.is_someone_playing = false;
                    } else {
                        SharedState.is_someone_playing = true;
                    }
                    SharedState.mMediaPlayer.setLooping(true);
                    SharedState.mMediaPlayer.setOnPreparedListener(new MediaPlayer.OnPreparedListener() {
                        @Override
                        public void onPrepared(MediaPlayer mp) {
                            SharedState.mMediaPlayer.start();
                        }
                    });
                    try {
                        SharedState.mMediaPlayer.setDataSource(HookGuards.getVideoFile().getAbsolutePath());
                        SharedState.mMediaPlayer.prepare();
                    } catch (IOException e) {
                        XposedBridge.log("【VCAM】" + e.toString());
                    }
                }
            }
        });
    }

    // ======================== setPreviewDisplay ========================
    private static void hookSetPreviewDisplay(final XC_LoadPackage.LoadPackageParam lpparam) {
        XposedHelpers.findAndHookMethod("android.hardware.Camera", lpparam.classLoader, "setPreviewDisplay",
                SurfaceHolder.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                XposedBridge.log("【VCAM】添加Surfaceview预览");
                File file = HookGuards.getVideoFile();
                SharedState.need_to_show_toast = HookGuards.shouldShowToast();
                if (!file.exists()) {
                    if (SharedState.toast_content != null && SharedState.need_to_show_toast) {
                        try {
                            Toast.makeText(SharedState.toast_content,
                                    "不存在替换视频\n" + lpparam.packageName + "当前路径：" + SharedState.video_path,
                                    Toast.LENGTH_SHORT).show();
                        } catch (Exception ee) {
                            XposedBridge.log("【VCAM】[toast]" + ee.toString());
                        }
                    }
                    return;
                }
                if (HookGuards.isDisabled()) return;

                SharedState.mcamera1 = (Camera) param.thisObject;
                SharedState.ori_holder = (SurfaceHolder) param.args[0];

                if (SharedState.c1_fake_texture == null) {
                    SharedState.c1_fake_texture = new SurfaceTexture(11);
                } else {
                    SharedState.c1_fake_texture.release();
                    SharedState.c1_fake_texture = null;
                    SharedState.c1_fake_texture = new SurfaceTexture(11);
                }
                if (SharedState.c1_fake_surface == null) {
                    SharedState.c1_fake_surface = new Surface(SharedState.c1_fake_texture);
                } else {
                    SharedState.c1_fake_surface.release();
                    SharedState.c1_fake_surface = null;
                    SharedState.c1_fake_surface = new Surface(SharedState.c1_fake_texture);
                }
                SharedState.is_hooked = true;
                SharedState.mcamera1.setPreviewTexture(SharedState.c1_fake_texture);
                param.setResult(null);
            }
        });
    }

    // ======================== setPreviewCallbackWithBuffer ========================
    private static void hookSetPreviewCallbackWithBuffer(final XC_LoadPackage.LoadPackageParam lpparam) {
        XposedHelpers.findAndHookMethod("android.hardware.Camera", lpparam.classLoader,
                "setPreviewCallbackWithBuffer", Camera.PreviewCallback.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (param.args[0] != null) {
                    processCallback(param);
                }
            }
        });
    }

    // ======================== addCallbackBuffer ========================
    private static void hookAddCallbackBuffer(final XC_LoadPackage.LoadPackageParam lpparam) {
        XposedHelpers.findAndHookMethod("android.hardware.Camera", lpparam.classLoader, "addCallbackBuffer",
                byte[].class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (param.args[0] != null) {
                    param.args[0] = new byte[((byte[]) param.args[0]).length];
                }
            }
        });
    }

    // ======================== setPreviewCallback ========================
    private static void hookSetPreviewCallback(final XC_LoadPackage.LoadPackageParam lpparam) {
        XposedHelpers.findAndHookMethod("android.hardware.Camera", lpparam.classLoader, "setPreviewCallback",
                Camera.PreviewCallback.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (param.args[0] != null) {
                    processCallback(param);
                }
            }
        });
    }

    // ======================== setOneShotPreviewCallback ========================
    private static void hookSetOneShotPreviewCallback(final XC_LoadPackage.LoadPackageParam lpparam) {
        XposedHelpers.findAndHookMethod("android.hardware.Camera", lpparam.classLoader, "setOneShotPreviewCallback",
                Camera.PreviewCallback.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (param.args[0] != null) {
                    processCallback(param);
                }
            }
        });
    }

    // ======================== takePicture ========================
    private static void hookTakePicture(final XC_LoadPackage.LoadPackageParam lpparam) {
        XposedHelpers.findAndHookMethod("android.hardware.Camera", lpparam.classLoader, "takePicture",
                Camera.ShutterCallback.class, Camera.PictureCallback.class,
                Camera.PictureCallback.class, Camera.PictureCallback.class, new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                XposedBridge.log("【VCAM】4参数拍照");
                if (param.args[1] != null) {
                    processAShotYUV(param);
                }
                if (param.args[3] != null) {
                    processAShotJPEG(param, 3);
                }
            }
        });
    }

    // ======================== processCallback ========================
    private static void processCallback(XC_MethodHook.MethodHookParam param) {
        Class preview_cb_class = param.args[0].getClass();
        int need_stop = 0;
        if (HookGuards.isDisabled()) {
            need_stop = 1;
        }
        File file = HookGuards.getVideoFile();
        SharedState.need_to_show_toast = HookGuards.shouldShowToast();
        if (!file.exists()) {
            if (SharedState.toast_content != null && SharedState.need_to_show_toast) {
                try {
                    Toast.makeText(SharedState.toast_content,
                            "不存在替换视频\n" + SharedState.toast_content.getPackageName() + "当前路径：" + SharedState.video_path,
                            Toast.LENGTH_SHORT).show();
                } catch (Exception ee) {
                    XposedBridge.log("【VCAM】[toast]" + ee);
                }
            }
            need_stop = 1;
        }
        final int finalNeed_stop = need_stop;
        XposedHelpers.findAndHookMethod(preview_cb_class, "onPreviewFrame",
                byte[].class, android.hardware.Camera.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam paramd) throws Throwable {
                Camera localcam = (android.hardware.Camera) paramd.args[1];
                if (localcam.equals(SharedState.camera_onPreviewFrame)) {
                    while (SharedState.data_buffer == null) {
                        // wait
                    }
                    System.arraycopy(SharedState.data_buffer, 0, paramd.args[0], 0,
                            Math.min(SharedState.data_buffer.length, ((byte[]) paramd.args[0]).length));
                } else {
                    SharedState.camera_callback_calss = preview_cb_class;
                    SharedState.camera_onPreviewFrame = (android.hardware.Camera) paramd.args[1];
                    SharedState.mwidth = SharedState.camera_onPreviewFrame.getParameters().getPreviewSize().width;
                    SharedState.mhight = SharedState.camera_onPreviewFrame.getParameters().getPreviewSize().height;
                    int frame_Rate = SharedState.camera_onPreviewFrame.getParameters().getPreviewFrameRate();
                    XposedBridge.log("【VCAM】帧预览回调初始化：宽：" + SharedState.mwidth +
                            " 高：" + SharedState.mhight + " 帧率：" + frame_Rate);
                    SharedState.need_to_show_toast = HookGuards.shouldShowToast();
                    if (SharedState.toast_content != null && SharedState.need_to_show_toast) {
                        try {
                            Toast.makeText(SharedState.toast_content, "发现预览\n宽：" + SharedState.mwidth +
                                    "\n高：" + SharedState.mhight + "\n" + "需要视频分辨率与其完全相同", Toast.LENGTH_SHORT).show();
                        } catch (Exception ee) {
                            XposedBridge.log("【VCAM】[toast]" + ee.toString());
                        }
                    }
                    if (finalNeed_stop == 1) return;

                    if (SharedState.hw_decode_obj != null) {
                        SharedState.hw_decode_obj.stopDecode();
                    }
                    SharedState.hw_decode_obj = new VideoToFrames();
                    SharedState.hw_decode_obj.setSaveFrames("", OutputImageFormat.NV21);
                    SharedState.hw_decode_obj.decode(HookGuards.getVideoFile().getAbsolutePath());
                    while (SharedState.data_buffer == null) {
                        // wait
                    }
                    System.arraycopy(SharedState.data_buffer, 0, paramd.args[0], 0,
                            Math.min(SharedState.data_buffer.length, ((byte[]) paramd.args[0]).length));
                }
            }
        });
    }

    // ======================== processAShotYUV ========================
    private static void processAShotYUV(XC_MethodHook.MethodHookParam param) {
        try {
            XposedBridge.log("【VCAM】发现拍照YUV:" + param.args[1].toString());
        } catch (Exception eee) {
            XposedBridge.log("【VCAM】" + eee);
        }
        Class callback = param.args[1].getClass();
        XposedHelpers.findAndHookMethod(callback, "onPictureTaken",
                byte[].class, android.hardware.Camera.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam paramd) throws Throwable {
                try {
                    Camera loaclcam = (Camera) paramd.args[1];
                    SharedState.onemwidth = loaclcam.getParameters().getPreviewSize().width;
                    SharedState.onemhight = loaclcam.getParameters().getPreviewSize().height;
                    XposedBridge.log("【VCAM】YUV拍照回调初始化：宽：" + SharedState.onemwidth +
                            "高：" + SharedState.onemhight + "对应的类：" + loaclcam.toString());
                    SharedState.need_to_show_toast = HookGuards.shouldShowToast();
                    if (SharedState.toast_content != null && SharedState.need_to_show_toast) {
                        try {
                            Toast.makeText(SharedState.toast_content, "发现拍照\n宽：" + SharedState.onemwidth +
                                    "\n高：" + SharedState.onemhight + "\n格式：YUV_420_888", Toast.LENGTH_SHORT).show();
                        } catch (Exception e) {
                            XposedBridge.log("【VCAM】[toast]" + e.toString());
                        }
                    }
                    if (HookGuards.isDisabled()) return;
                    SharedState.input = getYUVByBitmap(getBMP(SharedState.video_path + "1000.bmp"));
                    paramd.args[0] = SharedState.input;
                } catch (Exception ee) {
                    XposedBridge.log("【VCAM】" + ee.toString());
                }
            }
        });
    }

    // ======================== processAShotJPEG ========================
    private static void processAShotJPEG(XC_MethodHook.MethodHookParam param, int index) {
        try {
            XposedBridge.log("【VCAM】第二个jpeg:" + param.args[index].toString());
        } catch (Exception eee) {
            XposedBridge.log("【VCAM】" + eee);
        }
        Class callback = param.args[index].getClass();
        XposedHelpers.findAndHookMethod(callback, "onPictureTaken",
                byte[].class, android.hardware.Camera.class, new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam paramd) throws Throwable {
                try {
                    Camera loaclcam = (Camera) paramd.args[1];
                    SharedState.onemwidth = loaclcam.getParameters().getPreviewSize().width;
                    SharedState.onemhight = loaclcam.getParameters().getPreviewSize().height;
                    XposedBridge.log("【VCAM】JPEG拍照回调初始化：宽：" + SharedState.onemwidth +
                            "高：" + SharedState.onemhight + "对应的类：" + loaclcam.toString());
                    SharedState.need_to_show_toast = HookGuards.shouldShowToast();
                    if (SharedState.toast_content != null && SharedState.need_to_show_toast) {
                        try {
                            Toast.makeText(SharedState.toast_content, "发现拍照\n宽：" + SharedState.onemwidth +
                                    "\n高：" + SharedState.onemhight + "\n格式：JPEG", Toast.LENGTH_SHORT).show();
                        } catch (Exception e) {
                            XposedBridge.log("【VCAM】[toast]" + e.toString());
                        }
                    }
                    if (HookGuards.isDisabled()) return;
                    Bitmap pict = getBMP(SharedState.video_path + "1000.bmp");
                    ByteArrayOutputStream temp_array = new ByteArrayOutputStream();
                    pict.compress(Bitmap.CompressFormat.JPEG, 100, temp_array);
                    byte[] jpeg_data = temp_array.toByteArray();
                    paramd.args[0] = jpeg_data;
                } catch (Exception ee) {
                    XposedBridge.log("【VCAM】" + ee.toString());
                }
            }
        });
    }

    // ======================== 工具方法 ========================
    private static Bitmap getBMP(String file) throws Throwable {
        return BitmapFactory.decodeFile(file);
    }

    private static byte[] rgb2YCbCr420(int[] pixels, int width, int height) {
        int len = width * height;
        byte[] yuv = new byte[len * 3 / 2];
        int y, u, v;
        for (int i = 0; i < height; i++) {
            for (int j = 0; j < width; j++) {
                int rgb = (pixels[i * width + j]) & 0x00FFFFFF;
                int r = rgb & 0xFF;
                int g = (rgb >> 8) & 0xFF;
                int b = (rgb >> 16) & 0xFF;
                y = ((66 * r + 129 * g + 25 * b + 128) >> 8) + 16;
                u = ((-38 * r - 74 * g + 112 * b + 128) >> 8) + 128;
                v = ((112 * r - 94 * g - 18 * b + 128) >> 8) + 128;
                y = y < 16 ? 16 : (Math.min(y, 255));
                u = u < 0 ? 0 : (Math.min(u, 255));
                v = v < 0 ? 0 : (Math.min(v, 255));
                yuv[i * width + j] = (byte) y;
                yuv[len + (i >> 1) * width + (j & ~1)] = (byte) u;
                yuv[len + +(i >> 1) * width + (j & ~1) + 1] = (byte) v;
            }
        }
        return yuv;
    }

    private static byte[] getYUVByBitmap(Bitmap bitmap) {
        if (bitmap == null) return null;
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int size = width * height;
        int[] pixels = new int[size];
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height);
        return rgb2YCbCr420(pixels, width, height);
    }

    // ======================== 热切换：视频变更时重新加载播放器 ========================
    public static void reloadVideo() {
        HookGuards.getConfig().forceReload();
        if (HookGuards.isDisabled()) {
            stopAllPlayers();
            return;
        }
        File newFile = HookGuards.getVideoFile();
        String newPath = newFile.getAbsolutePath();
        boolean playSound = HookGuards.shouldPlaySound();
        XposedBridge.log("【VCAM】Camera1 热切换视频 → " + newPath);

        // MediaPlayer (SurfaceTexture 预览)
        if (SharedState.mSurfacetexture != null && SharedState.mMediaPlayer != null) {
            try {
                SharedState.mMediaPlayer.reset();
                SharedState.mMediaPlayer.setSurface(SharedState.mSurface);
                SharedState.mMediaPlayer.setVolume(playSound ? 1f : 0f, playSound ? 1f : 0f);
                SharedState.mMediaPlayer.setLooping(true);
                SharedState.mMediaPlayer.setOnPreparedListener(mp -> SharedState.mMediaPlayer.start());
                SharedState.mMediaPlayer.setDataSource(newPath);
                SharedState.mMediaPlayer.prepare();
            } catch (Exception e) {
                XposedBridge.log("【VCAM】Camera1 热切换 mMediaPlayer 失败: " + e);
            }
        }

        // mplayer1 (SurfaceHolder 预览)
        if (SharedState.ori_holder != null && SharedState.mplayer1 != null) {
            try {
                SharedState.mplayer1.reset();
                SharedState.mplayer1.setSurface(SharedState.ori_holder.getSurface());
                SharedState.mplayer1.setVolume(playSound ? 1f : 0f, playSound ? 1f : 0f);
                SharedState.mplayer1.setLooping(true);
                SharedState.mplayer1.setOnPreparedListener(mp -> SharedState.mplayer1.start());
                SharedState.mplayer1.setDataSource(newPath);
                SharedState.mplayer1.prepare();
            } catch (Exception e) {
                XposedBridge.log("【VCAM】Camera1 热切换 mplayer1 失败: " + e);
            }
        }

        // 解码器
        if (SharedState.hw_decode_obj != null) {
            try {
                SharedState.hw_decode_obj.stopDecode();
                SharedState.hw_decode_obj = new VideoToFrames();
                SharedState.hw_decode_obj.setSaveFrames("", OutputImageFormat.NV21);
                SharedState.hw_decode_obj.decode(newPath);
            } catch (Throwable t) {
                XposedBridge.log("【VCAM】Camera1 热切换 hw_decode 失败: " + t);
            }
        }

        XposedBridge.log("【VCAM】Camera1 热切换完成");
    }

    // ======================== 声音开关：动态更新音量 ========================
    public static void updateSoundVolume() {
        boolean playSound = HookGuards.shouldPlaySound();
        float vol = playSound ? 1f : 0f;
        XposedBridge.log("【VCAM】Camera1 更新音量 → " + (playSound ? "开" : "关"));

        if (SharedState.mMediaPlayer != null) {
            SharedState.mMediaPlayer.setVolume(vol, vol);
        }
        if (SharedState.mplayer1 != null) {
            SharedState.mplayer1.setVolume(vol, vol);
        }
    }

    // ======================== 停止所有播放器（禁用模块时调用） ========================
    public static void stopAllPlayers() {
        XposedBridge.log("【VCAM】Camera1 停止所有播放器/解码器");
        try {
            if (SharedState.mMediaPlayer != null) {
                SharedState.mMediaPlayer.stop();
                SharedState.mMediaPlayer.reset();
                SharedState.mMediaPlayer.release();
                SharedState.mMediaPlayer = null;
            }
        } catch (Exception e) {
            XposedBridge.log("【VCAM】Camera1 stop mMediaPlayer: " + e);
        }
        try {
            if (SharedState.mplayer1 != null) {
                SharedState.mplayer1.stop();
                SharedState.mplayer1.reset();
                SharedState.mplayer1.release();
                SharedState.mplayer1 = null;
            }
        } catch (Exception e) {
            XposedBridge.log("【VCAM】Camera1 stop mplayer1: " + e);
        }
        try {
            if (SharedState.hw_decode_obj != null) {
                SharedState.hw_decode_obj.stopDecode();
                SharedState.hw_decode_obj = null;
            }
        } catch (Exception e) {
            XposedBridge.log("【VCAM】Camera1 stop hw_decode: " + e);
        }
    }
}