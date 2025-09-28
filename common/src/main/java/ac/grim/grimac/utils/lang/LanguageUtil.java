package ac.grim.grimac.utils.lang;

import ac.grim.grimac.GrimAPI;
import ac.grim.grimac.utils.anticheat.LogUtil;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import lombok.experimental.UtilityClass;

import java.io.File;
import java.io.FileReader;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@UtilityClass
public class LanguageUtil {
    private static final File LANGUAGES_DIRECTORY = new File(GrimAPI.INSTANCE.getGrimPlugin().getDataFolder(), "lang");

    public Map<String, Map<String, String>> getLanguages() {
        File[] files = LANGUAGES_DIRECTORY.listFiles(file -> file.isFile() && file.getName().endsWith(".json"));

        if (files == null) {
            return Collections.emptyMap();
        }

        Map<String, Map<String, String>> map = new HashMap<>();

        for (File file : files) {
            String name = file.getName();
            name = name.substring(0, name.length() - ".json".length());

            Map<String, String> translations = new HashMap<>();

            try (JsonReader reader = new JsonReader(new FileReader(file))) {
                reader.beginObject();
                while (reader.hasNext() && reader.peek() != JsonToken.END_OBJECT) {
                    translations.put(reader.nextName(), reader.nextString());
                }
                reader.endObject();
            } catch (Throwable e) {
                LogUtil.error(e);
                continue;
            }

            map.put(name, translations);
        }

        return map;
    }
}
