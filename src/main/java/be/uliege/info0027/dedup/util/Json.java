package be.uliege.info0027.dedup.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal JSON parser and serializer using only the JDK.
 * <p>
 * Supports the subset needed by this module: objects, arrays, strings,
 * numbers, booleans, and null. Numbers are parsed as {@link Long} (integral)
 * or {@link Double} (decimal); strings handle the standard escapes. This
 * is deliberately small (~150 lines) so the project has zero external
 * runtime dependencies and ships as a thin JAR with no shading required.
 * <p>
 * The API exposes only what {@code FrontendGateImpl} needs:
 * {@link #parseObject(String)} and the {@link Writer} fluent serializer.
 *
 * @see #parseObject(String)
 */
public final class Json {

    private Json() {}

    /** Thrown for any malformed input. */
    public static final class JsonException extends RuntimeException {
        public JsonException(String msg) { super(msg); }
    }

    /* ============================== Parsing ============================== */

    /**
     * Parses a JSON object literal. The result is a {@code Map<String,Object>}
     * where values are {@code String}, {@code Long}/{@code Double},
     * {@code Boolean}, {@code null}, nested {@code Map<String,Object>}, or
     * {@code List<Object>}.
     *
     * @throws JsonException if the input is null, blank, or malformed
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String input) {
        if (input == null) throw new JsonException("null input");
        Parser p = new Parser(input);
        p.skipWs();
        Object v = p.parseValue();
        p.skipWs();
        if (p.hasMore()) throw new JsonException("trailing content");
        if (!(v instanceof Map)) throw new JsonException("not a JSON object");
        return (Map<String, Object>) v;
    }

    private static final class Parser {
        final String s;
        int i;
        Parser(String s) { this.s = s; }

        boolean hasMore() { return i < s.length(); }
        void skipWs() { while (i < s.length() && Character.isWhitespace(s.charAt(i))) i++; }
        char peek() { return i < s.length() ? s.charAt(i) : '\0'; }

        void expect(char c) {
            if (i >= s.length() || s.charAt(i) != c) {
                throw new JsonException("expected '" + c + "' at " + i);
            }
            i++;
        }

        Object parseValue() {
            skipWs();
            if (i >= s.length()) throw new JsonException("unexpected end");
            char c = s.charAt(i);
            return switch (c) {
                case '{' -> parseObj();
                case '[' -> parseArr();
                case '"' -> parseStr();
                case 't', 'f' -> parseBool();
                case 'n' -> parseNull();
                default -> parseNum();
            };
        }

        Map<String, Object> parseObj() {
            expect('{');
            Map<String, Object> m = new LinkedHashMap<>();
            skipWs();
            if (peek() == '}') { i++; return m; }
            while (true) {
                skipWs();
                String key = parseStr();
                skipWs();
                expect(':');
                Object val = parseValue();
                m.put(key, val);
                skipWs();
                if (peek() == ',') { i++; continue; }
                expect('}');
                return m;
            }
        }

        List<Object> parseArr() {
            expect('[');
            List<Object> list = new ArrayList<>();
            skipWs();
            if (peek() == ']') { i++; return list; }
            while (true) {
                list.add(parseValue());
                skipWs();
                if (peek() == ',') { i++; continue; }
                expect(']');
                return list;
            }
        }

        String parseStr() {
            expect('"');
            StringBuilder sb = new StringBuilder();
            while (i < s.length() && s.charAt(i) != '"') {
                char c = s.charAt(i++);
                if (c == '\\' && i < s.length()) {
                    char esc = s.charAt(i++);
                    switch (esc) {
                        case '"', '\\', '/' -> sb.append(esc);
                        case 'n' -> sb.append('\n');
                        case 't' -> sb.append('\t');
                        case 'r' -> sb.append('\r');
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'u' -> {
                            if (i + 4 > s.length()) throw new JsonException("bad \\u escape");
                            sb.append((char) Integer.parseInt(s.substring(i, i + 4), 16));
                            i += 4;
                        }
                        default -> throw new JsonException("bad escape \\" + esc);
                    }
                } else {
                    sb.append(c);
                }
            }
            expect('"');
            return sb.toString();
        }

        Boolean parseBool() {
            if (s.startsWith("true", i)) { i += 4; return Boolean.TRUE; }
            if (s.startsWith("false", i)) { i += 5; return Boolean.FALSE; }
            throw new JsonException("bad boolean at " + i);
        }

        Object parseNull() {
            if (s.startsWith("null", i)) { i += 4; return null; }
            throw new JsonException("bad null at " + i);
        }

        Number parseNum() {
            int start = i;
            if (peek() == '-') i++;
            while (i < s.length() && (Character.isDigit(s.charAt(i)) || "+-.eE".indexOf(s.charAt(i)) >= 0)) i++;
            String num = s.substring(start, i);
            if (num.isEmpty()) throw new JsonException("expected number at " + start);
            try {
                if (num.contains(".") || num.contains("e") || num.contains("E")) return Double.parseDouble(num);
                return Long.parseLong(num);
            } catch (NumberFormatException e) {
                throw new JsonException("bad number '" + num + "'");
            }
        }
    }

    /* ============================ Serializing ============================ */

    /**
     * Fluent JSON object serializer. Keys are written in insertion order.
     * Use {@link #beginArray(String)} / {@link #endArray()} for nested arrays.
     */
    public static final class Writer {
        private final StringBuilder sb = new StringBuilder();
        private boolean firstInScope = true;

        public Writer() { sb.append('{'); }

        /** Adds a string field. */
        public Writer put(String key, String value) {
            comma();
            quote(key); sb.append(':'); quote(value);
            return this;
        }

        /** Begins a nested array field; values are appended via {@link #addString}. */
        public Writer beginArray(String key) {
            comma();
            quote(key); sb.append(":[");
            firstInScope = true;
            return this;
        }

        /** Begins a nested array element inside the current array (for arrays of arrays). */
        public Writer beginNestedArray() {
            comma();
            sb.append('[');
            firstInScope = true;
            return this;
        }

        public Writer endArray() {
            sb.append(']');
            firstInScope = false;
            return this;
        }

        /** Adds a string element to the currently-open array. */
        public Writer addString(String value) {
            comma();
            quote(value);
            return this;
        }

        @Override
        public String toString() {
            return sb.toString() + '}';
        }

        private void comma() {
            if (!firstInScope) sb.append(',');
            firstInScope = false;
        }

        private void quote(String s) {
            sb.append('"');
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                switch (c) {
                    case '"' -> sb.append("\\\"");
                    case '\\' -> sb.append("\\\\");
                    case '\n' -> sb.append("\\n");
                    case '\r' -> sb.append("\\r");
                    case '\t' -> sb.append("\\t");
                    case '\b' -> sb.append("\\b");
                    case '\f' -> sb.append("\\f");
                    default -> {
                        if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                        else sb.append(c);
                    }
                }
            }
            sb.append('"');
        }
    }

    /** Serializes a list of groups (each group is a list of strings) as a JSON array of arrays. */
    public static String groupsToJsonArray(List<List<String>> groups) {
        StringBuilder sb = new StringBuilder("[");
        for (int g = 0; g < groups.size(); g++) {
            if (g > 0) sb.append(',');
            sb.append('[');
            List<String> group = groups.get(g);
            for (int j = 0; j < group.size(); j++) {
                if (j > 0) sb.append(',');
                sb.append('"').append(escape(group.get(j))).append('"');
            }
            sb.append(']');
        }
        return sb.append(']').toString();
    }

    /** Serializes a single list of strings as a JSON array literal. */
    public static String stringListToJsonArray(List<String> items) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) sb.append(',');
            sb.append('"').append(escape(items.get(i))).append('"');
        }
        return sb.append(']').toString();
    }

    private static String escape(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                default -> {
                    if (c < 0x20) sb.append(String.format("\\u%04x", (int) c));
                    else sb.append(c);
                }
            }
        }
        return sb.toString();
    }
}
