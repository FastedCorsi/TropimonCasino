package fr.tropimon.casino;

import java.util.List;

public record CasinoSnapshot(
        RemoteProfile profile,
        CasinoSettings settings,
        List<RemoteTransaction> transactions,
        List<RemoteSpin> spins
) {
    static CasinoSnapshot empty() { return new CasinoSnapshot(null, CasinoSettings.defaults(), List.of(), List.of()); }
}
