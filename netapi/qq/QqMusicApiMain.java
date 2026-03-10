package com.coloryr.allmusic.server.netapi.qq;

import com.coloryr.allmusic.server.core.AllMusic;
import com.coloryr.allmusic.server.core.IMusicApi;
import com.coloryr.allmusic.server.core.music.LyricSave;
import com.coloryr.allmusic.server.core.music.MusicHttpClient;
import com.coloryr.allmusic.server.core.objs.HttpResObj;
import com.coloryr.allmusic.server.core.objs.SearchMusicObj;
import com.coloryr.allmusic.server.core.objs.message.ARG;
import com.coloryr.allmusic.server.core.objs.music.LyricItemObj;
import com.coloryr.allmusic.server.core.objs.music.SearchPageObj;
import com.coloryr.allmusic.server.core.objs.music.SongInfoObj;
import com.coloryr.allmusic.server.core.sql.DataSql;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class QqMusicApiMain implements IMusicApi {
    private static final String QQ_HOST = "y.qq.com";
    private static final String SEARCH_URL = "https://c.y.qq.com/soso/fcgi-bin/client_search_cp";
    private static final String DETAIL_URL = "https://c.y.qq.com/v8/fcg-bin/fcg_play_single_song.fcg";
    private static final String PLAYLIST_URL = "https://c.y.qq.com/qzone/fcg-bin/fcg_ucc_getcdinfo_byids_cp.fcg";
    private static final String LYRIC_URL = "https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg";
    private static final String MUSICU_URL = "https://u.y.qq.com/cgi-bin/musicu.fcg";
    private static final String SEARCH_REFERER = "https://y.qq.com/portal/search.html";
    private static final String SONG_REFERER = "https://y.qq.com/n/ryqq/songDetail/";
    private static final String PLAYLIST_REFERER = "https://y.qq.com/n/ryqq/playlist/";
    private static final Pattern URL_ID = Pattern.compile("(?:songDetail|song|playlist|songlist)/([A-Za-z0-9]+)");
    private static final Pattern QUERY_ID = Pattern.compile("(?:songmid|mid|id)=([A-Za-z0-9]+)");
    private static final Pattern JSONP_PATTERN = Pattern.compile("^[^(]+\\((.*)\\)\\s*;?\\s*$", Pattern.DOTALL);
    private static final Pattern LRC_TIME = Pattern.compile("\\[(\\d+):(\\d+)(?:\\.(\\d{1,3}))?]");
    private static final Random RANDOM = new Random();

    private volatile boolean isUpdate;

    public QqMusicApiMain() {
        AllMusic.log.data("<light_purple>[AllMusic3]<yellow>Initializing QQ Music API");
        HttpResObj res = QqApiHttpClient.get(SEARCH_URL, makeSearchParams("test", 1, 1), SEARCH_REFERER);
        if (res == null || !res.ok) {
            AllMusic.log.data("<light_purple>[AllMusic3]<red>QQ Music API initialization failed");
        }
    }

    @Override
    public String getId() {
        return "qq";
    }

    @Override
    public SongInfoObj getMusic(String id, String player, boolean isList) {
        JsonObject song = requestSongDetail(id);
        if (song == null) {
            return null;
        }

        String songId = firstString(song, "songmid", "mid");
        if (isBlank(songId)) {
            songId = id;
        }

        String name = firstString(song, "songname", "name");
        if (isBlank(name)) {
            name = songId;
        }

        String author = buildArtists(song.get("singer"));
        String album = firstString(song, "albumname");
        String alia = firstString(song, "subtitle");
        long length = firstLong(song, 0, "interval");
        if (length > 0) {
            length *= 1000;
        }

        String picUrl = buildAlbumPic(firstString(song, "albummid"));
        return new SongInfoObj(author, name, songId, alia, player, album, isList, length, picUrl,
                false, null, getId());
    }

    @Override
    public SearchPageObj search(String[] args, boolean isDefault) {
        String keyword = joinArgs(args, isDefault ? 0 : 1);
        if (isBlank(keyword)) {
            return null;
        }

        JsonObject root = requestJson(SEARCH_URL, makeSearchParams(keyword, 1, 30), SEARCH_REFERER);
        logApiError("search", root, false);
        JsonArray list = firstArray(root, "data.song.list");
        if (list == null || list.size() == 0) {
            return null;
        }

        List<SearchMusicObj> items = new ArrayList<SearchMusicObj>();
        for (JsonElement item : list) {
            if (!item.isJsonObject()) {
                continue;
            }

            JsonObject song = item.getAsJsonObject();
            String songId = firstString(song, "songmid", "mid");
            String name = firstString(song, "songname", "name");
            if (isBlank(songId) || isBlank(name)) {
                continue;
            }

            String author = buildArtists(song.get("singer"));
            String album = firstString(song, "albumname");
            items.add(new SearchMusicObj(songId, name, author, album == null ? "" : album));
        }

        return items.isEmpty() ? null : new SearchPageObj(items, Math.max(1, (items.size() + 9) / 10), getId());
    }

    @Override
    public void setList(final String id, final Object sender) {
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    isUpdate = true;
                    JsonObject root = requestJson(PLAYLIST_URL, makePlaylistParams(id), PLAYLIST_REFERER + id);
                    logApiError("playlist", root, false);
                    JsonArray list = firstArray(root, "cdlist.0.songlist");
                    if (list == null || list.size() == 0) {
                        return;
                    }

                    List<String> ids = new ArrayList<String>();
                    for (JsonElement item : list) {
                        if (!item.isJsonObject()) {
                            continue;
                        }
                        String songId = firstString(item.getAsJsonObject(), "songmid");
                        if (!isBlank(songId)) {
                            ids.add(songId);
                        }
                    }

                    if (ids.isEmpty()) {
                        return;
                    }

                    DataSql.addIdleList(ids, getId());
                    String name = firstString(root, "cdlist.0.dissname");
                    if (isBlank(name)) {
                        name = id;
                    }
                    AllMusic.side.sendMessageTask(sender,
                            AllMusic.getMessage().musicPlay.listMusic.get.replace(ARG.name, name));
                } finally {
                    isUpdate = false;
                }
            }
        }, "AllMusic_setQQList");
        thread.start();
    }

    @Override
    public LyricSave getLyric(String id) {
        LyricSave lyric = new LyricSave();
        JsonObject root = requestJson(LYRIC_URL, makeLyricParams(id), SONG_REFERER + id);
        if (root == null) {
            return lyric;
        }
        if (!isSuccess(root)) {
            logApiError("lyric", root, true);
            return lyric;
        }

        String rawLyric = decodeLyric(firstString(root, "lyric"));
        if (isBlank(rawLyric)) {
            return lyric;
        }

        String rawTrans = decodeLyric(firstString(root, "trans"));
        Map<Long, String> transMap = parseTranslate(rawTrans);
        Map<Long, LyricItemObj> lyricMap = parseLyric(rawLyric, transMap);
        if (lyricMap.isEmpty()) {
            return lyric;
        }

        lyric.setHaveLyric(AllMusic.getConfig().sendLyric);
        lyric.setLyric(lyricMap);
        return lyric;
    }

    @Override
    public String getPlayUrl(String id) {
        JsonObject song = requestSongDetail(id);
        if (song == null) {
            return null;
        }

        JsonObject file = firstObject(song, "file");
        String mediaMid = firstString(file, "media_mid");
        if (isBlank(mediaMid)) {
            return null;
        }

        FileInfo info = chooseFile(file, mediaMid);
        JsonObject root = requestJson(MUSICU_URL, makePlayParams(id, info.fileName), SONG_REFERER + id);
        if (root == null) {
            return null;
        }
        if (!isSuccessPath(root, "req_0")) {
            logApiError("playUrl", firstObject(root, "req_0.data", "req_0"), true);
        }

        String sip = firstString(root, "req_0.data.sip.0", "req_1.data.sip.0");
        String purl = firstString(root, "req_0.data.midurlinfo.0.purl", "req_1.data.midurlinfo.0.purl");
        if (isBlank(purl)) {
            return null;
        }
        if (isBlank(sip)) {
            sip = "https://dl.stream.qqmusic.qq.com/";
        }
        return sip + purl;
    }

    @Override
    public boolean isBusy() {
        return isUpdate;
    }

    @Override
    public String getMusicId(String arg) {
        if (arg == null) {
            return null;
        }

        String value = arg.trim();
        Matcher queryMatcher = QUERY_ID.matcher(value);
        if (queryMatcher.find()) {
            return queryMatcher.group(1);
        }

        Matcher urlMatcher = URL_ID.matcher(value);
        if (urlMatcher.find()) {
            return urlMatcher.group(1);
        }

        return value;
    }

    @Override
    public boolean checkId(String id) {
        return !isBlank(id) && id.matches("^[A-Za-z0-9]+$");
    }

    private JsonObject requestSongDetail(String id) {
        JsonObject root = requestJson(DETAIL_URL, makeDetailParams(id), SONG_REFERER + id);
        logApiError("detail", root, false);
        JsonArray data = firstArray(root, "data");
        if (data == null || data.size() == 0 || !data.get(0).isJsonObject()) {
            return null;
        }
        return data.get(0).getAsJsonObject();
    }

    private JsonObject requestJson(String url, Map<String, String> query, String referer) {
        HttpResObj res = QqApiHttpClient.get(url, query, referer);
        if (res == null || !res.ok || isBlank(res.data)) {
            return null;
        }

        try {
            String data = unwrapJsonp(res.data);
            JsonElement element = new JsonParser().parse(data);
            return element.isJsonObject() ? element.getAsJsonObject() : null;
        } catch (Exception e) {
            AllMusic.log.data("<light_purple>[AllMusic3]<red>QQ Music API response parse failed");
            e.printStackTrace();
            return null;
        }
    }

    private LinkedHashMap<String, String> makeSearchParams(String keyword, int page, int limit) {
        LinkedHashMap<String, String> query = new LinkedHashMap<String, String>();
        String uin = getQqUin();
        String gtk = getGtk();
        query.put("ct", "24");
        query.put("qqmusic_ver", "1298");
        query.put("new_json", "1");
        query.put("remoteplace", "txt.yqq.song");
        query.put("searchid", randomSearchId());
        query.put("t", "0");
        query.put("aggr", "1");
        query.put("cr", "1");
        query.put("catZhida", "1");
        query.put("lossless", "0");
        query.put("flag_qc", "0");
        query.put("p", String.valueOf(page));
        query.put("n", String.valueOf(limit));
        query.put("w", keyword);
        query.put("g_tk", gtk);
        query.put("g_tk_new_20200303", gtk);
        query.put("loginUin", uin);
        query.put("hostUin", uin);
        query.put("format", "json");
        query.put("inCharset", "utf8");
        query.put("outCharset", "utf-8");
        query.put("notice", "0");
        query.put("platform", "yqq");
        query.put("needNewCode", "0");
        return query;
    }

    private LinkedHashMap<String, String> makeDetailParams(String id) {
        LinkedHashMap<String, String> query = new LinkedHashMap<String, String>();
        query.put("songmid", id);
        query.put("tpl", "yqq_song_detail");
        query.put("format", "json");
        return query;
    }

    private LinkedHashMap<String, String> makePlaylistParams(String id) {
        LinkedHashMap<String, String> query = new LinkedHashMap<String, String>();
        String uin = getQqUin();
        String gtk = getGtk();
        query.put("type", "1");
        query.put("json", "1");
        query.put("utf8", "1");
        query.put("onlysong", "0");
        query.put("new_format", "1");
        query.put("disstid", id);
        query.put("format", "json");
        query.put("g_tk", gtk);
        query.put("g_tk_new_20200303", gtk);
        query.put("loginUin", uin);
        query.put("hostUin", uin);
        query.put("inCharset", "utf8");
        query.put("outCharset", "utf-8");
        query.put("notice", "0");
        query.put("platform", "yqq.json");
        query.put("needNewCode", "0");
        return query;
    }

    private LinkedHashMap<String, String> makeLyricParams(String id) {
        LinkedHashMap<String, String> query = new LinkedHashMap<String, String>();
        String uin = getQqUin();
        String gtk = getGtk();
        query.put("songmid", id);
        query.put("format", "json");
        query.put("nobase64", "0");
        query.put("g_tk", gtk);
        query.put("g_tk_new_20200303", gtk);
        query.put("loginUin", uin);
        query.put("hostUin", uin);
        query.put("inCharset", "utf8");
        query.put("outCharset", "utf-8");
        query.put("notice", "0");
        query.put("platform", "yqq.json");
        query.put("needNewCode", "0");
        query.put("pcachetime", String.valueOf(System.currentTimeMillis()));
        return query;
    }

    private LinkedHashMap<String, String> makePlayParams(String songMid, String fileName) {
        LinkedHashMap<String, String> query = new LinkedHashMap<String, String>();
        query.put("format", "json");
        query.put("data", buildPlayRequestData(songMid, fileName));
        return query;
    }

    private String buildPlayRequestData(String songMid, String fileName) {
        String guid = randomGuid();
        String uin = getQqUin();
        JsonObject root = new JsonObject();

        JsonObject req = new JsonObject();
        req.addProperty("module", "vkey.GetVkeyServer");
        req.addProperty("method", "CgiGetVkey");
        JsonObject param = new JsonObject();
        JsonArray songMidArray = new JsonArray();
        JsonArray songTypeArray = new JsonArray();
        JsonArray fileNameArray = new JsonArray();
        songMidArray.add(songMid);
        songTypeArray.add(0);
        fileNameArray.add(fileName);
        param.add("songmid", songMidArray);
        param.add("songtype", songTypeArray);
        param.add("filename", fileNameArray);
        param.addProperty("guid", guid);
        param.addProperty("uin", uin);
        param.addProperty("loginflag", "0".equals(uin) ? 0 : 1);
        param.addProperty("platform", "20");
        req.add("param", param);
        root.add("req_0", req);

        JsonObject comm = new JsonObject();
        comm.addProperty("uin", uin);
        comm.addProperty("format", "json");
        comm.addProperty("ct", 24);
        comm.addProperty("cv", 0);
        root.add("comm", comm);
        return AllMusic.gson.toJson(root);
    }

    private FileInfo chooseFile(JsonObject file, String mediaMid) {
        List<FileInfo> files = new ArrayList<FileInfo>();
        if (firstLong(file, 0, "size_flac") > 0) {
            files.add(new FileInfo("F000", ".flac", "flac"));
        }
        if (firstLong(file, 0, "size_320mp3") > 0) {
            files.add(new FileInfo("M800", ".mp3", "320"));
        }
        if (firstLong(file, 0, "size_128mp3") > 0) {
            files.add(new FileInfo("M500", ".mp3", "128"));
        }
        if (firstLong(file, 0, "size_96aac") > 0) {
            files.add(new FileInfo("C400", ".m4a", "96"));
        }
        if (firstLong(file, 0, "size_48aac") > 0) {
            files.add(new FileInfo("C200", ".m4a", "48"));
        }

        String want = normalizeBr();
        FileInfo selected = null;
        for (FileInfo item : files) {
            if (want.equals(item.level)) {
                selected = item;
                break;
            }
        }
        if (selected == null && !files.isEmpty()) {
            selected = files.get(0);
        }
        if (selected == null) {
            selected = new FileInfo("M500", ".mp3", "128");
        }
        selected.fileName = selected.prefix + mediaMid + selected.ext;
        return selected;
    }

    private String normalizeBr() {
        String br = AllMusic.getConfig().musicBR;
        if (isBlank(br)) {
            return "320";
        }

        br = br.trim().toLowerCase(Locale.ROOT);
        if ("999000".equals(br) || "flac".equals(br) || "lossless".equals(br)) {
            return "flac";
        }
        if ("320000".equals(br) || "320".equals(br)) {
            return "320";
        }
        if ("128000".equals(br) || "128".equals(br)) {
            return "128";
        }
        return "320";
    }

    private String joinArgs(String[] args, int start) {
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
        return builder.toString();
    }

    private String buildArtists(JsonElement singer) {
        if (singer == null || singer.isJsonNull() || !singer.isJsonArray()) {
            return "";
        }

        List<String> names = new ArrayList<String>();
        for (JsonElement item : singer.getAsJsonArray()) {
            if (!item.isJsonObject()) {
                continue;
            }
            String name = firstString(item.getAsJsonObject(), "name");
            if (!isBlank(name)) {
                names.add(name);
            }
        }

        StringBuilder builder = new StringBuilder();
        for (String name : names) {
            if (builder.length() > 0) {
                builder.append('/');
            }
            builder.append(name);
        }
        return builder.toString();
    }

    private String buildAlbumPic(String albumMid) {
        if (isBlank(albumMid)) {
            return null;
        }
        return "https://y.gtimg.cn/music/photo_new/T002R300x300M000" + albumMid + ".jpg";
    }

    private String decodeLyric(String value) {
        if (isBlank(value)) {
            return null;
        }

        try {
            return new String(Base64.getDecoder().decode(value), StandardCharsets.UTF_8).trim();
        } catch (Exception ignored) {
            return value;
        }
    }

    private Map<Long, String> parseTranslate(String raw) {
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

    private Map<Long, LyricItemObj> parseLyric(String raw, Map<Long, String> transMap) {
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

    private List<Long> parseLineTimes(String line) {
        List<Long> times = new ArrayList<Long>();
        Matcher matcher = LRC_TIME.matcher(line);
        while (matcher.find()) {
            times.add(toTime(matcher));
        }
        return times;
    }

    private long toTime(Matcher matcher) {
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

    private String unwrapJsonp(String text) {
        String value = text == null ? null : text.trim();
        if (isBlank(value)) {
            return value;
        }
        Matcher matcher = JSONP_PATTERN.matcher(value);
        return matcher.matches() ? matcher.group(1) : value;
    }

    private JsonElement getPath(JsonElement root, String path) {
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

    private JsonArray firstArray(JsonObject root, String... paths) {
        JsonElement value = first(root, paths);
        return value != null && value.isJsonArray() ? value.getAsJsonArray() : null;
    }

    private JsonObject firstObject(JsonObject root, String... paths) {
        JsonElement value = first(root, paths);
        return value != null && value.isJsonObject() ? value.getAsJsonObject() : null;
    }

    private JsonElement first(JsonObject root, String... paths) {
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

    private String firstString(JsonObject root, String... paths) {
        JsonElement value = first(root, paths);
        if (value == null || value.isJsonNull() || !value.isJsonPrimitive()) {
            return null;
        }
        try {
            return value.getAsString();
        } catch (Exception ignored) {
            return null;
        }
    }

    private long firstLong(JsonObject root, long def, String... paths) {
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

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private boolean isSuccess(JsonObject root) {
        if (root == null) {
            return false;
        }
        JsonElement code = root.get("code");
        if (code != null && code.isJsonPrimitive()) {
            try {
                return code.getAsInt() == 0;
            } catch (Exception ignored) {
            }
        }
        JsonElement subcode = root.get("subcode");
        if (subcode != null && subcode.isJsonPrimitive()) {
            try {
                return subcode.getAsInt() == 0;
            } catch (Exception ignored) {
            }
        }
        return true;
    }

    private boolean isSuccessPath(JsonObject root, String path) {
        JsonObject obj = firstObject(root, path + ".data", path);
        return isSuccess(obj);
    }

    private void logApiError(String action, JsonObject root, boolean quiet) {
        if (root == null || isSuccess(root)) {
            return;
        }

        String code = firstString(root, "code");
        String subcode = firstString(root, "subcode");
        String message = firstString(root, "message", "msg");
        StringBuilder builder = new StringBuilder("<light_purple>[AllMusic3]<yellow>QQ ");
        builder.append(action).append(" degraded");
        if (!isBlank(code)) {
            builder.append(" code=").append(code);
        }
        if (!isBlank(subcode)) {
            builder.append(" subcode=").append(subcode);
        }
        if (!isBlank(message)) {
            builder.append(" msg=").append(message);
        }
        if (quiet && "1101".equals(code)) {
            builder.append(" auth_required");
        }
        AllMusic.log.data(builder.toString());
    }

    private String getQqUin() {
        String uin = MusicHttpClient.getCookieValue(QQ_HOST, "uin", "wxuin");
        if (isBlank(uin)) {
            return "0";
        }
        String digits = uin.replaceAll("[^0-9]", "");
        return isBlank(digits) ? "0" : digits;
    }

    private String getGtk() {
        String key = MusicHttpClient.getCookieValue(QQ_HOST, "qqmusic_key", "p_skey", "skey");
        if (isBlank(key)) {
            return "5381";
        }

        int hash = 5381;
        for (int i = 0; i < key.length(); i++) {
            hash += (hash << 5) + key.charAt(i);
        }
        return String.valueOf(hash & Integer.MAX_VALUE);
    }

    private String randomGuid() {
        long value = Math.abs(RANDOM.nextLong());
        String text = String.valueOf(value);
        if (text.length() > 10) {
            return text.substring(0, 10);
        }
        while (text.length() < 10) {
            text = "0" + text;
        }
        return text;
    }

    private String randomSearchId() {
        return String.valueOf(Math.abs(RANDOM.nextLong()));
    }

    private static class FileInfo {
        private final String prefix;
        private final String ext;
        private final String level;
        private String fileName;

        private FileInfo(String prefix, String ext, String level) {
            this.prefix = prefix;
            this.ext = ext;
            this.level = level;
        }
    }
}
