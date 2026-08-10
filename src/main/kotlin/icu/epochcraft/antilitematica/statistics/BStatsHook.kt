package icu.epochcraft.antilitematica.statistics

import icu.epochcraft.antilitematica.AntiLitematica
import org.bstats.bukkit.Metrics
import org.bstats.charts.SimplePie

/**
 * bStats 匿名统计接入。
 *
 * 插件 ID 硬编码在代码中（[PLUGIN_ID]），不写入 config.yml，防止被他人修改；
 * 管理员仍可在服务端全局配置文件 plugins/bStats/config.yml 中关闭统计。
 *
 * 自定义图表：
 *   - ban_backend  封禁后端（内置 / LiteBans / AdvancedBan）
 *   - language     当前语言
 *   - mode         预设模式
 *   - anti_cheat   反作弊联动
 *
 * @author 阿清
 */
class BStatsHook(private val plugin: AntiLitematica) {

    private var metrics: Metrics? = null

    /** 是否已启用 */
    val isEnabled: Boolean get() = metrics != null

    fun init() {
        try {
            val m = Metrics(plugin, PLUGIN_ID)
            // 封禁后端分布
            m.addCustomChart(SimplePie("ban_backend") { plugin.banManager.backendName })
            // 语言分布
            m.addCustomChart(SimplePie("language") { plugin.langManager.currentLang })
            // 预设模式分布
            m.addCustomChart(SimplePie("mode") { plugin.configHolder.mode.name.lowercase() })
            // 反作弊联动分布
            m.addCustomChart(SimplePie("anti_cheat") { plugin.integrationManager.currentName })
            metrics = m
            plugin.logger.info("bStats 统计已启用（插件 ID: $PLUGIN_ID）")
        } catch (e: Exception) {
            plugin.logger.warning("bStats 初始化失败: ${e.message}")
        }
    }

    companion object {

        /** bStats 插件 ID（bstats.org 注册，硬编码防止被修改） */
        const val PLUGIN_ID = 31012
    }
}
