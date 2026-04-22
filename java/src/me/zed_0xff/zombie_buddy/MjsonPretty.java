package me.zed_0xff.zombie_buddy;

import java.util.Iterator;
import java.util.List;
import java.util.Map;

import mjson.Json;

/**
 * Indented JSON text for {@link mjson.Json} (mjson's {@link Json#toString(int)} is max length, not pretty-print).
 */
final class MjsonPretty {

    private static final int SPACES_PER_LEVEL = 2;

    private MjsonPretty() {}

    static String format(Json root) {
        StringBuilder sb = new StringBuilder();
        write(root, sb, 0, null);
        sb.append('\n');
        return sb.toString();
    }

    private static void indent(StringBuilder sb, int depth) {
        for (int i = 0, n = depth * SPACES_PER_LEVEL; i < n; i++) {
            sb.append(' ');
        }
    }

    private static void write(Json j, StringBuilder sb, int depth, String fieldName) {
        if (j == null || j.isNull()) {
            sb.append("null");
            return;
        }
        if (j.isBoolean()) {
            sb.append(j.asBoolean());
            return;
        }
        if (j.isNumber()) {
            Object v = j.getValue();
            sb.append(v != null ? v.toString() : "0");
            return;
        }
        if (j.isString()) {
            appendQuoted(sb, j.asString());
            return;
        }
        if (j.isArray()) {
            List<Json> list = j.asJsonList();
            if (list.isEmpty()) {
                sb.append("[]");
                return;
            }
            if ("mod_ids".equals(fieldName)) {
                writeCompactArray(j, sb);
                return;
            }
            sb.append("[\n");
            for (int i = 0; i < list.size(); i++) {
                indent(sb, depth + 1);
                write(list.get(i), sb, depth + 1, null);
                if (i < list.size() - 1) {
                    sb.append(',');
                }
                sb.append('\n');
            }
            indent(sb, depth);
            sb.append(']');
            return;
        }
        if (j.isObject()) {
            Map<String, Json> map = j.asJsonMap();
            if (map.isEmpty()) {
                sb.append("{}");
                return;
            }
            if ("author".equals(fieldName)) {
                writeCompactObject(j, sb);
                return;
            }
            sb.append("{\n");
            Iterator<Map.Entry<String, Json>> it = map.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, Json> e = it.next();
                indent(sb, depth + 1);
                appendQuoted(sb, e.getKey());
                sb.append(": ");
                write(e.getValue(), sb, depth + 1, e.getKey());
                if (it.hasNext()) {
                    sb.append(',');
                }
                sb.append('\n');
            }
            indent(sb, depth);
            sb.append('}');
            return;
        }
        sb.append("null");
    }

    private static void writeCompactArray(Json j, StringBuilder sb) {
        List<Json> list = j.asJsonList();
        sb.append('[');
        for (int i = 0; i < list.size(); i++) {
            Json item = list.get(i);
            if (item == null || item.isNull()) {
                sb.append("null");
            } else if (item.isString()) {
                appendQuoted(sb, item.asString());
            } else if (item.isBoolean()) {
                sb.append(item.asBoolean());
            } else if (item.isNumber()) {
                Object v = item.getValue();
                sb.append(v != null ? v.toString() : "0");
            } else {
                // Fallback for unexpected nested structures.
                write(item, sb, 0, null);
            }
            if (i < list.size() - 1) {
                sb.append(", ");
            }
        }
        sb.append(']');
    }

    private static void writeCompactObject(Json j, StringBuilder sb) {
        Map<String, Json> map = j.asJsonMap();
        sb.append('{');
        Iterator<Map.Entry<String, Json>> it = map.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Json> e = it.next();
            appendQuoted(sb, e.getKey());
            sb.append(": ");
            Json v = e.getValue();
            if (v == null || v.isNull()) {
                sb.append("null");
            } else if (v.isString()) {
                appendQuoted(sb, v.asString());
            } else if (v.isBoolean()) {
                sb.append(v.asBoolean());
            } else if (v.isNumber()) {
                Object raw = v.getValue();
                sb.append(raw != null ? raw.toString() : "0");
            } else {
                // Fallback for unexpected nested structures.
                write(v, sb, 0, null);
            }
            if (it.hasNext()) {
                sb.append(", ");
            }
        }
        sb.append('}');
    }

    private static void appendQuoted(StringBuilder sb, String s) {
        if (s == null) {
            sb.append("null");
            return;
        }
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }
}
