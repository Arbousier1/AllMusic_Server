package com.coloryr.allmusic.server.core.command.sub;

import com.coloryr.allmusic.server.core.AllMusic;
import com.coloryr.allmusic.server.core.command.ACommand;
import com.coloryr.allmusic.server.netapi.qq.QqApiQrLogin;

public class CommandQqQrLogin extends ACommand {
    @Override
    public void execute(Object sender, String name, String[] args) {
        if (args.length != 1) {
            AllMusic.side.sendMessage(sender, AllMusic.getMessage().command.error);
            return;
        }

        if (QqApiQrLogin.start(sender)) {
            AllMusic.side.sendMessage(sender,
                    "<light_purple>[AllMusic3]<yellow>QQ Music QR code has been printed to console, scan it with QQ or QQ Music");
        } else {
            AllMusic.side.sendMessage(sender,
                    "<light_purple>[AllMusic3]<yellow>A QQ QR login task is already running");
        }
    }
}
