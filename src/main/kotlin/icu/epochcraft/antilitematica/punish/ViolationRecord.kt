package icu.epochcraft.antilitematica.punish

import java.util.UUID

/**
 * 玩家违规记录（渐进惩罚计数）。
 *
 * @author 阿清
 */
data class ViolationRecord(
    val uuid: UUID,
    val playerName: String,
    /** 当前窗口内违规次数 */
    var count: Int,
    val firstViolation: Long,
    var lastViolation: Long,
    /** 累计总次数（跨窗口不清零） */
    var totalViolations: Int,
    /** 所属世界（null = 全局） */
    val world: String?,
)
