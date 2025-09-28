package ac.grim.grimac.utils.lang;

import ac.grim.grimac.api.config.ConfigManager;
import ac.grim.grimac.api.config.ConfigReloadable;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

// TODO: actually use this
public class LanguageManager implements ConfigReloadable {
    public final @NotNull Map<@NotNull String, @NotNull String> fallbacks = new HashMap<>();
    public final @NotNull HashMap<@NotNull String, @NotNull ConfiguredLanguage> languages = new HashMap<>();

    // TODO: cache
    @Contract(pure = true)
    public @NotNull Language getSystemLanguage() {
        return get(LanguageCodes.getSystemLanguageCode().code());
    }

    // TODO: cache
    @Contract(pure = true)
    public @NotNull Language getConsoleLanguage() {
        return get(LanguageCodes.getConsoleLanguageCode().code());
    }

    // TODO: cache
    @Contract(pure = true)
    public @NotNull Language getDefaultLanguage() {
        return get(LanguageCodes.getDefaultLanguageCode().code());
    }

    // TODO: fallback from no country to any with same language
    public @NotNull Language get(String code) {
        if (code == null) return getDefaultLanguage();
        Language lang = languages.get(code);
        if (lang != null) return lang;
        String fallback = fallbacks.get(code);
        if (fallback != null) return get(fallback);
        return FallbackLanguage.INSTANCE;
    }

    public void reload(ConfigManager config) {
        String code = config.getString("default");
        LanguageCodes.setConsoleLanguageCode(code == null || code.equalsIgnoreCase("system")
                || code.equalsIgnoreCase("default") ? null : LanguageCode.of(code));

        code = config.getString("default");
        LanguageCodes.setDefaultLanguageCode(code == null || code.equalsIgnoreCase("system")
                || code.equalsIgnoreCase("default") ? null : LanguageCode.of(code));

        reloadLanguages();
        reloadFallbacks(config);
    }

    // TODO: actually implement this
    //  - fallback to no country (ie en_us -> en)
    //  - allow configured fallbacks
    private void reloadFallbacks(ConfigManager config) {
        fallbacks.clear();

        for (ConfiguredLanguage language : languages.values()) {
            language.setFallback(null);
        }

        Map<String, String> fallbacks = config.getMap("fallbacks");
    }

    private void reloadLanguages() {
        Map<String, Map<String, String>> languages = LanguageUtil.getLanguages();
        this.languages.keySet().retainAll(languages.keySet());

        for (Map.Entry<String, Map<String, String>> entry : languages.entrySet()) {
            ConfiguredLanguage language = this.languages.computeIfAbsent(entry.getKey(), k -> new ConfiguredLanguage());
            language.setTranslations(entry.getValue());
        }
    }
}
