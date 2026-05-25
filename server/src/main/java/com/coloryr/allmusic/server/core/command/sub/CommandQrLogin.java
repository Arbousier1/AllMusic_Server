package com.coloryr.allmusic.server.core.command.sub;

import com.coloryr.allmusic.server.core.AllMusic;
import com.coloryr.allmusic.server.core.command.ACommand;
import com.coloryr.allmusic.server.core.utils.CookieImportApi;
import com.coloryr.allmusic.server.core.utils.QrLoginBridge;

import java.util.Collections;
import java.util.List;

public class CommandQrLogin extends ACommand {
    @Override
    public void execute(Object sender, String name, String[] args) {
        if (args.length < 2) {
            AllMusic.side.sendMessage(sender, AllMusic.getMessage().command.error);
            return;
        }

        String api = CookieImportApi.normalizeApi(args[1]);
        CookieImportApi.Target target = CookieImportApi.resolve(api);
        if (target == null) {
            AllMusic.side.sendMessage(sender,
                    "<light_purple>[AllMusic3]<red>Unsupported api for QR login. Available: netease");
            return;
        }

        if (!target.supportsQrLogin) {
            AllMusic.side.sendMessage(sender,
                    "<light_purple>[AllMusic3]<red>QR login is not yet supported for "
                            + target.displayName + ". Currently supported: netease");
            return;
        }

        try {
            QrLoginBridge.SessionHandle handle = QrLoginBridge.start(sender, api);
            AllMusic.side.sendMessage(sender,
                    "<light_purple>[AllMusic3]<yellow>QR login started for " + target.displayName);
            AllMusic.side.sendMessage(sender,
                    "<light_purple>[AllMusic3]<yellow>Open in your browser: " + handle.url);
            AllMusic.side.sendMessage(sender,
                    "<light_purple>[AllMusic3]<yellow>Scan the QR code with the "
                            + target.displayName + " app. Session expires in 5 minutes.");
        } catch (Exception e) {
            AllMusic.side.sendMessage(sender,
                    "<light_purple>[AllMusic3]<red>Failed to start QR login: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    public List<String> tab(Object sender, String name, String[] args, int index) {
        if (index == 1) {
            return Collections.singletonList("netease");
        }
        return super.tab(sender, name, args, index);
    }
}
