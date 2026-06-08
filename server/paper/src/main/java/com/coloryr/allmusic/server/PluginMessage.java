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
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

public class PluginMessage implements PluginMessageListener {
    public static volatile int size;
    public static volatile String allList;
    public static volatile boolean update = false;
    private static BukkitTask clearTask;
    private final TopSongInfoObj info;
    private final TopLyricSave lyric;

    public PluginMessage() {
        info = (TopSongInfoObj) PlayMusic.nowPlayMusic;
        lyric = (TopLyricSave) PlayMusic.lyric;

        clearTask = Bukkit.getScheduler().runTaskTimer(AllMusicPaper.plugin, PluginMessage::clear, 0L, 20L * 30);
    }

    private static void clear() {
        PluginMessageBridge.clear();
        update = PluginMessageBridge.update;
    }

    private static void sendPack(byte[] data) {
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(AllMusicPaper.plugin, () -> sendPack(data));
            return;
        }
        Player player = Iterables.getFirst(Bukkit.getOnlinePlayers(), null);
        if (player == null)
            return;
        player.sendPluginMessage(AllMusicPaper.plugin, AllMusic.channelBC, data);
    }

    public static void startUpdate() {
        sendPack(PluginMessageBridge.createStartUpdatePacket());
    }

    public void stop() {
        if (clearTask != null) {
            clearTask.cancel();
            clearTask = null;
        }
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
            case 12: {
                String uuid = in.readUTF();
                int cost = in.readInt();
                String name = in.readUTF();

                if (AllMusic.economy == null) {
                    sendPack(PluginMessageBridge.createEconomyResponsePacket(12, uuid, 0));
                } else if (!AllMusic.economy.check(name, cost)) {
                    sendPack(PluginMessageBridge.createEconomyResponsePacket(12, uuid, 1));
                } else {
                    sendPack(PluginMessageBridge.createEconomyResponsePacket(12, uuid, 2));
                }
                break;
            }
            case 13: {
                String uuid = in.readUTF();
                int cost = in.readInt();
                String name = in.readUTF();

                if (AllMusic.economy == null) {
                    sendPack(PluginMessageBridge.createEconomyResponsePacket(13, uuid, 0));
                } else if (!AllMusic.economy.cost(name, cost)) {
                    sendPack(PluginMessageBridge.createEconomyResponsePacket(13, uuid, 1));
                } else {
                    sendPack(PluginMessageBridge.createEconomyResponsePacket(13, uuid, 2));
                }
                break;
            }
        }
    }
}
