package com.coloryr.allmusic.server.netapi.meting.kuwo;

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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class KuwoMusicApiMain extends BaseMetingApi {
    private static final String SEARCH_URL = "http://www.kuwo.cn/api/www/search/searchMusicBykeyWord";
    private static final String SONG_URL = "http://www.kuwo.cn/api/www/music/musicInfo";
    private static final String PLAYLIST_URL = "http://www.kuwo.cn/api/www/playlist/playListInfo";
    private static final String PLAY_URL = "http://www.kuwo.cn/api/v1/www/music/playUrl";
    private static final String LYRIC_URL = "http://m.kuwo.cn/newh5/singles/songinfoandlrc";

    public KuwoMusicApiMain() {
        super("kuwo");
    }

    @Override
    public SongInfoObj getMusic(String id, String player, boolean isList) {
        JsonObject song = requestSong(id);
        if (song == null) {
            return null;
        }
        String songId = normalizeRid(firstString(song, "rid", "musicrid"));
        String pic = firstString(song, "pic", "albumpic");
        long duration = firstLong(song, 0, "duration");
        return new SongInfoObj(defaultIfBlank(firstString(song, "artist"), ""),
                defaultIfBlank(firstString(song, "name"), songId), defaultIfBlank(songId, id), null, player,
                defaultIfBlank(firstString(song, "album"), ""), isList, secondsToMillis(duration),
                pic, false, null, getId());
    }

    @Override
    public SearchPageObj search(String[] args, boolean isDefault) {
        String keyword = joinArgs(args, isDefault ? 0 : 1);
        if (isBlank(keyword)) {
            return null;
        }
        Map<String, String> query = new LinkedHashMap<String, String>();
        query.put("key", keyword);
        query.put("pn", "1");
        query.put("rn", "30");
        query.put("httpsStatus", "1");
        HttpResObj res = MetingHttpClient.get(SEARCH_URL, query, createHeaders());
        if (res == null || !res.ok) {
            return null;
        }
        JsonObject root = parseObject(res.data);
        JsonArray list = firstArray(root, "data.list");
        if (list == null || list.size() == 0) {
            return null;
        }
        List<SearchMusicObj> items = new ArrayList<SearchMusicObj>();
        for (JsonElement item : list) {
            if (!item.isJsonObject()) {
                continue;
            }
            JsonObject song = item.getAsJsonObject();
            String rid = normalizeRid(firstString(song, "rid", "musicrid"));
            String name = firstString(song, "name");
            if (isBlank(rid) || isBlank(name)) {
                continue;
            }
            items.add(new SearchMusicObj(rid, name, defaultIfBlank(firstString(song, "artist"), ""),
                    defaultIfBlank(firstString(song, "album"), ""), getId()));
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
                    query.put("pid", id);
                    query.put("pn", "1");
                    query.put("rn", "1000");
                    query.put("httpsStatus", "1");
                    HttpResObj res = MetingHttpClient.get(PLAYLIST_URL, query, createHeaders());
                    if (res == null || !res.ok) {
                        return;
                    }
                    JsonObject root = parseObject(res.data);
                    JsonArray list = firstArray(root, "data.musicList");
                    if (list == null || list.size() == 0) {
                        return;
                    }
                    List<String> ids = new ArrayList<String>();
                    for (JsonElement item : list) {
                        if (!item.isJsonObject()) {
                            continue;
                        }
                        String rid = normalizeRid(firstString(item.getAsJsonObject(), "rid", "musicrid"));
                        if (!isBlank(rid)) {
                            ids.add(rid);
                        }
                    }
                    if (ids.isEmpty()) {
                        return;
                    }
                    DataSql.addIdleList(ids, getId());
                    String name = firstString(root, "data.name");
                    AllMusic.side.sendMessageTask(sender,
                            AllMusic.getMessage().musicPlay.listMusic.get.replace(ARG.name, defaultIfBlank(name, id)));
                } finally {
                    isUpdate = false;
                }
            }
        }, "AllMusic_setKuwoList");
        thread.start();
    }

    @Override
    public LyricSave getLyric(String id) {
        Map<String, String> query = new LinkedHashMap<String, String>();
        query.put("musicId", id);
        query.put("httpsStatus", "1");
        HttpResObj res = MetingHttpClient.get(LYRIC_URL, query, createHeaders());
        if (res == null || !res.ok) {
            return new LyricSave();
        }
        JsonObject root = parseObject(res.data);
        JsonArray list = firstArray(root, "data.lrclist");
        if (list == null || list.size() == 0) {
            return new LyricSave();
        }
        StringBuilder builder = new StringBuilder();
        for (JsonElement item : list) {
            if (!item.isJsonObject()) {
                continue;
            }
            JsonObject lyric = item.getAsJsonObject();
            String text = firstString(lyric, "lineLyric");
            if (isBlank(text)) {
                continue;
            }
            double time = 0;
            try {
                time = Double.parseDouble(defaultIfBlank(firstString(lyric, "time"), "0"));
            } catch (Exception ignored) {
            }
            int minute = (int) (time / 60);
            int second = (int) (time % 60);
            int ms = (int) Math.round((time - Math.floor(time)) * 100);
            builder.append('[');
            appendTwo(builder, minute);
            builder.append(':');
            appendTwo(builder, second);
            builder.append('.');
            appendTwo(builder, ms);
            builder.append(']').append(text).append('\n');
        }
        return buildLyric(builder.toString(), null);
    }

    @Override
    public String getPlayUrl(String id) {
        Map<String, String> query = new LinkedHashMap<String, String>();
        query.put("mid", id);
        query.put("type", "music");
        query.put("httpsStatus", "1");
        HttpResObj res = MetingHttpClient.get(PLAY_URL, query, createHeaders());
        if (res == null || !res.ok) {
            return null;
        }
        JsonObject root = parseObject(res.data);
        return firstString(root, "data.url");
    }

    @Override
    public String getMusicId(String arg) {
        return normalizeRid(normalizeNumericId(arg));
    }

    @Override
    public boolean checkId(String id) {
        return !isBlank(id) && id.matches("^\\d+$");
    }

    private JsonObject requestSong(String id) {
        Map<String, String> query = new LinkedHashMap<String, String>();
        query.put("mid", id);
        query.put("httpsStatus", "1");
        HttpResObj res = MetingHttpClient.get(SONG_URL, query, createHeaders());
        if (res == null || !res.ok) {
            return null;
        }
        return firstObject(parseObject(res.data), "data");
    }

    private Map<String, String> createHeaders() {
        Map<String, String> headers = new LinkedHashMap<String, String>();
        headers.put("Cookie", "Hm_lvt_cdb524f42f0ce19b169a8071123a4797=1623339177; kw_token=3E7JFQ7MRPL");
        headers.put("csrf", "3E7JFQ7MRPL");
        headers.put("Host", "www.kuwo.cn");
        headers.put("Referer", "http://www.kuwo.cn/");
        headers.put("User-Agent", "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.77 Safari/537.36");
        return headers;
    }

    private String normalizeRid(String value) {
        if (isBlank(value)) {
            return value;
        }
        int index = value.lastIndexOf('_');
        if (index >= 0 && index + 1 < value.length()) {
            return value.substring(index + 1);
        }
        return value;
    }

    private void appendTwo(StringBuilder builder, int value) {
        if (value < 10) {
            builder.append('0');
        }
        builder.append(value);
    }
}
