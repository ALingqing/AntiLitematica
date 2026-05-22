package top.chenray.antilitematica.integration;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import top.chenray.antilitematica.AntiLitematicaPlugin;

/**
 * Integration with Matrix anti-cheat.
 * <p>
 * Requires Matrix.jar (with API) in the classpath.
 * Uses reflection to avoid compile-time dependency.
 */
public final class MatrixIntegration implements AntiCheatIntegration {

    private final AntiLitematicaPlugin plugin;
    private boolean active = false;

    // Reflection: Matrix.getAPI()
    private Object matrixAPI;

    public MatrixIntegration(AntiLitematicaPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void enable() {
        try {
            Class<?> apiClass = Class.forName("com.matrixplugins.matrixapi.MatrixAPI");
            java.lang.reflect.Method getInstance = apiClass.getMethod("getAPI");
            this.matrixAPI = getInstance.invoke(null);
            if (this.matrixAPI != null) {
                this.active = true;
                this.plugin.getLogger().info("Matrix integration enabled.");
            }
        } catch (Exception e) {
            this.plugin.getLogger().warning("Matrix not found, integration disabled.");
        }
    }

    @Override
    public void disable() {
        this.active = false;
        this.matrixAPI = null;
    }

    @Override
    public boolean isActive() {
        return this.active;
    }

    @Override
    public void flag(Player player, String checkName, int vl, String details) {
        if (!this.active || player == null) return;

        try {
            // MatrixAPI.getAPI().getPlayerData(player)
            java.lang.reflect.Method getPlayerData = matrixAPI.getClass().getMethod("getPlayerData", Player.class);
            Object playerData = getPlayerData.invoke(matrixAPI, player);
            if (playerData == null) return;

            // PlayerData.addViolation(String checkName, int amount)
            java.lang.reflect.Method addViolation = playerData.getClass()
                    .getMethod("addViolation", String.class, int.class);
            addViolation.invoke(playerData, checkName, vl);
        } catch (Exception e) {
            this.plugin.getLogger().fine("Matrix flag failed: " + e.getMessage());
        }
    }

    @Override
    public String getName() {
        return "Matrix";
    }
}
