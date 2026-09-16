package fr.tropimon.casino;

import net.minecraft.client.resource.language.I18n;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

final class CasinoText {
    private static final Style STYLE = Style.EMPTY.withFont(Identifier.ofVanilla("uniform"));

    private CasinoText() {}

    static MutableText tr(String key, Object... arguments) {
        return Text.translatable(key, arguments).setStyle(STYLE);
    }

    static MutableText literal(Object value) {
        return Text.literal(String.valueOf(value)).setStyle(STYLE);
    }

    static String string(String key, Object... arguments) {
        return I18n.translate(key, arguments);
    }
}
