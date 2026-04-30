package top.chenray.antilitematica.gui;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import top.chenray.antilitematica.AntiLitematicaPlugin;
import top.chenray.antilitematica.config.Settings;
import top.chenray.antilitematica.punish.ViolationRecord;
import top.chenray.antilitematica.util.DiscordWebhook;

public final class ConfigGui {
    private final AntiLitematicaPlugin plugin;
    private final GuiInputManager inputManager;
    private final FileConfiguration guiMessages;
    private final File messagesFile;

    public ConfigGui(AntiLitematicaPlugin plugin, GuiInputManager inputManager) {
        this.plugin = plugin;
        this.inputManager = inputManager;
        this.messagesFile = new File(plugin.getDataFolder(), "messages.yml");
        this.guiMessages = YamlConfiguration.loadConfiguration(messagesFile);
    }

    public String msg(String path) {
        return msg(path, path);
    }

    public String msg(String path, String fallback) {
        String value = guiMessages.getString(path, fallback);
        return GuiItems.color(value);
    }

    // ==================== Pages ====================

    public void openMainPage(Player player) {
        GuiHolder holder = new GuiHolder(GuiPage.MAIN);
        Inventory inv = Bukkit.createInventory(holder, 27,
                msg("gui.main.title", "&8AntiLitematica &7配置"));
        holder.setInventory(inv);
        fillEmpty(inv);

        inv.setItem(10, GuiItems.item(Material.EMERALD_BLOCK, msg("gui.main.detection", "&a检测设置")));
        inv.setItem(12, GuiItems.item(Material.DIAMOND_SWORD, msg("gui.main.punishment", "&c惩罚设置")));
        inv.setItem(14, GuiItems.item(Material.DRAGON_EGG, msg("gui.main.integration", "&6反作弊集成")));
        inv.setItem(16, GuiItems.item(Material.SEA_LANTERN, msg("gui.main.webhook", "&bWebhook设置")));
        inv.setItem(18, GuiItems.item(Material.COMPASS, msg("gui.main.dynamic", "&b动态阈值")));
        inv.setItem(20, GuiItems.item(Material.STONE, msg("gui.main.printer", "&e反打印机")));
        inv.setItem(22, GuiItems.item(Material.BOOK, msg("gui.main.messages", "&d消息设置")));
        inv.setItem(24, GuiItems.item(Material.HOPPER, msg("gui.main.data", "&7数据管理")));
        inv.setItem(26, GuiItems.item(Material.NETHER_STAR, msg("gui.main.save", "&a&l保存并重载配置")));

        player.openInventory(inv);
    }

    public void openDetectionPage(Player player) {
        Settings s = plugin.settings();
        GuiHolder holder = new GuiHolder(GuiPage.DETECTION);
        Inventory inv = Bukkit.createInventory(holder, 27,
                msg("gui.detection.title", "&8检测设置"));
        holder.setInventory(inv);
        fillEmpty(inv);

        boolean servux = s.detection().signals().servuxMetadata().enabled();
        boolean easyPlace = s.detection().signals().easyPlace().enabled();
        boolean nbt = s.detection().signals().nbtQuery().enabled();

        inv.setItem(10, toggleItem(Material.REDSTONE_LAMP,
                msg("gui.detection.servux", "&eServux元数据检测"), servux));
        inv.setItem(12, toggleItem(Material.LEVER,
                msg("gui.detection.easyplace", "&eEasyPlace检测"), easyPlace));
        inv.setItem(14, toggleItem(Material.COMMAND_BLOCK,
                msg("gui.detection.nbt", "&eNBT查询检测"), nbt));
        inv.setItem(16, loreItem(Material.PAPER,
                msg("gui.detection.channels", "&e检测频道列表"),
                currentLore(String.join(", ", s.detection().channels()))));
        inv.setItem(22, backItem());

        player.openInventory(inv);
    }

    public void openPunishmentPage(Player player) {
        Settings s = plugin.settings();
        GuiHolder holder = new GuiHolder(GuiPage.PUNISHMENT);
        Inventory inv = Bukkit.createInventory(holder, 27,
                msg("gui.punishment.title", "&8惩罚设置"));
        holder.setInventory(inv);
        fillEmpty(inv);

        String action = s.detection().action().name();
        long window = s.graduatedPunishment() != null ? s.graduatedPunishment().windowMinutes() : 60;

        inv.setItem(10, loreItem(Material.DIAMOND_SWORD,
                msg("gui.punishment.action", "&c惩罚动作"),
                currentLore(action)));
        inv.setItem(12, loreItem(Material.BOOK,
                msg("gui.punishment.reason", "&c封禁原因"),
                currentLore(s.detection().reason())));
        inv.setItem(14, adjustItem(Material.CLOCK,
                msg("gui.punishment.window", "&c时间窗口"), window + " 分钟",
                "&e左键 +5分 右键 -5分"));
        inv.setItem(16, loreItem(Material.ANVIL,
                msg("gui.punishment.levels", "&c阶梯惩罚详情"),
                List.of("&7暂不支持在GUI中编辑")));
        inv.setItem(22, backItem());

        player.openInventory(inv);
    }

    public void openIntegrationPage(Player player) {
        Settings s = plugin.settings();
        GuiHolder holder = new GuiHolder(GuiPage.INTEGRATION);
        Inventory inv = Bukkit.createInventory(holder, 27,
                msg("gui.integration.title", "&8反作弊集成"));
        holder.setInventory(inv);
        fillEmpty(inv);

        boolean grimEnabled = "grim".equalsIgnoreCase(s.integration().type()) && s.integration().enabled();
        int vl = s.integration().violationLevel();

        inv.setItem(10, toggleItem(Material.DRAGON_EGG,
                msg("gui.integration.grim", "&aGrimAC"), grimEnabled));
        inv.setItem(12, notSupportedItem(Material.NETHER_STAR,
                msg("gui.integration.vulcan", "&aVulcan")));
        inv.setItem(14, notSupportedItem(Material.ENDER_PEARL,
                msg("gui.integration.matrix", "&aMatrix")));
        inv.setItem(16, adjustItem(Material.EXPERIENCE_BOTTLE,
                msg("gui.integration.vl", "&e违规等级"), String.valueOf(vl),
                "&e左键 +1 右键 -1"));
        inv.setItem(22, backItem());

        player.openInventory(inv);
    }

    public void openWebhookPage(Player player) {
        Settings s = plugin.settings();
        GuiHolder holder = new GuiHolder(GuiPage.WEBHOOK);
        Inventory inv = Bukkit.createInventory(holder, 27,
                msg("gui.webhook.title", "&8Webhook设置"));
        holder.setInventory(inv);
        fillEmpty(inv);

        boolean enabled = s.discord().enabled();
        String triggerMode = getWebhookTriggerMode(s);

        inv.setItem(10, toggleItem(Material.SEA_LANTERN,
                msg("gui.webhook.toggle", "&b开关"), enabled));
        inv.setItem(12, loreItem(Material.OAK_SIGN,
                msg("gui.webhook.url", "&b设置URL"),
                currentLore(maskUrl(s.discord().webhookUrl()))));
        inv.setItem(14, loreItem(Material.HOPPER,
                msg("gui.webhook.trigger", "&b触发条件"),
                currentLore(triggerMode)));
        inv.setItem(16, GuiItems.item(Material.PAPER,
                msg("gui.webhook.test", "&b测试发送")));
        inv.setItem(22, backItem());

        player.openInventory(inv);
    }

    public void openPrinterPage(Player player) {
        Settings s = plugin.settings();
        GuiHolder holder = new GuiHolder(GuiPage.PRINTER);
        Inventory inv = Bukkit.createInventory(holder, 27,
                msg("gui.printer.title", "&8反打印机设置"));
        holder.setInventory(inv);
        fillEmpty(inv);

        boolean enabled = s.antiPrinter().enabled();
        boolean creative = s.antiPrinter().applyToCreative();
        boolean raytrace = s.antiPrinter().enforceRaytrace();
        int rate = s.antiPrinter().maxBlocksPerSecond();
        double reach = s.antiPrinter().reachSurvival();

        inv.setItem(10, toggleItem(Material.STONE,
                msg("gui.printer.toggle", "&e开关"), enabled));
        inv.setItem(12, toggleItem(Material.CRAFTING_TABLE,
                msg("gui.printer.creative", "&e创造模式检测"), creative));
        inv.setItem(14, toggleItem(Material.ENDER_EYE,
                msg("gui.printer.raytrace", "&e射线检测"), raytrace));
        inv.setItem(16, adjustItem(Material.CLOCK,
                msg("gui.printer.rate", "&e放置速率"), rate + " 方块/秒",
                "&e左键 +1 右键 -1"));
        inv.setItem(18, loreItem(Material.ANVIL,
                msg("gui.printer.reach", "&e检测距离"),
                currentLore(String.valueOf(reach))));
        inv.setItem(22, backItem());

        player.openInventory(inv);
    }

    public void openMessagesPage(Player player) {
        GuiHolder holder = new GuiHolder(GuiPage.MESSAGES);
        Inventory inv = Bukkit.createInventory(holder, 27,
                msg("gui.messages.title", "&8消息设置"));
        holder.setInventory(inv);
        fillEmpty(inv);

        inv.setItem(10, loreItem(Material.NAME_TAG,
                msg("gui.messages.prefix", "&d前缀"),
                currentLore(plugin.settings().messages().prefix())));
        inv.setItem(12, loreItem(Material.PAPER,
                msg("gui.messages.kick", "&d踢出消息"),
                currentLore(plugin.settings().messages().kick())));
        inv.setItem(14, loreItem(Material.OAK_PLANKS,
                msg("gui.messages.blocked", "&d阻止放置消息"),
                currentLore(plugin.settings().messages().blockedPlace())));
        inv.setItem(22, backItem());

        player.openInventory(inv);
    }

    public void openDynamicPage(Player player) {
        GuiHolder holder = new GuiHolder(GuiPage.DYNAMIC);
        Inventory inv = Bukkit.createInventory(holder, 27,
                msg("gui.dynamic.title", "&8动态阈值设置"));
        holder.setInventory(inv);
        fillEmpty(inv);

        boolean enabled = plugin.getDynamicThresholdManager().isEnabled();
        double mul = plugin.getDynamicThresholdManager().getCurrentMultiplier();

        inv.setItem(10, toggleItem(Material.COMPASS,
                msg("gui.dynamic.toggle", "&b动态阈值开关"), enabled));
        inv.setItem(12, adjustItem(Material.CLOCK,
                msg("gui.dynamic.interval", "&b检查间隔"),
                plugin.getConfig().getInt("dynamic_threshold.check_interval_seconds", 30) + " 秒",
                "&e左键 +5秒 右键 -5秒"));
        inv.setItem(14, loreItem(Material.REDSTONE_LAMP,
                msg("gui.dynamic.tps", "&bTPS 阈值设置"),
                List.of(
                        "&7高: " + plugin.getConfig().getDouble("dynamic_threshold.tps.high", 19.5),
                        "&7低: " + plugin.getConfig().getDouble("dynamic_threshold.tps.low", 16.0),
                        "&e点击编辑"
                )));
        inv.setItem(16, loreItem(Material.PLAYER_HEAD,
                msg("gui.dynamic.players", "&b人数阈值设置"),
                List.of(
                        "&7高: " + plugin.getConfig().getInt("dynamic_threshold.players.high", 50),
                        "&7低: " + plugin.getConfig().getInt("dynamic_threshold.players.low", 5),
                        "&e点击编辑"
                )));
        inv.setItem(20, adjustItem(Material.ANVIL,
                msg("gui.dynamic.multiplier", "&b倍率范围"),
                String.format("%.1f - %.1f",
                        plugin.getConfig().getDouble("dynamic_threshold.multiplier.min", 1.0),
                        plugin.getConfig().getDouble("dynamic_threshold.multiplier.max", 2.0)),
                "&e左键 +0.1 右键 -0.1"));
        inv.setItem(22, backItem());

        player.openInventory(inv);
    }

    public void openDataPage(Player player) {
        GuiHolder holder = new GuiHolder(GuiPage.DATA);
        Inventory inv = Bukkit.createInventory(holder, 27,
                msg("gui.data.title", "&8数据管理"));
        holder.setInventory(inv);
        fillEmpty(inv);

        inv.setItem(10, GuiItems.item(Material.PLAYER_HEAD,
                msg("gui.data.reset", "&7重置玩家记录")));
        inv.setItem(12, GuiItems.item(Material.BOOK,
                msg("gui.data.history", "&7查看玩家历史")));
        inv.setItem(14, GuiItems.item(Material.LAVA_BUCKET,
                msg("gui.data.clear", "&4清空过期数据")));
        inv.setItem(22, backItem());

        player.openInventory(inv);
    }

    // ==================== Click Handler ====================

    public void handleClick(Player player, GuiPage page, int slot, ClickType click) {
        boolean left = click.isLeftClick();
        boolean right = click.isRightClick();

        switch (page) {
            case MAIN -> handleMainClick(player, slot);
            case DETECTION -> handleDetectionClick(player, slot);
            case PUNISHMENT -> handlePunishmentClick(player, slot, left, right);
            case INTEGRATION -> handleIntegrationClick(player, slot, left, right);
            case WEBHOOK -> handleWebhookClick(player, slot);
            case PRINTER -> handlePrinterClick(player, slot, left, right);
            case MESSAGES -> handleMessagesClick(player, slot);
            case DYNAMIC -> handleDynamicClick(player, slot, left, right);
            case DATA -> handleDataClick(player, slot);
        }
    }

    private void handleMainClick(Player player, int slot) {
        switch (slot) {
            case 10 -> openDetectionPage(player);
            case 12 -> openPunishmentPage(player);
            case 14 -> openIntegrationPage(player);
            case 16 -> openWebhookPage(player);
            case 18 -> openDynamicPage(player);
            case 20 -> openPrinterPage(player);
            case 22 -> openMessagesPage(player);
            case 24 -> openDataPage(player);
            case 26 -> saveAndReload(player);
        }
    }

    private void handleDetectionClick(Player player, int slot) {
        switch (slot) {
            case 10 -> toggleConfig("detection.signals.servux_metadata.enabled");
            case 12 -> toggleConfig("detection.signals.easy_place.enabled");
            case 14 -> toggleConfig("detection.signals.nbt_query.enabled");
            case 16 -> inputChannels(player);
            case 22 -> openMainPage(player);
        }
    }

    private void handlePunishmentClick(Player player, int slot, boolean left, boolean right) {
        switch (slot) {
            case 10 -> cycleAction();
            case 12 -> inputBanReason(player);
            case 14 -> {
                if (left) adjustConfigLong("graduated_punishment.window_minutes", 5);
                else if (right) adjustConfigLong("graduated_punishment.window_minutes", -5);
            }
            case 16 -> player.sendMessage(msg("input.cancel", "&c阶梯惩罚详情暂不支持GUI编辑"));
            case 22 -> openMainPage(player);
        }
    }

    private void handleIntegrationClick(Player player, int slot, boolean left, boolean right) {
        Settings s = plugin.settings();
        switch (slot) {
            case 10 -> {
                boolean grimEnabled = "grim".equalsIgnoreCase(s.integration().type()) && s.integration().enabled();
                if (grimEnabled) {
                    plugin.getConfig().set("integration.type", "none");
                    plugin.getConfig().set("integration.enabled", false);
                } else {
                    plugin.getConfig().set("integration.type", "grim");
                    plugin.getConfig().set("integration.enabled", true);
                }
                plugin.saveConfig();
                plugin.reloadSettings();
            }
            case 12, 14 -> player.sendMessage(msg("input.cancel", "&c暂不支持该反作弊集成"));
            case 16 -> {
                if (left) adjustConfigInt("integration.violation_level", 1);
                else if (right) adjustConfigInt("integration.violation_level", -1);
                plugin.reloadSettings();
            }
            case 22 -> openMainPage(player);
        }
    }

    private void handleWebhookClick(Player player, int slot) {
        Settings s = plugin.settings();
        switch (slot) {
            case 10 -> toggleConfig("discord.enabled");
            case 12 -> inputWebhookUrl(player);
            case 14 -> cycleWebhookTrigger();
            case 16 -> sendTestWebhook(player);
            case 22 -> openMainPage(player);
        }
    }

    private void handlePrinterClick(Player player, int slot, boolean left, boolean right) {
        switch (slot) {
            case 10 -> toggleConfig("anti_printer.enabled");
            case 12 -> toggleConfig("anti_printer.apply_to_creative");
            case 14 -> toggleConfig("anti_printer.enforce_raytrace");
            case 16 -> {
                if (left) adjustConfigInt("anti_printer.max_blocks_per_second", 1);
                else if (right) adjustConfigInt("anti_printer.max_blocks_per_second", -1);
            }
            case 18 -> inputReach(player);
            case 22 -> openMainPage(player);
        }
    }

    private void handleMessagesClick(Player player, int slot) {
        switch (slot) {
            case 10 -> inputPrefix(player);
            case 12 -> inputKickMsg(player);
            case 14 -> inputBlockedMsg(player);
            case 22 -> openMainPage(player);
        }
    }

    private void handleDynamicClick(Player player, int slot, boolean left, boolean right) {
        switch (slot) {
            case 10 -> {
                boolean next = !plugin.getConfig().getBoolean("dynamic_threshold.enabled", false);
                plugin.getConfig().set("dynamic_threshold.enabled", next);
                plugin.saveConfig();
                plugin.getDynamicThresholdManager().reload();
            }
            case 12 -> {
                if (left) adjustConfigInt("dynamic_threshold.check_interval_seconds", 5);
                else if (right) adjustConfigIntMin("dynamic_threshold.check_interval_seconds", -5, 5);
            }
            case 14 -> inputTpsThresholds(player);
            case 16 -> inputPlayerThresholds(player);
            case 20 -> {
                if (left) adjustConfigDouble("dynamic_threshold.multiplier.max", 0.1);
                else if (right) adjustConfigDouble("dynamic_threshold.multiplier.max", -0.1);
            }
            case 22 -> openMainPage(player);
        }
    }

    private void handleDataClick(Player player, int slot) {
        switch (slot) {
            case 10 -> inputPlayerReset(player);
            case 12 -> inputPlayerHistory(player);
            case 14 -> clearExpiredData(player);
            case 22 -> openMainPage(player);
        }
    }

    // ==================== Input Handlers ====================

    private void inputChannels(Player player) {
        Settings s = plugin.settings();
        String current = String.join(", ", s.detection().channels());
        inputManager.registerInput(player, GuiInputManager.InputType.CHANNEL_LIST, input -> {
            if (input.equalsIgnoreCase("cancel")) {
                player.sendMessage(msg("input.cancel", "&c已取消"));
                return;
            }
            List<String> list = Arrays.asList(input.split("[,，]"));
            Set<String> normalized = new LinkedHashSet<>();
            for (String ch : list) {
                String t = ch.trim().toLowerCase(Locale.ROOT);
                if (!t.isEmpty()) normalized.add(t);
            }
            plugin.getConfig().set("detection.channels", new ArrayList<>(normalized));
            plugin.saveConfig();
            plugin.reloadSettings();
            player.sendMessage(msg("input.success", "&a保存成功！"));
            openDetectionPage(player);
        }, msg("input.channel_list",
                "&e请在聊天栏输入频道列表，用逗号分隔\n&7输入 &ccancel &7取消").replace("\\n", "\n"));
    }

    private void inputBanReason(Player player) {
        inputManager.registerInput(player, GuiInputManager.InputType.BAN_REASON, input -> {
            if (input.equalsIgnoreCase("cancel")) {
                player.sendMessage(msg("input.cancel", "&c已取消"));
                return;
            }
            plugin.getConfig().set("detection.reason", input);
            plugin.saveConfig();
            plugin.reloadSettings();
            player.sendMessage(msg("input.success", "&a保存成功！"));
            openPunishmentPage(player);
        }, msg("input.ban_reason",
                "&e请在聊天栏输入封禁原因\n&7输入 &ccancel &7取消").replace("\\n", "\n"));
    }

    private void inputWebhookUrl(Player player) {
        inputManager.registerInput(player, GuiInputManager.InputType.WEBHOOK_URL, input -> {
            if (input.equalsIgnoreCase("cancel")) {
                player.sendMessage(msg("input.cancel", "&c已取消"));
                return;
            }
            if (!input.startsWith("http://") && !input.startsWith("https://")) {
                player.sendMessage(msg("input.invalid_url", "&c无效的URL格式！"));
                return;
            }
            plugin.getConfig().set("discord.webhook_url", input);
            plugin.saveConfig();
            plugin.reloadSettings();
            player.sendMessage(msg("input.success", "&a保存成功！"));
            openWebhookPage(player);
        }, msg("input.webhook_url",
                "&e请在聊天栏输入 Discord Webhook URL\n&7输入 &ccancel &7取消").replace("\\n", "\n"));
    }

    private void inputReach(Player player) {
        inputManager.registerInput(player, GuiInputManager.InputType.REACH_DISTANCE, input -> {
            if (input.equalsIgnoreCase("cancel")) {
                player.sendMessage(msg("input.cancel", "&c已取消"));
                return;
            }
            try {
                double val = Double.parseDouble(input);
                plugin.getConfig().set("anti_printer.reach_survival", val);
                plugin.saveConfig();
                plugin.reloadSettings();
                player.sendMessage(msg("input.success", "&a保存成功！"));
                openPrinterPage(player);
            } catch (NumberFormatException e) {
                player.sendMessage(msg("input.invalid_url", "&c无效的数值！"));
            }
        }, "&e请在聊天栏输入检测距离\n&7输入 &ccancel &7取消");
    }

    private void inputTpsThresholds(Player player) {
        inputManager.registerInput(player, GuiInputManager.InputType.CUSTOM, input -> {
            if (input.equalsIgnoreCase("cancel")) {
                player.sendMessage(msg("input.cancel", "&c已取消"));
                return;
            }
            String[] parts = input.split("[;, ]");
            if (parts.length != 2) {
                player.sendMessage("&c格式错误，请输入: 高阈值 低阈值 (如: 19.5 16.0)");
                return;
            }
            try {
                double high = Double.parseDouble(parts[0].trim());
                double low = Double.parseDouble(parts[1].trim());
                plugin.getConfig().set("dynamic_threshold.tps.high", high);
                plugin.getConfig().set("dynamic_threshold.tps.low", low);
                plugin.saveConfig();
                plugin.reloadSettings();
                player.sendMessage(msg("input.success", "&a保存成功！"));
                openDynamicPage(player);
            } catch (NumberFormatException e) {
                player.sendMessage("&c无效的数值！");
            }
        }, "&e请输入 TPS 阈值: 高阈值 低阈值\n&7示例: 19.5 16.0\n&7输入 &ccancel &7取消");
    }

    private void inputPlayerThresholds(Player player) {
        inputManager.registerInput(player, GuiInputManager.InputType.CUSTOM, input -> {
            if (input.equalsIgnoreCase("cancel")) {
                player.sendMessage(msg("input.cancel", "&c已取消"));
                return;
            }
            String[] parts = input.split("[;, ]");
            if (parts.length != 2) {
                player.sendMessage("&c格式错误，请输入: 高阈值 低阈值 (如: 50 5)");
                return;
            }
            try {
                int high = Integer.parseInt(parts[0].trim());
                int low = Integer.parseInt(parts[1].trim());
                plugin.getConfig().set("dynamic_threshold.players.high", high);
                plugin.getConfig().set("dynamic_threshold.players.low", low);
                plugin.saveConfig();
                plugin.reloadSettings();
                player.sendMessage(msg("input.success", "&a保存成功！"));
                openDynamicPage(player);
            } catch (NumberFormatException e) {
                player.sendMessage("&c无效的数值！");
            }
        }, "&e请输入在线人数阈值: 高阈值 低阈值\n&7示例: 50 5\n&7输入 &ccancel &7取消");
    }

    private void inputPrefix(Player player) {
        inputManager.registerInput(player, GuiInputManager.InputType.PREFIX, input -> {
            if (input.equalsIgnoreCase("cancel")) {
                player.sendMessage(msg("input.cancel", "&c已取消"));
                return;
            }
            guiMessages.set("prefix", input);
            saveMessages();
            plugin.reloadSettings();
            player.sendMessage(msg("input.success", "&a保存成功！"));
            openMessagesPage(player);
        }, "&e请在聊天栏输入前缀\n&7输入 &ccancel &7取消");
    }

    private void inputKickMsg(Player player) {
        inputManager.registerInput(player, GuiInputManager.InputType.KICK_MSG, input -> {
            if (input.equalsIgnoreCase("cancel")) {
                player.sendMessage(msg("input.cancel", "&c已取消"));
                return;
            }
            guiMessages.set("kick", input);
            saveMessages();
            plugin.reloadSettings();
            player.sendMessage(msg("input.success", "&a保存成功！"));
            openMessagesPage(player);
        }, "&e请在聊天栏输入踢出消息\n&7输入 &ccancel &7取消");
    }

    private void inputBlockedMsg(Player player) {
        inputManager.registerInput(player, GuiInputManager.InputType.BLOCKED_MSG, input -> {
            if (input.equalsIgnoreCase("cancel")) {
                player.sendMessage(msg("input.cancel", "&c已取消"));
                return;
            }
            guiMessages.set("blocked_place", input);
            saveMessages();
            plugin.reloadSettings();
            player.sendMessage(msg("input.success", "&a保存成功！"));
            openMessagesPage(player);
        }, "&e请在聊天栏输入阻止放置消息\n&7输入 &ccancel &7取消");
    }

    private void inputPlayerReset(Player player) {
        inputManager.registerInput(player, GuiInputManager.InputType.PLAYER_NAME, input -> {
            if (input.equalsIgnoreCase("cancel")) {
                player.sendMessage(msg("input.cancel", "&c已取消"));
                return;
            }
            if (plugin.getPunishmentTracker() == null) {
                player.sendMessage("&c阶梯惩罚系统未启用");
                return;
            }
            @SuppressWarnings("deprecation")
            OfflinePlayer target = Bukkit.getOfflinePlayer(input);
            if (target == null || (!target.hasPlayedBefore() && !target.isOnline())) {
                player.sendMessage("&c未找到玩家: " + input);
                return;
            }
            plugin.getPunishmentTracker().resetPlayer(target.getUniqueId());
            player.sendMessage("&a已重置玩家记录: " + input);
            openDataPage(player);
        }, msg("input.player_name", "&e请在聊天栏输入玩家名\n&7输入 &ccancel &7取消").replace("\\n", "\n"));
    }

    private void inputPlayerHistory(Player player) {
        inputManager.registerInput(player, GuiInputManager.InputType.PLAYER_NAME, input -> {
            if (input.equalsIgnoreCase("cancel")) {
                player.sendMessage(msg("input.cancel", "&c已取消"));
                return;
            }
            if (plugin.getPunishmentTracker() == null) {
                player.sendMessage("&c阶梯惩罚系统未启用");
                return;
            }
            @SuppressWarnings("deprecation")
            OfflinePlayer target = Bukkit.getOfflinePlayer(input);
            if (target == null || (!target.hasPlayedBefore() && !target.isOnline())) {
                player.sendMessage("&c未找到玩家: " + input);
                return;
            }
            ViolationRecord record = plugin.getPunishmentTracker().getRecord(target.getUniqueId());
            if (record == null) {
                player.sendMessage("&e" + input + " 没有违规记录");
                return;
            }
            player.sendMessage("&6=== 违规历史: " + input + " ===");
            player.sendMessage("&7当前窗口次数: &f" + record.count());
            player.sendMessage("&7总违规次数: &f" + record.totalViolations());
            java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            player.sendMessage("&7首次违规: &f" + sdf.format(new java.util.Date(record.firstViolation())));
            player.sendMessage("&7最近违规: &f" + sdf.format(new java.util.Date(record.lastViolation())));
            openDataPage(player);
        }, msg("input.player_name", "&e请在聊天栏输入玩家名\n&7输入 &ccancel &7取消").replace("\\n", "\n"));
    }

    private void clearExpiredData(Player player) {
        if (plugin.getPunishmentTracker() == null) {
            player.sendMessage("&c阶梯惩罚系统未启用");
            return;
        }
        plugin.getPunishmentTracker().clearExpiredRecords();
        player.sendMessage("&a已清空过期数据");
        openDataPage(player);
    }

    // ==================== Helpers ====================

    private void saveAndReload(Player player) {
        plugin.saveConfig();
        saveMessages();
        plugin.reloadSettings();
        player.sendMessage(msg("input.success", "&a保存并重载完成！"));
    }

    private void toggleConfig(String path) {
        boolean current = plugin.getConfig().getBoolean(path);
        plugin.getConfig().set(path, !current);
        plugin.saveConfig();
        plugin.reloadSettings();
    }

    private void adjustConfigInt(String path, int delta) {
        int current = plugin.getConfig().getInt(path);
        int next = Math.max(0, current + delta);
        plugin.getConfig().set(path, next);
        plugin.saveConfig();
        plugin.reloadSettings();
    }

    private void adjustConfigIntMin(String path, int delta, int min) {
        int current = plugin.getConfig().getInt(path);
        int next = Math.max(min, current + delta);
        plugin.getConfig().set(path, next);
        plugin.saveConfig();
        plugin.reloadSettings();
    }

    private void adjustConfigDouble(String path, double delta) {
        double current = plugin.getConfig().getDouble(path);
        double next = Math.max(0.1, current + delta);
        plugin.getConfig().set(path, next);
        plugin.saveConfig();
        plugin.reloadSettings();
    }

    private void adjustConfigLong(String path, long delta) {
        long current = plugin.getConfig().getLong(path);
        long next = Math.max(1, current + delta);
        plugin.getConfig().set(path, next);
        plugin.saveConfig();
        plugin.reloadSettings();
    }

    private void cycleAction() {
        String current = plugin.getConfig().getString("detection.action", "KICK").toUpperCase(Locale.ROOT);
        String next = switch (current) {
            case "LOG" -> "KICK";
            case "KICK" -> "BAN";
            case "BAN" -> "COMMANDS";
            default -> "LOG";
        };
        plugin.getConfig().set("detection.action", next);
        plugin.saveConfig();
        plugin.reloadSettings();
    }

    private String getWebhookTriggerMode(Settings s) {
        boolean det = s.discord().notifyOnDetection();
        boolean pun = s.discord().notifyOnPunish();
        if (det && pun) return "全部";
        if (det) return "仅检测";
        if (pun) return "仅惩罚";
        return "关闭";
    }

    private void cycleWebhookTrigger() {
        boolean det = plugin.getConfig().getBoolean("discord.notify_on_detection", true);
        boolean pun = plugin.getConfig().getBoolean("discord.notify_on_punish", true);
        if (det && pun) {
            plugin.getConfig().set("discord.notify_on_detection", true);
            plugin.getConfig().set("discord.notify_on_punish", false);
        } else if (det && !pun) {
            plugin.getConfig().set("discord.notify_on_detection", false);
            plugin.getConfig().set("discord.notify_on_punish", true);
        } else if (!det && pun) {
            plugin.getConfig().set("discord.notify_on_detection", false);
            plugin.getConfig().set("discord.notify_on_punish", false);
        } else {
            plugin.getConfig().set("discord.notify_on_detection", true);
            plugin.getConfig().set("discord.notify_on_punish", true);
        }
        plugin.saveConfig();
        plugin.reloadSettings();
    }

    private void sendTestWebhook(Player player) {
        Settings.Discord dc = plugin.settings().discord();
        if (!dc.enabled() || dc.webhookUrl().isEmpty()) {
            player.sendMessage("&cWebhook 未启用或 URL 为空");
            return;
        }
        DiscordWebhook wh = new DiscordWebhook(plugin, dc.webhookUrl(), dc.username(),
                dc.avatarUrl(), dc.embedTitle(), dc.embedColor(), dc.footerText(),
                dc.proxyHost() != null ? dc.proxyHost() : "",
                dc.proxyPort(),
                dc.proxyUsername() != null ? dc.proxyUsername() : "",
                dc.proxyPassword() != null ? dc.proxyPassword() : "");
        wh.sendDetection(player.getName(), player.getUniqueId().toString(),
                "test-channel", "Test message from GUI", "TEST");
        player.sendMessage("&a测试消息已发送");
    }

    private void saveMessages() {
        try {
            guiMessages.save(messagesFile);
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to save messages.yml: " + e.getMessage());
        }
    }

    // ==================== Item Builders ====================

    private void fillEmpty(Inventory inv) {
        ItemStack empty = GuiItems.empty(msg("gui.empty", "&7点我也没用"));
        for (int i = 0; i < inv.getSize(); i++) {
            if (inv.getItem(i) == null) {
                inv.setItem(i, empty);
            }
        }
    }

    private ItemStack backItem() {
        return GuiItems.item(Material.ARROW, msg("gui.back", "&c返回上级菜单"));
    }

    private ItemStack toggleItem(Material material, String name, boolean enabled) {
        String status = enabled ? msg("status.enabled", "&a开启") : msg("status.disabled", "&c关闭");
        return loreItem(material, name, List.of(
                msg("gui.current", "&7当前: &f") + status,
                msg("gui.click_toggle", "&e点击切换")
        ));
    }

    private ItemStack adjustItem(Material material, String name, String currentValue, String hint) {
        return loreItem(material, name, List.of(
                msg("gui.current", "&7当前: &f") + currentValue,
                GuiItems.color(hint)
        ));
    }

    private ItemStack loreItem(Material material, String name, List<String> lore) {
        return GuiItems.item(material, name, lore);
    }

    private List<String> currentLore(String value) {
        return List.of(msg("gui.current", "&7当前: &f") + value, msg("gui.click_edit", "&e点击编辑"));
    }

    private ItemStack notSupportedItem(Material material, String name) {
        return loreItem(material, name, List.of("&c暂不支持"));
    }

    private String maskUrl(String url) {
        if (url == null || url.isEmpty()) return "未设置";
        if (url.length() <= 20) return url;
        return url.substring(0, 10) + "..." + url.substring(url.length() - 10);
    }

    // ==================== Holder ====================

    public enum GuiPage {
        MAIN, DETECTION, PUNISHMENT, INTEGRATION, WEBHOOK, PRINTER, MESSAGES, DYNAMIC, DATA
    }

    public static final class GuiHolder implements InventoryHolder {
        private final GuiPage page;
        private Inventory inventory;

        public GuiHolder(GuiPage page) {
            this.page = page;
        }

        public GuiPage getPage() {
            return page;
        }

        void setInventory(Inventory inventory) {
            this.inventory = inventory;
        }

        @Override
        public Inventory getInventory() {
            return inventory;
        }
    }
}