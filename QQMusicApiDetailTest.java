import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * QQ 音乐 API 详细功能测试
 */
public class QQMusicApiDetailTest {
    
    private static final String SEARCH_URL = "https://c.y.qq.com/soso/fcgi-bin/client_search_cp";
    private static final String DETAIL_URL = "https://c.y.qq.com/v8/fcg-bin/fcg_play_single_song.fcg";
    private static final String LYRIC_URL = "https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg";
    
    public static void main(String[] args) throws Exception {
        System.out.println("========================================");
        System.out.println("QQ 音乐 API 详细功能测试");
        System.out.println("========================================\n");
        
        // 测试搜索
        System.out.println("【搜索测试】");
        String response = performSearch("五月天");
        printResponse(response, 300);
        
        // 测试歌曲详情（使用一个已知的歌曲 ID）
        System.out.println("\n【歌曲详情测试】");
        String detailResponse = performSongDetail("0007Nqkc4XDYUE");
        printResponse(detailResponse, 300);
        
        // 测试歌词
        System.out.println("\n【歌词测试】");
        String lyricResponse = performLyric("0007Nqkc4XDYUE");
        printResponse(lyricResponse, 300);
        
        System.out.println("\n========================================");
        System.out.println("测试完成");
        System.out.println("========================================");
    }
    
    private static String performSearch(String keyword) {
        try {
            String encodedKeyword = URLEncoder.encode(keyword, StandardCharsets.UTF_8.name());
            String url = SEARCH_URL + "?ct=24&qqmusic_ver=1298&new_json=1&p=1&n=10&w=" + encodedKeyword;
            
            System.out.println("  搜索关键词: " + keyword);
            System.out.println("  请求 URL: " + url);
            
            HttpURLConnection conn = createConnection(url, "https://y.qq.com/portal/search.html");
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            
            int statusCode = conn.getResponseCode();
            System.out.println("  状态码: " + statusCode);
            
            if (statusCode >= 200 && statusCode < 300) {
                String response = readResponse(conn);
                conn.disconnect();
                return "✓ 搜索成功\n  响应: " + response;
            } else {
                conn.disconnect();
                return "✗ 搜索失败 (状态码: " + statusCode + ")";
            }
        } catch (Exception e) {
            return "✗ 搜索异常: " + e.getMessage();
        }
    }
    
    private static String performSongDetail(String songId) {
        try {
            String url = DETAIL_URL + "?songmid=" + songId + "&format=json";
            
            System.out.println("  歌曲 ID: " + songId);
            System.out.println("  请求 URL: " + url);
            
            HttpURLConnection conn = createConnection(url, "https://y.qq.com/n/ryqq/songDetail/" + songId);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            
            int statusCode = conn.getResponseCode();
            System.out.println("  状态码: " + statusCode);
            
            if (statusCode >= 200 && statusCode < 300) {
                String response = readResponse(conn);
                conn.disconnect();
                return "✓ 获取成功\n  响应: " + response;
            } else {
                conn.disconnect();
                return "✗ 获取失败 (状态码: " + statusCode + ")";
            }
        } catch (Exception e) {
            return "✗ 获取异常: " + e.getMessage();
        }
    }
    
    private static String performLyric(String songId) {
        try {
            String url = LYRIC_URL + "?songmid=" + songId + "&format=json";
            
            System.out.println("  歌曲 ID: " + songId);
            System.out.println("  请求 URL: " + url);
            
            HttpURLConnection conn = createConnection(url, "https://y.qq.com/n/ryqq/songDetail/" + songId);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            
            int statusCode = conn.getResponseCode();
            System.out.println("  状态码: " + statusCode);
            
            if (statusCode >= 200 && statusCode < 300) {
                String response = readResponse(conn);
                conn.disconnect();
                return "✓ 获取成功\n  响应: " + response;
            } else {
                conn.disconnect();
                return "✗ 获取失败 (状态码: " + statusCode + ")";
            }
        } catch (Exception e) {
            return "✗ 获取异常: " + e.getMessage();
        }
    }
    
    private static String readResponse(HttpURLConnection conn) throws Exception {
        BufferedReader reader = new BufferedReader(
            new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8)
        );
        StringBuilder response = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            response.append(line);
        }
        reader.close();
        return response.toString();
    }
    
    private static void printResponse(String response, int maxLength) {
        if (response != null) {
            if (response.length() > maxLength) {
                System.out.println(response.substring(0, maxLength) + "...");
            } else {
                System.out.println(response);
            }
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
        
        return conn;
    }
}
