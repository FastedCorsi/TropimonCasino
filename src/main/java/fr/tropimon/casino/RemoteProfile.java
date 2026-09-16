package fr.tropimon.casino;

import java.util.UUID;

public record RemoteProfile(UUID userId, UUID minecraftUuid, String minecraftName, CasinoRole role, long chips) {
    public boolean admin() { return role == CasinoRole.ADMIN || role == CasinoRole.SUPER_ADMIN; }
    public boolean bank() { return role == CasinoRole.SUPER_ADMIN; }
}
