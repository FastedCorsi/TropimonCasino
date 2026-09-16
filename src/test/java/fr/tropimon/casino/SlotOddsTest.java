package fr.tropimon.casino;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class SlotOddsTest {
    @Test void sevenSymbolsKeepARealHouseEdge() {
        assertEquals(7, SlotSymbol.values().length);
        assertEquals(100, java.util.Arrays.stream(SlotSymbol.values()).mapToInt(SlotSymbol::weight).sum());
        assertEquals(0.931046, SlotOdds.theoreticalReturn(), 0.000001);
        assertEquals(150, SlotSymbol.TROPIMON.tripleMultiplier());
    }

    @Test void displayedReturnIncludesTheConfiguredPayoutCap() {
        double uncapped = SlotOdds.theoreticalReturn();
        double cappedAtLargeBet = SlotOdds.theoreticalReturn(500, 20_000);
        assertTrue(cappedAtLargeBet < uncapped);
        assertEquals(uncapped, SlotOdds.theoreticalReturn(20, 20_000), 0.000001);
        assertThrows(IllegalArgumentException.class, () -> SlotOdds.theoreticalReturn(0, 20_000));
    }

    @Test void withdrawalBecomesOverdueAtSeventyTwoHours() {
        Instant created = Instant.parse("2030-01-01T12:00:00Z");
        RemoteTransaction transaction = new RemoteTransaction(UUID.randomUUID(), UUID.randomUUID(),
                TransactionType.WITHDRAWAL, 2_000, 100, TransactionStatus.APPROVED, "", "", created);
        assertFalse(transaction.overdue(created.plusSeconds(71 * 3600)));
        assertTrue(transaction.overdue(created.plusSeconds(72 * 3600)));
        assertFalse(transaction.needsAttention(created.plusSeconds(23 * 3600)));
        assertTrue(transaction.needsAttention(created.plusSeconds(24 * 3600)));
        assertEquals(1, transaction.hoursUntilDeadline(created.plusSeconds(71 * 3600 + 1)));
    }

    @Test void casinoSettingsRejectOverflowAndInvalidPlayerNames() {
        assertTrue(new CasinoSettings(true, "CasinoBank", 20, 500, 20_000).valid());
        assertFalse(new CasinoSettings(true, "invalid name", 20, 500, 20_000).valid());
        assertFalse(new CasinoSettings(true, "CasinoBank", 500, 20, 20_000).valid());
    }
}
