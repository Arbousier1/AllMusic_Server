package com.coloryr.allmusic.server.core.message;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;

public final class PluginMessageBridge {
    public static volatile int size;
    public static volatile String allList;
    public static volatile boolean update = false;

    private PluginMessageBridge() {
    }

    public static void clear() {
        update = false;
    }

    public static void markUpdated() {
        update = true;
    }

    public static void setSize(int value) {
        size = value;
    }

    public static void setAllList(String value) {
        allList = value;
    }

    public static byte[] createStartUpdatePacket() {
        return write(out -> {
            out.writeInt(255);
            out.writeUTF("allmusic");
        });
    }

    public static byte[] createFoliaStartUpdatePacket() {
        return write(out -> out.writeUTF("allmusic"));
    }

    public static byte[] createEconomyResponsePacket(int type, String uuid, int status) {
        return write(out -> {
            out.writeInt(type);
            out.writeUTF(uuid);
            out.write(status);
        });
    }

    public static byte[] createSpigotEconomyResponsePacket(int type, String uuid, int status) {
        return write(out -> {
            out.write(type);
            out.writeUTF(uuid);
            out.write(status);
        });
    }

    private static byte[] write(PacketWriter writer) {
        try {
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(stream);
            writer.write(out);
            return stream.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private interface PacketWriter {
        void write(DataOutputStream out) throws IOException;
    }
}
