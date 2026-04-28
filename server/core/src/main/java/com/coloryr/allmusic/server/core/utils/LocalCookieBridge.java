package com.coloryr.allmusic.server.core.utils;

import com.coloryr.allmusic.server.core.AllMusic;
import com.coloryr.allmusic.server.core.music.MusicHttpClient;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

public final class LocalCookieBridge {
    private static final long SESSION_TTL = 10L * 60L * 1000L;
    private static final Object LOCK = new Object();
    private static final Map<String, Session> SESSIONS = new ConcurrentHashMap<String, Session>();
    private static HttpServer server;
    private static int port = -1;

    private LocalCookieBridge() {
    }

    public static SessionHandle start(Object sender, String api) throws Exception {
        CookieImportApi.Target target = CookieImportApi.resolve(api);
        if (target == null) {
            throw new IllegalArgumentException("Unsupported import api");
        }

        cleanupExpiredSessions();
        ensureServer();

        String token = UUID.randomUUID().toString().replace("-", "");
        Session session = new Session(token, sender, target, System.currentTimeMillis() + SESSION_TTL);
        SESSIONS.put(token, session);
        return new SessionHandle(target, "http://127.0.0.1:" + port + "/import?token=" + token);
    }

    public static void stop() {
        synchronized (LOCK) {
            if (server != null) {
                server.stop(0);
                server = null;
                port = -1;
            }
            SESSIONS.clear();
        }
    }

    private static void ensureServer() throws Exception {
        synchronized (LOCK) {
            if (server != null) {
                return;
            }
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/import", new HttpHandler() {
                @Override
                public void handle(HttpExchange exchange) {
                    handleImport(exchange);
                }
            });
            server.createContext("/submit", new HttpHandler() {
                @Override
                public void handle(HttpExchange exchange) {
                    handleSubmit(exchange);
                }
            });
            server.setExecutor(Executors.newCachedThreadPool());
            server.start();
            port = server.getAddress().getPort();
        }
    }

    private static void handleImport(HttpExchange exchange) {
        try {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                writeResponse(exchange, 405, "text/plain; charset=UTF-8", "Method not allowed");
                return;
            }

            Session session = findSession(exchange);
            if (session == null) {
                writeResponse(exchange, 404, "text/plain; charset=UTF-8", "Cookie import session not found or expired");
                return;
            }

            writeResponse(exchange, 200, "text/html; charset=UTF-8", buildImportPage(session));
        } catch (Exception e) {
            safeWriteFailure(exchange, e.getMessage());
        }
    }

    private static void handleSubmit(HttpExchange exchange) {
        try {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                writeResponse(exchange, 405, "text/plain; charset=UTF-8", "Method not allowed");
                return;
            }

            Session session = findSession(exchange);
            if (session == null) {
                writeResponse(exchange, 404, "text/plain; charset=UTF-8", "Cookie import session not found or expired");
                return;
            }

            Map<String, String> form = parseForm(readBody(exchange.getRequestBody()));
            String cookie = normalizeCookieHeader(form.get("cookie"));
            if (cookie == null || cookie.isEmpty()) {
                writeResponse(exchange, 400, "text/html; charset=UTF-8",
                        buildResultPage("No script-visible cookie found on the current site. Make sure you are logged in and run the helper from the target music site."));
                return;
            }

            MusicHttpClient.importCookieHeader(cookie, session.target.getImportDomains());
            int count = countCookiePairs(cookie);
            SESSIONS.remove(session.token);
            AllMusic.side.sendMessageTask(session.sender,
                    "<light_purple>[AllMusic3]<dark_green>Browser cookie callback received for "
                            + session.target.id + ", imported " + count + " cookie entries into cookie.json");
            AllMusic.log.data("<light_purple>[AllMusic3]<dark_green>Browser cookie callback received for "
                    + session.target.id);
            writeResponse(exchange, 200, "text/html; charset=UTF-8",
                    buildResultPage("Cookie import completed for " + session.target.displayName
                            + ". You can return to Minecraft now."));
        } catch (Exception e) {
            safeWriteFailure(exchange, e.getMessage());
        }
    }

    private static Session findSession(HttpExchange exchange) {
        cleanupExpiredSessions();
        String query = exchange.getRequestURI().getRawQuery();
        Map<String, String> params = parseForm(query);
        String token = params.get("token");
        if (token == null || token.isEmpty()) {
            return null;
        }
        Session session = SESSIONS.get(token);
        if (session == null || session.expireAt < System.currentTimeMillis()) {
            SESSIONS.remove(token);
            return null;
        }
        return session;
    }

    private static void cleanupExpiredSessions() {
        long now = System.currentTimeMillis();
        for (Iterator<Map.Entry<String, Session>> iterator = SESSIONS.entrySet().iterator(); iterator.hasNext(); ) {
            Map.Entry<String, Session> item = iterator.next();
            if (item.getValue().expireAt < now) {
                SESSIONS.remove(item.getKey(), item.getValue());
            }
        }
    }

    private static String buildImportPage(Session session) {
        String submitUrl = "http://127.0.0.1:" + port + "/submit?token=" + session.token;
        String script = buildSubmitScript(submitUrl);
        String bookmarklet = "javascript:" + script;
        StringBuilder builder = new StringBuilder(2048);
        builder.append("<!doctype html><html><head><meta charset=\"utf-8\">")
                .append("<title>AllMusic Cookie Import</title>")
                .append("<style>")
                .append("body{font-family:Segoe UI,Arial,sans-serif;margin:32px;line-height:1.5;color:#1f2937;}")
                .append("code,textarea,input{font-family:Consolas,Monaco,monospace;}")
                .append("textarea{width:100%;min-height:120px;margin:8px 0 16px;padding:12px;box-sizing:border-box;}")
                .append("button{display:inline-block;padding:10px 14px;background:#111827;color:#fff;border:0;cursor:pointer;}")
                .append(".box{border:1px solid #d1d5db;padding:16px;margin:16px 0;}")
                .append("</style></head><body>")
                .append("<h1>AllMusic Cookie Import</h1>")
                .append("<p>Target: <strong>").append(escapeHtml(session.target.displayName)).append("</strong></p>")
                .append("<p>Open <a href=\"").append(escapeHtml(session.target.siteUrl)).append("\">")
                .append(escapeHtml(session.target.siteUrl)).append("</a> in this browser, log in, then submit the visible cookie back to AllMusic.</p>")
                .append("<div class=\"box\"><h2>Option 1: bookmarklet</h2>")
                .append("<p>Copy this text as a bookmark URL, then open the target site and click that bookmark.</p>")
                .append("<textarea id=\"bookmarklet\" readonly>")
                .append(escapeHtml(bookmarklet))
                .append("</textarea>")
                .append("<button type=\"button\" onclick=\"copyText('bookmarklet')\">Copy bookmarklet</button></div>")
                .append("<div class=\"box\"><h2>Option 2: console snippet</h2>")
                .append("<p>Open the target site, press F12, paste this into the browser console, then press Enter.</p>")
                .append("<textarea id=\"snippet\" readonly>")
                .append(escapeHtml(script))
                .append("</textarea>")
                .append("<button type=\"button\" onclick=\"copyText('snippet')\">Copy snippet</button></div>")
                .append("<div class=\"box\"><h2>Option 3: manual paste</h2>")
                .append("<p>If the script is blocked, paste the cookie header manually.</p>")
                .append("<form method=\"post\" action=\"").append(escapeHtml(submitUrl)).append("\">")
                .append("<textarea name=\"cookie\" placeholder=\"uin=...; skey=...;\"></textarea>")
                .append("<button type=\"submit\">Submit cookie</button></form></div>")
                .append("<p>Note: this helper can only capture cookies visible to browser JavaScript. HttpOnly cookies still need manual import from browser devtools.</p>")
                .append("<script>")
                .append("function copyText(id){var el=document.getElementById(id);el.focus();el.select();document.execCommand('copy');}")
                .append("</script></body></html>");
        return builder.toString();
    }

    private static String buildSubmitScript(String submitUrl) {
        return "(function(){var f=document.createElement('form');f.method='POST';f.action='"
                + escapeJs(submitUrl)
                + "';var c=document.createElement('textarea');c.name='cookie';c.value=document.cookie;f.appendChild(c);document.body.appendChild(f);f.submit();})();";
    }

    private static String buildResultPage(String message) {
        return "<!doctype html><html><head><meta charset=\"utf-8\"><title>AllMusic Cookie Import</title></head><body>"
                + "<p>" + escapeHtml(message) + "</p><p>You can close this page.</p></body></html>";
    }

    private static String normalizeCookieHeader(String cookie) {
        if (cookie == null) {
            return null;
        }
        String text = cookie.replace('\r', ' ').replace('\n', ' ').trim();
        while (text.contains("  ")) {
            text = text.replace("  ", " ");
        }
        return text;
    }

    private static int countCookiePairs(String cookie) {
        int count = 0;
        String[] items = cookie.split(";");
        for (String item : items) {
            if (item != null && item.contains("=")) {
                count++;
            }
        }
        return count;
    }

    private static Map<String, String> parseForm(String raw) {
        Map<String, String> map = new HashMap<String, String>();
        if (raw == null || raw.isEmpty()) {
            return map;
        }
        String[] items = raw.split("&");
        for (String item : items) {
            if (item == null || item.isEmpty()) {
                continue;
            }
            int index = item.indexOf('=');
            String key = index < 0 ? item : item.substring(0, index);
            String value = index < 0 ? "" : item.substring(index + 1);
            map.put(urlDecode(key), urlDecode(value));
        }
        return map;
    }

    private static String urlDecode(String value) {
        try {
            return URLDecoder.decode(value, StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            return value;
        }
    }

    private static String readBody(InputStream stream) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int len;
        while ((len = stream.read(buffer)) > 0) {
            out.write(buffer, 0, len);
        }
        return out.toString(StandardCharsets.UTF_8.name());
    }

    private static void safeWriteFailure(HttpExchange exchange, String message) {
        try {
            writeResponse(exchange, 500, "text/html; charset=UTF-8",
                    buildResultPage("Cookie import failed: " + (message == null ? "unknown error" : message)));
        } catch (Exception ignored) {
        }
    }

    private static void writeResponse(HttpExchange exchange, int code, String contentType, String body) throws Exception {
        byte[] data = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(code, data.length);
        OutputStream out = exchange.getResponseBody();
        try {
            out.write(data);
        } finally {
            out.close();
        }
    }

    private static String escapeHtml(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static String escapeJs(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\\", "\\\\").replace("'", "\\'");
    }

    private static final class Session {
        private final String token;
        private final Object sender;
        private final CookieImportApi.Target target;
        private final long expireAt;

        private Session(String token, Object sender, CookieImportApi.Target target, long expireAt) {
            this.token = token;
            this.sender = sender;
            this.target = target;
            this.expireAt = expireAt;
        }
    }

    public static final class SessionHandle {
        public final CookieImportApi.Target target;
        public final String url;

        private SessionHandle(CookieImportApi.Target target, String url) {
            this.target = target;
            this.url = url;
        }
    }
}
