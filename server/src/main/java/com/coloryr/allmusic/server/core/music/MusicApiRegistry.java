package com.coloryr.allmusic.server.core.music;

import com.coloryr.allmusic.server.core.IMusicApi;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;

public class MusicApiRegistry {
    private final Map<String, IMusicApi> apis = new ConcurrentHashMap<>();
    private final Map<String, IMusicApi> primaryApis = Collections.synchronizedMap(new LinkedHashMap<String, IMusicApi>());
    private final Map<String, LinkedHashMap<String, IMusicApi>> groups = Collections.synchronizedMap(new LinkedHashMap<String, LinkedHashMap<String, IMusicApi>>());

    public void clear() {
        apis.clear();
        primaryApis.clear();
        groups.clear();
    }

    public void register(IMusicApi api, String... aliases) {
        if (api == null) {
            return;
        }

        String id = normalize(api.getId());
        if (id != null && !id.isEmpty()) {
            apis.put(id, api);
            primaryApis.put(id, api);
            addGroup(id, api);
            addGroup(resolveGroup(id), api);
        }

        if (aliases == null) {
            return;
        }

        for (String alias : aliases) {
            String key = normalize(alias);
            if (key != null && !key.isEmpty()) {
                apis.put(key, api);
                addGroup(key, api);
                addGroup(resolveGroup(key), api);
            }
        }
    }

    public IMusicApi get(String api) {
        String key = normalize(api);
        if (key == null || key.isEmpty()) {
            return null;
        }
        return apis.get(key);
    }

    public Collection<IMusicApi> getMany(String api) {
        String key = normalize(api);
        if (key == null || key.isEmpty() || "all".equalsIgnoreCase(key)) {
            return getRegisteredApis();
        }
        LinkedHashMap<String, IMusicApi> group = groups.get(key);
        if (group != null && !group.isEmpty()) {
            return Collections.unmodifiableCollection(new ArrayList<IMusicApi>(group.values()));
        }
        IMusicApi item = apis.get(key);
        if (item == null) {
            return Collections.emptyList();
        }
        return Collections.singletonList(item);
    }

    public boolean has(String api) {
        return !getMany(api).isEmpty();
    }

    public boolean hasRegisteredApis() {
        return !primaryApis.isEmpty();
    }

    public int size() {
        return apis.size();
    }

    public Map<String, IMusicApi> getApis() {
        return apis;
    }

    public String getApiList() {
        return String.join(", ", new TreeSet<>(apis.keySet()));
    }

    public Collection<IMusicApi> getRegisteredApis() {
        return Collections.unmodifiableCollection(new ArrayList<IMusicApi>(primaryApis.values()));
    }

    private void addGroup(String group, IMusicApi api) {
        String key = normalize(group);
        String id = normalize(api == null ? null : api.getId());
        if (key == null || key.isEmpty() || id == null || id.isEmpty()) {
            return;
        }
        LinkedHashMap<String, IMusicApi> groupApis = groups.get(key);
        if (groupApis == null) {
            groupApis = new LinkedHashMap<String, IMusicApi>();
            groups.put(key, groupApis);
        }
        groupApis.put(id, api);
    }

    private String resolveGroup(String api) {
        String key = normalize(api);
        if (key == null || key.isEmpty()) {
            return null;
        }
        if (key.contains("netease") || key.contains("wangyi") || key.contains("163") || key.contains("netapi")) {
            return "netease";
        }
        if (key.contains("qq") || key.contains("tencent")) {
            return "qq";
        }
        if (key.contains("kugou")) {
            return "kugou";
        }
        if (key.contains("kuwo")) {
            return "kuwo";
        }
        if (key.contains("baidu") || key.contains("taihe") || key.contains("qianqian")) {
            return "baidu";
        }
        return key;
    }

    private String normalize(String api) {
        if (api == null) {
            return null;
        }
        return api.trim().toLowerCase(Locale.ROOT);
    }
}
