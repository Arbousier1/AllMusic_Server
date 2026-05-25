package com.coloryr.allmusic.server.netapi.meting.baidu;

import com.coloryr.allmusic.server.core.AllMusic;
import com.coloryr.allmusic.server.core.music.LyricSave;
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

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class BaiduMusicApiMain extends BaseMetingApi {
    private static final String API_URL = "http://musicapi.taihe.com/v1/restserver/ting";
    private static final String AES_KEY = "DBEECF8C50FD160E";
    private static final String AES_IV = "1231021386755796";
    private final String baiduId = randomHex(32);

    public BaiduMusicApiMain() {
        super("baidu");
    }

    @Override
    public SongInfoObj getMusic(String id, String player, boolean isList) {
        JsonObject root = requestSong(id);
        JsonObject song = firstObject(root, "songinfo");
        if (song == null) {
            return null;
        }
        long duration = firstLong(song, 0, "file_duration");
        String pic = firstString(song, "pic_radio", "pic_small");
        return new SongInfoObj(defaultIfBlank(firstString(song, "author"), ""),
                defaultIfBlank(firstString(song, "title"), id), defaultIfBlank(firstString(song, "song_id"), id),
                null, player, defaultIfBlank(firstString(song, "album_title"), ""), isList,
                secondsToMillis(duration), pic, false, null, getId());
    }

    @Override
    public SearchPageObj search(String[] args, boolean isDefault) {
        String keyword = joinArgs(args, isDefault ? 0 : 1);
        if (isBlank(keyword)) {
            return null;
        }
        Map<String, String> query = baseQuery("baidu.ting.search.merge");
        query.put("isNew", "1");
        query.put("platform", "darwin");
        query.put("page_no", "1");
        query.put("query", keyword);
        query.put("version", "11.2.1");
        query.put("page_size", "30");
        HttpResObj res = MetingHttpClient.get(API_URL, query, createHeaders());
        if (res == null || !res.ok) {
            return null;
        }
        JsonObject root = parseObject(res.data);
        JsonArray list = firstArray(root, "result.song_info.song_list");
        if (list == null || list.size() == 0) {
            return null;
        }
        List<SearchMusicObj> items = new ArrayList<SearchMusicObj>();
        for (JsonElement item : list) {
            if (!item.isJsonObject()) {
                continue;
            }
            JsonObject song = item.getAsJsonObject();
            String songId = firstString(song, "song_id");
            String name = firstString(song, "title");
            if (isBlank(songId) || isBlank(name)) {
                continue;
            }
            items.add(new SearchMusicObj(songId, name, defaultIfBlank(firstString(song, "author"), ""),
                    defaultIfBlank(firstString(song, "album_title"), ""), getId()));
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
                    Map<String, String> query = baseQuery("baidu.ting.diy.gedanInfo");
                    query.put("listid", id);
                    query.put("platform", "darwin");
                    query.put("version", "11.2.1");
                    HttpResObj res = MetingHttpClient.get(API_URL, query, createHeaders());
                    if (res == null || !res.ok) {
                        return;
                    }
                    JsonObject root = parseObject(res.data);
                    JsonArray list = firstArray(root, "content");
                    if (list == null || list.size() == 0) {
                        return;
                    }
                    List<String> ids = new ArrayList<String>();
                    for (JsonElement item : list) {
                        if (!item.isJsonObject()) {
                            continue;
                        }
                        String songId = firstString(item.getAsJsonObject(), "song_id");
                        if (!isBlank(songId)) {
                            ids.add(songId);
                        }
                    }
                    if (ids.isEmpty()) {
                        return;
                    }
                    DataSql.addIdleList(ids, getId());
                    String name = firstString(root, "title", "listTitle");
                    AllMusic.side.sendMessageTask(sender,
                            AllMusic.getMessage().musicPlay.listMusic.get.replace(ARG.name, defaultIfBlank(name, id)));
                } finally {
                    isUpdate = false;
                }
            }
        }, "AllMusic_setBaiduList");
        thread.start();
    }

    @Override
    public LyricSave getLyric(String id) {
        Map<String, String> query = baseQuery("baidu.ting.song.lry");
        query.put("songid", id);
        query.put("platform", "darwin");
        query.put("version", "1.0.0");
        HttpResObj res = MetingHttpClient.get(API_URL, query, createHeaders());
        if (res == null || !res.ok) {
            return new LyricSave();
        }
        JsonObject root = parseObject(res.data);
        return buildLyric(firstString(root, "lrcContent"), null);
    }

    @Override
    public String getPlayUrl(String id) {
        JsonObject root = requestSong(id);
        JsonArray urls = firstArray(root, "songurl.url");
        if (urls == null || urls.size() == 0) {
            return null;
        }
        int want = resolveBitrateKbps();
        int maxBr = 0;
        String url = null;
        for (JsonElement item : urls) {
            if (!item.isJsonObject()) {
                continue;
            }
            JsonObject obj = item.getAsJsonObject();
            int bitrate = (int) firstLong(obj, -1, "file_bitrate");
            if (bitrate <= want && bitrate > maxBr) {
                String link = firstString(obj, "file_link");
                if (!isBlank(link)) {
                    maxBr = bitrate;
                    url = link;
                }
            }
        }
        if (url != null) {
            return url;
        }
        for (JsonElement item : urls) {
            if (!item.isJsonObject()) {
                continue;
            }
            String link = firstString(item.getAsJsonObject(), "file_link");
            if (!isBlank(link)) {
                return link;
            }
        }
        return null;
    }

    @Override
    public String getMusicId(String arg) {
        return normalizeNumericId(arg);
    }

    @Override
    public boolean checkId(String id) {
        return !isBlank(id) && id.matches("^\\d+$");
    }

    private JsonObject requestSong(String id) {
        Map<String, String> query = baseQuery("baidu.ting.song.getInfos");
        query.put("songid", id);
        query.put("res", "1");
        query.put("platform", "darwin");
        query.put("version", "1.0.0");
        query.put("e", aesCbcBase64("songid=" + id + "&ts=" + System.currentTimeMillis(), AES_KEY, AES_IV));
        HttpResObj res = MetingHttpClient.get(API_URL, query, createHeaders());
        return res == null || !res.ok ? null : parseObject(res.data);
    }

    private Map<String, String> baseQuery(String method) {
        Map<String, String> query = new LinkedHashMap<String, String>();
        query.put("from", "qianqianmini");
        query.put("method", method);
        return query;
    }

    private Map<String, String> createHeaders() {
        Map<String, String> headers = new LinkedHashMap<String, String>();
        headers.put("Cookie", "BAIDUID=" + baiduId + ":FG=1");
        headers.put("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_13_6) AppleWebKit/537.36 (KHTML, like Gecko) baidu-music/1.2.1 Chrome/66.0.3359.181 Electron/3.0.5 Safari/537.36");
        headers.put("Accept", "*/*");
        headers.put("Content-Type", "application/json;charset=UTF-8");
        headers.put("Accept-Language", "zh-CN");
        return headers;
    }

    private String randomHex(int length) {
        byte[] data = new byte[(length + 1) / 2];
        new SecureRandom().nextBytes(data);
        StringBuilder builder = new StringBuilder();
        for (byte item : data) {
            String text = Integer.toHexString(item & 0xff);
            if (text.length() == 1) {
                builder.append('0');
            }
            builder.append(text);
        }
        return builder.length() > length ? builder.substring(0, length) : builder.toString();
    }
}
