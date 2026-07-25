package top.chenray.antilitematica.integration;

import java.lang.reflect.Method;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import top.chenray.antilitematica.AntiLitematicaPlugin;

public final class GrimACIntegration implements AntiCheatIntegration {
   private final AntiLitematicaPlugin plugin;
   private boolean active = false;
   private Object grimPlayerDataManager;
   private Method getPlayerDataMethod;
   private Method flagMethod;

   public GrimACIntegration(AntiLitematicaPlugin plugin) {
      this.plugin = plugin;
   }

   public void enable() {
      if (Bukkit.getPluginManager().getPlugin("GrimAC") == null) {
         this.plugin.getLogger().warning("GrimAC not found, integration disabled.");
         return;
      }
      try {
         Class<?> grimApi = Class.forName("ac.grim.grimac.api.GrimAPI");
         Method getInstance = grimApi.getDeclaredMethod("getInstance");
         Object instance = getInstance.invoke(null);
         Method getPlayerDataManager = grimApi.getMethod("getPlayerDataManager");
         this.grimPlayerDataManager = getPlayerDataManager.invoke(instance);
         this.getPlayerDataMethod = this.grimPlayerDataManager.getClass().getMethod("getPlayerData", java.util.UUID.class);

         // Try to find the flag method - GrimAC API may vary by version
         Class<?> grimPlayerDataClass = null;
         for (Method m : this.grimPlayerDataManager.getClass().getMethods()) {
            if (m.getName().equals("getPlayerData") && m.getParameterCount() == 1) {
               grimPlayerDataClass = m.getReturnType();
               break;
            }
         }
         if (grimPlayerDataClass != null) {
            try {
               this.flagMethod = grimPlayerDataClass.getMethod("flag", boolean.class, String.class, String.class, int.class);
            } catch (NoSuchMethodException e1) {
               try {
                  this.flagMethod = grimPlayerDataClass.getMethod("flag", boolean.class, String.class, String.class);
               } catch (NoSuchMethodException e2) {
                  try {
                     this.flagMethod = grimPlayerDataClass.getMethod("flag", String.class, int.class);
                  } catch (NoSuchMethodException e3) {
                     this.plugin.getLogger().warning("GrimAC: Could not find flag method signature.");
                  }
               }
            }
         }
         this.active = this.flagMethod != null;
         if (this.active) {
            this.plugin.getLogger().info("GrimAC integration enabled.");
         }
      } catch (Exception e) {
         this.plugin.getLogger().warning("GrimAC integration failed: " + e.getMessage());
      }
   }

   public void disable() {
      this.active = false;
      this.grimPlayerDataManager = null;
      this.getPlayerDataMethod = null;
      this.flagMethod = null;
   }

   public boolean isActive() {
      return this.active;
   }

   public void flag(Player player, String checkName, int vl, String details) {
      if (!this.isActive() || player == null) return;
      try {
         Object playerData = this.getPlayerDataMethod.invoke(this.grimPlayerDataManager, player.getUniqueId());
         if (playerData == null) return;
         if (this.flagMethod.getParameterCount() == 4) {
            this.flagMethod.invoke(playerData, false, checkName, details, vl);
         } else if (this.flagMethod.getParameterCount() == 3) {
            this.flagMethod.invoke(playerData, false, checkName, details);
         } else if (this.flagMethod.getParameterCount() == 2) {
            this.flagMethod.invoke(playerData, checkName, vl);
         }
      } catch (Exception e) {
         this.plugin.getLogger().fine("GrimAC flag failed: " + e.getMessage());
      }
   }

   public String getName() {
      return "GrimAC";
   }
}
