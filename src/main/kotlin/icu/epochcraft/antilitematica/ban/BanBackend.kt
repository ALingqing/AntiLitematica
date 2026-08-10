package icu.epochcraft.antilitematica.ban

import icu.epochcraft.antilitematica.AntiLitematica
import icu.epochcraft.antilitematica.database.DetectionDatabase
import java.util.UUID

/**
 * 封禁后端抽象：支持内置 SQLite / LiteBans / AdvancedBan 三种后端。
 *
 * 选择顺序（config.yml 的 ban-backend）：
 *   auto        - 自动探测：LiteBans → AdvancedBan → 内置
 *   internal    - 强制内置 SQLite
 *   litebans    - 强制 LiteBans（未安装时回退内置）
 *   advancedban - 强制 AdvancedBan（未安装时回退内置）
 *
 * @author 阿清
 */
interface BanBackend {

    /** 后端名称（日志 / 通知展示用） */
    val name: String

    /** 服务端是否已安装并可用 */
    fun isAvailable(): Boolean

    /** 执行封禁（durationMillis 为 [BanRecord.PERMANENT] 表示永久） */
    fun ban(uuid: UUID, name: String, reason: String, durationMillis: Long)

    /** 执行解封 */
    fun unban(uuid: UUID)
}

/** 后端工厂：按配置创建并探测 */
object BanBackendFactory {

    fun create(plugin: AntiLitematica, database: DetectionDatabase, configured: String): BanBackend {
        val preference = configured.trim().lowercase()
        val internal = InternalBanBackend(database)
        val litebans = LiteBansBackend(plugin)
        val advancedban = AdvancedBanBackend(plugin)

        return when (preference) {
            "litebans" ->
                if (litebans.isAvailable()) {
                    plugin.logger.info("封禁后端: LiteBans（联动）")
                    litebans
                } else {
                    plugin.logger.warning("未检测到 LiteBans，封禁后端回退为内置 SQLite")
                    internal
                }
            "advancedban" ->
                if (advancedban.isAvailable()) {
                    plugin.logger.info("封禁后端: AdvancedBan（联动）")
                    advancedban
                } else {
                    plugin.logger.warning("未检测到 AdvancedBan，封禁后端回退为内置 SQLite")
                    internal
                }
            "internal" -> internal
            // auto：按优先级探测
            else -> when {
                litebans.isAvailable() -> {
                    plugin.logger.info("封禁后端: LiteBans（自动探测，联动）")
                    litebans
                }
                advancedban.isAvailable() -> {
                    plugin.logger.info("封禁后端: AdvancedBan（自动探测，联动）")
                    advancedban
                }
                else -> {
                    plugin.logger.info("封禁后端: 内置 SQLite")
                    internal
                }
            }
        }
    }
}
