package com.coloryr.allmusic.server.core.music.provider;

public class ProviderTextHelper {
    public boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    public String defaultIfBlank(String value, String def) {
        return isBlank(value) ? def : value;
    }
}
