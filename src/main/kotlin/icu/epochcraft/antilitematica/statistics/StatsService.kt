package icu.epochcraft.antilitematica.statistics

import icu.epochcraft.antilitematica.AntiLitematica
import java.text.SimpleDateFormat
import java.util.Date

/**
 * 统计服务：汇总数据库中的检测数据并格式化输出。
 *
 * @author 阿清
 */
class StatsService(private val plugin: AntiLitematica) {

    /** 生成统计文本 */
    fun buildStats(): String {
        val db = plugin.database
        val sb = StringBuilder()

        sb.appendLine(plugin.configHolder.lang("stats.header"))

        // 检测总数
        val total = db.getAllDetectionsCount()
        sb.appendLine(plugin.configHolder.lang("stats.detections").replace("{count}", total.toString()))

        // 当前封禁数
        val bans = db.getAllActiveBans().size
        sb.appendLine(plugin.configHolder.lang("stats.bans").replace("{count}", bans.toString()))

        // 通道命中分布 TOP5
        val top = db.getChannelStats().entries.sortedByDescending { it.value }.take(5)
        if (top.isNotEmpty()) {
            sb.appendLine(
                plugin.configHolder.lang("stats.top-channels").replace(
                    "{list}",
                    top.joinToString("  ") { "${it.key}×${it.value}" },
                ),
            )
        }

        // 近 7 天趋势
        val daily = db.getDailyStats(7)
        if (daily.isNotEmpty()) {
            sb.appendLine(
                plugin.configHolder.lang("stats.daily")
                    .replace("{days}", "7")
                    .replace("{list}", daily.entries.joinToString("  ") { "${it.key}:${it.value}" }),
            )
        }

        return sb.toString()
    }

    /** 格式化时间戳 */
    fun formatTime(timestamp: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date(timestamp))
}
