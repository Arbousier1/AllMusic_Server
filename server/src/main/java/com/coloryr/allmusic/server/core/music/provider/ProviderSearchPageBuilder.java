package com.coloryr.allmusic.server.core.music.provider;

import com.coloryr.allmusic.server.core.objs.SearchMusicObj;
import com.coloryr.allmusic.server.core.objs.music.SearchPageObj;

import java.util.List;

public class ProviderSearchPageBuilder {
    public SearchPageObj build(List<SearchMusicObj> items, String api) {
        return items == null || items.isEmpty() ? null
                : new SearchPageObj(items, Math.max(1, (items.size() + 9) / 10), api);
    }
}
