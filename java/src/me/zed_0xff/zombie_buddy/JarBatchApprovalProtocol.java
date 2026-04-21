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

    /** Legacy; {@link #readRequest} still accepts this. */
    static final String HDR_REQ_V3 = "ZB_BATCH_V3";
    static final String HDR_REQ_V4 = "ZB_BATCH_V4";
    static final String HDR_REQ  = "ZB_BATCH_V5";
    static final String HDR_RESP = "ZB_BATCH_V2_OUT";

    static final String TOK_ALLOW_PERSIST = "ALLOW_PERSIST";
    static final String TOK_ALLOW_SESSION = "ALLOW_SESSION";
    static final String TOK_DENY_PERSIST  = "DENY_PERSIST";
    static final String TOK_DENY_SESSION  = "DENY_SESSION";

    public static final class Entry {
        public final String modKey;
        public final String modId;
        public final String jarAbsolutePath;
        public final String sha256;
        public final String modifiedHuman;
        /** {@code yes} / {@code no} to pre-select that radio; empty = default (No). */
        public final String priorHint;
        /** Display name from mod.info {@code name=}; may be empty (UI falls back to {@link #modId}). */
        public final String modDisplayName;
        /** From mod.info {@code author=}. */
        public final String author;
        /** {@code yes} / {@code no} / empty (legacy request without ZBS fields). */
        public final String zbsValid;
        /** Steam custom URL id from {@code .zbs} when present. */
        public final String zbsSteamId;
        /** Non-empty when {@link #zbsValid} is {@code no}. */
        public final String zbsNotice;

        public Entry(
            String modKey,
            String modId,
            String jarAbsolutePath,
            String sha256,
            String modifiedHuman,
            String priorHint,
            String modDisplayName,
            String author,
            String zbsValid,
            String zbsSteamId,
            String zbsNotice
        ) {
            this.modKey = modKey;
            this.modId = modId;
            this.jarAbsolutePath = jarAbsolutePath != null ? jarAbsolutePath : "";
            this.sha256 = sha256 != null ? sha256 : "";
            this.modifiedHuman = modifiedHuman != null ? modifiedHuman : "";
            this.priorHint = priorHint != null ? priorHint : "";
            this.modDisplayName = modDisplayName != null ? modDisplayName : "";
            this.author = author != null ? author : "";
            this.zbsValid = zbsValid != null ? zbsValid : "";
            this.zbsSteamId = zbsSteamId != null ? zbsSteamId : "";
            this.zbsNotice = zbsNotice != null ? zbsNotice : "";
        }
    }

    /** One row in the batch response file: mod id, JAR hash, and UI token. */
    public static final class OutLine {
        public final String modId;
        public final String sha256;
        public final String token;

        public OutLine(String modId, String sha256, String token) {
            this.modId = modId != null ? modId : "";
            this.sha256 = sha256 != null ? sha256 : "";
            this.token = token != null ? token : "";
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
                w.write(escape(e.author));
                w.newLine();
                w.write(escape(e.zbsValid));
                w.newLine();
                w.write(escape(e.zbsSteamId));
                w.newLine();
                w.write(escape(e.zbsNotice));
                w.newLine();
            }
        }
    }

    public static List<Entry> readRequest(Path path) throws IOException {
        try (BufferedReader r = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            String hdr = r.readLine();
            boolean v3 = HDR_REQ_V3.equals(hdr);
            boolean v4 = HDR_REQ_V4.equals(hdr);
            boolean v5 = HDR_REQ.equals(hdr);
            if (!v3 && !v4 && !v5) {
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
                String jarPath = unescape(readMandatory(r, "jarPath"));
                String sha = unescape(readMandatory(r, "sha256"));
                String modHuman = unescape(readMandatory(r, "modifiedHuman"));
                String priorHint = unescape(readMandatory(r, "priorHint"));
                String modDisplayName;
                String author;
                String zbsValid = "";
                String zbsSteamId = "";
                String zbsNotice = "";
                if (v5) {
                    modDisplayName = unescape(readMandatory(r, "modDisplayName"));
                    author = unescape(readMandatory(r, "author"));
                    zbsValid = unescape(readMandatory(r, "zbsValid"));
                    zbsSteamId = unescape(readMandatory(r, "zbsSteamId"));
                    zbsNotice = unescape(readMandatory(r, "zbsNotice"));
                } else if (v4) {
                    modDisplayName = unescape(readMandatory(r, "modDisplayName"));
                    author = unescape(readMandatory(r, "author"));
                } else {
                    modDisplayName = "";
                    author = "";
                }
                out.add(new Entry(modKey, modId, jarPath, sha, modHuman, priorHint, modDisplayName, author,
                    zbsValid, zbsSteamId, zbsNotice));
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
     * Writes one line per decision: {@code modId + '\t' + sha256 + '\t' + token} (fields escaped).
     */
    public static void writeResponse(Path path, List<OutLine> lines) throws IOException {
        try (BufferedWriter w = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            w.write(HDR_RESP);
            w.newLine();
            for (OutLine ol : lines) {
                w.write(escape(ol.modId));
                w.write('\t');
                w.write(escape(ol.sha256));
                w.write('\t');
                w.write(escape(ol.token));
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
                String modId = unescape(line.substring(0, t1));
                String sha = unescape(line.substring(t1 + 1, t2));
                String tok = unescape(line.substring(t2 + 1));
                if (!isValidToken(tok)) return null;
                out.add(new OutLine(modId, sha, tok));
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
