package icu.epochcraft.antilitematica.database

import java.util.UUID

/**
 * 一条封禁记录。
 *
 * @author 阿清
 */
data class BanRecord(
    val uuid: UUID,
    val name: String,
    val reason: String,
    val createdAt: Long = System.currentTimeMillis(),
    /** 到期时间戳（毫秒），-1 表示永久 */
    val expiresAt: Long,
) {

    /** 是否已到期 */
    fun isExpired(now: Long = System.currentTimeMillis()): Boolean =
        expiresAt != PERMANENT && expiresAt <= now

    companion object {
        const val PERMANENT = -1L
    }
}
