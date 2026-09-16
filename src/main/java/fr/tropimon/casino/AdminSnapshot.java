package fr.tropimon.casino;

import java.util.List;

public record AdminSnapshot(
        List<RemoteProfile> profiles,
        List<RemoteTransaction> pending,
        List<RemoteAudit> audit,
        int overdue
) {}
