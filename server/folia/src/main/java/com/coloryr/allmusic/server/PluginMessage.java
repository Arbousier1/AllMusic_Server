package com.coloryr.allmusic.server;

import com.coloryr.allmusic.server.core.AllMusic;
import com.coloryr.allmusic.server.core.message.PluginMessageBridge;
import com.coloryr.allmusic.server.core.music.PlayMusic;
import com.coloryr.allmusic.server.core.music.TopLyricSave;
import com.coloryr.allmusic.server.core.objs.music.TopSongInfoObj;
import com.google.common.collect.Iterables;
import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteStreams;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.messaging.PluginMessageListener;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class PluginMessage implements PluginMessageListener {
    public static int size;
    public static String allList;
    public static boolean update = false;
    private static ScheduledExecutorService service;
    private final TopSongInfoObj info;
    private final TopLyricSave lyric;

    public PluginMessage() {
        info = (TopSongInfoObj) PlayMusic.nowPlayMusic;
        lyric = (TopLyricSave) PlayMusic.lyric;

        service = Executors.newSingleThreadScheduledExecutor();
        service.scheduleAtFixedRate(PluginMessage::clear, 0, 30, TimeUnit.SECONDS);
    }

    private static void clear() {
        PluginMessageBridge.clear();
        update = PluginMessageBridge.update;
    }

    public static void startUpdate() {
        Player player = Iterables.getFirst(Bukkit.getOnlinePlayers(), null);
        if (player == null)
            return;
        player.sendPluginMessage(AllMusicFolia.plugin, AllMusic.channelBC, PluginMessageBridge.createFoliaStartUpdatePacket());
    }

    public void stop() {
        service.shutdownNow();
        clear();
    }

    @Override
    public void onPluginMessageReceived(String channel, @NotNull Player player, byte[] message) {
        if (!channel.equals(AllMusic.channelBC)) {
            return;
        }
        ByteArrayDataInput in = ByteStreams.newDataInput(message);
        int type = in.readInt();
        PluginMessageBridge.markUpdated();
        update = PluginMessageBridge.update;
        switch (type) {
            case 0:
                info.setName(in.readUTF());
                break;
            case 1:
                info.setAl(in.readUTF());
                break;
            case 2:
                info.setAlia(in.readUTF());
                break;
            case 3:
                info.setAuthor(in.readUTF());
                break;
            case 4:
                info.setCall(in.readUTF());
                break;
            case 5:
                PluginMessageBridge.setSize(in.readInt());
                size = PluginMessageBridge.size;
                break;
            case 6:
                PluginMessageBridge.setAllList(in.readUTF());
                allList = PluginMessageBridge.allList;
                break;
            case 7:
                lyric.setLyric(in.readUTF());
                break;
            case 8:
                lyric.setTlyric(in.readUTF());
                break;
            case 9:
                lyric.setHaveT(in.readBoolean());
                break;
        }
    }
}
