package com.coloryr.allmusic.server.core.music;

import com.coloryr.allmusic.server.core.AllMusic;
import com.coloryr.allmusic.server.core.music.search.SearchAggregator;
import com.coloryr.allmusic.server.core.music.search.SearchPresenter;
import com.coloryr.allmusic.server.core.music.search.SearchSessionStore;
import com.coloryr.allmusic.server.core.objs.message.ARG;
import com.coloryr.allmusic.server.core.objs.music.PlayerAddMusicObj;
import com.coloryr.allmusic.server.core.objs.music.SearchPageObj;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class MusicSearch {

    private static final SearchSessionStore searchSessionStore = new SearchSessionStore();
    private static final SearchPresenter searchPresenter = new SearchPresenter();
    private static final SearchAggregator searchAggregator = new SearchAggregator();

    private static final Queue<PlayerAddMusicObj> tasks = new ConcurrentLinkedQueue<>();

    private static void task() {
        AllMusic.log.data("歌曲搜索线程启动");
        while (AllMusic.isRun) {
            try {
                PlayerAddMusicObj obj = tasks.poll();
                if (obj != null) {
                    SearchPageObj search = searchApis(obj);
                    if (search == null)
                        AllMusic.side.sendMessageTask(obj.sender, AllMusic.getMessage().search
                                .cantSearch.replace(ARG.name, obj.isDefault ? obj.args[0] : obj.args[1]));
                    else {
                        AllMusic.side.sendMessageTask(obj.sender, AllMusic.getMessage().search.res);
                        addSearch(obj.name, search);
                        AllMusic.side.runTask(() -> showSearch(obj.sender, search));
                    }
                }
                Thread.sleep(100);
            } catch (Exception e) {
                AllMusic.log.data("搜歌出现问题");
                e.printStackTrace();
            }
        }
        searchSessionStore.clear();
        tasks.clear();
        AllMusic.log.data("歌曲搜索线程停止");
    }

    public static void start() {
        new Thread(MusicSearch::task, "allmusic_search").start();
    }

    public static void addSearch(PlayerAddMusicObj obj) {
        tasks.add(obj);
    }

    private static SearchPageObj searchApis(PlayerAddMusicObj obj) {
        return searchAggregator.searchApis(obj);
    }

    /**
     * 展示搜歌结果
     *
     * @param sender 发送者
     * @param search 搜歌结果
     */
    public static void showSearch(Object sender, SearchPageObj search) {
        searchPresenter.showSearch(sender, search);
    }

    /**
     * 添加搜歌结果
     *
     * @param player 用户名
     * @param page   结果
     */
    public static void addSearch(String player, SearchPageObj page) {
        searchSessionStore.addSearch(player, page);
    }

    /**
     * 获取搜歌结果
     *
     * @param player 用户名
     * @return 结果
     */
    public static SearchPageObj getSearch(String player) {
        return searchSessionStore.getSearch(player);
    }

    /**
     * 删除搜歌结果
     *
     * @param player 用户名
     */
    public static void removeSearch(String player) {
        searchSessionStore.removeSearch(player);
    }
}
