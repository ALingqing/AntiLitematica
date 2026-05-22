package top.chenray.antilitematica.util;

import java.io.File;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Manages multi-language message lookups with per-player locale auto-detection.
 * <p>
 * Supports 50+ Minecraft client locales with fallback chain:
 * Player locale → Base language → Server default → messages.yml
 * <p>
 * Built-in: zh_CN, en_US, zh_TW
 * Unsupported locales gracefully fall back through the chain.
 */
public final class LocaleManager {

    private static final Map<String, String> LOCALE_MAP = buildLocaleMap();

    private final Plugin plugin;
    private final String serverDefault;
    private final boolean autoLocale;
    private final Map<String, FileConfiguration> messageCache = new HashMap<>();

    public LocaleManager(Plugin plugin, String serverDefault, boolean autoLocale) {
        this.plugin = plugin;
        this.serverDefault = normalize(serverDefault);
        this.autoLocale = autoLocale;
    }

    /**
     * Get a message for a specific player, respecting their client locale.
     *
     * @param player the target player (can be null for console)
     * @param key    message key (e.g. "kick", "blocked_place")
     * @return the localized message string with & color codes
     */
    public String getMessage(Player player, String key) {
        return getMessage(player, key, null);
    }

    /**
     * Get a message with a fallback default value.
     */
    public String getMessage(Player player, String key, String defaultValue) {
        String locale = resolveLocale(player);
        FileConfiguration msgs = loadMessages(locale);
        if (msgs != null && msgs.contains(key)) {
            return msgs.getString(key);
        }
        // Try base language
        String base = locale.contains("_") ? locale.split("_")[0] : locale;
        if (!base.equals(locale)) {
            msgs = loadMessages(base);
            if (msgs != null && msgs.contains(key)) {
                return msgs.getString(key);
            }
        }
        // Try server default
        if (!locale.equals(serverDefault)) {
            msgs = loadMessages(serverDefault);
            if (msgs != null && msgs.contains(key)) {
                return msgs.getString(key);
            }
        }
        // Ultimate fallback: lang/messages.yml
        if (!"default".equals(locale)) {
            msgs = loadMessages("default");
            if (msgs != null && msgs.contains(key)) {
                return msgs.getString(key);
            }
        }
        return defaultValue != null ? defaultValue : "<missing:" + key + ">";
    }

    /**
     * Get the prefix for a player in their locale.
     */
    public String getPrefix(Player player) {
        return getMessage(player, "prefix", "&7[&cAntiLitematica&7] ");
    }

    /**
     * Get the kick message for a player in their locale.
     */
    public String getKickMessage(Player player) {
        return getMessage(player, "kick",
                "&cYou are not allowed to use Litematica / Printer on this server.");
    }

    /**
     * Get the blocked placement message for a player in their locale.
     */
    public String getBlockedPlaceMessage(Player player) {
        return getMessage(player, "blocked_place",
                "&cPlease aim at the block before placing.");
    }

    /**
     * Resolve which locale to use for a given player.
     */
    private String resolveLocale(Player player) {
        if (!autoLocale || player == null) {
            return serverDefault;
        }
        try {
            String clientLocale = player.getLocale(); // e.g. "zh_CN", "de_DE", "fr_FR"
            if (clientLocale != null && !clientLocale.isEmpty()) {
                return normalize(clientLocale);
            }
        } catch (Exception e) {
            // Player locale API may fail on older server versions
        }
        return serverDefault;
    }

    /**
     * Normalize a locale string to xx_XX format.
     */
    private static String normalize(String locale) {
        if (locale == null || locale.isEmpty()) return "zh_CN";
        // Handle Minecraft locale codes like "zh_cn" → "zh_CN"
        String[] parts = locale.split("[-_]");
        if (parts.length >= 2) {
            return parts[0].toLowerCase(Locale.ROOT) + "_" + parts[1].toUpperCase(Locale.ROOT);
        }
        // Map base language codes
        String lower = locale.toLowerCase(Locale.ROOT);
        String mapped = LOCALE_MAP.get(lower);
        if (mapped != null) return mapped;
        // Map from Java locale
        for (String[] entry : new String[][]{
            {"zh", "zh_CN"}, {"en", "en_US"}, {"de", "de_DE"}, {"fr", "fr_FR"},
            {"ja", "ja_JP"}, {"ko", "ko_KR"}, {"ru", "ru_RU"}, {"es", "es_ES"},
            {"pt", "pt_BR"}, {"it", "it_IT"}, {"nl", "nl_NL"}, {"pl", "pl_PL"},
            {"tr", "tr_TR"}, {"ar", "ar_SA"}, {"th", "th_TH"}, {"vi", "vi_VN"},
            {"sv", "sv_SE"}, {"fi", "fi_FI"}, {"da", "da_DK"}, {"no", "no_NO"},
            {"cs", "cs_CZ"}, {"hu", "hu_HU"}, {"ro", "ro_RO"}, {"uk", "uk_UA"},
            {"el", "el_GR"}, {"he", "he_IL"}, {"id", "id_ID"}, {"ms", "ms_MY"},
            {"tl", "tl_PH"}, {"hi", "hi_IN"}, {"bn", "bn_BD"}, {"ta", "ta_IN"},
            {"te", "te_IN"}, {"mr", "mr_IN"}, {"gu", "gu_IN"}, {"kn", "kn_IN"},
            {"ml", "ml_IN"}, {"pa", "pa_IN"}, {"ne", "ne_NP"}, {"si", "si_LK"},
            {"km", "km_KH"}, {"lo", "lo_LA"}, {"my", "my_MM"}, {"ka", "ka_GE"},
            {"hy", "hy_AM"}, {"az", "az_AZ"}, {"kk", "kk_KZ"}, {"uz", "uz_UZ"}
        }) {
            if (lower.equals(entry[0])) return entry[1];
        }
        return lower;
    }

    /**
     * Load a messages file by locale, with caching.
     * Files are stored in the lang/ subfolder, e.g. lang/messages_zh_CN.yml
     */
    private FileConfiguration loadMessages(String locale) {
        if (locale == null || locale.isEmpty()) return null;
        String key = locale.toLowerCase(Locale.ROOT);

        if (messageCache.containsKey(key)) {
            FileConfiguration cached = messageCache.get(key);
            return cached != null && !cached.getKeys(false).isEmpty() ? cached : null;
        }

        // lang/ subfolder
        File langDir = new File(plugin.getDataFolder(), "lang");
        String fileName = "default".equals(key) ? "messages.yml" : "messages_" + key + ".yml";
        File file = new File(langDir, fileName);
        // Also check plugin root for backward compatibility
        File rootFile = new File(plugin.getDataFolder(), fileName);
        if (!file.exists() && rootFile.exists()) {
            file = rootFile;
        }
        if (!file.exists()) {
            // Try reading from JAR resources/lang/ directly
            String resourcePath = "lang/" + ("default".equals(key) ? "messages.yml" : "messages_" + key + ".yml");
            if (plugin.getResource(resourcePath) != null) {
                if (!langDir.exists()) langDir.mkdirs();
                try (java.io.InputStream in = plugin.getResource(resourcePath)) {
                    if (in != null) {
                        java.nio.file.Files.copy(in, file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    }
                } catch (java.io.IOException e) {
                    plugin.getLogger().warning("Failed to save language file: " + resourcePath);
                    messageCache.put(key, null);
                    return null;
                }
            } else {
                messageCache.put(key, null);
                return null;
            }
        }

        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        if (cfg.getKeys(false).isEmpty()) {
            messageCache.put(key, null);
            return null;
        }

        messageCache.put(key, cfg);
        return cfg;
    }

    /**
     * Clear message cache (call on /al reload).
     */
    public void clearCache() {
        messageCache.clear();
    }

    /**
     * List of available locale codes (filenames without extension).
     */
    public Set<String> getAvailableLocales() {
        return messageCache.keySet();
    }

    /**
     * Map of Minecraft locale codes to file names.
     * Minecraft client sends lowercase codes like "zh_cn".
     */
    private static Map<String, String> buildLocaleMap() {
        Map<String, String> map = new LinkedHashMap<>();
        // Chinese variants
        map.put("zh_cn", "zh_CN"); map.put("zh", "zh_CN");
        map.put("zh_tw", "zh_TW"); map.put("zh_hk", "zh_TW");
        // English variants
        map.put("en_us", "en_US"); map.put("en", "en_US");
        map.put("en_gb", "en_US"); map.put("en_au", "en_US");
        map.put("en_ca", "en_US"); map.put("en_nz", "en_US");
        // Major European
        map.put("de_de", "de_DE"); map.put("de", "de_DE");
        map.put("fr_fr", "fr_FR"); map.put("fr", "fr_FR");
        map.put("es_es", "es_ES"); map.put("es", "es_ES");
        map.put("pt_br", "pt_BR"); map.put("pt", "pt_BR");
        map.put("it_it", "it_IT"); map.put("it", "it_IT");
        map.put("nl_nl", "nl_NL"); map.put("nl", "nl_NL");
        // Nordic
        map.put("sv_se", "sv_SE"); map.put("sv", "sv_SE");
        map.put("fi_fi", "fi_FI"); map.put("fi", "fi_FI");
        map.put("da_dk", "da_DK"); map.put("da", "da_DK");
        map.put("nb_no", "no_NO"); map.put("no", "no_NO");
        // Eastern European
        map.put("pl_pl", "pl_PL"); map.put("pl", "pl_PL");
        map.put("cs_cz", "cs_CZ"); map.put("cs", "cs_CZ");
        map.put("hu_hu", "hu_HU"); map.put("hu", "hu_HU");
        map.put("ro_ro", "ro_RO"); map.put("ro", "ro_RO");
        map.put("uk_ua", "uk_UA"); map.put("uk", "uk_UA");
        map.put("el_gr", "el_GR"); map.put("el", "el_GR");
        map.put("bg_bg", "bg_BG"); map.put("bg", "bg_BG");
        map.put("sr_sp", "sr_SP"); map.put("hr", "hr_HR");
        map.put("sk_sk", "sk_SK"); map.put("sl", "sl_SI");
        map.put("et_ee", "et_EE"); map.put("lv", "lv_LV");
        map.put("lt_lt", "lt_LT");
        // Asian
        map.put("ja_jp", "ja_JP"); map.put("ja", "ja_JP");
        map.put("ko_kr", "ko_KR"); map.put("ko", "ko_KR");
        map.put("th_th", "th_TH"); map.put("th", "th_TH");
        map.put("vi_vn", "vi_VN"); map.put("vi", "vi_VN");
        map.put("id_id", "id_ID"); map.put("id", "id_ID");
        map.put("ms_my", "ms_MY"); map.put("ms", "ms_MY");
        map.put("tl_ph", "tl_PH"); map.put("fil", "tl_PH");
        // South Asian
        map.put("hi_in", "hi_IN"); map.put("hi", "hi_IN");
        map.put("bn_bd", "bn_BD"); map.put("bn", "bn_BD");
        map.put("ta_in", "ta_IN"); map.put("te", "te_IN");
        map.put("mr_in", "mr_IN"); map.put("gu", "gu_IN");
        map.put("kn_in", "kn_IN"); map.put("ml", "ml_IN");
        map.put("pa_in", "pa_IN"); map.put("ne", "ne_NP");
        map.put("si_lk", "si_LK");
        // Middle Eastern
        map.put("ar_sa", "ar_SA"); map.put("ar", "ar_SA");
        map.put("he_il", "he_IL"); map.put("he", "he_IL");
        map.put("tr_tr", "tr_TR"); map.put("tr", "tr_TR");
        map.put("fa_ir", "fa_IR"); map.put("ur", "ur_PK");
        // Russian / CIS
        map.put("ru_ru", "ru_RU"); map.put("ru", "ru_RU");
        map.put("kk_kz", "kk_KZ"); map.put("az", "az_AZ");
        map.put("hy_am", "hy_AM"); map.put("ka", "ka_GE");
        map.put("uz_uz", "uz_UZ");
        // Other
        map.put("af_za", "af_ZA"); map.put("sw", "sw_TZ");
        map.put("cy_gb", "cy_GB"); map.put("ga", "ga_IE");
        map.put("mt_mt", "mt_MT"); map.put("is", "is_IS");
        map.put("lb_lu", "lb_LU");
        return map;
    }
}
