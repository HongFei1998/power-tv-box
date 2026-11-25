package com.fongmi.android.tv.ui.custom;

import android.content.res.Resources;

import androidx.media3.common.C;
import androidx.media3.common.Format;
import androidx.media3.common.Player;
import androidx.media3.common.Tracks;
import androidx.media3.ui.DefaultTrackNameProvider;

import com.fongmi.android.tv.bean.Track;
import com.fongmi.android.tv.impl.SpeedListener;
import com.fongmi.android.tv.player.PlayerManager;
import com.fongmi.android.tv.player.util.PlayerHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;

/** UI-thread bridge shared by the two displays; never caches a service or creates a player. */
public final class OutputSession {

    private final Supplier<PlayerManager> current;
    private final Runnable play;
    private final SpeedListener changeSpeed;
    private final DefaultTrackNameProvider names;
    private final boolean livePage;

    public OutputSession(Resources resources, Supplier<PlayerManager> current, Runnable play, SpeedListener changeSpeed, boolean livePage) {
        this.current = current;
        this.play = play;
        this.changeSpeed = changeSpeed;
        this.names = new DefaultTrackNameProvider(resources);
        this.livePage = livePage;
    }

    public static final class State {
        private static final State EMPTY = new State(false, false, 0, 0, false, 1, false, false, false);
        public final boolean ready, playing, seekable, canSpeed, hasAudio, hasSubtitle;
        public final long position, duration;
        public final float speed;

        private State(boolean ready, boolean playing, long position, long duration, boolean seekable, float speed, boolean canSpeed, boolean hasAudio, boolean hasSubtitle) {
            this.ready = ready;
            this.playing = playing;
            this.position = position;
            this.duration = duration;
            this.seekable = seekable;
            this.speed = speed;
            this.canSpeed = canSpeed;
            this.hasAudio = hasAudio;
            this.hasSubtitle = hasSubtitle;
        }
    }

    public static final class Choice {
        public final String label;
        public final boolean selected;
        public final Runnable select;

        private Choice(String label, boolean selected, Runnable select) {
            this.label = label;
            this.selected = selected;
            this.select = select;
        }
    }

    private boolean seekable(PlayerManager manager) {
        return manager.getDuration() > 0 && (!livePage || !manager.isLive()) && manager.getPlayer().isCommandAvailable(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM);
    }

    private boolean canSpeed(PlayerManager manager) {
        return (!livePage || manager.isVod()) && manager.getPlayer().isCommandAvailable(Player.COMMAND_SET_SPEED_AND_PITCH);
    }

    private boolean playing(Player player) {
        return player.getPlayWhenReady() && player.getPlaybackState() != Player.STATE_ENDED && player.getPlaybackState() != Player.STATE_IDLE;
    }

    public State snapshot() {
        PlayerManager manager = current.get();
        if (manager == null) return State.EMPTY;
        Player player = manager.getPlayer();
        boolean trackSelection = player.isCommandAvailable(Player.COMMAND_SET_TRACK_SELECTION_PARAMETERS);
        return new State(player.isCommandAvailable(Player.COMMAND_PLAY_PAUSE), playing(player), Math.max(0, manager.getPosition()), Math.max(0, manager.getDuration()), seekable(manager), manager.getSpeed(), canSpeed(manager), trackSelection && manager.haveTrack(C.TRACK_TYPE_AUDIO), trackSelection && manager.haveTrack(C.TRACK_TYPE_TEXT));
    }

    public void toggle() {
        PlayerManager manager = current.get();
        if (manager == null || !manager.getPlayer().isCommandAvailable(Player.COMMAND_PLAY_PAUSE)) return;
        if (playing(manager.getPlayer())) manager.pause();
        else play.run(); // Page handles replay from ending/opening and idle preparation.
    }

    public void seekTo(long ms) {
        PlayerManager manager = current.get();
        if (manager == null || !seekable(manager)) return;
        Player player = manager.getPlayer();
        player.seekTo(Math.max(0, Math.min(ms, manager.getDuration())));
        if (player.isCommandAvailable(Player.COMMAND_PLAY_PAUSE)) player.play(); // Same as the local time bar.
    }

    public void skip(long deltaMs) {
        PlayerManager manager = current.get();
        if (manager != null) seekTo(manager.getPosition() + deltaMs);
    }

    public void setSpeed(float speed) {
        PlayerManager manager = current.get();
        if (manager != null && canSpeed(manager) && speed >= 0.25f && speed <= 5.0f) changeSpeed.setSpeed(speed);
    }

    public List<Choice> tracks(boolean subtitle) {
        PlayerManager manager = current.get();
        if (manager == null) return Collections.emptyList();
        Player player = manager.getPlayer();
        if (!player.isCommandAvailable(Player.COMMAND_SET_TRACK_SELECTION_PARAMETERS)) return Collections.emptyList();
        int type = subtitle ? C.TRACK_TYPE_TEXT : C.TRACK_TYPE_AUDIO;
        String key = manager.getKey(), url = manager.getUrl();
        List<Choice> choices = new ArrayList<>();
        for (Tracks.Group group : player.getCurrentTracks().getGroups()) {
            if (group.getType() != type) continue;
            for (int i = 0; i < group.length; i++) {
                final int index = i;
                Format format = group.getTrackFormat(i);
                String label = names.getTrackName(format);
                choices.add(new Choice(label, group.isTrackSelected(i), () -> {
                    PlayerManager active = current.get();
                    if (active != manager || active.getPlayer() != player || !Objects.equals(key, active.getKey()) || !Objects.equals(url, active.getUrl())) return;
                    if (!player.isCommandAvailable(Player.COMMAND_SET_TRACK_SELECTION_PARAMETERS)) return;
                    for (Tracks.Group now : player.getCurrentTracks().getGroups()) {
                        if (!now.getMediaTrackGroup().equals(group.getMediaTrackGroup())) continue;
                        // Match TrackDialog's select/deselect and per-key restoration for both engines.
                        Track track = new Track(type, label, PlayerHelper.describeFormat(format));
                        track.setSelected(!now.isTrackSelected(index));
                        active.setTrack(Collections.singletonList(track.key(key).save()));
                        return;
                    }
                }));
            }
        }
        return choices;
    }
}
