package com.coloryr.allmusic.server.netapi.meting;

import com.coloryr.allmusic.server.core.AllMusic;
import com.coloryr.allmusic.server.core.music.MusicHttpClient;
import com.coloryr.allmusic.server.core.objs.HttpResObj;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

public class MetingHttpClient {
    public static HttpResObj get(String url, Map<String, String> query, Map<String, String> headers) {
        return request("GET", url, query, null, null, headers);
    }

    public static HttpResObj postForm(String url, Map<String, String> form, Map<String, String> headers) {
        return request("POST", url, null, encodeForm(form), "application/x-www-form-urlencoded; charset=UTF-8", headers);
    }

    public static HttpResObj postJson(String url, String body, Map<String, String> headers) {
        return request("POST", url, null, body, "application/json; charset=UTF-8", headers);
    }

    private static HttpResObj request(String method, String url, Map<String, String> query, String body,
                                      String contentType, Map<String, String> headers) {
        String fullUrl = buildUrl(url, query);
        try {
            URI uri = URI.create(fullUrl);
            String host = uri.getHost();
            Map<String, String> headerMap = headers == null ? new LinkedHashMap<String, String>()
                    : new LinkedHashMap<String, String>(headers);
            mergeCookie(headerMap, host);
            if (!containsHeader(headerMap, "User-Agent")) {
                headerMap.put("User-Agent", MusicHttpClient.UserAgent);
            }
            if (!containsHeader(headerMap, "Accept")) {
                headerMap.put("Accept", "*/*");
            }

            if ("POST".equalsIgnoreCase(method)) {
                HttpPost request = new HttpPost(fullUrl);
                if (body != null) {
                    request.setEntity(new StringEntity(body, StandardCharsets.UTF_8));
                }
                if (contentType != null && !containsHeader(headerMap, "Content-Type")) {
                    headerMap.put("Content-Type", contentType);
                }
                applyHeaders(request, headerMap);
                return execute(request);
            }

            HttpGet request = new HttpGet(fullUrl);
            applyHeaders(request, headerMap);
            return execute(request);
        } catch (Exception e) {
            AllMusic.log.data("<light_purple>[AllMusic3]<red>Meting API request failed");
            e.printStackTrace();
            return null;
        }
    }

    private static HttpResObj execute(HttpGet request) {
        try (CloseableHttpResponse response = MusicHttpClient.client.execute(request)) {
            return readResponse(response);
        } catch (Exception e) {
            AllMusic.log.data("<light_purple>[AllMusic3]<red>Meting API request failed");
            e.printStackTrace();
            return null;
        }
    }

    private static HttpResObj execute(HttpPost request) {
        try (CloseableHttpResponse response = MusicHttpClient.client.execute(request)) {
            return readResponse(response);
        } catch (Exception e) {
            AllMusic.log.data("<light_purple>[AllMusic3]<red>Meting API request failed");
            e.printStackTrace();
            return null;
        }
    }

    private static HttpResObj readResponse(CloseableHttpResponse response) throws Exception {
        HttpEntity entity = response.getEntity();
        String data = entity == null ? "" : EntityUtils.toString(entity, StandardCharsets.UTF_8);
        int code = response.getCode();
        return new HttpResObj(data, code >= 200 && code < 300);
    }

    private static void applyHeaders(HttpGet request, Map<String, String> headers) {
        for (Map.Entry<String, String> item : headers.entrySet()) {
            if (item.getValue() != null) {
                request.setHeader(item.getKey(), item.getValue());
            }
        }
    }

    private static void applyHeaders(HttpPost request, Map<String, String> headers) {
        for (Map.Entry<String, String> item : headers.entrySet()) {
            if (item.getValue() != null) {
                request.setHeader(item.getKey(), item.getValue());
            }
        }
    }

    private static void mergeCookie(Map<String, String> headers, String host) {
        String dynamicCookie = MusicHttpClient.buildCookieHeader(host);
        String key = getHeaderKey(headers, "Cookie");
        String current = key == null ? null : headers.get(key);
        if (current == null || current.trim().isEmpty()) {
            if (dynamicCookie != null && !dynamicCookie.trim().isEmpty()) {
                headers.put("Cookie", dynamicCookie);
            }
            return;
        }
        if (dynamicCookie == null || dynamicCookie.trim().isEmpty()) {
            return;
        }
        headers.put(key, current + "; " + dynamicCookie);
    }

    private static boolean containsHeader(Map<String, String> headers, String name) {
        return getHeaderKey(headers, name) != null;
    }

    private static String getHeaderKey(Map<String, String> headers, String name) {
        for (String key : headers.keySet()) {
            if (key != null && key.equalsIgnoreCase(name)) {
                return key;
            }
        }
        return null;
    }

    private static String buildUrl(String url, Map<String, String> query) {
        if (query == null || query.isEmpty()) {
            return url;
        }
        return url + (url.contains("?") ? "&" : "?") + encodeForm(query);
    }

    private static String encodeForm(Map<String, String> form) {
        if (form == null || form.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> item : form.entrySet()) {
            if (item.getKey() == null || item.getValue() == null) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append('&');
            }
            builder.append(encode(item.getKey())).append('=').append(encode(item.getValue()));
        }
        return builder.toString();
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
        } catch (Exception ignored) {
            return value;
        }
    }
}
