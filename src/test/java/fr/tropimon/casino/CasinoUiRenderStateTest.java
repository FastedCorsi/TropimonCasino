package fr.tropimon.casino;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class CasinoUiRenderStateTest {
    @Test
    void interfaceIsUniformlyReduced() {
        assertEquals(492, CasinoUi.DISPLAY_WIDTH);
        assertEquals(385, CasinoUi.DISPLAY_HEIGHT);
        assertEquals(82, CasinoUi.scale(100));
    }

    @Test
    void guiRenderStacksStayBalanced() throws Exception {
        String source = Files.readString(Path.of("src/main/java/fr/tropimon/casino/CasinoUi.java"));
        assertEquals(occurrences(source, "enableScissor("), occurrences(source, "disableScissor("),
                "Chaque zone de découpe doit être retirée exactement une fois");
        assertEquals(occurrences(source, "getMatrices().push()"), occurrences(source, "getMatrices().pop()"),
                "Chaque matrice empilée doit être retirée exactement une fois");
    }

    private static int occurrences(String text, String token) {
        int count = 0;
        for (int position = 0; (position = text.indexOf(token, position)) >= 0; position += token.length()) count++;
        return count;
    }
}
