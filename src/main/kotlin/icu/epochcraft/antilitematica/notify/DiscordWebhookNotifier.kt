package icu.epochcraft.antilitematica.notify

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import icu.epochcraft.antilitematica.AntiLitematica
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Discord Webhook 通知（出站 HTTPS，无需服务端开放端口）。
 *
 * @author 阿清
 */
class DiscordWebhookNotifier(private val plugin: AntiLitematica) {

    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(3))
        .build()

    /** 发送 Discord 嵌入消息，返回是否成功 */
    fun send(title: String, fields: Map<String, String>, color: Int = 0xE74C3C): Boolean {
        val url = plugin.configHolder.discordWebhookUrl
        if (url.isBlank()) return false

        val embed = JsonObject().apply {
            addProperty("title", title)
            addProperty("color", color)
            add("fields", JsonArray().apply {
                fields.forEach { (k, v) ->
                    add(
                        JsonObject().apply {
                            addProperty("name", k)
                            addProperty("value", v.take(1000))
                            addProperty("inline", true)
                        },
                    )
                }
            })
            addProperty("timestamp", java.time.Instant.now().toString())
        }
        val payload = JsonObject().apply { add("embeds", JsonArray().apply { add(embed) }) }

        return post(url, payload.toString(), emptyMap())
    }

    private fun post(url: String, body: String, headers: Map<String, String>): Boolean {
        return try {
            val builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(5))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
            headers.forEach { (k, v) -> builder.header(k, v) }
            val response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString())
            response.statusCode() in 200..299
        } catch (e: Exception) {
            plugin.logger.warning("Discord Webhook 发送失败: ${e.message}")
            false
        }
    }
}
