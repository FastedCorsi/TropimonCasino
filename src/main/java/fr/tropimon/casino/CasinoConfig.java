package fr.tropimon.casino;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

final class CasinoConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final Path FILE = FabricLoader.getInstance().getConfigDir().resolve("tropimon-casino.json");
    private static final Path BACKUP_FILE = FabricLoader.getInstance().getConfigDir().resolve("tropimon-casino.session-backup.json");
    static final Path BOOTSTRAP_FILE = FabricLoader.getInstance().getConfigDir().resolve("tropimon-casino-bootstrap.secret");

    String supabaseUrl = "https://rphxgukdnkrxixyzwgwn.supabase.co";
    String publishableKey = "sb_publishable_MQEaysmdqyuxVx85aL4PQg_BjHIwuGP";
    String accessToken = "";
    String refreshToken = "";
    String userId = "";
    long accessTokenExpiresAt;
    int keyBindingVersion;
    transient String loadError = "";

    static CasinoConfig load() {
        if (!Files.isRegularFile(FILE)) {
            CasinoConfig value = new CasinoConfig();
            value.save();
            return value;
        }
        try {
            CasinoConfig value = read(FILE);
            value.save();
            return value;
        } catch (Exception primaryError) {
            try {
                CasinoConfig recovered = read(BACKUP_FILE);
                recovered.save();
                return recovered;
            } catch (Exception backupError) {
                CasinoConfig value = new CasinoConfig();
                value.loadError = "CONFIG_CORRUPT";
                return value;
            }
        }
    }

    private static CasinoConfig read(Path path) throws Exception {
        CasinoConfig value = GSON.fromJson(Files.readString(path), CasinoConfig.class);
        if (value == null || value.supabaseUrl == null || value.publishableKey == null
                || value.accessToken == null || value.refreshToken == null || value.userId == null) {
            throw new IllegalArgumentException("Configuration incomplète");
        }
        URI endpoint = URI.create(value.supabaseUrl);
        if (!"https".equalsIgnoreCase(endpoint.getScheme()) || endpoint.getHost() == null
                || value.publishableKey.isBlank()) {
            throw new IllegalArgumentException("Configuration distante invalide");
        }
        return value;
    }

    synchronized void save() {
        Path temporary = FILE.resolveSibling(FILE.getFileName() + ".tmp");
        try {
            Files.createDirectories(FILE.getParent());
            Files.writeString(temporary, GSON.toJson(this));
            try {
                Files.move(temporary, FILE, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception atomicMoveUnavailable) {
                Files.move(temporary, FILE, StandardCopyOption.REPLACE_EXISTING);
            }
            Files.copy(FILE, BACKUP_FILE, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception ignored) {
            try { Files.deleteIfExists(temporary); } catch (Exception ignoredDelete) {}
            // La session reste utilisable en mémoire ; aucun fichier existant n'est tronqué.
        }
    }
}
