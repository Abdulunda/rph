package com.mycompany.routeph1;

import java.util.*;

/** Tiny JSON helper (no external libraries needed). */
public class Json {

    /** Parses a flat JSON object like {"a":"x","b":5} into a String map. */
    public static Map<String, String> parse(String s) {
        Map<String, String> m = new HashMap<>();
        if (s == null) return m;
        int i = s.indexOf('{');
        if (i < 0) return m;
        i++;
        int n = s.length();
        while (i < n) {
            while (i < n && (Character.isWhitespace(s.charAt(i)) || s.charAt(i) == ',')) i++;
            if (i >= n || s.charAt(i) == '}') break;
            if (s.charAt(i) != '"') break;
            StringBuilder k = new StringBuilder();
            i = readString(s, i, k);
            while (i < n && (Character.isWhitespace(s.charAt(i)) || s.charAt(i) == ':')) i++;
            StringBuilder v = new StringBuilder();
            if (i < n && s.charAt(i) == '"') {
                i = readString(s, i, v);
            } else {
                while (i < n && s.charAt(i) != ',' && s.charAt(i) != '}') v.append(s.charAt(i++));
            }
            m.put(k.toString(), v.toString().trim());
        }
        return m;
    }

    private static int readString(String s, int i, StringBuilder out) {
        i++; // opening quote
        while (i < s.length()) {
            char c = s.charAt(i++);
            if (c == '"') break;
            if (c == '\\' && i < s.length()) {
                char e = s.charAt(i++);
                switch (e) {
                    case 'n': out.append('\n'); break;
                    case 't': out.append('\t'); break;
                    case 'r': out.append('\r'); break;
                    case 'u':
                        out.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                        i += 4;
                        break;
                    default: out.append(e);
                }
            } else {
                out.append(c);
            }
        }
        return i;
    }

    /** Serializes Map / List / String / Number / Boolean / null to JSON. */
    public static String write(Object o) {
        StringBuilder sb = new StringBuilder();
        write(o, sb);
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static void write(Object o, StringBuilder sb) {
        if (o == null) {
            sb.append("null");
        } else if (o instanceof Map) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<String, Object> e : ((Map<String, Object>) o).entrySet()) {
                if (!first) sb.append(',');
                first = false;
                quote(e.getKey(), sb);
                sb.append(':');
                write(e.getValue(), sb);
            }
            sb.append('}');
        } else if (o instanceof Collection) {
            sb.append('[');
            boolean first = true;
            for (Object x : (Collection<Object>) o) {
                if (!first) sb.append(',');
                first = false;
                write(x, sb);
            }
            sb.append(']');
        } else if (o instanceof Number || o instanceof Boolean) {
            sb.append(o);
        } else if (o instanceof java.sql.Timestamp || o instanceof java.time.temporal.TemporalAccessor) {
            String t = o.toString().replace('T', ' ');
            int dot = t.indexOf('.');
            quote(dot > 0 ? t.substring(0, dot) : t, sb);
        } else {
            quote(o.toString(), sb);
        }
    }

    private static void quote(String s, StringBuilder sb) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
            }
        }
        sb.append('"');
    }

    public static Map<String, Object> obj(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) m.put((String) kv[i], kv[i + 1]);
        return m;
    }
}
