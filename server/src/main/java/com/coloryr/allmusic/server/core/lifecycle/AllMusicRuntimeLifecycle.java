package com.coloryr.allmusic.server.core.lifecycle;

import com.coloryr.allmusic.server.core.music.MusicHttpClient;
import com.coloryr.allmusic.server.core.music.MusicSearch;
import com.coloryr.allmusic.server.core.music.PlayMusic;
import com.coloryr.allmusic.server.core.music.PlayRuntime;
import com.coloryr.allmusic.server.core.saves.SaveTask;
import com.coloryr.allmusic.server.core.side.BaseSide;

public class AllMusicRuntimeLifecycle {
    public void startRuntime() {
        MusicHttpClient.init();
        PlayMusic.start();
        PlayRuntime.start();
        MusicSearch.start();
        SaveTask.start();
    }

    public void stopRuntime(BaseSide side) {
        PlayRuntime.stop();
        SaveTask.stop();
        side.sendStop();
    }
}
