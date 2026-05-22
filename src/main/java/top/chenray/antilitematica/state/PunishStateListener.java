package top.chenray.antilitematica.state;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import top.chenray.antilitematica.AntiLitematicaPlugin;

public final class PunishStateListener implements Listener {
   private final AntiLitematicaPlugin plugin;

   public PunishStateListener(AntiLitematicaPlugin plugin) {
      this.plugin = plugin;
   }

   @EventHandler
   public void onQuit(PlayerQuitEvent event) {
      this.plugin.unmarkPunished(event.getPlayer().getUniqueId());
   }
}
