import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * QQ 音乐 API 网络连接测试
 */
public class QQMusicApiTest {
    
    private static final String[] ENDPOINTS = {
        "https://c.y.qq.com/soso/fcgi-bin/client_search_cp",
        "https://c.y.qq.com/v8/fcg-bin/fcg_play_single_song.fcg",
        "https://c.y.qq.com/qzone/fcg-bin/fcg_ucc_getcdinfo_byids_cp.fcg",
        "https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg",
        "https://u.y.qq.com/cgi-bin/musicu.fcg"
    };
    
    private static final String SEARCH_ENDPOINT = "https://c.y.qq.com/soso/fcgi-bin/client_search_cp";
    
    public static void main(String[] args) throws Exception {
        System.out.println("========================================");
        System.out.println("QQ 音乐 API 网络连接测试");
        System.out.println("========================================\n");
        
        // 1. 测试各个端点的基本连通性
        System.out.println("【步骤1】测试端点连通性:");
        testEndpoints();
        
        // 2. 测试搜索功能
        System.out.println("\n【步骤2】测试搜索功能:");
        testSearch();
        
        System.out.println("\n========================================");
        System.out.println("测试完成");
        System.out.println("========================================");
    }
    
    private static void testEndpoints() {
        for (String endpoint : ENDPOINTS) {
            System.out.print("  测试 " + endpoint + " ... ");
            try {
                HttpURLConnection conn = createConnection(endpoint, "https://y.qq.com/");
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);
                int statusCode = conn.getResponseCode();
                if (statusCode >= 200 && statusCode < 400) {
                    System.out.println("✓ 连接成功 (状态码: " + statusCode + ")");
                } else {
                    System.out.println("✗ 连接失败 (状态码: " + statusCode + ")");
                }
                conn.disconnect();
            } catch (Exception e) {
                System.out.println("✗ 错误: " + e.getMessage());
            }
        }
    }
    
    private static void testSearch() {
        try {
            // 构建搜索参数
            String keyword = URLEncoder.encode("周杰伦", StandardCharsets.UTF_8.name());
            String url = SEARCH_ENDPOINT 
                + "?ct=24&qqmusic_ver=1298&new_json=1&p=1&n=20&w=" + keyword;
            
            System.out.print("  搜索'周杰伦'... ");
            HttpURLConnection conn = createConnection(url, "https://y.qq.com/portal/search.html");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            
            int statusCode = conn.getResponseCode();
            if (statusCode >= 200 && statusCode < 300) {
                // 读取响应
                BufferedReader reader = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8)
                );
                StringBuilder response = new StringBuilder();
                String line;
                int lineCount = 0;
                while ((line = reader.readLine()) != null && lineCount++ < 5) {
                    response.append(line);
                }
                reader.close();
                
                String responseStr = response.toString();
                if (responseStr.length() > 200) {
                    responseStr = responseStr.substring(0, 200) + "...";
                }
                
                System.out.println("✓ 成功 (状态码: " + statusCode + ")");
                System.out.println("    响应内容: " + responseStr);
            } else {
                System.out.println("✗ 失败 (状态码: " + statusCode + ")");
            }
            conn.disconnect();
        } catch (Exception e) {
            System.out.println("✗ 错误: " + e.getMessage());
        }
    }
    
    private static HttpURLConnection createConnection(String urlStr, String referer) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36");
        conn.setRequestProperty("Accept", "application/json, text/javascript, */*; q=0.01");
        conn.setRequestProperty("Accept-Encoding", "gzip, deflate, br");
        conn.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9");
        conn.setRequestProperty("Origin", "https://y.qq.com");
        conn.setRequestProperty("Referer", referer);
        
        // 设置代理（如果需要）
        String proxyHost = System.getProperty("http.proxyHost");
        if (proxyHost != null && !proxyHost.isEmpty()) {
            System.setProperty("http.proxyPort", System.getProperty("http.proxyPort", "80"));
        }
        
        return conn;
    }
}
