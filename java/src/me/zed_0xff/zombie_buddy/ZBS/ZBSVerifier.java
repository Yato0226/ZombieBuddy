package me.zed_0xff.zombie_buddy;

import org.bouncycastle.crypto.params.Ed25519PublicKeyParameters;
import org.bouncycastle.crypto.signers.Ed25519Signer;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Verifies {@code .jar.zbs} sidecars signed with Ed25519 (see ZModUnbork Gradle {@code signJarZBS}).
 * The sidecar has three lines ({@code signature} is 128 hex chars); the Ed25519 public key (64 hex) is read from the author's Steam profile
 * page as {@code JavaModZBS:...} (same text authors add to their profile summary).
 */
public final class ZBSVerifier {

    private static final Pattern STEAM_ID = Pattern.compile("^[a-zA-Z0-9_]{3,}$");
    /** SteamID64 profile URLs use {@code /profiles/}; vanity ids use {@code /id/}. */
    private static final Pattern NUMERIC_STEAM_PROFILE_ID = Pattern.compile("^[0-9]{17,}$");
    private static final Pattern LINE_STEAM_ID = Pattern.compile("^steam_id:([a-zA-Z0-9_]{3,})$");
    /** Ed25519 signature: 64 bytes as 128 hex characters. */
    private static final Pattern LINE_SIGNATURE = Pattern.compile("^signature:([0-9a-fA-F]{128})$");
    /** Pubkey may appear anywhere in the profile HTML (summary, etc.). */
    private static final Pattern JAVA_MOD_ZBS_IN_HTML = Pattern.compile("JavaModZBS:([0-9a-fA-F]{64})");

    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();

    private ZBSVerifier() {}

    /**
     * @param jarSha256Hex lowercase hex SHA-256 of the JAR (same as Loader uses)
     */
    public static Result verify(File jarFile, File zbsFile, String jarSha256Hex) {
        if (zbsFile == null || !zbsFile.isFile()) {
            return new Result(false, "", "Missing .zbs file next to JAR: " + (zbsFile != null ? zbsFile.getName() : ""));
        }
        String steamId;
        byte[] sig;
        try {
            ParsedZBS p = parseZBS(zbsFile);
            steamId = p.steamId;
            sig = p.signature;
        } catch (IOException e) {
            return new Result(false, "", "Could not read .zbs: " + e.getMessage());
        }
        if (!STEAM_ID.matcher(steamId).matches()) {
            return new Result(false, steamId, "Invalid steam_id in .zbs file.");
        }
        List<String> pubHexes;
        try {
            pubHexes = fetchJavaModZBSHexesFromSteam(steamId);
        } catch (Exception e) {
            return new Result(false, steamId, e.getMessage());
        }
        if (pubHexes.isEmpty()) {
            return new Result(
                false,
                steamId,
                "Could not find JavaModZBS:<64 hex> on Steam profile — add it to your profile summary."
            );
        }
        try {
            String canonical = "ZBS:" + steamId + ":" + jarSha256Hex.toLowerCase(Locale.ROOT);
            byte[] msg = canonical.getBytes(StandardCharsets.UTF_8);
            for (String pubHex : pubHexes) {
                byte[] pubRaw;
                try {
                    pubRaw = hexToBytes(pubHex);
                } catch (Exception e) {
                    return new Result(false, steamId, "Invalid JavaModZBS hex on Steam profile.");
                }
                if (pubRaw.length != 32) {
                    return new Result(false, steamId, "JavaModZBS on Steam profile must be 64 hex chars (32-byte Ed25519 public key).");
                }
                Ed25519PublicKeyParameters pub = new Ed25519PublicKeyParameters(pubRaw, 0);
                Ed25519Signer signer = new Ed25519Signer();
                signer.init(false, pub);
                signer.update(msg, 0, msg.length);
                boolean ok = signer.verifySignature(sig);
                if (ok) {
                    return new Result(true, steamId, "");
                }
            }
            return new Result(false, steamId, "Invalid signature — JAR may have been tampered with.");
        } catch (Exception e) {
            return new Result(false, steamId, e.getMessage());
        }
    }

    /** Steam Community profile URL for {@code steam_id} from {@code .zbs} (vanity or numeric). */
    public static String steamCommunityProfileUrl(String steamId) {
        if (steamId == null || steamId.isEmpty()) {
            return "https://steamcommunity.com/";
        }
        if (NUMERIC_STEAM_PROFILE_ID.matcher(steamId).matches()) {
            return "https://steamcommunity.com/profiles/" + steamId + "/";
        }
        return "https://steamcommunity.com/id/" + steamId + "/";
    }

    private static List<String> fetchJavaModZBSHexesFromSteam(String steamId) throws IOException {
        String url = steamCommunityProfileUrl(steamId);
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(url))
            .timeout(Duration.ofSeconds(25))
            .header(
                "User-Agent",
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36 ZombieBuddy"
            )
            .GET()
            .build();
        HttpResponse<String> resp;
        try {
            resp = HTTP.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while fetching Steam profile.", e);
        }
        int code = resp.statusCode();
        if (code != 200) {
            throw new IOException("Could not load Steam profile (HTTP " + code + ").");
        }
        String body = resp.body();
        List<String> keys = new ArrayList<>();
        Matcher m = JAVA_MOD_ZBS_IN_HTML.matcher(body);
        while (m.find()) {
            keys.add(m.group(1));
        }
        return keys;
    }

    private static final class ParsedZBS {
        final String steamId;
        final byte[] signature;

        ParsedZBS(String steamId, byte[] signature) {
            this.steamId = steamId;
            this.signature = signature;
        }
    }

    private static ParsedZBS parseZBS(File zbsFile) throws IOException {
        try (BufferedReader r = Files.newBufferedReader(zbsFile.toPath(), StandardCharsets.UTF_8)) {
            String l1 = r.readLine();
            String l2 = r.readLine();
            String l3 = r.readLine();
            if (l1 == null || l2 == null || l3 == null) {
                throw new IOException("Expected at least 3 lines (ZBS, steam_id, signature)");
            }
            l1 = l1.trim();
            l2 = l2.trim();
            l3 = l3.trim();
            if (!"ZBS".equals(l1)) {
                throw new IOException("First line must be ZBS");
            }
            Matcher m2 = LINE_STEAM_ID.matcher(l2);
            if (!m2.matches()) {
                throw new IOException("Second line must be steam_id:<id>");
            }
            Matcher m3 = LINE_SIGNATURE.matcher(l3);
            if (!m3.matches()) {
                throw new IOException("Third line must be signature:<128 hex> (Ed25519)");
            }
            String steamId = m2.group(1);
            byte[] sig;
            try {
                sig = hexToBytes(m3.group(1));
            } catch (IllegalArgumentException e) {
                throw new IOException("Invalid signature hex: " + e.getMessage());
            }
            return new ParsedZBS(steamId, sig);
        }
    }

    private static byte[] hexToBytes(String hex) {
        String h = hex.trim().toLowerCase(Locale.ROOT);
        if ((h.length() & 1) != 0) {
            throw new IllegalArgumentException("odd hex length");
        }
        int n = h.length() / 2;
        byte[] out = new byte[n];
        for (int i = 0; i < n; i++) {
            int hi = Character.digit(h.charAt(i * 2), 16);
            int lo = Character.digit(h.charAt(i * 2 + 1), 16);
            if (hi < 0 || lo < 0) {
                throw new IllegalArgumentException("non-hex");
            }
            out[i] = (byte) ((hi << 4) | lo);
        }
        return out;
    }

    public static final class Result {
        public final boolean valid;
        /** Steam custom URL id from .zbs (for profile link); may be set when invalid for display. */
        public final String steamIdFromZBS;
        public final String invalidReason;

        public Result(boolean valid, String steamIdFromZBS, String invalidReason) {
            this.valid = valid;
            this.steamIdFromZBS = steamIdFromZBS != null ? steamIdFromZBS : "";
            this.invalidReason = invalidReason != null ? invalidReason : "";
        }

        public boolean valid() {
            return valid;
        }

        public String steamIdFromZBS() {
            return steamIdFromZBS;
        }

        public String invalidReason() {
            return invalidReason;
        }
    }
}
