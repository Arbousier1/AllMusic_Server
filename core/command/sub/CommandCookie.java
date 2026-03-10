package com.coloryr.allmusic.server.core.command.sub;

import com.coloryr.allmusic.server.core.AllMusic;
import com.coloryr.allmusic.server.core.command.ACommand;
import com.coloryr.allmusic.server.core.music.MusicHttpClient;

import java.util.Arrays;
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
            api = resolveAlias(args[0]);
            start = 1;
        }

        String[] domains = resolveDomains(api);
        if (domains == null) {
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

        MusicHttpClient.importCookieHeader(cookie, domains);
        AllMusic.side.sendMessage(sender,
                "<light_purple>[AllMusic3]<dark_green>Cookie imported to cookie.json for " + normalizeApi(api));
    }

    @Override
    public List<String> tab(Object sender, String name, String[] args, int index) {
        if ("cookie".equalsIgnoreCase(args[0]) && index == 1) {
            return Arrays.asList("netease", "qq", "kugou", "kuwo", "baidu");
        }
        return super.tab(sender, name, args, index);
    }

    private String resolveAlias(String arg) {
        String value = normalizeApi(arg);
        if (value.startsWith("qq") || value.startsWith("tencent")) {
            return "qq";
        }
        if (value.startsWith("wy") || value.startsWith("163") || value.startsWith("netease")) {
            return "netease";
        }
        if (value.startsWith("kg") || value.startsWith("kugou")) {
            return "kugou";
        }
        if (value.startsWith("kw") || value.startsWith("kuwo")) {
            return "kuwo";
        }
        if (value.startsWith("bd") || value.startsWith("baidu") || value.startsWith("taihe")
                || value.startsWith("qianqian")) {
            return "baidu";
        }
        return value;
    }

    private String normalizeApi(String api) {
        return api == null ? "" : api.trim().toLowerCase();
    }

    private String[] resolveDomains(String api) {
        String value = resolveAlias(api);
        if ("qq".equals(value)) {
            return new String[]{"y.qq.com", ".y.qq.com", "u.y.qq.com", "c.y.qq.com", ".qq.com"};
        }
        if ("netease".equals(value)) {
            return new String[]{"music.163.com", ".music.163.com", "interface3.music.163.com", ".163.com"};
        }
        if ("kugou".equals(value)) {
            return new String[]{"www.kugou.com", ".kugou.com", "m.kugou.com", "mobilecdn.kugou.com", "wwwapi.kugou.com"};
        }
        if ("kuwo".equals(value)) {
            return new String[]{"www.kuwo.cn", ".kuwo.cn", "m.kuwo.cn"};
        }
        if ("baidu".equals(value)) {
            return new String[]{"musicapi.taihe.com", ".taihe.com", ".qianqian.com"};
        }
        return null;
    }
}
