package com.yaahua.vcam;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.FileObserver;
import android.os.Handler;
import android.os.Looper;

/**
 * 配置变更监听器：ContentObserver + FileObserver + BroadcastReceiver。
 * v5.2 修复：防抖改为尾随执行模式（trailing debounce），
 * 快速连续变更时只执行最后一次，不丢失中间态。
 */
public final class ConfigWatcher {

    public interface Callback {
        void onMediaSourceChanged();
        void onRotationChanged(int degrees);
    }

    private final Callback callback;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private android.database.ContentObserver configObserver;
    private FileObserver configFileObserver;
    private Runnable pendingCallback;
    private static final long DEBOUNCE_MS = 300;

    public ConfigWatcher(Callback callback) {
        this.callback = callback;
    }

    public void init(final Context context) {
        if (configObserver != null) return;

        LogUtil.log("【CS】初始化配置监听");
        XposedBridge.log("【DIAG】ConfigWatcher.init → 开始, context=" + context.getPackageName());
        
        // ---- 确保 HookGuards 持有的 ConfigManager 有 Context（可走 Provider 跨进程读配置）----
        HookGuards.ensureConfigHasContext(context);
        XposedBridge.log("【DIAG】ConfigWatcher.init → ensureConfigHasContext 完成");

        // ---- ContentObserver（Provider 变更）----
        configObserver = new android.database.ContentObserver(mainHandler) {
            @Override
            public void onChange(boolean selfChange) {
                super.onChange(selfChange);
                LogUtil.log("【CS】Provider 配置变更");
                HookGuards.invalidateConfig();
                fireCallback();
            }
        };

        boolean observerRegistered = false;
        try {
            context.getContentResolver().registerContentObserver(IpcContract.URI_CONFIG, true, configObserver);
            observerRegistered = true;
            XposedBridge.log("【DIAG】ConfigWatcher.init → ContentObserver 注册成功: " + IpcContract.URI_CONFIG);
        } catch (Exception e) {
            XposedBridge.log("【DIAG】ConfigWatcher.init → ContentObserver 注册失败: " + e);
        }

        if (!observerRegistered) {
            LogUtil.log("【CS】降级到 FileObserver 监听");
            try {
                String configDir = ConfigManager.DEFAULT_CONFIG_DIR;
                configFileObserver = new FileObserver(configDir,
                        FileObserver.MODIFY | FileObserver.CREATE | FileObserver.MOVED_TO) {
                    @Override
                    public void onEvent(int event, String path) {
                        if (path != null && path.endsWith(".json")) {
                            LogUtil.log("【CS】文件变更: " + path);
                            mainHandler.post(() -> {
                                HookGuards.invalidateConfig();
                                fireCallback();
                            });
                        }
                    }
                };
                configFileObserver.startWatching();
                LogUtil.log("【CS】FileObserver 已启动: " + configDir);
            } catch (Exception e) {
                LogUtil.log("【CS】FileObserver 启动失败: " + e);
            }

            mainHandler.postDelayed(() ->
                    getConfig(context).requestConfig(context), 1000);
        }

        registerBroadcastReceiver(context);

        // ---- 兜底：定时文件轮询（5秒间隔），防止 ContentObserver/FileObserver 都失效 ----
        XposedBridge.log("【DIAG】ConfigWatcher.init → 启动文件轮询兜底");
        mainHandler.postDelayed(new Runnable() {
            private long lastMtime = new java.io.File(ConfigManager.DEFAULT_CONFIG_DIR,
                    ConfigManager.CONFIG_FILE_NAME).lastModified();
            @Override
            public void run() {
                try {
                    long mtime = new java.io.File(ConfigManager.DEFAULT_CONFIG_DIR,
                            ConfigManager.CONFIG_FILE_NAME).lastModified();
                    if (mtime > lastMtime) {
                        XposedBridge.log("【DIAG】ConfigWatcher.poll → mtime变更(" + lastMtime + "→" + mtime + "), 触发回调");
                        lastMtime = mtime;
                        HookGuards.invalidateConfig();
                        fireCallback();
                    }
                } catch (Exception e) {
                    XposedBridge.log("【DIAG】ConfigWatcher.poll → 异常: " + e);
                }
                mainHandler.postDelayed(this, 5000);
            }
        }, 5000);
    }

    /** 尾随执行防抖：每次调用重置计时器，300ms 内无新事件才真正执行回调 */
    private void fireCallback() {
        XposedBridge.log("【DIAG】ConfigWatcher.fireCallback 被调用");
        if (pendingCallback != null) {
            mainHandler.removeCallbacks(pendingCallback);
        }
        pendingCallback = new Runnable() {
            @Override
            public void run() {
                XposedBridge.log("【DIAG】ConfigWatcher → 防抖结束, 触发 onMediaSourceChanged");
                pendingCallback = null;
                callback.onMediaSourceChanged();
            }
        };
        mainHandler.postDelayed(pendingCallback, DEBOUNCE_MS);
    }

    private void registerBroadcastReceiver(final Context context) {
        try {
            BroadcastReceiver receiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context ctx, Intent intent) {
                    if (IpcContract.ACTION_UPDATE_CONFIG.equals(intent.getAction())) {
                        handleConfigUpdate(intent);
                    }
                }
            };
            IntentFilter filter = new IntentFilter();
            filter.addAction(IpcContract.ACTION_UPDATE_CONFIG);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                context.registerReceiver(receiver, filter);
            }
            LogUtil.log("【CS】广播接收器已注册");
        } catch (Exception e) {
            LogUtil.log("【CS】注册广播接收器失败: " + e);
        }
    }

    private void handleConfigUpdate(Intent intent) {
        String configJson = intent.getStringExtra(IpcContract.EXTRA_CONFIG_JSON);
        if (configJson == null) return;

        // 直接注入 HookGuards 持有的 ConfigManager，不再创建新实例
        ConfigManager config = HookGuards.getConfig();

        String oldVideo = config.getString(ConfigManager.KEY_SELECTED_VIDEO, "");
        String oldImage = config.getString(ConfigManager.KEY_SELECTED_IMAGE, "");
        String oldMode = config.getString(ConfigManager.KEY_REPLACE_MODE, ConfigManager.REPLACE_MODE_VIDEO);
        boolean oldFpd = config.getBoolean(ConfigManager.KEY_FORCE_PRIVATE_DIR, false);
        int oldRotation = config.getInt(ConfigManager.KEY_VIDEO_ROTATION_OFFSET, 0);
        String oldSourceType = config.getString(ConfigManager.KEY_MEDIA_SOURCE_TYPE, ConfigManager.MEDIA_SOURCE_LOCAL);
        String oldStreamUrl = config.getString(ConfigManager.KEY_STREAM_URL, "");
        boolean oldSound = config.getBoolean(ConfigManager.KEY_PLAY_VIDEO_SOUND, false);
        boolean oldDisable = config.getBoolean(ConfigManager.KEY_DISABLE_MODULE, false);
        boolean oldToast = config.getBoolean(ConfigManager.KEY_DISABLE_TOAST, false);

        config.updateConfigFromJSON(configJson);

        String newVideo = config.getString(ConfigManager.KEY_SELECTED_VIDEO, "");
        String newImage = config.getString(ConfigManager.KEY_SELECTED_IMAGE, "");
        String newMode = config.getString(ConfigManager.KEY_REPLACE_MODE, ConfigManager.REPLACE_MODE_VIDEO);
        boolean newFpd = config.getBoolean(ConfigManager.KEY_FORCE_PRIVATE_DIR, false);
        int newRotation = config.getInt(ConfigManager.KEY_VIDEO_ROTATION_OFFSET, 0);
        String newSourceType = config.getString(ConfigManager.KEY_MEDIA_SOURCE_TYPE, ConfigManager.MEDIA_SOURCE_LOCAL);
        String newStreamUrl = config.getString(ConfigManager.KEY_STREAM_URL, "");
        boolean newSound = config.getBoolean(ConfigManager.KEY_PLAY_VIDEO_SOUND, false);
        boolean newDisable = config.getBoolean(ConfigManager.KEY_DISABLE_MODULE, false);
        boolean newToast = config.getBoolean(ConfigManager.KEY_DISABLE_TOAST, false);

        boolean mediaChanged = !oldVideo.equals(newVideo) ||
                !oldImage.equals(newImage) ||
                !oldMode.equals(newMode) ||
                (oldFpd != newFpd) ||
                !oldSourceType.equals(newSourceType) ||
                !oldStreamUrl.equals(newStreamUrl) ||
                (oldSound != newSound) ||
                (oldDisable != newDisable) ||
                (oldToast != newToast);

        if (mediaChanged) {
            // 广播 JSON 已注入内存，但 invalidateConfig 确保后续 getConfig 走文件校验
            HookGuards.invalidateConfig();
            fireCallback();
            LogUtil.log("【CS】配置更新: 媒体源/开关变化");
        } else if (oldRotation != newRotation) {
            LogUtil.log("【CS】配置更新: 旋转 " + newRotation + "°");
            callback.onRotationChanged(newRotation);
        } else {
            LogUtil.log("【CS】配置更新: 无变化");
        }
    }

    private static ConfigManager getConfig(Context context) {
        ConfigManager cm = new ConfigManager(false);
        if (context != null) cm.setContext(context);
        cm.reload();
        return cm;
    }
}