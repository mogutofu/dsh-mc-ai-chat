package com.mogutofu.mcai.neoforge.net;

/**
 * 网络协议常量（服务端模组与 mcai_client 各持一份完全相同的拷贝）。
 *
 * <p>26.x 用 NeoForge 的 {@code CustomPacketPayload}：每个消息一个 id
 * （{@code mcai:client_hello} 等），字段与 1.2.0 的 {@code PROTOCOL.md} 一致，
 * 分块大小仍然是 24KB。</p>
 */
public final class NetProtocol {

    /** payload id 的命名空间。 */
    public static final String NAMESPACE = "mcai";

    /** 注册 payload 时声明的协议版本（{@code event.registrar(version)}）。 */
    public static final String VERSION = "1";

    /** ClientHello 里带的协议版本号。 */
    public static final int PROTOCOL_VERSION = 1;

    /** 单块原始字节上限（24576 字节）。 */
    public static final int CHUNK_SIZE = 24 * 1024;

    /** 截图附件上限 8MB。 */
    public static final int MAX_SCREENSHOT_BYTES = 8 * 1024 * 1024;

    /** 文件附件上限 16MB。 */
    public static final int MAX_FILE_BYTES = 16 * 1024 * 1024;

    /** 分块数量上限：按 16MB / 24KB 再加一块余量。 */
    public static final int MAX_CHUNKS = (MAX_FILE_BYTES + CHUNK_SIZE - 1) / CHUNK_SIZE + 1;

    /** 服务端等待客户端附件的最长时间。 */
    public static final long ATTACHMENT_TIMEOUT_MS = 15_000L;

    /** AttachmentRequest.kind 取值。 */
    public static final byte KIND_SCREENSHOT = 0;
    public static final byte KIND_FILE = 1;

    // ── payload id（namespace:path 的 path 部分） ──────────────────────
    public static final String PAYLOAD_CLIENT_HELLO = "client_hello";
    public static final String PAYLOAD_ATTACHMENT_REQUEST = "attachment_request";
    public static final String PAYLOAD_ATTACHMENT_CHUNK = "attachment_chunk";
    public static final String PAYLOAD_ATTACHMENT_ABORT = "attachment_abort";
    public static final String PAYLOAD_ATTACHMENT_TOO_LARGE = "attachment_too_large";

    private NetProtocol() {
    }

    public static String megabytes(int bytes) {
        return (bytes / (1024 * 1024)) + "MB";
    }
}
