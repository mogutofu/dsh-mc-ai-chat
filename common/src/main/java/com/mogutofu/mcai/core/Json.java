package com.mogutofu.mcai.core;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 极小 JSON 读写器（Java 8，零依赖）。
 *
 * <p>MC 各平台的 Gson 版本/可用性不一致，1.12.2 与 Bukkit 侧也不想额外打包依赖，
 * 所以公共逻辑用这份实现。只支持标准 JSON：对象、数组、字符串、数字、布尔、null。
 * 数字统一解析成 {@link Double}，读取配置时用 {@link #intValue} 取整数。</p>
 */
public final class Json {

    private Json() {
    }

    // ── 解析 ──────────────────────────────────────────────────────────

    /** 解析 JSON 文本；格式错误抛 {@link IllegalArgumentException}。 */
    public static Object parse(String text) {
        if (text == null) {
            throw new IllegalArgumentException("null json");
        }
        Parser parser = new Parser(text);
        Object value = parser.readValue();
        parser.skipWhitespace();
        if (!parser.atEnd()) {
            throw new IllegalArgumentException("trailing data at " + parser.pos);
        }
        return value;
    }

    /** 对象取子对象，类型不对返回空 Map。 */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> object(Object value) {
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        }
        return new LinkedHashMap<>();
    }

    /** 读取配置整数；缺失/类型不对用 fallback。 */
    public static int intValue(Map<String, Object> object, String key, int fallback) {
        Object value = object.get(key);
        if (value instanceof Number) {
            return (int) Math.round(((Number) value).doubleValue());
        }
        if (value instanceof String) {
            try {
                return (int) Math.round(Double.parseDouble((String) value));
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    /** 读取配置布尔；缺失/类型不对用 fallback。 */
    public static boolean boolValue(Map<String, Object> object, String key, boolean fallback) {
        Object value = object.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof String) {
            return Boolean.parseBoolean((String) value);
        }
        return fallback;
    }

    /** 读取字符串；缺失/类型不对返回 fallback。 */
    public static String stringValue(Map<String, Object> object, String key, String fallback) {
        Object value = object.get(key);
        return value instanceof String ? (String) value : fallback;
    }

    // ── 输出 ──────────────────────────────────────────────────────────

    /** 把 Map/List/String/Number/Boolean/null 写成 JSON。 */
    public static String write(Object value) {
        StringBuilder out = new StringBuilder(256);
        writeValue(out, value);
        return out.toString();
    }

    private static void writeValue(StringBuilder out, Object value) {
        if (value == null) {
            out.append("null");
        } else if (value instanceof String) {
            escape(out, (String) value);
        } else if (value instanceof Boolean) {
            out.append(value.toString());
        } else if (value instanceof Number) {
            Number number = (Number) value;
            double d = number.doubleValue();
            if (d == Math.rint(d) && !Double.isInfinite(d)) {
                out.append(number.longValue());
            } else {
                out.append(number.toString());
            }
        } else if (value instanceof Map) {
            out.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : ((Map<?, ?>) value).entrySet()) {
                if (!first) {
                    out.append(',');
                }
                first = false;
                escape(out, String.valueOf(entry.getKey()));
                out.append(':');
                writeValue(out, entry.getValue());
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
                writeValue(out, item);
            }
            out.append(']');
        } else {
            escape(out, value.toString());
        }
    }

    /** 输出一个 JSON 字符串字面量（含引号）。 */
    public static String quote(String text) {
        StringBuilder out = new StringBuilder(text.length() + 2);
        escape(out, text);
        return out.toString();
    }

    private static void escape(StringBuilder out, String text) {
        out.append('"');
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            switch (c) {
                case '"':
                    out.append("\\\"");
                    break;
                case '\\':
                    out.append("\\\\");
                    break;
                case '\n':
                    out.append("\\n");
                    break;
                case '\r':
                    out.append("\\r");
                    break;
                case '\t':
                    out.append("\\t");
                    break;
                case '\b':
                    out.append("\\b");
                    break;
                case '\f':
                    out.append("\\f");
                    break;
                default:
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
            }
        }
        out.append('"');
    }

    // ── 递归下降解析 ──────────────────────────────────────────────────

    private static final class Parser {
        private final String text;
        private int pos;

        Parser(String text) {
            this.text = text;
        }

        boolean atEnd() {
            return pos >= text.length();
        }

        void skipWhitespace() {
            while (pos < text.length()) {
                char c = text.charAt(pos);
                if (c == ' ' || c == '\n' || c == '\r' || c == '\t' || c == '\uFEFF') {
                    pos++;
                } else {
                    break;
                }
            }
        }

        Object readValue() {
            skipWhitespace();
            if (atEnd()) {
                throw error("unexpected end");
            }
            char c = text.charAt(pos);
            switch (c) {
                case '{':
                    return readObject();
                case '[':
                    return readArray();
                case '"':
                    return readString();
                case 't':
                    expect("true");
                    return Boolean.TRUE;
                case 'f':
                    expect("false");
                    return Boolean.FALSE;
                case 'n':
                    expect("null");
                    return null;
                default:
                    return readNumber();
            }
        }

        Map<String, Object> readObject() {
            Map<String, Object> map = new LinkedHashMap<>();
            pos++; // {
            skipWhitespace();
            if (peek() == '}') {
                pos++;
                return map;
            }
            while (true) {
                skipWhitespace();
                if (peek() != '"') {
                    throw error("expected object key");
                }
                String key = readString();
                skipWhitespace();
                expect(":");
                Object value = readValue();
                map.put(key, value);
                skipWhitespace();
                char c = next();
                if (c == '}') {
                    return map;
                }
                if (c != ',') {
                    throw error("expected , or }");
                }
            }
        }

        List<Object> readArray() {
            List<Object> list = new ArrayList<>();
            pos++; // [
            skipWhitespace();
            if (peek() == ']') {
                pos++;
                return list;
            }
            while (true) {
                list.add(readValue());
                skipWhitespace();
                char c = next();
                if (c == ']') {
                    return list;
                }
                if (c != ',') {
                    throw error("expected , or ]");
                }
            }
        }

        String readString() {
            pos++; // opening quote
            StringBuilder out = new StringBuilder();
            while (pos < text.length()) {
                char c = text.charAt(pos++);
                if (c == '"') {
                    return out.toString();
                }
                if (c != '\\') {
                    out.append(c);
                    continue;
                }
                if (atEnd()) {
                    throw error("bad escape");
                }
                char e = text.charAt(pos++);
                switch (e) {
                    case '"':
                        out.append('"');
                        break;
                    case '\\':
                        out.append('\\');
                        break;
                    case '/':
                        out.append('/');
                        break;
                    case 'b':
                        out.append('\b');
                        break;
                    case 'f':
                        out.append('\f');
                        break;
                    case 'n':
                        out.append('\n');
                        break;
                    case 'r':
                        out.append('\r');
                        break;
                    case 't':
                        out.append('\t');
                        break;
                    case 'u':
                        if (pos + 4 > text.length()) {
                            throw error("bad unicode escape");
                        }
                        out.append((char) Integer.parseInt(text.substring(pos, pos + 4), 16));
                        pos += 4;
                        break;
                    default:
                        throw error("bad escape \\" + e);
                }
            }
            throw error("unterminated string");
        }

        Number readNumber() {
            int start = pos;
            while (pos < text.length()) {
                char c = text.charAt(pos);
                if ((c >= '0' && c <= '9') || c == '-' || c == '+' || c == '.' || c == 'e' || c == 'E') {
                    pos++;
                } else {
                    break;
                }
            }
            if (start == pos) {
                throw error("unexpected char " + text.charAt(pos));
            }
            return Double.parseDouble(text.substring(start, pos));
        }

        void expect(String token) {
            if (!text.startsWith(token, pos)) {
                throw error("expected " + token);
            }
            pos += token.length();
        }

        char peek() {
            if (atEnd()) {
                throw error("unexpected end");
            }
            return text.charAt(pos);
        }

        char next() {
            if (atEnd()) {
                throw error("unexpected end");
            }
            return text.charAt(pos++);
        }

        IllegalArgumentException error(String message) {
            return new IllegalArgumentException(message + " at " + pos);
        }
    }
}
