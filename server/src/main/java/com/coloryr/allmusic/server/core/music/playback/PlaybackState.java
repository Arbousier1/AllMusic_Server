package com.coloryr.allmusic.server.core.music.playback;

import com.coloryr.allmusic.server.core.music.LyricSave;
import com.coloryr.allmusic.server.core.objs.music.SongInfoObj;

public class PlaybackState {
    private volatile long musicAllTime;
    private volatile long musicLessTime;
    private volatile long musicNowTime;
    private volatile SongInfoObj nowPlayMusic;
    private volatile LyricSave lyric;
    private volatile String url;
    private volatile int error;

    public long getMusicAllTime() {
        return musicAllTime;
    }

    public void setMusicAllTime(long musicAllTime) {
        this.musicAllTime = musicAllTime;
    }

    public long getMusicLessTime() {
        return musicLessTime;
    }

    public void setMusicLessTime(long musicLessTime) {
        this.musicLessTime = musicLessTime;
    }

    public long getMusicNowTime() {
        return musicNowTime;
    }

    public void setMusicNowTime(long musicNowTime) {
        this.musicNowTime = musicNowTime;
    }

    public SongInfoObj getNowPlayMusic() {
        return nowPlayMusic;
    }

    public void setNowPlayMusic(SongInfoObj nowPlayMusic) {
        this.nowPlayMusic = nowPlayMusic;
    }

    public LyricSave getLyric() {
        return lyric;
    }

    public void setLyric(LyricSave lyric) {
        this.lyric = lyric;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public int getError() {
        return error;
    }

    public void setError(int error) {
        this.error = error;
    }

    public void clear() {
        musicNowTime = 0;
        musicAllTime = 0;
        musicLessTime = 0;
        lyric = null;
        nowPlayMusic = null;
        url = null;
    }
}
