package com.yaahua.vcam;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.HashSet;
import java.util.Set;

public class VideoProvider extends ContentProvider {
    private ConfigManager configManager;

    private boolean isCallerAllowed() {
        android.content.Context context = getContext();
        if (context == null) return false;

        int callingUid = Binder.getCallingUid();
        if (callingUid == android.os.Process.myUid()) return true;

        String[] packages = context.getPackageManager().getPackagesForUid(callingUid);
        if (packages == null || packages.length == 0) {
            Log.w("VideoProvider", "Rejecting call with empty package list for uid=" + callingUid);
            return false;
        }

        Set<String> allowedPackages = new HashSet<>(configManager.getTargetPackages());
        allowedPackages.add(context.getPackageName());
        for (String pkg : packages) {
            if (allowedPackages.contains(pkg)) return true;
        }
        Log.w("VideoProvider", "Rejecting caller packages=" + java.util.Arrays.toString(packages));
        return false;
    }

    @Override
    public boolean onCreate() {
        configManager = new ConfigManager(false);
        configManager.setSkipProviderReload(true);
        if (getContext() != null) {
            configManager.setContext(getContext());
        }
        configManager.reload();
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs, String sortOrder) {
        if (!isCallerAllowed()) return null;
        String lastPathSegment = uri.getLastPathSegment();
        if (IpcContract.PATH_CONFIG.equals(lastPathSegment)) {
            configManager.reload();
            org.json.JSONObject data = configManager.getConfigData();
            if (data == null || data.length() == 0) {
                configManager.forceReload();
                data = configManager.getConfigData();
            }
            android.database.MatrixCursor cursor = new android.database.MatrixCursor(
                    new String[] { "key", "value", "type" });
            if (data != null) {
                java.util.Iterator<String> keys = data.keys();
                while (keys.hasNext()) {
                    String key = keys.next();
                    Object value = data.opt(key);
                    String type = "string";
                    if (value instanceof Boolean) type = "boolean";
                    else if (value instanceof Integer) type = "int";
                    else if (value instanceof Long) type = "long";
                    else if (value instanceof org.json.JSONArray) type = "json_array";
                    cursor.addRow(new Object[] { key, String.valueOf(value), type });
                }
            }
            Log.d("VideoProvider", "query /config: returning " + cursor.getCount() + " rows");
            return cursor;
        }
        return null;
    }

    @Override
    public String getType(Uri uri) {
        return "video/mp4";
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) { return null; }

    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) { return 0; }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) { return 0; }

    @Override
    public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        if (!isCallerAllowed()) throw new FileNotFoundException("Caller not allowed");
        configManager.reload();

        String lastSeg = uri.getLastPathSegment();

        // 音频
        if (IpcContract.PATH_AUDIO.equals(lastSeg)) {
            return openAudioFile();
        }

        // NotificationService 启动暂移入 UI 阶段

        String videoName = configManager.getString(ConfigManager.KEY_SELECTED_VIDEO, null);
        File videoDir = new File(ConfigManager.DEFAULT_CONFIG_DIR);
        File videoFile = null;

        if (videoName != null && !videoName.isEmpty()) {
            // 支持绝对路径
            if (videoName.startsWith("/")) {
                File absFile = new File(videoName);
                if (absFile.exists() && !absFile.isDirectory()) videoFile = absFile;
            }
            if (videoFile == null) videoFile = new File(videoDir, videoName);
        }

        // fallback to virtual.mp4
        if (videoFile == null || !videoFile.exists() || videoFile.isDirectory()) {
            videoFile = new File(videoDir, "virtual.mp4");
        }

        // find any mp4
        if (!videoFile.exists() || videoFile.isDirectory()) {
            File[] files = videoDir.listFiles((dir, name) -> name.toLowerCase().endsWith(".mp4"));
            if (files != null && files.length > 0) videoFile = files[0];
        }

        if (videoFile == null || !videoFile.exists()) {
            throw new FileNotFoundException("No video file found in " + videoDir.getAbsolutePath());
        }

        Log.d("VideoProvider", "openFile: opening " + videoFile.getAbsolutePath());
        try {
            return ParcelFileDescriptor.open(videoFile, ParcelFileDescriptor.MODE_READ_ONLY);
        } catch (Exception e) {
            throw new FileNotFoundException("Cannot open: " + videoFile.getAbsolutePath());
        }
    }

    private ParcelFileDescriptor openAudioFile() throws FileNotFoundException {
        File audioDir = new File(ConfigManager.DEFAULT_CONFIG_DIR);
        String selectedAudio = configManager.getString(ConfigManager.KEY_SELECTED_AUDIO, null);
        File audioFile = null;

        if (selectedAudio != null && !selectedAudio.isEmpty()) {
            audioFile = new File(audioDir, selectedAudio);
        }
        if (audioFile == null || !audioFile.exists()) {
            audioFile = new File(audioDir, "Mic.mp3");
        }
        if (!audioFile.exists()) {
            File[] files = audioDir.listFiles((dir, name) -> {
                String lower = name.toLowerCase();
                return lower.endsWith(".mp3") || lower.endsWith(".wav")
                        || lower.endsWith(".aac") || lower.endsWith(".m4a")
                        || lower.endsWith(".ogg") || lower.endsWith(".flac");
            });
            if (files != null && files.length > 0) audioFile = files[0];
        }

        if (audioFile == null || !audioFile.exists()) {
            throw new FileNotFoundException("No audio file found");
        }
        try {
            return ParcelFileDescriptor.open(audioFile, ParcelFileDescriptor.MODE_READ_ONLY);
        } catch (Exception e) {
            throw new FileNotFoundException("Cannot open audio: " + audioFile.getAbsolutePath());
        }
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        if (!isCallerAllowed()) {
            Bundle denied = new Bundle();
            denied.putBoolean(IpcContract.EXTRA_CHANGED, false);
            return denied;
        }
        configManager.reload();
        boolean changed = false;
        try {
            if (IpcContract.METHOD_NEXT.equals(method)) {
                changed = switchVideo(true);
            } else if (IpcContract.METHOD_PREV.equals(method)) {
                changed = switchVideo(false);
            } else if (IpcContract.METHOD_RANDOM.equals(method)) {
                changed = pickRandomVideo();
            }
            if (changed) {
                getContext().getContentResolver().notifyChange(IpcContract.URI_VIDEO, null);
                getContext().getContentResolver().notifyChange(IpcContract.URI_CONFIG, null);
            }
        } catch (Exception e) {
            Log.e("VideoProvider", "Error in call: " + method, e);
        }
        Bundle result = new Bundle();
        result.putBoolean(IpcContract.EXTRA_CHANGED, changed);
        return result;
    }

    private boolean switchVideo(boolean next) {
        if (configManager.getBoolean(ConfigManager.KEY_ENABLE_RANDOM_PLAY, false)) {
            return pickRandomVideo();
        }
        File dir = new File(ConfigManager.DEFAULT_CONFIG_DIR);
        File[] files = dir.listFiles((d, name) -> {
            String lower = name.toLowerCase();
            return lower.endsWith(".mp4") || lower.endsWith(".mov")
                    || lower.endsWith(".avi") || lower.endsWith(".mkv");
        });
        if (files == null || files.length == 0) return false;

        String selectedVideo = configManager.getString(ConfigManager.KEY_SELECTED_VIDEO, null);
        int currentIndex = -1;
        if (selectedVideo != null && !selectedVideo.isEmpty()) {
            // 支持绝对路径：提取文件名用于在当前目录匹配
            String searchName = selectedVideo.contains("/") ? new File(selectedVideo).getName() : selectedVideo;
            for (int i = 0; i < files.length; i++) {
                if (files[i].getName().equals(searchName)) {
                    currentIndex = i;
                    break;
                }
            }
        }
        int newIndex = (currentIndex == -1) ? 0
                : (next ? (currentIndex + 1) % files.length : (currentIndex - 1 + files.length) % files.length);
        configManager.setString(ConfigManager.KEY_SELECTED_VIDEO, files[newIndex].getName());
        return true;
    }

    private boolean pickRandomVideo() {
        File dir = new File(ConfigManager.DEFAULT_CONFIG_DIR);
        if (!dir.exists() || !dir.isDirectory()) return false;
        File[] files = dir.listFiles((d, name) -> {
            String lower = name.toLowerCase();
            return lower.endsWith(".mp4") || lower.endsWith(".mov")
                    || lower.endsWith(".avi") || lower.endsWith(".mkv");
        });
        if (files == null || files.length == 0) return false;
        int index = java.util.concurrent.ThreadLocalRandom.current().nextInt(files.length);
        configManager.setString(ConfigManager.KEY_SELECTED_VIDEO, files[index].getName());
        return true;
    }
}