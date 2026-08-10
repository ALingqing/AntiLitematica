package icu.epochcraft.antilitematica.punish

/**
 * 惩罚动作（渐进惩罚的每一级）。
 *
 * @author 阿清
 */
enum class PunishmentAction(val displayName: String) {

    /** 聊天警告 */
    WARN("警告"),

    /** 踢出 */
    KICK("踢出"),

    /** 临时封禁 */
    TEMPBAN("临时封禁"),

    /** 永久封禁 */
    BAN("封禁");

    companion object {
        fun parse(raw: String?): PunishmentAction =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: WARN
    }
}
