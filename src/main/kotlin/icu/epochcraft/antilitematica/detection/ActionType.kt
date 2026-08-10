package icu.epochcraft.antilitematica.detection

/**
 * 检测命中后的处理动作（通道级可配置）。
 *
 * @author 阿清
 */
enum class ActionType(val displayName: String) {

    /** 直接踢出玩家 */
    KICK("踢出"),

    /** 封禁（时长见配置）并踢出 */
    BAN("封禁"),

    /** 仅聊天警告，不踢出 */
    WARN("警告"),

    /** 仅记录日志，不做处理 */
    LOG("记录");

    companion object {

        /** 解析配置字符串，非法值返回默认 [KICK] */
        fun parse(raw: String?): ActionType =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: KICK

        /** 是否为"踢出类"动作（KICK / BAN） */
        fun ActionType.isKickLike(): Boolean = this == KICK || this == BAN
    }
}
