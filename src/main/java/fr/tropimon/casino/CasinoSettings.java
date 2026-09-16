package fr.tropimon.casino;

public record CasinoSettings(
        boolean open,
        String bankPlayerName,
        int minimumBet,
        int maximumBet,
        int maximumPayout
) {
    static CasinoSettings defaults() { return new CasinoSettings(false, "", 1, 25, 100000); }

    boolean valid() {
        return bankPlayerName != null && bankPlayerName.matches("[A-Za-z0-9_]{0,16}")
                && minimumBet > 0 && maximumBet >= minimumBet && maximumPayout > 0;
    }
}
