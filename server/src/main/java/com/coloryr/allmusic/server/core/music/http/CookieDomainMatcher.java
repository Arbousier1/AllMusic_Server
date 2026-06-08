package com.coloryr.allmusic.server.core.music.http;

import java.util.Locale;

public class CookieDomainMatcher {
    public boolean match(String host, String domain) {
        if (host == null || domain == null || domain.isEmpty()) {
            return false;
        }

        String domain1 = domain.toLowerCase(Locale.ROOT);
        while (domain1.startsWith(".")) {
            domain1 = domain1.substring(1);
        }
        return host.equals(domain1) || host.endsWith("." + domain1);
    }
}
