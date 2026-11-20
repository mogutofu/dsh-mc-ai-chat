package com.mogutofu.mcai.neoforge.net;

/**
 * 网络协议常量（mcai-client 侧有一份同值拷贝，改这里要同步改客户端）。
 *
 * <p>字段与 1.2.0 的 {@code PROTOCOL.md} 等价：协议版本 1、24KB 分块、
 * 截图 ≤ 8MB、文件 ≤ 16MB、服务端等附件最多 15 秒。</p>
 */
public final class NetProtocol {

    /** 协议版本字符串（payload 注册时的 version，两侧必须一致）。 */
    public static final String VERSION = "1";

    /** 协议版本号（ClientHello 里带）。 */
    public static final int PROTOCOL_VERSION = 1;

    /** 单块原始字节上限；现代 payload 上限更大，但统一 24KB。 */
    public static final int CHUNK_SIZE = 24 * 1024;

    /** 截图附件上限 8MB。 */
    public static final int MAX_SCREENSHOT_BYTES = 8 * 1024 * 1024;

    /** 文件附件上限 16MB。 */
    public static final int MAX_FILE_BYTES = 16 * 1024 * 1024;

    /** 分块数量上限：按 16MB / 24KB 再加一块余量。 */
    public static final int MAX_CHUNKS = (MAX_FILE_BYTES + CHUNK_SIZE - 1) / CHUNK_SIZE + 1;

    /** 服务端等待客户端附件的最长时间。 */
    public static final long ATTACHMENT_TIMEOUT_MS = 15_000L;

    /** 带附件的 HTTP 请求最短超时时间。 */
    public static final int MIN_HTTP_TIMEOUT_SECONDS = 90;

    /** AttachmentRequest.kind 取值。 */
    public static final byte KIND_SCREENSHOT = 0;
    public static final byte KIND_FILE = 1;

    private NetProtocol() {
    }

    public static String megabytes(int bytes) {
        return (bytes / (1024 * 1024)) + "MB";
    }
}
