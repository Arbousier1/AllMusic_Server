package com.coloryr.allmusic.server.netapi.meting;

import com.coloryr.allmusic.server.core.AllMusic;
import com.coloryr.allmusic.server.core.IMusicApi;
import com.coloryr.allmusic.server.core.music.LyricSave;
import com.coloryr.allmusic.server.core.objs.SearchMusicObj;
import com.coloryr.allmusic.server.core.objs.music.LyricItemObj;
import com.coloryr.allmusic.server.core.objs.music.SearchPageObj;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public abstract class BaseMetingApi implements IMusicApi {
    private static final Pattern LRC_TIME = Pattern.compile("\\[(\\d+):(\\d+)(?:\\.(\\d{1,3}))?]");
    private static final Pattern NUMBER_ID = Pattern.compile("(\\d+)");
    protected volatile boolean isUpdate;
    private final String id;

    protected BaseMetingApi(String id) {
        this.id = id;
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public boolean isBusy() {
        return isUpdate;
    }

    protected SearchPageObj buildSearchPage(List<SearchMusicObj> items) {
        return items == null || items.isEmpty() ? null
                : new SearchPageObj(items, Math.max(1, (items.size() + 9) / 10), getId());
    }

    protected String joinArgs(String[] args, int start) {
        StringBuilder builder = new StringBuilder();
        for (int i = start; i < args.length; i++) {
            if (isBlank(args[i])) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(args[i]);
        }
        return builder.toString().trim();
    }

    protected LyricSave buildLyric(String lyricText, String translateText) {
        LyricSave lyric = new LyricSave();
        if (isBlank(lyricText)) {
            return lyric;
        }

        Map<Long, String> transMap = parseTranslate(translateText);
        Map<Long, LyricItemObj> lyricMap = parseLyric(lyricText, transMap);
        if (lyricMap.isEmpty()) {
            return lyric;
        }

        lyric.setHaveLyric(AllMusic.getConfig().sendLyric);
        lyric.setLyric(lyricMap);
        return lyric;
    }

    protected Map<Long, String> parseTranslate(String raw) {
        Map<Long, String> map = new LinkedHashMap<Long, String>();
        if (isBlank(raw)) {
            return map;
        }
        String[] lines = raw.replace("\r", "").split("\n");
        for (String line : lines) {
            List<Long> times = parseLineTimes(line);
            if (times.isEmpty()) {
                continue;
            }
            String text = line.replaceAll("\\[[^]]+]", "").trim();
            for (Long time : times) {
                map.put(time, text);
            }
        }
        return map;
    }

    protected Map<Long, LyricItemObj> parseLyric(String raw, Map<Long, String> transMap) {
        Map<Long, LyricItemObj> map = new LinkedHashMap<Long, LyricItemObj>();
        String[] lines = raw.replace("\r", "").split("\n");
        for (String line : lines) {
            List<Long> times = parseLineTimes(line);
            if (times.isEmpty()) {
                continue;
            }
            String text = line.replaceAll("\\[[^]]+]", "").trim();
            for (Long time : times) {
                map.put(time, new LyricItemObj(text, transMap.get(time)));
            }
        }
        return map;
    }

    protected List<Long> parseLineTimes(String line) {
        List<Long> times = new ArrayList<Long>();
        Matcher matcher = LRC_TIME.matcher(line);
        while (matcher.find()) {
            times.add(toTime(matcher));
        }
        return times;
    }

    protected long toTime(Matcher matcher) {
        long minute = Long.parseLong(matcher.group(1));
        long second = Long.parseLong(matcher.group(2));
        String msText = matcher.group(3);
        long ms = 0;
        if (!isBlank(msText)) {
            if (msText.length() == 1) {
                ms = Long.parseLong(msText) * 100;
            } else if (msText.length() == 2) {
                ms = Long.parseLong(msText) * 10;
            } else {
                ms = Long.parseLong(msText);
            }
        }
        return (minute * 60000 + second * 1000 + ms) / 10 * 10;
    }

    protected JsonObject parseObject(String data) {
        try {
            JsonElement element = AllMusic.gson.fromJson(data, JsonElement.class);
            return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
        } catch (Exception e) {
            AllMusic.log.data("<light_purple>[AllMusic3]<red>Meting API response parse failed");
            e.printStackTrace();
            return null;
        }
    }

    protected JsonElement getPath(JsonElement root, String path) {
        if (root == null || isBlank(path)) {
            return null;
        }
        JsonElement current = root;
        String[] parts = path.split("\\.");
        for (String key : parts) {
            if (current == null || current.isJsonNull()) {
                return null;
            }
            if (current.isJsonObject()) {
                JsonObject obj = current.getAsJsonObject();
                if (!obj.has(key)) {
                    return null;
                }
                current = obj.get(key);
                continue;
            }
            if (current.isJsonArray()) {
                try {
                    int index = Integer.parseInt(key);
                    JsonArray array = current.getAsJsonArray();
                    if (index < 0 || index >= array.size()) {
                        return null;
                    }
                    current = array.get(index);
                    continue;
                } catch (Exception ignored) {
                    return null;
                }
            }
            return null;
        }
        return current;
    }

    protected JsonElement first(JsonObject root, String... paths) {
        if (root == null) {
            return null;
        }
        for (String path : paths) {
            JsonElement value = getPath(root, path);
            if (value != null && !value.isJsonNull()) {
                return value;
            }
        }
        return null;
    }

    protected JsonObject firstObject(JsonObject root, String... paths) {
        JsonElement value = first(root, paths);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : null;
    }

    protected JsonArray firstArray(JsonObject root, String... paths) {
        JsonElement value = first(root, paths);
        return value != null && value.isJsonArray() ? value.getAsJsonArray() : null;
    }

    protected String firstString(JsonObject root, String... paths) {
        JsonElement value = first(root, paths);
        if (value == null || value.isJsonNull()) {
            return null;
        }
        try {
            return value.getAsString();
        } catch (Exception ignored) {
            return null;
        }
    }

    protected long firstLong(JsonObject root, long def, String... paths) {
        JsonElement value = first(root, paths);
        if (value == null || value.isJsonNull()) {
            return def;
        }
        try {
            return value.getAsLong();
        } catch (Exception ignored) {
            return def;
        }
    }

    protected int resolveBitrateKbps() {
        String value = AllMusic.getConfig().musicBR;
        if (isBlank(value)) {
            return 320;
        }
        value = value.trim().toLowerCase(Locale.ROOT);
        if ("999000".equals(value) || "lossless".equals(value) || "flac".equals(value)) {
            return 999;
        }
        try {
            int br = Integer.parseInt(value);
            return br >= 1000 ? br / 1000 : br;
        } catch (Exception ignored) {
            return 320;
        }
    }

    protected String normalizeNumericId(String arg) {
        if (arg == null) {
            return null;
        }
        Matcher matcher = NUMBER_ID.matcher(arg);
        return matcher.find() ? matcher.group(1) : arg.trim();
    }

    protected String defaultIfBlank(String value, String def) {
        return isBlank(value) ? def : value;
    }

    protected long secondsToMillis(long value) {
        return value <= 0 ? 0 : value * 1000L;
    }

    protected long guessDurationMillis(long value) {
        if (value <= 0) {
            return 0;
        }
        return value >= 1000 ? value : value * 1000L;
    }

    protected String replaceSizeToken(String url, String size) {
        if (isBlank(url)) {
            return null;
        }
        return url.replace("{size}", size);
    }

    protected boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    protected String md5(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            byte[] data = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder builder = new StringBuilder();
            for (byte item : data) {
                String text = Integer.toHexString(item & 0xff);
                if (text.length() == 1) {
                    builder.append('0');
                }
                builder.append(text);
            }
            return builder.toString();
        } catch (Exception ignored) {
            return "";
        }
    }

    protected String aesCbcBase64(String data, String key, String iv) {
        try {
            Cipher cipher = Cipher.getInstance("AES/CBC/PKCS5Padding");
            SecretKeySpec keySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "AES");
            IvParameterSpec ivSpec = new IvParameterSpec(iv.getBytes(StandardCharsets.UTF_8));
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, ivSpec);
            return Base64.getEncoder().encodeToString(cipher.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ignored) {
            return "";
        }
    }
}
