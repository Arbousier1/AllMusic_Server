package com.coloryr.allmusic.server.core.music;

import com.coloryr.allmusic.server.core.AllMusic;
import com.coloryr.allmusic.server.core.IMusicApi;
import com.coloryr.allmusic.server.core.objs.SearchMusicObj;
import com.coloryr.allmusic.server.core.objs.message.ARG;
import com.coloryr.allmusic.server.core.objs.music.PlayerAddMusicObj;
import com.coloryr.allmusic.server.core.objs.music.SearchPageObj;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class MusicSearch {
    private static final Queue<PlayerAddMusicObj> tasks = new ConcurrentLinkedQueue<>();
    private static boolean isRun;

    private static void task() {
        while (isRun) {
            try {
                PlayerAddMusicObj obj = tasks.poll();
                if (obj != null) {
                    SearchPageObj search;
                    if ("all".equalsIgnoreCase(obj.api)) {
                        search = searchAll(obj);
                    } else {
                        IMusicApi api = AllMusic.getMusicApi(obj.api);
                        if (api == null) {
                            AllMusic.side.sendMessageTask(obj.sender, AllMusic.getUnknownApiMessage());
                            continue;
                        }
                        search = api.search(obj.args, obj.isDefault);
                    }
                    if (search == null)
                        AllMusic.side.sendMessageTask(obj.sender, AllMusic.getMessage().search
                                .cantSearch.replace(ARG.name, obj.isDefault ? obj.args[0] : obj.args[1]));
                    else {
                        AllMusic.side.sendMessageTask(obj.sender, AllMusic.getMessage().search.res);
                        AllMusic.addSearch(obj.name, search);
                        AllMusic.side.runTask(() -> showSearch(obj.sender, search));
                    }
                }
                Thread.sleep(100);
            } catch (Exception e) {
                AllMusic.log.data("搜歌出现问题");
                e.printStackTrace();
            }
        }
    }

    public static void start() {
        Thread taskT = new Thread(MusicSearch::task, "AllMusic_search");
        isRun = true;
        taskT.start();
    }

    public static void stop() {
        isRun = false;
    }

    public static void addSearch(PlayerAddMusicObj obj) {
        tasks.add(obj);
    }

    private static SearchPageObj searchAll(PlayerAddMusicObj obj) {
        List<SearchMusicObj> netease = searchByApi("netapi", obj);
        List<SearchMusicObj> qq = searchByApi("qq", obj);
        List<SearchMusicObj> res = new ArrayList<>();

        int max = Math.max(netease.size(), qq.size());
        for (int i = 0; i < max; i++) {
            if (i < netease.size()) {
                res.add(netease.get(i));
            }
            if (i < qq.size()) {
                res.add(qq.get(i));
            }
        }

        if (res.isEmpty()) {
            return null;
        }
        return new SearchPageObj(res, Math.max(1, (res.size() + 9) / 10), "all");
    }

    private static List<SearchMusicObj> searchByApi(String apiName, PlayerAddMusicObj obj) {
        IMusicApi api = AllMusic.getMusicApi(apiName);
        if (api == null) {
            return new ArrayList<>();
        }

        SearchPageObj page = api.search(obj.args, obj.isDefault);
        if (page == null) {
            return new ArrayList<>();
        }

        List<SearchMusicObj> list = new ArrayList<>();
        int limit = Math.min(10, page.getIndex() + page.getPage() * 10);
        for (int i = 0; i < limit; i++) {
            SearchMusicObj item = page.getRes(i);
            list.add(new SearchMusicObj(item.id, item.name, item.author, item.al, api.getId()));
        }
        return list;
    }

    /**
     * 展示搜歌结果
     *
     * @param sender 发送者
     * @param search 搜歌结果
     */
    public static void showSearch(Object sender, SearchPageObj search) {
        int index = search.getIndex();
        SearchMusicObj item;
        String info;
        AllMusic.side.sendMessage(sender, "");
        if (search.haveLastPage()) {
            AllMusic.side.sendMessage(sender, AllMusic.miniMessage(AllMusic.getMessage().search.lastPage)
                    .append(AllMusic.miniMessageRun(AllMusic.getMessage().page.last, "/music lastpage")));
        }
        for (int a = 0; a < index; a++) {
            item = search.getRes(a + search.getPage() * 10);
            info = AllMusic.getMessage().page.choice;
            info = info.replace(ARG.index, "" + (a + 1))
                    .replace(ARG.musicName, formatName(item))
                    .replace(ARG.musicAuthor, item.author)
                    .replace(ARG.musicAl, item.al);
            AllMusic.side.sendMessage(sender, AllMusic.miniMessage(info)
                    .append(AllMusic.miniMessageRun(AllMusic.getMessage().click.clickRun, "/music select " + (a + 1))));
        }
        if (search.haveNextPage()) {
            AllMusic.side.sendMessage(sender, AllMusic.miniMessage(AllMusic.getMessage().search.nextPage)
                    .append(AllMusic.miniMessageRun(AllMusic.getMessage().page.next, "/music nextpage")));
        }
        AllMusic.side.sendMessage(sender, "");
    }

    private static String formatName(SearchMusicObj item) {
        if (item == null || item.api == null || item.api.isEmpty()) {
            return item == null ? "" : item.name;
        }
        String api = item.api;
        if ("netapi".equalsIgnoreCase(api)) {
            api = "wy";
        } else if ("tencent".equalsIgnoreCase(api) || "qqmusic".equalsIgnoreCase(api)) {
            api = "qq";
        }
        return item.name + " [" + api + "]";
    }
}
