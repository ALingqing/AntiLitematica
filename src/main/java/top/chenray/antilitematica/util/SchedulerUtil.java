package top.chenray.antilitematica.util;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import java.util.concurrent.TimeUnit;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Folia-compatible scheduling helper.
 * <p>
 * On Folia servers, player operations must run on the player's entity region thread
 * via {@code player.getScheduler()}. Global tasks use {@code Bukkit.getGlobalRegionScheduler()},
 * and async tasks use {@code Bukkit.getAsyncScheduler()}.
 * <p>
 * Paper 1.20+ includes these APIs and executes them on the main thread,
 * so this works on both Paper and Folia without runtime detection.
 */
public final class SchedulerUtil {
    private SchedulerUtil() {}

    /** Run a task on the global region (Paper main thread equivalent). */
    public static void runGlobal(Plugin plugin, Runnable task) {
        Bukkit.getGlobalRegionScheduler().run(plugin, t -> task.run());
    }

    /** Run a task on the player's entity region thread (safe for player kick/message). */
    public static void runForPlayer(Plugin plugin, Player player, Runnable task) {
        player.getScheduler().run(plugin, t -> task.run(), null);
    }

    /** Schedule a repeating async task. Returns a handle for cancellation.
     *  @param delayTicks  initial delay in server ticks
     *  @param periodTicks period between runs in server ticks */
    public static ScheduledTask runTimerAsync(Plugin plugin, Runnable task, long delayTicks, long periodTicks) {
        return Bukkit.getAsyncScheduler().runAtFixedRate(plugin,
                t -> task.run(),
                delayTicks * 50L, periodTicks * 50L,
                TimeUnit.MILLISECONDS);
    }

    /** Schedule a repeating global region task. Returns a handle for cancellation. */
    public static ScheduledTask runTimerGlobal(Plugin plugin, Runnable task, long delayTicks, long periodTicks) {
        return Bukkit.getGlobalRegionScheduler().runAtFixedRate(plugin,
                t -> task.run(), delayTicks, periodTicks);
    }
}
