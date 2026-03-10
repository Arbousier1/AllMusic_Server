import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.zip.GZIPInputStream;

/**
 * QQ 音乐 API 网络连接检查
 */
public class QQMusicConnectivityTest {
    
    private static final String SEARCH_URL = "https://c.y.qq.com/soso/fcgi-bin/client_search_cp";
    private static final String DETAIL_URL = "https://c.y.qq.com/v8/fcg-bin/fcg_play_single_song.fcg";
    private static final String LYRIC_URL = "https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg";
    
    public static void main(String[] args) throws Exception {
        System.out.println("╔════════════════════════════════════════╗");
        System.out.println("║  QQ 音乐 API 网络连接性检查与诊断     ║");
        System.out.println("╚════════════════════════════════════════╝\n");
        
        try {
            // 测试网络连接
            System.out.println("【测试1】基本网络连接测试");
            testBasicConnectivity();
            
            // 测试搜索功能
            System.out.println("\n【测试2】搜索功能测试（五月天）");
            testSearch("五月天");
            
            // 测试歌曲详情
            System.out.println("\n【测试3】歌曲详情获取测试");
            testSongDetail();
            
            // 测试歌词获取
            System.out.println("\n【测试4】歌词获取测试");
            testLyricFetch();
            
        } catch (Exception e) {
            System.err.println("❌ 测试过程中出错: " + e.getMessage());
            e.printStackTrace();
        }
        
        System.out.println("\n╔════════════════════════════════════════╗");
        System.out.println("║          测试完成                      ║");
        System.out.println("╚════════════════════════════════════════╝");
    }
    
    private static void testBasicConnectivity() {
        String[] endpoints = {
            "https://c.y.qq.com/soso/fcgi-bin/client_search_cp",
            "https://c.y.qq.com/v8/fcg-bin/fcg_play_single_song.fcg",
            "https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg",
            "https://u.y.qq.com/cgi-bin/musicu.fcg"
        };
        
        System.out.println("  检查各端点连通性...");
        boolean allConnected = true;
        
        for (String endpoint : endpoints) {
            try {
                URL url = new URL(endpoint);
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                setHeaders(conn, endpoint, "https://y.qq.com/");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                
                int code = conn.getResponseCode();
                String status = (code >= 200 && code < 400) ? "✓" : "✗";
                System.out.println("    " + status + " " + endpoint.substring(8, Math.min(40, endpoint.length())) + "... [" + code + "]");
                
                if (code < 200 || code >= 400) {
                    allConnected = false;
                }
                conn.disconnect();
            } catch (Exception e) {
                System.out.println("    ✗ " + endpoint + " [异常: " + e.getMessage() + "]");
                allConnected = false;
            }
        }
        
        System.out.println("  结果: " + (allConnected ? "✓ 所有端点连接成功" : "✗ 部分端点连接失败"));
    }
    
    private static void testSearch(String keyword) {
        try {
            // 构建搜索参数
            StringBuilder url = new StringBuilder(SEARCH_URL);
            url.append("?ct=24");
            url.append("&qqmusic_ver=1298");
            url.append("&new_json=1");
            url.append("&remoteplace=txt.yqq.song");
            url.append("&searchid=").append(randomSearchId());
            url.append("&t=0");
            url.append("&aggr=1");
            url.append("&cr=1");
            url.append("&catZhida=1");
            url.append("&lossless=0");
            url.append("&flag_qc=0");
            url.append("&p=1");
            url.append("&n=10");
            url.append("&w=").append(URLEncoder.encode(keyword, StandardCharsets.UTF_8.name()));
            url.append("&g_tk=5381");
            url.append("&loginUin=0");
            url.append("&hostUin=0");
            url.append("&format=json");
            url.append("&inCharset=utf8");
            url.append("&outCharset=utf-8");
            url.append("&notice=0");
            url.append("&platform=yqq");
            url.append("&needNewCode=0");
            
            System.out.println("  搜索关键词: " + keyword);
            
            HttpURLConnection conn = (HttpURLConnection) new URL(url.toString()).openConnection();
            conn.setRequestMethod("GET");
            setHeaders(conn, SEARCH_URL, "https://y.qq.com/portal/search.html");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            
            int code = conn.getResponseCode();
            System.out.println("  状态码: " + code);
            
            if (code == 200) {
                String response = readResponse(conn);
                
                // 简单检查响应内容
                if (response.contains("\"code\":0")) {
                    System.out.println("  ✓ 返回 API 成功响应");
                    
                    // 检查是否有搜索结果
                    if (response.contains("\"list\":[]") || response.contains("\"totalnum\":0")) {
                        System.out.println("  ⚠ 搜索结果为空（可能需要特殊参数或登录）");
                    } else if (response.contains("\"songname\"")) {
                        System.out.println("  ✓ 搜索到音乐结果");
                    }
                    
                    // 显示部分响应
                    if (response.length() > 150) {
                        System.out.println("  响应预览: " + response.substring(0, 150) + "...");
                    } else {
                        System.out.println("  响应: " + response);
                    }
                } else {
                    System.out.println("  ✗ API 返回错误响应");
                    System.out.println("  响应: " + response.substring(0, Math.min(200, response.length())));
                }
                conn.disconnect();
            } else {
                System.out.println("  ✗ HTTP 请求失败");
            }
        } catch (Exception e) {
            System.out.println("  ✗ 异常: " + e.getMessage());
        }
    }
    
    private static void testSongDetail() {
        try {
            // 使用一个常见歌曲进行测试
            String url = DETAIL_URL + "?songmid=0007Nqkc4XDYUE&tpl=yqq_song_detail&format=json";
            
            System.out.println("  测试歌曲详情获取...");
            
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("GET");
            setHeaders(conn, DETAIL_URL, "https://y.qq.com/n/ryqq/songDetail/0007Nqkc4XDYUE");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            
            int code = conn.getResponseCode();
            System.out.println("  状态码: " + code);
            
            if (code == 200) {
                String response = readResponse(conn);
                
                if (response.contains("\"code\":0") || response.length() > 100) {
                    System.out.println("  ✓ 成功获取歌曲信息");
                    System.out.println("  响应内容: " + response.substring(0, Math.min(150, response.length())) + "...");
                } else {
                    System.out.println("  ⚠ 获取到响应但格式可能异常");
                }
                conn.disconnect();
            } else {
                System.out.println("  ✗ 请求失败 (HTTP " + code + ")");
            }
        } catch (Exception e) {
            System.out.println("  ✗ 异常: " + e.getMessage());
        }
    }
    
    private static void testLyricFetch() {
        try {
            String url = LYRIC_URL + "?songmid=0007Nqkc4XDYUE&format=json&nobase64=0&g_tk=5381&loginUin=0&hostUin=0&inCharset=utf8&outCharset=utf-8&notice=0&platform=yqq.json&needNewCode=0&pcachetime=" + System.currentTimeMillis();
            
            System.out.println("  测试歌词获取...");
            
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setRequestMethod("GET");
            setHeaders(conn, LYRIC_URL, "https://y.qq.com/n/ryqq/songDetail/0007Nqkc4XDYUE");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            
            int code = conn.getResponseCode();
            System.out.println("  状态码: " + code);
            
            if (code == 200) {
                String response = readResponse(conn);
                
                if (response.contains("\"retcode\":0")) {
                    System.out.println("  ✓ 成功获取歌词");
                } else if (response.contains("\"retcode\"")) {
                    String retcode = response.replaceAll(".*\"retcode\":(\\d+).*", "$1");
                    System.out.println("  ⚠ 返回错误码: " + retcode + " (可能需要特殊授权或参数)");
                } else {
                    System.out.println("  ✓ 获得响应");
                }
                
                System.out.println("  响应内容: " + response.substring(0, Math.min(150, response.length())));
                conn.disconnect();
            } else {
                System.out.println("  ✗ 请求失败 (HTTP " + code + ")");
            }
        } catch (Exception e) {
            System.out.println("  ✗ 异常: " + e.getMessage());
        }
    }
    
    private static String readResponse(HttpURLConnection conn) throws Exception {
        InputStream input = conn.getInputStream();
        
        // 检查是否是压缩响应
        if ("gzip".equals(conn.getContentEncoding())) {
            input = new GZIPInputStream(input);
        }
        
        StringBuilder response = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
        }
        
        return response.toString();
    }
    
    private static void setHeaders(HttpURLConnection conn, String url, String referer) {
        conn.setRequestProperty("User-Agent", 
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");
        conn.setRequestProperty("Accept", "application/json, text/javascript, */*; q=0.01");
        conn.setRequestProperty("Accept-Encoding", "gzip, deflate");
        conn.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9");
        conn.setRequestProperty("Origin", "https://y.qq.com");
        conn.setRequestProperty("Referer", referer);
        conn.setRequestProperty("Connection", "keep-alive");
    }
    
    private static String randomSearchId() {
        return String.valueOf(System.nanoTime() % 1000000000L);
    }
}
