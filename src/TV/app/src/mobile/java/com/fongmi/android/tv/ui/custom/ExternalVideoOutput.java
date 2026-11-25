package com.fongmi.android.tv.ui.custom;

import android.app.Activity;
import android.app.Presentation;
import android.graphics.Color;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.media3.common.Player;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.utils.PlaybackDisplays;

import java.util.function.Supplier;

/** Moves one existing playback hierarchy between windows. All methods run on the UI thread. */
public final class ExternalVideoOutput {

    private final Activity activity;
    private final FrameLayout host;
    private final View content;
    private final View panel;
    private final TextView status;
    private final FrameLayout controlsHost;
    private final OutputSession session;
    private final Runnable onRouteChanged;

    private AlertDialog picker;
    private Presentation presentation;
    private final View mainControl;
    private final boolean livePage;
    private final Supplier<Player> currentPlayer;
    private OriginalOutputBar secondary;
    private ViewGroup.LayoutParams controlParams;
    private int controlIndex;
    private int controlVisibility;
    private ViewGroup.LayoutParams originalParams;
    private int originalIndex;
    private boolean external;
    private boolean released;

    public ExternalVideoOutput(Activity activity, FrameLayout host, View content, Runnable onCancel, Runnable onRouteChanged,
                               OutputSession session, boolean livePage, Supplier<Player> currentPlayer) {
        mainControl = host.findViewById(R.id.control);
        if (content.getParent() != host || mainControl == null || mainControl.getParent() != host)
            throw new IllegalArgumentException("Playback view and controls must be children of host");
        this.livePage = livePage;
        this.currentPlayer = currentPlayer;
        this.activity = activity;
        this.host = host;
        this.content = content;
        this.onRouteChanged = onRouteChanged;
        this.session = session;
        panel = activity.getLayoutInflater().inflate(R.layout.view_external_output, host, false);
        status = panel.findViewById(R.id.externalOutputStatus);
        controlsHost = panel.findViewById(R.id.externalOutputControls);
        panel.findViewById(R.id.externalOutputChoose).setOnClickListener(v -> chooseScreen());
        panel.findViewById(R.id.externalOutputCancel).setOnClickListener(v -> { if (!released) onCancel.run(); });
    }

    /** Call once, before starting playback; a missing preferred display is not the local route. */
    public void applyDefault() {
        if (released || !PlaybackDisplays.hasExternalPreference()) return;
        showExternal(PlaybackDisplays.resolve(activity));
    }

    public void chooseScreen() {
        if (released || activity.isFinishing() || activity.isDestroyed() || (picker != null && picker.isShowing())) return;
        AlertDialog next = PlaybackDisplays.pick(activity, presentation == null ? null : presentation.getDisplay(), isWaiting(), display -> {
            if (released) return;
            if (display != null) showExternal(display);
            else if (external) {
                restoreLocal();
                onRouteChanged.run();
            }
        });
        picker = next;
        next.setOnDismissListener(dialog -> { if (picker == next) picker = null; });
    }

    public boolean dismissSelection() {
        return secondary != null && secondary.dismissSelection();
    }

    public boolean isExternal() {
        return external;
    }

    public boolean isWaiting() {
        return external && presentation == null;
    }

    private void showExternal(Display display) {
        if (display != null && display.isValid() && presentation != null && presentation.isShowing()
                && display.getDisplayId() == presentation.getDisplay().getDisplayId()) return;
        if (!external) {
            // Borrow the actual Activity subtree: its listeners, seek binding and page state stay intact.
            controlIndex = host.indexOfChild(mainControl);
            controlParams = mainControl.getLayoutParams();
            controlVisibility = mainControl.getVisibility();
            host.removeView(mainControl);
            controlsHost.addView(mainControl, new FrameLayout.LayoutParams(-1, -1));
            originalIndex = host.indexOfChild(content);
            originalParams = content.getLayoutParams();
            host.addView(panel);
            external = true;
        }
        closePresentation();
        status.setText(R.string.external_output_waiting);
        if (display != null && display.isValid()) {
            try {
                Presentation next = new Presentation(activity, display);
                presentation = next;
                Window window = next.getWindow();
                // Leave key focus with the Activity, but accept touch on this display.
                window.clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE);
                window.addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                        | (activity.getWindow().getAttributes().flags & WindowManager.LayoutParams.FLAG_SECURE));
                window.getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
                FrameLayout surface = new FrameLayout(next.getContext());
                surface.setBackgroundColor(Color.BLACK);
                next.setContentView(surface);
                next.setCancelable(false);
                // Presentation dismisses itself on display removal or incompatible metrics changes.
                next.setOnDismissListener(dialog -> {
                    if (presentation != next) return; // Stale dismiss after a switch/release.
                    presentation = null;
                    clearViews();
                    closePicker();
                    status.setText(R.string.external_output_waiting);
                    onRouteChanged.run();
                });
                next.show();
                surface.addView(content, new FrameLayout.LayoutParams(-1, -1));
                // OriginalOutputBar applies the Activity theme to this display's context and metrics.
                secondary = new OriginalOutputBar(next.getContext(), session, mainControl, livePage, currentPlayer);
                surface.addView(secondary, new FrameLayout.LayoutParams(-1, -1));
                panel.setClickable(false);
                controlsHost.setVisibility(View.VISIBLE);
                status.setText(activity.getString(R.string.external_output_active, display.getName()));
            } catch (RuntimeException e) {
                // The display/window token can disappear between enumeration and show().
                Log.w("ExternalVideoOutput", "Could not open presentation", e);
                closePresentation();
            }
        }
        onRouteChanged.run();
    }

    private static void detach(View view) {
        if (view != null && view.getParent() instanceof ViewGroup) ((ViewGroup) view.getParent()).removeView(view);
    }

    private void clearViews() {
        // Detach cancels the presentation bar's callbacks. Keep the Activity's actual controls
        // bound but inaccessible while waiting; only the two route actions stay active.
        detach(secondary);
        secondary = null;
        panel.setClickable(true);
        controlsHost.setVisibility(View.GONE);
        detach(content);
    }

    private void closePicker() {
        if (picker == null) return;
        picker.setOnDismissListener(null);
        picker.dismiss();
        picker = null;
    }

    private void closePresentation() {
        Presentation old = presentation;
        presentation = null;
        clearViews();
        if (old == null) return;
        old.setOnDismissListener(null);
        old.dismiss();
    }

    private void restoreLocal() {
        closePresentation();
        host.removeView(panel);
        host.addView(content, Math.min(originalIndex, host.getChildCount()), originalParams);
        controlsHost.removeView(mainControl);
        mainControl.setVisibility(controlVisibility);
        host.addView(mainControl, Math.min(controlIndex, host.getChildCount()), controlParams);
        external = false;
    }

    /** Stop playback first if ending the session; this only restores the view and closes the window. */
    public void release() {
        if (released) return;
        released = true;
        closePicker();
        if (external) restoreLocal();
    }
}
