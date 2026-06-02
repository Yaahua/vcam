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
 * 简化自 CamSwap ConfigWatcher，暂不依赖 VideoManager。
 */
public final class ConfigWatcher {

    public interface Callback {
        void onMediaSourceChanged();
        void onRotationChanged(int degrees);
    }

    private final Callback callback;
    private android.database.ContentObserver configObserver;
    private FileObserver configFileObserver;

    public ConfigWatcher(Callback callback) {
        this.callback = callback;
    }

    public void init(final Context context) {
        if (configObserver != null) return;

        LogUtil.log("【CS】初始化配置监听");
        configObserver = new android.database.ContentObserver(new Handler(Looper.getMainLooper())) {
            @Override
            public void onChange(boolean selfChange) {
                super.onChange(selfChange);
                LogUtil.log("【CS】Provider 配置变更");
                ConfigManager config = getConfig(context);
                config.forceReload();
                callback.onMediaSourceChanged();
            }
        };

        boolean observerRegistered = false;
        try {
            context.getContentResolver().registerContentObserver(IpcContract.URI_CONFIG, true, configObserver);
            observerRegistered = true;
        } catch (Exception e) {
            LogUtil.log("【CS】注册 ContentObserver 失败: " + e);
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
                            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                                getConfig(context).forceReload();
                                callback.onMediaSourceChanged();
                            }, 200);
                        }
                    }
                };
                configFileObserver.startWatching();
                LogUtil.log("【CS】FileObserver 已启动: " + configDir);
            } catch (Exception e) {
                LogUtil.log("【CS】FileObserver 启动失败: " + e);
            }

            new Handler(Looper.getMainLooper()).postDelayed(() ->
                    getConfig(context).requestConfig(context), 1000);
        }

        registerBroadcastReceiver(context);
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
        ConfigManager config = getConfig(null);
        String configJson = intent.getStringExtra(IpcContract.EXTRA_CONFIG_JSON);
        if (configJson == null) return;

        String oldVideo = config.getString(ConfigManager.KEY_SELECTED_VIDEO, "");
        String oldImage = config.getString(ConfigManager.KEY_SELECTED_IMAGE, "");
        String oldMode = config.getString(ConfigManager.KEY_REPLACE_MODE, ConfigManager.REPLACE_MODE_VIDEO);
        boolean oldFpd = config.getBoolean(ConfigManager.KEY_FORCE_PRIVATE_DIR, false);
        int oldRotation = config.getInt(ConfigManager.KEY_VIDEO_ROTATION_OFFSET, 0);
        String oldSourceType = config.getString(ConfigManager.KEY_MEDIA_SOURCE_TYPE, ConfigManager.MEDIA_SOURCE_LOCAL);
        String oldStreamUrl = config.getString(ConfigManager.KEY_STREAM_URL, "");

        config.updateConfigFromJSON(configJson);

        String newVideo = config.getString(ConfigManager.KEY_SELECTED_VIDEO, "");
        String newImage = config.getString(ConfigManager.KEY_SELECTED_IMAGE, "");
        String newMode = config.getString(ConfigManager.KEY_REPLACE_MODE, ConfigManager.REPLACE_MODE_VIDEO);
        boolean newFpd = config.getBoolean(ConfigManager.KEY_FORCE_PRIVATE_DIR, false);
        int newRotation = config.getInt(ConfigManager.KEY_VIDEO_ROTATION_OFFSET, 0);
        String newSourceType = config.getString(ConfigManager.KEY_MEDIA_SOURCE_TYPE, ConfigManager.MEDIA_SOURCE_LOCAL);
        String newStreamUrl = config.getString(ConfigManager.KEY_STREAM_URL, "");

        boolean mediaChanged = !oldVideo.equals(newVideo) ||
                !oldImage.equals(newImage) ||
                !oldMode.equals(newMode) ||
                (oldFpd != newFpd) ||
                !oldSourceType.equals(newSourceType) ||
                !oldStreamUrl.equals(newStreamUrl);

        if (mediaChanged) {
            callback.onMediaSourceChanged();
            LogUtil.log("【CS】配置更新: 媒体源变化");
        } else if (oldRotation != newRotation) {
            LogUtil.log("【CS】配置更新: 旋转 " + newRotation + "°");
            callback.onRotationChanged(newRotation);
        } else {
            LogUtil.log("【CS】配置更新: 无变化");
        }
    }

    private static ConfigManager getConfig(Context context) {
        // 返回一个共享的 ConfigManager，如有 Context 则设置
        ConfigManager cm = new ConfigManager(false);
        if (context != null) cm.setContext(context);
        cm.reload();
        return cm;
    }
}