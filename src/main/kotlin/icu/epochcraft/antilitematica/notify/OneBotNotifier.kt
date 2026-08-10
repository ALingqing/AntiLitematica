package icu.epochcraft.antilitematica.notify

import com.google.gson.JsonObject
import icu.epochcraft.antilitematica.AntiLitematica
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * QQ 群通知（OneBot v11 / NapCat 兼容，出站 HTTP，无需开放端口）。
 *
 * 通过调用本地/远程已运行的 NapCat OneBot HTTP API 发送群消息：
 *   POST {base}/send_group_msg
 *
 * @author 阿清
 */
class OneBotNotifier(private val plugin: AntiLitematica) {

    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(3))
        .build()

    /** 发送群消息，返回是否成功 */
    fun sendGroupMessage(message: String): Boolean {
        val cfg = plugin.configHolder
        if (!cfg.onebotEnabled || cfg.onebotGroupId <= 0) return false

        val payload = JsonObject().apply {
            addProperty("message_type", "group")
            addProperty("group_id", cfg.onebotGroupId)
            addProperty("message", stripColor(message))
        }
        val headers = if (cfg.onebotAccessToken.isNotBlank()) {
            mapOf("Authorization" to "Bearer ${cfg.onebotAccessToken}")
        } else {
            emptyMap()
        }

        return try {
            val url = cfg.onebotBaseUrl.trimEnd('/') + "/send_group_msg"
            val builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload.toString()))
            headers.forEach { (k, v) -> builder.header(k, v) }
            val response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
            response.statusCode() in 200..299
        } catch (e: Exception) {
            plugin.logger.warning("QQ OneBot 通知发送失败: ${e.message}")
            false
        }
    }

    /** 去掉 § 颜色代码（QQ 无法渲染） */
    private fun stripColor(text: String): String = text.replace(Regex("§[0-9a-fk-or]"), "")
}
