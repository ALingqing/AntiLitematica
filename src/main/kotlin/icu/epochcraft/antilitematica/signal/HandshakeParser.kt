package icu.epochcraft.antilitematica.signal

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser

/**
 * FML / Fabric 握手 Mod 列表解析器（纯逻辑，零外部依赖）。
 *
 * 原理：所有 Forge/NeoForge/Fabric 客户端在握手阶段**必定**上报完整 mod 列表
 * （modid + 版本），即使 mod 不注册插件通道（或客户端禁用通道上报）也能拿到。
 *
 * 支持的握手协议：
 *   - `fml:handshake`（Forge / NeoForge 1.13 - 1.20.1）：
 *     包体 = VarInt 类型（0=HELLO）+ VarInt 网络版本 + String FML 版本
 *            + VarInt mod 数量 + N × (String modId, String version)
 *   - `fml:login`（Forge / NeoForge 1.20.2+，configuration 阶段）：
 *     包体 = String 子通道 id（如 fml:login/modlist）+ String JSON
 *   - `fabric:handshake`（Fabric 1.16.2 - 1.20.4，play 阶段）：
 *     包体 = VarInt 标记（0=hello）+ String 游戏版本 + String JSON
 *   - `fabric:login`（Fabric 1.20.5+，configuration 阶段）：
 *     包体 = String 子通道 id + String JSON
 *
 * 解析采用"宽进严出"策略：任何格式解析失败都返回 null（只记录日志、绝不误伤），
 * 只有成功提取出完整 mod 列表才算命中。
 *
 * @author 阿清
 */
object HandshakeParser {

    /** Mod 加载器类型 */
    enum class ModLoader(val displayName: String) {
        FORGE("Forge"),
        NEOFORGE("NeoForge"),
        FABRIC("Fabric"),
        UNKNOWN("Unknown"),
    }

    /** 一次成功解析出的 mod 列表 */
    data class ParsedModList(
        val loader: ModLoader,
        val loaderVersion: String?,
        val gameVersion: String?,
        /** modid(小写) -> 版本 */
        val mods: Map<String, String>,
    ) {
        /** 证据摘要：如 "Fabric 1.20.4 [fabricloader:0.15.11, minecraft:1.20.4, litematica:0.19.5]" */
        fun summary(): String {
            val ver = gameVersion?.let { " $it" } ?: ""
            val loaderVer = loaderVersion?.let { " (loader $it)" } ?: ""
            val modsStr = mods.entries.sortedBy { it.key }.joinToString(", ") { "${it.key}:${it.value}" }
            return "${loader.displayName}$ver$loaderVer [$modsStr]"
        }
    }

    /** 是否为可解析的握手通道（拦截过滤用，降低无关包开销） */
    fun isHandshakeChannel(channel: String): Boolean {
        val lower = channel.lowercase()
        return lower == "fml:handshake" || lower == "fml:login" ||
            lower == "fabric:handshake" || lower == "fabric:login"
    }

    /** 解析握手包 payload；解析失败返回 null（不抛异常） */
    fun parse(channel: String, data: ByteArray): ParsedModList? = try {
        val lower = channel.lowercase()
        when {
            lower == "fml:handshake" -> parseFmlHandshake(data)
            lower == "fml:login" -> parseFmlLogin(data)
            lower == "fabric:handshake" -> parseFabricHandshake(data)
            lower == "fabric:login" -> parseFabricLogin(data)
            else -> null
        }
    } catch (e: Exception) {
        null
    }

    // ---------------- FML: fml:handshake（1.13 - 1.20.1） ----------------

    private fun parseFmlHandshake(data: ByteArray): ParsedModList? {
        val buf = BufReader(data)
        // 包类型：0 = HELLO（客户端→服务端第一个包，携带 mod 列表）
        val type = buf.readVarInt()
        if (type != 0) return null

        buf.readVarInt() // FML 网络版本（1.13+ 恒为 70），跳过
        val fmlVersion = buf.readString()
        val modCount = buf.readVarInt()
        if (modCount <= 0 || modCount > 500) return null // 防御异常值

        val mods = linkedMapOf<String, String>()
        repeat(modCount) {
            val modId = buf.readString().lowercase()
            val version = buf.readString()
            if (modId.isNotEmpty()) mods[modId] = version
        }
        if (mods.isEmpty()) return null
        return ParsedModList(ModLoader.FORGE, fmlVersion, null, mods)
    }

    // ---------------- FML: fml:login（1.20.2+ / NeoForge） ----------------

    private fun parseFmlLogin(data: ByteArray): ParsedModList? {
        val buf = BufReader(data)
        // 子通道 id，如 fml:login/hello / fml:login/modlist
        val payloadId = buf.readString()
        if (!payloadId.contains("modlist", ignoreCase = true)) return null
        val json = buf.readString()
        val mods = extractMods(json) ?: return null
        if (mods.isEmpty()) return null
        val loader = if (payloadId.startsWith("neoforge") ||
            payloadId.contains("neoforge") ||
            mods.containsKey("neoforge")
        ) ModLoader.NEOFORGE else ModLoader.FORGE
        return ParsedModList(loader, mods["neoforge"] ?: mods["forge"], null, mods)
    }

    // ---------------- Fabric: fabric:handshake（1.16.2 - 1.20.4） ----------------

    private fun parseFabricHandshake(data: ByteArray): ParsedModList? {
        val buf = BufReader(data)
        val marker = buf.readVarInt()
        if (marker != 0) return null // 0 = hello（携带 mod 列表）
        val gameVersion = buf.readString()
        val json = buf.readString()
        val mods = extractMods(json) ?: return null
        if (mods.isEmpty()) return null
        return ParsedModList(ModLoader.FABRIC, mods["fabricloader"], gameVersion, mods)
    }

    // ---------------- Fabric: fabric:login（1.20.5+） ----------------

    private fun parseFabricLogin(data: ByteArray): ParsedModList? {
        val buf = BufReader(data)
        val payloadId = buf.readString()
        if (buf.remaining() <= 0) return null
        val json = buf.readString()
        val mods = extractMods(json) ?: return null
        if (mods.isEmpty()) return null
        return ParsedModList(
            ModLoader.FABRIC,
            mods["fabricloader"],
            mods["minecraft"],
            mods,
        )
    }

    // ---------------- JSON mod 列表提取（容错多种格式） ----------------

    /**
     * 从 JSON 中提取 modid -> version 映射，支持：
     *   1. {"mods": [{"modId": "...", "version": "..."}, ...], ...}（Forge/NeoForge）
     *   2. {"mods": {"modid": "version", ...}, ...}（Fabric）
     *   3. 整个对象就是 modid -> version 映射（Fabric handshake）
     * 解析失败返回 null。
     */
    private fun extractMods(json: String): Map<String, String>? {
        val root = try {
            JsonParser.parseString(json).asJsonObject
        } catch (e: Exception) {
            return null
        }
        val node = root.get("mods") ?: root
        return when {
            node.isJsonObject -> extractFromObject(node.asJsonObject)
            node.isJsonArray -> extractFromArray(node.asJsonArray)
            else -> null
        }
    }

    private fun extractFromObject(obj: JsonObject): Map<String, String> {
        val mods = linkedMapOf<String, String>()
        obj.entrySet().forEach { (key, value) ->
            val id = key.lowercase()
            if (id.isEmpty() || id.startsWith("__")) return@forEach
            mods[id] = value?.let { safeString(it) } ?: "?"
        }
        return mods
    }

    private fun extractFromArray(arr: JsonArray): Map<String, String> {
        val mods = linkedMapOf<String, String>()
        for (element in arr) {
            val obj = element.asJsonObject
            val id = obj.get("modId")?.let { safeString(it) }
                ?: obj.get("id")?.let { safeString(it) }
                ?: continue
            val version = obj.get("version")?.let { safeString(it) } ?: "?"
            if (id.isNotEmpty()) mods[id.lowercase()] = version
        }
        return mods
    }

    /** JsonElement 安全转 String（version 可能是数字） */
    private fun safeString(element: com.google.gson.JsonElement): String? =
        if (element.isJsonPrimitive) element.asString else element.toString()

    // ---------------- Minecraft 协议原语读取器 ----------------

    /** 仅读取的 ByteArray 游标（VarInt / 长度前缀 String） */
    private class BufReader(private val data: ByteArray, private var pos: Int = 0) {

        /** 剩余可读字节数 */
        fun remaining(): Int = data.size - pos

        /** 读取 Minecraft VarInt（最多 5 字节，7 bit / 字节，最高位为续读标记） */
        fun readVarInt(): Int {
            var value = 0
            var shift = 0
            while (true) {
                if (pos >= data.size) throw IllegalStateException("VarInt 越界")
                val byte = data[pos++].toInt() and 0xFF
                value = value or ((byte and 0x7F) shl shift)
                if (byte and 0x80 == 0) break
                shift += 7
                if (shift > 35) throw IllegalStateException("VarInt 过长")
            }
            return value
        }

        /** 读取长度前缀 UTF-8 字符串 */
        fun readString(): String {
            val length = readVarInt()
            if (length < 0 || length > 1 shl 20) throw IllegalStateException("String 长度异常: $length")
            if (pos + length > data.size) throw IllegalStateException("String 越界: $length")
            val str = String(data, pos, length, Charsets.UTF_8)
            pos += length
            return str
        }
    }
}
