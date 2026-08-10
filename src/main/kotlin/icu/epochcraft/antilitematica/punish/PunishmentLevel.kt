package icu.epochcraft.antilitematica.punish

/**
 * 一级惩罚配置。
 *
 * @author 阿清
 */
data class PunishmentLevel(
    val action: PunishmentAction,
    val reason: String,
    /** 封禁时长（毫秒），仅 TEMPBAN 使用 */
    val durationMillis: Long,
    val broadcast: Boolean,
    val staffAlert: Boolean,
)
