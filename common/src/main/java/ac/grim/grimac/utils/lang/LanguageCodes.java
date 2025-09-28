package ac.grim.grimac.utils.lang;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.UtilityClass;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

// TODO: merge with LanguageManager?
@UtilityClass
public class LanguageCodes {
    @Getter private final @NotNull LanguageCode systemLanguageCode = LanguageCode.of(
            System.getProperty("user.language"),
            System.getProperty("user.country")
    );
    @Setter private LanguageCode consoleLanguageCode;
    @Setter private LanguageCode defaultLanguageCode;

    @Contract(pure = true)
    public @NotNull LanguageCode getConsoleLanguageCode() {
        return consoleLanguageCode != null ? consoleLanguageCode : getDefaultLanguageCode();
    }

    @Contract(pure = true)
    public @NotNull LanguageCode getDefaultLanguageCode() {
        return defaultLanguageCode != null ? defaultLanguageCode : systemLanguageCode;
    }
}
