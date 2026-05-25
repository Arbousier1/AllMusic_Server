package com.coloryr.allmusic.server.core.command.sub;

import com.coloryr.allmusic.server.core.AllMusic;
import com.coloryr.allmusic.server.core.command.ACommand;
import com.coloryr.allmusic.server.core.objs.CookieObj;
import com.coloryr.allmusic.server.core.utils.CookieImportApi;
import com.coloryr.allmusic.server.core.utils.WindowsCookieImporter;

import java.io.File;
import java.util.List;

public class CommandImportCookieDb extends ACommand {
    private static volatile boolean isRunning;

    @Override
    public void execute(final Object sender, String name, String[] args) {
        if (args.length < 2) {
            AllMusic.side.sendMessage(sender, AllMusic.getMessage().command.error);
            return;
        }

        final String api = CookieImportApi.normalizeApi(args[1]);
        if (CookieImportApi.resolve(api) == null) {
            AllMusic.side.sendMessage(sender, "<light_purple>[AllMusic3]<red>Unsupported import api");
            return;
        }
        if (isRunning) {
            AllMusic.side.sendMessage(sender, "<light_purple>[AllMusic3]<red>Browser cookie db import is already running");
            return;
        }

        isRunning = true;
        AllMusic.side.sendMessage(sender, "<light_purple>[AllMusic3]<yellow>Importing browser cookie db for " + api);
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    File folder = AllMusic.side.getFolder();
                    File cookieFile = new File(folder, "cookie.json");
                    List<CookieObj> imported = WindowsCookieImporter.importToFile(api, cookieFile);
                    AllMusic.cookie = WindowsCookieImporter.readCookieFile(cookieFile);
                    AllMusic.side.sendMessageTask(sender,
                            "<light_purple>[AllMusic3]<dark_green>Imported " + imported.size()
                                    + " browser cookies for " + api + " into cookie.json");
                } catch (Exception e) {
                    AllMusic.side.sendMessageTask(sender,
                            "<light_purple>[AllMusic3]<red>Browser cookie db import failed: " + e.getMessage());
                    e.printStackTrace();
                } finally {
                    isRunning = false;
                }
            }
        }, "AllMusic_importCookieDb");
        thread.start();
    }

    @Override
    public List<String> tab(Object sender, String name, String[] args, int index) {
        if (index == 1) {
            return CookieImportApi.listApis();
        }
        return super.tab(sender, name, args, index);
    }
}
