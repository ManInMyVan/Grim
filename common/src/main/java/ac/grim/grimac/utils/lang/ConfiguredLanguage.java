package ac.grim.grimac.utils.lang;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class ConfiguredLanguage implements Language {
    private @NotNull Language fallback = FallbackLanguage.INSTANCE;
    public final @NotNull Map<@NotNull String, @NotNull String> translations = new HashMap<>();

    @Contract(value = "null -> null", pure = true)
    public @Nullable String get(@Nullable String key) {
        if (key == null) return null;
        String value = translations.get(key);
        if (value != null) return value;
        value = getFromFallback(key);
        if (value != null) translations.put(key, value);
        return value;
    }

    private @Nullable String getFromFallback(@NotNull String key) {
        Language lang = fallback;

        Set<Language> seenLanguages = new HashSet<>();

        while (seenLanguages.add(lang) && lang instanceof ConfiguredLanguage language) {
            String value = language.translations.get(key);
            if (value != null) return value;
            lang = language.fallback;
        }

        return fallback.get(key);
    }

    @Contract(mutates = "this")
    public void setFallback(Language language) {
        fallback = language != null ? language : FallbackLanguage.INSTANCE;
    }

    @Contract(mutates = "this")
    public void setTranslations(Map<String, String> translations) {
        this.translations.clear();
        this.translations.putAll(translations);
    }
}
