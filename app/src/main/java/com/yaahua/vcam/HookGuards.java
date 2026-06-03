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

    /** 确保 ConfigManager 持有有效的 Context（用于 Provider 跨进程读配置）。 */
    public static void ensureConfigHasContext(android.content.Context context) {
        if (context == null) {
            XposedBridge.log("【DIAG】ensureConfigHasContext → context 为null, 跳过");
            return;
        }
        XposedBridge.log("【DIAG】ensureConfigHasContext → 注入context: " + context.getPackageName());
        if (configManager == null) {
            configManager = new ConfigManager(false);
            XposedBridge.log("【DIAG】ensureConfigHasContext → 新建 ConfigManager");
        }
        configManager.setSkipProviderReload(false);
        configManager.setContext(context);
        XposedBridge.log("【DIAG】ensureConfigHasContext → 完成, configManager已就绪");
    }

    private static long configFileLastModified = 0;

    private static final String[] VIDEO_EXTENSIONS = { ".mp4", ".mov", ".avi", ".mkv" };

    public static ConfigManager getConfig() {
        if (configManager == null) {
            XposedBridge.log("【DIAG】HookGuards.getConfig → 首次创建");
            configManager = new ConfigManager(false);
            configManager.reload();
            configFileLastModified = getConfigFileMtime();
            configDirty = false;
        } else if (configDirty) {
            XposedBridge.log("【DIAG】HookGuards.getConfig → configDirty, forceReload");
            configManager.forceReload();
            configFileLastModified = getConfigFileMtime();
            configDirty = false;
        } else {
            long currentMtime = getConfigFileMtime();
            if (currentMtime > configFileLastModified) {
                XposedBridge.log("【DIAG】HookGuards.getConfig → mtime变更, forceReload");
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
        XposedBridge.log("【DIAG】getVideoFile → dir=" + dir.getAbsolutePath() + " selected=" + selectedName);
        if (selectedName != null && !selectedName.isEmpty()) {
            // 支持绝对路径
            if (selectedName.startsWith("/")) {
                File absolute = new File(selectedName);
                XposedBridge.log("【DIAG】getVideoFile → 尝试绝对路径: " + absolute.getAbsolutePath());
                try {
                    if (absolute.exists() && !absolute.isDirectory()) {
                        XposedBridge.log("【DIAG】getVideoFile → 命中绝对路径: " + absolute.getName());
                        return absolute;
                    }
                    XposedBridge.log("【DIAG】getVideoFile → 绝对路径不存在: " + absolute.getAbsolutePath());
                } catch (SecurityException e) {
                    XposedBridge.log("【DIAG】getVideoFile → SecurityException, 信任路径: " + e);
                    return absolute;
                }
            }
            File selected = new File(dir, selectedName);
            XposedBridge.log("【DIAG】getVideoFile → 尝试相对路径: " + selected.getAbsolutePath());
            try {
                if (selected.exists() && !selected.isDirectory()) {
                    XposedBridge.log("【DIAG】getVideoFile → 命中相对路径: " + selected.getName());
                    return selected;
                }
                XposedBridge.log("【DIAG】getVideoFile → 相对路径不存在: " + selected.getAbsolutePath());
            } catch (SecurityException e) {
                XposedBridge.log("【DIAG】getVideoFile → SecurityException, 信任路径: " + e);
                return selected;
            }
        } else {
            XposedBridge.log("【DIAG】getVideoFile → selectedName 为空, 走fallback");
        }
        // 回退：目录中任意视频（支持四种格式）
        try {
            File[] files = dir.listFiles((d, name) -> {
                String lower = name.toLowerCase();
                for (String ext : VIDEO_EXTENSIONS) {
                    if (lower.endsWith(ext)) return true;
                }
                return false;
            });
            if (files != null && files.length > 0) {
                XposedBridge.log("【DIAG】getVideoFile → fallback 找到 " + files.length + " 个视频, 取第一个: " + files[0].getName());
                return files[0];
            }
            XposedBridge.log("【DIAG】getVideoFile → fallback listFiles 返回空");
        } catch (SecurityException e) {
            XposedBridge.log("【DIAG】getVideoFile → fallback listFiles 抛异常: " + e);
        }
        XposedBridge.log("【DIAG】getVideoFile → 最终回退到 virtual.mp4");
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