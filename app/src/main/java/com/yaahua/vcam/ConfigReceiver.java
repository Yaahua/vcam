package com.yaahua.vcam;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class ConfigReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (IpcContract.ACTION_REQUEST_CONFIG.equals(intent.getAction())) {
            String requesterPackage = intent.getStringExtra(IpcContract.EXTRA_REQUESTER_PACKAGE);
            if (requesterPackage == null || requesterPackage.isEmpty()) {
                LogUtil.log("【CS-Host】配置请求缺少 requester_package，已忽略");
                return;
            }
            LogUtil.log("【CS-Host】收到配置请求，正在发送当前配置");

            ConfigManager cm = new ConfigManager();
            cm.setContext(context);

            try {
                context.getPackageManager().getPackageInfo(requesterPackage, 0);
            } catch (Exception e) {
                LogUtil.log("【CS-Host】配置请求来源包不存在: " + requesterPackage);
                return;
            }

            java.util.Set<String> targetPackages = cm.getTargetPackages();
            if (!requesterPackage.equals(context.getPackageName())
                    && !targetPackages.isEmpty()
                    && !targetPackages.contains(requesterPackage)) {
                LogUtil.log("【CS-Host】拒绝向未授权包发送配置: " + requesterPackage);
                return;
            }
            cm.sendConfigBroadcast(context, requesterPackage);
        }
    }
}