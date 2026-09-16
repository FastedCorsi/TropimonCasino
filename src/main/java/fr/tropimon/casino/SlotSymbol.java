package fr.tropimon.casino;

import net.minecraft.util.Identifier;
import net.minecraft.text.Text;

public enum SlotSymbol {
    ORAN("symbol.tropimon_casino.oran", 30, 2, 1, Identifier.of("cobblemon", "textures/item/berries/oran_berry.png"), 16, 0, 0, 16),
    POKE_BALL("symbol.tropimon_casino.poke_ball", 25, 3, 1, Identifier.of("cobblemon", "textures/item/poke_balls/poke_ball.png"), 16, 0, 0, 16),
    PSYDUCK("symbol.tropimon_casino.psyduck", 18, 5, 2, local("psyduck.png"), 56, 19, 19, 18),
    SLOWPOKE("symbol.tropimon_casino.slowpoke", 12, 10, 3, local("slowpoke.png"), 56, 19, 19, 18),
    MEOWTH("symbol.tropimon_casino.meowth", 8, 20, 5, local("meowth.png"), 56, 19, 19, 18),
    GENGAR("symbol.tropimon_casino.gengar", 5, 50, 8, local("gengar.png"), 56, 19, 19, 18),
    TROPIMON("symbol.tropimon_casino.tropimon", 2, 150, 15, local("tropimon.png"), 32, 0, 0, 32);

    private final String label;
    private final int weight;
    private final int tripleMultiplier;
    private final int pairMultiplier;
    private final Identifier texture;
    private final int textureSize;
    private final int cropX;
    private final int cropY;
    private final int cropSize;

    SlotSymbol(String label, int weight, int tripleMultiplier, int pairMultiplier, Identifier texture,
               int textureSize, int cropX, int cropY, int cropSize) {
        this.label = label;
        this.weight = weight;
        this.tripleMultiplier = tripleMultiplier;
        this.pairMultiplier = pairMultiplier;
        this.texture = texture;
        this.textureSize = textureSize;
        this.cropX = cropX;
        this.cropY = cropY;
        this.cropSize = cropSize;
    }

    public Text label() { return CasinoText.tr(label); }
    public int weight() { return weight; }
    public int tripleMultiplier() { return tripleMultiplier; }
    public int pairMultiplier() { return pairMultiplier; }
    public Identifier texture() { return texture; }
    public int textureSize() { return textureSize; }
    public int cropX() { return cropX; }
    public int cropY() { return cropY; }
    public int cropSize() { return cropSize; }

    private static Identifier local(String name) {
        return Identifier.of("tropimon_casino", "textures/gui/symbols/" + name);
    }
}
