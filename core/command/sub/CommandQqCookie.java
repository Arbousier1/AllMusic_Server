package com.coloryr.allmusic.server.core.command.sub;

import com.coloryr.allmusic.server.core.AllMusic;
import com.coloryr.allmusic.server.core.command.ACommand;
import com.coloryr.allmusic.server.core.music.MusicHttpClient;

public class CommandQqCookie extends ACommand {
    @Override
    public void execute(Object sender, String name, String[] args) {
        if (args.length < 2) {
            AllMusic.side.sendMessage(sender, AllMusic.getMessage().command.error);
            return;
        }

        StringBuilder builder = new StringBuilder();
        for (int i = 1; i < args.length; i++) {
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
            AllMusic.side.sendMessage(sender, "<light_purple>[AllMusic3]<red>QQ cookie is empty");
            return;
        }

        MusicHttpClient.importCookieHeader(cookie,
                "y.qq.com",
                ".y.qq.com",
                "u.y.qq.com",
                "c.y.qq.com",
                ".qq.com");
        AllMusic.side.sendMessage(sender,
                "<light_purple>[AllMusic3]<dark_green>QQ cookie imported to cookie.json");
    }
}
