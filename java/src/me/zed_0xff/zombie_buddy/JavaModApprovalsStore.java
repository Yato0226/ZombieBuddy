package me.zed_0xff.zombie_buddy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import mjson.Json;

/**
 * Persistent Java-mod JAR allow/deny decisions under {@code ~/.zombie_buddy/}.
 * <p>
 * JSON shape (extensible root; only {@code jarDecisions} is written by this class):
 * <pre>
 * {
 *   "formatVersion": 1,
 *   "jarDecisions": {
 *     "ModId": {
 *       "sha256hex": true | false
 *     }
 *   }
 * }
 * </pre>
 * Values are JSON booleans: {@code true} = allow, {@code false} = deny. String {@code yes}/{@code no} is not read.
 */
public final class JavaModApprovalsStore {

    /** Current JSON file. */
    public static final String JSON_FILE_NAME = "java_mod_approvals.json";

    /**
     * Obsolete line-oriented file from older ZombieBuddy builds. If still present on disk,
     * it is deleted at startup without importing its contents.
     */
    public static final String LEGACY_TXT_FILE_NAME = "java_mod_approvals.txt";

    private static final int FORMAT_VERSION = 1;

    private JavaModApprovalsStore() {}

    static Path directory() {
        return Path.of(System.getProperty("user.home"), ".zombie_buddy");
    }

    static Path jsonPath() {
        return directory().resolve(JSON_FILE_NAME);
    }

    static Path legacyTxtPath() {
        return directory().resolve(LEGACY_TXT_FILE_NAME);
    }

    static JarDecisionTable load() {
        Path jp = jsonPath();
        Path leg = legacyTxtPath();
        JarDecisionTable table = new JarDecisionTable();
        try {
            if (Files.exists(jp)) {
                readJsonInto(jp, table);
                Logger.info("Java mod approvals JSON read from " + jp + ": " + table.decisionCount() + " decision(s)");
            }
            if (Files.exists(leg)) {
                Files.delete(leg);
                Logger.info("Deleted legacy Java mod approvals file " + leg.getFileName()
                    + " without importing (use " + jp.getFileName() + " only)");
            }
            return table;
        } catch (Exception e) {
            Logger.error("Could not load Java mod approvals: " + e);
        }
        return table;
    }

    /**
     * Writes {@code jarDecisions}. If a file already exists, reads it first and merges,
     * replacing only {@code jarDecisions}; other top-level keys are kept when possible.
     */
    static void save(JarDecisionTable table) {
        try {
            Path jp = jsonPath();
            if (jp.getParent() != null) {
                Files.createDirectories(jp.getParent());
            }
            Json root;
            if (Files.exists(jp)) {
                try {
                    String existing = Files.readString(jp, StandardCharsets.UTF_8).trim();
                    root = existing.isEmpty() ? Json.object() : Json.read(existing);
                    if (!root.isObject()) {
                        root = Json.object();
                    }
                } catch (Exception e) {
                    Logger.warn("Approvals JSON unreadable; rewriting: " + e);
                    root = Json.object();
                }
            } else {
                root = Json.object();
            }
            if (!root.has("formatVersion")) {
                root.set("formatVersion", FORMAT_VERSION);
            }
            root.set("jarDecisions", jarDecisionsToJson(table));
            Files.writeString(jp, MjsonPretty.format(root), StandardCharsets.UTF_8);
            int written = table == null ? 0 : table.decisionCount();
            Logger.info("Java mod approvals JSON written to " + jp + ": " + written + " decision(s)");
        } catch (Exception e) {
            Logger.error("Could not save Java mod approvals: " + e);
        }
    }

    static Json jarDecisionsToJson(JarDecisionTable table) {
        Json jarDecisions = Json.object();
        if (table == null) return jarDecisions;
        for (String modId : table.modIds()) {
            Json hashes = Json.object();
            for (Map.Entry<String, String> e : table.hashesOf(modId).entrySet()) {
                String v = e.getValue();
                if (Loader.DECISION_YES.equals(v) || Loader.DECISION_NO.equals(v)) {
                    hashes.set(e.getKey(), Loader.DECISION_YES.equals(v));
                }
            }
            jarDecisions.set(modId, hashes);
        }
        return jarDecisions;
    }

    private static void readJsonInto(Path jp, JarDecisionTable into) throws Exception {
        String raw = Files.readString(jp, StandardCharsets.UTF_8);
        String text = raw == null ? "" : raw.trim();
        if (text.isEmpty()) {
            return;
        }
        Json root = Json.read(text);
        if (!root.isObject()) {
            Logger.warn("Java mod approvals file is not a JSON object; ignoring nested decisions");
            return;
        }
        Json jarDecisions = root.at("jarDecisions");
        if (jarDecisions == null || !jarDecisions.isObject()) {
            return;
        }
        for (Map.Entry<String, Json> modEntry : jarDecisions.asJsonMap().entrySet()) {
            String modId = modEntry.getKey();
            Json inner = modEntry.getValue();
            if (inner == null || !inner.isObject()) continue;
            for (Map.Entry<String, Json> he : inner.asJsonMap().entrySet()) {
                Json jv = he.getValue();
                if (jv == null || !jv.isBoolean()) continue;
                into.put(modId, he.getKey(), jv.asBoolean() ? Loader.DECISION_YES : Loader.DECISION_NO);
            }
        }
    }
}
