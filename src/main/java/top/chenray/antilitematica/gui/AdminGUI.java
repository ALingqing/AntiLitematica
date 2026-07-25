package top.chenray.antilitematica.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import top.chenray.antilitematica.AntiLitematicaPlugin;
import top.chenray.antilitematica.config.Settings;
import top.chenray.antilitematica.punish.ViolationRecord;
import top.chenray.antilitematica.util.Msg;
import top.chenray.antilitematica.util.StatsTracker;

/**
 * Inventory-based GUI for AntiLitematica administration.
 * Provides a visual interface for managing detection, punishment, and per-world settings.
 */
public final class AdminGUI implements Listener {

   private static final String PREFIX = ChatColor.DARK_GRAY + "[" + ChatColor.RED + "AntiLitematica" + ChatColor.DARK_GRAY + "] ";
   private static final String GUI_TITLE_MAIN = ChatColor.RED + "AntiLitematica 管理面板";
   private static final String GUI_TITLE_PLAYERS = ChatColor.RED + "玩家列表";
   private static final String GUI_TITLE_WORLDS = ChatColor.RED + "世界配置";
   private static final String GUI_TITLE_WHITELIST = ChatColor.RED + "白名单管理";

   private final AntiLitematicaPlugin plugin;

   public AdminGUI(AntiLitematicaPlugin plugin) {
      this.plugin = plugin;
   }

   // ======================== Inventory Holder ========================

   /** Custom holder to identify our GUIs. */
   private static final class Holder implements InventoryHolder {
      final String page;    // "main", "players", "worlds", "whitelist"
      final int pageNum;    // for paginated pages

      Holder(String page) {
         this(page, 0);
      }

      Holder(String page, int pageNum) {
         this.page = page;
         this.pageNum = pageNum;
      }

      @Override
      public Inventory getInventory() { return null; }
   }

   // ======================== Open Methods ========================

   public void openMain(Player player) {
      Inventory inv = buildMainMenu();
      player.openInventory(inv);
   }

   public void openPlayers(Player player, int page) {
      Inventory inv = buildPlayerList(player, page);
      if (inv != null) player.openInventory(inv);
   }

   public void openWorlds(Player player) {
      Inventory inv = buildWorldSettings();
      player.openInventory(inv);
   }

   public void openWhitelist(Player player) {
      Inventory inv = buildWhitelist();
      player.openInventory(inv);
   }

   // ======================== Builders ========================

   private Inventory buildMainMenu() {
      Inventory inv = Bukkit.createInventory(new Holder("main"), 54, GUI_TITLE_MAIN);
      Settings s = plugin.settings();

      // ---- Row 0: Status Info (glass pane decoration) ----
      ItemStack border = createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
      for (int i = 0; i < 9; i++) inv.setItem(i, border);

      // ---- Row 1: Plugin Status ----
      inv.setItem(9, createItem(Material.REDSTONE_BLOCK,
            ChatColor.GREEN + "全局状态",
            ChatColor.GRAY + "全局: " + statusColor(s.enabled()) + (s.enabled() ? "已启用" : "已禁用"),
            ChatColor.GRAY + "检测: " + statusColor(s.detection().enabled()) + (s.detection().enabled() ? "已启用" : "已禁用"),
            ChatColor.GRAY + "反Printer: " + statusColor(s.antiPrinter().enabled()) + (s.antiPrinter().enabled() ? "已启用" : "已禁用"),
            ChatColor.GRAY + "命令防护: " + statusColor(s.commandGuard().enabled()) + (s.commandGuard().enabled() ? "已启用" : "已禁用"),
            ChatColor.GRAY + "渐进惩罚: " + statusColor(s.graduatedPunishment() != null && s.graduatedPunishment().enabled())
                  + ((s.graduatedPunishment() != null && s.graduatedPunishment().enabled()) ? "已启用" : "已禁用")
      ));

      // Detection Action
      inv.setItem(11, createItem(Material.BOOK,
            ChatColor.GOLD + "检测动作",
            ChatColor.GRAY + "当前: " + ChatColor.WHITE + s.detection().action().name(),
            ChatColor.GRAY + "世界白名单: " + (s.worldWhitelist() != null && s.worldWhitelist().enabled() ? "已启用" : "已禁用")
      ));

      // Storage Type
      inv.setItem(13, createItem(Material.CHEST,
            ChatColor.GOLD + "存储方式",
            ChatColor.GRAY + "类型: " + ChatColor.WHITE + (s.graduatedPunishment() != null ? s.graduatedPunishment().storage() : "none")
      ));

      // ---- Row 2: Feature Quick Toggles ----
      inv.setItem(18, createToggleItem(Material.LEVER,
            ChatColor.AQUA + "切换 检测",
            s.detection().enabled(), "detection"));
      inv.setItem(20, createToggleItem(Material.STONE_AXE,
            ChatColor.AQUA + "切换 反Printer",
            s.antiPrinter().enabled(), "anti_printer"));
      inv.setItem(22, createToggleItem(Material.COMMAND_BLOCK,
            ChatColor.AQUA + "切换 命令防护",
            s.commandGuard().enabled(), "command_guard"));
      inv.setItem(24, createToggleItem(Material.EXPERIENCE_BOTTLE,
            ChatColor.AQUA + "切换 渐进惩罚",
            s.graduatedPunishment() != null && s.graduatedPunishment().enabled(), "graduated"));

      // ---- Row 3: Navigation ----
      inv.setItem(27, createItem(Material.PLAYER_HEAD,
            ChatColor.YELLOW + "玩家列表",
            ChatColor.GRAY + "查看在线玩家违规状态"));
      inv.setItem(29, createItem(Material.GRASS_BLOCK,
            ChatColor.YELLOW + "世界配置",
            ChatColor.GRAY + "管理每个世界的检测设置"));
      inv.setItem(31, createItem(Material.WRITABLE_BOOK,
            ChatColor.YELLOW + "白名单管理",
            ChatColor.GRAY + "管理违规白名单"));
      inv.setItem(33, createItem(Material.COMPASS,
            ChatColor.YELLOW + "多世界状态",
            ChatColor.GRAY + "查看所有世界的有效配置"));

      // ---- Row 4: Actions ----
      StatsTracker stats = plugin.getStatsTracker();
      int totalDet = stats != null ? stats.getTotalDetections() : 0;
      int totalPun = stats != null ? stats.getTotalPunishments() : 0;
      inv.setItem(36, createItem(Material.FILLED_MAP,
            ChatColor.LIGHT_PURPLE + "统计信息",
            ChatColor.GRAY + "检测次数: " + ChatColor.WHITE + totalDet,
            ChatColor.GRAY + "处罚次数: " + ChatColor.WHITE + totalPun,
            ChatColor.GRAY + "命中率: " + ChatColor.WHITE + String.format("%.1f%%", stats != null ? stats.getHitRate() : 0)));

      inv.setItem(38, createItem(Material.REDSTONE,
            ChatColor.LIGHT_PURPLE + "重载配置",
            ChatColor.GRAY + "重新加载配置文件"));

      inv.setItem(40, createItem(Material.BARRIER,
            ChatColor.LIGHT_PURPLE + "清除所有违规记录",
            ChatColor.RED + "警告: 此操作不可撤销!"));

      inv.setItem(42, createItem(Material.DARK_OAK_DOOR,
            ChatColor.RED + "关闭面板"));

      // ---- Bottom border ----
      for (int i = 45; i < 54; i++) inv.setItem(i, border);

      return inv;
   }

   private Inventory buildPlayerList(Player viewer, int page) {
      List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
      if (online.isEmpty()) {
         viewer.sendMessage(PREFIX + ChatColor.YELLOW + "当前没有在线玩家。");
         return null;
      }

      int perPage = 36; // 4 rows of 9
      int totalPages = Math.max(1, (int) Math.ceil((double) online.size() / perPage));
      if (page < 1) page = 1;
      if (page > totalPages) page = totalPages;

      Inventory inv = Bukkit.createInventory(new Holder("players", page), 54, GUI_TITLE_PLAYERS + " (第" + page + "/" + totalPages + "页)");

      // Decoration
      ItemStack border = createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
      for (int i = 0; i < 9; i++) inv.setItem(i, border);
      for (int i = 45; i < 54; i++) inv.setItem(i, border);

      int startIdx = (page - 1) * perPage;
      int slot = 9;
      for (int i = startIdx; i < online.size() && slot < 45; i++) {
         Player p = online.get(i);
         UUID uid = p.getUniqueId();
         boolean punished = plugin.isPunished(uid);
         boolean whitelisted = plugin.settings().whitelist() != null
               && plugin.settings().whitelist().players().contains(p.getName().toLowerCase(Locale.ROOT));

         ViolationRecord record = plugin.getPunishmentTracker() != null
               ? plugin.getPunishmentTracker().getRecord(uid) : null;

         ItemStack head = createPlayerHead(p, ChatColor.WHITE + p.getName(),
               ChatColor.GRAY + "世界: " + ChatColor.WHITE + p.getWorld().getName(),
               ChatColor.GRAY + "违规次数: " + ChatColor.WHITE + (record != null ? record.count() : 0) + " (总计: " + (record != null ? record.totalViolations() : 0) + ")",
               ChatColor.GRAY + "处罚状态: " + (punished ? ChatColor.RED + "已处罚" : ChatColor.GREEN + "正常"),
               whitelisted ? ChatColor.GRAY + "白名单: " + ChatColor.GREEN + "是" : ""
         );
         inv.setItem(slot++, head);
      }

      // Navigation buttons
      if (page > 1) {
         inv.setItem(45, createItem(Material.ARROW, ChatColor.YELLOW + "上一页"));
      }
      if (page < totalPages) {
         inv.setItem(53, createItem(Material.ARROW, ChatColor.YELLOW + "下一页"));
      }
      inv.setItem(49, createItem(Material.DARK_OAK_DOOR, ChatColor.RED + "返回主菜单"));

      return inv;
   }

   private Inventory buildWorldSettings() {
      Settings s = plugin.settings();
      Inventory inv = Bukkit.createInventory(new Holder("worlds"), 54, GUI_TITLE_WORLDS);

      // Decoration
      ItemStack border = createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
      for (int i = 0; i < 9; i++) inv.setItem(i, border);
      for (int i = 45; i < 54; i++) inv.setItem(i, border);

      // Add online worlds
      int slot = 9;
      for (org.bukkit.World world : Bukkit.getWorlds()) {
         if (slot >= 45) break;
         String name = world.getName();
         boolean hasOverride = s.worldSettings(name) != null;

         List<String> lore = new ArrayList<>();
         lore.add(ChatColor.GRAY + "世界: " + ChatColor.WHITE + name);
         lore.add(ChatColor.GRAY + "环境: " + ChatColor.WHITE + world.getEnvironment().name());
         lore.add(ChatColor.GRAY + "玩家数: " + ChatColor.WHITE + world.getPlayers().size());
         lore.add("");
         lore.add(ChatColor.GRAY + "全局检测: " + statusColor(s.isDetectionEnabledForWorld(name)) + (s.isDetectionEnabledForWorld(name) ? "启用" : "禁用"));
         lore.add(ChatColor.GRAY + "反Printer: " + statusColor(s.isAntiPrinterEnabledForWorld(name)) + (s.isAntiPrinterEnabledForWorld(name) ? "启用" : "禁用"));
         lore.add(ChatColor.GRAY + "命令防护: " + statusColor(s.isCommandGuardEnabledForWorld(name)) + (s.isCommandGuardEnabledForWorld(name) ? "启用" : "禁用"));
         lore.add("");
         lore.add(hasOverride
               ? ChatColor.GREEN + "✔ 该世界有独立配置"
               : ChatColor.YELLOW + "使用全局配置");
         lore.add(ChatColor.GRAY + "左键点击切换检测状态");

         inv.setItem(slot++, createItem(Material.GRASS_BLOCK, ChatColor.YELLOW + name, lore.toArray(new String[0])));
      }

      inv.setItem(49, createItem(Material.DARK_OAK_DOOR, ChatColor.RED + "返回主菜单"));

      return inv;
   }

   private Inventory buildWhitelist() {
      Settings s = plugin.settings();
      Inventory inv = Bukkit.createInventory(new Holder("whitelist"), 54, GUI_TITLE_WHITELIST);

      // Decoration
      ItemStack border = createItem(Material.GRAY_STAINED_GLASS_PANE, " ");
      for (int i = 0; i < 9; i++) inv.setItem(i, border);
      for (int i = 45; i < 54; i++) inv.setItem(i, border);

      Settings.Whitelist wl = s.whitelist();
      boolean enabled = wl != null && wl.enabled();
      String mode = wl != null ? wl.mode() : "NONE";

      inv.setItem(9, createToggleItem(Material.LEVER,
            ChatColor.AQUA + "白名单开关", enabled, "wl_toggle"));

      inv.setItem(11, createItem(Material.BOOK,
            ChatColor.GOLD + "模式",
            ChatColor.GRAY + "当前: " + ChatColor.WHITE + mode));

      // List whitelisted players
      if (wl != null) {
         int slot = 18;
         for (String name : wl.players()) {
            if (slot >= 45) break;
            inv.setItem(slot++, createItem(Material.PLAYER_HEAD, ChatColor.WHITE + name,
                  ChatColor.GRAY + "点击移出白名单"));
         }
         if (wl.players().isEmpty()) {
            inv.setItem(18, createItem(Material.PAPER, ChatColor.YELLOW + "白名单为空",
                  ChatColor.GRAY + "使用 /al whitelist add <玩家名> 添加"));
         }
      }

      inv.setItem(49, createItem(Material.DARK_OAK_DOOR, ChatColor.RED + "返回主菜单"));

      return inv;
   }

   // ======================== Click Handler ========================

   @EventHandler
   public void onClick(InventoryClickEvent event) {
      if (!(event.getInventory().getHolder() instanceof Holder holder)) return;
      if (!(event.getWhoClicked() instanceof Player player)) return;
      event.setCancelled(true);

      ItemStack item = event.getCurrentItem();
      if (item == null || !item.hasItemMeta()) return;
      if (isBorderItem(item)) return; // Ignore border/decoration clicks

      switch (holder.page) {
         case "main" -> handleMainClick(player, event.getSlot(), item);
         case "players" -> handlePlayersClick(player, event.getSlot(), holder);
         case "worlds" -> handleWorldsClick(player, event.getSlot(), item);
         case "whitelist" -> handleWhitelistClick(player, event.getSlot(), item);
      }
   }

   /** Check if an item is a border/decoration item and should be unclickable. */
   private static boolean isBorderItem(ItemStack item) {
      return item.getType() == Material.GRAY_STAINED_GLASS_PANE;
   }

   private void handleMainClick(Player player, int slot, ItemStack item) {
      switch (slot) {
         case 18 -> toggleFeature(player, "detection");
         case 20 -> toggleFeature(player, "anti_printer");
         case 22 -> toggleFeature(player, "command_guard");
         case 24 -> toggleFeature(player, "graduated");
         case 27 -> openPlayers(player, 1);
         case 29 -> openWorlds(player);
         case 31 -> openWhitelist(player);
         case 33 -> showWorldStatus(player);
         case 38 -> {
            plugin.reloadSettings();
            player.sendMessage(PREFIX + ChatColor.GREEN + "配置已重载!");
            openMain(player);
         }
         case 40 -> {
            if (plugin.getPunishmentTracker() != null) {
               for (ViolationRecord r : plugin.getPunishmentTracker().getAllRecords()) {
                  plugin.getPunishmentTracker().resetPlayer(r.uuid());
               }
               player.sendMessage(PREFIX + ChatColor.RED + "所有违规记录已清除!");
            }
            openMain(player);
         }
         case 42 -> player.closeInventory();
      }
   }

   private void handlePlayersClick(Player player, int slot, Holder holder) {
      if (slot == 49) {
         openMain(player);
      } else if (slot == 45 && holder.pageNum > 1) {
         openPlayers(player, holder.pageNum - 1);
      } else if (slot == 53 && holder.pageNum > 0) {
         openPlayers(player, holder.pageNum + 1);
      } else if (slot >= 9 && slot < 45) {
         ItemStack item = player.getOpenInventory().getItem(slot);
         // Only PLAYER_HEAD items are actual player entries
         if (item == null || item.getType() != Material.PLAYER_HEAD || !item.hasItemMeta()) return;
         String displayName = ChatColor.stripColor(item.getItemMeta().getDisplayName());
         if (displayName != null && !displayName.isEmpty()) {
            showPlayerDetail(player, displayName);
         }
      }
   }

   private void handleWorldsClick(Player player, int slot, ItemStack item) {
      if (slot == 49) {
         openMain(player);
         return;
      }
      // Only GRASS_BLOCK items are actual world entries
      if (item.getType() != Material.GRASS_BLOCK) return;
      if (slot >= 9 && slot < 45) {
         String displayName = ChatColor.stripColor(item.getItemMeta().getDisplayName());
         if (displayName != null && !displayName.isEmpty() && Bukkit.getWorld(displayName) != null) {
            toggleWorldDetection(player, displayName);
            openWorlds(player);
         }
      }
   }

   private void handleWhitelistClick(Player player, int slot, ItemStack item) {
      if (slot == 49) {
         openMain(player);
      } else if (slot == 9) {
         toggleFeature(player, "wl_toggle");
         openWhitelist(player);
      } else if (slot >= 18 && slot < 45) {
         // Only PLAYER_HEAD items are actual whitelist entries
         if (item.getType() != Material.PLAYER_HEAD) return;
         String name = ChatColor.stripColor(item.getItemMeta().getDisplayName());
         if (name != null && !name.isEmpty() && plugin.settings().whitelist() != null) {
            Settings.Whitelist wl = plugin.settings().whitelist();
            var players = new java.util.LinkedHashSet<>(wl.players());
            if (players.remove(name.toLowerCase(Locale.ROOT))) {
               saveWhitelistConfig(players);
               player.sendMessage(PREFIX + ChatColor.GREEN + name + " 已从白名单移除!");
            }
            openWhitelist(player);
         }
      }
   }

   // ======================== Actions ========================

   private synchronized void toggleFeature(Player player, String feature) {
      java.io.File configFile = new java.io.File(plugin.getDataFolder(), "config.yml");
      org.bukkit.configuration.file.YamlConfiguration cfg =
            org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(configFile);

      String path;
      String featureName;
      switch (feature) {
         case "detection" -> { path = "detection.enabled"; featureName = "检测"; }
         case "anti_printer" -> { path = "anti_printer.enabled"; featureName = "反Printer"; }
         case "command_guard" -> { path = "command_guard.enabled"; featureName = "命令防护"; }
         case "graduated" -> { path = "graduated_punishment.enabled"; featureName = "渐进惩罚"; }
         case "wl_toggle" -> { path = "whitelist.enabled"; featureName = "白名单"; }
         default -> { return; }
      }

      boolean current = cfg.getBoolean(path, true);
      cfg.set(path, !current);
      try { cfg.save(configFile); } catch (java.io.IOException e) {
         player.sendMessage(PREFIX + ChatColor.RED + "保存配置失败!");
         return;
      }
      plugin.reloadSettings();
      player.sendMessage(PREFIX + ChatColor.GREEN + featureName + " 已" + (!current ? "启用" : "禁用"));
      // Refresh current GUI
      String title = player.getOpenInventory().getTitle();
      if (title.equals(GUI_TITLE_MAIN)) openMain(player);
   }

   private void toggleWorldDetection(Player player, String worldName) {
      java.io.File configFile = new java.io.File(plugin.getDataFolder(), "config.yml");
      org.bukkit.configuration.file.YamlConfiguration cfg =
            org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(configFile);

      String path = "worlds." + worldName + ".detection.enabled";
      boolean current = plugin.settings().isDetectionEnabledForWorld(worldName);
      // If no override exists, the effective value equals global setting
      // Create the world section if needed
      cfg.set(path, !current);
      try { cfg.save(configFile); } catch (java.io.IOException e) {
         player.sendMessage(PREFIX + ChatColor.RED + "保存配置失败!");
         return;
      }
      plugin.reloadSettings();
      player.sendMessage(PREFIX + ChatColor.GREEN + worldName + " 检测已" + (!current ? "启用" : "禁用"));
   }

   private void showPlayerDetail(Player viewer, String playerName) {
      @SuppressWarnings("deprecation")
      org.bukkit.OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
      if (target == null) return;

      UUID uuid = target.getUniqueId();
      boolean punished = plugin.isPunished(uuid);
      ViolationRecord record = plugin.getPunishmentTracker() != null
            ? plugin.getPunishmentTracker().getRecord(uuid) : null;

      viewer.sendMessage(ChatColor.GOLD + "=== " + playerName + " ===");
      viewer.sendMessage(ChatColor.GRAY + "处罚状态: " + (punished ? ChatColor.RED + "已处罚" : ChatColor.GREEN + "正常"));
      if (record != null) {
         viewer.sendMessage(ChatColor.GRAY + "窗口违规: " + ChatColor.WHITE + record.count());
         viewer.sendMessage(ChatColor.GRAY + "总计违规: " + ChatColor.WHITE + record.totalViolations());
         viewer.sendMessage(ChatColor.GRAY + "首次违规: " + ChatColor.WHITE + new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
               .format(new java.util.Date(record.firstViolation())));
         viewer.sendMessage(ChatColor.GRAY + "最近违规: " + ChatColor.WHITE + new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
               .format(new java.util.Date(record.lastViolation())));
         if (record.world() != null) {
            viewer.sendMessage(ChatColor.GRAY + "违规世界: " + ChatColor.WHITE + record.world());
         }
      } else {
         viewer.sendMessage(ChatColor.GRAY + "无违规记录");
      }
      viewer.sendMessage(ChatColor.GRAY + "UUID: " + ChatColor.WHITE + uuid);
   }

   private void showWorldStatus(Player player) {
      Settings s = plugin.settings();
      player.sendMessage(ChatColor.GOLD + "=== 多世界状态 ===");
      for (org.bukkit.World world : Bukkit.getWorlds()) {
         String name = world.getName();
         Settings.WorldSettings ws = s.worldSettings(name);
         player.sendMessage(ChatColor.GRAY + " - " + ChatColor.WHITE + name
               + ChatColor.GRAY + " [检测=" + boolEmoji(s.isDetectionEnabledForWorld(name))
               + " 反Printer=" + boolEmoji(s.isAntiPrinterEnabledForWorld(name))
               + " 命令=" + boolEmoji(s.isCommandGuardEnabledForWorld(name))
               + " 惩罚=" + boolEmoji(s.isGraduatedPunishmentEnabledForWorld(name))
               + "]" + (ws != null ? ChatColor.GREEN + " ✔" : ""));
      }
   }

   private void saveWhitelistConfig(java.util.Set<String> players) {
      java.io.File configFile = new java.io.File(plugin.getDataFolder(), "config.yml");
      org.bukkit.configuration.file.YamlConfiguration cfg =
            org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(configFile);
      cfg.set("whitelist.players", new java.util.ArrayList<>(players));
      try { cfg.save(configFile); } catch (java.io.IOException e) {
         plugin.getLogger().warning("Failed to save whitelist: " + e.getMessage());
      }
      plugin.reloadSettings();
   }

   // ======================== Utilities ========================

   private static String statusColor(boolean enabled) {
      return enabled ? ChatColor.GREEN.toString() : ChatColor.RED.toString();
   }

   private static String boolEmoji(boolean b) {
      return b ? ChatColor.GREEN + "✓" : ChatColor.RED + "✗";
   }

   private static ItemStack createItem(Material material, String name, String... lore) {
      ItemStack item = new ItemStack(material);
      ItemMeta meta = item.getItemMeta();
      if (meta != null) {
         meta.setDisplayName(name);
         if (lore.length > 0) {
            List<String> loreList = new ArrayList<>();
            for (String line : lore) {
               if (line != null && !line.isEmpty()) {
                  loreList.add(line);
               }
            }
            meta.setLore(loreList);
         }
         item.setItemMeta(meta);
      }
      return item;
   }

   private static ItemStack createToggleItem(Material material, String name, boolean enabled, String feature) {
      ItemStack item = new ItemStack(enabled ? Material.LIME_DYE : Material.GRAY_DYE);
      ItemMeta meta = item.getItemMeta();
      if (meta != null) {
         meta.setDisplayName(name);
         List<String> lore = new ArrayList<>();
         lore.add(ChatColor.GRAY + "状态: " + (enabled ? ChatColor.GREEN + "已启用" : ChatColor.RED + "已禁用"));
         lore.add(ChatColor.GRAY + "点击切换");
         meta.setLore(lore);
         item.setItemMeta(meta);
      }
      return item;
   }

   private static ItemStack createPlayerHead(Player player, String name, String... lore) {
      ItemStack item = new ItemStack(Material.PLAYER_HEAD);
      SkullMeta meta = (SkullMeta) item.getItemMeta();
      if (meta != null) {
         meta.setOwningPlayer(player);
         meta.setDisplayName(name);
         if (lore.length > 0) {
            List<String> loreList = new ArrayList<>();
            for (String line : lore) {
               if (line != null && !line.isEmpty()) {
                  loreList.add(line);
               }
            }
            meta.setLore(loreList);
         }
         item.setItemMeta(meta);
      }
      return item;
   }

   @EventHandler
   public void onClose(InventoryCloseEvent event) {
      // Optional: clean up on close
   }
}
