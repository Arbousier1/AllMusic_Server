package com.coloryr.allmusic.server.core.music.http;

public class HttpTextHelper {
    public boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
