package com.yaahua.vcam;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.widget.Button;
import android.widget.Switch;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;

public class MainActivity extends Activity {

    private Switch force_show_switch;
    private Switch disable_switch;
    private Switch play_sound_switch;
    private Switch force_private_dir;
    private Switch disable_toast_switch;
    private ConfigManager cm;

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (grantResults.length > 0) {
            if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
                Toast.makeText(MainActivity.this, R.string.permission_lack_warn, Toast.LENGTH_SHORT).show();
            } else {
                File camera_dir = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/DCIM/Camera1/");
                if (!camera_dir.exists()) {
                    camera_dir.mkdir();
                }
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        sync_statue_with_files();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 初始化 ConfigManager（单例，整个进程共享）
        cm = new ConfigManager();
        cm.setContext(this);

        Button repo_button = findViewById(R.id.button);
        force_show_switch = findViewById(R.id.switch1);
        disable_switch = findViewById(R.id.switch2);
        play_sound_switch = findViewById(R.id.switch3);
        force_private_dir = findViewById(R.id.switch4);
        disable_toast_switch = findViewById(R.id.switch5);

        sync_statue_with_files();

        repo_button.setOnClickListener(v -> {
            Uri uri = Uri.parse("https://github.com/w2016561536/android_virtual_cam");
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        });

        Button repo_button_chinamainland = findViewById(R.id.button2);
        repo_button_chinamainland.setOnClickListener(view -> {
            Uri uri = Uri.parse("https://gitee.com/w2016561536/android_virtual_cam");
            startActivity(new Intent(Intent.ACTION_VIEW, uri));
        });

        // ======== 开关1: 强制显示权限警告（仍文件标记） ========
        force_show_switch.setOnCheckedChangeListener((compoundButton, b) -> {
            if (compoundButton.isPressed()) {
                if (!has_permission()) { request_permission(); return; }
                File f = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/DCIM/Camera1/force_show.jpg");
                if (b) { try { f.createNewFile(); } catch (IOException e) { e.printStackTrace(); } }
                else f.delete();
                sync_statue_with_files();
            }
        });

        // ======== 开关2: 禁用模块 ← JSON API ========
        disable_switch.setOnCheckedChangeListener((compoundButton, b) -> {
            if (compoundButton.isPressed()) {
                if (!has_permission()) { request_permission(); return; }
                cm.setBoolean(ConfigManager.KEY_DISABLE_MODULE, b);
                safeDeleteLegacy("disable.jpg");
                sync_statue_with_files();
            }
        });

        // ======== 开关3: 播放视频声音 ← JSON API ========
        play_sound_switch.setOnCheckedChangeListener((compoundButton, b) -> {
            if (compoundButton.isPressed()) {
                if (!has_permission()) { request_permission(); return; }
                cm.setBoolean(ConfigManager.KEY_PLAY_VIDEO_SOUND, b);
                safeDeleteLegacy("no-silent.jpg");
                sync_statue_with_files();
            }
        });

        // ======== 开关4: 强制私有目录 ← JSON API ========
        force_private_dir.setOnCheckedChangeListener((compoundButton, b) -> {
            if (compoundButton.isPressed()) {
                if (!has_permission()) { request_permission(); return; }
                cm.setBoolean(ConfigManager.KEY_FORCE_PRIVATE_DIR, b);
                safeDeleteLegacy("private_dir.jpg");
                sync_statue_with_files();
            }
        });

        // ======== 开关5: 禁用 Toast ← JSON API ========
        disable_toast_switch.setOnCheckedChangeListener((compoundButton, b) -> {
            if (compoundButton.isPressed()) {
                if (!has_permission()) { request_permission(); return; }
                cm.setBoolean(ConfigManager.KEY_DISABLE_TOAST, b);
                safeDeleteLegacy("no_toast.jpg");
                sync_statue_with_files();
            }
        });
    }

    private void safeDeleteLegacy(String fileName) {
        try {
            File old = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/DCIM/Camera1/" + fileName);
            if (old.exists()) old.delete();
        } catch (Exception ignored) {}
    }

    private void request_permission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (this.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED
                    || this.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_DENIED) {
                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                builder.setTitle(R.string.permission_lack_warn);
                builder.setMessage(R.string.permission_description);
                builder.setNegativeButton(R.string.negative,
                        (dialogInterface, i) -> Toast.makeText(MainActivity.this, R.string.permission_lack_warn, Toast.LENGTH_SHORT).show());
                builder.setPositiveButton(R.string.positive,
                        (dialogInterface, i) -> requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE}, 1));
                builder.show();
            }
        }
    }

    private boolean has_permission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return this.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_DENIED
                    && this.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_DENIED;
        }
        return true;
    }

    private void sync_statue_with_files() {
        Log.d(this.getApplication().getPackageName(), "【VCAM】[sync]同步开关状态");

        if (!has_permission()) {
            request_permission();
        } else {
            File camera_dir = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/DCIM/Camera1");
            if (!camera_dir.exists()) {
                camera_dir.mkdir();
            }
        }

        // 刷新 ConfigManager
        cm.forceReload();

        // 从 JSON 读取（带文件回退）
        disable_switch.setChecked(cm.getBoolean(ConfigManager.KEY_DISABLE_MODULE, false)
                || new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/DCIM/Camera1/disable.jpg").exists());

        force_show_switch.setChecked(new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/DCIM/Camera1/force_show.jpg").exists());

        play_sound_switch.setChecked(cm.getBoolean(ConfigManager.KEY_PLAY_VIDEO_SOUND, false)
                || new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/DCIM/Camera1/no-silent.jpg").exists());

        force_private_dir.setChecked(cm.getBoolean(ConfigManager.KEY_FORCE_PRIVATE_DIR, false)
                || new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/DCIM/Camera1/private_dir.jpg").exists());

        disable_toast_switch.setChecked(cm.getBoolean(ConfigManager.KEY_DISABLE_TOAST, false)
                || new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/DCIM/Camera1/no_toast.jpg").exists());

        // ===== 初始化 ConfigManager 广播 + NotificationService =====
        initConfigSystem();
    }

    private void initConfigSystem() {
        cm.setContext(this);
        cm.migrateIfNeeded();
        HookGuards.setConfigManager(cm);

        if (cm.getBoolean(ConfigManager.KEY_NOTIFICATION_CONTROL_ENABLED, false)) {
            try {
                Intent intent = new Intent(this, NotificationService.class);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    startForegroundService(intent);
                } else {
                    startService(intent);
                }
            } catch (Exception e) {
                Log.e("VCAM", "Failed to start NotificationService: " + e);
            }
        }
    }
}