package com.ominous.batterynotification.service;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import com.ominous.batterynotification.util.NotificationUtils;

/**
 * Foreground service to keep the battery notification live and updating.
 * Updates notification every 15 seconds using most recent battery intent.
 */
public class BatteryService extends Service {
    public final static IntentFilter UPDATE_FILTER = new IntentFilter(Intent.ACTION_BATTERY_CHANGED);
    private final static String TAG = "BatteryService";
    private BroadcastReceiver batteryReceiver;
    private Handler handler = new Handler();
    private Intent lastIntent = null;
    private boolean running = false;

    // Runnable to update notification every 15 seconds
    private final Runnable updateTask = new Runnable() {
        @Override
        public void run() {
            if (lastIntent != null) {
                startForeground(NotificationUtils.NOTIFICATION_ID,
                        NotificationUtils.makeBatteryNotification(BatteryService.this, lastIntent));
            }
            if (running) {
                handler.postDelayed(this, 15000); // 15 second interval
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG, "Starting Foreground Service");
        running = true;
        batteryReceiver = new BroadcastReceiver() {
            public void onReceive(Context context, Intent intent) {
                lastIntent = intent;
            }
        };
        registerReceiver(batteryReceiver, UPDATE_FILTER);
        // Immediately request initial battery intent
        Intent sticky = registerReceiver(null, UPDATE_FILTER);
        if (sticky != null) lastIntent = sticky;
        handler.post(updateTask);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // Service is already running, just keep updating notification
        return Service.START_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        running = false;
        handler.removeCallbacks(updateTask);
        try { this.unregisterReceiver(batteryReceiver); } catch (IllegalArgumentException e) { }
        super.onDestroy();
    }
}