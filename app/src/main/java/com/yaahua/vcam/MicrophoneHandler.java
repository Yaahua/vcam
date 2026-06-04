package com.yaahua.vcam;

import android.hardware.Camera;
import android.media.AudioRecord;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaRecorder;
import android.os.Environment;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.util.Arrays;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class MicrophoneHandler {

    private static byte[] cachedAudioData = null;
    private static String cachedAudioPath = null;
    private static int cachedAudioPos = 0;

    public static void init(final XC_LoadPackage.LoadPackageParam lpparam) {
        // Hook AudioRecord.read — 拦截音频读取，实现静音/替换
        hookAudioRecordRead(lpparam);

        // Hook MediaRecorder.setAudioSource — 检测录音行为
        hookMediaRecorderSetAudioSource(lpparam);

        // Hook MediaRecorder.setCamera — 检测录像行为
        hookMediaRecorderSetCamera(lpparam);
    }

    // ======================== AudioRecord.read ========================
    private static void hookAudioRecordRead(final XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(AudioRecord.class, "read",
                    byte[].class, int.class, int.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (!HookGuards.getConfig().getBoolean(ConfigManager.KEY_ENABLE_MIC_HOOK, false)) return;
                    String mode = HookGuards.getConfig().getString(ConfigManager.KEY_MIC_HOOK_MODE, ConfigManager.MIC_MODE_MUTE);
                    byte[] buffer = (byte[]) param.args[0];
                    int offset = (int) param.args[1];
                    int size = (int) param.args[2];

                    if (ConfigManager.MIC_MODE_MUTE.equals(mode)) {
                        // 静音：填充零值
                        Arrays.fill(buffer, offset, offset + size, (byte) 0);
                        param.setResult(size);
                    } else if (ConfigManager.MIC_MODE_REPLACE.equals(mode) || ConfigManager.MIC_MODE_VIDEO_SYNC.equals(mode)) {
                        // 替换：用预录制音频填充
                        loadAudioDataIfNeeded();
                        if (cachedAudioData != null) {
                            int remaining = cachedAudioData.length - cachedAudioPos;
                            if (remaining <= 0) cachedAudioPos = 0; // 循环
                            int copyLen = Math.min(size, cachedAudioData.length - cachedAudioPos);
                            System.arraycopy(cachedAudioData, cachedAudioPos, buffer, offset, copyLen);
                            cachedAudioPos += copyLen;
                            param.setResult(copyLen);
                        }
                    }
                }
            });

            // 双参数版本: read(byte[], int)
            XposedHelpers.findAndHookMethod(AudioRecord.class, "read",
                    byte[].class, int.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (!HookGuards.getConfig().getBoolean(ConfigManager.KEY_ENABLE_MIC_HOOK, false)) return;
                    String mode = HookGuards.getConfig().getString(ConfigManager.KEY_MIC_HOOK_MODE, ConfigManager.MIC_MODE_MUTE);
                    byte[] buffer = (byte[]) param.args[0];
                    int offset = (int) param.args[1];

                    if (ConfigManager.MIC_MODE_MUTE.equals(mode)) {
                        Arrays.fill(buffer, offset, buffer.length, (byte) 0);
                        param.setResult(buffer.length - offset);
                    } else if (ConfigManager.MIC_MODE_REPLACE.equals(mode) || ConfigManager.MIC_MODE_VIDEO_SYNC.equals(mode)) {
                        loadAudioDataIfNeeded();
                        if (cachedAudioData != null) {
                            int remaining = cachedAudioData.length - cachedAudioPos;
                            if (remaining <= 0) cachedAudioPos = 0;
                            int copyLen = Math.min(buffer.length - offset, cachedAudioData.length - cachedAudioPos);
                            System.arraycopy(cachedAudioData, cachedAudioPos, buffer, offset, copyLen);
                            cachedAudioPos += copyLen;
                            param.setResult(copyLen);
                        }
                    }
                }
            });

            // 短读单参数: read(short[], int, int)
            XposedHelpers.findAndHookMethod(AudioRecord.class, "read",
                    short[].class, int.class, int.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (!HookGuards.getConfig().getBoolean(ConfigManager.KEY_ENABLE_MIC_HOOK, false)) return;
                    String mode = HookGuards.getConfig().getString(ConfigManager.KEY_MIC_HOOK_MODE, ConfigManager.MIC_MODE_MUTE);
                    short[] buffer = (short[]) param.args[0];
                    int offset = (int) param.args[1];
                    int size = (int) param.args[2];

                    if (ConfigManager.MIC_MODE_MUTE.equals(mode)) {
                        Arrays.fill(buffer, offset, offset + size, (short) 0);
                        param.setResult(size);
                    } else if (ConfigManager.MIC_MODE_REPLACE.equals(mode) || ConfigManager.MIC_MODE_VIDEO_SYNC.equals(mode)) {
                        loadAudioDataIfNeeded();
                        if (cachedAudioData != null) {
                            int remaining = (cachedAudioData.length - cachedAudioPos) / 2;
                            if (remaining <= 0) cachedAudioPos = 0;
                            int copyLen = Math.min(size, remaining);
                            for (int i = 0; i < copyLen; i++) {
                                int idx = cachedAudioPos + i * 2;
                                if (idx + 1 < cachedAudioData.length) {
                                    buffer[offset + i] = (short) ((cachedAudioData[idx + 1] << 8) | (cachedAudioData[idx] & 0xFF));
                                }
                            }
                            cachedAudioPos += copyLen * 2;
                            param.setResult(copyLen);
                        }
                    }
                }
            });

            XposedBridge.log("【VCAM】Microphone AudioRecord Hook 已安装");
        } catch (Throwable e) {
            XposedBridge.log("【VCAM】Microphone AudioRecord Hook 失败: " + e);
        }
    }

    private static void loadAudioDataIfNeeded() {
        String mode = HookGuards.getConfig().getString(ConfigManager.KEY_MIC_HOOK_MODE, ConfigManager.MIC_MODE_MUTE);

        // video_sync 模式：从视频提取音频轨道
        if (ConfigManager.MIC_MODE_VIDEO_SYNC.equals(mode)) {
            loadAudioFromVideo();
            return;
        }

        String selectedAudio = HookGuards.getConfig().getString(ConfigManager.KEY_SELECTED_AUDIO, null);
        String audioPath = (selectedAudio != null && !selectedAudio.isEmpty())
                ? new File(ConfigManager.DEFAULT_CONFIG_DIR, selectedAudio).getAbsolutePath()
                : ConfigManager.DEFAULT_CONFIG_DIR + "Mic.mp3";

        if (audioPath.equals(cachedAudioPath) && cachedAudioData != null) return;
        cachedAudioPath = audioPath;
        cachedAudioPos = 0;

        try {
            File audioFile = new File(audioPath);
            if (!audioFile.exists()) {
                // 尝试找目录中的任一音频文件
                File dir = new File(ConfigManager.DEFAULT_CONFIG_DIR);
                File[] files = dir.listFiles((d, name) -> {
                    String lower = name.toLowerCase();
                    return lower.endsWith(".mp3") || lower.endsWith(".wav") || lower.endsWith(".aac")
                            || lower.endsWith(".m4a") || lower.endsWith(".ogg") || lower.endsWith(".flac");
                });
                if (files != null && files.length > 0) {
                    audioFile = files[0];
                    cachedAudioPath = audioFile.getAbsolutePath();
                } else {
                    cachedAudioData = null;
                    return;
                }
            }

            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            try (FileInputStream fis = new FileInputStream(audioFile)) {
                byte[] buf = new byte[4096];
                int read;
                while ((read = fis.read(buf)) != -1) {
                    bos.write(buf, 0, read);
                }
            }
            cachedAudioData = bos.toByteArray();
            XposedBridge.log("【VCAM】麦克风音频已加载: " + cachedAudioPath + " (" + cachedAudioData.length + " bytes)");
        } catch (Exception e) {
            XposedBridge.log("【VCAM】加载麦克风音频失败: " + e);
            cachedAudioData = null;
        }
    }

    // 从当前视频文件提取音频轨道（video_sync 模式）
    private static void loadAudioFromVideo() {
        try {
            java.io.File videoFile = HookGuards.getVideoFile();
            if (videoFile == null || !videoFile.exists()) {
                cachedAudioData = null;
                return;
            }
            String videoPath = videoFile.getAbsolutePath();
            if (videoPath.equals(cachedAudioPath) && cachedAudioData != null) return;
            cachedAudioPath = videoPath;
            cachedAudioPos = 0;

            MediaExtractor extractor = new MediaExtractor();
            extractor.setDataSource(videoPath);

            // 查找音频轨道
            int audioTrack = -1;
            for (int i = 0; i < extractor.getTrackCount(); i++) {
                MediaFormat fmt = extractor.getTrackFormat(i);
                String mime = fmt.getString(MediaFormat.KEY_MIME);
                if (mime != null && mime.startsWith("audio/")) {
                    audioTrack = i;
                    break;
                }
            }
            if (audioTrack < 0) {
                extractor.release();
                cachedAudioData = null;
                XposedBridge.log("【VCAM】视频无音频轨道，video_sync 回退到静音");
                return;
            }

            extractor.selectTrack(audioTrack);
            java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
            java.nio.ByteBuffer buf = java.nio.ByteBuffer.allocate(65536);
            MediaCodec.BufferInfo info = new MediaCodec.BufferInfo();

            // 尝试用 MediaCodec 解码音频为 PCM
            MediaFormat fmt = extractor.getTrackFormat(audioTrack);
            String mime = fmt.getString(MediaFormat.KEY_MIME);
            MediaCodec decoder = null;
            boolean sawOutputEOS = false;

            try {
                decoder = MediaCodec.createDecoderByType(mime);
                decoder.configure(fmt, null, null, 0);
                decoder.start();

                while (!sawOutputEOS) {
                    int inIdx = decoder.dequeueInputBuffer(10000);
                    if (inIdx >= 0) {
                        java.nio.ByteBuffer inBuf = decoder.getInputBuffer(inIdx);
                        int sampleSize = extractor.readSampleData(inBuf, 0);
                        if (sampleSize < 0) {
                            decoder.queueInputBuffer(inIdx, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM);
                        } else {
                            long pts = extractor.getSampleTime();
                            decoder.queueInputBuffer(inIdx, 0, sampleSize, pts, 0);
                            extractor.advance();
                        }
                    }
                    int outIdx = decoder.dequeueOutputBuffer(info, 10000);
                    if (outIdx >= 0) {
                        if ((info.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            sawOutputEOS = true;
                        }
                        if (info.size > 0) {
                            java.nio.ByteBuffer outBuf = decoder.getOutputBuffer(outIdx);
                            byte[] chunk = new byte[info.size];
                            outBuf.get(chunk);
                            bos.write(chunk);
                        }
                        decoder.releaseOutputBuffer(outIdx, false);
                    }
                }
            } catch (Exception e) {
                XposedBridge.log("【VCAM】音频解码失败，尝试直接提取原始数据: " + e);
                // 回退：直接读取原始压缩数据
                extractor.seekTo(0, 0);
                while (true) {
                    buf.clear();
                    int n = extractor.readSampleData(buf, 0);
                    if (n < 0) break;
                    byte[] chunk = new byte[n];
                    buf.position(0);
                    buf.get(chunk);
                    bos.write(chunk);
                    extractor.advance();
                }
            }

            if (decoder != null) {
                decoder.stop();
                decoder.release();
            }
            extractor.release();

            cachedAudioData = bos.toByteArray();
            XposedBridge.log("【VCAM】video_sync 音频已提取: " + cachedAudioData.length + " bytes");
        } catch (Exception e) {
            XposedBridge.log("【VCAM】video_sync 提取音频失败: " + e);
            cachedAudioData = null;
        }
    }

    // ======================== MediaRecorder.setAudioSource ========================
    private static void hookMediaRecorderSetAudioSource(final XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(MediaRecorder.class, "setAudioSource",
                    int.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) throws Throwable {
                    if (!HookGuards.getConfig().getBoolean(ConfigManager.KEY_ENABLE_MIC_HOOK, false)) return;
                    XposedBridge.log("【VCAM】MediaRecorder.setAudioSource: " + param.args[0]);
                }
            });
        } catch (Throwable e) {
            XposedBridge.log("【VCAM】MediaRecorder.setAudioSource Hook 失败: " + e);
        }
    }

    // ======================== MediaRecorder.setCamera（保留原有逻辑）=======================
    private static void hookMediaRecorderSetCamera(final XC_LoadPackage.LoadPackageParam lpparam) {
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