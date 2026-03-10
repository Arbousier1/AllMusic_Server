package com.coloryr.allmusic.server.netapi.qq;

import com.coloryr.allmusic.server.core.AllMusic;
import com.coloryr.allmusic.server.core.music.MusicHttpClient;
import com.coloryr.allmusic.server.core.objs.HttpResObj;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.cookie.CookieStore;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

public final class QqApiHttpClient {
    private QqApiHttpClient() {
    }

    public static HttpResObj get(String url, Map<String, String> query, String referer) {
        try {
            String fullUrl = buildUrl(url, query);
            HttpGet request = new HttpGet(fullUrl);
            request.setHeader("accept", "application/json");
            request.setHeader("accept-language", "zh-CN,zh;q=0.9");
            request.setHeader("user-agent", MusicHttpClient.UserAgent);
            request.setHeader("origin", "https://y.qq.com");
            request.setHeader("referer", referer == null ? "https://y.qq.com/" : referer);

            String cookieHeader = MusicHttpClient.buildCookieHeader(new URI(fullUrl).getHost());
            if (!isBlank(cookieHeader)) {
                request.setHeader("cookie", cookieHeader);
            }

            HttpClientContext context = HttpClientContext.create();
            CookieStore cookieStore = MusicHttpClient.createCookieStore();
            context.setCookieStore(cookieStore);

            try (CloseableHttpResponse response = MusicHttpClient.client.execute(request, context)) {
                int httpCode = response.getCode();
                HttpEntity entity = response.getEntity();
                if (entity == null) {
                    return null;
                }

                try (InputStream inputStream = entity.getContent();
                     ByteArrayOutputStream result = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[4096];
                    int length;
                    while ((length = inputStream.read(buffer)) != -1) {
                        result.write(buffer, 0, length);
                    }

                    String data = result.toString(StandardCharsets.UTF_8.name());
                    EntityUtils.consume(entity);
                    MusicHttpClient.saveCookies(cookieStore);
                    return new HttpResObj(data, httpCode >= 200 && httpCode < 300);
                }
            }
        } catch (Exception e) {
            AllMusic.log.data("<light_purple>[AllMusic3]<red>QQ Music API request failed");
            e.printStackTrace();
        }

        return null;
    }

    private static String buildUrl(String url, Map<String, String> query) {
        StringBuilder builder = new StringBuilder(url);
        boolean first = !url.contains("?");
        for (Map.Entry<String, String> item : query.entrySet()) {
            if (isBlank(item.getValue())) {
                continue;
            }
            builder.append(first ? '?' : '&');
            first = false;
            builder.append(encode(item.getKey())).append('=').append(encode(item.getValue()));
        }
        return builder.toString();
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            throw new IllegalStateException("UTF-8 is not supported", e);
        }
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
