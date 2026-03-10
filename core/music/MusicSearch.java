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
        List<List<SearchMusicObj>> groups = new ArrayList<List<SearchMusicObj>>();
        List<SearchMusicObj> res = new ArrayList<>();
        int max = 0;
        for (IMusicApi api : AllMusic.getRegisteredMusicApis()) {
            List<SearchMusicObj> items = searchByApi(api, obj);
            if (items.isEmpty()) {
                continue;
            }
            groups.add(items);
            max = Math.max(max, items.size());
        }
        if (groups.isEmpty()) {
            return null;
        }
        for (int i = 0; i < max; i++) {
            for (List<SearchMusicObj> items : groups) {
                if (i < items.size()) {
                    res.add(items.get(i));
                }
            }
        }
        return new SearchPageObj(res, Math.max(1, (res.size() + 9) / 10), "all");
    }

    private static List<SearchMusicObj> searchByApi(IMusicApi api, PlayerAddMusicObj obj) {
        if (api == null) {
            return new ArrayList<>();
        }

        SearchPageObj page;
        try {
            page = api.search(obj.args, obj.isDefault);
        } catch (Exception e) {
            AllMusic.log.data("<light_purple>[AllMusic3]<red>Search failed on api " + api.getId());
            e.printStackTrace();
            return new ArrayList<>();
        }
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
        } else if ("kugou".equalsIgnoreCase(api)) {
            api = "kg";
        } else if ("kuwo".equalsIgnoreCase(api)) {
            api = "kw";
        } else if ("baidu".equalsIgnoreCase(api) || "taihe".equalsIgnoreCase(api)
                || "qianqian".equalsIgnoreCase(api)) {
            api = "bd";
        }
        return item.name + " [" + api + "]";
    }
}
