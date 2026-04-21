package me.zed_0xff.zombie_buddy;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * In-memory allow/deny decisions for Java mod JARs: {@code modId -> (sha256 -> yes|no)}.
 * Mirrors the nested {@code jarDecisions} object in {@link JavaModApprovalsStore}'s JSON file.
 */
public final class JarDecisionTable {

    private final Map<String, Map<String, String>> byModId = new HashMap<>();

    public String get(String modId, String sha256) {
        if (modId == null || sha256 == null) return null;
        Map<String, String> inner = byModId.get(modId);
        return inner == null ? null : inner.get(sha256);
    }

    public void put(String modId, String sha256, String decision) {
        if (modId == null || sha256 == null || decision == null) return;
        byModId.computeIfAbsent(modId, k -> new HashMap<>()).put(sha256, decision);
    }

    /** All mod ids that have at least one hash decision. */
    public Set<String> modIds() {
        return new HashSet<>(byModId.keySet());
    }

    /** Defensive copy of hash → decision for one mod (empty map if none). */
    public Map<String, String> hashesOf(String modId) {
        if (modId == null) return Collections.emptyMap();
        Map<String, String> inner = byModId.get(modId);
        return inner == null ? Collections.emptyMap() : new HashMap<>(inner);
    }

    public boolean isEmpty() {
        return byModId.isEmpty();
    }

    /** Total stored decisions (one per mod id + SHA-256 pair). */
    public int decisionCount() {
        int n = 0;
        for (Map<String, String> inner : byModId.values()) {
            n += inner.size();
        }
        return n;
    }

    public JarDecisionTable copy() {
        JarDecisionTable c = new JarDecisionTable();
        for (Map.Entry<String, Map<String, String>> e : byModId.entrySet()) {
            c.byModId.put(e.getKey(), new HashMap<>(e.getValue()));
        }
        return c;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof JarDecisionTable that)) return false;
        return byModId.equals(that.byModId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(byModId);
    }
}
