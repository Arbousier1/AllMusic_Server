package com.coloryr.allmusic.server.core.music.playback;

import com.coloryr.allmusic.server.core.AllMusic;
import com.coloryr.allmusic.server.core.objs.message.ARG;
import com.coloryr.allmusic.server.core.objs.music.MusicObj;
import com.coloryr.allmusic.server.core.objs.music.SongInfoObj;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class PlaybackQueue {
    private final Object lock = new Object();
    private final List<SongInfoObj> playList = new ArrayList<>();

    public void add(SongInfoObj info) {
        synchronized (lock) {
            playList.add(info);
        }
    }

    public int getListSize() {
        synchronized (lock) {
            return playList.size();
        }
    }

    public List<SongInfoObj> getList() {
        synchronized (lock) {
            return new ArrayList<>(playList);
        }
    }

    public void clear() {
        synchronized (lock) {
            playList.clear();
        }
    }

    public SongInfoObj remove(int index) {
        synchronized (lock) {
            return playList.remove(index);
        }
    }

    public void remove(SongInfoObj index) {
        synchronized (lock) {
            playList.remove(index);
        }
    }

    public void moveToFirst(SongInfoObj obj) {
        synchronized (lock) {
            playList.remove(obj);
            playList.add(0, obj);
        }
    }

    public String getAllList() {
        List<SongInfoObj> list1 = getList();
        StringBuilder list = new StringBuilder();
        String a;

        SongInfoObj info;
        for (int i = 0; i < list1.size(); i++) {
            info = list1.get(i);
            a = AllMusic.getMessage().musicPlay.listMusic.item;
            a = a.replace(ARG.index, String.valueOf(i + 1))
                    .replace(ARG.musicName, info.getName())
                    .replace(ARG.musicAuthor, info.getAuthor())
                    .replace(ARG.musicAl, info.getAl())
                    .replace(ARG.musicAlia, info.getAlia())
                    .replace(ARG.player, info.getCall());
            list.append(a).append("\n");
        }
        String temp = list.toString();
        if (temp.isEmpty())
            return "";
        return temp.substring(0, temp.length() - 1);
    }

    public boolean haveMusic(MusicObj music, SongInfoObj nowPlayMusic) {
        return haveMusic(music.id, music.api, nowPlayMusic);
    }

    public boolean haveMusic(String id, String api, SongInfoObj nowPlayMusic) {
        if (nowPlayMusic != null && nowPlayMusic.getID().equalsIgnoreCase(id)
                && Objects.equals(nowPlayMusic.getApi(), api))
            return true;
        synchronized (lock) {
            for (SongInfoObj item : playList) {
                if (item.getID().equalsIgnoreCase(id) && Objects.equals(item.getApi(), api)) {
                    return true;
                }
            }
        }
        return false;
    }

    public SongInfoObj findPlayerMusic(String name) {
        List<SongInfoObj> list1 = getList();
        for (SongInfoObj item : list1) {
            if (name.equalsIgnoreCase(item.getCall())) {
                return item;
            }
        }

        return null;
    }

    public SongInfoObj findMusicIndex(int index) {
        List<SongInfoObj> list1 = getList();
        index--;
        if (index <= 0) {
            return null;
        }
        if (list1.size() <= index) {
            return null;
        }

        return list1.get(index);
    }
}
