import java.io.*;
import java.nio.charset.*;
import java.nio.file.*;
import java.util.*;
import java.util.regex.*;
import java.util.zip.*;

/** Standalone build-time checker; never shipped as runtime code. No personal denylist is stored. */
class PrivacyCheck {
    private static final Pattern ACCOUNT_PATH = Pattern.compile(
            "(?i)(?:[a-z]:[\\\\/]+Users[\\\\/]+[^\\\\/\\s\"<>]+|/(?:home|Users)/[^/\\s\"<>]+)");
    private static final Pattern EMAIL = Pattern.compile(
            "[A-Za-z0-9._%+\\-]+@([A-Za-z0-9.\\-]+\\.[A-Za-z]{2,})");
    private static final Pattern SECRET = Pattern.compile(
            "(?:gh[pousr]_[A-Za-z0-9]{30,}|github_pat_[A-Za-z0-9_]{30,}|AKIA[0-9A-Z]{16}"
            + "|eyJ[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}\\.[A-Za-z0-9_-]{10,}"
            + "|-----BEGIN (?:RSA |EC |OPENSSH )?PRIVATE KEY-----"
            + "|(?i:(?:password|api[_-]?key|secret|access[_-]?token)\\s*[:=]\\s*[\"'][A-Za-z0-9_+/=-]{20,}[\"']))");
    private static final Pattern AUTHOR = Pattern.compile(
            "\"authors\"\\s*:\\s*\\[\\s*\"By FastedCorsi\"\\s*]");
    private static final Set<String> LOCAL_DIRS = Set.of("build", ".gradle", ".git", "run",
            "logs", "config", "configs", "saves", "screenshots", "crash-reports", "sessions");
    private static final int LIMIT = 64 * 1024 * 1024;
    private final List<String> privateTerms;
    private final List<String> issues = new ArrayList<>();
    private int inspected;

    PrivacyCheck(List<String> terms) { privateTerms = terms; }

    static List<String> findings(String text, List<String> terms) {
        var result = new ArrayList<String>();
        if (ACCOUNT_PATH.matcher(text).find()) result.add("personal-account-path");
        var emails = EMAIL.matcher(text);
        while (emails.find()) {
            String domain = emails.group(1).toLowerCase(Locale.ROOT);
            if (!(domain.endsWith(".invalid") || domain.endsWith(".test")
                    || Set.of("example.com", "example.org", "example.net", "users.noreply.github.com").contains(domain))) {
                result.add("email-review-required"); break;
            }
        }
        if (SECRET.matcher(text).find()) result.add("possible-secret-review-required");
        for (String term : terms) {
            if (Pattern.compile("(?iu)(?<![\\p{L}\\p{N}_])" + Pattern.quote(term)
                    + "(?![\\p{L}\\p{N}_])").matcher(text).find()) {
                result.add("private-identity-term"); break;
            }
        }
        return result;
    }

    private static boolean forbidden(String path) {
        for (String part : path.replace('\\', '/').split("/")) {
            String lower = part.toLowerCase(Locale.ROOT);
            if (LOCAL_DIRS.contains(lower) || lower.startsWith(".env") || lower.endsWith(".log")
                    || lower.endsWith(".pem") || lower.endsWith(".jks") || lower.endsWith(".p12")) return true;
        }
        return false;
    }

    private String safeLocation(String location) {
        String clean = location;
        for (String term : privateTerms) clean = clean.replaceAll("(?iu)" + Pattern.quote(term), "[redacted]");
        return clean;
    }

    private void issue(String location, String category) {
        issues.add(safeLocation(location) + ": " + category);
    }

    private void bytes(String location, byte[] bytes, int depth) throws IOException {
        inspected++;
        if (forbidden(location)) issue(location, "excluded-private-content");
        var categories = new LinkedHashSet<String>();
        byte[] patternBytes = location.toLowerCase(Locale.ROOT).endsWith(".png") ? pngTextMetadata(bytes) : bytes;
        // Includes class constant pools, text resources, UTF-16 strings and binary metadata.
        for (Charset encoding : List.of(StandardCharsets.UTF_8, StandardCharsets.UTF_16LE, StandardCharsets.UTF_16BE)) {
            categories.addAll(findings(new String(patternBytes, encoding), privateTerms));
        }
        for (String category : categories) issue(location, category);
        String text = new String(bytes, StandardCharsets.UTF_8);
        if (location.endsWith("fabric.mod.json") && text.contains("\"tropimon_casino\"")
                && !AUTHOR.matcher(text).find()) issue(location, "incorrect-public-author");
        if (bytes.length >= 4 && bytes[0] == 'P' && bytes[1] == 'K') {
            if (depth >= 8) { issue(location, "archive-depth-review-required"); return; }
            try (var zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
                ZipEntry entry;
                while ((entry = zip.getNextEntry()) != null) {
                    if (entry.isDirectory()) continue;
                    if (entry.getName().contains("../")) { issue(location, "unsafe-archive-entry"); continue; }
                    byte[] content = zip.readNBytes(LIMIT + 1);
                    if (content.length > LIMIT) { issue(location, "oversized-entry-review-required"); return; }
                    bytes(location + "!/" + entry.getName(), content, depth + 1);
                }
            }
        }
    }

    private static byte[] pngTextMetadata(byte[] png) throws IOException {
        byte[] signature = {(byte) 0x89, 'P', 'N', 'G', 13, 10, 26, 10};
        if (png.length < signature.length || !Arrays.equals(signature, Arrays.copyOf(png, signature.length))) {
            return png;
        }
        var metadata = new ByteArrayOutputStream();
        try (var in = new DataInputStream(new ByteArrayInputStream(png))) {
            in.skipNBytes(signature.length);
            while (in.available() >= 12) {
                int length = in.readInt();
                if (length < 0 || length > in.available() - 8) break;
                byte[] type = in.readNBytes(4);
                byte[] data = in.readNBytes(length);
                in.skipNBytes(4);
                String name = new String(type, StandardCharsets.US_ASCII);
                if (name.equals("tEXt") || name.equals("iTXt")) metadata.write(data);
                else if (name.equals("zTXt")) {
                    int separator = 0;
                    while (separator < data.length && data[separator] != 0) separator++;
                    metadata.write(data, 0, Math.min(separator, data.length));
                    if (separator + 2 < data.length) {
                        try (var compressed = new InflaterInputStream(
                                new ByteArrayInputStream(data, separator + 2, data.length - separator - 2))) {
                            compressed.transferTo(metadata);
                        } catch (ZipException ignored) {
                            // Invalid ancillary metadata is handled by the game decoder; do not scan compressed pixels.
                        }
                    }
                }
            }
        }
        return metadata.toByteArray();
    }

    private void sources(Path root) throws IOException {
        try (var paths = Files.walk(root)) {
            for (Path path : paths.filter(Files::isRegularFile).toList()) {
                Path relative = root.relativize(path);
                // Existing personal/test data remains on disk; never select it for sharing.
                if (forbidden(relative.toString())) continue;
                if (Files.size(path) > LIMIT) { issue(relative.toString(), "oversized-source-review-required"); continue; }
                bytes(relative.toString().replace('\\', '/'), Files.readAllBytes(path), 0);
            }
        }
    }

    private static List<String> localTerms() {
        var terms = new LinkedHashSet<String>();
        String account = System.getProperty("user.name", "");
        if (account.length() >= 4 && !Set.of("root", "user", "admin", "runner", "developer").contains(account.toLowerCase(Locale.ROOT)))
            terms.add(account);
        String supplied = System.getenv("TROPIMON_CASINO_PRIVATE_TERMS");
        if (supplied != null) for (String term : supplied.split("\\R")) if (term.strip().length() >= 3) terms.add(term.strip());
        return List.copyOf(terms);
    }

    private static byte[] archive(String name, byte[] content) throws IOException {
        var out = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry(name)); zip.write(content); zip.closeEntry();
        }
        return out.toByteArray();
    }

    private static void selfTest() throws IOException {
        String fictional = "FictionalMaintainer";
        var terms = List.of(fictional);
        require(findings("credits: " + fictional, terms).contains("private-identity-term"));
        require(findings("C:" + "\\Users\\" + fictional + "\\project", terms).contains("personal-account-path"));
        require(findings("/home/" + fictional + "/project", terms).contains("personal-account-path"));
        require(findings("contact@" + "sample-company.dev", List.of()).contains("email-review-required"));
        require(findings("ghp_" + "Z".repeat(36), List.of()).contains("possible-secret-review-required"));
        require(findings("https://example.org/api By FastedCorsi", terms).isEmpty());
        require(findings("fixture@" + "example.invalid", terms).isEmpty());
        require(forbidden("assets/.env.local") && forbidden("logs/latest.log") && forbidden(".git/HEAD"));
        require(!forbidden("src/main/java/Example.java") && !forbidden(".gitignore"));
        var check = new PrivacyCheck(terms);
        check.bytes("fixture.jar", archive("nested.jar", archive("Example.class",
                ("constant:" + fictional).getBytes(StandardCharsets.UTF_8))), 0);
        require(check.issues.stream().anyMatch(s -> s.contains("private-identity-term")));
        check.issues.clear();
        check.bytes("fixture.bin", fictional.getBytes(StandardCharsets.UTF_16LE), 0);
        require(!check.issues.isEmpty());
        check.issues.clear();
        check.bytes("fixture.jar", archive("config/session.json", "{}".getBytes(StandardCharsets.UTF_8)), 0);
        require(check.issues.stream().anyMatch(s -> s.contains("excluded-private-content")));
        require(AUTHOR.matcher("{\"authors\":[\"By FastedCorsi\"]}").find());
        require(!AUTHOR.matcher("{\"authors\":[\"FictionalMaintainer\"]}").find());
        System.out.println("Privacy checker: synthetic regression tests passed.");
    }

    private static void require(boolean condition) {
        if (!condition) throw new IllegalStateException("Privacy checker synthetic self-test failed.");
    }

    public static void main(String[] args) throws Exception {
        selfTest();
        if (args.length < 2) throw new IllegalArgumentException("Expected project directory and final archives.");
        var check = new PrivacyCheck(localTerms());
        check.sources(Path.of(args[0]));
        for (int i = 1; i < args.length; i++) {
            Path artifact = Path.of(args[i]);
            byte[] bytes = Files.readAllBytes(artifact);
            check.bytes(artifact.getFileName().toString(), bytes, 0);
            // The supplied deliverables must carry the exact public credit in their metadata.
            try (var zip = new ZipFile(artifact.toFile())) {
                boolean metadata = false;
                var entries = zip.entries();
                while (entries.hasMoreElements()) {
                    var entry = entries.nextElement();
                    if (!entry.getName().endsWith("fabric.mod.json")) continue;
                    try (var in = zip.getInputStream(entry)) {
                        String json = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                        if (json.contains("\"tropimon_casino\"") && AUTHOR.matcher(json).find()) metadata = true;
                    }
                }
                if (!metadata) check.issue(artifact.getFileName().toString(), "missing-public-metadata");
            }
        }
        if (!check.issues.isEmpty()) {
            check.issues.forEach(System.err::println);
            throw new IllegalStateException("Privacy review required; matched values are never printed.");
        }
        System.out.println("Privacy check passed: " + check.inspected + " files/archive entries; credit: By FastedCorsi.");
    }
}
