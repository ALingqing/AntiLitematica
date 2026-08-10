package icu.epochcraft.antilitematica.util

/**
 * 时长解析工具：支持 30s / 5m / 2h / 7d / permanent / 纯数字（毫秒）。
 *
 * @author 阿清
 */
object DurationParser {

    private val pattern = Regex("""(\d+)\s*(s|m|h|d|w)?$""", RegexOption.IGNORE_CASE)

    /**
     * 解析时长为毫秒。
     * @param raw 原始字符串
     * @param default 解析失败时的默认值（毫秒）
     */
    fun parseMillis(raw: String?, default: Long = 86_400_000L): Long {
        if (raw == null) return default
        val trimmed = raw.trim()
        if (trimmed.equals("permanent", ignoreCase = true) || trimmed == "-1") return -1L
        val match = pattern.find(trimmed) ?: return default
        val value = match.groupValues[1].toLongOrNull() ?: return default
        val unit = match.groupValues[2].lowercase()
        return when (unit) {
            "s" -> value * 1_000L
            "m" -> value * 60_000L
            "h" -> value * 3_600_000L
            "d" -> value * 86_400_000L
            "w" -> value * 604_800_000L
            else -> value // 纯数字按毫秒
        }
    }

    /** 将毫秒时长格式化为人类可读文本（如 "30天"、"permanent"） */
    fun format(millis: Long): String {
        if (millis == -1L) return "permanent"
        if (millis < 60_000L) return "${millis / 1000}s"
        val minutes = millis / 60_000L
        if (minutes < 60) return "${minutes}m"
        val hours = minutes / 60
        if (hours < 24) return "${hours}h"
        val days = hours / 24
        val weeks = days / 7
        return if (weeks >= 1 && days % 7 == 0L) "${weeks}w" else "${days}d"
    }
}
