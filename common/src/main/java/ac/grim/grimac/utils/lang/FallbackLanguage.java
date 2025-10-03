package ac.grim.grimac.utils.lang;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

public enum FallbackLanguage implements Language {
    INSTANCE;

    // TODO: add more defaults
    @Contract(value = "null -> null", pure = true)
    @Override
    public @Nullable String get(@Nullable String key) {
        if (key == null) return null;
        return switch (key) {
            case "disconnect.timeout" -> "<lang:disconnect.timeout>";
            default -> null;
        };
    }
}
