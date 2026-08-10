package icu.epochcraft.antilitematica.signal

import icu.epochcraft.antilitematica.AntiLitematica
import org.bukkit.Bukkit

/**
 * Mod List 深度解析工厂：ProtocolLib 未安装时安全降级。
 *
 * ModListHandshakeDetector 通过反射 + Proxy 实现 PacketListener，
 * 编译期不引用 ProtocolLib，但为了与 [SignalFactory] 模式统一（且为未来
 * 引入 ProtocolLib 类型预留空间），仍通过 Class.forName 反射实例化。
 *
 * @author 阿清
 */
object HandshakeDetectorFactory {

    /** 创建 Mod List 检测器（服务端无 ProtocolLib 或配置关闭时返回 null） */
    fun create(plugin: AntiLitematica): ModListHandshakeDetector? {
        if (Bukkit.getPluginManager().getPlugin("ProtocolLib") == null) {
            plugin.logger.info("未检测到 ProtocolLib，FML/Fabric Mod List 深度解析已跳过")
            return null
        }
        if (!plugin.configHolder.modList.enabled) {
            plugin.logger.info("Mod List 深度解析未启用（config.yml mod-list.enabled）")
            return null
        }
        return runCatching {
            val clazz = Class.forName("icu.epochcraft.antilitematica.signal.ModListHandshakeDetector")
            clazz.getConstructor(AntiLitematica::class.java).newInstance(plugin) as ModListHandshakeDetector
        }.getOrNull()
    }
}
