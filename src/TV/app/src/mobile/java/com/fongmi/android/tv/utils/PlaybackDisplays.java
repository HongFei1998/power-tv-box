package com.fongmi.android.tv.utils;

import android.app.Activity;
import android.hardware.display.DisplayManager;
import android.provider.Settings;
import android.view.Display;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;

import com.fongmi.android.tv.R;
import com.github.catvod.utils.Prefers;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Mobile playback destinations. A null destination always means the Activity's own window. */
public final class PlaybackDisplays {
    private static final String KEY = "mobile_playback_display_v1";
    private static final String LABEL = KEY + "_label";
    private static final String PROCESS = UUID.randomUUID().toString();

    private PlaybackDisplays() {}

    public interface Listener {
        void onSelected(@Nullable Display display);
    }

    private static Display host(Activity activity) {
        Display display = activity.getWindow().getDecorView().getDisplay();
        return display != null ? display : activity.getWindowManager().getDefaultDisplay();
    }

    private static List<Display> external(Activity activity) {
        List<Display> result = new ArrayList<>();
        DisplayManager manager = activity.getSystemService(DisplayManager.class);
        int hostId = host(activity).getDisplayId();
        if (manager == null) return result;
        // Some head units build their extra panels without advertising DISPLAY_CATEGORY_PRESENTATION
        // although a Presentation window still works there, so every display is inspected instead.
        for (Display display : manager.getDisplays()) {
            if (!display.isValid() || display.getDisplayId() == hostId) continue;
            if ((display.getFlags() & Display.FLAG_PRIVATE) != 0) continue;
            if (display.getState() == Display.STATE_OFF) continue;
            Display.Mode mode = display.getMode();
            if (mode == null || mode.getPhysicalWidth() <= 0 || mode.getPhysicalHeight() <= 0) continue;
            result.add(display);
        }
        return result;
    }

    private static String describe(Activity activity, Display display) {
        Display.Mode mode = display.getMode();
        return activity.getString(R.string.output_screen_description, display.getName(), mode.getPhysicalWidth(), mode.getPhysicalHeight(), display.getDisplayId());
    }

    // A logical display ID is not a permanent hardware ID. Keep the boot count and mode
    // with it: never route by a same-name or same-size heuristic after an ID change.
    private static String fingerprint(Display display) {
        Display.Mode mode = display.getMode();
        return display.getDisplayId() + ":" + mode.getPhysicalWidth() + ":" + mode.getPhysicalHeight() + ":" + display.getName();
    }

    private static String boot(Activity activity) {
        try {
            int count = Settings.Global.getInt(activity.getContentResolver(), Settings.Global.BOOT_COUNT, -1);
            if (count >= 0) return Integer.toString(count);
        } catch (SecurityException ignored) {
            // Some OEMs restrict boot count; recognize only the current process then.
        }
        return PROCESS;
    }

    public static boolean hasExternalPreference() {
        return !Prefers.getString(KEY).isEmpty();
    }

    @Nullable
    public static Display resolve(Activity activity) {
        String wanted = Prefers.getString(KEY);
        if (wanted.isEmpty()) return null;
        String boot = boot(activity);
        for (Display display : external(activity)) if (wanted.equals(boot + "/" + fingerprint(display))) return display;
        return null;
    }

    public static String summary(Activity activity) {
        if (!hasExternalPreference()) return activity.getString(R.string.output_screen_host);
        Display display = resolve(activity);
        if (display != null) return describe(activity, display);
        return activity.getString(R.string.output_screen_unavailable, Prefers.getString(LABEL));
    }

    public static void pickDefault(Activity activity, Runnable changed) {
        Display selected = resolve(activity);
        show(activity, selected, selected == null && hasExternalPreference(), true, display -> {
            if (display == null) Prefers.getPrefers().edit().remove(KEY).remove(LABEL).apply();
            else Prefers.getPrefers().edit().putString(KEY, boot(activity) + "/" + fingerprint(display)).putString(LABEL, describe(activity, display)).apply();
            changed.run();
        });
    }

    /** Session-only picker; choosing here never modifies the default. */
    public static void pick(Activity activity, @Nullable Display active, Listener listener) {
        pick(activity, active, false, listener);
    }

    public static AlertDialog pick(Activity activity, @Nullable Display active, boolean missing, Listener listener) {
        return show(activity, active, missing, false, listener);
    }

    private static AlertDialog show(Activity activity, @Nullable Display active, boolean missing, boolean defaults, Listener listener) {
        List<Display> screens = external(activity);
        String[] labels = new String[screens.size() + 1];
        String[] keys = new String[labels.length];
        labels[0] = activity.getString(R.string.output_screen_host) + "\n" + describe(activity, host(activity));
        int checked = active == null && !missing ? 0 : -1;
        for (int i = 0; i < screens.size(); i++) {
            Display display = screens.get(i);
            labels[i + 1] = describe(activity, display);
            keys[i + 1] = fingerprint(display);
            if (active != null && active.getDisplayId() == display.getDisplayId()) checked = i + 1;
        }
        AlertDialog dialog = new MaterialAlertDialogBuilder(activity)
                .setTitle(defaults ? R.string.output_screen_default : R.string.output_screen_choose)
                .setNegativeButton(android.R.string.cancel, null)
                .setSingleChoiceItems(labels, checked, (d, which) -> {
                    d.dismiss();
                    if (which == 0) {
                        listener.onSelected(null);
                        return;
                    }
                    // The screen may have disappeared while the choices were visible.
                    for (Display live : external(activity)) if (keys[which].equals(fingerprint(live))) {
                        listener.onSelected(live);
                        return;
                    }
                    Toast.makeText(activity, R.string.output_screen_list_changed, Toast.LENGTH_SHORT).show();
                }).create();
        dialog.show();
        return dialog;
    }
}
