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

    /** 同步检查更新 */
    fun check() {
        val repo = plugin.configHolder.updateRepo
        if (repo.isBlank()) return
        try {
            val url = "https://api.github.com/repos/$repo/releases/latest"
            val request = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(8))
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "AntiLitematica")
                .GET()
                .build()
            val response = client.send(request, HttpResponse.BodyHandlers.ofString())
            if (response.statusCode() != 200) return

            val json = JsonParser.parseString(response.body()).asJsonObject
            val tag = json.get("tag_name")?.asString ?: return
            latestVersion = tag.removePrefix("v")
            hasUpdate = isNewer(latestVersion!!, plugin.description.version)
            if (hasUpdate) {
                plugin.logger.info("发现新版本: v${latestVersion}（当前 v${plugin.description.version}），请前往 $repo 更新")
            }
        } catch (e: Exception) {
            plugin.logger.warning("更新检查失败: ${e.message}")
        }
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
