package fr.tropimon.casino;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

public final class CasinoApi {
    private static final String SCHEMA = "tropimon_casino";
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(12)).build();
    private final CasinoConfig config;
    private CompletableFuture<Void> authentication;

    CasinoApi(CasinoConfig config) {
        this.config = config;
    }

    public CompletableFuture<CasinoSnapshot> connect(UUID minecraftUuid, String minecraftName) {
        return authenticate()
                .thenCompose(ignored -> rpc("register_profile", object(
                        "p_minecraft_uuid", minecraftUuid.toString(),
                        "p_minecraft_name", minecraftName)))
                .thenCompose(ignored -> bootstrapIfPresent(minecraftUuid, minecraftName))
                .thenCompose(ignored -> snapshot());
    }

    public CompletableFuture<CasinoSnapshot> snapshot() {
        return authenticatedRpc("snapshot", new JsonObject()).thenApply(CasinoApi::parseSnapshot);
    }

    public CompletableFuture<RemoteSpin> spin(int bet, UUID requestId) {
        return authenticatedRpc("spin", object("p_bet", bet, "p_request_id", requestId.toString()))
                .thenApply(CasinoApi::parseSpin);
    }

    public CompletableFuture<RemoteTransaction> requestDeposit(long pokeDollars, String note, UUID requestId) {
        return authenticatedRpc("request_deposit", object("p_poke_dollars", pokeDollars,
                "p_request_id", requestId.toString(), "p_note", note)).thenApply(CasinoApi::parseTransaction);
    }

    public CompletableFuture<RemoteTransaction> requestWithdrawal(long pokeDollars, String note, UUID requestId) {
        return authenticatedRpc("request_withdrawal", object("p_chips", pokeDollars,
                "p_request_id", requestId.toString(), "p_note", note)).thenApply(CasinoApi::parseTransaction);
    }

    public CompletableFuture<AdminSnapshot> adminSnapshot() {
        return authenticatedRpc("admin_snapshot", new JsonObject()).thenApply(CasinoApi::parseAdminSnapshot);
    }

    public CompletableFuture<RemoteTransaction> review(UUID transactionId, boolean approved, String note) {
        return authenticatedRpc("review_transaction", object("p_transaction_id", transactionId.toString(),
                "p_approved", approved, "p_admin_note", note)).thenApply(CasinoApi::parseTransaction);
    }

    public CompletableFuture<RemoteTransaction> markWithdrawalPaid(UUID transactionId) {
        return authenticatedRpc("mark_withdrawal_paid", object("p_transaction_id", transactionId.toString()))
                .thenApply(CasinoApi::parseTransaction);
    }

    public CompletableFuture<RemoteProfile> setAdmin(UUID profileId, boolean enabled) {
        return authenticatedRpc("set_admin", object("p_profile_id", profileId.toString(), "p_enabled", enabled))
                .thenApply(CasinoApi::parseProfile);
    }

    public CompletableFuture<CasinoSettings> updateSettings(CasinoSettings value) {
        return authenticatedRpc("update_settings", object(
                "p_casino_open", value.open(), "p_bank_player_name", value.bankPlayerName(),
                "p_rate", 1, "p_minimum_bet", value.minimumBet(),
                "p_maximum_bet", value.maximumBet(), "p_maximum_payout", value.maximumPayout(),
                "p_minimum_bank_reserve", 0)).thenApply(CasinoApi::parseSettings);
    }

    private CompletableFuture<Void> bootstrapIfPresent(UUID minecraftUuid, String minecraftName) {
        if (!Files.isRegularFile(CasinoConfig.BOOTSTRAP_FILE)) return CompletableFuture.completedFuture(null);
        final String secret;
        try {
            secret = Files.readString(CasinoConfig.BOOTSTRAP_FILE, StandardCharsets.UTF_8).trim();
        } catch (Exception error) {
            return CompletableFuture.failedFuture(new CasinoApiException("BOOTSTRAP_READ_ERROR"));
        }
        if (secret.isBlank()) return CompletableFuture.failedFuture(new CasinoApiException("BOOTSTRAP_EMPTY"));
        return rpc("bootstrap_super_admin", object("p_secret", secret,
                        "p_minecraft_uuid", minecraftUuid.toString(), "p_minecraft_name", minecraftName))
                .thenAccept(ignored -> {
                    try {
                        Files.deleteIfExists(CasinoConfig.BOOTSTRAP_FILE);
                    } catch (Exception ignoredDelete) {
                        // Le secret est déjà inutilisable côté serveur après une activation réussie.
                    }
                });
    }

    private CompletableFuture<JsonElement> authenticatedRpc(String function, JsonObject body) {
        return authenticate().thenCompose(ignored -> rpc(function, body));
    }

    private synchronized CompletableFuture<Void> authenticate() {
        if (!config.loadError.isBlank()) {
            return CompletableFuture.failedFuture(new CasinoApiException(config.loadError));
        }
        if (!config.accessToken.isBlank() && config.accessTokenExpiresAt > Instant.now().getEpochSecond() + 30) {
            return CompletableFuture.completedFuture(null);
        }
        if (authentication != null && !authentication.isDone()) return authentication;
        CompletableFuture<Void> started;
        if (!config.refreshToken.isBlank()) {
            started = authRequest("/auth/v1/token?grant_type=refresh_token", object("refresh_token", config.refreshToken))
                    .thenAccept(this::storeSession);
        } else started = signUp();
        authentication = started;
        started.whenComplete((ignored, error) -> clearAuthentication(started));
        return started;
    }

    private synchronized void clearAuthentication(CompletableFuture<Void> completed) {
        if (authentication == completed) authentication = null;
    }

    private CompletableFuture<Void> signUp() {
        return authRequest("/auth/v1/signup", new JsonObject()).thenAccept(this::storeSession);
    }

    private CompletableFuture<JsonElement> authRequest(String path, JsonObject body) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(config.supabaseUrl + path))
                .timeout(Duration.ofSeconds(20))
                .header("apikey", config.publishableKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8)).build();
        return send(request);
    }

    private CompletableFuture<JsonElement> rpc(String function, JsonObject body) {
        HttpRequest request = HttpRequest.newBuilder(URI.create(config.supabaseUrl + "/rest/v1/rpc/" + function))
                .timeout(Duration.ofSeconds(20))
                .header("apikey", config.publishableKey)
                .header("Authorization", "Bearer " + config.accessToken)
                .header("Content-Type", "application/json")
                .header("Accept-Profile", SCHEMA)
                .header("Content-Profile", SCHEMA)
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8)).build();
        return send(request);
    }

    private CompletableFuture<JsonElement> send(HttpRequest request) {
        return http.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .thenApply(response -> {
                    JsonElement json;
                    try {
                        json = response.body().isBlank() ? new JsonObject() : JsonParser.parseString(response.body());
                    } catch (Exception ignored) {
                        json = new JsonObject();
                    }
                    if (response.statusCode() < 200 || response.statusCode() >= 300) {
                        throw new CompletionException(new CasinoApiException(readError(json, response.statusCode())));
                    }
                    return json;
                });
    }

    private synchronized void storeSession(JsonElement element) {
        JsonObject json = objectValue(element);
        config.accessToken = string(json, "access_token", "");
        config.refreshToken = string(json, "refresh_token", config.refreshToken);
        config.accessTokenExpiresAt = Instant.now().getEpochSecond() + number(json, "expires_in", 3600);
        JsonObject user = json.has("user") && json.get("user").isJsonObject() ? json.getAsJsonObject("user") : null;
        config.userId = user == null ? config.userId : string(user, "id", config.userId);
        if (config.accessToken.isBlank()) throw new CasinoApiException("INCOMPLETE_SESSION");
        config.save();
    }

    private static CasinoSnapshot parseSnapshot(JsonElement element) {
        JsonObject json = objectValue(element);
        RemoteProfile profile = json.has("profile") && json.get("profile").isJsonObject()
                ? parseProfile(json.get("profile")) : null;
        CasinoSettings settings = json.has("settings") ? parseSettings(json.get("settings")) : CasinoSettings.defaults();
        List<RemoteTransaction> transactions = new ArrayList<>();
        array(json, "transactions").forEach(value -> transactions.add(parseTransaction(value)));
        List<RemoteSpin> spins = new ArrayList<>();
        array(json, "spins").forEach(value -> spins.add(parseSpin(value)));
        return new CasinoSnapshot(profile, settings, List.copyOf(transactions), List.copyOf(spins));
    }

    private static AdminSnapshot parseAdminSnapshot(JsonElement element) {
        JsonObject json = objectValue(element);
        List<RemoteProfile> profiles = new ArrayList<>();
        array(json, "profiles").forEach(value -> profiles.add(parseProfile(value)));
        List<RemoteTransaction> pending = new ArrayList<>();
        array(json, "pending").forEach(value -> pending.add(parseTransaction(value)));
        List<RemoteAudit> audit = new ArrayList<>();
        array(json, "audit").forEach(value -> audit.add(parseAudit(value)));
        return new AdminSnapshot(List.copyOf(profiles), List.copyOf(pending), List.copyOf(audit),
                (int) number(json, "overdue", 0));
    }

    private static RemoteProfile parseProfile(JsonElement element) {
        JsonObject json = objectValue(element);
        return new RemoteProfile(uuid(json, "user_id"), uuid(json, "minecraft_uuid"),
                string(json, "minecraft_name", "?"), enumValue(CasinoRole.class, string(json, "role", "player")),
                number(json, "chips", 0));
    }

    private static CasinoSettings parseSettings(JsonElement element) {
        JsonObject json = objectValue(element);
        return new CasinoSettings(bool(json, "casino_open", false), string(json, "bank_player_name", ""),
                (int) number(json, "minimum_bet", 1), (int) number(json, "maximum_bet", 25),
                (int) number(json, "maximum_payout", 100000));
    }

    private static RemoteTransaction parseTransaction(JsonElement element) {
        JsonObject json = objectValue(element);
        return new RemoteTransaction(uuid(json, "id"), uuid(json, "user_id"),
                enumValue(TransactionType.class, string(json, "kind", "deposit")),
                number(json, "poke_dollars", 0), number(json, "chips", 0),
                enumValue(TransactionStatus.class, string(json, "status", "pending")),
                string(json, "player_note", ""), string(json, "admin_note", ""),
                instant(json, "created_at"));
    }

    private static RemoteSpin parseSpin(JsonElement element) {
        JsonObject json = objectValue(element);
        return new RemoteSpin(enumValue(SlotSymbol.class, string(json, "reel_1", "oran")),
                enumValue(SlotSymbol.class, string(json, "reel_2", "oran")),
                enumValue(SlotSymbol.class, string(json, "reel_3", "oran")),
                (int) number(json, "bet", 0), number(json, "payout", 0), number(json, "balance_after", 0));
    }

    private static RemoteAudit parseAudit(JsonElement element) {
        JsonObject json = objectValue(element);
        return new RemoteAudit(uuid(json, "actor_user_id"), string(json, "action", "unknown"),
                string(json, "target_id", ""), instant(json, "created_at"));
    }

    private static String readError(JsonElement element, int status) {
        if (element.isJsonObject()) {
            JsonObject json = element.getAsJsonObject();
            String message = string(json, "message", string(json, "msg", string(json, "error_description", "")));
            if (!message.isBlank()) return message;
        }
        return "HTTP_ERROR:" + status;
    }

    static String friendly(Throwable error) {
        Throwable cause = error;
        while ((cause instanceof CompletionException || cause instanceof java.util.concurrent.ExecutionException)
                && cause.getCause() != null) cause = cause.getCause();
        String raw = cause.getMessage() == null ? cause.getClass().getSimpleName() : cause.getMessage();
        if (raw.toLowerCase(Locale.ROOT).contains("refresh token")) {
            return CasinoText.string("error.tropimon_casino.session_expired");
        }
        if (raw.startsWith("HTTP_ERROR:")) return CasinoText.string("error.tropimon_casino.http", raw.substring(11));
        return switch (raw) {
            case "CASINO_CLOSED" -> CasinoText.string("error.tropimon_casino.closed");
            case "BANK_UNCONFIGURED" -> CasinoText.string("error.tropimon_casino.bank_unconfigured");
            case "BANK_CANNOT_PLAY", "BANK_CANNOT_TRANSACT" -> CasinoText.string("error.tropimon_casino.bank_transaction");
            case "INSUFFICIENT_CHIPS" -> CasinoText.string("error.tropimon_casino.insufficient_balance");
            case "INVALID_BET" -> CasinoText.string("error.tropimon_casino.invalid_bet");
            case "INVALID_DEPOSIT_AMOUNT" -> CasinoText.string("error.tropimon_casino.invalid_deposit");
            case "INVALID_WITHDRAWAL" -> CasinoText.string("error.tropimon_casino.invalid_withdrawal");
            case "INVALID_SETTINGS" -> CasinoText.string("error.tropimon_casino.invalid_settings");
            case "INVALID_ADMIN_TARGET" -> CasinoText.string("error.tropimon_casino.invalid_admin_target");
            case "SELF_REVIEW_FORBIDDEN", "SELF_PAYMENT_FORBIDDEN" -> CasinoText.string("error.tropimon_casino.self_operation");
            case "TRANSACTION_NOT_PENDING", "WITHDRAWAL_NOT_PAYABLE" -> CasinoText.string("error.tropimon_casino.operation_changed");
            case "ADMIN_REQUIRED", "SUPER_ADMIN_REQUIRED" -> CasinoText.string("error.tropimon_casino.permission");
            case "BOOTSTRAP_DENIED" -> CasinoText.string("error.tropimon_casino.bootstrap_denied");
            case "BOOTSTRAP_READ_ERROR" -> CasinoText.string("error.tropimon_casino.bootstrap_read");
            case "BOOTSTRAP_EMPTY" -> CasinoText.string("error.tropimon_casino.bootstrap_empty");
            case "BOOTSTRAP_CLOSED" -> CasinoText.string("error.tropimon_casino.bootstrap_closed");
            case "INCOMPLETE_SESSION" -> CasinoText.string("error.tropimon_casino.incomplete_session");
            case "CONFIG_CORRUPT" -> CasinoText.string("error.tropimon_casino.config_corrupt");
            case "AUTH_REQUIRED", "PROFILE_REQUIRED" -> CasinoText.string("error.tropimon_casino.session_expired");
            case "IDENTITY_LOCKED" -> CasinoText.string("error.tropimon_casino.identity_locked");
            case "INVALID_SERVER_RESPONSE" -> CasinoText.string("error.tropimon_casino.invalid_response");
            default -> CasinoText.string("error.tropimon_casino.technical", raw.replace("PGRST", ""));
        };
    }

    private static JsonObject object(Object... values) {
        JsonObject json = new JsonObject();
        for (int i = 0; i < values.length; i += 2) {
            String key = (String) values[i];
            Object value = values[i + 1];
            if (value instanceof Boolean booleanValue) json.addProperty(key, booleanValue);
            else if (value instanceof Number numberValue) json.addProperty(key, numberValue);
            else json.addProperty(key, String.valueOf(value));
        }
        return json;
    }

    private static JsonObject objectValue(JsonElement element) {
        if (element == null || !element.isJsonObject()) throw new CasinoApiException("INVALID_SERVER_RESPONSE");
        return element.getAsJsonObject();
    }

    private static JsonArray array(JsonObject json, String name) {
        return json.has(name) && json.get(name).isJsonArray() ? json.getAsJsonArray(name) : new JsonArray();
    }

    private static String string(JsonObject json, String name, String fallback) {
        return json.has(name) && !json.get(name).isJsonNull() ? json.get(name).getAsString() : fallback;
    }

    private static long number(JsonObject json, String name, long fallback) {
        try { return json.has(name) && !json.get(name).isJsonNull() ? json.get(name).getAsLong() : fallback; }
        catch (Exception ignored) { return fallback; }
    }

    private static boolean bool(JsonObject json, String name, boolean fallback) {
        try { return json.has(name) && !json.get(name).isJsonNull() ? json.get(name).getAsBoolean() : fallback; }
        catch (Exception ignored) { return fallback; }
    }

    private static UUID uuid(JsonObject json, String name) {
        try { return UUID.fromString(string(json, name, "00000000-0000-0000-0000-000000000000")); }
        catch (Exception ignored) { return new UUID(0, 0); }
    }

    private static Instant instant(JsonObject json, String name) {
        try { return Instant.parse(string(json, name, Instant.EPOCH.toString())); }
        catch (Exception ignored) { return Instant.EPOCH; }
    }

    private static <E extends Enum<E>> E enumValue(Class<E> type, String value) {
        try { return Enum.valueOf(type, value.toUpperCase(Locale.ROOT)); }
        catch (RuntimeException error) { throw new CasinoApiException("INVALID_SERVER_RESPONSE"); }
    }

    public static final class CasinoApiException extends RuntimeException {
        CasinoApiException(String message) { super(message); }
    }
}
