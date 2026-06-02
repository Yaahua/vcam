package com.yaahua.vcam;

import android.os.Environment;

import java.io.File;

public class HookGuards {
    private HookGuards() {}

    private static ConfigManager configManager;

    public static void setConfigManager(ConfigManager cm) {
        configManager = cm;
    }

    private static ConfigManager getConfig() {
        if (configManager == null) {
            configManager = new ConfigManager(false);
            configManager.reload();
        }
        return configManager;
    }

    public static File getVideoFile() {
        return new File(SharedState.video_path + "virtual.mp4");
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