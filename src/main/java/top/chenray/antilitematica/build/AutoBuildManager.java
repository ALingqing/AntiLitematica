package top.chenray.antilitematica.build;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Scanner;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;

import top.chenray.antilitematica.AntiLitematicaPlugin;
import top.chenray.antilitematica.config.Settings;

/**
 * Auto-downloads the latest AntiLitematica release JAR from GitHub Releases
 * and deploys it to the server plugins folder.
 *
 * Supports:
 * - Nightly scheduled download at configured time
 * - Manual trigger via /al update command
 * - Optional server reload after download
 */
public final class AutoBuildManager {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final String GITHUB_API = "https://api.github.com/repos/ALingqing/AntiLitematica/releases/latest";
    private static final int CONNECT_TIMEOUT = 10000;
    private static final int READ_TIMEOUT = 30000;

    private final AntiLitematicaPlugin plugin;
    private int taskId = -1;
    private BukkitRunnable nightlyTask;
    private volatile boolean downloading = false;

    public AutoBuildManager(AntiLitematicaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Start the nightly auto-update scheduler.
     */
    public void start() {
        scheduleNightly();
    }

    /**
     * Stop the nightly auto-update scheduler.
     */
    public void stop() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }
    }

    /**
     * Schedule the nightly download task.
     */
    private void scheduleNightly() {
        stop();

        Settings.AutoBuild cfg = plugin.settings().autoBuild();
        if (cfg == null || !cfg.enabled()) {
            return;
        }

        String timeStr = cfg.nightlyTime();
        if (timeStr == null || timeStr.isEmpty()) {
            plugin.getLogger().info("AutoUpdate: Nightly auto-update is disabled (no time configured).");
            return;
        }

        LocalTime targetTime;
        try {
            targetTime = LocalTime.parse(timeStr, TIME_FORMATTER);
        } catch (DateTimeParseException e) {
            plugin.getLogger().warning("AutoUpdate: Invalid nightly time format: " + timeStr + " (expected HH:mm)");
            return;
        }

        plugin.getLogger().info("AutoUpdate: Nightly auto-update scheduled at " + timeStr);

        // Check every 30 seconds if it's time to download
        nightlyTask = new BukkitRunnable() {
            @Override
            public void run() {
                LocalTime now = LocalTime.now();
                if (now.getHour() == targetTime.getHour() && now.getMinute() == targetTime.getMinute()) {
                    plugin.getLogger().info("AutoUpdate: Nightly auto-update triggered at " + now.format(TIME_FORMATTER));
                    downloadLatestAsync().thenAccept(success -> {
                        if (success) {
                            plugin.getLogger().info("AutoUpdate: Nightly auto-update completed successfully.");
                        } else {
                            plugin.getLogger().warning("AutoUpdate: Nightly auto-update failed. Check logs.");
                        }
                    });
                }
            }
        };
        taskId = nightlyTask.runTaskTimerAsynchronously(plugin, 0L, 600L).getTaskId(); // 600 ticks = 30 seconds
    }

    /**
     * Check if an update is available by querying the GitHub API.
     *
     * @return CompletableFuture with the latest version string, or null if error
     */
    public CompletableFuture<String> checkUpdateAsync() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                URI uri = new URI(GITHUB_API);
                URL url = uri.toURL();
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
                conn.setRequestProperty("User-Agent", "AntiLitematica/" + plugin.getDescription().getVersion());
                conn.setConnectTimeout(CONNECT_TIMEOUT);
                conn.setReadTimeout(CONNECT_TIMEOUT);

                int responseCode = conn.getResponseCode();
                if (responseCode != 200) {
                    plugin.getLogger().warning("AutoUpdate: GitHub API returned HTTP " + responseCode);
                    return null;
                }

                String json;
                try (InputStream in = conn.getInputStream();
                     Scanner scanner = new Scanner(in, StandardCharsets.UTF_8.name())) {
                    json = scanner.useDelimiter("\\A").hasNext() ? scanner.next() : "";
                }

                // Parse tag_name
                String tagName = extractJsonValue(json, "tag_name");
                if (tagName == null) {
                    plugin.getLogger().warning("AutoUpdate: Could not parse tag_name from GitHub response");
                    return null;
                }

                return tagName.startsWith("v") ? tagName.substring(1) : tagName;
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "AutoUpdate: Failed to check for updates", e);
                return null;
            }
        });
    }

    /**
     * Get the download URL for the first .jar asset from the latest release.
     *
     * @return CompletableFuture with the download URL, or null if not found
     */
    public CompletableFuture<String> getDownloadUrlAsync() {
        return CompletableFuture.supplyAsync(() -> {
            try {
                URI uri = new URI(GITHUB_API);
                URL url = uri.toURL();
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
                conn.setRequestProperty("User-Agent", "AntiLitematica/" + plugin.getDescription().getVersion());
                conn.setConnectTimeout(CONNECT_TIMEOUT);
                conn.setReadTimeout(CONNECT_TIMEOUT);

                int responseCode = conn.getResponseCode();
                if (responseCode != 200) {
                    plugin.getLogger().warning("AutoUpdate: GitHub API returned HTTP " + responseCode);
                    return null;
                }

                String json;
                try (InputStream in = conn.getInputStream();
                     Scanner scanner = new Scanner(in, StandardCharsets.UTF_8.name())) {
                    json = scanner.useDelimiter("\\A").hasNext() ? scanner.next() : "";
                }

                // Parse tag_name
                String tagName = extractJsonValue(json, "tag_name");
                if (tagName == null) return null;

                // Find the first asset with a .jar download URL
                return findJarDownloadUrl(json, tagName);
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "AutoUpdate: Failed to get download URL", e);
                return null;
            }
        });
    }

    /**
     * Download the latest release asynchronously and deploy it.
     */
    public CompletableFuture<Boolean> downloadLatestAsync() {
        if (downloading) {
            plugin.getLogger().info("AutoUpdate: Download already in progress, skipping.");
            return CompletableFuture.completedFuture(false);
        }

        downloading = true;
        return CompletableFuture.supplyAsync(() -> {
            try {
                return doDownload();
            } catch (Exception e) {
                plugin.getLogger().log(Level.SEVERE, "AutoUpdate: Download failed with exception", e);
                return false;
            } finally {
                downloading = false;
            }
        });
    }

    /**
     * Execute the download and deployment process.
     */
    private boolean doDownload() {
        Settings.AutoBuild cfg = plugin.settings().autoBuild();
        if (cfg == null || !cfg.enabled()) {
            plugin.getLogger().warning("AutoUpdate: Auto-update is not enabled.");
            return false;
        }

        String outputPath = cfg.outputPath();

        // Auto-detect plugins folder if output_path is empty
        if (outputPath == null || outputPath.isEmpty()) {
            outputPath = autoDetectPluginsFolder();
            if (outputPath == null) {
                plugin.getLogger().warning("AutoUpdate: output_path is not configured and could not auto-detect plugins folder.");
                plugin.getLogger().info("AutoUpdate: Set 'output_path' in config.yml or ensure the plugin is in a standard Paper/Spigot server setup.");
                return false;
            }
            plugin.getLogger().info("AutoUpdate: Auto-detected plugins folder: " + outputPath);
        }

        File outputDir = new File(outputPath);
        if (!outputDir.exists() && !outputDir.mkdirs()) {
            plugin.getLogger().warning("AutoUpdate: Could not create output directory: " + outputPath);
            return false;
        }

        // Step 1: Get download URL from GitHub API
        plugin.getLogger().info("AutoUpdate: Fetching latest release info from GitHub...");
        String downloadUrl;
        String version;

        try {
            URI uri = new URI(GITHUB_API);
            URL url = uri.toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
            conn.setRequestProperty("User-Agent", "AntiLitematica/" + plugin.getDescription().getVersion());
            conn.setConnectTimeout(CONNECT_TIMEOUT);
            conn.setReadTimeout(CONNECT_TIMEOUT);

            int responseCode = conn.getResponseCode();
            if (responseCode != 200) {
                plugin.getLogger().warning("AutoUpdate: GitHub API returned HTTP " + responseCode);
                return false;
            }

            String json;
            try (InputStream in = conn.getInputStream();
                 Scanner scanner = new Scanner(in, StandardCharsets.UTF_8.name())) {
                json = scanner.useDelimiter("\\A").hasNext() ? scanner.next() : "";
            }

            String tagName = extractJsonValue(json, "tag_name");
            if (tagName == null) {
                plugin.getLogger().warning("AutoUpdate: Could not parse tag_name from response");
                return false;
            }
            version = tagName.startsWith("v") ? tagName.substring(1) : tagName;

            downloadUrl = findJarDownloadUrl(json, tagName);
            if (downloadUrl == null) {
                plugin.getLogger().warning("AutoUpdate: No .jar asset found in the latest release.");
                return false;
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "AutoUpdate: Failed to query GitHub API", e);
            return false;
        }

        // Check if already up-to-date
        String currentVersion = plugin.getDescription().getVersion();
        if (version.equals(currentVersion)) {
            plugin.getLogger().info("AutoUpdate: Already at the latest version (v" + currentVersion + ").");
            return true;
        }

        // Step 2: Download the JAR file
        String jarName = "AntiLitematica-" + version + ".jar";
        File tempFile = new File(outputDir, jarName + ".downloading");
        File destFile = new File(outputDir, jarName);

        plugin.getLogger().info("AutoUpdate: Downloading v" + version + " from GitHub...");
        plugin.getLogger().info("AutoUpdate: URL: " + downloadUrl);

        try {
            URL jarUrl = new URI(downloadUrl).toURL();
            HttpURLConnection jarConn = (HttpURLConnection) jarUrl.openConnection();
            jarConn.setRequestProperty("User-Agent", "AntiLitematica/" + currentVersion);
            jarConn.setConnectTimeout(CONNECT_TIMEOUT);
            jarConn.setReadTimeout(READ_TIMEOUT);
            jarConn.setInstanceFollowRedirects(true);

            int jarResponseCode = jarConn.getResponseCode();
            if (jarResponseCode != 200) {
                plugin.getLogger().warning("AutoUpdate: Download returned HTTP " + jarResponseCode);
                return false;
            }

            long contentLength = jarConn.getContentLengthLong();
            plugin.getLogger().info("AutoUpdate: File size: " + (contentLength > 0 ? (contentLength / 1024) + " KB" : "unknown"));

            try (InputStream in = jarConn.getInputStream();
                 FileOutputStream fos = new FileOutputStream(tempFile);
                 ReadableByteChannel rbc = Channels.newChannel(in)) {
                fos.getChannel().transferFrom(rbc, 0, Long.MAX_VALUE);
            }

            // Verify the downloaded file
            if (!tempFile.exists() || tempFile.length() == 0) {
                plugin.getLogger().severe("AutoUpdate: Downloaded file is empty or missing.");
                tempFile.delete();
                return false;
            }

            // Rename temp file to final name
            if (destFile.exists()) {
                destFile.delete();
            }
            if (!tempFile.renameTo(destFile)) {
                plugin.getLogger().warning("AutoUpdate: Failed to rename temp file, copying instead.");
                try {
                    java.nio.file.Files.copy(tempFile.toPath(), destFile.toPath(),
                            java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    tempFile.delete();
                } catch (Exception e) {
                    plugin.getLogger().log(Level.SEVERE, "AutoUpdate: Failed to save JAR file", e);
                    tempFile.delete();
                    return false;
                }
            }

            plugin.getLogger().info("AutoUpdate: Successfully downloaded " + jarName
                    + " (" + (destFile.length() / 1024) + " KB)");

            // Also remove old version JARs
            cleanupOldJars(outputDir, jarName);

            // Step 3: Post-download action (reload command)
            String postCmd = cfg.postBuildCommand();
            if (postCmd != null && !postCmd.isEmpty()) {
                plugin.getLogger().info("AutoUpdate: Executing post-update command: " + postCmd);
                Bukkit.getScheduler().runTask(plugin, () -> {
                    try {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), postCmd);
                    } catch (Exception e) {
                        plugin.getLogger().log(Level.WARNING, "AutoUpdate: Failed to execute post-update command", e);
                    }
                });
            } else if (cfg.autoReload()) {
                plugin.getLogger().info("AutoUpdate: Running plugman reload...");
                Bukkit.getScheduler().runTask(plugin, () -> {
                    try {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "plugman reload AntiLitematica");
                    } catch (Exception e) {
                        plugin.getLogger().log(Level.WARNING, "AutoUpdate: plugman reload failed", e);
                    }
                });
            } else {
                plugin.getLogger().info("AutoUpdate: Download complete. Restart server or use /plugman reload to apply.");
            }

            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "AutoUpdate: Download failed", e);
            if (tempFile.exists()) tempFile.delete();
            return false;
        }
    }

    /**
     * Remove old AntiLitematica JARs from the output directory, keeping only the current one.
     */
    private void cleanupOldJars(File dir, String currentJarName) {
        File[] oldJars = dir.listFiles((d, name) ->
                name.startsWith("AntiLitematica-") && name.endsWith(".jar") && !name.equals(currentJarName));
        if (oldJars != null) {
            for (File old : oldJars) {
                if (old.delete()) {
                    plugin.getLogger().info("AutoUpdate: Removed old JAR: " + old.getName());
                }
            }
        }
    }

    /**
     * Find the download URL of the first .jar asset in the release JSON.
     */
    private String findJarDownloadUrl(String json, String tagName) {
        // Look for assets array and find a .jar file
        String assetsKey = "\"assets\":[";
        int assetsStart = json.indexOf(assetsKey);
        if (assetsStart < 0) {
            // Fallback: construct URL from tag
            return "https://github.com/ALingqing/AntiLitematica/releases/download/"
                    + tagName + "/AntiLitematica-" + (tagName.startsWith("v") ? tagName.substring(1) : tagName) + ".jar";
        }

        // Find all browser_download_url entries within assets
        String searchKey = "\"browser_download_url\":\"";
        int searchStart = assetsStart;
        while (true) {
            int idx = json.indexOf(searchKey, searchStart);
            if (idx < 0) break;
            idx += searchKey.length();
            int end = json.indexOf("\"", idx);
            if (end < 0) break;
            String url = json.substring(idx, end);
            if (url.endsWith(".jar")) {
                return url;
            }
            searchStart = end + 1;
        }

        // Fallback
        return "https://github.com/ALingqing/AntiLitematica/releases/download/"
                + tagName + "/AntiLitematica-" + (tagName.startsWith("v") ? tagName.substring(1) : tagName) + ".jar";
    }

    /**
     * Extract a simple string value from a JSON key.
     */
    private static String extractJsonValue(String json, String key) {
        String search = "\"" + key + "\":\"";
        int start = json.indexOf(search);
        if (start < 0) return null;
        start += search.length();
        int end = json.indexOf("\"", start);
        if (end < 0) return null;
        return json.substring(start, end);
    }

    /**
     * Auto-detect the server's plugins folder by checking common paths.
     * Returns the detected path, or null if not found.
     */
    private String autoDetectPluginsFolder() {
        // The plugin's data folder is typically: ./plugins/AntiLitematica/
        // So the parent's parent should be the server root
        File dataFolder = plugin.getDataFolder(); // ./plugins/AntiLitematica/
        if (dataFolder != null) {
            File parent = dataFolder.getParentFile(); // ./plugins/
            if (parent != null && parent.exists() && parent.isDirectory()) {
                // Double-check: this folder should contain .jar files
                File[] jars = parent.listFiles((d, n) -> n.endsWith(".jar"));
                if (jars != null && jars.length > 0) {
                    return parent.getAbsolutePath();
                }
            }
            // Try parent of parent (server root) + /plugins
            File grandParent = dataFolder.getParentFile() != null
                    ? dataFolder.getParentFile().getParentFile() : null;
            if (grandParent != null) {
                File pluginsDir = new File(grandParent, "plugins");
                if (pluginsDir.exists() && pluginsDir.isDirectory()) {
                    File[] jars = pluginsDir.listFiles((d, n) -> n.endsWith(".jar"));
                    if (jars != null && jars.length > 0) {
                        return pluginsDir.getAbsolutePath();
                    }
                }
            }
        }
        // Last resort: check common relative paths
        String[] candidates = { "plugins", "../plugins", "./plugins" };
        for (String path : candidates) {
            File dir = new File(path);
            if (dir.exists() && dir.isDirectory()) {
                File[] jars = dir.listFiles((d, n) -> n.endsWith(".jar"));
                if (jars != null && jars.length > 0) {
                    return dir.getAbsolutePath();
                }
            }
        }
        return null;
    }

    /**
     * Reschedule after config reload.
     */
    public void reschedule() {
        scheduleNightly();
    }
}
