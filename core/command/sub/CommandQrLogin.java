package com.coloryr.allmusic.server.core.command.sub;

import com.coloryr.allmusic.server.core.AllMusic;
import com.coloryr.allmusic.server.core.command.ACommand;
import com.coloryr.allmusic.server.netapi.NetApiQrLogin;
import com.coloryr.allmusic.server.netapi.qq.QqApiQrLogin;

import java.util.Arrays;
import java.util.List;

public class CommandQrLogin extends ACommand {
    @Override
    public void execute(Object sender, String name, String[] args) {
        if (args.length == 1) {
            startNetease(sender);
            return;
        }

        if (args.length != 2) {
            AllMusic.side.sendMessage(sender, AllMusic.getMessage().command.error);
            return;
        }

        String api = args[1] == null ? "" : args[1].trim().toLowerCase();
        switch (api) {
            case "163":
            case "netease":
            case "wangyi":
            case "wy":
                startNetease(sender);
                return;
            case "qq":
            case "qqmusic":
            case "tencent":
                startQq(sender);
                return;
            default:
                AllMusic.side.sendMessage(sender,
                        "<light_purple>[AllMusic3]<red>Unknown qr login api, use netease or qq");
        }
    }

    @Override
    public List<String> tab(Object sender, String name, String[] args, int index) {
        if (index == 1) {
            return Arrays.asList("netease", "qq");
        }
        return super.tab(sender, name, args, index);
    }

    private void startNetease(Object sender) {
        if (NetApiQrLogin.start(sender)) {
            AllMusic.side.sendMessage(sender,
                    "<light_purple>[AllMusic3]<yellow>NetEase QR code has been printed to console, scan it with the NetEase Cloud Music app");
        } else {
            AllMusic.side.sendMessage(sender,
                    "<light_purple>[AllMusic3]<yellow>A NetEase QR login task is already running");
        }
    }

    private void startQq(Object sender) {
        if (QqApiQrLogin.start(sender)) {
            AllMusic.side.sendMessage(sender,
                    "<light_purple>[AllMusic3]<yellow>QQ Music QR code has been printed to console, scan it with QQ or QQ Music");
        } else {
            AllMusic.side.sendMessage(sender,
                    "<light_purple>[AllMusic3]<yellow>A QQ QR login task is already running");
        }
    }
}
