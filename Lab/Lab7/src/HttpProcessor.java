import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Xử lý 3 phương thức HTTP (GET, POST, HEAD), phân tích trang HTML trả về:
 * - Tính chiều dài trang HTML (số ký tự & số byte)
 * - Đếm số lượng thẻ <p>, <div>, <span>, <img> (và cả <imag> theo đề bài)
 * - Trích xuất thông tin tài nguyên khi gọi phương thức HEAD
 * - Tích hợp sẵn một Local Demo Web Server tại http://localhost:8088/vku-demo để test GET/POST/HEAD mọi lúc.
 */
public class HttpProcessor {

    private static HttpServer localDemoServer;

    public static class HtmlTagStats {
        public int charLength;
        public int byteLength;
        public int pCount;
        public int divCount;
        public int spanCount;
        public int imgCount;
        public int imagLiteralCount;
        public int aCount;
        public int formCount;
        public int headingCount;
        public String pageTitle = "(Không có tiêu đề)";
        public List<String> extractedLinks = new ArrayList<>();
        public List<String> extractedImages = new ArrayList<>();
    }

    public static class HttpResponseData {
        public String requestMethod;
        public String finalUrl;
        public int statusCode;
        public String statusMessage;
        public long elapsedMs;
        public String contentType;
        public int contentLengthHeader;
        public String serverName;
        public String lastModified;
        public Map<String, String> headers = new LinkedHashMap<>();
        public String htmlBody = "";
        public HtmlTagStats tagStats = new HtmlTagStats();
        public String errorMessage;
    }

    public static HttpResponseData executeRequest(String rawUrl, String method,
                                                  String postFormData, File attachedFile) {
        HttpResponseData result = new HttpResponseData();
        String urlStr = UrlInetHelper.normalizeUrl(rawUrl);
        result.finalUrl = urlStr;
        result.requestMethod = method == null ? "GET" : method.trim().toUpperCase();

        long startTime = System.currentTimeMillis();
        HttpURLConnection conn = null;

        try {
            URL url = new URL(urlStr);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod(result.requestMethod);
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(10000);
            conn.setInstanceFollowRedirects(true);
            conn.setRequestProperty("User-Agent", "VKU-SimpleBrowser/1.0 (Java Swing; Network Programming Lab)");
            conn.setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8");
            conn.setRequestProperty("Accept-Charset", "UTF-8");

            if ("POST".equalsIgnoreCase(result.requestMethod)) {
                conn.setDoOutput(true);
                StringBuilder payloadBuilder = new StringBuilder();
                if (postFormData != null && !postFormData.trim().isEmpty()) {
                    payloadBuilder.append(postFormData.trim());
                }
                if (attachedFile != null && attachedFile.exists() && attachedFile.isFile()) {
                    if (payloadBuilder.length() > 0) {
                        payloadBuilder.append("&");
                    }
                    String filePreview = readSmallFileText(attachedFile);
                    payloadBuilder.append("uploaded_filename=").append(attachedFile.getName())
                            .append("&uploaded_filesize=").append(attachedFile.length())
                            .append("&uploaded_content=").append(filePreview);
                }
                byte[] postBytes = payloadBuilder.toString().getBytes(StandardCharsets.UTF_8);
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
                conn.setRequestProperty("Content-Length", String.valueOf(postBytes.length));
                try (OutputStream os = conn.getOutputStream()) {
                    os.write(postBytes);
                    os.flush();
                }
            }

            result.statusCode = conn.getResponseCode();
            result.statusMessage = conn.getResponseMessage();
            result.finalUrl = conn.getURL().toString();
            result.contentType = conn.getContentType() != null ? conn.getContentType() : "Không xác định";
            result.contentLengthHeader = conn.getContentLength();
            result.serverName = conn.getHeaderField("Server") != null ? conn.getHeaderField("Server") : "Không công bố";
            result.lastModified = conn.getLastModified() > 0 ? new Date(conn.getLastModified()).toString() : "Không có";

            Map<String, List<String>> rawHeaders = conn.getHeaderFields();
            if (rawHeaders != null) {
                for (Map.Entry<String, List<String>> entry : rawHeaders.entrySet()) {
                    String key = entry.getKey() == null ? "Status-Line" : entry.getKey();
                    String val = String.join("; ", entry.getValue());
                    result.headers.put(key, val);
                }
            }

            if (!"HEAD".equalsIgnoreCase(result.requestMethod)) {
                InputStream is = null;
                try {
                    is = conn.getInputStream();
                } catch (IOException ex) {
                    is = conn.getErrorStream();
                }
                if (is != null) {
                    ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                    byte[] data = new byte[4096];
                    int nRead;
                    while ((nRead = is.read(data, 0, data.length)) != -1) {
                        buffer.write(data, 0, nRead);
                    }
                    byte[] rawBytes = buffer.toByteArray();
                    result.htmlBody = new String(rawBytes, StandardCharsets.UTF_8);
                    result.tagStats = analyzeHtml(result.htmlBody, rawBytes.length, result.finalUrl);
                }
            } else {
                result.htmlBody = buildHeadSummaryHtml(result);
            }

        } catch (Exception e) {
            result.errorMessage = e.getClass().getSimpleName() + ": " + e.getMessage();
        } finally {
            result.elapsedMs = System.currentTimeMillis() - startTime;
            if (conn != null) {
                conn.disconnect();
            }
        }
        return result;
    }

    public static HtmlTagStats analyzeHtml(String html, int byteLen, String baseUrl) {
        HtmlTagStats stats = new HtmlTagStats();
        if (html == null) {
            return stats;
        }
        stats.charLength = html.length();
        stats.byteLength = byteLen > 0 ? byteLen : html.getBytes(StandardCharsets.UTF_8).length;

        stats.pCount = countTag(html, "p");
        stats.divCount = countTag(html, "div");
        stats.spanCount = countTag(html, "span");
        int standardImg = countTag(html, "img");
        stats.imagLiteralCount = countTag(html, "imag");
        stats.imgCount = standardImg + stats.imagLiteralCount;

        stats.aCount = countTag(html, "a");
        stats.formCount = countTag(html, "form");
        stats.headingCount = countTagRegex(html, "<h[1-6](?=\\s|>|/)");

        Matcher titleMatcher = Pattern.compile("<title[^>]*>(.*?)</title>",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL).matcher(html);
        if (titleMatcher.find()) {
            stats.pageTitle = titleMatcher.group(1).replaceAll("\\s+", " ").trim();
        }

        Matcher linkMatcher = Pattern.compile("<a\\s+[^>]*href\\s*=\\s*[\"']([^\"'#]+)[\"']",
                Pattern.CASE_INSENSITIVE).matcher(html);
        while (linkMatcher.find() && stats.extractedLinks.size() < 50) {
            String href = resolveRelativeUrl(baseUrl, linkMatcher.group(1).trim());
            if (!stats.extractedLinks.contains(href)) {
                stats.extractedLinks.add(href);
            }
        }

        Matcher imgMatcher = Pattern.compile("<(?:img|imag)\\s+[^>]*src\\s*=\\s*[\"']([^\"']+)[\"']",
                Pattern.CASE_INSENSITIVE).matcher(html);
        while (imgMatcher.find() && stats.extractedImages.size() < 50) {
            String src = resolveRelativeUrl(baseUrl, imgMatcher.group(1).trim());
            if (!stats.extractedImages.contains(src)) {
                stats.extractedImages.add(src);
            }
        }

        return stats;
    }

    private static int countTag(String html, String tagName) {
        return countTagRegex(html, "<" + tagName + "(?=\\s|>|/)");
    }

    private static int countTagRegex(String html, String regex) {
        Matcher m = Pattern.compile(regex, Pattern.CASE_INSENSITIVE).matcher(html);
        int count = 0;
        while (m.find()) {
            count++;
        }
        return count;
    }

    private static String resolveRelativeUrl(String baseUrl, String link) {
        try {
            URL base = new URL(baseUrl);
            return new URL(base, link).toString();
        } catch (Exception e) {
            return link;
        }
    }

    private static String readSmallFileText(File f) {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
            String line;
            int total = 0;
            while ((line = br.readLine()) != null && total < 500) {
                sb.append(line).append(" ");
                total += line.length();
            }
        } catch (Exception ignored) {
        }
        return sb.toString().replace("&", " ").replace("=", ":");
    }

    private static String buildHeadSummaryHtml(HttpResponseData res) {
        StringBuilder sb = new StringBuilder();
        sb.append("<html><body style='font-family:sans-serif; padding:16px; background:#f8fafc;'>");
        sb.append("<h2 style='color:#0f172a;'>Thông tin Tài nguyên (Phương thức HEAD)</h2>");
        sb.append("<p>Phương thức <b>HEAD</b> chỉ yêu cầu Server trả về <b>HTTP Headers</b> (thông tin tài nguyên) mà không tải thân trang HTML.</p>");
        sb.append("<table border='1' cellpadding='6' cellspacing='0' style='border-collapse:collapse; width:100%; background:#ffffff;'>");
        sb.append("<tr style='background:#e2e8f0;'><th>Thuộc tính tài nguyên (Header)</th><th>Giá trị trả về từ Server</th></tr>");
        sb.append("<tr><td><b>URL Tài nguyên</b></td><td>").append(res.finalUrl).append("</td></tr>");
        sb.append("<tr><td><b>Mã trạng thái (Status)</b></td><td>").append(res.statusCode).append(" ").append(res.statusMessage).append("</td></tr>");
        sb.append("<tr><td><b>Loại nội dung (Content-Type)</b></td><td>").append(res.contentType).append("</td></tr>");
        sb.append("<tr><td><b>Kích thước (Content-Length)</b></td><td>").append(res.contentLengthHeader >= 0 ? res.contentLengthHeader + " bytes" : "Chunked / Không xác định").append("</td></tr>");
        sb.append("<tr><td><b>Máy chủ (Server)</b></td><td>").append(res.serverName).append("</td></tr>");
        sb.append("<tr><td><b>Sửa đổi lần cuối (Last-Modified)</b></td><td>").append(res.lastModified).append("</td></tr>");
        for (Map.Entry<String, String> entry : res.headers.entrySet()) {
            sb.append("<tr><td>").append(entry.getKey()).append("</td><td>").append(entry.getValue()).append("</td></tr>");
        }
        sb.append("</table></body></html>");
        return sb.toString();
    }

    public static synchronized void startLocalDemoServer(int port) {
        if (localDemoServer != null) {
            return;
        }
        try {
            localDemoServer = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
            localDemoServer.createContext("/vku-demo", new HttpHandler() {
                @Override
                public void handle(HttpExchange exchange) throws IOException {
                    String method = exchange.getRequestMethod();
                    String query = exchange.getRequestURI().getQuery();
                    String postBody = "";

                    if ("POST".equalsIgnoreCase(method)) {
                        ByteArrayOutputStream baos = new ByteArrayOutputStream();
                        InputStream is = exchange.getRequestBody();
                        byte[] buf = new byte[2048];
                        int r;
                        while ((r = is.read(buf)) != -1) {
                            baos.write(buf, 0, r);
                        }
                        postBody = new String(baos.toByteArray(), StandardCharsets.UTF_8);
                    }

                    String localHostStr = "localhost (127.0.0.1)";
                    try {
                        InetAddress ia = InetAddress.getLocalHost();
                        localHostStr = ia.getHostName() + " (" + ia.getHostAddress() + ")";
                    } catch (Exception ignored) {
                    }

                    String html = "<!DOCTYPE html>\n"
                            + "<html>\n"
                            + "<head>\n"
                            + "  <meta charset='UTF-8'>\n"
                            + "  <title>VKU Network Programming Lab - Trang Demo GET POST HEAD</title>\n"
                            + "</head>\n"
                            + "<body style='font-family: Arial, sans-serif; margin: 18px; background-color: #f0f9ff;'>\n"
                            + "  <div id='header' style='background:#0284c7; color:white; padding:12px;'>\n"
                            + "    <h1>TRƯỜNG ĐẠI HỌC CÔNG NGHỆ THÔNG TIN & TRUYỀN THÔNG VIỆT - HÀN (VKU)</h1>\n"
                            + "    <p>Trang Web Demo Nội Bộ hỗ trợ kiểm thử đầy đủ 3 phương thức: <b>GET</b>, <b>POST</b>, <b>HEAD</b>.</p>\n"
                            + "  </div>\n"
                            + "  <div id='request-info' style='background:white; padding:12px; margin-top:12px; border:1px solid #bae6fd;'>\n"
                            + "    <h2>1. Thông tin Request vừa nhận</h2>\n"
                            + "    <p>Phương thức HTTP: <span><b>" + method + "</b></span></p>\n"
                            + "    <p>Query trên URL: <span>" + (query == null ? "(Không có)" : query) + "</span></p>\n"
                            + "    <p>Dữ liệu POST / File gửi lên Server: <span><b>"
                            + (postBody.isEmpty() ? "(Chưa có dữ liệu POST)" : postBody) + "</b></span></p>\n"
                            + "    <p>Thông tin InetAddress Server: <span>" + localHostStr + "</span></p>\n"
                            + "  </div>\n"
                            + "  <div id='tag-sample' style='background:white; padding:12px; margin-top:12px; border:1px solid #bae6fd;'>\n"
                            + "    <h2>2. Khu vực mẫu kiểm thử bộ đếm thẻ HTML (&lt;p&gt;, &lt;div&gt;, &lt;span&gt;, &lt;img&gt;)</h2>\n"
                            + "    <p>Đoạn văn mẫu thứ 6 (thẻ p).</p>\n"
                            + "    <p>Đoạn văn mẫu thứ 7 kết hợp <span>thẻ span số 5</span> và <span>thẻ span số 6</span>.</p>\n"
                            + "    <div>Khối div phụ thứ 4 chứa ảnh minh họa bên dưới:</div>\n"
                            + "    <img src='http://vku.udn.vn/logo_vku_1.png' alt='Logo VKU 1' width='120' height='40' />\n"
                            + "    <img src='http://vku.udn.vn/banner_lab_2.png' alt='Banner Lab 2' width='120' height='40' />\n"
                            + "    <imag src='http://vku.udn.vn/sample_imag_tag.png' alt='The imag theo de bai' />\n"
                            + "    <p>Liên kết nhanh để test click link: <a href='http://example.com'>Trang Example.com</a> | <a href='http://vku.udn.vn'>Trang chủ VKU</a></p>\n"
                            + "  </div>\n"
                            + "</body>\n"
                            + "</html>";

                    byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
                    Headers h = exchange.getResponseHeaders();
                    h.set("Content-Type", "text/html; charset=UTF-8");
                    h.set("Server", "VKU-Embedded-Java-HttpServer/1.0");
                    h.set("X-Lab-Group", "Nhom 1 - Simple Browser (GET/POST/HEAD)");
                    h.set("Last-Modified", new Date().toString());

                    if ("HEAD".equalsIgnoreCase(method)) {
                        h.set("Content-Length", String.valueOf(bytes.length));
                        exchange.sendResponseHeaders(200, -1);
                    } else {
                        exchange.sendResponseHeaders(200, bytes.length);
                        try (OutputStream os = exchange.getResponseBody()) {
                            os.write(bytes);
                        }
                    }
                    exchange.close();
                }
            });
            localDemoServer.setExecutor(null);
            localDemoServer.start();
        } catch (IOException ignored) {
        }
    }
}