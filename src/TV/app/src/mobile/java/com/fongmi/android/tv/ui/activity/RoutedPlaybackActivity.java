package com.fongmi.android.tv.ui.activity;

import android.content.ComponentName;
import android.os.Bundle;
import android.view.View;
import android.widget.FrameLayout;

import androidx.annotation.NonNull;
import androidx.media3.common.MediaMetadata;
import androidx.media3.common.Player;

import com.fongmi.android.tv.R;
import com.fongmi.android.tv.bean.Result;
import com.fongmi.android.tv.player.PlayerManager;
import com.fongmi.android.tv.ui.custom.ExternalVideoOutput;
import com.fongmi.android.tv.ui.custom.OutputSession;

import java.util.Objects;

/** A mobile playback page owns its presentation, never a second playback session. */
public abstract class RoutedPlaybackActivity extends PlaybackActivity {

    private ExternalVideoOutput output;
    private Runnable pendingStart;
    private boolean resumeOnOutput;
    private boolean closingOutput;

    @Override
    protected void initView(Bundle savedInstanceState) {
        FrameLayout host = findViewById(R.id.video);
        OutputSession session = new OutputSession(getResources(), this::outputPlayer, this::onPlay, this::setOutputSpeed, isLiveOutputPage());
        output = new ExternalVideoOutput(this, host, getPlayerView(), this::cancelExternalPlayback, this::outputChanged,
                session, isLiveOutputPage(), () -> outputPlayer() == null ? null : controller());
        // Route before service binding or a player surface can show a first frame here.
        output.applyDefault();
        super.initView(savedInstanceState);
    }

    protected final boolean isExternalOutput() {
        return output != null && output.isExternal();
    }

    protected final void chooseOutput() {
        if (!closingOutput && !isInPictureInPictureMode()) output.chooseScreen();
    }

    protected final boolean hasOutputMedia() {
        return outputPlayer() != null;
    }

    protected abstract void showControl();

    @Override
    public void onServiceDisconnected(ComponentName name) {
        super.onServiceDisconnected(name);
        if (isExternalOutput()) showControl();
    }

    @Override
    protected void onPlaybackControllerReady() {
        if (isExternalOutput()) showControl();
    }

    @Override
    public void onEvents(@NonNull Player player, @NonNull Player.Events events) {
        super.onEvents(player, events);
        if (isExternalOutput() && events.containsAny(Player.EVENT_MEDIA_ITEM_TRANSITION, Player.EVENT_TIMELINE_CHANGED,
                Player.EVENT_AVAILABLE_COMMANDS_CHANGED, Player.EVENT_PLAYBACK_STATE_CHANGED)) showControl();
    }

    protected abstract void onPlay();

    protected abstract void setOutputSpeed(float speed);

    protected boolean isLiveOutputPage() {
        return false;
    }

    protected final boolean dismissOutputSelection() {
        return output != null && output.dismissSelection();
    }

    private PlayerManager outputPlayer() {
        if (closingOutput || isFinishing() || isDestroyed() || !isExternalOutput() || output.isWaiting()) return null;
        if (controller() == null || !ownsMedia()) return null;
        PlayerManager manager = player();
        return manager.isReleased() || manager.isEmpty() ? null : manager;
    }

    private boolean ownsMedia() {
        return service() != null && getPlaybackKey() != null && isOwner();
    }

    private void outputChanged() {
        if (closingOutput || isFinishing()) return;
        onOutputChanged();
        if (output.isWaiting()) {
            pauseWhileWaiting();
            return;
        }
        Runnable start = pendingStart;
        boolean resume = resumeOnOutput;
        pendingStart = null;
        resumeOnOutput = false;
        if (start != null) start.run();
        else if (resume && ownsMedia()) player().play();
    }

    protected void onOutputChanged() {
    }

    private void pauseWhileWaiting() {
        if (!output.isWaiting() || !ownsMedia() || !player().getPlayer().getPlayWhenReady()) return;
        resumeOnOutput = true;
        player().pause();
    }

    @Override
    public void onPlayWhenReadyChanged(boolean playWhenReady, int reason) {
        // A parse or engine preparation already in flight may request play after removal.
        if (playWhenReady && !closingOutput) pauseWhileWaiting();
    }

    @Override
    protected void startPlayer(String key, Result result, boolean useParse, long timeout, long position, MediaMetadata metadata) {
        if (closingOutput || isFinishing()) return;
        if (output.isWaiting()) {
            pendingStart = () -> {
                if (service() != null && Objects.equals(key, getPlaybackKey()))
                    super.startPlayer(key, result, useParse, timeout, position, metadata);
            };
        } else {
            super.startPlayer(key, result, useParse, timeout, position, metadata);
        }
    }

    protected final void cancelExternalPlayback() {
        if (closingOutput) return;
        closingOutput = true;
        pendingStart = null;
        resumeOnOutput = false;
        // release() restores the original view hierarchy. Do not expose a last frame.
        getPlayerView().setVisibility(View.INVISIBLE);
        if (ownsMedia()) {
            player().reset();
            player().stop();
        }
        output.release();
        finish();
    }

    @Override
    protected boolean hasVisibleExternalPlayback() {
        return isExternalOutput() && !output.isWaiting() && !closingOutput && !isFinishing();
    }

    @Override
    protected void onDestroy() {
        pendingStart = null;
        if (output != null && !closingOutput) output.release();
        super.onDestroy();
    }
}
