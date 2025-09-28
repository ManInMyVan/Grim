package ac.grim.grimac.utils.lang;

import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

public sealed interface Language permits FallbackLanguage, ConfiguredLanguage {
    @Contract(value = "null -> null", pure = true)
    @Nullable String get(@Nullable String key);
}
