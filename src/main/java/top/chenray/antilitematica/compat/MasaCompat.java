package top.chenray.antilitematica.compat;

import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.Player;

import top.chenray.antilitematica.AntiLitematicaPlugin;
import top.chenray.antilitematica.config.Settings;

/**
 * MasaMods (Masa家族模组) 兼容管理器。
 * <p>
 * Masa 家族包括：
 * - Litematica (投影) — 本项目主要检测对象
 * - Tweakeroo (推客入) — 灵活放置/强制放置等辅助功能
 * - MiniHUD — 显示信息叠加层，无检测冲突
 * - Item Scroller — 物品快捷移动，无检测冲突
 * <p>
 * 此类统一管理所有 Masa 模组的兼容性策略：
 * 1. 检测玩家是否使用 Tweakeroo (通过通道/行为特征)
 * 2. 根据配置调整检测灵敏度 (tweakerooMode)
 * 3. 提供查询接口供各检测点调用
 */
public final class MasaCompat {

   private final AntiLitematicaPlugin plugin;
   private final Settings settings;

   // 已知的 Masa 模组插件通道
   private static final Set<String> MASA_CHANNELS = Set.of(
         "servux:litematics",        // Litematica/Servux
         "servux:litematica",        // Litematica alt
         "litematica:hello",         // Litematica 握手
         "litematica:main",          // Litematica 主通道
         "litematica:place",         // Litematica 操作请求
         "tweakeroo:hello",          // Tweakeroo 握手
         "tweakeroo:main",           // Tweakeroo 主通道
         "minihud:hello",            // MiniHUD
         "itemscroller:hello"        // Item Scroller
   );

   // 各模组的关键检测特征
   private static final Set<String> TWEAKEROO_CHANNELS = Set.of("tweakeroo:hello", "tweakeroo:main");
   private static final Set<String> LITEMATICA_CHANNELS = Set.of(
         "servux:litematics", "servux:litematica",
         "litematica:hello", "litematica:main", "litematica:place"
   );

   // 玩家 -> 检测到的 Masa 模组列表
   private final Map<UUID, MasaMods> playerMods = new ConcurrentHashMap<>();

   public MasaCompat(AntiLitematicaPlugin plugin, Settings settings) {
      this.plugin = plugin;
      this.settings = settings;
   }

   /**
    * 记录玩家注册的 Masa 模组通道。
    * 由 ModChannelDetector 在检测到通道注册时调用。
    *
    * @return true 如果是已知的 Masa 通道
    */
   public boolean detectChannel(Player player, String channel) {
      if (channel == null || !MASA_CHANNELS.contains(channel.toLowerCase(Locale.ROOT))) {
         return false;
      }
      MasaMods mods = playerMods.computeIfAbsent(player.getUniqueId(), k -> new MasaMods());

      if (TWEAKEROO_CHANNELS.contains(channel)) {
         mods.tweakeroo = true;
      }
      if (LITEMATICA_CHANNELS.contains(channel)) {
         mods.litematica = true;
      }
      if (channel.equalsIgnoreCase("minihud:hello")) {
         mods.minihud = true;
      }
      if (channel.equalsIgnoreCase("itemscroller:hello")) {
         mods.itemScroller = true;
      }
      return true;
   }

   /**
    * 清除玩家的模组检测记录（退出时调用）。
    */
   public void clearPlayer(Player player) {
      playerMods.remove(player.getUniqueId());
   }

   /**
    * 清除玩家的模组检测记录（通过 UUID）。
    */
   public void clearPlayer(UUID uuid) {
      playerMods.remove(uuid);
   }

   // ======================== 查询接口 ========================

   /**
    * 玩家是否使用了任何 Masa 模组。
    */
   public boolean hasAnyMasaMod(Player player) {
      MasaMods mods = playerMods.get(player.getUniqueId());
      return mods != null && (mods.litematica || mods.tweakeroo || mods.minihud || mods.itemScroller);
   }

   /**
    * 玩家是否使用了 Litematica (投影)。
    */
   public boolean hasLitematica(Player player) {
      MasaMods mods = playerMods.get(player.getUniqueId());
      return mods != null && mods.litematica;
   }

   /**
    * 玩家是否使用了 Tweakeroo (推客入)。
    */
   public boolean hasTweakeroo(Player player) {
      MasaMods mods = playerMods.get(player.getUniqueId());
      return mods != null && mods.tweakeroo;
   }

   /**
    * 玩家是否使用了 MiniHUD。
    */
   public boolean hasMiniHUD(Player player) {
      MasaMods mods = playerMods.get(player.getUniqueId());
      return mods != null && mods.minihud;
   }

   /**
    * 玩家是否使用了 ItemScroller。
    */
   public boolean hasItemScroller(Player player) {
      MasaMods mods = playerMods.get(player.getUniqueId());
      return mods != null && mods.itemScroller;
   }

   // ======================== 兼容策略 ========================

   /**
    * 是否应跳过射线追踪检测 (enforce_raytrace)。
    * <p>
    * Tweakeroo 的柔性放置 (flexi placement) 会修改命中向量，
    * 导致服务器端射线追踪结果不匹配。开启 tweakerooMode 时跳过此检查。
    */
   public boolean shouldSkipRaytrace(Player player) {
      return settings.compatibility() != null
            && settings.compatibility().tweakerooMode()
            && hasTweakeroo(player);
   }

   /**
    * 获取 EasyPlace 信号检测的最低连续命中次数。
    * <p>
    * Tweakeroo 模式下从 3 次提高到 8 次，减少误报。
    */
   public int getEasyPlaceThreshold() {
      return settings.compatibility() != null
            && settings.compatibility().tweakerooMode() ? 8 : 3;
   }

   /**
    * 获取连续同类型方块检测的触发比例。
    * <p>
    * Tweakeroo 模式下从 80% 提高到 95%，减少建筑玩家的误报。
    */
   public double getConsecutiveSameTypeRatio() {
      return settings.compatibility() != null
            && settings.compatibility().tweakerooMode() ? 0.95 : 0.8;
   }

   /**
    * 获取玩家的 Masa 模组摘要信息（用于调试/日志）。
    */
   public String getPlayerSummary(Player player) {
      MasaMods mods = playerMods.get(player.getUniqueId());
      if (mods == null) return "none";
      StringBuilder sb = new StringBuilder();
      if (mods.litematica) sb.append("Litematica ");
      if (mods.tweakeroo) sb.append("Tweakeroo ");
      if (mods.minihud) sb.append("MiniHUD ");
      if (mods.itemScroller) sb.append("ItemScroller ");
      return sb.toString().trim();
   }

   /**
    * 玩家的 Masa 模组状态。
    */
   private static final class MasaMods {
      boolean litematica;
      boolean tweakeroo;
      boolean minihud;
      boolean itemScroller;
   }
}
