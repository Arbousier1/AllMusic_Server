package com.coloryr.allmusic.server.core.config;

import com.coloryr.allmusic.server.core.AllMusic;
import com.coloryr.allmusic.server.core.objs.CookieObj;
import com.coloryr.allmusic.server.core.objs.config.ConfigObj;
import com.coloryr.allmusic.server.core.objs.message.MessageObj;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class AllMusicConfigStore {
    private final Gson gson;

    public AllMusicConfigStore(Gson gson) {
        this.gson = gson;
    }

    public File ensureFile(File dir, String name) throws IOException {
        File file = new File(dir, name);
        if (!file.exists()) {
            file.createNewFile();
        }
        return file;
    }

    public ConfigObj loadConfig(File file) throws IOException {
        try (InputStreamReader reader = new InputStreamReader(Files.newInputStream(file.toPath()), StandardCharsets.UTF_8);
             BufferedReader bf = new BufferedReader(reader)) {
            return gson.fromJson(bf, ConfigObj.class);
        }
    }

    public MessageObj loadMessage(File file) throws IOException {
        try (InputStreamReader reader = new InputStreamReader(Files.newInputStream(file.toPath()), StandardCharsets.UTF_8);
             BufferedReader bf = new BufferedReader(reader)) {
            return gson.fromJson(bf, MessageObj.class);
        }
    }

    public List<CookieObj> loadCookie(File file) throws IOException {
        try (InputStreamReader reader = new InputStreamReader(Files.newInputStream(file.toPath()), StandardCharsets.UTF_8);
             BufferedReader bf = new BufferedReader(reader)) {
            Type listType = new TypeToken<ArrayList<CookieObj>>() {
            }.getType();
            return gson.fromJson(bf, listType);
        }
    }

    public void save(File file, Object data, String errorMessage) {
        try {
            String json = gson.toJson(data);
            try (FileOutputStream out = new FileOutputStream(file);
                 OutputStreamWriter writer = new OutputStreamWriter(out, StandardCharsets.UTF_8)) {
                writer.write(json);
            }
        } catch (Exception e) {
            AllMusic.log.data(errorMessage);
            e.printStackTrace();
        }
    }
}
