package com.coloryr.allmusic.server.core.utils;

import com.coloryr.allmusic.server.core.AllMusic;
import com.coloryr.allmusic.server.core.music.MusicHttpClient;
import com.coloryr.allmusic.server.core.objs.HttpResObj;
import com.google.gson.JsonObject;
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

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public final class NeteaseQrLoginHandler {
    private static final String UNIKEY_URL = "https://music.163.com/weapi/login/qrcode/unikey";
    private static final String CHECK_URL = "https://music.163.com/weapi/login/qrcode/client/login";
    private static final String QR_PREFIX = "https://music.163.com/login?codekey=";
    private static final String NETEASE_APPVER = "8.7.01";
    private static final String PRESET_KEY = "0CoJUm6Qyw8W8jud";
    private static final String IV = "0102030405060708";
    private static final String BASE62 = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
    private static final char[] HEX_ARRAY = "0123456789ABCDEF".toCharArray();
    private static final int QR_EXPIRED = 800;
    private static final int QR_WAITING = 801;
    private static final int QR_SCANNED = 802;
    private static final int QR_CONFIRMED = 803;

    private NeteaseQrLoginHandler() {
    }

    public static Result requestUnikey() {
        JsonObject params = new JsonObject();
        params.addProperty("type", 1);
        HttpResObj res = post(UNIKEY_URL, params);
        if (res == null || !res.ok) {
            return null;
        }
        try {
            JsonObject root = AllMusic.gson.fromJson(res.data, JsonObject.class);
            if (root == null || root.get("code").getAsInt() != 200) {
                return null;
            }
            JsonObject data = root.getAsJsonObject("data");
            if (data == null) {
                return null;
            }
            String unikey = data.get("unikey").getAsString();
            return new Result(unikey, QR_PREFIX + unikey);
        } catch (Exception e) {
            AllMusic.log.data("<light_purple>[AllMusic3]<red>Failed to parse unikey response");
            e.printStackTrace();
            return null;
        }
    }

    public static PollResult pollStatus(String unikey) {
        JsonObject params = new JsonObject();
        params.addProperty("key", unikey);
        params.addProperty("type", 1);
        HttpResObj res = post(CHECK_URL, params);
        if (res == null || !res.ok) {
            return new PollResult(Status.ERROR, null);
        }
        try {
            JsonObject root = AllMusic.gson.fromJson(res.data, JsonObject.class);
            if (root == null) {
                return new PollResult(Status.ERROR, null);
            }
            int code = root.get("code").getAsInt();
            if (code == QR_EXPIRED) {
                return new PollResult(Status.EXPIRED, null);
            }
            if (code == QR_WAITING) {
                return new PollResult(Status.WAITING, null);
            }
            if (code == QR_SCANNED) {
                String nickname = null;
                if (root.has("data") && !root.get("data").isJsonNull()) {
                    JsonObject data = root.getAsJsonObject("data");
                    if (data.has("nickname")) {
                        nickname = data.get("nickname").getAsString();
                    }
                }
                return new PollResult(Status.SCANNED, nickname);
            }
            if (code == QR_CONFIRMED) {
                return new PollResult(Status.CONFIRMED, null);
            }
            return new PollResult(Status.ERROR, null);
        } catch (Exception e) {
            AllMusic.log.data("<light_purple>[AllMusic3]<red>Failed to parse QR poll response");
            e.printStackTrace();
            return new PollResult(Status.ERROR, null);
        }
    }

    private static HttpResObj post(String url, JsonObject data) {
        try {
            HttpPost request = new HttpPost(url);
            request.setHeader("Content-Type", "application/x-www-form-urlencoded");
            request.setHeader("Referer", "https://music.163.com");
            request.setHeader("Accept", "*/*");
            request.setHeader("Accept-Language", "zh-CN,zh;q=0.9,en-US;q=0.8,en;q=0.7");
            request.setHeader("Connection", "keep-alive");
            request.setHeader("User-Agent", MusicHttpClient.UserAgent);
            HttpClientContext context = HttpClientContext.create();
            CookieStore cookieStore = MusicHttpClient.createCookieStore();
            context.setCookieStore(cookieStore);
            List<Cookie> cookies = cookieStore.getCookies();
            request.setHeader("Cookie", buildNeteaseCookieHeader(cookies));
            data.addProperty("csrf_token", getCsrfToken(cookies));
            EncRes res = weapiEncrypt(AllMusic.gson.toJson(data));
            request.setUri(new URI(url.replaceFirst("\\w*api", "weapi")));
            List<NameValuePair> params = new ArrayList<>();
            params.add(new BasicNameValuePair("params", res.params));
            params.add(new BasicNameValuePair("encSecKey", res.encSecKey));
            request.setEntity(new UrlEncodedFormEntity(params));
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
                MusicHttpClient.saveCookies(cookieStore);
                return new HttpResObj(data1, ok);
            }
        } catch (Exception e) {
            AllMusic.log.data("<light_purple>[AllMusic3]<red>获取网页错误");
            e.printStackTrace();
        }
        return null;
    }

    private static String getCsrfToken(List<Cookie> cookies) {
        for (Cookie cookie : cookies) {
            if (cookie.getName().equalsIgnoreCase("__csrf")) {
                return cookie.getValue();
            }
        }
        return "";
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

    private static EncRes weapiEncrypt(String content) {
        String key = createSecretKey(16);
        String encText = aesEncrypt(aesEncrypt(content, PRESET_KEY, IV), key, IV);
        String encSecKey = rsaEncrypt(key);
        return new EncRes(encText, encSecKey);
    }

    private static String createSecretKey(int size) {
        StringBuilder key = new StringBuilder();
        for (int i = 0; i < size; i++) {
            double index = Math.floor(Math.random() * BASE62.length());
            key.append(BASE62.charAt((int) index));
        }
        return key.toString();
    }

    private static String aesEncrypt(String content, String key, String iv) {
        if (content == null || key == null) {
            return null;
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            byte[] bytes = content.getBytes(StandardCharsets.UTF_8);
            byte[] keys = key.getBytes(StandardCharsets.UTF_8);
            byte[] ivs = iv.getBytes(StandardCharsets.UTF_8);
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(keys, "AES"), new IvParameterSpec(ivs));
            bytes = cipher.doFinal(bytes);
            return Base64.getEncoder().encodeToString(bytes);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private static String rsaEncrypt(String text) {
        text = new StringBuffer(text).reverse().toString();
        BigInteger biText = new BigInteger(strToHex(text), 16);
        BigInteger biEx = new BigInteger("010001", 16);
        BigInteger biMod = new BigInteger("00e0b509f6259df8642dbc35662901477df22677ec152b5ff68ace615bb7b725152b3ab17a876aea8a5aa76d2e417629ec4ee341f56135fccf695280104e0312ecbda92557c93870114af6c9d05c4f7f0c3685b7a46bee255932575cce10b424d813cfe4875d3e82047b97ddef52741d546b8e289dc6935b3ece0462db0a22b8e7", 16);
        BigInteger biRet = biText.modPow(biEx, biMod);
        return zFill(biRet.toString(16));
    }

    private static String zFill(String str) {
        StringBuilder strBuilder = new StringBuilder(str);
        while (strBuilder.length() < 256) {
            strBuilder.insert(0, "0");
        }
        return strBuilder.toString();
    }

    private static String strToHex(String text) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < text.length(); i++) {
            int ch = text.charAt(i);
            builder.append(Integer.toHexString(ch));
        }
        return builder.toString();
    }

    private static String byteArrToHex(byte[] bytes) {
        char[] hexChars = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            int value = bytes[i] & 0xFF;
            hexChars[i * 2] = HEX_ARRAY[value >>> 4];
            hexChars[i * 2 + 1] = HEX_ARRAY[value & 0x0F];
        }
        return new String(hexChars);
    }

    private static String randomHex(int length) {
        StringBuilder builder = new StringBuilder(length);
        while (builder.length() < length) {
            builder.append(Integer.toHexString(AllMusic.random.nextInt(16)));
        }
        return builder.substring(0, length);
    }

    public enum Status {
        WAITING,
        SCANNED,
        CONFIRMED,
        EXPIRED,
        ERROR
    }

    public static final class Result {
        public final String unikey;
        public final String qrContent;

        private Result(String unikey, String qrContent) {
            this.unikey = unikey;
            this.qrContent = qrContent;
        }
    }

    public static final class PollResult {
        public final Status status;
        public final String nickname;

        private PollResult(Status status, String nickname) {
            this.status = status;
            this.nickname = nickname;
        }
    }

    private static final class EncRes {
        private final String params;
        private final String encSecKey;

        private EncRes(String params, String encSecKey) {
            this.params = params;
            this.encSecKey = encSecKey;
        }
    }
}
