package com.yaahua.vcam;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

import com.yaahua.vcam.ui.MainActivity;

public class NotificationService extends Service {
    private static final String CHANNEL_ID = "vcam_control_channel";
    private static final int NOTIFICATION_ID = 1001;
    private static final String ACTION_PREV = "com.yaahua.vcam.action.PREV";
    private static final String ACTION_NEXT = "com.yaahua.vcam.action.NEXT";
    private static final String ACTION_EXIT = "com.yaahua.vcam.action.EXIT";
    private static final String ACTION_PAUSE = "com.yaahua.vcam.action.PAUSE";

    private ConfigManager configManager;
    private Handler progressHandler;
    private Runnable progressUpdater;

    private BroadcastReceiver controlReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_EXIT.equals(action)) {
                stopSelf();
            } else if (ACTION_PREV.equals(action)) {
                ControlActionHelper.switchVideo(NotificationService.this, false);
            } else if (ACTION_NEXT.equals(action)) {
                ControlActionHelper.switchVideo(NotificationService.this, true);
            } else if (ACTION_PAUSE.equals(action)) {
                SharedState.playPaused = !SharedState.playPaused;
                updateNotification();
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        configManager = new ConfigManager();
        configManager.setContext(this);

        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_EXIT);
        filter.addAction(ACTION_PREV);
        filter.addAction(ACTION_NEXT);
        filter.addAction(ACTION_PAUSE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(controlReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(controlReceiver, filter);
        }

        progressHandler = new Handler(Looper.getMainLooper());
        progressUpdater = new Runnable() {
            @Override
            public void run() {
                updateNotification();
                progressHandler.postDelayed(this, 1000);
            }
        };
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIFICATION_ID, buildNotification());
        progressHandler.post(progressUpdater);
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try { unregisterReceiver(controlReceiver); } catch (Exception ignored) {}
        if (progressHandler != null) progressHandler.removeCallbacks(progressUpdater);
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    private void updateNotification() {
        NotificationManager nm = getSystemService(NotificationManager.class);
        if (nm != null) nm.notify(NOTIFICATION_ID, buildNotification());
    }

    private Notification buildNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        long posMs = 0, durMs = 0;
        if (SharedState.c2_hw_decode_obj != null) {
            posMs = SharedState.c2_hw_decode_obj.getCurrentPositionMs();
            durMs = SharedState.c2_hw_decode_obj.getDurationMs();
        } else if (SharedState.hw_decode_obj != null) {
            posMs = SharedState.hw_decode_obj.getCurrentPositionMs();
            durMs = SharedState.hw_decode_obj.getDurationMs();
        }

        String videoName = "";
        String curPath = SharedState.currentVideoPath;
        if (curPath != null) {
            int idx = curPath.lastIndexOf('/');
            videoName = idx >= 0 ? curPath.substring(idx + 1) : curPath;
        }

        String posStr = formatTime(posMs);
        String durStr = formatTime(durMs);
        String content = videoName.isEmpty() ? getString(R.string.notif_content)
                : videoName + "  " + posStr + " / " + durStr;

        Notification.Builder builder = new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(content)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setOnlyAlertOnce(true);

        // 进度条
        if (durMs > 0) {
            builder.setProgress((int) durMs, (int) posMs, false);
        } else {
            builder.setProgress(0, 0, true);
        }

        builder.addAction(new Notification.Action.Builder(null,
                getString(R.string.notif_action_prev),
                getPendingIntent(ACTION_PREV)).build());

        // 暂停/播放按钮
        String pauseLabel = SharedState.playPaused ? "\u25B6 \u64AD\u653E" : "\u23F8 \u6682\u505C";
        builder.addAction(new Notification.Action.Builder(null,
                pauseLabel,
                getPendingIntent(ACTION_PAUSE)).build());

        builder.addAction(new Notification.Action.Builder(null,
                getString(R.string.notif_action_next),
                getPendingIntent(ACTION_NEXT)).build());

        builder.addAction(new Notification.Action.Builder(null,
                getString(R.string.notif_action_exit),
                getPendingIntent(ACTION_EXIT)).build());

        return builder.build();
    }

    private String formatTime(long ms) {
        long sec = ms / 1000;
        return String.format("%02d:%02d", sec / 60, sec % 60);
    }

    private PendingIntent getPendingIntent(String action) {
        Intent intent = new Intent(action);
        intent.setPackage(getPackageName());
        return PendingIntent.getBroadcast(this, action.hashCode(), intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.notif_channel_name),
                    NotificationManager.IMPORTANCE_LOW);
            channel.setDescription(getString(R.string.notif_channel_desc));
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }
}