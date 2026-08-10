package icu.epochcraft.antilitematica.detection

/**
 * 通道特征指纹库：已知插件通道 -> mod 名称说明。
 *
 * 用于菜单/命令展示"这个通道是什么 mod"，以及快速参考。
 * 实际拦截仍以 config.yml 中的通道配置为准。
 *
 * @author 阿清
 */
object ChannelRegistry {

    data class ChannelInfo(val channel: String, val mod: String, val note: String)

    /** 已知通道指纹库 */
    val KNOWN_CHANNELS: List<ChannelInfo> = listOf(
        // ---- 投影类 ----
        ChannelInfo("servux:litematics", "Litematica", "投影 mod，Fabric 26.x / Forge 移植版均注册"),
        ChannelInfo("litematica:main", "Litematica", "旧版本投影通道"),
        ChannelInfo("schematica", "Schematica", "1.12 旧版投影 mod"),
        ChannelInfo("malilib:main", "malilib", "masa 系列 mod 前置库"),
        // ---- Servux 系 ----
        ChannelInfo("servux:main", "Servux", "Servux 本体通道"),
        ChannelInfo("servux:hello", "Servux", "Servux 握手通道"),
        // ---- 环境识别 ----
        ChannelInfo("fml:handshake", "Forge/FML", "Forge 配置阶段握手通道"),
        ChannelInfo("fml:play", "Forge/FML", "Forge play 阶段通道"),
        ChannelInfo("FML|HS", "Forge/FML", "旧版 Forge 握手通道"),
        ChannelInfo("fabric:registry", "Fabric API", "Fabric 环境注册表同步通道"),
        ChannelInfo("fabric:item_group", "Fabric API", "Fabric 环境通道"),
        // ---- 常用作弊类（供参考，默认不启用） ----
        ChannelInfo("minema:main", "Minema", "录屏 mod"),
        ChannelInfo("vanillaplus:main", "VanillaPlus", "小地图/光影辅助 mod"),
    )

    /** 根据通道名反查 mod 说明 */
    fun describe(channel: String): String? =
        KNOWN_CHANNELS.firstOrNull { it.channel.equals(channel, ignoreCase = true) }?.let {
            "${it.mod}（${it.note}）"
        }

    /** 是否为环境识别类通道（Forge / Fabric，用于预设联动） */
    fun isEnvironmentChannel(channel: String): Boolean {
        val lower = channel.lowercase()
        return lower.startsWith("fml:") || lower.startsWith("fabric:") || lower == "fml|hs"
    }

    /**
     * 通道是否与某 modid 关联（交叉验证用：mod 列表与通道互证）。
     * 正常客户端装了该 mod 必然注册对应通道；交叉验证发现缺失即可疑。
     */
    fun associatesMod(channel: String, modId: String): Boolean {
        val lower = channel.lowercase()
        return when (modId) {
            "litematica" -> lower.contains("litematic") ||
                lower == "servux:litematics" || lower == "servux:litematica"
            "litematica-printer" -> false // 无独立通道，依赖 Litematica
            "servux" -> lower.startsWith("servux")
            "schematica" -> lower.startsWith("schematica")
            "malilib" -> lower.startsWith("malilib")
            "minihud" -> lower.startsWith("minihud")
            "tweakeroo" -> lower.startsWith("tweakeroo")
            "itemscroller" -> lower.startsWith("itemscroller")
            else -> false
        }
    }
}
