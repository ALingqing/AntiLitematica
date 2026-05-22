package top.chenray.antilitematica.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import top.chenray.antilitematica.AntiLitematicaPlugin;

public final class GuiListener implements Listener {
    private final AntiLitematicaPlugin plugin;
    private final ConfigGui configGui;
    private final GuiInputManager inputManager;

    public GuiListener(AntiLitematicaPlugin plugin, ConfigGui configGui, GuiInputManager inputManager) {
        this.plugin = plugin;
        this.configGui = configGui;
        this.inputManager = inputManager;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (event.getInventory().getHolder() instanceof ConfigGui.GuiHolder holder) {
            event.setCancelled(true);
            if (event.getClickedInventory() != event.getInventory()) {
                return;
            }

            int slot = event.getSlot();
            configGui.handleClick(player, holder.getPage(), slot, event.getClick());

            // Refresh current page if not navigating to another page
            // The handleClick methods that navigate call openXxxPage which reopens inventory
            // For toggle/adjust actions, we refresh the current page to show updated state
            refreshIfSameOpen(player, holder.getPage());
        }
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (event.getInventory().getHolder() instanceof ConfigGui.GuiHolder) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onAsyncPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (!inputManager.isInputting(player)) {
            return;
        }

        event.setCancelled(true);
        String message = event.getMessage();

        // Run callback synchronously on main thread since it may access Bukkit API
        org.bukkit.Bukkit.getScheduler().runTask(plugin, () -> {
            inputManager.handleInput(player, message);
        });
    }

    private void refreshIfSameOpen(Player player, ConfigGui.GuiPage page) {
        // Small delay to ensure config is saved before refreshing
        org.bukkit.Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!(player.getOpenInventory().getTopInventory().getHolder() instanceof ConfigGui.GuiHolder currentHolder)) {
                return;
            }
            if (currentHolder.getPage() != page) {
                return;
            }
            switch (page) {
                case MAIN -> configGui.openMainPage(player);
                case DETECTION -> configGui.openDetectionPage(player);
                case PUNISHMENT -> configGui.openPunishmentPage(player);
                case INTEGRATION -> configGui.openIntegrationPage(player);
                case WEBHOOK -> configGui.openWebhookPage(player);
                case PRINTER -> configGui.openPrinterPage(player);
                case MESSAGES -> configGui.openMessagesPage(player);
                case DYNAMIC -> configGui.openDynamicPage(player);
                case DATA -> configGui.openDataPage(player);
            }
        }, 1L);
    }
}
