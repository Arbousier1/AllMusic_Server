package com.coloryr.allmusic.server.core.utils;

import com.coloryr.allmusic.server.core.AllMusic;
import com.coloryr.allmusic.server.core.objs.CookieObj;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.sql.*;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class WindowsCookieImporter {
    private static final Type COOKIE_LIST_TYPE = new TypeToken<ArrayList<CookieObj>>() {
    }.getType();
    private WindowsCookieImporter() {
    }

    public static String normalizeApi(String api) {
        String value = api == null ? "" : api.trim().toLowerCase(Locale.ROOT);
        if (value.startsWith("qq") || value.startsWith("tencent")) {
            return "qq";
        }
        if (value.startsWith("wy") || value.startsWith("163") || value.startsWith("netease")) {
            return "netease";
        }
        if (value.startsWith("kg") || value.startsWith("kugou")) {
            return "kugou";
        }
        if (value.startsWith("kw") || value.startsWith("kuwo")) {
            return "kuwo";
        }
        if (value.startsWith("bd") || value.startsWith("baidu") || value.startsWith("taihe")
                || value.startsWith("qianqian")) {
            return "baidu";
        }
        return value;
    }

    public static List<CookieObj> importToFile(String api, File cookieFile) throws Exception {
        String normalized = normalizeApi(api);
        if (!isWindows()) {
            throw new IOException("Windows only");
        }

        BrowserTarget target = resolveTarget(normalized);
        if (target == null) {
            throw new IOException("Unsupported api: " + api);
        }

        List<CookieObj> list = readCookieFile(cookieFile);
        List<CookieObj> imported = readChromiumCookies(target);
        if (imported.isEmpty()) {
            throw new IOException("No browser cookies found for " + normalized);
        }

        mergeCookies(list, imported);
        writeCookieFile(cookieFile, list);
        return imported;
    }

    public static List<CookieObj> readCookieFile(File cookieFile) throws Exception {
        if (cookieFile == null || !cookieFile.exists()) {
            return new ArrayList<CookieObj>();
        }
        Reader reader = null;
        try {
            reader = new InputStreamReader(new FileInputStream(cookieFile), StandardCharsets.UTF_8);
            List<CookieObj> list = AllMusic.gson.fromJson(reader, COOKIE_LIST_TYPE);
            return list == null ? new ArrayList<CookieObj>() : new ArrayList<CookieObj>(list);
        } finally {
            if (reader != null) {
                reader.close();
            }
        }
    }

    public static void writeCookieFile(File cookieFile, List<CookieObj> list) throws Exception {
        Writer writer = null;
        try {
            writer = new OutputStreamWriter(new FileOutputStream(cookieFile), StandardCharsets.UTF_8);
            writer.write(AllMusic.gson.toJson(list));
        } finally {
            if (writer != null) {
                writer.close();
            }
        }
    }

    private static List<CookieObj> readChromiumCookies(BrowserTarget target) throws Exception {
        List<CookieObj> list = new ArrayList<CookieObj>();
        for (BrowserProfile browser : getBrowsers()) {
            if (!browser.userDataDir.exists() || !browser.localState.exists()) {
                continue;
            }
            byte[] masterKey = readMasterKey(browser.localState);
            for (File profile : getProfiles(browser.userDataDir)) {
                File db = new File(profile, "Network\\Cookies");
                if (!db.exists()) {
                    db = new File(profile, "Cookies");
                }
                if (!db.exists()) {
                    continue;
                }
                readBrowserDb(list, db, masterKey, target);
            }
        }
        return list;
    }

    private static void readBrowserDb(List<CookieObj> list, File db, byte[] masterKey, BrowserTarget target) throws Exception {
        File temp = copyTemp(db);
        Connection connection = null;
        PreparedStatement statement = null;
        ResultSet set = null;
        try {
            Class.forName("org.sqlite.JDBC");
            connection = DriverManager.getConnection("jdbc:sqlite:" + temp.getAbsolutePath());
            StringBuilder sql = new StringBuilder("SELECT host_key,path,name,encrypted_value,value,is_httponly FROM cookies WHERE ");
            for (int i = 0; i < target.domains.length; i++) {
                if (i > 0) {
                    sql.append(" OR ");
                }
                sql.append("host_key LIKE ?");
            }
            statement = connection.prepareStatement(sql.toString());
            for (int i = 0; i < target.domains.length; i++) {
                statement.setString(i + 1, target.domains[i]);
            }
            set = statement.executeQuery();
            while (set.next()) {
                String value = set.getString(5);
                byte[] encrypted = set.getBytes(4);
                if ((value == null || value.isEmpty()) && encrypted != null && encrypted.length > 0) {
                    value = decryptCookie(encrypted, masterKey);
                }
                if (value == null || value.isEmpty()) {
                    continue;
                }
                CookieObj item = new CookieObj();
                item.domain = set.getString(1);
                item.path = set.getString(2);
                item.name = set.getString(3);
                item.value = value;
                item.httpOnly = set.getInt(6) == 1;
                item.hostOnly = item.domain != null && !item.domain.startsWith(".");
                upsert(list, item);
            }
        } finally {
            if (set != null) {
                set.close();
            }
            if (statement != null) {
                statement.close();
            }
            if (connection != null) {
                connection.close();
            }
            temp.delete();
        }
    }

    private static File copyTemp(File source) throws Exception {
        File temp = File.createTempFile("am_cookie_", ".db");
        temp.deleteOnExit();
        java.nio.file.Files.copy(source.toPath(), temp.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        return temp;
    }

    private static byte[] readMasterKey(File localState) throws Exception {
        Reader reader = null;
        try {
            reader = new InputStreamReader(new FileInputStream(localState), StandardCharsets.UTF_8);
            JsonObject root = new JsonParser().parse(reader).getAsJsonObject();
            String key = root.getAsJsonObject("os_crypt").get("encrypted_key").getAsString();
            byte[] data = Base64.getDecoder().decode(key);
            byte[] raw = new byte[data.length - 5];
            System.arraycopy(data, 5, raw, 0, raw.length);
            return dpapiUnprotect(raw);
        } finally {
            if (reader != null) {
                reader.close();
            }
        }
    }

    private static String decryptCookie(byte[] encrypted, byte[] masterKey) throws Exception {
        if (encrypted == null || encrypted.length == 0) {
            return null;
        }
        if (startsWith(encrypted, "v10") || startsWith(encrypted, "v11")) {
            byte[] nonce = new byte[12];
            System.arraycopy(encrypted, 3, nonce, 0, nonce.length);
            byte[] payload = new byte[encrypted.length - 15];
            System.arraycopy(encrypted, 15, payload, 0, payload.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(masterKey, "AES"), new GCMParameterSpec(128, nonce));
            return new String(cipher.doFinal(payload), StandardCharsets.UTF_8);
        }
        return new String(dpapiUnprotect(encrypted), StandardCharsets.UTF_8);
    }

    private static boolean startsWith(byte[] data, String prefix) {
        byte[] raw = prefix.getBytes(StandardCharsets.UTF_8);
        if (data.length < raw.length) {
            return false;
        }
        for (int i = 0; i < raw.length; i++) {
            if (data[i] != raw[i]) {
                return false;
            }
        }
        return true;
    }

    private static byte[] dpapiUnprotect(byte[] data) throws Exception {
        String input = Base64.getEncoder().encodeToString(data);
        String command = "[Convert]::ToBase64String([System.Security.Cryptography.ProtectedData]::Unprotect([Convert]::FromBase64String('"
                + input + "'), $null, [System.Security.Cryptography.DataProtectionScope]::CurrentUser))";
        ProcessBuilder builder = new ProcessBuilder("powershell.exe", "-NoProfile", "-NonInteractive",
                "-ExecutionPolicy", "Bypass", "-Command", command);
        builder.redirectErrorStream(true);
        Process process = builder.start();
        String output = readText(process.getInputStream()).trim();
        int code = process.waitFor();
        if (code != 0 || output.isEmpty()) {
            throw new IOException("DPAPI decrypt failed: " + output);
        }
        return Base64.getDecoder().decode(output);
    }

    private static String readText(InputStream stream) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int len;
        while ((len = stream.read(buffer)) > 0) {
            out.write(buffer, 0, len);
        }
        return out.toString(StandardCharsets.UTF_8.name());
    }

    private static List<File> getProfiles(File userDataDir) {
        List<File> list = new ArrayList<File>();
        File[] files = userDataDir.listFiles();
        if (files == null) {
            return list;
        }
        for (File file : files) {
            if (!file.isDirectory()) {
                continue;
            }
            String name = file.getName();
            if ("Default".equals(name) || name.startsWith("Profile ")) {
                list.add(file);
            }
        }
        return list;
    }

    private static List<BrowserProfile> getBrowsers() {
        List<BrowserProfile> list = new ArrayList<BrowserProfile>();
        String local = System.getenv("LOCALAPPDATA");
        if (local == null || local.isEmpty()) {
            return list;
        }
        list.add(new BrowserProfile(new File(local, "Microsoft\\Edge\\User Data")));
        list.add(new BrowserProfile(new File(local, "Google\\Chrome\\User Data")));
        return list;
    }

    private static BrowserTarget resolveTarget(String api) {
        if ("qq".equals(api)) {
            return new BrowserTarget("%.qq.com", "%.y.qq.com", "y.qq.com", "c.y.qq.com", "u.y.qq.com");
        }
        if ("netease".equals(api)) {
            return new BrowserTarget("%.163.com", "%.music.163.com", "music.163.com", "interface3.music.163.com");
        }
        if ("kugou".equals(api)) {
            return new BrowserTarget("%.kugou.com", "www.kugou.com", "m.kugou.com", "mobilecdn.kugou.com", "wwwapi.kugou.com");
        }
        if ("kuwo".equals(api)) {
            return new BrowserTarget("%.kuwo.cn", "www.kuwo.cn", "m.kuwo.cn");
        }
        if ("baidu".equals(api)) {
            return new BrowserTarget("%.taihe.com", "%.qianqian.com", "musicapi.taihe.com");
        }
        return null;
    }

    private static void mergeCookies(List<CookieObj> base, List<CookieObj> imported) {
        for (CookieObj item : imported) {
            upsert(base, item);
        }
    }

    private static void upsert(List<CookieObj> list, CookieObj item) {
        for (int i = 0; i < list.size(); i++) {
            CookieObj old = list.get(i);
            if (old == null || old.name == null || old.domain == null) {
                continue;
            }
            if (old.name.equalsIgnoreCase(item.name)
                    && old.domain.equalsIgnoreCase(item.domain)
                    && samePath(old.path, item.path)) {
                list.remove(i);
                break;
            }
        }
        list.add(item);
    }

    private static boolean samePath(String a, String b) {
        String a1 = a == null || a.isEmpty() ? "/" : a;
        String b1 = b == null || b.isEmpty() ? "/" : b;
        return a1.equals(b1);
    }

    private static boolean isWindows() {
        String value = System.getProperty("os.name");
        return value != null && value.toLowerCase(Locale.ROOT).contains("win");
    }
    private static final class BrowserProfile {
        private final File userDataDir;
        private final File localState;

        private BrowserProfile(File userDataDir) {
            this.userDataDir = userDataDir;
            this.localState = new File(userDataDir, "Local State");
        }
    }

    private static final class BrowserTarget {
        private final String[] domains;

        private BrowserTarget(String... domains) {
            this.domains = domains;
        }
    }
}
