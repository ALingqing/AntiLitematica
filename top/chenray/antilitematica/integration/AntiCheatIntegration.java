package top.chenray.antilitematica.integration;

import org.bukkit.entity.Player;

public interface AntiCheatIntegration {
   void enable();

   void disable();

   boolean isActive();

   void flag(Player var1, String var2, int var3, String var4);

   String getName();
}
