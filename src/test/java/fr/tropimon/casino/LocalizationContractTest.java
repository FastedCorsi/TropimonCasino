package fr.tropimon.casino;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalizationContractTest {
    private static final Pattern KEY = Pattern.compile("[a-z]+\\.tropimon_casino\\.[a-z0-9_.]+");

    @Test void frenchAndEnglishContainEveryKeyUsedByTheClient() throws Exception {
        Set<String> french = keys("fr_fr.json");
        Set<String> english = keys("en_us.json");
        assertEquals(french, english);

        Set<String> used = new HashSet<>();
        try (var files = Files.walk(Path.of("src", "main", "java"))) {
            files.filter(path -> path.toString().endsWith(".java")).forEach(path -> {
                try {
                    Matcher matcher = KEY.matcher(Files.readString(path));
                    while (matcher.find()) used.add(matcher.group());
                } catch (Exception error) {
                    throw new RuntimeException(error);
                }
            });
        }
        assertTrue(french.containsAll(used), () -> "Clés absentes : " + difference(used, french));
    }

    private static Set<String> keys(String file) throws Exception {
        Path path = Path.of("src", "main", "resources", "assets", "tropimon_casino", "lang", file);
        JsonObject json = JsonParser.parseString(Files.readString(path)).getAsJsonObject();
        return json.keySet();
    }

    private static Set<String> difference(Set<String> expected, Set<String> actual) {
        Set<String> result = new HashSet<>(expected);
        result.removeAll(actual);
        return result;
    }
}
