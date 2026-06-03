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
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import com.yaahua.vcam.BuildConfig;

public class MainActivity extends Activity {

    private Switch force_show_switch;
    private Switch disable_switch;
    private Switch play_sound_switch;
    private Switch force_private_dir;
    private Switch disable_toast_switch;
    private ConfigManager cm;
    private TextView debugInfo;
    private ScrollView debugScroll;
    private final Handler handler = new Handler(Looper.getMainLooper());

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
        refreshDebugInfo();
    }

    @Override
    protected void onResume() {
        super.onResume();
        sync_statue_with_files();
        refreshDebugInfo();
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
        debugInfo = findViewById(R.id.debug_info);
        debugScroll = (ScrollView) debugInfo.getParent();
        Button btnRefresh = findViewById(R.id.btn_refresh_debug);

        btnRefresh.setOnClickListener(v -> refreshDebugInfo());

        // 打开应用时自动请求存储权限（直接弹系统对话框）
        autoRequestStoragePermission();

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
                refreshDebugInfo();
            }
        });

        // ======== 开关2: 禁用模块 ← JSON API ========
        disable_switch.setOnCheckedChangeListener((compoundButton, b) -> {
            if (compoundButton.isPressed()) {
                if (!has_permission()) { request_permission(); return; }
                cm.setBoolean(ConfigManager.KEY_DISABLE_MODULE, b);
                safeDeleteLegacy("disable.jpg");
                sync_statue_with_files();
                refreshDebugInfo();
            }
        });

        // ======== 开关3: 播放视频声音 ← JSON API ========
        play_sound_switch.setOnCheckedChangeListener((compoundButton, b) -> {
            if (compoundButton.isPressed()) {
                if (!has_permission()) { request_permission(); return; }
                cm.setBoolean(ConfigManager.KEY_PLAY_VIDEO_SOUND, b);
                safeDeleteLegacy("no-silent.jpg");
                sync_statue_with_files();
                refreshDebugInfo();
            }
        });

        // ======== 开关4: 强制私有目录 ← JSON API ========
        force_private_dir.setOnCheckedChangeListener((compoundButton, b) -> {
            if (compoundButton.isPressed()) {
                if (!has_permission()) { request_permission(); return; }
                cm.setBoolean(ConfigManager.KEY_FORCE_PRIVATE_DIR, b);
                safeDeleteLegacy("private_dir.jpg");
                sync_statue_with_files();
                refreshDebugInfo();
            }
        });

        // ======== 开关5: 禁用 Toast ← JSON API ========
        disable_toast_switch.setOnCheckedChangeListener((compoundButton, b) -> {
            if (compoundButton.isPressed()) {
                if (!has_permission()) { request_permission(); return; }
                cm.setBoolean(ConfigManager.KEY_DISABLE_TOAST, b);
                safeDeleteLegacy("no_toast.jpg");
                sync_statue_with_files();
                refreshDebugInfo();
            }
        });

        // 初始显示调试信息
        refreshDebugInfo();
    }

    // ==================== 调试信息 ====================

    private void refreshDebugInfo() {
        handler.post(() -> {
            StringBuilder sb = new StringBuilder();
            String now = new SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(new Date());
            sb.append("=== VCAM Debug ===  ").append(now).append("\n\n");

            // ── ① 运行环境 ──
            sb.append("── 环境 ───────────────────────────\n");
            sb.append("Package  : ").append(getPackageName()).append("\n");
            sb.append("Version  : ").append(BuildConfig.VERSION_NAME).append(" (").append(BuildConfig.VERSION_CODE).append(")\n");
            sb.append("SDK      : ").append(Build.VERSION.SDK_INT)
              .append(" (release: ").append(Build.VERSION.RELEASE).append(")\n");
            sb.append("Device   : ").append(Build.MANUFACTURER).append(" ").append(Build.MODEL).append("\n");

            // Xposed 检测
            boolean xposed = isXposedAvailable();
            sb.append("Xposed   : ").append(xposed ? "YES ✓" : "NO ✗ (non-Xposed mode)").append("\n");

            // 权限
            boolean perm = has_permission();
            sb.append("Storage  : ").append(perm ? "GRANTED ✓" : "DENIED ✗").append("\n");

            // ── ② 文件系统 ──
            sb.append("\n── 文件系统 ────────────────────────\n");
            String camPath = Environment.getExternalStorageDirectory().getAbsolutePath() + "/DCIM/Camera1";
            File camDir = new File(camPath);
            sb.append("Camera1  : ").append(camPath).append("\n");
            sb.append("  存在    : ").append(camDir.exists()).append("\n");
            if (camDir.exists()) {
                sb.append("  可读    : ").append(camDir.canRead()).append("\n");
                sb.append("  可写    : ").append(camDir.canWrite()).append("\n");
                File[] files = camDir.listFiles();
                sb.append("  文件数  : ").append(files != null ? files.length : -1).append("\n");
                if (files != null && files.length > 0) {
                    sb.append("  ──────────────────────────────\n");
                    for (File f : files) {
                        String type = f.isDirectory() ? " [DIR]" : f.isFile() ? " [FILE]" : " [?]";
                        sb.append("  ").append(f.getName()).append(type).append("  size=").append(f.length()).append("\n");
                    }
                }
            }

            // Config JSON 文件
            File configFile = new File(camDir, ConfigManager.CONFIG_FILE_NAME);
            sb.append("\nConfig   : ").append(configFile.getAbsolutePath()).append("\n");
            sb.append("  存在    : ").append(configFile.exists()).append("\n");

            // ── ③ 配置内容 ──
            sb.append("\n── JSON 配置 ──────────────────────\n");
            try {
                String raw = cm.exportConfig();
                if (raw == null || raw.isEmpty() || raw.equals("{}")) {
                    sb.append("  (empty / default)\n");
                } else {
                    // 缩进格式化
                    String pretty = new org.json.JSONObject(raw).toString(2);
                    sb.append(pretty).append("\n");
                }
            } catch (Exception e) {
                sb.append("  [parse error: ").append(e.getMessage()).append("]\n");
            }

            // ── ④ 开关状态 ──
            sb.append("\n── 开关状态 ───────────────────────\n");
            sb.append("S1 force_show      : ").append(force_show_switch.isChecked()).append("\n");
            sb.append("S2 disable_module   : ").append(disable_switch.isChecked())
              .append("  (JSON:").append(cm.getBoolean(ConfigManager.KEY_DISABLE_MODULE, false)).append(")\n");
            sb.append("S3 play_sound       : ").append(play_sound_switch.isChecked())
              .append("  (JSON:").append(cm.getBoolean(ConfigManager.KEY_PLAY_VIDEO_SOUND, false)).append(")\n");
            sb.append("S4 force_private    : ").append(force_private_dir.isChecked())
              .append("  (JSON:").append(cm.getBoolean(ConfigManager.KEY_FORCE_PRIVATE_DIR, false)).append(")\n");
            sb.append("S5 disable_toast    : ").append(disable_toast_switch.isChecked())
              .append("  (JSON:").append(cm.getBoolean(ConfigManager.KEY_DISABLE_TOAST, false)).append(")\n");

            // Notification
            sb.append("Notification Ctrl   : ").append(cm.getBoolean(ConfigManager.KEY_NOTIFICATION_CONTROL_ENABLED, false)).append("\n");

            // ── ⑤ Logcat (最近几行 VCAM) ──
            sb.append("\n── 最近 Logcat ────────────────────\n");
            String logLines = getRecentLogcat(15);
            sb.append(logLines.isEmpty() ? "  (no matching log)" : logLines);

            debugInfo.setText(sb.toString());
        });
    }

    private boolean isXposedAvailable() {
        try {
            Class.forName("de.robv.android.xposed.XposedBridge");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private String getRecentLogcat(int maxLines) {
        StringBuilder out = new StringBuilder();
        try {
            Process process = Runtime.getRuntime().exec(
                new String[]{"logcat", "-d", "-e", "VCAM", "-t", String.valueOf(maxLines)});
            BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream()));
            String line;
            int count = 0;
            while ((line = reader.readLine()) != null && count < maxLines) {
                out.append("  ").append(line).append("\n");
                count++;
            }
            reader.close();
            process.destroy();
        } catch (Exception e) {
            out.append("  [logcat unavailable: ").append(e.getMessage()).append("]\n");
        }
        return out.toString();
    }

    // ==================== 原有辅助方法 ====================

    private void safeDeleteLegacy(String fileName) {
        try {
            File old = new File(Environment.getExternalStorageDirectory().getAbsolutePath() + "/DCIM/Camera1/" + fileName);
            if (old.exists()) old.delete();
        } catch (Exception ignored) {}
    }

    private void autoRequestStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            boolean needRead = this.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_DENIED;
            // Android 13+ 用细粒度媒体权限
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                boolean needVideo = this.checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO)
                        == PackageManager.PERMISSION_DENIED;
                boolean needImages = this.checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES)
                        == PackageManager.PERMISSION_DENIED;
                if (needVideo || needImages) {
                    requestPermissions(
                        new String[]{Manifest.permission.READ_MEDIA_VIDEO, Manifest.permission.READ_MEDIA_IMAGES},
                        1);
                    return;
                }
            }
            if (needRead) {
                requestPermissions(
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    1);
            }
        }
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