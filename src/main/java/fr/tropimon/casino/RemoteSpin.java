package fr.tropimon.casino;

public record RemoteSpin(SlotSymbol first, SlotSymbol second, SlotSymbol third, int bet, long payout, long balanceAfter) {
    SlotSymbol symbol(int reel) {
        return switch (reel) {
            case 0 -> first;
            case 1 -> second;
            case 2 -> third;
            default -> throw new IndexOutOfBoundsException(reel);
        };
    }
}
