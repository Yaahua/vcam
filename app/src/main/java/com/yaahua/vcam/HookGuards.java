package com.yaahua.vcam;

import android.os.Environment;

import java.io.File;

public class HookGuards {
    private HookGuards() {}

    private static ConfigManager configManager;

    public static void setConfigManager(ConfigManager cm) {
        configManager = cm;
    }

    private static long configFileLastModified = 0;

    private static ConfigManager getConfig() {
        if (configManager == null) {
            configManager = new ConfigManager(false);
            configManager.reload();
            configFileLastModified = getConfigFileMtime();
        } else {
            // 每次访问检查文件 mtime，变化则强制重载
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
        // 优先从 ConfigManager 读取选中的视频
        ConfigManager config = getConfig();
        String selectedName = config.getString(ConfigManager.KEY_SELECTED_VIDEO, null);
        File dir = new File(SharedState.video_path);
        if (selectedName != null && !selectedName.isEmpty()) {
            File selected = new File(dir, selectedName);
            if (selected.exists()) return selected;
        }
        // 回退：目录中任意 mp4 → virtual.mp4
        File[] files = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".mp4"));
        if (files != null && files.length > 0) return files[0];
        return new File(dir, "virtual.mp4");
    }

    /** JSON 配置优先，无 JSON 时回退到文件标记。 */
    public static boolean isDisabled() {
        ConfigManager config = getConfig();
        if (config.getBoolean(ConfigManager.KEY_DISABLE_MODULE, false)) return true;
        // 回退：文件标记
        File controlFile = new File(Environment.getExternalStorageDirectory().getPath() + "/DCIM/Camera1/disable.jpg");
        return controlFile.exists();
    }

    /** JSON 配置优先，无 JSON 时回退到文件标记。 */
    public static boolean shouldShowToast() {
        ConfigManager config = getConfig();
        if (config.getBoolean(ConfigManager.KEY_DISABLE_TOAST, false)) return false;
        // 回退：文件标记（no_toast.jpg存在表示禁用toast → 不应显示toast）
        File toastControl = new File(Environment.getExternalStorageDirectory().getPath() + "/DCIM/Camera1/no_toast.jpg");
        return !toastControl.exists();
    }

    /** JSON 配置优先，无 JSON 时回退到文件标记。 */
    public static boolean shouldPlaySound() {
        ConfigManager config = getConfig();
        if (config.getBoolean(ConfigManager.KEY_PLAY_VIDEO_SOUND, false)) return true;
        // 回退：文件标记
        File sfile = new File(Environment.getExternalStorageDirectory().getPath() + "/DCIM/Camera1/no-silent.jpg");
        return sfile.exists();
    }
}