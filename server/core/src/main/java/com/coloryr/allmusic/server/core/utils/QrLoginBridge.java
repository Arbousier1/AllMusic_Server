package com.coloryr.allmusic.server.core.utils;

import com.coloryr.allmusic.server.core.AllMusic;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class QrLoginBridge {
    private static final long SESSION_TTL = 300_000L;
    private static final long POLL_INTERVAL_MS = 2000L;
    private static final int QR_SIZE = 256;
    private static final Object LOCK = new Object();
    private static final Map<String, QrSession> SESSIONS = new ConcurrentHashMap<String, QrSession>();
    private static HttpServer server;
    private static ScheduledExecutorService pollExecutor;
    private static int port = -1;

    private QrLoginBridge() {
    }

    public static SessionHandle start(Object sender, String api) throws Exception {
        CookieImportApi.Target target = CookieImportApi.resolve(api);
        if (target == null) {
            throw new IllegalArgumentException("Unsupported api: " + api);
        }

        NeteaseQrLoginHandler.Result result = NeteaseQrLoginHandler.requestUnikey();
        if (result == null) {
            throw new RuntimeException("Failed to request QR login key from NetEase API");
        }

        cleanupExpiredSessions();
        ensureServer();

        String token = UUID.randomUUID().toString().replace("-", "");
        byte[] qrPng = generateQrPng(result.qrContent);

        QrSession session = new QrSession(token, sender, target, result.unikey,
                result.qrContent, qrPng, System.currentTimeMillis() + SESSION_TTL);
        SESSIONS.put(token, session);

        startPolling(session);

        return new SessionHandle(target, "http://127.0.0.1:" + port + "/qrlogin?token=" + token);
    }

    public static void stop() {
        synchronized (LOCK) {
            if (pollExecutor != null && !pollExecutor.isShutdown()) {
                pollExecutor.shutdownNow();
                pollExecutor = null;
            }
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
            server.createContext("/qrlogin", new HttpHandler() {
                @Override
                public void handle(HttpExchange exchange) {
                    handleLoginPage(exchange);
                }
            });
            server.createContext("/qrlogin/qr.png", new HttpHandler() {
                @Override
                public void handle(HttpExchange exchange) {
                    handleQrImage(exchange);
                }
            });
            server.createContext("/qrlogin/status", new HttpHandler() {
                @Override
                public void handle(HttpExchange exchange) {
                    handlePollStatus(exchange);
                }
            });
            server.setExecutor(Executors.newCachedThreadPool());
            server.start();
            port = server.getAddress().getPort();
            pollExecutor = Executors.newSingleThreadScheduledExecutor();
        }
    }

    private static byte[] generateQrPng(String content) throws Exception {
        Map<EncodeHintType, Object> hints = new HashMap<EncodeHintType, Object>();
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
        hints.put(EncodeHintType.MARGIN, 1);
        QRCodeWriter writer = new QRCodeWriter();
        BitMatrix matrix = writer.encode(content, BarcodeFormat.QR_CODE, QR_SIZE, QR_SIZE, hints);
        BufferedImage image = MatrixToImageWriter.toBufferedImage(matrix);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ImageIO.write(image, "PNG", baos);
        return baos.toByteArray();
    }

    private static void handleLoginPage(HttpExchange exchange) {
        try {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                writeResponse(exchange, 405, "text/plain; charset=UTF-8", "Method not allowed");
                return;
            }

            QrSession session = findSession(exchange);
            if (session == null) {
                writeResponse(exchange, 404, "text/html; charset=UTF-8",
                        "<!doctype html><html><head><meta charset=\"utf-8\"><title>AllMusic QR Login</title></head><body>"
                                + "<h1>Session expired</h1><p>Please run /music qrlogin again.</p></body></html>");
                return;
            }

            writeResponse(exchange, 200, "text/html; charset=UTF-8", buildLoginPage(session));
        } catch (Exception e) {
            safeWriteFailure(exchange, e.getMessage());
        }
    }

    private static void handleQrImage(HttpExchange exchange) {
        try {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                writeResponse(exchange, 405, "text/plain; charset=UTF-8", "Method not allowed");
                return;
            }

            QrSession session = findSession(exchange);
            if (session == null) {
                writeResponse(exchange, 404, "text/plain; charset=UTF-8", "Session not found");
                return;
            }

            exchange.getResponseHeaders().set("Content-Type", "image/png");
            exchange.getResponseHeaders().set("Cache-Control", "no-cache");
            exchange.sendResponseHeaders(200, session.qrPng.length);
            OutputStream out = exchange.getResponseBody();
            try {
                out.write(session.qrPng);
            } finally {
                out.close();
            }
        } catch (Exception e) {
            safeWriteFailure(exchange, e.getMessage());
        }
    }

    private static void handlePollStatus(HttpExchange exchange) {
        try {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                writeResponse(exchange, 405, "application/json; charset=UTF-8", "{\"error\":\"Method not allowed\"}");
                return;
            }

            QrSession session = findSession(exchange);
            if (session == null) {
                writeResponse(exchange, 404, "application/json; charset=UTF-8", "{\"status\":\"expired\"}");
                return;
            }

            String json;
            if (session.nickname != null) {
                json = "{\"status\":\"" + session.status.name().toLowerCase()
                        + "\",\"nickname\":\"" + escapeJson(session.nickname) + "\"}";
            } else {
                json = "{\"status\":\"" + session.status.name().toLowerCase() + "\"}";
            }
            writeResponse(exchange, 200, "application/json; charset=UTF-8", json);
        } catch (Exception e) {
            safeWriteFailure(exchange, e.getMessage());
        }
    }

    private static QrSession findSession(HttpExchange exchange) {
        cleanupExpiredSessions();
        String query = exchange.getRequestURI().getRawQuery();
        Map<String, String> params = parseQuery(query);
        String token = params.get("token");
        if (token == null || token.isEmpty()) {
            return null;
        }
        QrSession session = SESSIONS.get(token);
        if (session == null || session.expireAt < System.currentTimeMillis()) {
            SESSIONS.remove(token);
            return null;
        }
        return session;
    }

    private static void cleanupExpiredSessions() {
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<String, QrSession>> iterator = SESSIONS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, QrSession> item = iterator.next();
            if (item.getValue().expireAt < now) {
                iterator.remove();
            }
        }
    }

    private static void startPolling(final QrSession session) {
        pollExecutor.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                try {
                    if (session.pollingDone) {
                        return;
                    }
                    long now = System.currentTimeMillis();
                    if (now >= session.expireAt) {
                        session.status = NeteaseQrLoginHandler.Status.EXPIRED;
                        session.pollingDone = true;
                        SESSIONS.remove(session.token);
                        AllMusic.side.sendMessageTask(session.sender,
                                "<light_purple>[AllMusic3]<red>QR login session expired for "
                                        + session.target.displayName);
                        return;
                    }

                    NeteaseQrLoginHandler.PollResult result =
                            NeteaseQrLoginHandler.pollStatus(session.unikey);
                    if (result == null) {
                        return;
                    }

                    session.status = result.status;

                    if (result.status == NeteaseQrLoginHandler.Status.SCANNED && result.nickname != null) {
                        session.nickname = result.nickname;
                    }

                    if (result.status == NeteaseQrLoginHandler.Status.CONFIRMED) {
                        session.pollingDone = true;
                        SESSIONS.remove(session.token);
                        AllMusic.side.sendMessageTask(session.sender,
                                "<light_purple>[AllMusic3]<dark_green>QR login successful for "
                                        + session.target.displayName
                                        + ". Cookies have been saved to cookie.json.");
                        AllMusic.log.data("<light_purple>[AllMusic3]<dark_green>QR login completed for "
                                + session.target.id);
                    } else if (result.status == NeteaseQrLoginHandler.Status.EXPIRED) {
                        session.pollingDone = true;
                        SESSIONS.remove(session.token);
                        AllMusic.side.sendMessageTask(session.sender,
                                "<light_purple>[AllMusic3]<red>QR code expired for "
                                        + session.target.displayName
                                        + ". Please run /music qrlogin again.");
                    }
                } catch (Exception e) {
                    AllMusic.log.data("<light_purple>[AllMusic3]<red>QR polling error");
                    e.printStackTrace();
                }
            }
        }, POLL_INTERVAL_MS, POLL_INTERVAL_MS, TimeUnit.MILLISECONDS);
    }

    private static String buildLoginPage(QrSession session) {
        String imageUrl = "http://127.0.0.1:" + port + "/qrlogin/qr.png?token=" + session.token;
        String statusUrl = "http://127.0.0.1:" + port + "/qrlogin/status?token=" + session.token;
        StringBuilder builder = new StringBuilder(4096);
        builder.append("<!doctype html><html><head><meta charset=\"utf-8\">")
                .append("<title>AllMusic QR Login - ").append(escapeHtml(session.target.displayName)).append("</title>")
                .append("<style>")
                .append("*{margin:0;padding:0;box-sizing:border-box;}")
                .append("body{font-family:Segoe UI,Arial,sans-serif;display:flex;justify-content:center;align-items:center;min-height:100vh;background:#0f172a;color:#e2e8f0;}")
                .append(".card{background:#1e293b;border-radius:12px;padding:32px;text-align:center;max-width:400px;width:100%;box-shadow:0 25px 50px -12px rgba(0,0,0,0.5);}")
                .append("h1{font-size:20px;margin-bottom:8px;}")
                .append(".target{color:#94a3b8;margin-bottom:24px;font-size:14px;}")
                .append(".qr-box{background:#fff;padding:16px;border-radius:8px;display:inline-block;margin-bottom:20px;}")
                .append(".qr-box img{display:block;width:256px;height:256px;}")
                .append("#status{padding:12px 16px;border-radius:8px;font-size:14px;margin-bottom:16px;}")
                .append(".status-waiting{background:#1e40af;color:#bfdbfe;}")
                .append(".status-scanned{background:#854d0e;color:#fef3c7;}")
                .append(".status-confirmed{background:#14532d;color:#bbf7d0;}")
                .append(".status-expired{background:#7f1d1d;color:#fecaca;}")
                .append(".status-error{background:#7f1d1d;color:#fecaca;}")
                .append(".hint{font-size:12px;color:#64748b;margin-top:16px;}")
                .append("</style></head><body>")
                .append("<div class=\"card\">")
                .append("<h1>AllMusic QR Login</h1>")
                .append("<p class=\"target\">").append(escapeHtml(session.target.displayName)).append("</p>")
                .append("<div class=\"qr-box\"><img id=\"qr\" src=\"").append(escapeHtml(imageUrl)).append("\" alt=\"QR Code\"></div>")
                .append("<div id=\"status\" class=\"status-waiting\">Waiting for scan...</div>")
                .append("<p class=\"hint\">Open the NetEase Music app on your phone and scan the QR code.<br>This session expires in 5 minutes.</p>")
                .append("</div>")
                .append("<script>")
                .append("(function(){")
                .append("var statusEl=document.getElementById('status');")
                .append("function poll(){")
                .append("fetch('").append(escapeJs(statusUrl)).append("')")
                .append(".then(function(r){return r.json();})")
                .append(".then(function(data){")
                .append("if(data.status==='waiting'){")
                .append("statusEl.textContent='Waiting for scan...';")
                .append("statusEl.className='status-waiting';")
                .append("setTimeout(poll,2000);")
                .append("}else if(data.status==='scanned'){")
                .append("statusEl.textContent='Scanned'+(data.nickname?' by '+data.nickname:'')+'! Confirm in the app...';")
                .append("statusEl.className='status-scanned';")
                .append("setTimeout(poll,2000);")
                .append("}else if(data.status==='confirmed'){")
                .append("statusEl.textContent='Login successful! Cookies saved. You can close this page.';")
                .append("statusEl.className='status-confirmed';")
                .append("}else if(data.status==='expired'){")
                .append("statusEl.textContent='QR code expired. Please run /music qrlogin again.';")
                .append("statusEl.className='status-expired';")
                .append("}else{")
                .append("statusEl.textContent='Connection error. Retrying...';")
                .append("statusEl.className='status-error';")
                .append("setTimeout(poll,3000);")
                .append("}")
                .append("})")
                .append(".catch(function(){")
                .append("statusEl.textContent='Connection lost. Retrying...';")
                .append("statusEl.className='status-error';")
                .append("setTimeout(poll,3000);")
                .append("});")
                .append("}")
                .append("setTimeout(poll,2000);")
                .append("})();")
                .append("</script></body></html>");
        return builder.toString();
    }

    private static Map<String, String> parseQuery(String raw) {
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
            map.put(key, value);
        }
        return map;
    }

    private static void safeWriteFailure(HttpExchange exchange, String message) {
        try {
            writeResponse(exchange, 500, "text/html; charset=UTF-8",
                    "<!doctype html><html><head><meta charset=\"utf-8\"><title>Error</title></head><body>"
                            + "<h1>QR Login Error</h1><p>" + escapeHtml(message) + "</p></body></html>");
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

    private static String escapeJson(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private static final class QrSession {
        final String token;
        final Object sender;
        final CookieImportApi.Target target;
        final String unikey;
        final String qrContent;
        final byte[] qrPng;
        final long expireAt;
        volatile NeteaseQrLoginHandler.Status status = NeteaseQrLoginHandler.Status.WAITING;
        volatile boolean pollingDone;
        volatile String nickname;

        private QrSession(String token, Object sender, CookieImportApi.Target target,
                          String unikey, String qrContent, byte[] qrPng, long expireAt) {
            this.token = token;
            this.sender = sender;
            this.target = target;
            this.unikey = unikey;
            this.qrContent = qrContent;
            this.qrPng = qrPng;
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
