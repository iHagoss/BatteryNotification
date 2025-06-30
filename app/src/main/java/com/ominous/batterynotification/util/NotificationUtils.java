package com.ominous.batterynotification.util;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import androidx.core.content.ContextCompat;
import com.ominous.batterynotification.R;

/**
 * Utility class for creating and updating the battery notification.
 */
public class NotificationUtils {
    public final static int NOTIFICATION_ID = 22222;

    /**
     * Creates the battery notification with all live values.
     * Uses a transparent icon so no real estate is taken in the status bar.
     * Battery percent decimal is half the size of the main digits.
     */
    public static Notification makeBatteryNotification(Context context, Intent intent) {
        NotificationManager notificationManager = ContextCompat.getSystemService(context, NotificationManager.class);

        // Create a notification channel on Android O+
        if (Build.VERSION.SDK_INT >= 26 &&
                notificationManager != null &&
                notificationManager.getNotificationChannel(context.getString(R.string.app_name)) == null) {
            NotificationChannel notificationChannel = new NotificationChannel(
                    context.getString(R.string.app_name),
                    context.getString(R.string.app_name),
                    NotificationManager.IMPORTANCE_MIN
            );
            notificationChannel.setDescription("Live battery info");
            notificationChannel.enableLights(false);
            notificationChannel.enableVibration(false);
            notificationChannel.setLockscreenVisibility(Notification.VISIBILITY_SECRET);
            notificationManager.createNotificationChannel(notificationChannel);
        }

        // Gather data for notification
        String dischargeCurrent = BatteryUtils.getLiveDischargeCurrent(context);
        String dischargeTime = BatteryUtils.getLiveDischargeTime(context, intent);
        CharSequence percentStyled = BatteryUtils.getStyledPercentString(intent); // Styled percent
        String temperature = BatteryUtils.getTemperatureC(context, intent);

        // Notification line formatting
        String notificationTitle = "Battery";
        // Compose the main text, using percentStyled for the percent
        CharSequence notificationText = dischargeTime + " (" + dischargeCurrent + ")\n"
                + percentStyled + "  |  " + temperature;

        Notification.Builder notificationBuilder;
        if (Build.VERSION.SDK_INT >= 26) {
            notificationBuilder = new Notification.Builder(context, context.getString(R.string.app_name));
        } else {
            notificationBuilder = new Notification.Builder(context)
                    .setPriority(Notification.PRIORITY_MIN);
        }

        notificationBuilder
                .setOngoing(true)
                .setShowWhen(false)
                .setContentTitle(notificationTitle)
                .setContentText(notificationText)
                .setStyle(new Notification.BigTextStyle().bigText(notificationText))
                .setSmallIcon(android.R.color.transparent); // Transparent icon: no status bar slot used

        return notificationBuilder.build();
    }
}