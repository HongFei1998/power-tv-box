package com.fongmi.android.tv.setting;

import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.graphics.Point;
import android.view.Display;
import android.view.Surface;

import com.github.catvod.utils.Prefers;

public final class AppOrientation {
    private static final String KEY = "app_orientation";

    private AppOrientation() {}

    public static void initialize(Activity activity) {
        if (Prefers.getPrefers().contains(KEY)) return;
        Display display = activity.getWindowManager().getDefaultDisplay();
        Point size = new Point();
        display.getRealSize(size);
        int rotation = display.getRotation();
        boolean quarterTurn = rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270;
        int width = quarterTurn ? size.y : size.x;
        int height = quarterTurn ? size.x : size.y;
        if (width <= 0 || height <= 0) return;
        setMode(width == height ? 0 : width > height ? 1 : 2);
    }

    public static int getMode() {
        int mode = Prefers.getInt(KEY, 0);
        return mode >= 0 && mode <= 2 ? mode : 0;
    }

    public static void setMode(int mode) {
        Prefers.put(KEY, mode);
    }

    public static int resolve(int requested) {
        switch (getMode()) {
            case 1: return ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
            case 2: return ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
            default: return requested;
        }
    }
}
