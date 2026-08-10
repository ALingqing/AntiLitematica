package icu.epochcraft.antilitematica.config

import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.io.File
import java.util.concurrent.ConcurrentHashMap

/**
 * 多语言管理：从 <数据文件夹>/lang/ 目录加载语言文件（zh_cn / zh_tw / en_us ...）。
 *
 * - 插件 jar 内置默认语言文件，首次启动自动复制到数据文件夹（可自由修改/新增）
 * - 通过 config.yml 的 `language` 项切换语言（/antilitematica reload 生效）
 * - 回退链：所选语言缺失的键 → 中文(zh_cn) → 键名本身
 *
 * @author 阿清
 */
class LangManager(private val plugin: JavaPlugin, private val langDir: File) {

    private var lang: YamlConfiguration = YamlConfiguration()

    /** 当前语言代码（如 zh_cn / en_us） */
    var currentLang: String = DEFAULT_LANG
        private set

    /** 数据文件夹下可用的语言代码列表 */
    val availableLanguages: List<String>
        get() = langDir.listFiles { f -> f.isFile && f.extension.equals("yml", true) }
            ?.map { it.nameWithoutExtension.lowercase() }
            ?.sorted()
            ?: emptyList()

    /**
     * 加载指定语言；language 为空或非法时回退默认中文。
     * 首次调用会先把 jar 内置语言文件复制到数据文件夹。
     */
    fun load(language: String? = null) {
        copyDefaults()
        val code = normalize(language ?: currentLang)
        val file = File(langDir, "$code.yml")
        if (file.exists()) {
            lang = YamlConfiguration.loadConfiguration(file)
            currentLang = normalize(lang.getString("lang") ?: code)
        } else {
            plugin.logger.warning("未找到语言文件 lang/$code.yml，回退到默认语言 $DEFAULT_LANG")
            lang = YamlConfiguration.loadConfiguration(File(langDir, "$DEFAULT_LANG.yml"))
            currentLang = DEFAULT_LANG
        }
    }

    /** 重新加载当前语言（reload 时调用） */
    fun reload() = load(currentLang)

    /** 取一条消息（缺失时回退中文，再回退键名） */
    fun get(key: String): String =
        lang.getString(key)
            ?: fallback(DEFAULT_LANG).getString(key)
            ?: key

    /** 取消息并替换 {占位符} */
    fun get(key: String, vararg placeholders: Pair<String, Any>): String {
        var text = get(key)
        placeholders.forEach { (k, v) -> text = text.replace("{$k}", v.toString()) }
        return text
    }

    // ---------------- 内部 ----------------

    private fun copyDefaults() {
        langDir.mkdirs()
        BUILTIN_LANGS.forEach { code ->
            val target = File(langDir, "$code.yml")
            if (!target.exists()) {
                plugin.getResource("lang/$code.yml")?.use { input ->
                    target.outputStream().use { out -> input.copyTo(out) }
                    plugin.logger.info("已生成默认语言文件: lang/$code.yml")
                } ?: plugin.logger.warning("内置语言文件缺失: lang/$code.yml")
            }
        }
    }

    /** 中文兜底缓存（避免重复读盘） */
    private fun fallback(code: String): YamlConfiguration =
        fallbackCache.getOrPut(code) {
            val f = File(langDir, "$code.yml")
            if (f.exists()) YamlConfiguration.loadConfiguration(f) else YamlConfiguration()
        }

    private fun normalize(code: String?): String = (code ?: DEFAULT_LANG).trim().lowercase()

    companion object {

        /** 默认语言（兜底语言，保证任意语言缺失键都能显示中文） */
        const val DEFAULT_LANG = "zh_cn"

        /** 插件 jar 内置的语言文件（首次启动自动复制到数据文件夹） */
        val BUILTIN_LANGS: List<String> = listOf("zh_cn", "zh_tw", "en_us")

        private val fallbackCache = ConcurrentHashMap<String, YamlConfiguration>()
    }
}
