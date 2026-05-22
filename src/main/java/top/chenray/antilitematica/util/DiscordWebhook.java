package top.chenray.antilitematica.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

import org.bukkit.plugin.Plugin;

public final class DiscordWebhook {
   private final String webhookUrl;
   private final String username;
   private final String avatarUrl;
   private final String embedTitle;
   private final int embedColor;
   private final String footerText;
   private final Plugin plugin;
   private final boolean useProxy;
   private final String proxyHost;
   private final int proxyPort;

   public DiscordWebhook(Plugin plugin, String webhookUrl, String username, String avatarUrl,
                         String embedTitle, int embedColor, String footerText,
                         String proxyHost, int proxyPort, String proxyUsername, String proxyPassword) {
      this.plugin = plugin;
      this.webhookUrl = webhookUrl;
      this.username = username;
      this.avatarUrl = avatarUrl;
      this.embedTitle = embedTitle;
      this.embedColor = embedColor;
      this.footerText = footerText;
      this.useProxy = proxyHost != null && !proxyHost.isEmpty() && proxyPort > 0;
      this.proxyHost = proxyHost;
      this.proxyPort = proxyPort;
   }

   /**
    * Send a detection alert to Discord via webhook.
    */
   public void sendDetection(String playerName, String playerUuid, String channel, String reason, String action) {
      StringBuilder json = new StringBuilder();
      json.append("{");
      json.append("\"username\": \"").append(escapeJson(this.username)).append("\"");
      if (this.avatarUrl != null && !this.avatarUrl.isEmpty()) {
         json.append(",\"avatar_url\": \"").append(escapeJson(this.avatarUrl)).append("\"");
      }
      json.append(",\"content\": \"\"");
      json.append(",\"embeds\": [{");
      json.append("\"title\": \"").append(escapeJson(this.embedTitle)).append("\"");
      json.append(",\"color\": ").append(this.embedColor);
      json.append(",\"fields\": [");
      json.append("{\"name\": \"Player\", \"value\": \"").append(escapeJson(playerName)).append("\", \"inline\": true}");
      json.append(",{\"name\": \"UUID\", \"value\": \"").append(escapeJson(playerUuid)).append("\", \"inline\": true}");
      json.append(",{\"name\": \"Reason\", \"value\": \"").append(escapeJson(reason)).append("\", \"inline\": true}");
      json.append(",{\"name\": \"Channel\", \"value\": \"").append(escapeJson(channel)).append("\", \"inline\": true}");
      json.append(",{\"name\": \"Action\", \"value\": \"").append(escapeJson(action)).append("\", \"inline\": true}");
      json.append("]");
      if (this.footerText != null && !this.footerText.isEmpty()) {
         json.append(",\"footer\": {\"text\": \"").append(escapeJson(this.footerText)).append("\"}");
      }
      json.append("}]");
      json.append("}");
      sendJson(json.toString());
   }

   /**
    * Send a raw text message to Discord via webhook (for testing or custom messages).
    */
   public void sendMessage(String message) {
      StringBuilder json = new StringBuilder();
      json.append("{");
      json.append("\"username\": \"").append(escapeJson(this.username)).append("\"");
      if (this.avatarUrl != null && !this.avatarUrl.isEmpty()) {
         json.append(",\"avatar_url\": \"").append(escapeJson(this.avatarUrl)).append("\"");
      }
      json.append(",\"content\": \"").append(escapeJson(message)).append("\"");
      json.append("}");
      sendJson(json.toString());
   }

   /**
    * Test the webhook connection by sending a simple test message.
    * @return true if the webhook responded with 2xx
    */
   public boolean test() {
      try {
         String json = "{\"username\":\"" + escapeJson(this.username)
               + "\",\"content\":\"AntiLitematica Webhook Test - " + java.time.Instant.now() + "\"}";
         URL url = new URL(this.webhookUrl);
         HttpURLConnection conn = openConnection(url);
         conn.setRequestMethod("POST");
         conn.setRequestProperty("Content-Type", "application/json");
         conn.setRequestProperty("User-Agent", "AntiLitematica/" + plugin.getDescription().getVersion());
         conn.setDoOutput(true);
         conn.setUseCaches(false);
         conn.setConnectTimeout(8000);
         conn.setReadTimeout(8000);
         byte[] input = json.getBytes(StandardCharsets.UTF_8);
         conn.setFixedLengthStreamingMode(input.length);
         try (OutputStream os = conn.getOutputStream()) {
            os.write(input);
         }
         int code = conn.getResponseCode();
         conn.disconnect();
         // Discord returns 204 No Content on success
         return code == 204 || code == 200;
      } catch (Exception e) {
         plugin.getLogger().warning("Discord webhook test failed: " + e.getMessage());
         return false;
      }
   }

   private void sendJson(String jsonPayload) {
      if (webhookUrl == null || webhookUrl.isEmpty()) return;
      CompletableFuture.runAsync(() -> {
         HttpURLConnection conn = null;
         try {
            URL url = new URL(webhookUrl);
            conn = openConnection(url);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("User-Agent", "AntiLitematica/" + plugin.getDescription().getVersion());
            conn.setDoOutput(true);
            conn.setUseCaches(false);
            conn.setConnectTimeout(8000);
            conn.setReadTimeout(8000);

            byte[] input = jsonPayload.getBytes(StandardCharsets.UTF_8);
            conn.setFixedLengthStreamingMode(input.length);
            try (OutputStream os = conn.getOutputStream()) {
               os.write(input);
            }

            int code = conn.getResponseCode();
            // Handle HTTP 307 redirect (Discord CDN)
            if (code == 307 || code == 302 || code == 301) {
               String redirectUrl = conn.getHeaderField("Location");
               if (redirectUrl != null && !redirectUrl.isEmpty()) {
                  conn.disconnect();
                  URL redirect = new URL(redirectUrl);
                  conn = openConnection(redirect);
                  conn.setRequestMethod("POST");
                  conn.setRequestProperty("Content-Type", "application/json");
                  conn.setRequestProperty("User-Agent", "AntiLitematica/" + plugin.getDescription().getVersion());
                  conn.setDoOutput(true);
                  conn.setUseCaches(false);
                  conn.setConnectTimeout(8000);
                  conn.setReadTimeout(8000);
                  conn.setFixedLengthStreamingMode(input.length);
                  try (OutputStream os = conn.getOutputStream()) {
                     os.write(input);
                  }
                  code = conn.getResponseCode();
               }
            }
            if (code != 204 && code != 200) {
               String err = readStream(conn.getErrorStream());
               plugin.getLogger().warning("Discord webhook error: HTTP " + code + " " + err);
            }
         } catch (Exception e) {
            plugin.getLogger().warning("Discord webhook failed: " + e.getMessage());
         } finally {
            if (conn != null) conn.disconnect();
         }
      });
   }

   private HttpURLConnection openConnection(URL url) throws IOException {
      if (useProxy) {
         Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, proxyPort));
         return (HttpURLConnection) url.openConnection(proxy);
      }
      return (HttpURLConnection) url.openConnection();
   }

   private static String readStream(InputStream in) {
      if (in == null) return "";
      try (java.util.Scanner s = new java.util.Scanner(in, StandardCharsets.UTF_8.name())) {
         return s.useDelimiter("\\A").hasNext() ? s.next() : "";
      } catch (Exception e) { return ""; }
   }

   static String escapeJson(String s) {
      if (s == null) return "";
      return s.replace("\\", "\\\\").replace("\"", "\\\"")
              .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
   }
}