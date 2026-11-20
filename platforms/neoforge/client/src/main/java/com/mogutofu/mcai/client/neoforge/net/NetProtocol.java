package com.mogutofu.mcai.client.neoforge.net;

/**
 * 网络协议常量（mcai 服务端侧有一份同值拷贝，改这里要同步改服务端）。
 *
 * <p>字段与 1.2.0 的 {@code PROTOCOL.md} 等价：协议版本 1、24KB 分块、
 * 截图 ≤ 8MB、文件 ≤ 16MB、服务端等附件最多 15 秒。</p>
 */
public final class NetProtocol {

    public static final String VERSION = "1";
    public static final int PROTOCOL_VERSION = 1;
    public static final int CHUNK_SIZE = 24 * 1024;
    public static final int MAX_SCREENSHOT_BYTES = 8 * 1024 * 1024;
    public static final int MAX_FILE_BYTES = 16 * 1024 * 1024;
    public static final int MAX_CHUNKS = (MAX_FILE_BYTES + CHUNK_SIZE - 1) / CHUNK_SIZE + 1;
    public static final long ATTACHMENT_TIMEOUT_MS = 15_000L;
    public static final int MIN_HTTP_TIMEOUT_SECONDS = 90;
    public static final byte KIND_SCREENSHOT = 0;
    public static final byte KIND_FILE = 1;

    private NetProtocol() {
    }

    public static String megabytes(int bytes) {
        return (bytes / (1024 * 1024)) + "MB";
    }
}
