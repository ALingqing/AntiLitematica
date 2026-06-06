package top.chenray.antilitematica.threshold;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;

import top.chenray.antilitematica.AntiLitematicaPlugin;
import top.chenray.antilitematica.util.SchedulerUtil;

public class DynamicThresholdManager {

   private final AntiLitematicaPlugin plugin;
   private double currentMultiplier = 1.0;
   private boolean enabled;
   private ScheduledTask task;

   // Cached config values, refreshed on reload()
   private double tpsHigh = 19.5;
   private double tpsLow = 16.0;
   private int playersHigh = 50;
   private int playersLow = 5;
   private double minMul = 1.0;
   private double maxMul = 2.0;
   private int interval = 30;

   public DynamicThresholdManager(AntiLitematicaPlugin plugin) {
      this.plugin = plugin;
      reload();
   }

   public void reload() {
      stop();
      this.enabled = plugin.getConfig().getBoolean("dynamic_threshold.enabled", false);
      // Cache all config values once on reload
      this.tpsHigh = plugin.getConfig().getDouble("dynamic_threshold.tps.high", 19.5);
      this.tpsLow = plugin.getConfig().getDouble("dynamic_threshold.tps.low", 16.0);
      this.playersHigh = plugin.getConfig().getInt("dynamic_threshold.players.high", 50);
      this.playersLow = plugin.getConfig().getInt("dynamic_threshold.players.low", 5);
      this.minMul = plugin.getConfig().getDouble("dynamic_threshold.multiplier.min", 1.0);
      this.maxMul = plugin.getConfig().getDouble("dynamic_threshold.multiplier.max", 2.0);
      this.interval = Math.max(5, plugin.getConfig().getInt("dynamic_threshold.check_interval_seconds", 30));
      if (enabled) {
         startTask();
      }
   }

   public void stop() {
      if (task != null) {
         task.cancel();
         task = null;
      }
   }

   private void startTask() {
      task = SchedulerUtil.runTimerGlobal(plugin, this::recalculate, interval * 20L, interval * 20L);
   }

   private void recalculate() {
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
