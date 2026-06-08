package com.coloryr.allmusic.server.core.music.playback;

import com.coloryr.allmusic.server.core.AllMusic;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class VoteService {
    private final Set<String> votePlayer = ConcurrentHashMap.newKeySet();
    private volatile int voteTime = 0;
    private volatile String voteSender;

    public void addVote(String player) {
        player = player.toLowerCase();
        votePlayer.add(player);
    }

    public void startVote(String player) {
        player = player.toLowerCase();
        voteSender = player;
        votePlayer.add(player);
        voteTime = AllMusic.getConfig().voteTime;
    }

    public void voteTick() {
        voteTime--;
    }

    public int getVoteTime() {
        return voteTime;
    }

    public String getVoteSender() {
        return voteSender;
    }

    public int getVoteCount() {
        return votePlayer.size();
    }

    public void clearVote() {
        voteTime = -1;
        voteSender = null;
        votePlayer.clear();
    }

    public boolean containVote(String player) {
        player = player.toLowerCase();
        return votePlayer.contains(player);
    }
}
