package com.coloryr.allmusic.server.core.command.sub;

import com.coloryr.allmusic.server.core.AllMusic;
import com.coloryr.allmusic.server.core.command.ACommand;
import com.coloryr.allmusic.server.core.music.MusicHttpClient;
import com.coloryr.allmusic.server.core.utils.CookieImportApi;

import java.util.List;

public class CommandCookie extends ACommand {
    @Override
    public void execute(Object sender, String name, String[] args) {
        String api;
        int start;

        if ("cookie".equalsIgnoreCase(args[0])) {
            if (args.length < 3) {
                AllMusic.side.sendMessage(sender, AllMusic.getMessage().command.error);
                return;
            }
            api = args[1];
            start = 2;
        } else {
            if (args.length < 2) {
                AllMusic.side.sendMessage(sender, AllMusic.getMessage().command.error);
                return;
            }
            api = CookieImportApi.normalizeApi(args[0]);
            start = 1;
        }

        CookieImportApi.Target target = CookieImportApi.resolve(api);
        if (target == null) {
            AllMusic.side.sendMessage(sender, "<light_purple>[AllMusic3]<red>Unsupported cookie api");
            return;
        }

        StringBuilder builder = new StringBuilder();
        for (int i = start; i < args.length; i++) {
            if (args[i] == null || args[i].isEmpty()) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(args[i]);
        }

        String cookie = builder.toString().trim();
        if (cookie.isEmpty()) {
            AllMusic.side.sendMessage(sender, "<light_purple>[AllMusic3]<red>Cookie is empty");
            return;
        }

        MusicHttpClient.importCookieHeader(cookie, target.getImportDomains());
        AllMusic.side.sendMessage(sender,
                "<light_purple>[AllMusic3]<dark_green>Cookie imported to cookie.json for " + target.id);
    }

    @Override
    public List<String> tab(Object sender, String name, String[] args, int index) {
        if ("cookie".equalsIgnoreCase(args[0]) && index == 1) {
            return CookieImportApi.listApis();
        }
        return super.tab(sender, name, args, index);
    }
}
