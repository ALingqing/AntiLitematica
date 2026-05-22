package top.chenray.antilitematica.integration;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import top.chenray.antilitematica.AntiLitematicaPlugin;

/**
 * Integration with Vulcan anti-cheat.
 * <p>
 * Requires VulcanAPI.jar in the classpath (provided or soft-depend).
 * Uses reflection to avoid compile-time dependency — the integration
 * is only active when Vulcan is installed on the server.
 */
public final class VulcanIntegration implements AntiCheatIntegration {

    private final AntiLitematicaPlugin plugin;
    private boolean active = false;

    // Reflection targets
    private Object vulcanAPI;
    private Object vulcanPlayer;

    public VulcanIntegration(AntiLitematicaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void enable() {
        try {
            Class<?> apiClass = Class.forName("me.frep.vulcan.api.VulcanAPI");
            java.lang.reflect.Method getApi = apiClass.getMethod("getApi");
            this.vulcanAPI = getApi.invoke(null);
            if (this.vulcanAPI != null) {
                this.active = true;
                this.plugin.getLogger().info("Vulcan integration enabled.");
            }
        } catch (Exception e) {
            this.plugin.getLogger().warning("Vulcan not found, integration disabled.");
        }
    }

    @Override
    public void disable() {
        this.active = false;
        this.vulcanAPI = null;
        this.vulcanPlayer = null;
    }

    @Override
    public boolean isActive() {
        return this.active;
    }

    @Override
    public void flag(Player player, String checkName, int vl, String details) {
        if (!this.active || player == null) return;

        try {
            // VulcanAPI.getApi().getPlayer(player.getUniqueId())
            java.lang.reflect.Method getPlayer = vulcanAPI.getClass().getMethod("getPlayer", java.util.UUID.class);
            Object vp = getPlayer.invoke(vulcanAPI, player.getUniqueId());
            if (vp == null) return;

            // VulcanPlayer.flag(String checkName, String details, int vl)
            java.lang.reflect.Method flag = vp.getClass().getMethod("flag", String.class, String.class, int.class);
            flag.invoke(vp, checkName, details, vl);
        } catch (Exception e) {
            this.plugin.getLogger().fine("Vulcan flag failed: " + e.getMessage());
        }
    }

    @Override
    public String getName() {
        return "Vulcan";
    }
}
