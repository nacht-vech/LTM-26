import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Tích hợp đầy đủ 2 bài mẫu diachi.java (java.net.URL) và inet.java (java.net.InetAddress)
 * trực tiếp vào đề tài Nhóm 1 (Web Browser).
 */
public class UrlInetHelper {

    public static class UrlAnalysisResult {
        public boolean valid;
        public String errorMessage;
        public String normalizedUrl;
        public Map<String, String> properties = new LinkedHashMap<>();
    }

    public static class InetAnalysisResult {
        public String localHostName;
        public String localHostIp;
        public String targetHostInput;
        public String targetHostName;
        public String targetHostIp;
        public String canonicalHostName;
        public boolean reachable;
        public List<String> allAddresses = new ArrayList<>();
        public String errorMessage;
    }

    public static String normalizeUrl(String rawUrl) {
        if (rawUrl == null) {
            return "";
        }
        String trimmed = rawUrl.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        if (!trimmed.matches("^[a-zA-Z][a-zA-Z0-9+.-]*://.*")) {
            return "http://" + trimmed;
        }
        return trimmed;
    }

    public static UrlAnalysisResult analyzeUrl(String rawUrl) {
        UrlAnalysisResult res = new UrlAnalysisResult();
        String urlStr = normalizeUrl(rawUrl);
        res.normalizedUrl = urlStr;

        try {
            URL url = new URL(urlStr);
            res.valid = true;
            res.properties.put("URL (url.toString())", String.valueOf(url.toString()));
            res.properties.put("Protocol (url.getProtocol())", String.valueOf(url.getProtocol()));
            res.properties.put("Authority (url.getAuthority())", String.valueOf(url.getAuthority()));
            res.properties.put("File Name (url.getFile())", String.valueOf(url.getFile()));
            res.properties.put("Host (url.getHost())", String.valueOf(url.getHost()));
            res.properties.put("Path (url.getPath())", String.valueOf(url.getPath()));
            res.properties.put("Port (url.getPort())", String.valueOf(url.getPort()));
            res.properties.put("Default Port (url.getDefaultPort())", String.valueOf(url.getDefaultPort()));
            res.properties.put("Query (url.getQuery())", String.valueOf(url.getQuery()));
            res.properties.put("Ref / Anchor (url.getRef())", String.valueOf(url.getRef()));
        } catch (MalformedURLException e) {
            res.valid = false;
            res.errorMessage = "URL không hợp lệ: " + e.getMessage();
        }
        return res;
    }

    public static InetAnalysisResult analyzeInet(String hostOrUrl) {
        InetAnalysisResult res = new InetAnalysisResult();

        try {
            InetAddress local = InetAddress.getLocalHost();
            res.localHostName = local.getHostName();
            res.localHostIp = local.getHostAddress();
        } catch (UnknownHostException e) {
            res.localHostName = "localhost";
            res.localHostIp = "127.0.0.1";
        }

        String host = hostOrUrl == null ? "" : hostOrUrl.trim();
        if (host.contains("://")) {
            try {
                URL u = new URL(host);
                host = u.getHost();
            } catch (MalformedURLException ignored) {
            }
        } else if (host.contains("/")) {
            host = host.substring(0, host.indexOf('/'));
        }
        if (host.contains(":")) {
            host = host.substring(0, host.indexOf(':'));
        }
        if (host.isEmpty()) {
            host = "vku.udn.vn";
        }
        res.targetHostInput = host;

        try {
            InetAddress primaryIp = InetAddress.getByName(host);
            res.targetHostName = primaryIp.getHostName();
            res.targetHostIp = primaryIp.getHostAddress();
            res.canonicalHostName = primaryIp.getCanonicalHostName();
            try {
                res.reachable = primaryIp.isReachable(2000);
            } catch (Exception ignored) {
                res.reachable = false;
            }

            InetAddress[] sw = InetAddress.getAllByName(host);
            for (int i = 0; i < sw.length; i++) {
                res.allAddresses.add("[" + (i + 1) + "] " + sw[i].toString()
                        + "  (Host: " + sw[i].getHostName() + " | IP: " + sw[i].getHostAddress() + ")");
            }
        } catch (UnknownHostException e) {
            res.errorMessage = "Không thể phân giải tên miền '" + host + "': " + e.getMessage();
        }

        return res;
    }

    public static String formatCombinedReport(UrlAnalysisResult urlRes, InetAnalysisResult inetRes) {
        StringBuilder sb = new StringBuilder();
        sb.append("================ 1. KET QUA PHAN TICH URL (Bai mau diachi.java) ================\n");
        if (urlRes.valid) {
            for (Map.Entry<String, String> entry : urlRes.properties.entrySet()) {
                sb.append(String.format("%-34s : %s\n", entry.getKey(), entry.getValue()));
            }
        } else {
            sb.append("Loi URL: ").append(urlRes.errorMessage).append("\n");
        }

        sb.append("\n============= 2. KET QUA PHAN TICH INETADDRESS (Bai mau inet.java) =============\n");
        sb.append("[May cuc bo - InetAddress.getLocalHost()]\n");
        sb.append("  Host Name          : ").append(inetRes.localHostName).append("\n");
        sb.append("  IP Address         : ").append(inetRes.localHostIp).append("\n\n");

        sb.append("[Ten mien muc tieu - InetAddress.getByName(\"").append(inetRes.targetHostInput).append("\")]\n");
        if (inetRes.errorMessage == null) {
            sb.append("  Host Name          : ").append(inetRes.targetHostName).append("\n");
            sb.append("  IP Address         : ").append(inetRes.targetHostIp).append("\n");
            sb.append("  Canonical HostName : ").append(inetRes.canonicalHostName).append("\n");
            sb.append("  Ping / Reachable   : ").append(inetRes.reachable ? "Co phan hoi (Reachable)" : "ICMP bi chan / Timeout").append("\n\n");
            sb.append("[Danh sach tat ca IP - InetAddress.getAllByName(\"").append(inetRes.targetHostInput).append("\")]\n");
            for (String addr : inetRes.allAddresses) {
                sb.append("  ").append(addr).append("\n");
            }
        } else {
            sb.append("  Loi phan giai DNS  : ").append(inetRes.errorMessage).append("\n");
        }
        return sb.toString();
    }
}