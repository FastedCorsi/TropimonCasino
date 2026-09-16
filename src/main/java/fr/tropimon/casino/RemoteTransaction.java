package fr.tropimon.casino;

import java.time.Instant;
import java.time.Duration;
import java.util.UUID;

public record RemoteTransaction(
        UUID id,
        UUID userId,
        TransactionType type,
        long pokeDollars,
        long chips,
        TransactionStatus status,
        String playerNote,
        String adminNote,
        Instant createdAt
) {
    private boolean awaitingPayment() {
        return type == TransactionType.WITHDRAWAL
                && status != TransactionStatus.PAID && status != TransactionStatus.REJECTED;
    }

    public boolean needsAttention(Instant now) {
        return awaitingPayment() && !now.isBefore(createdAt.plus(Duration.ofHours(24)));
    }

    public boolean overdue(Instant now) {
        return awaitingPayment() && !now.isBefore(createdAt.plus(Duration.ofHours(72)));
    }

    public long hoursUntilDeadline(Instant now) {
        if (!awaitingPayment() || overdue(now)) return 0;
        long seconds = Duration.between(now, createdAt.plus(Duration.ofHours(72))).getSeconds();
        return Math.max(1, (seconds + 3599) / 3600);
    }
}
