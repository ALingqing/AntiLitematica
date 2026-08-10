package icu.epochcraft.antilitematica.sync

import icu.epochcraft.antilitematica.AntiLitematica
import icu.epochcraft.antilitematica.database.BanRecord
import icu.epochcraft.antilitematica.punish.ViolationRecord
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.messaging.PluginMessageListener
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.util.UUID

/**
 * 跨服同步：通过 BungeeCord / Velocity 插件消息在服务器网络内同步违规记录与封禁。
 *
 * 频道：
 *   - `al:violation`  违规记录广播（渐进惩罚计数跨服共享，防换服清零重来）
 *   - `al:punish`     封禁动作广播（全网封禁，防换服绕封）
 *
 * 需在代理环境（BungeeCord / Velocity）使用，config.yml 开启 `cross-server-sync.enabled` 生效。
 * 接收端封禁时置 [suppressBroadcast]，避免本地再次广播形成循环。
 *
 * @author 阿清
 */
class CrossServerSync(private val plugin: AntiLitematica) : PluginMessageListener {

    companion object {
        private const val CHANNEL_VIOLATION = "al:violation"
        private const val CHANNEL_PUNISH = "al:punish"
        private const val BUNGEECORD_CHANNEL = "BungeeCord"
    }

    private var enabled = false

    /** 接收跨服封禁时置 true，抑制本地再次广播（防循环） */
    @Volatile
    private var suppressBroadcast = false

    val isEnabled: Boolean get() = enabled

    /** 启用跨服同步（配置开启时注册频道） */
    fun enable() {
        if (!plugin.configHolder.crossServerSyncEnabled) return
        try {
            Bukkit.getMessenger().registerOutgoingPluginChannel(plugin, BUNGEECORD_CHANNEL)
            Bukkit.getMessenger().registerIncomingPluginChannel(plugin, CHANNEL_VIOLATION, this)
            Bukkit.getMessenger().registerIncomingPluginChannel(plugin, CHANNEL_PUNISH, this)
            enabled = true
            plugin.logger.info("跨服同步已启用（通道: $CHANNEL_VIOLATION / $CHANNEL_PUNISH）")
        } catch (e: Exception) {
            plugin.logger.warning("跨服同步启用失败: ${e.message}")
        }
    }

    fun disable() {
        if (!enabled) return
        try {
            Bukkit.getMessenger().unregisterOutgoingPluginChannel(plugin, BUNGEECORD_CHANNEL)
            Bukkit.getMessenger().unregisterIncomingPluginChannel(plugin, CHANNEL_VIOLATION, this)
            Bukkit.getMessenger().unregisterIncomingPluginChannel(plugin, CHANNEL_PUNISH, this)
        } catch (_: Exception) {
        }
        enabled = false
    }

    // ---------------- 发送 ----------------

    /** 广播违规记录（渐进惩罚计数跨服共享） */
    fun broadcastViolation(player: Player, world: String, count: Int, total: Int) {
        if (!enabled) return
        try {
            val data = writeMessage {
                writeUTF(player.uniqueId.toString())
                writeUTF(world)
                writeInt(count)
                writeInt(total)
                writeLong(System.currentTimeMillis())
                writeUTF(player.name)
            }
            forward(CHANNEL_VIOLATION, data)
        } catch (e: Exception) {
            plugin.logger.warning("跨服同步：违规广播失败: ${e.message}")
        }
    }

    /** 本地封禁后广播（接收端不再转发，防循环） */
    fun onLocalBan(uuid: UUID, name: String, reason: String, permanent: Boolean) {
        if (!enabled || suppressBroadcast) return
        try {
            val data = writeMessage {
                writeUTF(uuid.toString())
                writeUTF(if (permanent) "ban" else "tempban")
                writeUTF(reason)
                writeLong(System.currentTimeMillis())
                writeUTF(name)
            }
            forward(CHANNEL_PUNISH, data)
        } catch (e: Exception) {
            plugin.logger.warning("跨服同步：封禁广播失败: ${e.message}")
        }
    }

    /** 通过 BungeeCord Forward 子通道转发到所有子服 */
    private fun forward(subChannel: String, data: ByteArray) {
        val player = Bukkit.getOnlinePlayers().firstOrNull() ?: return
        val payload = writeMessage {
            writeUTF("Forward")
            writeUTF("ALL")
            writeUTF(subChannel)
            writeShort(data.size)
            write(data)
        }
        player.sendPluginMessage(plugin, BUNGEECORD_CHANNEL, payload)
    }

    /** 便捷：写入 DataOutputStream 并返回字节数组 */
    private inline fun writeMessage(block: DataOutputStream.() -> Unit): ByteArray =
        ByteArrayOutputStream().use { bout ->
            DataOutputStream(bout).use { out -> out.block() }
            bout.toByteArray()
        }

    // ---------------- 接收 ----------------

    override fun onPluginMessageReceived(channel: String, player: Player, message: ByteArray) {
        if (!enabled) return
        try {
            val input = DataInputStream(ByteArrayInputStream(message))
            when (channel) {
                CHANNEL_VIOLATION -> handleViolation(input)
                CHANNEL_PUNISH -> handlePunish(input)
            }
        } catch (e: Exception) {
            plugin.logger.warning("跨服同步：消息处理失败: ${e.message}")
        }
    }

    /** 导入违规记录：本地渐进惩罚计数跨服共享 */
    private fun handleViolation(input: DataInputStream) {
        val uuid = UUID.fromString(input.readUTF())
        val world = input.readUTF().ifBlank { null }
        val count = input.readInt()
        val total = input.readInt()
        val timestamp = input.readLong()
        val name = input.readUTF()

        plugin.database.upsertViolation(
            ViolationRecord(
                uuid = uuid,
                playerName = name,
                count = count,
                firstViolation = timestamp,
                lastViolation = timestamp,
                totalViolations = total,
                world = world,
            ),
        )
    }

    /** 全网封禁：本地执行封禁（不递归广播） */
    private fun handlePunish(input: DataInputStream) {
        val uuid = UUID.fromString(input.readUTF())
        val action = input.readUTF()
        val reason = input.readUTF()
        input.readLong() // timestamp
        val name = input.readUTF()

        if (action == "ban" || action == "tempban") {
            suppressBroadcast = true
            try {
                plugin.banManager.ban(uuid, name, "[Cross-Server] $reason", BanRecord.PERMANENT)
            } finally {
                suppressBroadcast = false
            }
        }
    }
}
