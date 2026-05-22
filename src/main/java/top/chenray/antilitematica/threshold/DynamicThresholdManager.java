package top.chenray.antilitematica.threshold;

import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;

import top.chenray.antilitematica.AntiLitematicaPlugin;

public class DynamicThresholdManager {

   private final AntiLitematicaPlugin plugin;
   private double currentMultiplier = 1.0;
   private boolean enabled;
   private int taskId = -1;

   public DynamicThresholdManager(AntiLitematicaPlugin plugin) {
      this.plugin = plugin;
      reload();
   }

   public void reload() {
      stop();
      this.enabled = plugin.getConfig().getBoolean("dynamic_threshold.enabled", false);
      if (enabled) {
         startTask();
      }
   }

   public void stop() {
      if (taskId != -1) {
         Bukkit.getScheduler().cancelTask(taskId);
         taskId = -1;
      }
   }

   private void startTask() {
      int interval = Math.max(5, plugin.getConfig().getInt("dynamic_threshold.check_interval_seconds", 30));
      taskId = new BukkitRunnable() {
         @Override
         public void run() {
            recalculate();
         }
      }.runTaskTimer(plugin, interval * 20L, interval * 20L).getTaskId();
   }

   private void recalculate() {
      double tpsHigh = plugin.getConfig().getDouble("dynamic_threshold.tps.high", 19.5);
      double tpsLow = plugin.getConfig().getDouble("dynamic_threshold.tps.low", 16.0);
      int playersHigh = plugin.getConfig().getInt("dynamic_threshold.players.high", 50);
      int playersLow = plugin.getConfig().getInt("dynamic_threshold.players.low", 5);

      double minMul = plugin.getConfig().getDouble("dynamic_threshold.multiplier.min", 1.0);
      double maxMul = plugin.getConfig().getDouble("dynamic_threshold.multiplier.max", 2.0);

      double tps = getRecentTPS();
      int online = Bukkit.getOnlinePlayers().size();

      // TPS factor: lower TPS = higher factor (more lenient)
      double tpsFactor = 0.0;
      if (tps >= tpsHigh) {
         tpsFactor = 0.0;
      } else if (tps <= tpsLow) {
         tpsFactor = 1.0;
      } else {
         tpsFactor = (tpsHigh - tps) / (tpsHigh - tpsLow);
      }

      // Player factor: more players = higher factor (more lenient)
      double playerFactor = 0.0;
      if (online <= playersLow) {
         playerFactor = 0.0;
      } else if (online >= playersHigh) {
         playerFactor = 1.0;
      } else {
         playerFactor = (double) (online - playersLow) / (playersHigh - playersLow);
      }

      // Combine factors (take max to be more responsive)
      double stress = Math.max(tpsFactor, playerFactor);
      this.currentMultiplier = minMul + (maxMul - minMul) * stress;
   }

   private double getRecentTPS() {
      try {
         double[] tps = Bukkit.getTPS();
         return tps.length > 0 ? Math.min(20.0, tps[0]) : 20.0;
      } catch (Exception e) {
         return 20.0;
      }
   }

   public boolean isEnabled() {
      return enabled;
   }

   public double getCurrentMultiplier() {
      return enabled ? currentMultiplier : 1.0;
   }

   public int adjustInt(int base) {
      if (!enabled) return base;
      return Math.max(1, (int) Math.round(base * currentMultiplier));
   }

   public double adjustDouble(double base) {
      if (!enabled) return base;
      return base * currentMultiplier;
   }

   public long adjustLong(long base) {
      if (!enabled) return base;
      return Math.max(1, (long) Math.round(base * currentMultiplier));
   }
}
