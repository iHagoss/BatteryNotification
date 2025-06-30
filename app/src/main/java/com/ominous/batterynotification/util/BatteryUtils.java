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
 * Utility class for robust cross-device battery info retrieval/formatting.
 * Includes mainstream device and AOSP fallbacks for sysfs battery paths.
 */
public class BatteryUtils {

    // Device models and fallback design capacities (add more as needed)
    private static final String S10_PLUS_MODEL = "SM-G975F";
    private static final String PIXEL_6_MODEL = "oriole";           // Pixel 6 codename
    private static final String PIXEL_7_MODEL = "panther";          // Pixel 7 codename
    private static final String ONEPLUS_8_MODEL = "IN2013";
    private static final double S10_PLUS_CAPACITY = 4100.0;
    private static final double PIXEL_6_CAPACITY = 4614.0;
    private static final double PIXEL_7_CAPACITY = 4355.0;
    private static final double ONEPLUS_8_CAPACITY = 4300.0;
    private static final double GENERIC_AOSP_CAPACITY = 4000.0;

    // Known sysfs paths for current_now (add more for other devices/kernels)
    private static final String[] CURRENT_NOW_PATHS = new String[] {
        // Samsung/OneUI
        "/sys/class/power_supply/battery/current_now",
        // AOSP/Pixel
        "/sys/class/power_supply/bms/current_now",
        // Some OnePlus/OPPO
        "/sys/class/power_supply/usb/current_now",
        // Some MIUI/Xiaomi
        "/sys/class/power_supply/charger/current_now"
    };

    // Known sysfs paths for charge_full_design
    private static final String[] CHARGE_FULL_DESIGN_PATHS = new String[] {
        // Samsung/Pixel/AOSP
        "/sys/class/power_supply/battery/charge_full_design",
        "/sys/class/power_supply/bms/charge_full_design"
    };

    // Known sysfs paths for temp
    private static final String[] TEMP_PATHS = new String[] {
        "/sys/class/power_supply/battery/temp",
        "/sys/class/power_supply/max170xx_battery/temp",
        "/sys/class/power_supply/bms/temp"
    };

    /**
     * Attempts to read a value from sysfs using root (su), falling back to normal read.
     */
    private static String readSysfs(String path) {
        try {
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
     * Reads a double value from sysfs, scaled (for micro-units).
     * Tries all candidates in paths, returns first valid; null if all fail.
     */
    private static Double readSysfsDouble(String[] paths, double scale) {
        for (String path : paths) {
            String value = readSysfs(path);
            if (value != null) {
                try { return Double.parseDouble(value) / scale; }
                catch (NumberFormatException ignored) {}
            }
        }
        return null;
    }

    /**
     * Returns current_now in mA (may be negative for discharge), or null if unavailable.
     * Tries all mainstream device paths.
     */
    public static Double getCurrentNow_mA() {
        Double current = readSysfsDouble(CURRENT_NOW_PATHS, 1000.0);
        if (current != null) return current;
        // Fallback: attempt to parse from AOSP battery intent, if available in future
        return null;
    }

    /**
     * Returns design battery capacity in mAh.
     * Tries all mainstream device paths, then model-based fallback.
     */
    public static double getDesignCapacity_mAh() {
        Double capacity = readSysfsDouble(CHARGE_FULL_DESIGN_PATHS, 1000.0);
        if (capacity != null && capacity > 1000.0) return capacity;
        // Model-based fallback
        String model = Build.MODEL != null ? Build.MODEL : "";
        String device = Build.DEVICE != null ? Build.DEVICE : "";
        if (model.equalsIgnoreCase(S10_PLUS_MODEL) || device.equalsIgnoreCase("beyond2lte")) return S10_PLUS_CAPACITY;
        if (model.equalsIgnoreCase(PIXEL_6_MODEL) || device.equalsIgnoreCase(PIXEL_6_MODEL)) return PIXEL_6_CAPACITY;
        if (model.equalsIgnoreCase(PIXEL_7_MODEL) || device.equalsIgnoreCase(PIXEL_7_MODEL)) return PIXEL_7_CAPACITY;
        if (model.equalsIgnoreCase(ONEPLUS_8_MODEL) || device.equalsIgnoreCase(ONEPLUS_8_MODEL)) return ONEPLUS_8_CAPACITY;
        return GENERIC_AOSP_CAPACITY;
    }

    /**
     * Returns battery percent as a fraction (0..1).
     * Always uses system battery intent.
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
            return String.format(Locale.US, "%d mA", Math.abs(current_mA.intValue()));
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
        return String.format(Locale.US, "%dh %02dm %02ds", h, m, s);
    }

    /**
     * Returns the battery percent as a styled SpannableString (e.g., "85.6%", with decimal digit half size).
     * Only the decimal digit is shrunk, "%" is the same size as the integer part.
     */
    public static SpannableString getStyledPercentString(Intent intent) {
        int level = intent.getIntExtra("level", -1);
        int scale = intent.getIntExtra("scale", 100);
        double percent = (level < 0 || scale <= 0) ? 0 : (level * 100.0 / scale);
        String percentStr = String.format(Locale.US, "%.1f%%", percent);
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
     * Tries all mainstream device paths, then system intent.
     */
    public static String getTemperatureC(Context context, Intent intent) {
        // Try sysfs paths first (hardware accuracy)
        for (String path : TEMP_PATHS) {
            String value = readSysfs(path);
            if (value != null) {
                try {
                    // Most sysfs temps are in tenths of a degree C
                    double tempC = Double.parseDouble(value) / 10.0;
                    return String.format(Locale.US, "%.1f°C", tempC);
                } catch (NumberFormatException ignored) {}
            }
        }
        // Fallback: use system intent (usually tenths of a degree)
        int temp = intent.getIntExtra("temperature", -1);
        if (temp >= 0) {
            return String.format(Locale.US, "%.1f°C", temp / 10.0);
        }
        return "N/A";
    }
}