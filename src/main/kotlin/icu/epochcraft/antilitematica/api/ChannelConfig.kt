package icu.epochcraft.antilitematica.api

/**
 * 通道配置（API 只读视图）。
 *
 * @param channel 通道名（小写）
 * @param action 处理动作：KICK / BAN / WARN / LOG
 * @param banDurationMillis 封禁时长（毫秒），仅 BAN 动作有意义
 */
class ChannelConfig(
    val channel: String,
    val action: String,
    val banDurationMillis: Long,
) {

    override fun toString(): String = "ChannelConfig(channel=$channel, action=$action, banDuration=$banDurationMillis)"
}
