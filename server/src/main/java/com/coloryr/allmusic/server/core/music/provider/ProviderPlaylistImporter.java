package com.coloryr.allmusic.server.core.music.provider;

import com.coloryr.allmusic.server.core.AllMusic;
import com.coloryr.allmusic.server.core.objs.message.ARG;
import com.coloryr.allmusic.server.core.saves.MusicListSave;

import java.util.List;

public class ProviderPlaylistImporter {
    public void importIdleList(List<String> ids, String api, Object sender, String name) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        MusicListSave.addIdleList(ids, api);
        AllMusic.side.sendMessageTask(sender,
                AllMusic.getMessage().musicPlay.listMusic.get.replace(ARG.name, name));
    }
}
