package icu.epochcraft.antilitematica.signal

import icu.epochcraft.antilitematica.AntiLitematica
import org.bukkit.Bukkit

/**
 * 信号检测工厂：ProtocolLib 未安装时安全降级。
 *
 * ProtocolLibSignalDetector 引用了 ProtocolLib 类，必须反射实例化，
 * 避免服务端未装 ProtocolLib 时触发 NoClassDefFoundError。
 *
 * @author 阿清
 */
object SignalFactory {

    /** 创建信号检测器（服务端无 ProtocolLib 或配置关闭时返回 null） */
    fun create(plugin: AntiLitematica): ProtocolLibSignalDetector? {
        if (Bukkit.getPluginManager().getPlugin("ProtocolLib") == null) {
            plugin.logger.info("未检测到 ProtocolLib，信号检测（EasyPlace/NBT）已跳过")
            return null
        }
        if (!plugin.configHolder.signals.easyPlaceEnabled && !plugin.configHolder.signals.nbtQueryEnabled) {
            plugin.logger.info("信号检测未启用（config.yml signals 段）")
            return null
        }
        return runCatching {
            val clazz = Class.forName("icu.epochcraft.antilitematica.signal.ProtocolLibSignalDetector")
            clazz.getConstructor(AntiLitematica::class.java).newInstance(plugin) as ProtocolLibSignalDetector
        }.getOrNull()
    }
}
