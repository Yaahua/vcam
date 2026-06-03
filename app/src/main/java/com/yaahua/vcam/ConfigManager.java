package com.yaahua.vcam;

import android.os.Environment;
import android.content.Context;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class ConfigManager {
    public static final String CONFIG_FILE_NAME = "cs_config.json";
    public static final String DEFAULT_CONFIG_DIR;
    static {
        String path;
        try {
            path = Environment.getExternalStorageDirectory().getAbsolutePath() + "/DCIM/Camera1/";
        } catch (Throwable e) {
            path = "/sdcard/DCIM/Camera1/";
        }
        DEFAULT_CONFIG_DIR = path;
    }

    // Config Keys
    public static final String KEY_DISABLE_MODULE = "disable_module";
    public static final String KEY_PLAY_VIDEO_SOUND = "play_video_sound";
    public static final String KEY_FORCE_PRIVATE_DIR = "force_private_dir";
    public static final String KEY_DISABLE_TOAST = "disable_toast";
    public static final String KEY_ENABLE_RANDOM_PLAY = "enable_random_play";
    public static final String KEY_TARGET_PACKAGES = "target_packages";
    public static final String KEY_SELECTED_VIDEO = "selected_video";
    public static final String KEY_ORIGINAL_VIDEO_NAME = "original_video_name";
    public static final String KEY_SELECTED_IMAGE = "selected_image";
    public static final String KEY_REPLACE_MODE = "replace_mode";
    public static final String KEY_ENABLE_MIC_HOOK = "enable_mic_hook";
    public static final String KEY_MIC_HOOK_MODE = "mic_hook_mode";
    public static final String KEY_SELECTED_AUDIO = "selected_audio";
    public static final String KEY_NOTIFICATION_CONTROL_ENABLED = "notification_control_enabled";
    public static final String KEY_OVERLAY_CONTROL_ENABLED = "overlay_control_enabled";
    public static final String MIC_MODE_MUTE = "mute";
    public static final String MIC_MODE_REPLACE = "replace";
    public static final String MIC_MODE_VIDEO_SYNC = "video_sync";
    public static final String REPLACE_MODE_VIDEO = "video";
    public static final String REPLACE_MODE_IMAGE = "image";
    public static final String KEY_VIDEO_ROTATION_OFFSET = "video_rotation_offset";
    public static final String KEY_ENABLE_PHOTO_FAKE = "enable_photo_fake";
    public static final String KEY_ENABLE_WHATSAPP_CAMERA2_COMPAT = "enable_whatsapp_camera2_compat";
    public static final String KEY_MEDIA_SOURCE_TYPE = "media_source_type";
    public static final String KEY_STREAM_URL = "stream_url";
    public static final String KEY_STREAM_AUTO_RECONNECT = "stream_auto_reconnect";
    public static final String KEY_STREAM_LOCAL_FALLBACK = "stream_enable_local_fallback";
    public static final String KEY_STREAM_TRANSPORT_HINT = "stream_transport_hint";
    public static final String KEY_STREAM_TIMEOUT_MS = "stream_timeout_ms";
    public static final String MEDIA_SOURCE_LOCAL = "local";
    public static final String MEDIA_SOURCE_STREAM = "stream";

    public static final String ACTION_UPDATE_CONFIG = IpcContract.ACTION_UPDATE_CONFIG;
    public static final String ACTION_REQUEST_CONFIG = IpcContract.ACTION_REQUEST_CONFIG;
    public static final String EXTRA_CONFIG_JSON = IpcContract.EXTRA_CONFIG_JSON;

    public static boolean ENABLE_LEGACY_FILE_ACCESS = true;

    private final AtomicReference<JSONObject> configData = new AtomicReference<>(new JSONObject());
    private volatile long lastLoadedTime = 0;
    private volatile Context context;
    private volatile boolean skipProviderReload = false;
    private final Object configWriteLock = new Object();

    public ConfigManager() {
        this(true);
    }

    public ConfigManager(boolean initReload) {
        if (initReload) {
            reload();
        }
    }

    public void setSkipProviderReload(boolean skip) {
        this.skipProviderReload = skip;
    }

    public void setContext(Context context) {
        this.context = context;
        forceReload();
    }

    public JSONObject getConfigData() {
        return copyConfig(getConfigSnapshot());
    }

    private final AtomicLong lastReloadTime = new AtomicLong(0);
    private static final long MIN_RELOAD_INTERVAL_MS = 200;

    private interface ConfigMutation {
        void apply(JSONObject config) throws JSONException;
    }

    private JSONObject getConfigSnapshot() {
        JSONObject snapshot = configData.get();
        return snapshot != null ? snapshot : new JSONObject();
    }

    private static JSONObject copyConfig(JSONObject source) {
        if (source == null) return new JSONObject();
        try {
            return new JSONObject(source.toString());
        } catch (JSONException e) {
            return new JSONObject();
        }
    }

    private void setConfigSnapshot(JSONObject snapshot) {
        configData.set(snapshot != null ? snapshot : new JSONObject());
    }

    private void updateConfigAndSave(ConfigMutation mutation) {
        synchronized (configWriteLock) {
            try {
                JSONObject updated = copyConfig(getConfigSnapshot());
                mutation.apply(updated);
                setConfigSnapshot(updated);
                save(updated);
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
    }

    public void reload() {
        long now = System.currentTimeMillis();
        while (true) {
            long last = lastReloadTime.get();
            if (now - last < MIN_RELOAD_INTERVAL_MS) return;
            if (lastReloadTime.compareAndSet(last, now)) break;
        }

        boolean providerSuccess = false;
        if (context != null && !skipProviderReload) {
            providerSuccess = reloadFromProvider();
        }
        if (!providerSuccess && ENABLE_LEGACY_FILE_ACCESS) {
            reloadFromFile();
        }
    }

    public void forceReload() {
        lastReloadTime.set(0);
        lastLoadedTime = 0;
        reload();
    }

    private boolean reloadFromProvider() {
        android.net.Uri uri = IpcContract.URI_CONFIG;
        LogUtil.log("【DIAG】ConfigManager.reloadFromProvider 开始查询: " + uri);
        try (android.database.Cursor cursor = context.getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null) {
                LogUtil.log("【DIAG】ConfigManager.reloadFromProvider cursor count=" + cursor.getCount());
                JSONObject newConfig = new JSONObject();
                while (cursor.moveToNext()) {
                    String key = cursor.getString(0);
                    String valueStr = cursor.getString(1);
                    String type = cursor.getString(2);
                    try {
                        if ("boolean".equals(type)) {
                            newConfig.put(key, Boolean.parseBoolean(valueStr));
                        } else if ("int".equals(type)) {
                            newConfig.put(key, Integer.parseInt(valueStr));
                        } else if ("long".equals(type)) {
                            newConfig.put(key, Long.parseLong(valueStr));
                        } else if ("json_array".equals(type)) {
                            newConfig.put(key, new JSONArray(valueStr));
                        } else {
                            newConfig.put(key, valueStr);
                        }
                    } catch (Exception e) {
                        newConfig.put(key, valueStr);
                    }
                }
                if (newConfig.length() > 0) {
                    setConfigSnapshot(newConfig);
                    LogUtil.log("【CS】配置已通过 Provider 加载 (" + newConfig.length() + " keys)");
                    return true;
                } else {
                    LogUtil.log("【CS】Provider Cursor 为空, 降级到文件读取");
                }
            } else {
                LogUtil.log("【CS】Provider Cursor 为空, 降级到文件读取");
            }
        } catch (Exception e) {
            LogUtil.log("【CS】配置 Provider 错误: " + e);
        }
        return false;
    }

    public void requestConfig(Context context) {
        try {
            android.content.Intent intent = new android.content.Intent(IpcContract.ACTION_REQUEST_CONFIG);
            intent.setPackage("com.yaahua.vcam");
            intent.putExtra(IpcContract.EXTRA_REQUESTER_PACKAGE, context.getPackageName());
            context.sendBroadcast(intent);
            LogUtil.log("【CS】已发送配置请求广播");
        } catch (Exception e) {
            LogUtil.log("【CS】发送配置请求广播失败: " + e);
        }
    }

    public void sendConfigBroadcast(Context context) {
        sendConfigBroadcast(context, null);
    }

    public void sendConfigBroadcast(Context context, String explicitTargetPackage) {
        Set<String> targetPackages = new HashSet<>();
        if (explicitTargetPackage != null && !explicitTargetPackage.isEmpty()) {
            targetPackages.add(explicitTargetPackage);
        } else {
            targetPackages.addAll(getTargetPackages());
        }
        if (targetPackages.isEmpty()) {
            sendConfigBroadcastInternal(context, null);
            return;
        }
        for (String targetPackage : targetPackages) {
            sendConfigBroadcastInternal(context, targetPackage);
        }
    }

    private void sendConfigBroadcastInternal(Context context, String targetPackage) {
        try {
            android.content.Intent intent = new android.content.Intent(IpcContract.ACTION_UPDATE_CONFIG);
            if (targetPackage != null && !targetPackage.isEmpty()) {
                intent.setPackage(targetPackage);
            }
            intent.putExtra(IpcContract.EXTRA_CONFIG_JSON, getConfigSnapshot().toString());

            if (getBoolean(KEY_FORCE_PRIVATE_DIR, false)) {
                String videoName = getString(KEY_SELECTED_VIDEO, "virtual.mp4");
                File videoFile = null;
                if (videoName != null && !videoName.isEmpty()) {
                    // 支持绝对路径
                    if (videoName.startsWith("/")) {
                        File absFile = new File(videoName);
                        if (absFile.exists() && !absFile.isDirectory()) videoFile = absFile;
                    }
                    if (videoFile == null) videoFile = new File(DEFAULT_CONFIG_DIR, videoName);
                }
                if (videoFile == null || !videoFile.exists()) {
                    File[] files = new File(DEFAULT_CONFIG_DIR)
                            .listFiles((dir, name) -> name.toLowerCase().endsWith(".mp4"));
                    if (files != null && files.length > 0) videoFile = files[0];
                }
                if (videoFile != null && !videoFile.exists()) {
                    videoFile = new File(DEFAULT_CONFIG_DIR, "virtual.mp4");
                }
                if (videoFile != null && videoFile.exists()) {
                    try {
                        final File finalVideoFile = videoFile;
                        android.os.Bundle bundle = new android.os.Bundle();
                        bundle.putBinder(IpcContract.EXTRA_VIDEO_BINDER, new android.os.Binder() {
                            @Override
                            protected boolean onTransact(int code, android.os.Parcel data, android.os.Parcel reply,
                                    int flags) throws android.os.RemoteException {
                                if (code == 1) {
                                    reply.writeNoException();
                                    try {
                                        android.os.ParcelFileDescriptor pfd = android.os.ParcelFileDescriptor
                                                .open(finalVideoFile, android.os.ParcelFileDescriptor.MODE_READ_ONLY);
                                        reply.writeInt(1);
                                        pfd.writeToParcel(reply, android.os.Parcelable.PARCELABLE_WRITE_RETURN_VALUE);
                                    } catch (Exception e) {
                                        LogUtil.log("【CS】Binder PFD 失败: " + e);
                                        reply.writeInt(0);
                                    }
                                    return true;
                                }
                                return super.onTransact(code, data, reply, flags);
                            }
                        });
                        intent.putExtra(IpcContract.EXTRA_VIDEO_BUNDLE, bundle);
                    } catch (Exception e) {
                        LogUtil.log("【CS】广播附加 video_bundle 失败: " + e);
                    }
                }
            }

            context.sendBroadcast(intent);
            if (targetPackage != null && !targetPackage.isEmpty()) {
                LogUtil.log("【CS】配置广播已发送到: " + targetPackage);
            } else {
                LogUtil.log("【CS】配置广播已发送");
            }
        } catch (Exception e) {
            LogUtil.log("【CS】广播配置失败: " + e);
        }
    }

    private void reloadFromFile() {
        File configFile = new File(DEFAULT_CONFIG_DIR, CONFIG_FILE_NAME);
        if (configFile.exists()) {
            long fileModTime = configFile.lastModified();
            boolean shouldRead = (lastLoadedTime == 0) || (fileModTime > 0 && fileModTime > lastLoadedTime);
            if (shouldRead) {
                try {
                    StringBuilder stringBuilder = new StringBuilder();
                    try (BufferedReader bufferedReader = new BufferedReader(
                            new InputStreamReader(new FileInputStream(configFile), StandardCharsets.UTF_8))) {
                        String line;
                        while ((line = bufferedReader.readLine()) != null) {
                            stringBuilder.append(line);
                        }
                    }
                    setConfigSnapshot(new JSONObject(stringBuilder.toString()));
                    lastLoadedTime = (fileModTime > 0) ? fileModTime : System.currentTimeMillis();
                    LogUtil.log("【CS】配置已从文件加载: " + configFile.getName());
                } catch (Exception e) {
                    LogUtil.log("【CS】Config file read error: " + e);
                    setConfigSnapshot(getConfigSnapshot());
                }
            }
        } else {
            LogUtil.log("【CS】Config file not found: " + configFile.getAbsolutePath());
            setConfigSnapshot(getConfigSnapshot());
        }
    }

    public boolean getBoolean(String key, boolean defValue) {
        return getConfigSnapshot().optBoolean(key, defValue);
    }

    public int getInt(String key, int defValue) {
        return getConfigSnapshot().optInt(key, defValue);
    }

    public void setInt(String key, int value) {
        updateConfigAndSave(config -> config.put(key, value));
    }

    public void setBoolean(String key, boolean value) {
        updateConfigAndSave(config -> config.put(key, value));
    }

    public Set<String> getTargetPackages() {
        Set<String> packages = new HashSet<>();
        JSONArray jsonArray = getConfigSnapshot().optJSONArray(KEY_TARGET_PACKAGES);
        if (jsonArray != null) {
            for (int i = 0; i < jsonArray.length(); i++) {
                try {
                    packages.add(jsonArray.getString(i));
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        }
        return packages;
    }

    public void setTargetPackages(Set<String> packages) {
        JSONArray jsonArray = new JSONArray();
        for (String pkg : packages) jsonArray.put(pkg);
        updateConfigAndSave(config -> config.put(KEY_TARGET_PACKAGES, jsonArray));
    }

    public long getLong(String key, long defValue) {
        return getConfigSnapshot().optLong(key, defValue);
    }

    public void setLong(String key, long value) {
        updateConfigAndSave(config -> config.put(key, value));
    }

    public String getString(String key, String defValue) {
        return getConfigSnapshot().optString(key, defValue);
    }

    public void setString(String key, String value) {
        updateConfigAndSave(config -> config.put(key, value));
    }

    private void save() {
        save(getConfigSnapshot());
    }

    private void save(JSONObject snapshot) {
        File dir = new File(DEFAULT_CONFIG_DIR);
        if (!dir.exists()) dir.mkdirs();
        File configFile = new File(dir, CONFIG_FILE_NAME);
        try {
            try (FileOutputStream fos = new FileOutputStream(configFile)) {
                fos.write(snapshot.toString(4).getBytes(StandardCharsets.UTF_8));
            }
            LogUtil.log("【DIAG】ConfigManager.save → 文件已写入: " + configFile.getAbsolutePath() + " size=" + configFile.length());
            try {
                configFile.setReadable(true, false);
                configFile.setWritable(true, true);
                dir.setExecutable(true, false);
                dir.setReadable(true, false);
            } catch (Exception ignored) {}
            if (context != null) {
                try {
                    context.getContentResolver().notifyChange(IpcContract.URI_CONFIG, null);
                    LogUtil.log("【DIAG】ConfigManager.save → notifyChange 已发送");
                } catch (Exception e) {
                    LogUtil.log("【DIAG】ConfigManager.save → notifyChange 失败: " + e);
                }
                sendConfigBroadcast(context);
                LogUtil.log("【DIAG】ConfigManager.save → 广播已发送");
            } else {
                LogUtil.log("【DIAG】ConfigManager.save → 跳过通知(context=null)");
            }
        } catch (Exception e) {
            LogUtil.log("【DIAG】ConfigManager.save → 写入失败: " + e);
            e.printStackTrace();
        }
    }

    public boolean migrateIfNeeded() {
        boolean migrated = false;
        File dir = new File(DEFAULT_CONFIG_DIR);
        String[][] fileToKey = {
                { "disable.jpg", KEY_DISABLE_MODULE },
                { "no-silent.jpg", KEY_PLAY_VIDEO_SOUND },
                { "private_dir.jpg", KEY_FORCE_PRIVATE_DIR },
                { "no_toast.jpg", KEY_DISABLE_TOAST }
        };
        for (String[] map : fileToKey) {
            File oldFile = new File(dir, map[0]);
            if (oldFile.exists()) {
                setBoolean(map[1], true);
                oldFile.delete();
                migrated = true;
            }
        }
        return migrated;
    }

    public void resetToDefault() {
        synchronized (configWriteLock) {
            JSONObject updated = new JSONObject();
            setConfigSnapshot(updated);
            save(updated);
        }
    }

    public String exportConfig() {
        return getConfigSnapshot().toString();
    }

    public void importConfig(String json) throws JSONException {
        synchronized (configWriteLock) {
            JSONObject updated = new JSONObject(json);
            setConfigSnapshot(updated);
            save(updated);
        }
    }

    public void updateConfigFromJSON(String json) {
        try {
            JSONObject updated = new JSONObject(json);
            setConfigSnapshot(updated);
            long now = System.currentTimeMillis();
            lastLoadedTime = now;
            lastReloadTime.set(now);
            LogUtil.log("【CS】已通过广播更新内存配置");
        } catch (JSONException e) {
            LogUtil.log("【CS】解析广播配置失败: " + e);
        }
    }
}