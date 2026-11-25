package com.fongmi.android.tv.ui.custom;

import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.ContextThemeWrapper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.media3.common.Player;
import androidx.media3.ui.PlayerSeekView;
import androidx.media3.ui.TimeBar;

import com.fongmi.android.tv.R;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** An independent copy of the mobile playback controls in the Presentation's view tree. */
public final class OriginalOutputBar extends FrameLayout {
    private final View controls;
    private final PlayerSeekView seek;
    private final OutputSession session;
    private final Supplier<Player> currentPlayer;
    private final List<Mirror> mirrors = new ArrayList<>();
    private final Runnable refresh = this::sync;
    private final Runnable hide = () -> showControls(false);
    private Player bound;
    private ScrollView menu;
    private View menuOwner;
    private boolean attached, touching, hidePending;

    public OriginalOutputBar(Context displayContext, OutputSession session, View mainControl,
                             boolean livePage, Supplier<Player> currentPlayer) {
        // Presentation's stock theme need not define Material/AppCompat attrs; retain its display metrics.
        super(new ContextThemeWrapper(displayContext, R.style.Theme_App));
        this.session = session;
        this.currentPlayer = currentPlayer;
        controls = LayoutInflater.from(getContext()).inflate(livePage ? R.layout.view_control_live : R.layout.view_control_vod, this, false);
        addView(controls);
        seek = controls.findViewById(R.id.seek);
        seek.getTimeBar().addListener(new TimeBar.OnScrubListener() {
            @Override public void onScrubStart(TimeBar bar, long position) { cancelHide(); }
            @Override public void onScrubMove(TimeBar bar, long position) { }
            @Override public void onScrubStop(TimeBar bar, long position, boolean canceled) {
                // PlayerSeekView already issued the seek. Match the page's resume-on-scrub behavior.
                if (!canceled && bound != null && bound == currentPlayer.get() && bound.isCommandAvailable(Player.COMMAND_PLAY_PAUSE)) bound.play();
            }
        });
        bindChildren((ViewGroup) controls, mainControl);
        setContentDescription(getResources().getString(R.string.output_toggle_controls));
        setOnClickListener(v -> { if (!dismissSelection()) showControls(controls.getVisibility() != VISIBLE); });
    }

    // Scope searches at each named container: the VOD side icon and action text both have id/danmaku.
    private void bindChildren(ViewGroup copy, View sourceScope) {
        for (int i = 0; i < copy.getChildCount(); i++) {
            View target = copy.getChildAt(i);
            View source = target.getId() == NO_ID ? null : sourceScope.findViewById(target.getId());
            if (source != null) {
                mirrors.add(new Mirror(source, target));
                if (target instanceof TextView || target instanceof ImageView) {
                    // applyDefault() may inflate this copy before Activity.initEvent() wires its listeners.
                    target.setOnClickListener(v -> {
                        if (!isAttachedToWindow() || !source.isEnabled()) return;
                        if (isChoice(v)) openChoices(v);
                        else if (currentPlayer.get() != null || v.getId() == R.id.output || v.getId() == R.id.back) source.performClick();
                        sync();
                    });
                    target.setOnLongClickListener(v -> currentPlayer.get() != null && source.isLongClickable() && source.isEnabled() && source.performLongClick());
                }
            }
            if (target instanceof ViewGroup && target != seek) bindChildren((ViewGroup) target, source == null ? sourceScope : source);
        }
    }

    private static boolean isChoice(View v) {
        int id = v.getId();
        return id == R.id.speed || id == R.id.audio || id == R.id.text;
    }

    private static final class Mirror {
        final View source, target;
        Drawable lastIcon;
        Mirror(View source, View target) { this.source = source; this.target = target; }
        void copy() {
            // The output is always fullscreen, even if the Activity is still in portrait detail mode.
            target.setVisibility(target.getId() == R.id.action ? VISIBLE : source.getVisibility());
            target.setSelected(source.isSelected());
            target.setActivated(source.isActivated());
            if (target instanceof ImageView || target instanceof TextView) {
                target.setClickable(source.hasOnClickListeners() || isChoice(target));
                target.setLongClickable(source.isLongClickable());
            }
            target.setEnabled(source.isEnabled());
            target.setAlpha(source.getAlpha());
            if (!TextUtils.equals(target.getContentDescription(), source.getContentDescription())) target.setContentDescription(source.getContentDescription());
            if (source instanceof TextView && target instanceof TextView) {
                TextView text = (TextView) target;
                CharSequence value = ((TextView) source).getText();
                if (!TextUtils.equals(text.getText(), value)) text.setText(value);
            } else if (source instanceof ImageView && target instanceof ImageView) {
                Drawable icon = ((ImageView) source).getDrawable();
                if (icon != lastIcon) {
                    lastIcon = icon;
                    // Never steal the primary ImageView's Drawable.Callback.
                    if (icon == null) ((ImageView) target).setImageDrawable(null);
                    else if (icon.getConstantState() != null) ((ImageView) target).setImageDrawable(icon.getConstantState().newDrawable(target.getResources(), target.getContext().getTheme()).mutate());
                }
            }
        }
    }

    private void sync() {
        removeCallbacks(refresh);
        if (!attached || getWindowVisibility() != VISIBLE || controls.getVisibility() != VISIBLE && menu == null) return;
        Player player = currentPlayer.get();
        if (bound != player && menu != null) closeMenu();
        bound = player;
        seek.setPlayer(player);
        OutputSession.State state = session.snapshot();
        for (Mirror mirror : mirrors) mirror.copy();
        seek.getTimeBar().setEnabled(state.seekable);
        View speed = controls.findViewById(R.id.speed), audio = controls.findViewById(R.id.audio), text = controls.findViewById(R.id.text);
        speed.setEnabled(state.canSpeed);
        audio.setEnabled(state.hasAudio);
        text.setEnabled(state.hasSubtitle);
        if (menuOwner != null && !menuOwner.isEnabled()) closeMenu();
        AccessibilityManager accessibility = (AccessibilityManager) getContext().getSystemService(Context.ACCESSIBILITY_SERVICE);
        if (state.playing && menu == null && !touching && (accessibility == null || !accessibility.isTouchExplorationEnabled())) {
            if (!hidePending) { hidePending = true; postDelayed(hide, 4000); }
        } else cancelHide();
        postDelayed(refresh, 350);
    }

    private void openChoices(View owner) {
        if (menu != null) return;
        boolean speed = owner.getId() == R.id.speed;
        OutputSession.State state = session.snapshot();
        List<OutputSession.Choice> tracks = speed ? null : session.tracks(owner.getId() == R.id.text);
        if (speed ? !state.canSpeed : tracks.isEmpty()) return;
        cancelHide();
        menuOwner = owner;
        menu = new ScrollView(getContext());
        menu.setBackgroundColor(Color.rgb(32, 32, 32));
        menu.setFillViewport(true);
        LinearLayout list = new LinearLayout(getContext());
        list.setGravity(Gravity.CENTER);
        list.setOrientation(LinearLayout.VERTICAL);
        menu.addView(list);
        controls.setVisibility(GONE);
        addView(menu, new LayoutParams(-1, -1));
        TextView title = new TextView(getContext());
        title.setText(speed ? R.string.control_speed : owner.getId() == R.id.audio ? R.string.play_track_audio : R.string.play_track_text);
        title.setTextColor(Color.WHITE);
        list.addView(title);
        Button cancel = addChoice(list, getResources().getString(android.R.string.cancel), false, () -> {});
        if (!isInTouchMode()) cancel.requestFocus();
        if (speed) {
            NumberFormat format = NumberFormat.getNumberInstance();
            for (float rate : new float[]{0.25f, 0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f, 2.5f, 3f, 3.5f, 4f, 4.5f, 5f}) {
                addChoice(list, format.format(rate) + "×", Math.abs(state.speed - rate) < 0.001f, () -> session.setSpeed(rate));
            }
        } else {
            for (OutputSession.Choice track : tracks) addChoice(list, track.label, track.selected, track.select);
        }
    }

    private Button addChoice(LinearLayout list, String label, boolean selected, Runnable action) {
        Button b = new Button(getContext());
        b.setAllCaps(false);
        b.setText(selected ? "✓ " + label : label);
        b.setSelected(selected);
        b.setOnClickListener(v -> {
            if (attached && bound != null && bound == currentPlayer.get() && menu != null) action.run();
            dismissSelection();
        });
        list.addView(b);
        if (selected && !isInTouchMode()) b.requestFocus();
        return b;
    }

    public boolean dismissSelection() {
        if (menu == null) return false;
        closeMenu();
        sync();
        return true;
    }

    private void closeMenu() {
        if (menu == null) return;
        removeView(menu);
        menu = null;
        controls.setVisibility(VISIBLE);
        if (!isInTouchMode()) menuOwner.requestFocus();
        menuOwner = null;
    }

    private void cancelHide() { removeCallbacks(hide); hidePending = false; }

    private void showControls(boolean show) {
        cancelHide();
        controls.setVisibility(show ? VISIBLE : GONE);
        if (show) sync();
        else { removeCallbacks(refresh); seek.setPlayer(null); bound = null; }
    }

    @Override public boolean dispatchTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) { touching = true; cancelHide(); }
        boolean handled = super.dispatchTouchEvent(event);
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) { touching = false; sync(); }
        return handled;
    }

    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); attached = true; sync(); }

    @Override protected void onWindowVisibilityChanged(int visibility) {
        super.onWindowVisibilityChanged(visibility);
        if (!attached) return;
        if (visibility == VISIBLE) sync();
        else suspend();
    }

    private void suspend() {
        cancelHide();
        removeCallbacks(refresh);
        closeMenu();
        seek.setPlayer(null);
        bound = null;
        touching = false;
    }

    @Override protected void onDetachedFromWindow() {
        attached = false;
        suspend();
        super.onDetachedFromWindow();
    }
}
