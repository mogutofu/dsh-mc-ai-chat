package com.mogutofu.mcai.bridge;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * mcai:bridge 附件桥接协议（mcai-bridge/1）。
 *
 * <p>Bukkit 插件 ↔ 客户端模组之间的一条 <b>raw</b> plugin message 通道，只负责握手与
 * 元数据；真正的截图/文件字节走可配置的 HTTP 上传端口，不进 Minecraft 的 32KB 自定义包。
 * 大端序；字符串 = int 长度 + UTF-8；UUID = 两个 long；帧 = "MCAI" + u8 版本 + u8 类型 + payload。</p>
 *
 * <table>
 *   <caption>帧类型</caption>
 *   <tr><th>type</th><th>方向</th><th>内容</th></tr>
 *   <tr><td>1 HELLO</td><td>C→S</td><td>UTF loader、UTF clientVersion</td></tr>
 *   <tr><td>2 INFO</td><td>S→C</td><td>int port、UTF advertiseHost、UTF token、int ttlSeconds、int maxImageBytes、int maxFileBytes</td></tr>
 *   <tr><td>3 REQUEST</td><td>S→C</td><td>UUID transferId、u8 kind、UTF path、int maxBytes</td></tr>
 *   <tr><td>4 ABORT</td><td>C→S</td><td>UUID transferId、UTF reason</td></tr>
 *   <tr><td>5 DONE</td><td>C→S</td><td>UUID transferId、u8 ok、UTF error</td></tr>
 * </table>
 *
 * <p>本类零依赖、Java 8 语法：Bukkit 插件、Forge / NeoForge / Fabric 客户端共用同一份，
 * 单元测试可以脱离 Minecraft 直接跑。</p>
 */
public final class BridgeProtocol {

    /** 通道名固定。 */
    public static final String CHANNEL = "mcai:bridge";

    /** 协议版本（帧头的 u8）。 */
    public static final int VERSION = 1;

    public static final byte TYPE_HELLO = 1;
    public static final byte TYPE_INFO = 2;
    public static final byte TYPE_REQUEST = 3;
    public static final byte TYPE_ABORT = 4;
    public static final byte TYPE_DONE = 5;

    public static final byte KIND_SCREENSHOT = 0;
    public static final byte KIND_FILE = 1;

    /** 单个 UTF 字符串的字节上限，防止恶意长度把内存打爆。 */
    public static final int MAX_STRING_BYTES = 4096;

    /** 帧本身都很小；超过 64KB 直接判非法。 */
    public static final int MAX_FRAME_BYTES = 64 * 1024;

    /** 文件名上限（和 DSH 插件契约一致）。 */
    public static final int MAX_FILE_NAME_CHARS = 255;

    private static final byte[] MAGIC = {'M', 'C', 'A', 'I'};

    private BridgeProtocol() {
    }

    // ── 编码 ──────────────────────────────────────────────────────────

    public static byte[] hello(String loader, String clientVersion) {
        Writer out = new Writer(TYPE_HELLO);
        out.writeUtf(loader == null ? "" : loader);
        out.writeUtf(clientVersion == null ? "" : clientVersion);
        return out.toByteArray();
    }

    public static byte[] info(int port, String advertiseHost, String token, int ttlSeconds,
                             int maxImageBytes, int maxFileBytes) {
        Writer out = new Writer(TYPE_INFO);
        out.writeInt(port);
        out.writeUtf(advertiseHost == null ? "" : advertiseHost);
        out.writeUtf(token == null ? "" : token);
        out.writeInt(ttlSeconds);
        out.writeInt(maxImageBytes);
        out.writeInt(maxFileBytes);
        return out.toByteArray();
    }

    public static byte[] request(UUID transferId, int kind, String path, int maxBytes) {
        Writer out = new Writer(TYPE_REQUEST);
        out.writeUuid(transferId);
        out.writeByte(kind);
        out.writeUtf(path == null ? "" : path);
        out.writeInt(maxBytes);
        return out.toByteArray();
    }

    public static byte[] abort(UUID transferId, String reason) {
        Writer out = new Writer(TYPE_ABORT);
        out.writeUuid(transferId);
        out.writeUtf(reason == null ? "" : reason);
        return out.toByteArray();
    }

    public static byte[] done(UUID transferId, boolean ok, String error) {
        Writer out = new Writer(TYPE_DONE);
        out.writeUuid(transferId);
        out.writeByte(ok ? 1 : 0);
        out.writeUtf(error == null ? "" : error);
        return out.toByteArray();
    }

    // ── 解码 ──────────────────────────────────────────────────────────

    /** HELLO 帧。 */
    public static final class Hello {
        public final String loader;
        public final String clientVersion;

        Hello(String loader, String clientVersion) {
            this.loader = loader;
            this.clientVersion = clientVersion;
        }
    }

    /** INFO 帧。 */
    public static final class Info {
        public final int port;
        public final String advertiseHost;
        public final String token;
        public final int ttlSeconds;
        public final int maxImageBytes;
        public final int maxFileBytes;

        Info(int port, String advertiseHost, String token, int ttlSeconds,
             int maxImageBytes, int maxFileBytes) {
            this.port = port;
            this.advertiseHost = advertiseHost;
            this.token = token;
            this.ttlSeconds = ttlSeconds;
            this.maxImageBytes = maxImageBytes;
            this.maxFileBytes = maxFileBytes;
        }
    }

    /** REQUEST 帧。 */
    public static final class Request {
        public final UUID transferId;
        public final int kind;
        public final String path;
        public final int maxBytes;

        Request(UUID transferId, int kind, String path, int maxBytes) {
            this.transferId = transferId;
            this.kind = kind;
            this.path = path;
            this.maxBytes = maxBytes;
        }
    }

    /** ABORT 帧。 */
    public static final class Abort {
        public final UUID transferId;
        public final String reason;

        Abort(UUID transferId, String reason) {
            this.transferId = transferId;
            this.reason = reason;
        }
    }

    /** DONE 帧。 */
    public static final class Done {
        public final UUID transferId;
        public final boolean ok;
        public final String error;

        Done(UUID transferId, boolean ok, String error) {
            this.transferId = transferId;
            this.ok = ok;
            this.error = error;
        }
    }

    public static Hello parseHello(byte[] frame) {
        Reader in = reader(frame, TYPE_HELLO);
        return new Hello(in.readUtf(), in.readUtf());
    }

    public static Info parseInfo(byte[] frame) {
        Reader in = reader(frame, TYPE_INFO);
        return new Info(in.readInt(), in.readUtf(), in.readUtf(), in.readInt(), in.readInt(), in.readInt());
    }

    public static Request parseRequest(byte[] frame) {
        Reader in = reader(frame, TYPE_REQUEST);
        return new Request(in.readUuid(), in.readU8(), in.readUtf(), in.readInt());
    }

    public static Abort parseAbort(byte[] frame) {
        Reader in = reader(frame, TYPE_ABORT);
        return new Abort(in.readUuid(), in.readUtf());
    }

    public static Done parseDone(byte[] frame) {
        Reader in = reader(frame, TYPE_DONE);
        return new Done(in.readUuid(), in.readU8() != 0, in.readUtf());
    }

    /** 帧类型；用于分发。非法帧抛 {@link IllegalArgumentException}。 */
    public static byte typeOf(byte[] frame) {
        Reader in = reader(frame, (byte) -1);
        return in.type;
    }

    /**
     * 校验帧头并返回 payload reader。
     *
     * @param expectedType 期望的 type；传 {@code -1} 表示只校验帧头
     */
    private static Reader reader(byte[] frame, int expectedType) {
        if (frame == null || frame.length < 6) {
            throw new IllegalArgumentException("帧太短（" + (frame == null ? 0 : frame.length) + " 字节）");
        }
        if (frame.length > MAX_FRAME_BYTES) {
            throw new IllegalArgumentException("帧太大（" + frame.length + " 字节）");
        }
        if (frame[0] != MAGIC[0] || frame[1] != MAGIC[1] || frame[2] != MAGIC[2] || frame[3] != MAGIC[3]) {
            throw new IllegalArgumentException("不是 mcai:bridge 帧（magic 不匹配）");
        }
        int version = frame[4] & 0xFF;
        if (version != VERSION) {
            throw new IllegalArgumentException("协议版本不支持：" + version);
        }
        int type = frame[5] & 0xFF;
        if (expectedType >= 0 && type != expectedType) {
            throw new IllegalArgumentException("帧类型不符：期望 " + expectedType + "，收到 " + type);
        }
        Reader in = new Reader(frame, expectedType);
        in.pos = 6;
        in.type = (byte) type;
        return in;
    }

    // ── 底层读写 ──────────────────────────────────────────────────────

    /** 小端/大端都不容易搞错的最小 writer。 */
    public static final class Writer {
        private final ByteArrayOutputStream out = new ByteArrayOutputStream(64);

        public Writer(byte type) {
            out.write(MAGIC, 0, MAGIC.length);
            out.write(VERSION);
            out.write(type);
        }

        public Writer writeByte(int value) {
            out.write(value & 0xFF);
            return this;
        }

        public Writer writeInt(int value) {
            out.write((value >>> 24) & 0xFF);
            out.write((value >>> 16) & 0xFF);
            out.write((value >>> 8) & 0xFF);
            out.write(value & 0xFF);
            return this;
        }

        public Writer writeLong(long value) {
            writeInt((int) (value >>> 32));
            writeInt((int) value);
            return this;
        }

        public Writer writeUuid(UUID value) {
            UUID id = value == null ? new UUID(0L, 0L) : value;
            writeLong(id.getMostSignificantBits());
            writeLong(id.getLeastSignificantBits());
            return this;
        }

        public Writer writeUtf(String value) {
            byte[] bytes = (value == null ? "" : value).getBytes(StandardCharsets.UTF_8);
            if (bytes.length > MAX_STRING_BYTES) {
                throw new IllegalArgumentException("字符串太长：" + bytes.length + " 字节");
            }
            writeInt(bytes.length);
            out.write(bytes, 0, bytes.length);
            return this;
        }

        public byte[] toByteArray() {
            return out.toByteArray();
        }
    }

    /** 越界/长度非法时抛 IllegalArgumentException。 */
    public static final class Reader {
        private final byte[] data;
        private int pos;
        private byte type;

        Reader(byte[] data, int expectedType) {
            this.data = data;
            this.type = expectedType < 0 ? 0 : (byte) expectedType;
            this.pos = 0;
        }

        public byte type() {
            return type;
        }

        public int readU8() {
            require(1);
            return data[pos++] & 0xFF;
        }

        public int readInt() {
            require(4);
            int value = ((data[pos] & 0xFF) << 24)
                    | ((data[pos + 1] & 0xFF) << 16)
                    | ((data[pos + 2] & 0xFF) << 8)
                    | (data[pos + 3] & 0xFF);
            pos += 4;
            return value;
        }

        public long readLong() {
            long high = readInt() & 0xFFFFFFFFL;
            long low = readInt() & 0xFFFFFFFFL;
            return (high << 32) | low;
        }

        public UUID readUuid() {
            return new UUID(readLong(), readLong());
        }

        public String readUtf() {
            int length = readInt();
            if (length < 0 || length > MAX_STRING_BYTES) {
                throw new IllegalArgumentException("字符串长度非法：" + length);
            }
            require(length);
            String value = new String(data, pos, length, StandardCharsets.UTF_8);
            pos += length;
            return value;
        }

        public void requireEnd() {
            if (pos != data.length) {
                throw new IllegalArgumentException("帧尾有多余字节：" + (data.length - pos));
            }
        }

        private void require(int count) {
            if (pos + count > data.length) {
                throw new IllegalArgumentException("帧被截断：还差 " + (pos + count - data.length) + " 字节");
            }
        }
    }
}
