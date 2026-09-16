package fr.tropimon.casino;

import java.time.Instant;
import java.util.UUID;

public record RemoteAudit(UUID actorUserId, String action, String targetId, Instant createdAt) {}
