package fr.tropimon.casino;

public final class SlotOdds {
    private SlotOdds() {}

    public static double theoreticalReturn() {
        return theoreticalReturn(1, Long.MAX_VALUE);
    }

    public static double theoreticalReturn(int bet, long maximumPayout) {
        if (bet <= 0 || maximumPayout < 0) throw new IllegalArgumentException("Mise ou gain maximal invalide");
        int totalWeight = java.util.Arrays.stream(SlotSymbol.values()).mapToInt(SlotSymbol::weight).sum();
        double result = 0.0;
        for (SlotSymbol symbol : SlotSymbol.values()) {
            double probability = symbol.weight() / (double) totalWeight;
            double tripleReturn = Math.min((long) bet * symbol.tripleMultiplier(), maximumPayout) / (double) bet;
            double pairReturn = Math.min((long) bet * symbol.pairMultiplier(), maximumPayout) / (double) bet;
            result += probability * probability * probability * tripleReturn;
            result += 3.0 * probability * probability * (1.0 - probability) * pairReturn;
        }
        return result;
    }
}
