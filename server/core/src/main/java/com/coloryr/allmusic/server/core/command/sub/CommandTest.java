package com.coloryr.allmusic.server.core.command.sub;

import com.coloryr.allmusic.server.core.AllMusic;
import com.coloryr.allmusic.server.core.IMusicApi;
import com.coloryr.allmusic.server.core.command.ACommand;
import com.coloryr.allmusic.server.core.objs.music.SongInfoObj;

public class CommandTest extends ACommand {
    @Override
    public void execute(Object sender, String name, String[] args) {
        if (args.length < 2) {
            AllMusic.side.sendMessage(sender, AllMusic.getMessage().command.error);
            return;
        }

        String musicID = null;
        IMusicApi api = null;

        if (args.length == 2) {
            api = AllMusic.getMusicApi(AllMusic.getConfig().defaultApi);
            musicID = args[1];
        } else if (args.length == 3) {
            api = AllMusic.getMusicApi(args[1]);
            musicID = args[2];
        } else {
            AllMusic.side.sendMessage(sender, "<light_purple>[AllMusic3]<dark_green>Invalid command");
        }

        if (api == null) {
            AllMusic.side.sendMessage(sender, AllMusic.getUnknownApiMessage());
            return;
        }

        musicID = api.getMusicId(musicID);

        if (api.checkId(musicID)) {
            AllMusic.side.sendMessage(sender, "<light_purple>[AllMusic3]<dark_green>Testing song " + musicID);
            try {
                SongInfoObj info = api.getMusic(musicID, "test", false);
                if (info == null) {
                    AllMusic.side.sendMessage(sender, "<light_purple>[AllMusic3]<dark_green>Test failed");
                    return;
                }
                AllMusic.side.sendMessage(sender, "<light_purple>[AllMusic3]<dark_green>Music name " + info.getName());
                AllMusic.side.sendMessage(sender, "<light_purple>[AllMusic3]<dark_green>Music author " + info.getAuthor());
                String url = api.getPlayUrl(musicID);
                AllMusic.side.sendMessage(sender, "<light_purple>[AllMusic3]<dark_green>Play url " + url);
            } catch (Exception e) {
                AllMusic.side.sendMessage(sender, "<light_purple>[AllMusic3]<dark_green>Test error");
                e.printStackTrace();
            }
        }
    }
}
