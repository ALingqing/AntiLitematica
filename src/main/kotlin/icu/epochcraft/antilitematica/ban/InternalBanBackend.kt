package icu.epochcraft.antilitematica.ban

import icu.epochcraft.antilitematica.database.BanRecord
import icu.epochcraft.antilitematica.database.DetectionDatabase
import java.util.UUID

/**
 * 内置封禁后端：SQLite 持久化（默认，无需任何外部插件）。
 *
 * @author 阿清
 */
class InternalBanBackend(private val database: DetectionDatabase) : BanBackend {

    override val name: String = "内置SQLite"

    override fun isAvailable(): Boolean = true

    override fun ban(uuid: UUID, name: String, reason: String, durationMillis: Long) {
        val expiresAt = if (durationMillis == BanRecord.PERMANENT) BanRecord.PERMANENT
        else System.currentTimeMillis() + durationMillis
        database.insertBan(BanRecord(uuid, name, reason, expiresAt = expiresAt))
    }

    override fun unban(uuid: UUID) {
        database.removeBan(uuid)
    }
}
