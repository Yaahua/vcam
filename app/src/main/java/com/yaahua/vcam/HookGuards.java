package com.yaahua.vcam;

import android.os.Environment;

import java.io.File;

public class HookGuards {
    private HookGuards() {}

    private static ConfigManager configManager;
    private static volatile boolean configDirty = true;

    public static void setConfigManager(ConfigManager cm) {
        configManager = cm;
        configDirty = false;
    }

    /** 标记配置已过期，下次 getConfig() 强制重载 */
    public static void invalidateConfig() {
        configDirty = true;
    }

    private static long configFileLastModified = 0;

    private static final String[] VIDEO_EXTENSIONS = { ".mp4", ".mov", ".avi", ".mkv" };

    public static ConfigManager getConfig() {
        if (configManager == null) {
            configManager = new ConfigManager(false);
            configManager.reload();
            configFileLastModified = getConfigFileMtime();
            configDirty = false;
        } else if (configDirty) {
            configManager.forceReload();
            configFileLastModified = getConfigFileMtime();
            configDirty = false;
        } else {
            // mtime 仍作为兜底（文件系统被动监听场景）
            long currentMtime = getConfigFileMtime();
            if (currentMtime > configFileLastModified) {
                configManager.forceReload();
                configFileLastModified = currentMtime;
            }
        }
        return configManager;
    }

    private static long getConfigFileMtime() {
        try {
            File f = new File(ConfigManager.DEFAULT_CONFIG_DIR, ConfigManager.CONFIG_FILE_NAME);
            return f.lastModified();
        } catch (Exception e) {
            return 0;
        }
    }

    public static File getVideoFile() {
        ConfigManager config = getConfig();
        String selectedName = config.getString(ConfigManager.KEY_SELECTED_VIDEO, null);
        File dir = new File(SharedState.video_path);
        if (selectedName != null && !selectedName.isEmpty()) {
            if (selectedName.startsWith("/")) {
                File absolute = new File(selectedName);
                if (absolute.exists() && !absolute.isDirectory()) return absolute;
            }
            File selected = new File(dir, selectedName);
            if (selected.exists() && !selected.isDirectory()) return selected;
        }
        // 回退：目录中任意视频（支持四种格式）
        File[] files = dir.listFiles((d, name) -> {
            String lower = name.toLowerCase();
            for (String ext : VIDEO_EXTENSIONS) {
                if (lower.endsWith(ext)) return true;
            }
            return false;
        });
        if (files != null && files.length > 0) return files[0];
        return new File(dir, "virtual.mp4");
    }

    /** JSON 配置优先，无 JSON 时回退到文件标记。 */
    public static boolean isDisabled() {
        ConfigManager config = getConfig();
        if (config.getBoolean(ConfigManager.KEY_DISABLE_MODULE, false)) return true;
        File controlFile = new File(Environment.getExternalStorageDirectory().getPath() + "/DCIM/Camera1/disable.jpg");
        return controlFile.exists();
    }

    /** JSON 配置优先，无 JSON 时回退到文件标记。 */
    public static boolean shouldShowToast() {
        ConfigManager config = getConfig();
        if (config.getBoolean(ConfigManager.KEY_DISABLE_TOAST, false)) return false;
        File toastControl = new File(Environment.getExternalStorageDirectory().getPath() + "/DCIM/Camera1/no_toast.jpg");
        return !toastControl.exists();
    }

    /** JSON 配置优先，无 JSON 时回退到文件标记。 */
    public static boolean shouldPlaySound() {
        ConfigManager config = getConfig();
        if (config.getBoolean(ConfigManager.KEY_PLAY_VIDEO_SOUND, false)) return true;
        File sfile = new File(Environment.getExternalStorageDirectory().getPath() + "/DCIM/Camera1/no-silent.jpg");
        return sfile.exists();
    }
}