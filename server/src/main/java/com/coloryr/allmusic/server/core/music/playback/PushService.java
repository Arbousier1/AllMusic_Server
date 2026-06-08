package com.coloryr.allmusic.server.core.music.playback;

import com.coloryr.allmusic.server.core.AllMusic;
import com.coloryr.allmusic.server.core.objs.music.SongInfoObj;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class PushService {
    private final Set<String> pushPlayer = ConcurrentHashMap.newKeySet();
    private volatile int pushTime = 0;
    private volatile String pushSender;
    private volatile SongInfoObj push;

    public void addPush(String player) {
        player = player.toLowerCase();
        pushPlayer.add(player);
    }

    public void startPush(String player, SongInfoObj music) {
        player = player.toLowerCase();
        push = music;
        pushSender = player;
        pushPlayer.add(player);
        pushTime = AllMusic.getConfig().voteTime;
    }

    public void pushTick() {
        pushTime--;
    }

    public SongInfoObj getPush() {
        return push;
    }

    public int getPushTime() {
        return pushTime;
    }

    public String getPushSender() {
        return pushSender;
    }

    public int getPushCount() {
        return pushPlayer.size();
    }

    public void clearPush() {
        pushTime = -1;
        push = null;
        pushSender = null;
        pushPlayer.clear();
    }

    public boolean containPush(String player) {
        player = player.toLowerCase();
        return pushPlayer.contains(player);
    }
}
