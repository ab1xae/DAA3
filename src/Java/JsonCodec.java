import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal JSON reader/writer for the JSON Lines protocol.
 *
 * Reading produces LinkedHashMap, ArrayList, String, Long (or Double for a
 * number with a fraction/exponent), Boolean and null. Writing accepts those
 * types plus int[], long[] and object arrays (for example Long[] or int[][]).
 */
final class JsonCodec {
    private final String text;
    private int pos;

    private JsonCodec(String text) {
        this.text = text;
    }

    static Object parse(String text) {
        JsonCodec reader = new JsonCodec(text);
        Object value = reader.value();
        reader.skipSpace();
        if (reader.pos != text.length()) {
            throw reader.error("unexpected trailing characters");
        }
        return value;
    }

    // ------------------------------------------------------------------ reading

    private Object value() {
        skipSpace();
        if (pos >= text.length()) {
            throw error("unexpected end of input");
        }
        char c = text.charAt(pos);
        switch (c) {
            case '{':
                return object();
            case '[':
                return array();
            case '"':
                return string();
            case 't':
                return literal("true", Boolean.TRUE);
            case 'f':
                return literal("false", Boolean.FALSE);
            case 'n':
                return literal("null", null);
            default:
                if (c == '-' || (c >= '0' && c <= '9')) {
                    return number();
                }
                throw error("unexpected character '" + c + "'");
        }
    }

    private Map<String, Object> object() {
        pos++; // '{'
        Map<String, Object> result = new LinkedHashMap<>();
        skipSpace();
        if (peek() == '}') {
            pos++;
            return result;
        }
        while (true) {
            skipSpace();
            if (peek() != '"') {
                throw error("expected a string key");
            }
            String key = string();
            skipSpace();
            expect(':');
            result.put(key, value());
            skipSpace();
            char c = next();
            if (c == '}') {
                return result;
            }
            if (c != ',') {
                throw error("expected ',' or '}'");
            }
        }
    }

    private List<Object> array() {
        pos++; // '['
        List<Object> result = new ArrayList<>();
        skipSpace();
        if (peek() == ']') {
            pos++;
            return result;
        }
        while (true) {
            result.add(value());
            skipSpace();
            char c = next();
            if (c == ']') {
                return result;
            }
            if (c != ',') {
                throw error("expected ',' or ']'");
            }
        }
    }

    private String string() {
        pos++; // opening quote
        StringBuilder out = new StringBuilder();
        while (true) {
            char c = next();
            if (c == '"') {
                return out.toString();
            }
            if (c != '\\') {
                out.append(c);
                continue;
            }
            char e = next();
            switch (e) {
                case 'n': out.append('\n'); break;
                case 't': out.append('\t'); break;
                case 'r': out.append('\r'); break;
                case 'b': out.append('\b'); break;
                case 'f': out.append('\f'); break;
                case 'u':
                    if (pos + 4 > text.length()) {
                        throw error("bad unicode escape");
                    }
                    out.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
                    pos += 4;
                    break;
                default: out.append(e); // '"', '\\' and '/'
            }
        }
    }

    private Object number() {
        int start = pos;
        if (peek() == '-') {
            pos++;
        }
        boolean integral = true;
        while (pos < text.length()) {
            char c = text.charAt(pos);
            if (c >= '0' && c <= '9') {
                pos++;
            } else if (c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-') {
                integral = false;
                pos++;
            } else {
                break;
            }
        }
        String token = text.substring(start, pos);
        return integral ? (Object) Long.parseLong(token) : (Object) Double.parseDouble(token);
    }

    private Object literal(String word, Object result) {
        if (!text.startsWith(word, pos)) {
            throw error("unknown literal");
        }
        pos += word.length();
        return result;
    }

    private void skipSpace() {
        while (pos < text.length()) {
            char c = text.charAt(pos);
            if (c != ' ' && c != '\t' && c != '\n' && c != '\r') {
                return;
            }
            pos++;
        }
    }

    private char peek() {
        if (pos >= text.length()) {
            throw error("unexpected end of input");
        }
        return text.charAt(pos);
    }

    private char next() {
        char c = peek();
        pos++;
        return c;
    }

    private void expect(char c) {
        if (next() != c) {
            throw error("expected '" + c + "'");
        }
    }

    private IllegalArgumentException error(String message) {
        return new IllegalArgumentException("JSON " + message + " at position " + pos);
    }

    // ------------------------------------------------------------------ writing

    static String write(Object value) {
        StringBuilder out = new StringBuilder();
        write(value, out);
        return out.toString();
    }

    private static void write(Object value, StringBuilder out) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof String) {
            writeString((String) value, out);
        } else if (value instanceof Number || value instanceof Boolean) {
            out.append(value);
        } else if (value instanceof Map) {
            out.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                writeString(String.valueOf(entry.getKey()), out);
                out.append(':');
                write(entry.getValue(), out);
            }
            out.append('}');
        } else if (value instanceof Iterable) {
            out.append('[');
            boolean first = true;
            for (Object item : (Iterable<?>) value) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                write(item, out);
            }
            out.append(']');
        } else if (value instanceof int[]) {
            int[] items = (int[]) value;
            out.append('[');
            for (int i = 0; i < items.length; i++) {
                if (i > 0) {
                    out.append(',');
                }
                out.append(items[i]);
            }
            out.append(']');
        } else if (value instanceof long[]) {
            long[] items = (long[]) value;
            out.append('[');
            for (int i = 0; i < items.length; i++) {
                if (i > 0) {
                    out.append(',');
                }
                out.append(items[i]);
            }
            out.append(']');
        } else if (value instanceof Object[]) {
            Object[] items = (Object[]) value;
            out.append('[');
            for (int i = 0; i < items.length; i++) {
                if (i > 0) {
                    out.append(',');
                }
                write(items[i], out);
            }
            out.append(']');
        } else {
            throw new IllegalArgumentException("cannot write " + value.getClass().getName());
        }
    }

    private static void writeString(String s, StringBuilder out) {
        out.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' || c == '\\') {
                out.append('\\').append(c);
            } else if (c == '\n') {
                out.append("\\n");
            } else if (c == '\t') {
                out.append("\\t");
            } else if (c == '\r') {
                out.append("\\r");
            } else if (c < 0x20) {
                out.append(String.format("\\u%04x", (int) c));
            } else {
                out.append(c);
            }
        }
        out.append('"');
    }
}
