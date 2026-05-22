package top.chenray.antilitematica.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Authenticator;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
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
   private final String proxyHost;
   private final int proxyPort;
   private final String proxyUsername;
   private final String proxyPassword;

   public DiscordWebhook(Plugin plugin, String webhookUrl, String username, String avatarUrl, String embedTitle, int embedColor, String footerText) {
      this(plugin, webhookUrl, username, avatarUrl, embedTitle, embedColor, footerText, "", 0, "", "");
   }

   public DiscordWebhook(Plugin plugin, String webhookUrl, String username, String avatarUrl, String embedTitle, int embedColor, String footerText, String proxyHost, int proxyPort, String proxyUsername, String proxyPassword) {
      this.plugin = plugin;
      this.webhookUrl = webhookUrl;
      this.username = username;
      this.avatarUrl = avatarUrl;
      this.embedTitle = embedTitle;
      this.embedColor = embedColor;
      this.footerText = footerText;
      this.proxyHost = proxyHost;
      this.proxyPort = proxyPort;
      this.proxyUsername = proxyUsername;
      this.proxyPassword = proxyPassword;
   }

   public void sendDetection(String playerName, String playerUuid, String channel, String reason, String action) {
      StringBuilder json = new StringBuilder();
      json.append("{");
      json.append("\"username\": \"").append(escapeJson(this.username)).append("\",");
      if (this.avatarUrl != null && !this.avatarUrl.isEmpty()) {
         json.append("\"avatar_url\": \"").append(escapeJson(this.avatarUrl)).append("\",");
      }
      json.append("\"content\": \"\",");
      json.append("\"embeds\": [{");
      json.append("\"title\": \"").append(escapeJson(this.embedTitle)).append("\",");
      json.append("\"color\": ").append(this.embedColor).append(",");
      json.append("\"fields\": [");
      json.append("{\"name\": \"Player\", \"value\": \"").append(escapeJson(playerName)).append("\", \"inline\": true},");
      json.append("{\"name\": \"UUID\", \"value\": \"").append(escapeJson(playerUuid)).append("\", \"inline\": true},");
      json.append("{\"name\": \"Reason\", \"value\": \"").append(escapeJson(reason)).append("\", \"inline\": true},");
      json.append("{\"name\": \"Channel\", \"value\": \"").append(escapeJson(channel)).append("\", \"inline\": true},");
      json.append("{\"name\": \"Action\", \"value\": \"").append(escapeJson(action)).append("\", \"inline\": true}");
      json.append("]");
      if (this.footerText != null && !this.footerText.isEmpty()) {
         json.append(",\"footer\": {\"text\": \"").append(escapeJson(this.footerText)).append("\"}");
      }
      json.append("}]");
      json.append("}");
      this.sendAsync(json.toString());
   }

   private void sendAsync(String jsonPayload) {
      if (this.webhookUrl == null || this.webhookUrl.isEmpty()) {
         return;
      }
      CompletableFuture.runAsync(() -> {
         HttpURLConnection conn = null;
         try {
            URL url = new URL(this.webhookUrl);
            if (this.proxyHost != null && !this.proxyHost.isEmpty() && this.proxyPort > 0) {
               Proxy proxy = new Proxy(Proxy.Type.HTTP, new InetSocketAddress(this.proxyHost, this.proxyPort));
               conn = (HttpURLConnection) url.openConnection(proxy);
               if (this.proxyUsername != null && !this.proxyUsername.isEmpty()) {
                  final String user = this.proxyUsername;
                  final String pass = this.proxyPassword != null ? this.proxyPassword : "";
                  Authenticator authenticator = new Authenticator() {
                     public PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(user, pass.toCharArray());
                     }
                  };
                  Authenticator.setDefault(authenticator);
               }
            } else {
               conn = (HttpURLConnection) url.openConnection();
            }
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("User-Agent", "AntiLitematica/3.1.0");
            conn.setDoOutput(true);
            conn.setUseCaches(false);
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(5000);
            byte[] input = jsonPayload.getBytes(StandardCharsets.UTF_8);
            conn.setFixedLengthStreamingMode(input.length);
            try (OutputStream os = conn.getOutputStream()) {
               os.write(input, 0, input.length);
            }
            int responseCode = conn.getResponseCode();
            if (responseCode != 204 && responseCode != 200) {
               String err = readStream(conn.getErrorStream());
               this.plugin.getLogger().warning("Discord Webhook send failed, response code: " + responseCode + " body: " + err);
            }
         } catch (IOException e) {
            this.plugin.getLogger().warning("Discord Webhook send failed: " + e.getMessage());
         } catch (Exception e) {
            this.plugin.getLogger().warning("Discord Webhook unknown exception: " + e.getMessage());
         } finally {
            if (conn != null) {
               conn.disconnect();
            }
         }
      });
   }

   private static String readStream(InputStream in) {
      if (in == null) {
         return "";
      }
      try (java.util.Scanner scanner = new java.util.Scanner(in, StandardCharsets.UTF_8.name())) {
         return scanner.useDelimiter("\\A").hasNext() ? scanner.next() : "";
      } catch (Exception e) {
         return "";
      }
   }

   private static String escapeJson(String s) {
      return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
   }
}