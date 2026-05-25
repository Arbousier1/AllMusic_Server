package com.coloryr.allmusic.server.netapi.obj.music.trialinfo;

import java.util.List;

public class TrialInfoObj {
    private List<song> data;

    public boolean isTrial() {
        song song = firstSong();
        if (song == null) {
            return false;
        }
        return song.getCode() != 200 && !song.hasPlayableUrl();
    }

    public freeTrialInfo getFreeTrialInfo() {
        song song = firstSong();
        if (song == null) {
            return new freeTrialInfo() {{
                this.setEnd(30);
            }};
        }
        return song.getFreeTrialInfo() == null ? new freeTrialInfo() {{
            this.setEnd(30);
        }} : song.getFreeTrialInfo();
    }

    public String getUrl() {
        song song = firstSong();
        return song == null ? null : song.getUrl();
    }

    private song firstSong() {
        if (data == null || data.size() == 0) {
            return null;
        }
        return data.get(0);
    }
}

class song {
    private freeTrialInfo freeTrialInfo;
    private int code;
    private String url;
    private uf uf;

    public String getUrl() {
        if (url != null && !url.isEmpty()) {
            return url;
        }
        return uf == null ? null : uf.getUrl();
    }

    public int getCode() {
        return code;
    }

    public freeTrialInfo getFreeTrialInfo() {
        return freeTrialInfo;
    }

    public boolean hasPlayableUrl() {
        String value = getUrl();
        return value != null && !value.isEmpty();
    }
}

class uf {
    private String url;

    public String getUrl() {
        return url;
    }
}
