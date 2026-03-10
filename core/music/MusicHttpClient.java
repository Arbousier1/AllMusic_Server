package com.coloryr.allmusic.server.core.music;

import com.coloryr.allmusic.server.core.AllMusic;
import com.coloryr.allmusic.server.core.objs.CookieObj;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.cookie.BasicCookieStore;
import org.apache.hc.client5.http.cookie.Cookie;
import org.apache.hc.client5.http.cookie.CookieStore;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.cookie.BasicClientCookie;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.util.Timeout;

import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

public class MusicHttpClient {
    public static final String UserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36 Edg/145.0.0.0";
    private static final int CONNECT_TIMEOUT = 5;
    private static final int READ_TIMEOUT = 7;
    public static CloseableHttpClient client;

    public static void init() {
        try {
            synchronized (com.coloryr.allmusic.server.netapi.NetApiHttpClient.class) {
                RequestConfig requestConfig = RequestConfig.custom()
                        .setConnectTimeout(Timeout.ofSeconds(CONNECT_TIMEOUT))
                        .setResponseTimeout(Timeout.ofSeconds(READ_TIMEOUT))
                        .build();
                client = HttpClients.custom()
                        .setDefaultRequestConfig(requestConfig)
                        .build();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static CookieStore createCookieStore() {
        BasicCookieStore cookieStore = new BasicCookieStore();
        if (AllMusic.cookie == null) {
            return cookieStore;
        }
        for (CookieObj cookie : AllMusic.cookie) {
            if (cookie == null || cookie.name == null || cookie.value == null || cookie.domain == null) {
                continue;
            }
            BasicClientCookie cookie1 = new BasicClientCookie(cookie.name, cookie.value);
            cookie1.setExpiryDate(Instant.MAX);
            cookie1.setDomain(cookie.domain);
            cookie1.setPath(cookie.path == null || cookie.path.isEmpty() ? "/" : cookie.path);
            cookie1.setHttpOnly(cookie.httpOnly);
            cookieStore.addCookie(cookie1);
        }
        return cookieStore;
    }

    public static void saveCookies(CookieStore cookieStore) {
        List<Cookie> cookies = cookieStore.getCookies();
        List<CookieObj> list = new ArrayList<>();
        for (Cookie cookie : cookies) {
            CookieObj obj = new CookieObj();
            obj.domain = cookie.getDomain();
            obj.hostOnly = false;
            obj.httpOnly = cookie.isHttpOnly();
            obj.name = cookie.getName();
            obj.path = cookie.getPath();
            obj.value = cookie.getValue();
            list.add(obj);
        }
        AllMusic.cookie = list;
        AllMusic.saveCookie();
    }

    public static void importCookieHeader(String cookieHeader, String... domains) {
        if (cookieHeader == null || cookieHeader.trim().isEmpty() || domains == null || domains.length == 0) {
            return;
        }

        List<CookieObj> list = AllMusic.cookie == null ? new ArrayList<>() : new ArrayList<>(AllMusic.cookie);
        String[] items = cookieHeader.split(";");
        for (String item : items) {
            if (item == null) {
                continue;
            }
            String text = item.trim();
            if (text.isEmpty()) {
                continue;
            }

            int index = text.indexOf('=');
            if (index <= 0) {
                continue;
            }

            String name = text.substring(0, index).trim();
            String value = text.substring(index + 1).trim();
            if (name.isEmpty()) {
                continue;
            }

            for (String domain : domains) {
                upsertCookie(list, domain, name, value);
            }
        }

        AllMusic.cookie = list;
        AllMusic.saveCookie();
    }

    public static String buildCookieHeader(String host) {
        if (host == null || host.isEmpty() || AllMusic.cookie == null) {
            return null;
        }

        StringBuilder builder = new StringBuilder();
        String host1 = host.toLowerCase(Locale.ROOT);
        for (CookieObj item : AllMusic.cookie) {
            if (item == null || item.name == null || item.value == null || item.domain == null) {
                continue;
            }
            if (!matchDomain(host1, item.domain)) {
                continue;
            }
            if (builder.length() > 0) {
                builder.append("; ");
            }
            builder.append(item.name).append("=").append(item.value);
        }

        return builder.length() == 0 ? null : builder.toString();
    }

    public static String getCookieValue(String host, String... names) {
        if (AllMusic.cookie == null || names == null) {
            return null;
        }

        String host1 = host == null ? null : host.toLowerCase(Locale.ROOT);
        for (String name : names) {
            if (name == null) {
                continue;
            }
            for (CookieObj item : AllMusic.cookie) {
                if (item == null || item.name == null || item.value == null) {
                    continue;
                }
                if (!item.name.equalsIgnoreCase(name)) {
                    continue;
                }
                if (host1 != null && item.domain != null && !matchDomain(host1, item.domain)) {
                    continue;
                }
                return item.value;
            }
        }

        return null;
    }

    private static boolean matchDomain(String host, String domain) {
        if (host == null || domain == null || domain.isEmpty()) {
            return false;
        }

        String domain1 = domain.toLowerCase(Locale.ROOT);
        while (domain1.startsWith(".")) {
            domain1 = domain1.substring(1);
        }
        return host.equals(domain1) || host.endsWith("." + domain1);
    }

    private static void upsertCookie(List<CookieObj> list, String domain, String name, String value) {
        if (list == null || domain == null || name == null) {
            return;
        }

        for (Iterator<CookieObj> iterator = list.iterator(); iterator.hasNext(); ) {
            CookieObj item = iterator.next();
            if (item == null || item.domain == null || item.name == null) {
                continue;
            }
            if (item.domain.equalsIgnoreCase(domain) && item.name.equalsIgnoreCase(name)) {
                iterator.remove();
            }
        }

        CookieObj obj = new CookieObj();
        obj.domain = domain;
        obj.hostOnly = false;
        obj.httpOnly = false;
        obj.path = "/";
        obj.name = name;
        obj.value = value;
        list.add(obj);
    }

    public static InputStream get(String path) {
        try {
            HttpGet request = new HttpGet(path);
            request.setHeader("user-agent", UserAgent);
            HttpClientContext context = HttpClientContext.create();
            CookieStore cookieStore = createCookieStore();
            context.setCookieStore(cookieStore);
            CloseableHttpResponse response = client.execute(request, context);
            int httpCode = response.getCode();
            HttpEntity entity = response.getEntity();
            if (entity == null) {
                AllMusic.log.data("<light_purple>[AllMusic3]<red>获取网页错误");
                return null;
            }
            InputStream inputStream = entity.getContent();
            boolean ok = httpCode == 200;
            if (!ok) {
                EntityUtils.consume(entity);
                inputStream.close();
                response.close();
                return null;
            }
            // 保存 cookies
            saveCookies(cookieStore);
            // 注意：需要调用者关闭 InputStream
            return inputStream;
        } catch (Exception e) {
            AllMusic.log.data("<light_purple>[AllMusic3]<red>获取网页错误");
            e.printStackTrace();
        }
        return null;
    }
}
