package ac.grim.grimac.utils.lang;

import ac.grim.grimac.api.config.ConfigManager;
import ac.grim.grimac.api.config.ConfigReloadable;
import lombok.Getter;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

// TODO: actually use this
public class LanguageManager implements ConfigReloadable {
    private static final @NotNull LanguageCode systemLanguageCode = LanguageCode.of(
            System.getProperty("user.language"),
            System.getProperty("user.country")
    );

    public final @NotNull Map<@NotNull String, @NotNull String> fallbacks = new HashMap<>();
    public final @NotNull HashMap<@NotNull String, @NotNull ConfiguredLanguage> languages = new HashMap<>();
    @Getter private @NotNull Language systemLanguage = FallbackLanguage.INSTANCE;
    @Getter private @NotNull Language consoleLanguage = FallbackLanguage.INSTANCE;
    @Getter private @NotNull Language defaultLanguage = FallbackLanguage.INSTANCE;

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
        reloadLanguages();
        reloadFallbacks(config);

        systemLanguage = get(systemLanguageCode.code());

        String code = config.getString("default-language");
        defaultLanguage = code == null || code.equalsIgnoreCase("system") || code.equalsIgnoreCase("default")
                ? systemLanguage : get(code);

        code = config.getString("console-language");
        consoleLanguage = code == null || code.equalsIgnoreCase("system") || code.equalsIgnoreCase("default")
                ? defaultLanguage : get(code);
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
        @NotNull Map<String, LanguageFile> languageFiles = LanguageFile.getLanguages();
        languages.keySet().retainAll(languageFiles.keySet());

        for (Map.Entry<String, LanguageFile> entry : languageFiles.entrySet()) {
            ConfiguredLanguage language = languages.computeIfAbsent(entry.getKey(), k -> new ConfiguredLanguage());
            language.setTranslations(entry.getValue().translations());
        }
    }
}
