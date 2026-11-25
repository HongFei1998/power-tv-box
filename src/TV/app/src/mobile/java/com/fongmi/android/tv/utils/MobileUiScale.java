package com.fongmi.android.tv.utils;

import android.content.Context;
import android.content.res.Configuration;
import android.util.DisplayMetrics;

import com.github.catvod.utils.Prefers;

/**
 * High-resolution car head units often report a low density (1dp == 1px), which makes every
 * 48dp touch target physically tiny. Density is scaled up so the usable layout width stays in a
 * comfortable range; layout selection (sw600dp and friends) is recomputed to match.
 */
public final class MobileUiScale {

    public static final String KEY = "ui_scale";
    public static final int AUTO = 0;
    public static final int DEFAULT = 1;
    public static final int LARGE = 2;

    private static final float AUTO_TARGET_DP = 720f;
    private static final float LARGE_TARGET_DP = 560f;
    private static final float MAX_SCALE = 2.0f;

    private MobileUiScale() {}

    public static int getMode() {
        int mode = Prefers.getInt(KEY, AUTO);
        return mode >= AUTO && mode <= LARGE ? mode : AUTO;
    }

    public static void setMode(int mode) {
        Prefers.put(KEY, mode);
    }

    public static float scaleFor(Context base, int mode) {
        if (mode == DEFAULT) return 1f;
        DisplayMetrics metrics = base.getResources().getDisplayMetrics();
        if (metrics.density <= 0f) return 1f;
        float shortestDp = Math.min(metrics.widthPixels, metrics.heightPixels) / metrics.density;
        float target = mode == LARGE ? LARGE_TARGET_DP : AUTO_TARGET_DP;
        if (shortestDp <= target) return 1f;
        return Math.min(shortestDp / target, MAX_SCALE);
    }

    public static Context wrap(Context base) {
        float scale = scaleFor(base, getMode());
        // Keep dp/sp helpers on the application context in step with this screen's density.
        ResUtil.setUiScale(scale);
        if (scale <= 1.02f) return base;
        DisplayMetrics metrics = base.getResources().getDisplayMetrics();
        Configuration configuration = new Configuration(base.getResources().getConfiguration());
        int densityDpi = Math.round(metrics.densityDpi * scale);
        configuration.densityDpi = densityDpi;
        float density = densityDpi / 160f;
        if (density > 0f) {
            configuration.screenWidthDp = Math.round(metrics.widthPixels / density);
            configuration.screenHeightDp = Math.round(metrics.heightPixels / density);
            configuration.smallestScreenWidthDp = Math.min(configuration.screenWidthDp, configuration.screenHeightDp);
        }
        return base.createConfigurationContext(configuration);
    }
}
