package com.coloryr.allmusic.server.netapi;

import com.coloryr.allmusic.server.core.AllMusic;
import com.coloryr.allmusic.server.core.music.MusicHttpClient;
import com.coloryr.allmusic.server.core.objs.HttpResObj;
import com.coloryr.allmusic.server.netapi.obj.EncResObj;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.cookie.Cookie;
import org.apache.hc.client5.http.cookie.CookieStore;
import org.apache.hc.client5.http.entity.UrlEncodedFormEntity;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.NameValuePair;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.message.BasicNameValuePair;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class NetApiHttpClient {
    private static final String NETEASE_APPVER = "8.7.01";
    private static final String NETEASE_EAPI_USER_AGENT = "Mozilla/5.0 (Linux; Android 11; M2007J3SC Build/RKQ1.200826.002; wv) AppleWebKit/537.36 (KHTML, like Gecko) Version/4.0 Chrome/77.0.3865.120 MQQBrowser/6.2 TBS/045714 Mobile Safari/537.36 NeteaseMusic/8.7.01";

    public static HttpResObj get(String path, String data) {
        try {
            data = URLEncoder.encode(data, StandardCharsets.UTF_8.toString());
            HttpGet request = new HttpGet(path + data);
            request.setHeader("referer", "https://music.163.com");
            request.setHeader("content-type", "application/json;charset=UTF-8");
            request.setHeader("user-agent", MusicHttpClient.UserAgent);
            HttpClientContext context = HttpClientContext.create();
            CookieStore cookieStore = MusicHttpClient.createCookieStore();
            context.setCookieStore(cookieStore);
            request.setHeader("cookie", buildNeteaseCookieHeader(cookieStore.getCookies()));
            try (CloseableHttpResponse response = MusicHttpClient.client.execute(request, context)) {
                int httpCode = response.getCode();
                HttpEntity entity = response.getEntity();
                if (entity == null) {
                    AllMusic.log.data("<light_purple>[AllMusic3]<red>获取网页错误");
                    return null;
                }
                InputStream inputStream = entity.getContent();
                boolean ok = httpCode == 200;
                ByteArrayOutputStream result = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int length;
                while ((length = inputStream.read(buffer)) != -1) {
                    result.write(buffer, 0, length);
                }
                inputStream.close();
                EntityUtils.consume(entity);
                String data1 = result.toString(StandardCharsets.UTF_8.toString());
                if (!ok) {
                    AllMusic.log.data("<light_purple>[AllMusic3]<red>服务器返回错误：" + data1);
                }
                // 保存 cookies
                MusicHttpClient.saveCookies(cookieStore);
                return new HttpResObj(data1, ok);
            }
        } catch (Exception e) {
            AllMusic.log.data("<light_purple>[AllMusic3]<red>获取网页错误");
            e.printStackTrace();
        }
        return null;
    }

    public static HttpResObj post(String url, JsonObject data, EncryptType type, String ourl) {
        try {
            HttpPost request = new HttpPost(url);
            request.setHeader("Content-Type", "application/x-www-form-urlencoded");
            request.setHeader("Referer", "https://music.163.com");
            HttpClientContext context = HttpClientContext.create();
            CookieStore cookieStore = MusicHttpClient.createCookieStore();
            context.setCookieStore(cookieStore);
            EncResObj res;
            List<Cookie> cookies = cookieStore.getCookies();
            request.setHeader("Accept", "*/*");
            request.setHeader("Accept-Language", "zh-CN,zh;q=0.9,en-US;q=0.8,en;q=0.7");
            request.setHeader("Connection", "keep-alive");
            request.setHeader("Cookie", buildNeteaseCookieHeader(cookies));
            // 注意：这里需要根据域名过滤，但为了简化，我们使用所有 cookies
            if (type == EncryptType.WEAPI) {
                request.setHeader("User-Agent", MusicHttpClient.UserAgent);
                String csrfToken = "";
                for (Cookie cookie : cookies) {
                    if (cookie.getName().equalsIgnoreCase("__csrf")) {
                        csrfToken = cookie.getValue();
                    }
                }
                data.addProperty("csrf_token", csrfToken);
                res = CryptoUtil.weapiEncrypt(AllMusic.gson.toJson(data));
                url = url.replaceFirst("\\w*api", "weapi");
                request.setUri(new URI(url));
                List<NameValuePair> params = new ArrayList<>();
                params.add(new BasicNameValuePair("params", res.params));
                params.add(new BasicNameValuePair("encSecKey", res.encSecKey));
                request.setEntity(new UrlEncodedFormEntity(params));
            } else if (type == EncryptType.EAPI) {
                String deviceId = randomHex(16).toUpperCase();
                String requestId = System.currentTimeMillis() + "_" + String.format("%04d", (int) (Math.random() * 1000));
                request.setHeader("User-Agent", NETEASE_EAPI_USER_AGENT);
                JsonObject header = new JsonObject();
                header.addProperty("appver", NETEASE_APPVER);
                header.addProperty("versioncode", "140");
                header.addProperty("buildver", new SimpleDateFormat("yyyyMMdd").format(new Date()));
                header.addProperty("resolution", "1920x1080");
                header.addProperty("os", "android");
                header.addProperty("requestId", requestId);
                header.addProperty("deviceId", deviceId);
                header.addProperty("channel", "netease");
                header.addProperty("osver", "android");
                for (Cookie cookie : cookies) {
                    if (cookie.getName().equalsIgnoreCase("MUSIC_U")) {
                        header.addProperty("MUSIC_U", cookie.getValue());
                    } else if (cookie.getName().equalsIgnoreCase("MUSIC_A")) {
                        header.addProperty("MUSIC_A", cookie.getValue());
                    } else if (cookie.getName().equalsIgnoreCase("channel")) {
                        header.addProperty("channel", cookie.getValue());
                    } else if (cookie.getName().equalsIgnoreCase("mobilename")) {
                        header.addProperty("mobilename", cookie.getValue());
                    } else if (cookie.getName().equalsIgnoreCase("osver")) {
                        header.addProperty("osver", cookie.getValue());
                    } else if (cookie.getName().equalsIgnoreCase("__csrf")) {
                        header.addProperty("__csrf", cookie.getValue());
                    }
                }
                data.add("header", header);
                res = CryptoUtil.eapi(ourl, data);
                url = url.replaceFirst("\\w*api", "eapi");
                request.setUri(new URI(url));
                List<NameValuePair> params = new ArrayList<>();
                params.add(new BasicNameValuePair("params", res.params));
                request.setEntity(new UrlEncodedFormEntity(params));
            } else {
                request.setUri(new URI(url));
                List<NameValuePair> params = new ArrayList<>();
                for (Map.Entry<String, JsonElement> item : data.entrySet()) {
                    params.add(new BasicNameValuePair(item.getKey(), item.getValue().getAsString()));
                }
                request.setEntity(new UrlEncodedFormEntity(params));
            }
            try (CloseableHttpResponse response = MusicHttpClient.client.execute(request, context)) {
                int httpCode = response.getCode();
                HttpEntity entity = response.getEntity();
                if (entity == null) {
                    AllMusic.log.data("<light_purple>[AllMusic3]<red>获取网页错误");
                    return null;
                }
                InputStream inputStream = entity.getContent();
                boolean ok = httpCode == 200;
                ByteArrayOutputStream result = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int length;
                while ((length = inputStream.read(buffer)) != -1) {
                    result.write(buffer, 0, length);
                }
                String data1 = result.toString(StandardCharsets.UTF_8.toString());
                EntityUtils.consume(entity);
                if (!ok) {
                    AllMusic.log.data("<light_purple>[AllMusic3]<red>服务器返回错误：" + data1);
                }
                // 保存 cookies
                MusicHttpClient.saveCookies(cookieStore);
                return new HttpResObj(data1, ok);
            }
        } catch (Exception e) {
            AllMusic.log.data("<light_purple>[AllMusic3]<red>获取网页错误");
            e.printStackTrace();
        }
        return null;
    }

    private static String buildNeteaseCookieHeader(List<Cookie> cookies) {
        StringBuilder builder = new StringBuilder();
        appendCookie(builder, "osver", "android");
        appendCookie(builder, "appver", NETEASE_APPVER);
        appendCookie(builder, "os", "android");
        appendCookie(builder, "deviceId", randomHex(16).toUpperCase());
        appendCookie(builder, "channel", "netease");
        appendCookie(builder, "requestId", System.currentTimeMillis() + "_" + String.format("%04d", (int) (Math.random() * 1000)));
        appendCookie(builder, "__remember_me", "true");
        for (Cookie cookie : cookies) {
            if (cookie == null || cookie.getName() == null || cookie.getValue() == null) {
                continue;
            }
            appendCookie(builder, cookie.getName(), cookie.getValue());
        }
        return builder.toString();
    }

    private static void appendCookie(StringBuilder builder, String name, String value) {
        if (value == null || value.isEmpty()) {
            return;
        }
        if (builder.length() > 0) {
            builder.append("; ");
        }
        builder.append(name).append("=").append(value);
    }

    private static String randomHex(int length) {
        StringBuilder builder = new StringBuilder(length);
        while (builder.length() < length) {
            builder.append(Integer.toHexString(AllMusic.random.nextInt(16)));
        }
        return builder.substring(0, length);
    }
}
