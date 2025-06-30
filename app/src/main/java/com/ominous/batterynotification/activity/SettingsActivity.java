package com.ominous.batterynotification.util;

import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.RelativeSizeSpan;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.util.Locale;

/**
 * Utility class for battery info retrieval and formatting.
 * All methods are static for convenience.
 * Uses Australian English locale for formatting (Sydney).
 */
public class BatteryUtils {

    // Fallback design capacity for Galaxy S10+ (change for your device if needed)
    private static final double FALLBACK_CAPACITY_MAH = 4100.0;
    private static final String S10_PLUS_MODEL = "SM-G975F";
    private static final Locale SYDNEY_LOCALE = new Locale("en", "AU");

    /**
     * Attempts to read a value from sysfs using root (su), falling back to normal read.
     */
    private static String readSysfs(String path) {
        try {
            // Try reading using root (su)
            Process process = Runtime.getRuntime().exec(new String[] {"su", "-c", "cat " + path});
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String value = reader.readLine();
            process.waitFor();
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        } catch (Exception ignored) {}
        // Fallback: try without root
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            String line = br.readLine();
            if (line != null) return line.trim();
        } catch (IOException ignored) {}
        return null;
    }

    /**
     * Reads a double value from sysfs. Scales from micro-units to mA/mAh.
     */
    private static Double readSysfsDouble(String path, double scale) {
        String value = readSysfs(path);
        if (value == null) return null;
        try { return Double.parseDouble(value) / scale; }
        catch (NumberFormatException ignored) {}
        return null;
    }

    /**
     * Returns current_now in mA (may be negative for discharge), or null if unavailable.
     */
    public static Double getCurrentNow_mA() {
        Double current = readSysfsDouble("/sys/class/power_supply/battery/current_now", 1000.0);
        if (current != null) return current;
        return null;
    }

    /**
     * Returns design battery capacity in mAh.
     */
    public static double getDesignCapacity_mAh() {
        Double capacity = readSysfsDouble("/sys/class/power_supply/battery/charge_full_design", 1000.0);
        if (capacity != null && capacity > 1000.0) return capacity;
        if (Build.MODEL != null && Build.MODEL.equalsIgnoreCase(S10_PLUS_MODEL)) return FALLBACK_CAPACITY_MAH;
        return 4000.0;
    }

    /**
     * Returns battery percent as a fraction (0..1).
     */
    public static double getPercentRemaining(Intent intent) {
        int level = intent.getIntExtra("level", -1);
        int scale = intent.getIntExtra("scale", 100);
        if (level < 0 || scale <= 0) return 0.0;
        return Math.min(1.0, Math.max(0.0, (double)level / (double)scale));
    }

    /**
     * Returns the current draw as string (e.g. "550 mA"), or "Current unavailable".
     */
    public static String getLiveDischargeCurrent(Context context) {
        Double current_mA = getCurrentNow_mA();
        if (current_mA != null) {
            return String.format(SYDNEY_LOCALE, "%d mA", Math.abs(current_mA.intValue()));
        }
        return "Current unavailable";
    }

    /**
     * Returns the live calculated time remaining (as "Xh XXm XXs"), or "Time unavailable".
     */
    public static String getLiveDischargeTime(Context context, Intent intent) {
        double percent = getPercentRemaining(intent);
        double full_mAh = getDesignCapacity_mAh();
        double now_mAh = full_mAh * percent;

        Double current_mA = getCurrentNow_mA();
        if (current_mA == null || Math.abs(current_mA) < 1e-2) {
            return "Time unavailable";
        }
        int seconds = (int)((now_mAh / Math.abs(current_mA)) * 3600);
        return formatTimeHMS(seconds);
    }

    /**
     * Formats seconds as "Xh XXm XXs".
     */
    private static String formatTimeHMS(int seconds) {
        int h = seconds / 3600;
        int m = (seconds % 3600) / 60;
        int s = seconds % 60;
        return String.format(SYDNEY_LOCALE, "%dh %02dm %02ds", h, m, s);
    }

    /**
     * Returns the battery percent as a styled SpannableString (e.g., "85.6%", with decimal digit half size).
     * Only the decimal digit is shrunk, "%" is the same size as the integer part.
     */
    public static SpannableString getStyledPercentString(Intent intent) {
        int level = intent.getIntExtra("level", -1);
        int scale = intent.getIntExtra("scale", 100);
        double percent = (level < 0 || scale <= 0) ? 0 : (level * 100.0 / scale);
        String percentStr = String.format(SYDNEY_LOCALE, "%.1f%%", percent);
        int dotIndex = percentStr.indexOf(".");
        SpannableString ss = new SpannableString(percentStr);
        if (dotIndex > 0 && percentStr.length() > dotIndex + 1) {
            // Only shrink the decimal digit, not dot or percent
            ss.setSpan(new RelativeSizeSpan(0.5f), dotIndex + 1, dotIndex + 2, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
        return ss;
    }

    /**
     * Returns battery temperature in Celsius as a string (e.g., "36.2°C").
     */
    public static String getTemperatureC(Context context, Intent intent) {
        int temp = intent.getIntExtra("temperature", -1);
        if (temp >= 0) {
            return String.format(SYDNEY_LOCALE, "%.1f°C", temp / 10.0);
        }
        return "N/A";
    }
}
