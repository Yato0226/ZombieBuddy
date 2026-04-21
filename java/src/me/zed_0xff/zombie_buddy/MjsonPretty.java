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
        write(root, sb, 0);
        sb.append('\n');
        return sb.toString();
    }

    private static void indent(StringBuilder sb, int depth) {
        for (int i = 0, n = depth * SPACES_PER_LEVEL; i < n; i++) {
            sb.append(' ');
        }
    }

    private static void write(Json j, StringBuilder sb, int depth) {
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
            sb.append("[\n");
            for (int i = 0; i < list.size(); i++) {
                indent(sb, depth + 1);
                write(list.get(i), sb, depth + 1);
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
            sb.append("{\n");
            Iterator<Map.Entry<String, Json>> it = map.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<String, Json> e = it.next();
                indent(sb, depth + 1);
                appendQuoted(sb, e.getKey());
                sb.append(": ");
                write(e.getValue(), sb, depth + 1);
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
