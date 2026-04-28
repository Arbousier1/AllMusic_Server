package com.coloryr.allmusic.server.core.command.sub;

import com.coloryr.allmusic.server.core.AllMusic;
import com.coloryr.allmusic.server.core.command.ACommand;
import com.coloryr.allmusic.server.core.utils.CookieImportApi;
import com.coloryr.allmusic.server.core.utils.LocalCookieBridge;

import java.util.List;

public class CommandImportCookie extends ACommand {
    @Override
    public void execute(Object sender, String name, String[] args) {
        if (args.length < 2) {
            AllMusic.side.sendMessage(sender, AllMusic.getMessage().command.error);
            return;
        }

        String api = CookieImportApi.normalizeApi(args[1]);
        CookieImportApi.Target target = CookieImportApi.resolve(api);
        if (target == null) {
            AllMusic.side.sendMessage(sender, "<light_purple>[AllMusic3]<red>Unsupported import api");
            return;
        }

        try {
            LocalCookieBridge.SessionHandle handle = LocalCookieBridge.start(sender, api);
            AllMusic.side.sendMessage(sender,
                    "<light_purple>[AllMusic3]<yellow>Local cookie helper started for " + target.id);
            AllMusic.side.sendMessage(sender,
                    "<light_purple>[AllMusic3]<yellow>Open in your local browser: " + handle.url);
            AllMusic.side.sendMessage(sender,
                    "<light_purple>[AllMusic3]<yellow>Then log in on " + target.siteUrl
                            + " and use the bookmarklet or console snippet from that helper page");
        } catch (Exception e) {
            AllMusic.side.sendMessage(sender,
                    "<light_purple>[AllMusic3]<red>Failed to start local cookie helper: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public List<String> tab(Object sender, String name, String[] args, int index) {
        if (index == 1) {
            return CookieImportApi.listApis();
        }
        return super.tab(sender, name, args, index);
    }
}
