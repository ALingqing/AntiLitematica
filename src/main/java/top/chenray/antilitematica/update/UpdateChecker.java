package top.chenray.antilitematica.update;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Scanner;
import java.util.concurrent.CompletableFuture;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import top.chenray.antilitematica.AntiLitematicaPlugin;
import top.chenray.antilitematica.util.Msg;

public final class UpdateChecker implements Listener {
    private static final String GITHUB_API = "https://api.github.com/repos/ALingqing/AntiLitematica/releases/latest";
    private static final String GITHUB_DOWNLOAD = "https://github.com/ALingqing/AntiLitematica/releases/latest";

    private final AntiLitematicaPlugin plugin;
    private volatile String latestVersion;
    private volatile String latestDownloadUrl;
    private volatile boolean updateAvailable;
    private volatile boolean checked;

    public UpdateChecker(AntiLitematicaPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        Bukkit.getPluginManager().registerEvents(this, plugin);
        checkAsync();
    }

    /**
     * Check for updates asynchronously.
     */
    public CompletableFuture<Void> checkAsync() {
        return CompletableFuture.runAsync(() -> {
            try {
                URI uri = new URI(GITHUB_API);
                URL url = uri.toURL();
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
                conn.setRequestProperty("User-Agent", "AntiLitematica/" + plugin.getDescription().getVersion());
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                int responseCode = conn.getResponseCode();
                if (responseCode != 200) {
                    plugin.getLogger().warning("Update check failed: HTTP " + responseCode);
                    return;
                }

                String json;
                try (InputStream in = conn.getInputStream();
                     Scanner scanner = new Scanner(in, StandardCharsets.UTF_8.name())) {
                    json = scanner.useDelimiter("\\A").hasNext() ? scanner.next() : "";
                }

                // Parse tag_name from JSON response
                String tagName = extractJsonString(json, "tag_name");
                if (tagName == null) {
                    plugin.getLogger().warning("Update check: could not parse tag_name from response");
                    return;
                }

                // Normalize: remove leading 'v' if present
                String remote = tagName.startsWith("v") ? tagName.substring(1) : tagName;
                String local = plugin.getDescription().getVersion();

                latestVersion = remote;
                latestDownloadUrl = extractJsonString(json, "html_url");
                if (latestDownloadUrl == null) {
                    latestDownloadUrl = GITHUB_DOWNLOAD;
                }
                updateAvailable = compareVersions(remote, local) > 0;
                checked = true;

                if (updateAvailable) {
                    plugin.getLogger().info("Update available: v" + remote + " (current: v" + local + ")");
                    plugin.getLogger().info("Download: " + latestDownloadUrl);
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Update check failed: " + e.getMessage());
            }
        });
    }

    /**
     * Compare two semver strings. Returns >0 if a > b, <0 if a < b, 0 if equal.
     */
    private static int compareVersions(String a, String b) {
        String[] partsA = a.split("\\.");
        String[] partsB = b.split("\\.");
        int len = Math.max(partsA.length, partsB.length);
        for (int i = 0; i < len; i++) {
            int numA = i < partsA.length ? parseIntSafe(partsA[i]) : 0;
            int numB = i < partsB.length ? parseIntSafe(partsB[i]) : 0;
            if (numA != numB) return numA - numB;
        }
        return 0;
    }

    private static int parseIntSafe(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    /**
     * Extract a simple string value from a JSON key.
     */
    private static String extractJsonString(String json, String key) {
        String search = "\"" + key + "\":\"";
        int start = json.indexOf(search);
        if (start < 0) return null;
        start += search.length();
        int end = json.indexOf("\"", start);
        if (end < 0) return null;
        return json.substring(start, end);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!checked || !updateAvailable) return;
        if (!player.hasPermission("antilitematica.admin")) return;

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            player.sendMessage(Msg.color("&7[&cAntiLitematica&7] &eUpdate available: &fv" + latestVersion
                    + " &7(current: v" + plugin.getDescription().getVersion() + ")"));
            player.sendMessage(Msg.color("&7Download: &b" + latestDownloadUrl));
        }, 40L); // 2 seconds delay
    }

    public boolean isUpdateAvailable() {
        return updateAvailable;
    }

    public String getLatestVersion() {
        return latestVersion;
    }

    public String getLatestDownloadUrl() {
        return latestDownloadUrl;
    }
}
