package com.coloryr.allmusic.server.core.music.search;

import com.coloryr.allmusic.server.core.objs.music.SearchPageObj;

import java.util.HashMap;
import java.util.Map;

public class SearchSessionStore {
    private final Map<String, SearchPageObj> searchSave = new HashMap<>();

    public void addSearch(String player, SearchPageObj page) {
        player = player.toLowerCase();
        searchSave.put(player, page);
    }

    public SearchPageObj getSearch(String player) {
        player = player.toLowerCase();
        return searchSave.get(player);
    }

    public void removeSearch(String player) {
        player = player.toLowerCase();
        searchSave.remove(player);
    }

    public void clear() {
        searchSave.clear();
    }
}
