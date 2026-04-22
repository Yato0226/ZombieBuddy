package me.zed_0xff.zombie_buddy;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Text file protocol between {@link Loader} (game process) and
 * {@link BatchJarApprovalMain} (non-headless child JVM with Swing UI).
 */
public final class JarBatchApprovalProtocol {

    static final String HDR_REQ  = "ZB_BATCH_V6";
    static final String HDR_RESP = "ZB_BATCH_V3_OUT";

    static final String TOK_ALLOW_PERSIST = "ALLOW_PERSIST";
    static final String TOK_ALLOW_SESSION = "ALLOW_SESSION";
    static final String TOK_DENY_PERSIST  = "DENY_PERSIST";
    static final String TOK_DENY_SESSION  = "DENY_SESSION";

    public static final class Entry {
        public final String modKey;
        public final String modId;
        /** Nullable workshop item id for this row. */
        public final JavaModInfo.WorkshopItemID workshopItemId;
        public final String jarAbsolutePath;
        public final String sha256;
        public final String modifiedHuman;
        /** {@code yes} / {@code no} to pre-select that radio; empty = default (No). */
        public final String priorHint;
        /** Display name from mod.info {@code name=}; may be empty (UI falls back to {@link #modId}). */
        public final String modDisplayName;
        /** {@code yes} / {@code no} / {@code unsigned} (missing .zbs while allowed) / empty (legacy). */
        public final String zbsValid;
        /** Author's Steam id from {@code .zbs} when present. */
        public final SteamID64 zbsSteamId;
        /** Non-empty when {@link #zbsValid} is {@code no}. */
        public final String zbsNotice;
        /** {@code yes} / {@code no} / {@code unknown}. */
        public final String steamBanStatus;
        /** Optional explanation (e.g. API error or Steam ban reason). */
        public final String steamBanReason;

        public Entry(
            String modKey,
            String modId,
            JavaModInfo.WorkshopItemID workshopItemId,
            String jarAbsolutePath,
            String sha256,
            String modifiedHuman,
            String priorHint,
            String modDisplayName,
            String zbsValid,
            SteamID64 zbsSteamId,
            String zbsNotice,
            String steamBanStatus,
            String steamBanReason
        ) {
            this.modKey = modKey;
            this.modId = modId;
            this.workshopItemId = workshopItemId;
            this.jarAbsolutePath = jarAbsolutePath != null ? jarAbsolutePath : "";
            this.sha256 = sha256 != null ? sha256 : "";
            this.modifiedHuman = modifiedHuman != null ? modifiedHuman : "";
            this.priorHint = priorHint != null ? priorHint : "";
            this.modDisplayName = modDisplayName != null ? modDisplayName : "";
            this.zbsValid = zbsValid != null ? zbsValid : "";
            this.zbsSteamId = zbsSteamId;
            this.zbsNotice = zbsNotice != null ? zbsNotice : "";
            this.steamBanStatus = steamBanStatus != null ? steamBanStatus : "";
            this.steamBanReason = steamBanReason != null ? steamBanReason : "";
        }
    }

    /** One row in the batch response file: decision key, optional workshop id, JAR hash, token, optional trusted author SteamID64. */
    public static final class OutLine {
        public final String modId;
        public final JavaModInfo.WorkshopItemID workshopItemId;
        public final String sha256;
        public final String token;
        public final String trustedAuthorSteamId;

        public OutLine(String modId, JavaModInfo.WorkshopItemID workshopItemId, String sha256, String token) {
            this(modId, workshopItemId, sha256, token, "");
        }

        public OutLine(
            String modId,
            JavaModInfo.WorkshopItemID workshopItemId,
            String sha256,
            String token,
            String trustedAuthorSteamId
        ) {
            this.modId = modId != null ? modId : "";
            this.workshopItemId = workshopItemId;
            this.sha256 = sha256 != null ? sha256 : "";
            this.token = token != null ? token : "";
            this.trustedAuthorSteamId = trustedAuthorSteamId != null ? trustedAuthorSteamId : "";
        }
    }

    public static void writeRequest(Path path, List<Entry> entries) throws IOException {
        try (BufferedWriter w = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            w.write(HDR_REQ);
            w.newLine();
            w.write(Integer.toString(entries.size()));
            w.newLine();
            for (Entry e : entries) {
                w.write("---");
                w.newLine();
                w.write(escape(e.modKey));
                w.newLine();
                w.write(escape(e.modId));
                w.newLine();
                w.write(escape(e.workshopItemId != null ? Long.toString(e.workshopItemId.value()) : ""));
                w.newLine();
                w.write(escape(e.jarAbsolutePath));
                w.newLine();
                w.write(escape(e.sha256));
                w.newLine();
                w.write(escape(e.modifiedHuman));
                w.newLine();
                w.write(escape(e.priorHint));
                w.newLine();
                w.write(escape(e.modDisplayName));
                w.newLine();
                w.write(escape(e.zbsValid));
                w.newLine();
                w.write(escape(e.zbsSteamId != null ? e.zbsSteamId.value() : ""));
                w.newLine();
                w.write(escape(e.zbsNotice));
                w.newLine();
                w.write(escape(e.steamBanStatus));
                w.newLine();
                w.write(escape(e.steamBanReason));
                w.newLine();
            }
        }
    }

    public static List<Entry> readRequest(Path path) throws IOException {
        try (BufferedReader r = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String hdr = r.readLine();
            if (!HDR_REQ.equals(hdr)) {
                throw new IOException("Bad request header: " + hdr);
            }
            String nLine = r.readLine();
            if (nLine == null) throw new IOException("Missing count line");
            int n = Integer.parseInt(nLine.trim());
            List<Entry> out = new ArrayList<>(Math.max(0, n));
            for (int i = 0; i < n; i++) {
                String sep = r.readLine();
                if (!"---".equals(sep)) {
                    throw new IOException("Expected '---', got: " + sep);
                }
                String modKey = unescape(readMandatory(r, "modKey"));
                String modId = unescape(readMandatory(r, "modId"));
                String workshopItemIdRaw = unescape(readMandatory(r, "workshopItemId"));
                JavaModInfo.WorkshopItemID workshopItemId = workshopItemIdRaw.isEmpty()
                    ? null
                    : new JavaModInfo.WorkshopItemID(Long.parseLong(workshopItemIdRaw));
                String jarPath = unescape(readMandatory(r, "jarPath"));
                String sha = unescape(readMandatory(r, "sha256"));
                String modHuman = unescape(readMandatory(r, "modifiedHuman"));
                String priorHint = unescape(readMandatory(r, "priorHint"));
                String modDisplayName;
                String zbsValid = "";
                SteamID64 zbsSteamId = null;
                String zbsNotice = "";
                String steamBanStatus = "";
                String steamBanReason = "";
                modDisplayName = unescape(readMandatory(r, "modDisplayName"));
                zbsValid = unescape(readMandatory(r, "zbsValid"));
                String zbsSteamIdRaw = unescape(readMandatory(r, "zbsSteamId"));
                zbsSteamId = zbsSteamIdRaw.isEmpty() ? null : new SteamID64(zbsSteamIdRaw);
                zbsNotice = unescape(readMandatory(r, "zbsNotice"));
                steamBanStatus = unescape(readMandatory(r, "steamBanStatus"));
                steamBanReason = unescape(readMandatory(r, "steamBanReason"));
                out.add(new Entry(modKey, modId, workshopItemId, jarPath, sha, modHuman, priorHint, modDisplayName,
                    zbsValid, zbsSteamId, zbsNotice, steamBanStatus, steamBanReason));
            }
            return out;
        }
    }

    private static String readMandatory(BufferedReader r, String what) throws IOException {
        String line = r.readLine();
        if (line == null) throw new IOException("Missing line: " + what);
        return line;
    }

    /**
     * Writes one line per decision:
     * {@code modId + '\t' + workshopItemId + '\t' + sha256 + '\t' + token + ['\t' + trustedAuthorSteamId]} (fields escaped).
     */
    public static void writeResponse(Path path, List<OutLine> lines) throws IOException {
        try (BufferedWriter w = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            w.write(HDR_RESP);
            w.newLine();
            for (OutLine ol : lines) {
                w.write(escape(ol.modId));
                w.write('\t');
                w.write(escape(ol.workshopItemId != null ? Long.toString(ol.workshopItemId.value()) : ""));
                w.write('\t');
                w.write(escape(ol.sha256));
                w.write('\t');
                w.write(escape(ol.token));
                if (ol.trustedAuthorSteamId != null && !ol.trustedAuthorSteamId.isEmpty()) {
                    w.write('\t');
                    w.write(escape(ol.trustedAuthorSteamId));
                }
                w.newLine();
            }
        }
    }

    /**
     * @return one row per pending mod, or null if malformed
     */
    public static List<OutLine> readResponse(Path path) throws IOException {
        try (BufferedReader r = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String hdr = r.readLine();
            if (!HDR_RESP.equals(hdr)) {
                return null;
            }
            List<OutLine> out = new ArrayList<>();
            String line;
            while ((line = r.readLine()) != null) {
                if (line.isEmpty()) continue;
                int t1 = line.indexOf('\t');
                if (t1 <= 0) return null;
                int t2 = line.indexOf('\t', t1 + 1);
                if (t2 <= t1 || t2 >= line.length() - 1) return null;
                int t3 = line.indexOf('\t', t2 + 1);
                if (t3 <= t2 || t3 >= line.length() - 1) return null;
                int t4 = line.indexOf('\t', t3 + 1);
                String modId = unescape(line.substring(0, t1));
                String workshopItemIdRaw = unescape(line.substring(t1 + 1, t2));
                JavaModInfo.WorkshopItemID workshopItemId = workshopItemIdRaw.isEmpty()
                    ? null
                    : new JavaModInfo.WorkshopItemID(Long.parseLong(workshopItemIdRaw));
                String sha = unescape(line.substring(t2 + 1, t3));
                String tok = unescape(t4 < 0 ? line.substring(t3 + 1) : line.substring(t3 + 1, t4));
                if (!isValidToken(tok)) return null;
                String trustedAuthorSteamId = t4 < 0 ? "" : unescape(line.substring(t4 + 1));
                out.add(new OutLine(modId, workshopItemId, sha, tok, trustedAuthorSteamId));
            }
            return out;
        }
    }

    static boolean isValidToken(String tok) {
        return TOK_ALLOW_PERSIST.equals(tok)
            || TOK_ALLOW_SESSION.equals(tok)
            || TOK_DENY_PERSIST.equals(tok)
            || TOK_DENY_SESSION.equals(tok);
    }

    /** Minimal escaping so mod ids / paths with newlines do not break blocks. */
    static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    static String unescape(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char n = s.charAt(++i);
                if (n == 'n') b.append('\n');
                else if (n == 'r') b.append('\r');
                else if (n == 't') b.append('\t');
                else if (n == '\\') b.append('\\');
                else { b.append(c); b.append(n); }
            } else {
                b.append(c);
            }
        }
        return b.toString();
    }

    static String displayToken(String tok) {
        switch (tok) {
            case TOK_DENY_SESSION: return "Deny for this session only";
            case TOK_DENY_PERSIST:  return "Deny permanently (save)";
            case TOK_ALLOW_SESSION: return "Allow for this session only";
            case TOK_ALLOW_PERSIST: return "Allow permanently (save)";
            default:
                return tok;
        }
    }

    /** Friendly suffix for subprocess window title. */
    static String osTag() {
        String os = System.getProperty("os.name", "unknown");
        return "(" + os.toLowerCase(Locale.US) + ")";
    }
}
