package com.coloryr.allmusic.server.netapi;

import com.coloryr.allmusic.server.core.AllMusic;
import com.coloryr.allmusic.server.core.music.MusicHttpClient;
import com.coloryr.allmusic.server.core.objs.HttpResObj;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.kyori.adventure.text.Component;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicBoolean;

public final class NetApiQrLogin {
    private static final String[] KEY_URLS = new String[]{
            "https://music.163.com/weapi/login/qrcode/unikey",
            "https://music.163.com/weapi/login/qrcode/key"
    };
    private static final String[] CREATE_URLS = new String[]{
            "https://music.163.com/weapi/login/qrcode/create"
    };
    private static final String[] CHECK_URLS = new String[]{
            "https://music.163.com/weapi/login/qrcode/client/login"
    };
    private static final long POLL_INTERVAL = 2000L;
    private static final long LOGIN_TIMEOUT = 180000L;
    private static final AtomicBoolean RUNNING = new AtomicBoolean(false);

    private NetApiQrLogin() {
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
        }, "AllMusic-NetEaseQrLogin");
        thread.setDaemon(true);
        thread.start();
        return true;
    }

    private static void doLogin(Object sender) {
        try {
            AllMusic.log.data("<light_purple>[AllMusic3]<yellow>正在获取网易云音乐扫码登录二维码");
            String key = requestKey();
            if (isBlank(key)) {
                fail(sender, "获取二维码 key 失败");
                return;
            }

            QrData qrData = requestQrData(key);
            String qrUrl = qrData.url;
            if (isBlank(qrUrl)) {
                qrUrl = "https://music.163.com/login?codekey=" + key;
            }

            if (!isBlank(qrData.image)) {
                printQr(qrData.image);
            } else {
                AllMusic.log.data("<light_purple>[AllMusic3]<yellow>二维码图片接口不可用，请手动打开下方链接");
            }

            AllMusic.log.data(Component.text("登录链接: " + qrUrl));
            AllMusic.log.data("<light_purple>[AllMusic3]<yellow>请使用网易云音乐 App 扫码并确认登录");

            long endTime = System.currentTimeMillis() + LOGIN_TIMEOUT;
            int lastCode = Integer.MIN_VALUE;
            while (System.currentTimeMillis() < endTime) {
                QrStatus status = checkStatus(key);
                if (status == null) {
                    sleep();
                    continue;
                }

                if (status.code != lastCode) {
                    lastCode = status.code;
                    logStatus(status.code, status.message);
                }

                if (status.code == 800) {
                    fail(sender, "二维码已过期，请重新执行 /music loginqr");
                    return;
                }

                if (!isBlank(status.ticket)) {
                    if (finalizeLogin(status.ticket)) {
                        success(sender);
                        return;
                    }
                }

                if (status.code == 803 || (status.code == 200 && hasLoginCookie())) {
                    success(sender);
                    return;
                }

                sleep();
            }

            fail(sender, "扫码登录超时，请重新执行 /music loginqr");
        } catch (Exception e) {
            AllMusic.log.data("<light_purple>[AllMusic3]<red>扫码登录出现异常");
            e.printStackTrace();
            fail(sender, "扫码登录失败，请查看控制台日志");
        }
    }

    private static void success(Object sender) {
        String user = MusicHttpClient.getCookieValue("music.163.com", "MUSIC_U", "MUSIC_A");
        if (isBlank(user)) {
            AllMusic.log.data("<light_purple>[AllMusic3]<yellow>扫码流程完成，但未检测到登录 cookie");
            sendMessage(sender, "<light_purple>[AllMusic3]<yellow>扫码流程已完成，但未检测到登录 cookie，请重试");
            return;
        }

        AllMusic.log.data("<light_purple>[AllMusic3]<dark_green>网易云扫码登录成功，cookie.json 已更新");
        sendMessage(sender, "<light_purple>[AllMusic3]<dark_green>网易云扫码登录成功，cookie.json 已更新");
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

    private static String requestKey() {
        for (String url : KEY_URLS) {
            JsonObject body = new JsonObject();
            body.addProperty("type", 1);
            body.addProperty("timestamp", System.currentTimeMillis());
            JsonObject root = requestJson(url, body);
            if (root == null) {
                continue;
            }

            String key = firstString(root, "data.unikey", "unikey", "data.key", "key");
            if (!isBlank(key)) {
                return key;
            }
        }
        return null;
    }

    private static QrData requestQrData(String key) {
        QrData qrData = new QrData();
        for (String url : CREATE_URLS) {
            JsonObject body = new JsonObject();
            body.addProperty("key", key);
            body.addProperty("qrimg", true);
            body.addProperty("type", 1);
            body.addProperty("timestamp", System.currentTimeMillis());
            JsonObject root = requestJson(url, body);
            if (root == null) {
                continue;
            }

            qrData.image = firstString(root, "data.qrimg", "qrimg");
            qrData.url = firstString(root, "data.qrurl", "qrurl", "data.url", "url");
            if (!isBlank(qrData.image) || !isBlank(qrData.url)) {
                return qrData;
            }
        }
        return qrData;
    }

    private static QrStatus checkStatus(String key) {
        for (String url : CHECK_URLS) {
            JsonObject body = new JsonObject();
            body.addProperty("key", key);
            body.addProperty("type", 1);
            body.addProperty("timestamp", System.currentTimeMillis());
            JsonObject root = requestJson(url, body);
            if (root == null) {
                continue;
            }

            QrStatus status = new QrStatus();
            status.code = resolveCode(root);
            status.message = firstString(root, "message", "msg", "data.message", "data.msg");
            status.ticket = firstString(root, "ticket", "data.ticket");
            return status;
        }
        return null;
    }

    private static boolean finalizeLogin(String ticket) {
        for (String url : CHECK_URLS) {
            JsonObject body = new JsonObject();
            body.addProperty("ticket", ticket);
            body.addProperty("type", 1);
            body.addProperty("timestamp", System.currentTimeMillis());
            JsonObject root = requestJson(url, body);
            if (root == null) {
                continue;
            }

            int code = resolveCode(root);
            if (code == 803 || code == 200) {
                return hasLoginCookie();
            }
        }
        return false;
    }

    private static JsonObject requestJson(String url, JsonObject body) {
        HttpResObj res = NetApiHttpClient.post(url, body, EncryptType.WEAPI, null);
        if (res == null || !res.ok || isBlank(res.data)) {
            return null;
        }

        try {
            JsonElement element = AllMusic.gson.fromJson(res.data, JsonElement.class);
            return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
        } catch (Exception e) {
            AllMusic.log.data("<light_purple>[AllMusic3]<red>二维码登录响应解析失败");
            e.printStackTrace();
            return null;
        }
    }

    private static boolean hasLoginCookie() {
        return !isBlank(MusicHttpClient.getCookieValue("music.163.com", "MUSIC_U", "MUSIC_A"));
    }

    private static void logStatus(int code, String message) {
        switch (code) {
            case 801:
                AllMusic.log.data("<light_purple>[AllMusic3]<yellow>等待扫码");
                break;
            case 802:
                AllMusic.log.data("<light_purple>[AllMusic3]<yellow>已扫码，等待确认");
                break;
            case 803:
                AllMusic.log.data("<light_purple>[AllMusic3]<yellow>已确认，正在保存登录状态");
                break;
            case 800:
                AllMusic.log.data("<light_purple>[AllMusic3]<yellow>二维码已过期");
                break;
            default:
                if (!isBlank(message)) {
                    AllMusic.log.data("<light_purple>[AllMusic3]<yellow>扫码状态: code=" + code + " msg=" + message);
                } else {
                    AllMusic.log.data("<light_purple>[AllMusic3]<yellow>扫码状态: code=" + code);
                }
                break;
        }
    }

    private static void printQr(String dataUri) throws Exception {
        String base64 = dataUri;
        int index = base64.indexOf(',');
        if (index >= 0) {
            base64 = base64.substring(index + 1);
        }

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(Base64.getDecoder().decode(base64)));
        if (image == null) {
            throw new IllegalStateException("二维码图片解码失败");
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
            throw new IllegalStateException("二维码图片内容为空");
        }

        int padding = 2;
        AllMusic.log.data(Component.text(""));
        for (int y = minY - padding; y <= maxY + padding; y++) {
            StringBuilder line = new StringBuilder();
            for (int x = minX - padding; x <= maxX + padding; x++) {
                boolean dark = x >= 0 && y >= 0 && x < image.getWidth() && y < image.getHeight()
                        && isDark(image.getRGB(x, y));
                line.append(dark ? "\u2588\u2588" : "  ");
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

    private static JsonElement getPath(JsonElement root, String path) {
        if (root == null || isBlank(path)) {
            return null;
        }

        JsonElement current = root;
        String[] parts = path.split("\\.");
        for (String key : parts) {
            if (current == null || current.isJsonNull() || !current.isJsonObject()) {
                return null;
            }

            JsonObject obj = current.getAsJsonObject();
            if (!obj.has(key)) {
                return null;
            }
            current = obj.get(key);
        }
        return current;
    }

    private static String firstString(JsonObject root, String... paths) {
        for (String path : paths) {
            JsonElement value = getPath(root, path);
            if (value == null || value.isJsonNull()) {
                continue;
            }
            try {
                return value.getAsString();
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private static int firstInt(JsonObject root, int def, String... paths) {
        for (String path : paths) {
            JsonElement value = getPath(root, path);
            if (value == null || value.isJsonNull()) {
                continue;
            }
            try {
                return value.getAsInt();
            } catch (Exception ignored) {
            }
        }
        return def;
    }

    private static int resolveCode(JsonObject root) {
        int rootCode = firstInt(root, -1, "code");
        int dataCode = firstInt(root, -1, "data.code");
        if (rootCode == -1) {
            return dataCode;
        }
        if (dataCode == -1) {
            return rootCode;
        }
        if (rootCode == 200 && dataCode != 200) {
            return dataCode;
        }
        return rootCode;
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

    private static final class QrData {
        private String image;
        private String url;
    }

    private static final class QrStatus {
        private int code;
        private String message;
        private String ticket;
    }
}
