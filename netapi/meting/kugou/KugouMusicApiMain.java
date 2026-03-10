package com.coloryr.allmusic.server.netapi.meting.kugou;

import com.coloryr.allmusic.server.core.AllMusic;
import com.coloryr.allmusic.server.core.music.LyricSave;
import com.coloryr.allmusic.server.core.music.MusicHttpClient;
import com.coloryr.allmusic.server.core.objs.HttpResObj;
import com.coloryr.allmusic.server.core.objs.SearchMusicObj;
import com.coloryr.allmusic.server.core.objs.message.ARG;
import com.coloryr.allmusic.server.core.objs.music.SearchPageObj;
import com.coloryr.allmusic.server.core.objs.music.SongInfoObj;
import com.coloryr.allmusic.server.core.sql.DataSql;
import com.coloryr.allmusic.server.netapi.meting.BaseMetingApi;
import com.coloryr.allmusic.server.netapi.meting.MetingHttpClient;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class KugouMusicApiMain extends BaseMetingApi {
    private static final String SEARCH_URL = "http://mobilecdn.kugou.com/api/v3/search/song";
    private static final String SONG_URL = "http://m.kugou.com/app/i/getSongInfo.php";
    private static final String PLAYLIST_URL = "http://mobilecdn.kugou.com/api/v3/special/song";
    private static final String LEGACY_PLAY_URL = "http://media.store.kugou.com/v1/get_res_privilege";
    private static final String TRACK_URL = "http://trackercdn.kugou.com/i/v2/";
    private static final String SONGINFO_URL = "https://wwwapi.kugou.com/play/songinfo";
    private static final String LYRIC_SEARCH_URL = "http://krcs.kugou.com/search";
    private static final String LYRIC_DOWNLOAD_URL = "http://lyrics.kugou.com/download";
    private static final Pattern HASH_PATTERN = Pattern.compile("([A-Fa-f0-9]{32})");

    public KugouMusicApiMain() {
        super("kugou");
    }

    @Override
    public SongInfoObj getMusic(String id, String player, boolean isList) {
        Map<String, String> form = new LinkedHashMap<String, String>();
        form.put("cmd", "playInfo");
        form.put("hash", id);
        form.put("from", "mkugou");
        HttpResObj res = MetingHttpClient.postForm(SONG_URL, form, createHeaders());
        if (res == null || !res.ok) {
            return null;
        }
        JsonObject song = parseObject(res.data);
        if (song == null) {
            return null;
        }
        String[] parsed = parseFilename(firstString(song, "fileName", "filename"));
        String name = defaultIfBlank(firstString(song, "songName"), parsed[1]);
        String author = defaultIfBlank(firstString(song, "author_name"), parsed[0]);
        String album = defaultIfBlank(firstString(song, "album_name"), "");
        long duration = firstLong(song, 0, "timeLength", "timelength");
        String pic = replaceSizeToken(firstString(song, "imgUrl"), "400");
        return new SongInfoObj(author, defaultIfBlank(name, id), id, null, player, album,
                isList, guessDurationMillis(duration), pic, false, null, getId());
    }

    @Override
    public SearchPageObj search(String[] args, boolean isDefault) {
        String keyword = joinArgs(args, isDefault ? 0 : 1);
        if (isBlank(keyword)) {
            return null;
        }
        Map<String, String> query = new LinkedHashMap<String, String>();
        query.put("api_ver", "1");
        query.put("area_code", "1");
        query.put("correct", "1");
        query.put("pagesize", "30");
        query.put("plat", "2");
        query.put("tag", "1");
        query.put("sver", "5");
        query.put("showtype", "10");
        query.put("page", "1");
        query.put("keyword", keyword);
        query.put("version", "8990");
        HttpResObj res = MetingHttpClient.get(SEARCH_URL, query, createHeaders());
        if (res == null || !res.ok) {
            return null;
        }
        JsonObject root = parseObject(res.data);
        JsonArray list = firstArray(root, "data.info");
        if (list == null || list.size() == 0) {
            return null;
        }
        List<SearchMusicObj> items = new ArrayList<SearchMusicObj>();
        for (JsonElement item : list) {
            if (!item.isJsonObject()) {
                continue;
            }
            JsonObject song = item.getAsJsonObject();
            String hash = firstString(song, "hash");
            String[] parsed = parseFilename(firstString(song, "filename", "fileName"));
            String name = defaultIfBlank(firstString(song, "songname", "songName"), parsed[1]);
            if (isBlank(hash) || isBlank(name)) {
                continue;
            }
            String author = defaultIfBlank(firstString(song, "singername", "author_name"), parsed[0]);
            String album = defaultIfBlank(firstString(song, "album_name"), "");
            items.add(new SearchMusicObj(hash, name, author, album, getId()));
        }
        return buildSearchPage(items);
    }

    @Override
    public void setList(final String id, final Object sender) {
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    isUpdate = true;
                    Map<String, String> query = new LinkedHashMap<String, String>();
                    query.put("specialid", id);
                    query.put("area_code", "1");
                    query.put("page", "1");
                    query.put("plat", "2");
                    query.put("pagesize", "-1");
                    query.put("version", "8990");
                    HttpResObj res = MetingHttpClient.get(PLAYLIST_URL, query, createHeaders());
                    if (res == null || !res.ok) {
                        return;
                    }
                    JsonObject root = parseObject(res.data);
                    JsonArray list = firstArray(root, "data.info");
                    if (list == null || list.size() == 0) {
                        return;
                    }
                    List<String> ids = new ArrayList<String>();
                    for (JsonElement item : list) {
                        if (!item.isJsonObject()) {
                            continue;
                        }
                        String hash = firstString(item.getAsJsonObject(), "hash");
                        if (!isBlank(hash)) {
                            ids.add(hash);
                        }
                    }
                    if (ids.isEmpty()) {
                        return;
                    }
                    DataSql.addIdleList(ids, getId());
                    String name = firstString(root, "data.specialname");
                    AllMusic.side.sendMessageTask(sender,
                            AllMusic.getMessage().musicPlay.listMusic.get.replace(ARG.name, defaultIfBlank(name, id)));
                } finally {
                    isUpdate = false;
                }
            }
        }, "AllMusic_setKugouList");
        thread.start();
    }

    @Override
    public LyricSave getLyric(String id) {
        Map<String, String> query = new LinkedHashMap<String, String>();
        query.put("keyword", "%20-%20");
        query.put("ver", "1");
        query.put("hash", id);
        query.put("client", "mobi");
        query.put("man", "yes");
        HttpResObj res = MetingHttpClient.get(LYRIC_SEARCH_URL, query, createHeaders());
        if (res == null || !res.ok) {
            return new LyricSave();
        }
        JsonObject root = parseObject(res.data);
        JsonArray candidates = firstArray(root, "candidates");
        if (candidates == null || candidates.size() == 0 || !candidates.get(0).isJsonObject()) {
            return new LyricSave();
        }
        JsonObject first = candidates.get(0).getAsJsonObject();
        Map<String, String> download = new LinkedHashMap<String, String>();
        download.put("charset", "utf8");
        download.put("accesskey", defaultIfBlank(firstString(first, "accesskey"), ""));
        download.put("id", defaultIfBlank(firstString(first, "id"), ""));
        download.put("client", "mobi");
        download.put("fmt", "lrc");
        download.put("ver", "1");
        HttpResObj detail = MetingHttpClient.get(LYRIC_DOWNLOAD_URL, download, createHeaders());
        if (detail == null || !detail.ok) {
            return new LyricSave();
        }
        JsonObject lyric = parseObject(detail.data);
        String content = firstString(lyric, "content");
        if (isBlank(content)) {
            return new LyricSave();
        }
        try {
            return buildLyric(new String(Base64.getDecoder().decode(content), StandardCharsets.UTF_8), null);
        } catch (Exception ignored) {
            return new LyricSave();
        }
    }

    @Override
    public String getPlayUrl(String id) {
        String cookieUrl = getPlayUrlWithCookie(id);
        if (!isBlank(cookieUrl)) {
            return cookieUrl;
        }
        return getPlayUrlLegacy(id);
    }

    @Override
    public String getMusicId(String arg) {
        if (arg == null) {
            return null;
        }
        Matcher matcher = HASH_PATTERN.matcher(arg);
        return matcher.find() ? matcher.group(1) : arg.trim();
    }

    @Override
    public boolean checkId(String id) {
        return !isBlank(id) && id.matches("^[A-Fa-f0-9]{32}$");
    }

    private String getPlayUrlWithCookie(String id) {
        String token = MusicHttpClient.getCookieValue("wwwapi.kugou.com", "t");
        String userId = MusicHttpClient.getCookieValue("wwwapi.kugou.com", "KugooID");
        if (isBlank(token) || isBlank(userId)) {
            return null;
        }
        JsonObject first = requestSonginfo(buildSonginfoParams(id, false));
        if (first == null) {
            return null;
        }
        JsonObject data = firstObject(first, "data");
        if (data == null) {
            return null;
        }
        String playUrl = firstString(data, "play_url", "play_backup_url");
        if (!isBlank(playUrl)) {
            return playUrl;
        }
        String encodeId = firstString(data, "encode_album_audio_id");
        if (isBlank(encodeId)) {
            return null;
        }
        JsonObject second = requestSonginfo(buildSonginfoParams(encodeId, true));
        JsonObject detail = firstObject(second, "data");
        return detail == null ? null : firstString(detail, "play_url", "play_backup_url");
    }

    private JsonObject requestSonginfo(Map<String, String> params) {
        Map<String, String> query = new LinkedHashMap<String, String>(params);
        query.put("signature", buildSignature(params));
        HttpResObj res = MetingHttpClient.get(SONGINFO_URL, query, createHeaders());
        return res == null || !res.ok ? null : parseObject(res.data);
    }

    private Map<String, String> buildSonginfoParams(String value, boolean encoded) {
        long now = System.currentTimeMillis();
        Map<String, String> params = new LinkedHashMap<String, String>();
        params.put("srcappid", "2919");
        params.put("clientver", "20000");
        params.put("clienttime", String.valueOf(now));
        params.put("mid", defaultIfBlank(MusicHttpClient.getCookieValue("wwwapi.kugou.com", "mid", "kg_mid"), ""));
        params.put("uuid", defaultIfBlank(MusicHttpClient.getCookieValue("wwwapi.kugou.com", "uuid", "mid", "kg_mid"), ""));
        params.put("dfid", defaultIfBlank(MusicHttpClient.getCookieValue("wwwapi.kugou.com", "dfid", "kg_dfid"), ""));
        params.put("appid", "1014");
        params.put("platid", "4");
        if (encoded) {
            params.put("encode_album_audio_id", value);
        } else {
            params.put("hash", value);
        }
        params.put("token", defaultIfBlank(MusicHttpClient.getCookieValue("wwwapi.kugou.com", "t"), ""));
        params.put("userid", defaultIfBlank(MusicHttpClient.getCookieValue("wwwapi.kugou.com", "KugooID"), ""));
        return params;
    }

    private String buildSignature(Map<String, String> params) {
        List<String> list = new ArrayList<String>();
        for (Map.Entry<String, String> item : params.entrySet()) {
            list.add(item.getKey() + "=" + item.getValue());
        }
        list.sort(String::compareTo);
        StringBuilder builder = new StringBuilder("NVPh5oo715z5DIWAeQlhMDsWXXQV4hwt");
        for (String item : list) {
            builder.append(item);
        }
        builder.append("NVPh5oo715z5DIWAeQlhMDsWXXQV4hwt");
        return md5(builder.toString());
    }

    private String getPlayUrlLegacy(String id) {
        JsonObject body = new JsonObject();
        body.addProperty("relate", 1);
        body.addProperty("userid", "0");
        body.addProperty("vip", 0);
        body.addProperty("appid", 1000);
        body.addProperty("token", "");
        body.addProperty("behavior", "download");
        body.addProperty("area_code", "1");
        body.addProperty("clientver", "8990");
        JsonArray resource = new JsonArray();
        JsonObject item = new JsonObject();
        item.addProperty("id", 0);
        item.addProperty("type", "audio");
        item.addProperty("hash", id);
        resource.add(item);
        body.add("resource", resource);

        HttpResObj res = MetingHttpClient.postJson(LEGACY_PLAY_URL, body.toString(), createHeaders());
        if (res == null || !res.ok) {
            return null;
        }
        JsonObject root = parseObject(res.data);
        JsonArray goods = firstArray(root, "data.0.relate_goods");
        if (goods == null || goods.size() == 0) {
            return null;
        }

        int want = resolveBitrateKbps();
        int maxBr = 0;
        String url = null;
        for (JsonElement element : goods) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject song = element.getAsJsonObject();
            int bitrate = (int) firstLong(song, -1, "info.bitrate");
            if (bitrate > want || bitrate <= maxBr) {
                continue;
            }
            String hash = firstString(song, "hash");
            if (isBlank(hash)) {
                continue;
            }
            Map<String, String> query = new LinkedHashMap<String, String>();
            query.put("hash", hash);
            query.put("key", md5(hash + "kgcloudv2"));
            query.put("pid", "3");
            query.put("behavior", "play");
            query.put("cmd", "25");
            query.put("version", "8990");
            HttpResObj detail = MetingHttpClient.get(TRACK_URL, query, createHeaders());
            if (detail == null || !detail.ok) {
                continue;
            }
            JsonObject data = parseObject(detail.data);
            String nextUrl = firstString(data, "url.0", "url");
            if (!isBlank(nextUrl)) {
                maxBr = (int) firstLong(data, bitrate * 1000L, "bitRate") / 1000;
                url = nextUrl;
            }
        }
        return url;
    }

    private Map<String, String> createHeaders() {
        Map<String, String> headers = new LinkedHashMap<String, String>();
        headers.put("User-Agent", "IPhone-8990-searchSong");
        headers.put("UNI-UserAgent", "iOS11.4-Phone8990-1009-0-WiFi");
        return headers;
    }

    private String[] parseFilename(String filename) {
        String[] res = new String[]{"", ""};
        if (isBlank(filename)) {
            return res;
        }
        String[] items = filename.split(" - ", 2);
        if (items.length == 2) {
            res[0] = items[0];
            res[1] = items[1];
        } else {
            res[1] = filename;
        }
        return res;
    }
}
