package icu.epochcraft.antilitematica.database

import icu.epochcraft.antilitematica.detection.ActionType
import java.util.UUID

/**
 * 一次检测命中记录。
 *
 * @author 阿清
 */
data class DetectionRecord(
    val uuid: UUID,
    val name: String,
    val channel: String,
    val modDescription: String?,
    val action: ActionType,
    val timestamp: Long = System.currentTimeMillis(),
)
