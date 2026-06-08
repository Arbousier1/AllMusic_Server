package com.coloryr.allmusic.server.core.music.search;

import com.coloryr.allmusic.server.core.AllMusic;
import com.coloryr.allmusic.server.core.objs.message.ARG;
import com.coloryr.allmusic.server.core.objs.music.PlayerAddMusicObj;
import com.coloryr.allmusic.server.core.objs.music.SearchPageObj;

public class SearchService {
    private final SearchSessionStore sessionStore;
    private final SearchPresenter presenter;
    private final SearchAggregator aggregator;

    public SearchService(SearchSessionStore sessionStore, SearchPresenter presenter, SearchAggregator aggregator) {
        this.sessionStore = sessionStore;
        this.presenter = presenter;
        this.aggregator = aggregator;
    }

    public void handleSearch(PlayerAddMusicObj obj) {
        SearchPageObj search = aggregator.searchApis(obj);
        if (search == null)
            AllMusic.side.sendMessageTask(obj.sender, AllMusic.getMessage().search
                    .cantSearch.replace(ARG.name, obj.isDefault ? obj.args[0] : obj.args[1]));
        else {
            AllMusic.side.sendMessageTask(obj.sender, AllMusic.getMessage().search.res);
            addSearch(obj.name, search);
            AllMusic.side.runTask(() -> showSearch(obj.sender, search));
        }
    }

    public void showSearch(Object sender, SearchPageObj search) {
        presenter.showSearch(sender, search);
    }

    public void addSearch(String player, SearchPageObj page) {
        sessionStore.addSearch(player, page);
    }

    public SearchPageObj getSearch(String player) {
        return sessionStore.getSearch(player);
    }

    public void removeSearch(String player) {
        sessionStore.removeSearch(player);
    }

    public void clear() {
        sessionStore.clear();
    }
}
