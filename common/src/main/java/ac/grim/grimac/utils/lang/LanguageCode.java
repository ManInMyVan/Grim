package ac.grim.grimac.utils.lang;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Locale;

public record LanguageCode(@NotNull String language, @NotNull String country) {
    @Contract(value = "_ -> new", pure = true)
    public static @NotNull LanguageCode of(@NotNull String code) {
        int index = code.indexOf('_');
        return index == -1
                ? of(code, "")
                : of(code.substring(0, index), code.substring(index));
    }

    @Contract(value = "_, _ -> new", pure = true)
    public static @NotNull LanguageCode of(@NotNull String language, @NotNull String country) {
        return new LanguageCode(language.toLowerCase(Locale.ROOT), country.toLowerCase(Locale.ROOT));
    }

    @Contract(pure = true)
    public @NotNull String code() {
        return language + (country.isEmpty() ? "" : "_" + country);
    }
}
