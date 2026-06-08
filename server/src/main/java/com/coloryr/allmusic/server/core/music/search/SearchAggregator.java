package com.coloryr.allmusic.server.core.music.search;

import com.coloryr.allmusic.server.core.AllMusic;
import com.coloryr.allmusic.server.core.IMusicApi;
import com.coloryr.allmusic.server.core.objs.SearchMusicObj;
import com.coloryr.allmusic.server.core.objs.music.PlayerAddMusicObj;
import com.coloryr.allmusic.server.core.objs.music.SearchPageObj;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class SearchAggregator {
    public SearchPageObj searchApis(PlayerAddMusicObj obj) {
        Collection<IMusicApi> apis = AllMusic.getMusicApis(obj.api);
        if (apis.isEmpty()) {
            AllMusic.side.sendMessageTask(obj.sender, AllMusic.getUnknownApiMessage());
            return null;
        }
        if (apis.size() == 1) {
            return apis.iterator().next().search(obj.args, obj.isDefault);
        }

        List<List<SearchMusicObj>> groups = new ArrayList<List<SearchMusicObj>>();
        List<SearchMusicObj> res = new ArrayList<>();
        int max = 0;
        for (IMusicApi api : apis) {
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
        return new SearchPageObj(res, Math.max(1, (res.size() + 9) / 10), obj.api);
    }

    private List<SearchMusicObj> searchByApi(IMusicApi api, PlayerAddMusicObj obj) {
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
}
