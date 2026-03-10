package com.coloryr.allmusic.server.core.utils;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class CookieImportApi {
    private static final Target QQ = new Target(
            "qq",
            "QQ Music",
            "https://y.qq.com",
            new String[]{"y.qq.com", ".y.qq.com", "u.y.qq.com", "c.y.qq.com", ".qq.com"},
            new String[]{"%.qq.com", "%.y.qq.com", "y.qq.com", "c.y.qq.com", "u.y.qq.com"}
    );
    private static final Target NETEASE = new Target(
            "netease",
            "NetEase Cloud Music",
            "https://music.163.com",
            new String[]{"music.163.com", ".music.163.com", "interface3.music.163.com", ".163.com"},
            new String[]{"%.163.com", "%.music.163.com", "music.163.com", "interface3.music.163.com"}
    );
    private static final Target KUGOU = new Target(
            "kugou",
            "Kugou Music",
            "https://www.kugou.com",
            new String[]{"www.kugou.com", ".kugou.com", "m.kugou.com", "mobilecdn.kugou.com", "wwwapi.kugou.com"},
            new String[]{"%.kugou.com", "www.kugou.com", "m.kugou.com", "mobilecdn.kugou.com", "wwwapi.kugou.com"}
    );
    private static final Target KUWO = new Target(
            "kuwo",
            "Kuwo Music",
            "https://www.kuwo.cn",
            new String[]{"www.kuwo.cn", ".kuwo.cn", "m.kuwo.cn"},
            new String[]{"%.kuwo.cn", "www.kuwo.cn", "m.kuwo.cn"}
    );
    private static final Target BAIDU = new Target(
            "baidu",
            "Baidu Music",
            "https://music.91q.com",
            new String[]{"musicapi.taihe.com", ".taihe.com", ".qianqian.com"},
            new String[]{"%.taihe.com", "%.qianqian.com", "musicapi.taihe.com"}
    );

    private CookieImportApi() {
    }

    public static List<String> listApis() {
        return Arrays.asList("netease", "qq", "kugou", "kuwo", "baidu");
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

    public static Target resolve(String api) {
        String value = normalizeApi(api);
        if ("qq".equals(value)) {
            return QQ;
        }
        if ("netease".equals(value)) {
            return NETEASE;
        }
        if ("kugou".equals(value)) {
            return KUGOU;
        }
        if ("kuwo".equals(value)) {
            return KUWO;
        }
        if ("baidu".equals(value)) {
            return BAIDU;
        }
        return null;
    }

    public static final class Target {
        public final String id;
        public final String displayName;
        public final String siteUrl;
        private final String[] importDomains;
        private final String[] sqlPatterns;

        private Target(String id, String displayName, String siteUrl, String[] importDomains, String[] sqlPatterns) {
            this.id = id;
            this.displayName = displayName;
            this.siteUrl = siteUrl;
            this.importDomains = importDomains;
            this.sqlPatterns = sqlPatterns;
        }

        public String[] getImportDomains() {
            return importDomains.clone();
        }

        public String[] getSqlPatterns() {
            return sqlPatterns.clone();
        }
    }
}
