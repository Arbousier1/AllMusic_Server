package com.coloryr.allmusic.server.netapi.qq;

import com.coloryr.allmusic.server.core.AllMusic;
import com.coloryr.allmusic.server.core.music.MusicHttpClient;
import net.kyori.adventure.text.Component;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.cookie.Cookie;
import org.apache.hc.client5.http.cookie.CookieStore;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class QqApiQrLogin {
    private static final String LOGIN_URL = "https://xui.ptlogin2.qq.com/cgi-bin/xlogin";
    private static final String QR_URL = "https://ssl.ptlogin2.qq.com/ptqrshow";
    private static final String CHECK_URL = "https://ssl.ptlogin2.qq.com/ptqrlogin";
    private static final String APP_ID = "716027609";
    private static final String DAID = "383";
    private static final String S_URL = "https://y.qq.com/portal/profile.html";
    private static final String USER_AGENT = MusicHttpClient.UserAgent;
    private static final long POLL_INTERVAL = 2000L;
    private static final long LOGIN_TIMEOUT = 180000L;
    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);
    private static final Pattern CALLBACK_PATTERN = Pattern.compile("'([^']*)'");

    private QqApiQrLogin() {
    }

    public static boolean start(final Object sender) {
        if (!RUNNING.compareAndSet(false, true)) {
            return false;
        }

        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    doLogin(sender);
                } finally {
                    RUNNING.set(false);
                }
            }
        }, "AllMusic-QqQrLogin");
        thread.setDaemon(true);
        thread.start();
        return true;
    }

    private static void doLogin(Object sender) {
        CookieStore cookieStore = MusicHttpClient.createCookieStore();
        HttpClientContext context = HttpClientContext.create();
        context.setCookieStore(cookieStore);

        try {
            AllMusic.log.data("<light_purple>[AllMusic3]<yellow>Fetching QQ Music QR login code");

            requestText(context, buildXloginUrl());
            byte[] image = requestBytes(context, buildQrUrl());
            if (image == null || image.length == 0) {
                fail(sender, "Failed to fetch QQ Music QR code");
                return;
            }

            String qrsig = getCookieValue(cookieStore, "qrsig");
            if (isBlank(qrsig)) {
                fail(sender, "Missing qrsig cookie, QQ QR login cannot continue");
                return;
            }

            printQr(image);
            AllMusic.log.data(Component.text("Login page: https://y.qq.com"));
            AllMusic.log.data("<light_purple>[AllMusic3]<yellow>Scan with QQ or QQ Music and confirm on your phone");

            long endTime = System.currentTimeMillis() + LOGIN_TIMEOUT;
            String ptqrtoken = buildToken(qrsig);
            String loginSig = getCookieValue(cookieStore, "pt_login_sig");
            String lastCode = null;

            while (System.currentTimeMillis() < endTime) {
                String text = requestText(context, buildCheckUrl(ptqrtoken, loginSig));
                if (isBlank(text)) {
                    sleep();
                    continue;
                }

                Status status = parseStatus(text);
                if (status == null) {
                    sleep();
                    continue;
                }

                if (!same(lastCode, status.code)) {
                    lastCode = status.code;
                    logStatus(status);
                }

                if ("65".equals(status.code)) {
                    fail(sender, "QQ QR code expired, run /music loginqr qq again");
                    return;
                }

                if ("0".equals(status.code)) {
                    if (isBlank(status.redirectUrl)) {
                        fail(sender, "QQ QR login succeeded but redirect url is missing");
                        return;
                    }

                    requestText(context, status.redirectUrl);
                    MusicHttpClient.saveCookies(cookieStore);
                    if (hasLoginCookie()) {
                        success(sender);
                        return;
                    }

                    fail(sender, "QQ QR flow finished but login cookie was not detected");
                    return;
                }

                sleep();
            }

            fail(sender, "QQ QR login timed out, run /music loginqr qq again");
        } catch (Exception e) {
            AllMusic.log.data("<light_purple>[AllMusic3]<red>QQ QR login failed");
            e.printStackTrace();
            fail(sender, "QQ QR login failed, check console logs");
        }
    }

    private static String buildXloginUrl() throws Exception {
        StringBuilder builder = new StringBuilder(LOGIN_URL);
        builder.append("?appid=").append(APP_ID);
        builder.append("&daid=").append(DAID);
        builder.append("&style=33");
        builder.append("&hide_title_bar=1");
        builder.append("&hide_border=1");
        builder.append("&target=self");
        builder.append("&s_url=").append(encode(S_URL));
        builder.append("&pt_no_auth=1");
        return builder.toString();
    }

    private static String buildQrUrl() {
        return QR_URL + "?appid=" + APP_ID
                + "&e=2&l=M&s=3&d=72&v=4&daid=" + DAID
                + "&pt_3rd_aid=0&t=" + Math.random();
    }

    private static String buildCheckUrl(String token, String loginSig) throws Exception {
        StringBuilder builder = new StringBuilder(CHECK_URL);
        builder.append("?u1=").append(encode(S_URL));
        builder.append("&ptqrtoken=").append(token);
        builder.append("&ptredirect=0");
        builder.append("&h=1&t=1&g=1&from_ui=1");
        builder.append("&ptlang=2052");
        builder.append("&action=0-0-").append(System.currentTimeMillis());
        builder.append("&js_ver=22080914");
        builder.append("&js_type=1");
        builder.append("&login_sig=").append(encode(loginSig == null ? "" : loginSig));
        builder.append("&pt_uistyle=40");
        builder.append("&aid=").append(APP_ID);
        builder.append("&daid=").append(DAID);
        builder.append("&pt_3rd_aid=0");
        builder.append("&has_onekey=1");
        return builder.toString();
    }

    private static String requestText(HttpClientContext context, String url) {
        try {
            HttpGet request = new HttpGet(url);
            request.setHeader("user-agent", USER_AGENT);
            request.setHeader("accept", "*/*");
            request.setHeader("accept-language", "zh-CN,zh;q=0.9");
            request.setHeader("referer", "https://y.qq.com/");
            try (CloseableHttpResponse response = MusicHttpClient.client.execute(request, context)) {
                HttpEntity entity = response.getEntity();
                if (entity == null) {
                    return null;
                }
                String data = EntityUtils.toString(entity, StandardCharsets.UTF_8);
                EntityUtils.consume(entity);
                return data;
            }
        } catch (Exception e) {
            AllMusic.log.data("<light_purple>[AllMusic3]<red>QQ login request failed");
            e.printStackTrace();
            return null;
        }
    }

    private static byte[] requestBytes(HttpClientContext context, String url) {
        try {
            HttpGet request = new HttpGet(url);
            request.setHeader("user-agent", USER_AGENT);
            request.setHeader("accept", "image/webp,image/apng,image/svg+xml,image/*,*/*;q=0.8");
            request.setHeader("referer", "https://y.qq.com/");
            try (CloseableHttpResponse response = MusicHttpClient.client.execute(request, context)) {
                HttpEntity entity = response.getEntity();
                if (entity == null) {
                    return null;
                }
                try (InputStream inputStream = entity.getContent();
                     ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[4096];
                    int length;
                    while ((length = inputStream.read(buffer)) != -1) {
                        outputStream.write(buffer, 0, length);
                    }
                    EntityUtils.consume(entity);
                    return outputStream.toByteArray();
                }
            }
        } catch (Exception e) {
            AllMusic.log.data("<light_purple>[AllMusic3]<red>QQ QR image request failed");
            e.printStackTrace();
            return null;
        }
    }

    private static Status parseStatus(String text) {
        Matcher matcher = CALLBACK_PATTERN.matcher(text);
        String[] values = new String[6];
        int index = 0;
        while (matcher.find() && index < values.length) {
            values[index++] = matcher.group(1);
        }
        if (index == 0) {
            return null;
        }

        Status status = new Status();
        status.code = values[0];
        status.redirectUrl = index > 2 ? values[2] : null;
        status.message = index > 4 ? values[4] : null;
        return status;
    }

    private static void success(Object sender) {
        AllMusic.log.data("<light_purple>[AllMusic3]<dark_green>QQ Music QR login succeeded, cookie.json updated");
        sendMessage(sender, "<light_purple>[AllMusic3]<dark_green>QQ Music QR login succeeded, cookie.json updated");
    }

    private static void fail(Object sender, String message) {
        AllMusic.log.data("<light_purple>[AllMusic3]<red>" + message);
        sendMessage(sender, "<light_purple>[AllMusic3]<red>" + message);
    }

    private static void sendMessage(Object sender, String message) {
        if (sender != null) {
            AllMusic.side.sendMessageTask(sender, message);
        }
    }

    private static void logStatus(Status status) {
        if ("66".equals(status.code)) {
            AllMusic.log.data("<light_purple>[AllMusic3]<yellow>QQ QR status: waiting for scan");
            return;
        }
        if ("67".equals(status.code)) {
            AllMusic.log.data("<light_purple>[AllMusic3]<yellow>QQ QR status: scanned, waiting for confirmation");
            return;
        }
        if ("65".equals(status.code)) {
            AllMusic.log.data("<light_purple>[AllMusic3]<yellow>QQ QR status: expired");
            return;
        }
        if ("0".equals(status.code)) {
            AllMusic.log.data("<light_purple>[AllMusic3]<yellow>QQ QR status: confirmed, saving login state");
            return;
        }

        StringBuilder builder = new StringBuilder("<light_purple>[AllMusic3]<yellow>QQ QR status code=");
        builder.append(status.code == null ? "unknown" : status.code);
        if (!isBlank(status.message)) {
            builder.append(" msg=").append(status.message);
        }
        AllMusic.log.data(builder.toString());
    }

    private static boolean hasLoginCookie() {
        String uin = MusicHttpClient.getCookieValue("y.qq.com", "uin", "wxuin");
        String key = MusicHttpClient.getCookieValue("y.qq.com", "qqmusic_key", "p_skey", "skey");
        return !isBlank(uin) && !isBlank(key);
    }

    private static String getCookieValue(CookieStore cookieStore, String name) {
        if (cookieStore == null || name == null) {
            return null;
        }

        for (Cookie cookie : cookieStore.getCookies()) {
            if (cookie == null || cookie.getName() == null) {
                continue;
            }
            if (cookie.getName().equalsIgnoreCase(name)) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private static String buildToken(String qrsig) {
        int hash = 0;
        for (int i = 0; i < qrsig.length(); i++) {
            hash += (hash << 5) + qrsig.charAt(i);
        }
        return String.valueOf(hash & Integer.MAX_VALUE);
    }

    private static String encode(String value) throws Exception {
        return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
    }

    private static boolean same(String a, String b) {
        if (a == null) {
            return b == null;
        }
        return a.equals(b);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static void sleep() {
        try {
            Thread.sleep(POLL_INTERVAL);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private static void printQr(byte[] imageData) throws Exception {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageData));
        if (image == null) {
            throw new IllegalStateException("QQ QR image decode failed");
        }

        int minX = image.getWidth();
        int minY = image.getHeight();
        int maxX = -1;
        int maxY = -1;
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (!isDark(image.getRGB(x, y))) {
                    continue;
                }
                minX = Math.min(minX, x);
                minY = Math.min(minY, y);
                maxX = Math.max(maxX, x);
                maxY = Math.max(maxY, y);
            }
        }

        if (maxX < minX || maxY < minY) {
            throw new IllegalStateException("QQ QR image is empty");
        }

        int sourceWidth = maxX - minX + 1;
        int sourceHeight = maxY - minY + 1;
        int targetSize = Math.min(Math.max(sourceWidth, sourceHeight), 48);
        double scale = Math.max(sourceWidth, sourceHeight) / (double) targetSize;
        int compactWidth = Math.max(1, (int) Math.round(sourceWidth / scale));
        int compactHeight = Math.max(1, (int) Math.round(sourceHeight / scale));
        int padding = 1;

        boolean[][] pixels = new boolean[compactHeight + padding * 2][compactWidth + padding * 2];
        for (int y = 0; y < compactHeight; y++) {
            int sourceY = minY + Math.min(sourceHeight - 1, (int) Math.floor((y + 0.5D) * scale));
            for (int x = 0; x < compactWidth; x++) {
                int sourceX = minX + Math.min(sourceWidth - 1, (int) Math.floor((x + 0.5D) * scale));
                pixels[y + padding][x + padding] = isDark(image.getRGB(sourceX, sourceY));
            }
        }

        AllMusic.log.data(Component.text(""));
        for (int y = 0; y < pixels.length; y += 2) {
            StringBuilder line = new StringBuilder();
            for (int x = 0; x < pixels[0].length; x++) {
                boolean top = pixels[y][x];
                boolean bottom = y + 1 < pixels.length && pixels[y + 1][x];
                if (top && bottom) {
                    line.append('\u2588');
                } else if (top) {
                    line.append('\u2580');
                } else if (bottom) {
                    line.append('\u2584');
                } else {
                    line.append(' ');
                }
            }
            AllMusic.log.data(Component.text(line.toString()));
        }
        AllMusic.log.data(Component.text(""));
    }

    private static boolean isDark(int rgb) {
        int alpha = rgb >>> 24 & 255;
        if (alpha < 128) {
            return false;
        }

        int red = rgb >>> 16 & 255;
        int green = rgb >>> 8 & 255;
        int blue = rgb & 255;
        int luminance = (red * 299 + green * 587 + blue * 114) / 1000;
        return luminance < 128;
    }

    private static final class Status {
        private String code;
        private String message;
        private String redirectUrl;
    }
}
