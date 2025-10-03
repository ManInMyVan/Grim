package ac.grim.grimac.utils.lang;

import ac.grim.grimac.GrimAPI;
import ac.grim.grimac.utils.anticheat.LogUtil;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static ac.grim.grimac.GrimAPI.GSON;

public record LanguageFile(Map<String, String> translations) {
    private static final File LANGUAGES_DIRECTORY = new File(GrimAPI.INSTANCE.getGrimPlugin().getDataFolder(), "lang");

    public static @NotNull Map<String, LanguageFile> getLanguages() {
        File[] files = LANGUAGES_DIRECTORY.listFiles(file -> file.isFile() && file.getName().endsWith(".json"));

        if (files == null) {
            return Collections.emptyMap();
        }

        Map<String, LanguageFile> map = new HashMap<>();

        for (File file : files) {
            String name = file.getName();
            name = name.substring(0, name.length() - ".json".length());

            JsonObject object;
            try (FileReader reader = new FileReader(file)) {
                JsonElement element = GSON.fromJson(reader, JsonElement.class);
                if (element instanceof JsonObject o) {
                    object = o;
                } else {
                    LogUtil.warn("language file " + name + " is not a json object, skipping");
                    continue;
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }

            Map<String, String> translations = new HashMap<>();
            {
                JsonElement element = object.get("translations");
                if (element instanceof JsonObject o) {
                    for (var e : o.entrySet()) {
                        if (e.getValue() instanceof JsonPrimitive primitive && primitive.isString()) {
                            translations.put(e.getKey(), primitive.getAsString());
                        } else {
                            LogUtil.warn("translation \"" + e.getKey() + "\" in language file " + name + " is not a string, skipping");
                        }
                    }
                } else {
                    LogUtil.warn("language file " + name + " does not have a \"translations\" key with an object value, skipping");
                    continue;
                }
            }

            map.put(name, new LanguageFile(translations));
        }

        return map;
    }
}
