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
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

public class MusicHttpClient {
    public static final String UserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36 Edg/145.0.0.0";
    private static final int CONNECT_TIMEOUT = 5;
    private static final int READ_TIMEOUT = 7;
    private static final long PERSISTENT_COOKIE_SENTINEL_MILLIS = 253402300799000L;
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
            if (cookie.expirationDate != null) {
                long millis = Math.round(cookie.expirationDate * 1000D);
                cookie1.setExpiryDate(new Date(millis));
            } else if (!Boolean.TRUE.equals(cookie.session)) {
                cookie1.setExpiryDate(new Date(PERSISTENT_COOKIE_SENTINEL_MILLIS));
            }
            cookie1.setDomain(cookie.domain);
            cookie1.setPath(cookie.path == null || cookie.path.isEmpty() ? "/" : cookie.path);
            cookie1.setHttpOnly(cookie.httpOnly);
            cookie1.setSecure(Boolean.TRUE.equals(cookie.secure));
            if (cookie.sameSite != null && !cookie.sameSite.isEmpty()) {
                cookie1.setAttribute("samesite", cookie.sameSite);
            }
            cookieStore.addCookie(cookie1);
        }
        return cookieStore;
    }

    public static void saveCookies(CookieStore cookieStore) {
        List<Cookie> cookies = cookieStore.getCookies();
        List<CookieObj> oldList = AllMusic.cookie == null ? new ArrayList<>() : new ArrayList<>(AllMusic.cookie);
        List<CookieObj> list = new ArrayList<>();
        for (Cookie cookie : cookies) {
            CookieObj obj = new CookieObj();
            obj.domain = cookie.getDomain();
            obj.path = cookie.getPath() == null || cookie.getPath().isEmpty() ? "/" : cookie.getPath();
            obj.name = cookie.getName();
            obj.value = cookie.getValue();
            CookieObj old = findCookie(oldList, obj.domain, obj.path, obj.name);
            obj.expirationDate = getExpirationDate(cookie, old);
            obj.hostOnly = old != null ? old.hostOnly : (obj.domain != null && !obj.domain.startsWith("."));
            obj.httpOnly = cookie.isHttpOnly();
            obj.sameSite = getCookieAttribute(cookie, "samesite", old == null ? null : old.sameSite);
            obj.secure = cookie.isSecure();
            obj.session = isSessionCookie(cookie, old);
            obj.storeId = old == null ? null : old.storeId;
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

    private static CookieObj findCookie(List<CookieObj> list, String domain, String path, String name) {
        if (list == null || domain == null || name == null) {
            return null;
        }
        String path1 = path == null || path.isEmpty() ? "/" : path;
        for (CookieObj item : list) {
            if (item == null || item.domain == null || item.name == null) {
                continue;
            }
            String itemPath = item.path == null || item.path.isEmpty() ? "/" : item.path;
            if (item.domain.equalsIgnoreCase(domain)
                    && item.name.equalsIgnoreCase(name)
                    && itemPath.equals(path1)) {
                return item;
            }
        }
        return null;
    }

    private static Double getExpirationDate(Cookie cookie, CookieObj old) {
        Date expiryDate = cookie.getExpiryDate();
        if (expiryDate != null && expiryDate.getTime() != PERSISTENT_COOKIE_SENTINEL_MILLIS) {
            return expiryDate.getTime() / 1000D;
        }
        return old == null ? null : old.expirationDate;
    }

    private static boolean isSessionCookie(Cookie cookie, CookieObj old) {
        if (cookie.isPersistent()) {
            return false;
        }
        if (old != null && old.session != null) {
            return old.session;
        }
        return old == null || old.expirationDate == null;
    }

    private static String getCookieAttribute(Cookie cookie, String name, String fallback) {
        String value = cookie.getAttribute(name);
        if (value == null || value.isEmpty()) {
            return fallback;
        }
        return value;
    }

    private static void upsertCookie(List<CookieObj> list, String domain, String name, String value) {
        if (list == null || domain == null || name == null) {
            return;
        }

        CookieObj oldValue = null;
        for (Iterator<CookieObj> iterator = list.iterator(); iterator.hasNext(); ) {
            CookieObj item = iterator.next();
            if (item == null || item.domain == null || item.name == null) {
                continue;
            }
            if (item.domain.equalsIgnoreCase(domain) && item.name.equalsIgnoreCase(name)) {
                oldValue = item;
                iterator.remove();
            }
        }

        CookieObj obj = new CookieObj();
        obj.domain = domain;
        obj.expirationDate = oldValue == null ? null : oldValue.expirationDate;
        obj.hostOnly = oldValue != null ? oldValue.hostOnly : false;
        obj.httpOnly = oldValue != null && oldValue.httpOnly;
        obj.path = "/";
        obj.sameSite = oldValue == null ? null : oldValue.sameSite;
        obj.secure = oldValue == null ? Boolean.FALSE : oldValue.secure;
        obj.session = oldValue == null ? Boolean.FALSE : oldValue.session;
        obj.storeId = oldValue == null ? null : oldValue.storeId;
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
