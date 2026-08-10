package icu.epochcraft.antilitematica.util

import org.bukkit.Bukkit
import org.bukkit.entity.Entity
import org.bukkit.plugin.Plugin
import java.util.function.Consumer

/**
 * 调度工具：兼容 Paper / Spigot / Folia。
 *
 * Folia 没有主线程调度器，通过反射使用其 Global / Async / Entity Scheduler；
 * 普通 Paper/Spigot 环境回退到 Bukkit 主线程调度。插件无需引入 folia-api 依赖。
 *
 * @author 阿清
 */
object Scheduler {

    /** 是否为 Folia（存在 RegionizedServer 类即判定为 Folia） */
    val isFolia: Boolean = try {
        Class.forName("io.papermc.paper.threadedregions.RegionizedServer") != null
    } catch (e: ClassNotFoundException) {
        false
    }

    /** 全局线程任务（Paper 主线程 / Folia GlobalRegionScheduler） */
    fun global(plugin: Plugin, task: Runnable) {
        if (isFolia) {
            val scheduler = globalRegionScheduler()
            scheduler.javaClass.getMethod("run", Plugin::class.java, Consumer::class.java)
                .invoke(scheduler, plugin, consumer(task))
        } else {
            Bukkit.getScheduler().runTask(plugin, task)
        }
    }

    /** 延迟全局任务（tick） */
    fun globalLater(plugin: Plugin, delayTicks: Long, task: Runnable) {
        if (isFolia) {
            val scheduler = globalRegionScheduler()
            scheduler.javaClass.getMethod(
                "runDelayed", Plugin::class.java, Consumer::class.java, Long::class.javaPrimitiveType,
            ).invoke(scheduler, plugin, consumer(task), delayTicks)
        } else {
            Bukkit.getScheduler().runTaskLater(plugin, task, delayTicks)
        }
    }

    /** 异步任务 */
    fun async(plugin: Plugin, task: Runnable) {
        if (isFolia) {
            val scheduler = asyncScheduler()
            scheduler.javaClass.getMethod("runNow", Plugin::class.java, Consumer::class.java)
                .invoke(scheduler, plugin, consumer(task))
        } else {
            Bukkit.getScheduler().runTaskAsynchronously(plugin, task)
        }
    }

    /** 异步定时任务（tick 单位），返回句柄可取消 */
    fun asyncTimer(plugin: Plugin, delayTicks: Long, periodTicks: Long, task: Runnable): TaskHandle =
        if (isFolia) {
            val scheduler = asyncScheduler()
            // Folia runAtFixedRate 单位为毫秒
            val scheduled = scheduler.javaClass.getMethod(
                "runAtFixedRate", Plugin::class.java, Consumer::class.java,
                Long::class.javaPrimitiveType, Long::class.javaPrimitiveType,
            ).invoke(scheduler, plugin, consumer(task), delayTicks * 50L, periodTicks * 50L)
            FoliaHandle(scheduled)
        } else {
            BukkitHandle(Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, task, delayTicks, periodTicks).taskId)
        }

    /**
     * 实体所在区域线程任务（踢出等对玩家实体的操作）。
     * Folia 必须在实体所属区域线程执行，Paper 回退主线程。
     */
    fun entity(entity: Entity, plugin: Plugin, task: Runnable) {
        if (isFolia) {
            try {
                val scheduler = entity.javaClass.getMethod("getScheduler").invoke(entity) ?: return
                scheduler.javaClass.getMethod(
                    "run", Plugin::class.java, Consumer::class.java, Runnable::class.java,
                ).invoke(scheduler, plugin, consumer(task), task)
            } catch (e: Exception) {
                // 实体不可用等异常时兜底到全局线程
                global(plugin, task)
            }
        } else {
            Bukkit.getScheduler().runTask(plugin, task)
        }
    }

    /** 任务句柄 */
    interface TaskHandle {
        fun cancel()
    }

    private class BukkitHandle(private val taskId: Int) : TaskHandle {
        override fun cancel() {
            Bukkit.getScheduler().cancelTask(taskId)
        }
    }

    private class FoliaHandle(private val scheduled: Any) : TaskHandle {
        override fun cancel() {
            try {
                scheduled.javaClass.getMethod("cancel").invoke(scheduled)
            } catch (_: Exception) {
            }
        }
    }

    // ---------------- 反射 ----------------

    private fun consumer(task: Runnable): Consumer<Any> = Consumer { task.run() }

    private fun globalRegionScheduler(): Any =
        Bukkit.getServer().javaClass.getMethod("getGlobalRegionScheduler").invoke(Bukkit.getServer())

    private fun asyncScheduler(): Any =
        Bukkit.getServer().javaClass.getMethod("getAsyncScheduler").invoke(Bukkit.getServer())
}
