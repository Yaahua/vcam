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
import android.os.IBinder;

public class NotificationService extends Service {
    private static final String CHANNEL_ID = "vcam_control_channel";
    private static final int NOTIFICATION_ID = 1001;
    private static final String ACTION_PREV = "com.yaahua.vcam.action.PREV";
    private static final String ACTION_NEXT = "com.yaahua.vcam.action.NEXT";
    private static final String ACTION_EXIT = "com.yaahua.vcam.action.EXIT";

    private ConfigManager configManager;

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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(controlReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(controlReceiver, filter);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIFICATION_ID, buildNotification());
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try { unregisterReceiver(controlReceiver); } catch (Exception ignored) {}
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    private Notification buildNotification() {
        Intent notificationIntent = new Intent(this, MainActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent,
                PendingIntent.FLAG_IMMUTABLE);

        Notification.Builder builder = new Notification.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.notif_content))
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(pendingIntent)
                .setOngoing(true);

        builder.addAction(new Notification.Action.Builder(null,
                getString(R.string.notif_action_prev),
                getPendingIntent(ACTION_PREV)).build());
        builder.addAction(new Notification.Action.Builder(null,
                getString(R.string.notif_action_next),
                getPendingIntent(ACTION_NEXT)).build());
        builder.addAction(new Notification.Action.Builder(null,
                getString(R.string.notif_action_exit),
                getPendingIntent(ACTION_EXIT)).build());

        return builder.build();
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