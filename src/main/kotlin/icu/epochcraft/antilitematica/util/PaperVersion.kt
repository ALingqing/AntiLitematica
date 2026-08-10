package icu.epochcraft.antilitematica.util

import org.bukkit.Bukkit

/**
 * 服务端版本工具：解析 Bukkit 版本字符串，并提供能力判断。
 */
object PaperVersion {

    /** 语义化版本号 */
    data class Version(val major: Int, val minor: Int, val patch: Int = 0) : Comparable<Version> {

        override fun compareTo(other: Version): Int = when {
            major != other.major -> major - other.major
            minor != other.minor -> minor - other.minor
            else -> patch - other.patch
        }

        override fun toString(): String = "$major.$minor.$patch"
    }

    private val versionPattern = Regex("""(\d+)\.(\d+)(?:\.(\d+))?""")

    /** 当前服务端版本（如 1.21.7 / 26.2） */
    val current: Version = parse(Bukkit.getBukkitVersion())

    /**
     * 服务端是否支持 Paper Dialog API。
     *
     * 原版 Dialog 功能于 Minecraft 1.21.6 加入，Paper 从 1.21.6 起提供
     * `io.papermc.paper.dialog.*` / `io.papermc.paper.registry.data.dialog.*` API
     * （1.21.11 与 26.2 的 Dialog API 类结构完全一致）。
     * 这里同时校验版本号与 API 类是否存在，双保险：
     *   - 旧版本服务端（< 1.21.6）：类不存在 → false → 使用箱子菜单
     *   - 1.21.6+ / 26.x：类存在 → true → 使用 Dialog 菜单
     */
    val supportsDialogApi: Boolean by lazy {
        current >= Version(1, 21, 6) && hasDialogClasses()
    }

    /** 解析 "1.21.7-R0.1-SNAPSHOT" / "26.2-..." 之类的版本字符串 */
    fun parse(raw: String): Version {
        val match = versionPattern.find(raw) ?: return Version(0, 0, 0)
        return Version(
            major = match.groupValues[1].toIntOrNull() ?: 0,
            minor = match.groupValues[2].toIntOrNull() ?: 0,
            patch = match.groupValues[3].toIntOrNull() ?: 0,
        )
    }

    /**
     * Dialog API 相关类在当前服务端是否存在。
     *
     * 注意类路径：`DialogType` 位于 `...dialog.type` 子包（不是 `...dialog` 顶层），
     * 写错会导致 Class.forName 永远失败，误判为"不支持 Dialog API"。
     */
    private fun hasDialogClasses(): Boolean = runCatching {
        Class.forName("io.papermc.paper.dialog.Dialog")
        Class.forName("io.papermc.paper.registry.data.dialog.type.DialogType")
        Class.forName("io.papermc.paper.registry.data.dialog.body.DialogBody")
    }.isSuccess
}
