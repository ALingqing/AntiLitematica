package top.chenray.antilitematica.util;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

import org.bukkit.plugin.Plugin;

/**
 * OneBot 11 (QQ Bot) notification sender.
 * <p>
 * Supported endpoints:
 * - send_group_msg: sends message to a QQ group
 * - send_private_msg: sends private message to a QQ user
 * <p>
 * Configure in config.yml:
 * <pre>
 * onebot:
 *   enabled: false
 *   http_url: "http://localhost:5700"
 *   access_token: ""
 *   group_id: 0
 * </pre>
 */
public final class OneBotNotifier {

    private final Plugin plugin;
    private final String httpUrl;
    private final String accessToken;
    private final long groupId;

    public OneBotNotifier(Plugin plugin, String httpUrl, String accessToken, long groupId) {
        this.plugin = plugin;
        // Normalize URL: remove trailing slash
        this.httpUrl = httpUrl != null ? httpUrl.replaceAll("/+$", "") : "";
        this.accessToken = accessToken != null ? accessToken : "";
        this.groupId = groupId;
    }

    /**
     * Send a detection alert to the configured QQ group.
     */
    public void sendDetection(String playerName, String reason, String action) {
        String msg = String.format(
                "[AntiLitematica]\nPlayer: %s\nReason: %s\nAction: %s\nTime: %s",
                playerName, reason, action,
                java.time.LocalDateTime.now().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
        );
        sendGroupMessage(msg);
    }

    /**
     * Send a test message to verify OneBot connectivity.
     *
     * @return true if the request was accepted (HTTP 2xx)
     */
    public boolean test() {
        String msg = "AntiLitematica OneBot test - " + java.time.Instant.now();
        return sendGroupMessage(msg);
    }

    /**
     * Send a message to the configured QQ group.
     */
    public boolean sendGroupMessage(String message) {
        if (httpUrl.isEmpty() || groupId <= 0) return false;
        try {
            String json = String.format(
                    "{\"group_id\":%d,\"message\":\"%s\",\"auto_escape\":true}",
                    groupId, escapeJson(message)
            );
            return post("send_group_msg", json);
        } catch (Exception e) {
            plugin.getLogger().warning("OneBot send_group_msg failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Send a private message to a QQ user.
     */
    public boolean sendPrivateMessage(long userId, String message) {
        if (httpUrl.isEmpty()) return false;
        try {
            String json = String.format(
                    "{\"user_id\":%d,\"message\":\"%s\",\"auto_escape\":true}",
                    userId, escapeJson(message)
            );
            return post("send_private_msg", json);
        } catch (Exception e) {
            plugin.getLogger().warning("OneBot send_private_msg failed: " + e.getMessage());
            return false;
        }
    }

    private boolean post(String endpoint, String jsonPayload) throws Exception {
        URL url = new URL(httpUrl + "/" + endpoint);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("User-Agent", "AntiLitematica/" + plugin.getDescription().getVersion());
        if (accessToken != null && !accessToken.isEmpty()) {
            conn.setRequestProperty("Authorization", "Bearer " + accessToken);
        }
        conn.setDoOutput(true);
        conn.setUseCaches(false);
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);

        byte[] input = jsonPayload.getBytes(StandardCharsets.UTF_8);
        conn.setFixedLengthStreamingMode(input.length);
        try (OutputStream os = conn.getOutputStream()) {
            os.write(input);
        }

        int code = conn.getResponseCode();
        // OneBot returns 200 even on business errors; check retcode in body
        if (code == 200) {
            try (java.util.Scanner s = new java.util.Scanner(
                    conn.getInputStream(), StandardCharsets.UTF_8.name())) {
                String body = s.useDelimiter("\\A").hasNext() ? s.next() : "";
                if (body.contains("\"retcode\":0") || body.contains("\"status\":\"ok\"")) {
                    return true;
                }
                plugin.getLogger().warning("OneBot API error: " + body);
            }
        } else if (code == 401) {
            plugin.getLogger().warning("OneBot auth failed: access_token mismatch");
        } else if (code == 404) {
            plugin.getLogger().warning("OneBot endpoint not found: " + endpoint);
        }
        conn.disconnect();
        return false;
    }

    void sendAsync(String endpoint, String jsonPayload) {
        CompletableFuture.runAsync(() -> {
            try {
                post(endpoint, jsonPayload);
            } catch (Exception e) {
                plugin.getLogger().warning("OneBot async send failed: " + e.getMessage());
            }
        });
    }

    private static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
}
