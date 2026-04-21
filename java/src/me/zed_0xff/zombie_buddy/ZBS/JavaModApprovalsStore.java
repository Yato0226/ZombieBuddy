package me.zed_0xff.zombie_buddy;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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
    private static final String KEY_FORMAT_VERSION = "formatVersion";
    private static final String KEY_JAR_DECISIONS = "jarDecisions";
    private static final String KEY_TRUSTED_AUTHORS = "trustedAuthors";

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

    static final class Snapshot {
        private final JarDecisionTable jarDecisions;
        private final Set<String> trustedAuthors;

        Snapshot(JarDecisionTable jarDecisions, Set<String> trustedAuthors) {
            this.jarDecisions = jarDecisions;
            this.trustedAuthors = trustedAuthors;
        }

        JarDecisionTable jarDecisions() {
            return jarDecisions;
        }

        Set<String> trustedAuthors() {
            return trustedAuthors;
        }
    }

    static Snapshot loadSnapshot() {
        Path jp = jsonPath();
        Path leg = legacyTxtPath();
        JarDecisionTable table = new JarDecisionTable();
        Set<String> trustedAuthors = new HashSet<>();
        try {
            if (Files.exists(jp)) {
                readJsonInto(jp, table, trustedAuthors);
                Logger.info("Java mod approvals JSON read from " + jp + ": " + table.decisionCount()
                    + " decision(s), " + trustedAuthors.size() + " trusted author(s)");
            }
            if (Files.exists(leg)) {
                Files.delete(leg);
                Logger.info("Deleted legacy Java mod approvals file " + leg.getFileName()
                    + " without importing (use " + jp.getFileName() + " only)");
            }
            return new Snapshot(table, trustedAuthors);
        } catch (Exception e) {
            Logger.error("Could not load Java mod approvals: " + e);
        }
        return new Snapshot(table, trustedAuthors);
    }

    static JarDecisionTable load() {
        return loadSnapshot().jarDecisions();
    }

    static Set<String> loadTrustedAuthors() {
        return new HashSet<>(loadSnapshot().trustedAuthors());
    }

    /**
     * Writes {@code jarDecisions}. If a file already exists, reads it first and merges,
     * replacing only {@code jarDecisions}; other top-level keys are kept when possible.
     */
    static void save(JarDecisionTable table) {
        save(table, loadTrustedAuthors());
    }

    static void save(JarDecisionTable table, Set<String> trustedAuthors) {
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
            if (!root.has(KEY_FORMAT_VERSION)) {
                root.set(KEY_FORMAT_VERSION, FORMAT_VERSION);
            }
            root.set(KEY_JAR_DECISIONS, jarDecisionsToJson(table));
            root.set(KEY_TRUSTED_AUTHORS, trustedAuthorsToJson(trustedAuthors));
            Files.writeString(jp, MjsonPretty.format(root), StandardCharsets.UTF_8);
            int written = table == null ? 0 : table.decisionCount();
            int trustedCount = trustedAuthors == null ? 0 : trustedAuthors.size();
            Logger.info("Java mod approvals JSON written to " + jp + ": " + written
                + " decision(s), " + trustedCount + " trusted author(s)");
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

    static Json trustedAuthorsToJson(Set<String> trustedAuthors) {
        List<Json> values = new ArrayList<>();
        if (trustedAuthors != null) {
            for (String steamId : trustedAuthors) {
                if (steamId != null && !steamId.isEmpty()) {
                    values.add(Json.make(steamId));
                }
            }
        }
        return Json.array(values.toArray(new Object[0]));
    }

    private static void readJsonInto(Path jp, JarDecisionTable into, Set<String> trustedAuthors) throws Exception {
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
        Json jarDecisions = root.at(KEY_JAR_DECISIONS);
        if (jarDecisions == null || !jarDecisions.isObject()) {
            readTrustedAuthors(root, trustedAuthors);
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
        readTrustedAuthors(root, trustedAuthors);
    }

    private static void readTrustedAuthors(Json root, Set<String> trustedAuthors) {
        if (trustedAuthors == null) {
            return;
        }
        Json arr = root.at(KEY_TRUSTED_AUTHORS);
        if (arr == null || !arr.isArray()) {
            return;
        }
        for (Json item : arr.asJsonList()) {
            if (item == null || !item.isString()) {
                continue;
            }
            String steamId = item.asString();
            if (steamId != null && !steamId.isEmpty()) {
                trustedAuthors.add(steamId);
            }
        }
    }
}
