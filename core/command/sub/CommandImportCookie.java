package com.coloryr.allmusic.server.core.command.sub;

import com.coloryr.allmusic.server.core.AllMusic;
import com.coloryr.allmusic.server.core.command.ACommand;
import com.coloryr.allmusic.server.core.utils.WindowsCookieImporter;

import java.io.File;
import java.util.Arrays;
import java.util.List;

public class CommandImportCookie extends ACommand {
    @Override
    public void execute(final Object sender, String name, String[] args) {
        if (args.length < 2) {
            AllMusic.side.sendMessage(sender, AllMusic.getMessage().command.error);
            return;
        }

        final String api = WindowsCookieImporter.normalizeApi(args[1]);
        if (api.isEmpty()) {
            AllMusic.side.sendMessage(sender, "<light_purple>[AllMusic3]<red>Unsupported import api");
            return;
        }

        AllMusic.side.sendMessage(sender, "<light_purple>[AllMusic3]<yellow>Importing browser cookies for " + api);
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    File folder = AllMusic.side.getFolder();
                    File cookieFile = new File(folder, "cookie.json");
                    File jarFile = WindowsCookieImporter.resolveJarFile(CommandImportCookie.class);
                    File scriptFile = null;
                    if (jarFile != null && jarFile.isFile()) {
                        scriptFile = WindowsCookieImporter.writeHelperScript(folder, cookieFile, jarFile);
                    }
                    List<com.coloryr.allmusic.server.core.objs.CookieObj> imported =
                            WindowsCookieImporter.importToFile(api, cookieFile);
                    AllMusic.cookie = WindowsCookieImporter.readCookieFile(cookieFile);
                    AllMusic.side.sendMessageTask(sender,
                            "<light_purple>[AllMusic3]<dark_green>Imported " + imported.size()
                                    + " browser cookies for " + api + " into cookie.json");
                    if (scriptFile != null) {
                        AllMusic.side.sendMessageTask(sender,
                                "<light_purple>[AllMusic3]<yellow>Helper script: " + scriptFile.getAbsolutePath());
                    }
                } catch (Exception e) {
                    AllMusic.side.sendMessageTask(sender,
                            "<light_purple>[AllMusic3]<red>Browser cookie import failed: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }, "AllMusic_importCookie");
        thread.start();
    }

    @Override
    public List<String> tab(Object sender, String name, String[] args, int index) {
        if (index == 1) {
            return Arrays.asList("netease", "qq", "kugou", "kuwo", "baidu");
        }
        return super.tab(sender, name, args, index);
    }
}
