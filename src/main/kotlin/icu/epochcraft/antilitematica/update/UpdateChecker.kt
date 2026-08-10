package icu.epochcraft.antilitematica.update

import icu.epochcraft.antilitematica.AntiLitematica
import icu.epochcraft.antilitematica.util.Scheduler
import com.google.gson.JsonParser
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * 更新检查器：通过 GitHub Releases API 检查新版本（纯出站请求）。
 *
 * @author 阿清
 */
class UpdateChecker(private val plugin: AntiLitematica) {

    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build()

    /** 最新版本号（检查到的新版本，null 表示未知） */
    @Volatile
    var latestVersion: String? = null
        private set

    @Volatile
    var hasUpdate: Boolean = false
        private set

    /** 异步检查更新（onEnable 时调用） */
    fun checkAsync() {
        if (!plugin.configHolder.updateCheckerEnabled) return
        Scheduler.async(plugin) { check() }
    }

    /** 同步检查更新（多源回退：直连 API → 加速代理，任一成功即停） */
    fun check() {
        val repo = plugin.configHolder.updateRepo
        if (repo.isBlank()) return

        // 来源列表：api.github.com 国内直连不稳定，回退到加速代理
        val sources = listOf(
            "https://api.github.com/repos/$repo/releases/latest",
            "https://ghfast.top/https://api.github.com/repos/$repo/releases/latest",
            "https://ghproxy.net/https://api.github.com/repos/$repo/releases/latest",
        )

        for (url in sources) {
            try {
                if (checkSource(url)) return
            } catch (e: Exception) {
                plugin.logger.warning("更新检查失败（${url.take(60)}…）: ${e.message}")
            }
        }
    }

    /** 尝试单个来源，成功（拿到版本号）返回 true */
    private fun checkSource(url: String): Boolean {
        val request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(8))
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "AntiLitematica")
            .GET()
            .build()
        val response = client.send(request, HttpResponse.BodyHandlers.ofString())
        if (response.statusCode() != 200) return false

        val json = try {
            JsonParser.parseString(response.body()).asJsonObject
        } catch (e: Exception) {
            return false
        }
        val tag = json.get("tag_name")?.asString ?: return false
        latestVersion = tag.removePrefix("v")
        hasUpdate = isNewer(latestVersion!!, plugin.description.version)
        if (hasUpdate) {
            plugin.logger.info("发现新版本: v${latestVersion}（当前 v${plugin.description.version}），请前往 ${plugin.configHolder.updateRepo} 更新")
        }
        return true
    }

    /** 简易版本比较：1.2.3 > 1.1.9 */
    private fun isNewer(latest: String, current: String): Boolean {
        fun parts(v: String): List<Int> =
            Regex("""\d+""").findAll(v).map { it.value.toInt() }.toList()

        val l = parts(latest)
        val c = parts(current)
        for (i in 0 until maxOf(l.size, c.size)) {
            val a = l.getOrElse(i) { 0 }
            val b = c.getOrElse(i) { 0 }
            if (a != b) return a > b
        }
        return false
    }
}
