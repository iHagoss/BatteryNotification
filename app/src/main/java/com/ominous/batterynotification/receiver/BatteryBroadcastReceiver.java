package com.ominous.batterynotification.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import com.ominous.batterynotification.service.BatteryService;

/**
 * BroadcastReceiver to start BatteryService at boot or app update.
 */
public class BatteryBroadcastReceiver extends BroadcastReceiver {
    public final static String ACTION_UPDATE = "com.ominous.batterynotification.UPDATE_ACTION";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction() == null) return;
        switch (intent.getAction()) {
            case Intent.ACTION_BOOT_COMPLETED:
            case Intent.ACTION_MY_PACKAGE_REPLACED:
                if (Build.VERSION.SDK_INT >= 26) {
                    context.startForegroundService(new Intent(context, BatteryService.class));
                } else {
                    context.startService(new Intent(context, BatteryService.class));
                }
                break;
        }
    }
}