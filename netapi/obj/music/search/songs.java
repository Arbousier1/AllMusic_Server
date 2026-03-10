package com.coloryr.allmusic.server.netapi.obj.music.search;

import java.util.List;

public class songs {
    private long id;
    private String name;
    private List<artists> artists;
    private album album;

    public long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getAlbum() {
        if (album == null || album.getName() == null) {
            return "";
        }
        return album.getName();
    }

    public String getArtists() {
        StringBuilder a = new StringBuilder();
        if (artists == null || artists.isEmpty()) {
            return "";
        }
        for (artists temp : artists) {
            if (temp == null || temp.getName() == null || temp.getName().isEmpty()) {
                continue;
            }
            a.append(temp.getName()).append(",");
        }
        if (a.length() == 0) {
            return "";
        }
        return a.substring(0, a.length() - 1);
    }
}
