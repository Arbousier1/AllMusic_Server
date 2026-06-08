package com.coloryr.allmusic.server.core.music;

import com.coloryr.allmusic.server.core.AllMusic;
import com.coloryr.allmusic.server.core.music.search.SearchAggregator;
import com.coloryr.allmusic.server.core.music.search.SearchPresenter;
import com.coloryr.allmusic.server.core.music.search.SearchService;
import com.coloryr.allmusic.server.core.music.search.SearchSessionStore;
import com.coloryr.allmusic.server.core.objs.music.PlayerAddMusicObj;
import com.coloryr.allmusic.server.core.objs.music.SearchPageObj;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class MusicSearch {

    private static final SearchSessionStore searchSessionStore = new SearchSessionStore();
    private static final SearchPresenter searchPresenter = new SearchPresenter();
    private static final SearchAggregator searchAggregator = new SearchAggregator();
    private static final SearchService searchService = new SearchService(searchSessionStore, searchPresenter, searchAggregator);

    private static final Queue<PlayerAddMusicObj> tasks = new ConcurrentLinkedQueue<>();

    private static void task() {
        AllMusic.log.data("歌曲搜索线程启动");
        while (AllMusic.isRun) {
            try {
                PlayerAddMusicObj obj = tasks.poll();
                if (obj != null) {
                    searchService.handleSearch(obj);
                }
                Thread.sleep(100);
            } catch (Exception e) {
                AllMusic.log.data("搜歌出现问题");
                e.printStackTrace();
            }
        }
        searchService.clear();
        tasks.clear();
        AllMusic.log.data("歌曲搜索线程停止");
    }

    public static void start() {
        new Thread(MusicSearch::task, "allmusic_search").start();
    }

    public static void addSearch(PlayerAddMusicObj obj) {
        tasks.add(obj);
    }

    /**
     * 展示搜歌结果
     *
     * @param sender 发送者
     * @param search 搜歌结果
     */
    public static void showSearch(Object sender, SearchPageObj search) {
        searchService.showSearch(sender, search);
    }

    /**
     * 添加搜歌结果
     *
     * @param player 用户名
     * @param page   结果
     */
    public static void addSearch(String player, SearchPageObj page) {
        searchService.addSearch(player, page);
    }

    /**
     * 获取搜歌结果
     *
     * @param player 用户名
     * @return 结果
     */
    public static SearchPageObj getSearch(String player) {
        return searchService.getSearch(player);
    }

    /**
     * 删除搜歌结果
     *
     * @param player 用户名
     */
    public static void removeSearch(String player) {
        searchService.removeSearch(player);
    }
}
