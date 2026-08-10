package icu.epochcraft.antilitematica.api

import java.util.UUID

/**
 * 封禁信息（API 只读视图）。
 *
 * @param uuid 玩家 UUID
 * @param name 玩家名（封禁时的名字）
 * @param reason 封禁原因
 * @param createdAt 封禁时间戳（毫秒）
 * @param expiresAt 到期时间戳（毫秒），[PERMANENT] 表示永久
 * @param isPermanent 是否永久封禁
 * @param expiresInMillis 剩余毫秒（已过期为 0，永久为 -1）
 */
class BanInfo(
    val uuid: UUID,
    val name: String,
    val reason: String,
    val createdAt: Long,
    val expiresAt: Long,
    val isPermanent: Boolean,
    val expiresInMillis: Long,
) {

    override fun toString(): String =
        "BanInfo(uuid=$uuid, name=$name, reason=$reason, permanent=$isPermanent, expiresIn=${expiresInMillis}ms)"

    companion object {
        /** 永久封禁的到期时间戳 */
        const val PERMANENT = -1L
    }
}
