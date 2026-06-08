package com.coloryr.allmusic.server.core.music.search;

import com.coloryr.allmusic.server.core.AllMusic;
import com.coloryr.allmusic.server.core.objs.SearchMusicObj;
import com.coloryr.allmusic.server.core.objs.message.ARG;
import com.coloryr.allmusic.server.core.objs.music.SearchPageObj;

public class SearchPresenter {
    public void showSearch(Object sender, SearchPageObj search) {
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

    private String formatName(SearchMusicObj item) {
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
