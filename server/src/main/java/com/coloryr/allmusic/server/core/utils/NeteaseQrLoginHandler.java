package com.coloryr.allmusic.server.core.utils;

import com.coloryr.allmusic.server.core.AllMusic;
import com.coloryr.allmusic.server.core.objs.HttpResObj;
import com.coloryr.allmusic.server.netapi.EncryptType;
import com.coloryr.allmusic.server.netapi.NetApiHttpClient;
import com.google.gson.JsonObject;

public final class NeteaseQrLoginHandler {

    private static final String UNIKEY_URL = "https://music.163.com/weapi/login/qrcode/unikey";
    private static final String CHECK_URL = "https://music.163.com/weapi/login/qrcode/client/login";
    private static final String QR_PREFIX = "https://music.163.com/login?codekey=";
    private static final int QR_EXPIRED = 800;
    private static final int QR_WAITING = 801;
    private static final int QR_SCANNED = 802;
    private static final int QR_CONFIRMED = 803;

    private NeteaseQrLoginHandler() {
    }

    public static Result requestUnikey() {
        JsonObject params = new JsonObject();
        params.addProperty("type", 1);
        HttpResObj res = NetApiHttpClient.post(UNIKEY_URL, params, EncryptType.WEAPI, null);
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
        HttpResObj res = NetApiHttpClient.post(CHECK_URL, params, EncryptType.WEAPI, null);
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
}
