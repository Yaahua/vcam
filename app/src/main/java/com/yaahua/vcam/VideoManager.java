package com.yaahua.vcam;

import android.content.Context;

import java.io.File;
import java.util.Arrays;
import java.util.concurrent.ThreadLocalRandom;

public class VideoManager {
    public static String video_path = "/storage/emulated/0/DCIM/Camera1/";
    public static String current_video_path = null;
    public static final String CAM_VIDEO_NAME = "virtual.mp4";
    private static final Object pathLock = new Object();
    private static Context toast_content;
    private static ConfigManager configManager;

    private static final String[] VIDEO_EXTENSIONS = { ".mp4", ".mov", ".avi", ".mkv" };

    public static File[] listVideoFiles(File dir) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) return null;
        File[] files = dir.listFiles(file -> {
            String name = file.getName().toLowerCase();
            for (String ext : VIDEO_EXTENSIONS) {
                if (name.endsWith(ext)) return true;
            }
            return false;
        });
        if (files != null && files.length > 0) {
            Arrays.sort(files);
            return files;
        }
        return null;
    }

    public static void setContext(Context context) {
        toast_content = context;
        if (configManager != null) configManager.setContext(context);
    }

    public static void setConfigManager(ConfigManager manager) {
        configManager = manager;
    }

    public static ConfigManager getConfig() {
        if (configManager == null) {
            configManager = new ConfigManager();
            if (toast_content != null) configManager.setContext(toast_content);
        }
        return configManager;
    }

    /**
     * 解析 selected_video 值对应的文件。支持绝对路径和相对路径（相对 video_path）。
     * 返回 null 表示文件不存在。
     */
    private static File resolveVideoFile(String selectedValue, File dir) {
        if (selectedValue == null || selectedValue.isEmpty()) return null;
        // 绝对路径
        if (selectedValue.startsWith("/")) {
            File f = new File(selectedValue);
            if (f.exists() && !f.isDirectory()) return f;
            return null;
        }
        // 相对路径
        File f = new File(dir, selectedValue);
        if (f.exists() && !f.isDirectory()) return f;
        return null;
    }

    public static void updateVideoPath(boolean forceRandom) {
        synchronized (pathLock) {
            ConfigManager config = getConfig();
            File dir = new File(video_path);

            // 随机播放模式
            if (config.getBoolean(ConfigManager.KEY_ENABLE_RANDOM_PLAY, false)) {
                if (forceRandom) pickRandomVideoToConfig(config);
                String selected = config.getString(ConfigManager.KEY_SELECTED_VIDEO, null);
                if (selected != null) {
                    File f = resolveVideoFile(selected, dir);
                    if (f != null) {
                        current_video_path = f.getAbsolutePath();
                        LogUtil.log("【CS】[Random] 使用: " + current_video_path);
                        return;
                    }
                }
            }

            // 优先使用配置中选中的视频
            String selectedName = config.getString(ConfigManager.KEY_SELECTED_VIDEO, null);
            if (selectedName != null && !selectedName.isEmpty()) {
                File selectedFile = resolveVideoFile(selectedName, dir);
                if (selectedFile != null) {
                    current_video_path = selectedFile.getAbsolutePath();
                    LogUtil.log("【CS】[Video] 使用配置路径: " + current_video_path);
                    return;
                }
            }

            // 降级：virtual.mp4 → 目录中任意视频
            File camFile = new File(video_path, CAM_VIDEO_NAME);
            if (camFile.exists()) {
                current_video_path = camFile.getAbsolutePath();
                return;
            }
            File[] files = listVideoFiles(dir);
            if (files != null) {
                current_video_path = files[0].getAbsolutePath();
                LogUtil.log("【CS】[Video] 自动选择: " + files[0].getName());
                return;
            }
            current_video_path = camFile.getAbsolutePath();
            LogUtil.log("【CS】[Video] 警告：目录中无可用视频文件");
        }
    }

    private static void pickRandomVideoToConfig(ConfigManager config) {
        File dir = new File(video_path);
        if (!dir.exists() || !dir.isDirectory()) return;
        File[] files = listVideoFiles(dir);
        if (files != null && files.length > 0) {
            int index = ThreadLocalRandom.current().nextInt(files.length);
            config.setString(ConfigManager.KEY_SELECTED_VIDEO, files[index].getName());
            LogUtil.log("【CS】[Random] 选择了: " + files[index].getName());
        }
    }

    public static String getCurrentVideoPath() {
        synchronized (pathLock) {
            if (current_video_path == null) updateVideoPath(false);
            return current_video_path;
        }
    }

    public static boolean isStreamMode() {
        ConfigManager config = getConfig();
        String type = config.getString(ConfigManager.KEY_MEDIA_SOURCE_TYPE, ConfigManager.MEDIA_SOURCE_LOCAL);
        return ConfigManager.MEDIA_SOURCE_STREAM.equals(type);
    }

    public static boolean hasUsableMediaSource() {
        return getCurrentMediaSource().isValid();
    }

    public static MediaSourceDescriptor getCurrentMediaSource() {
        ConfigManager config = getConfig();
        String sourceType = config.getString(ConfigManager.KEY_MEDIA_SOURCE_TYPE, ConfigManager.MEDIA_SOURCE_LOCAL);
        if (ConfigManager.MEDIA_SOURCE_STREAM.equals(sourceType)) {
            String url = config.getString(ConfigManager.KEY_STREAM_URL, "");
            if (url != null && !url.isEmpty()) {
                return MediaSourceDescriptor.stream(url)
                        .autoReconnect(config.getBoolean(ConfigManager.KEY_STREAM_AUTO_RECONNECT, true))
                        .enableLocalFallback(config.getBoolean(ConfigManager.KEY_STREAM_LOCAL_FALLBACK, true))
                        .transportHint(config.getString(ConfigManager.KEY_STREAM_TRANSPORT_HINT, "auto"))
                        .timeoutMs(config.getLong(ConfigManager.KEY_STREAM_TIMEOUT_MS, 8000L))
                        .build();
            }
            return MediaSourceDescriptor.stream("").build();
        }
        String path = getCurrentVideoPath();
        return MediaSourceDescriptor.localFile(path != null ? path : "").build();
    }

    public static boolean switchVideo(boolean next) {
        File dir = new File(video_path);
        File[] files = listVideoFiles(dir);
        if (files == null || files.length == 0) return false;

        int currentIndex = -1;
        if (current_video_path != null) {
            // 检查当前视频是否在 video_path 目录内
            String currentParent = new File(current_video_path).getParent();
            if (currentParent != null && currentParent.equals(dir.getAbsolutePath())) {
                for (int i = 0; i < files.length; i++) {
                    if (files[i].getAbsolutePath().equals(current_video_path)) {
                        currentIndex = i;
                        break;
                    }
                }
            }
            // 如果当前视频在外部（绝对路径），currentIndex 保持 -1，回退到目录第一项
        }
        int newIndex = (currentIndex == -1) ? 0
                : (next ? (currentIndex + 1) % files.length : (currentIndex - 1 + files.length) % files.length);
        current_video_path = files[newIndex].getAbsolutePath();
        getConfig().setString(ConfigManager.KEY_SELECTED_VIDEO, files[newIndex].getName());
        return true;
    }
}